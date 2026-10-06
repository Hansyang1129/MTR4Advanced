# 项目介绍

> **MTR4 Advanced** 是 Minecraft Transit Railway（MTR **4.0.4**）的附属模组。
> 它把列车的限速行为从「车头质点模型」改造成现实里的规则 ——
> **整列车通过才允许提速，车头一到就开始制动** —— 并给每条侧线一个可调的列车最高时速上限。

Minecraft 1.20.1 · Fabric · 客户端 + 服务端 · MIT

## 零、声明

该项目由AI完成（DeepSeek-V4-Flash & DeepSeek-V4.1-Flash)

如果您需要其他MTR版本适配，请开新issue，我会尽可能在一周内答复

---

## 一、它解决什么问题

### 痛点 1：车头一进高速区段，整列车立刻加速

MTR 原版在 `Vehicle#simulateMoving` 里只取**车头所在那一个轨段**的限速当目标：

```java
speedTarget = pathData[currentIndex].getSpeedLimitMetersPerMillisecond() * deviationSpeedAdjustment;
```

于是 120 km/h 区段前面压着一段 40 km/h 的道岔时，车头刚过界，8 节编组的整列车就开始冲 ——
现实中这是要脱轨的。

**本模组改成**：取「列车占用区间 `[车尾, 车头]` 内的**最低**线路限速」。
车尾没越过提速点，就不许加速。制动方向仍然由车头前瞻曲线控制（本来就该如此）。

### 痛点 2：线路限速 120，但这条线的车只能跑 80

MTR 里没有「这列车能跑多快」的概念，只有线路限速。想让某条侧线的车慢一点，只能去改线路限速，
而线路限速是共享的。

**本模组新增**：每条侧线一个「列车最高时速」字段（0 = 不限制），外加一个「跟随晚点浮动」开关。
默认是**真硬顶**：公式写成 `min(限速, 上限 / 晚点增益) × 晚点增益`，
展开后等于 `min(限速 × 增益, 上限)` —— 连 MTR 原版的晚点追赶（最多 ×1.25）也顶不破这个天花板。

### 痛点 3：时刻表永远对不上，列车永远显示晚点

运行时是「整列车通过才提速」，而 MTR 原版的时刻表推算（`generatePathDistancesAndTimeSegments`）
是**质点模型**，只看车头那一点。两者不一致 → 计划时间必然比实际跑得短 →
**结构性晚点，而且只累积不消失**。

**本模组改成**：推算时刻表时用**这条侧线自己的车型长度**做占用区间计算。
不同编组的侧线各算各的，互不干扰 —— 这是「晚点」问题的根因修复，不只是调参。

---

## 二、特性一览

| 特性 | 一句话 | 开关 |
| --- | --- | --- |
| **车尾控制提速** | 加速目标 = 占用区间内最低线路限速；车尾没过提速点不加速 | `tail-controlled-acceleration`（默认开） |
| **列车最高时速** | 侧线级硬上限，可选跟随晚点浮动 | 侧线字段，0 = 不限制 |
| **时刻表按车型长度推算** | 把「整列通过才提速」的延迟算进计划时间，消除结构性晚点 | `apply-vehicle-length-to-time-segments`（默认开） |
| **上限套进时刻表** | 否则线路 120 / 列车 80 时，计划按 120 算，永远晚点 | `apply-max-speed-to-time-segments`（默认开） |
| **批量复制** | 一次把当前设置刷到同车场其他所有侧线（按 40 条分批，防止大车场整包被拒） | 侧线界面的按钮 |
| **全服默认上限** | 不想逐条侧线填时，给一个全局默认值 | `default-max-speed-kmh` |

侧线界面新增四行（相对布局，跟着 MTR 自己的 `textWidth` 与最后一个原生控件走，不写死坐标）：

```
列车最高时速 km/h        [ 80 ]
0 = 不限制，留空同 0
[ ] 跟随晚点浮动
[ 复制到车场其他侧线 ]
```

---

## 三、技术看点（给开发者）

- **零 Gradle、零 Fabric Loom、零联网构建。** 直接把 MTR 官方 jar 当 classpath 跑 `javac`；
  为了解析 MTR 映射层的继承链，脚本会**迭代生成少量 `net.minecraft.*` 空壳 stub**
  （实测 3 轮收敛、7 个 stub），stub 不进最终产物。
  只要一个 JDK 17+，`python build.py` 就能出 jar。
- **不用启动游戏就能校验注入点。** `tools/check_targets.py` 用 `javap` 做三类检查：
  宿主方法是否存在、`@At` 目标成员是否存在、以及**该调用点在宿主方法内是否恰好 1 处**。
  最后一条最关键 —— 四个 `@Redirect` 都没写 `ordinal`，全靠「调用点唯一」成立；
  MTR 改版后新增一个调用点，就会**静默改错位置**（不报错、只是限速算错）。
  当前 18 项检查全 OK。
- **四轮对照字节码的代码审查。** 审查物不是源码而是 `javap -p -c` / `-l` 的反汇编结果，
  连局部变量表都拿去核对「车头里程」的坐标口径。发现并修掉的问题包括配置初始化的内存可见性
  （会导致服务端**永久**跑默认配置）、resize 后复选框丢状态、大车场发包超限整包被拒等。
  记录在 `CODE_REVIEW.md`。
- **客户端 / 服务端职责切分干净。** 界面相关的两条 Mixin 放在 mixin 配置的 `"client"` 数组里，
  专用服务端**不加载**它们 → 服务端不需要任何 Minecraft 客户端类，不会 `NoClassDefFoundError`。
- **兼容策略是「扩展 key」。** 侧线的新字段走 MTR 自己的序列化，
  没装本模组的一端读不到这些 key 时**直接跳过**（不清零、不报错），
  所以单端安装 / 中途卸载都不会破坏存档。
- **显式防御退化输入**：`NaN` / `Infinity` / 负值、`vehicleExtraData` 为 null、路径为空、
  车头越界、车尾还没入路径 —— 每一种都有明确回退，不猜。

---

## 四、适合谁

| 你是 | 能得到什么 |
| --- | --- |
| 建了复杂枢纽 / 道岔群的线路作者 | 列车不再顶着道岔限速冲进 120 区段，进路观感立刻正确 |
| 想区分「这条线用 80 的车、那条线用 120 的车」的运营方 | 侧线级上限 + 全服默认值 |
| 被「列车永远晚点」折磨的服主 | 时刻表按车型长度推算，根因修复 |
| 写 MTR 附属模组的开发者 | 一份不依赖 Gradle 的构建脚本、一个离线注入点校验器、四轮字节码审查记录 |

**前置**：Minecraft 1.20.1 · Fabric Loader ≥ 0.14.0 · **MTR 4.0.4**（4.0.5 会被主动拒绝启动）。

---

## 五、现状与边界

- 版本 **1.0.0**，只针对 **MTR 4.0.4** 的字节码做过校验；换版本必须重跑注入点校验。
- **手动驾驶模式下三项功能都不参与** —— 这是明确取舍（手动是给人开的，硬夹目标速度手感很怪），
  不是漏做。详见 [已知限制与路线图](已知限制与路线图.md#1-手动驾驶完全不生效)。
- 输入框只能填整数、界面在 GUI scale 很大时会溢出、配置没有热重载 —— 都记录在案，
  见 [已知限制与路线图](已知限制与路线图.md)。
- 本项目**不附带 MTR 本体**，也不附带任何 Minecraft 资源。

---

## 六、English Introduction

> **MTR4 Advanced** is an addon for **Minecraft Transit Railway 4.0.4** (Minecraft 1.20.1 / Fabric)
> that makes train speed limits behave the way they do in reality.

**What it changes**

- **Acceleration is released by the tail, braking is led by the head.** Vanilla MTR takes the speed
  limit of the single rail section under the train's *head*, so a train starts accelerating the moment
  its head enters a 120 km/h section — even with the tail still on a 40 km/h switch. This mod uses the
  **lowest limit within the occupied interval** `[tail, head]` instead, and keeps MTR's own
  head-based look-ahead braking curve.
- **Per-siding maximum speed.** Each siding gets a "train max speed" field (0 = unlimited) plus an
  optional "follow delay adjustment" toggle. By default it is a *hard* ceiling: written as
  `min(limit, cap / adjustment) × adjustment`, i.e. `min(limit × adjustment, cap)` — even MTR's
  delay-recovery boost (up to ×1.25) cannot break it.
- **Vehicle-length-aware schedules.** MTR generates timetable time segments with a point-mass model,
  which is inconsistent with tail-controlled acceleration and causes permanent, accumulating delays.
  This mod recomputes them from the siding's own train length.

**Engineering highlights**

- **No Gradle, no Fabric Loom, no network.** `python build.py` compiles against the official MTR jar
  with a plain JDK 17+, auto-generating the few `net.minecraft.*` stubs needed to resolve MTR's
  mapping layer (stubs never ship in the jar).
- **Offline injection-point verification.** `tools/check_targets.py` uses `javap` to check that each
  target method exists *and* that the redirected call site occurs exactly once inside its host method —
  the precondition for `@Redirect` without `ordinal`. 18/18 checks pass.
- **Four rounds of bytecode-level code review**, documented in `CODE_REVIEW.md`.
- **Clean side split**: client-only mixins live in the `"client"` array, so dedicated servers never
  load any Minecraft client class.

**Status**: v1.0.0, verified against MTR 4.0.4 only. Does **not** apply in manual driving mode (deliberate).
Requires Minecraft 1.20.1, Fabric Loader ≥ 0.14.0 and MTR 4.0.4.

---

---

## 相关页面

- [快速开始](快速开始.md) —— 安装与验证
- [工作原理](工作原理.md) —— 上面每一条的技术推导
- [已知限制与路线图](已知限制与路线图.md) —— 不做什么、以后做什么
