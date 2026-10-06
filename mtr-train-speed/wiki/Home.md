# MTR4 Advanced

> **MTR 4.0.4 的附属模组（Minecraft 1.20.1 / Fabric）**：把列车的限速行为改成现实里的样子，并给每条侧线加一个「列车最高时速」上限。

| 项目 | 值 |
| --- | --- |
| mod id | `mtr4-advanced` |
| 版本 | `1.0.0` |
| 环境 | 客户端 + 服务端都要装（`environment: "*"`） |
| 前置 | Minecraft 1.20.1 · Fabric Loader ≥ 0.14.0 · **MTR 4.0.4** |
| 硬冲突 | MTR `>=4.0.5 <4.0.6`（`breaks`，装了直接拒绝启动） |
| 许可 | MIT |
| 构建产物 | `build/libs/MTR4Advanced-1.0.0.jar` |
| 依赖 Fabric API | 否（只依赖 MTR + Fabric Loader） |

---

## 一、它改了什么

MTR 原版在 `Vehicle#simulateMoving` 里只用**车头所在那一个轨段**的限速当目标速度。
结果就是：车头一压进 120 km/h 区段，整列车立刻加速，哪怕车尾还卡在 40 km/h 的道岔上。

本模组动了两处取值，并新增了一个字段：

| # | 特性 | 一句话 |
| --- | --- | --- |
| 1 | **提速由车尾控制** | 取「列车占用区间 `[车尾, 车头]` 内的最低线路限速」当加速目标 —— 车尾没过提速点就不许加速 |
| 2 | **列车最高时速** | 每条侧线一个硬上限（可选「跟随晚点浮动」），最终速度 = `min(线路限速, 上限)` |
| 3 | **时刻表按车型长度推算** | 时刻表不再用车头质点模型，改用本侧线车型长度算占用区间 —— 消除「结构性晚点」 |

降速方向沿用 MTR 自己的 `Siding#getUpcomingSlowerSpeed` 前瞻制动曲线（本来就是车头语义），
本模组只在其上叠加列车最高时速。

---

## 二、30 秒上手

```bash
# 1) 构建（只要 JDK 17+，不需要 Gradle / 联网）
python build.py                       # 产出 build/libs/MTR4Advanced-1.0.0.jar
python tools/check_targets.py         # 校验 Mixin 注入点，必须 ALL OK

# 2) 客户端和服务端各拷一份
cp build/libs/MTR4Advanced-1.0.0.jar <游戏目录>/mods/
```

进游戏后打开任意侧线的编辑界面，最下面会多出四行：

```
列车最高时速 km/h        [ 80 ]
0 = 不限制，留空同 0
[ ] 跟随晚点浮动
[ 复制到车场其他侧线 ]
```

> 更详细的分步说明见 **[快速开始](快速开始.md)**；改配置见 **[配置参考](配置参考.md)**。

---

## 三、文档地图

| 页面 | 内容 | 谁会看 |
| --- | --- | --- |
| [快速开始](快速开始.md) | 前置、安装、验证生效、从旧版迁移、卸载 | 玩家 / 服主 |
| [工作原理](工作原理.md) | 占用区间、两个 `@Redirect`、限速公式推导、时刻表推算、调用链 | 想搞懂为什么这么写的人 |
| [配置参考](配置参考.md) | 四个配置项逐个说明、生效时机、配置文件到底落在哪 | 服主 |
| [侧线界面](侧线界面.md) | 那四行控件的布局、输入规则、「复制到车场」的真实语义 | 玩家 |
| [构建与工具](构建与工具.md) | `build.py` 与 `tools/check_targets.py` 的原理、预期输出、跨平台注意 | 二次开发者 |
| [开发指南](开发指南.md) | Mixin 注入点总表、访问器清单、加一个新字段的完整清单、踩坑 | 二次开发者 |
| [常见问题](常见问题.md) | 症状 → 原因 → 处理，一张表查到底 | 所有人 |
| [已知限制与路线图](已知限制与路线图.md) | 现在**不做**什么（手动驾驶等），以及后面可以做什么 | 所有人 |
| [变更日志](变更日志.md) | 1.0.0、改名记录、四轮代码审查的时间线 | 所有人 |

---

## 四、代码地图

```
src/main/java/cn/nansai/mtrspeed/          （11 个文件，约 970 行）
├── Config.java                          配置读写，纯 JDK，双击检查锁懒加载
├── SpeedLogic.java                      限速核心：占用区间最低限速、硬顶、晚点增益
├── api/
│   └── SidingMaxSpeedAccess.java        侧线新字段的访问接口（**必须在 mixin 包之外**）
└── mixin/
    ├── SidingMixin.java                 给 Siding 加两个 @Unique 字段 + 反序列化
    ├── SidingSchemaMixin.java           把字段写进序列化（注入 SidingSchema#serializeData）
    ├── SidingTimeSegmentsMixin.java     时刻表推算：占用区间最低限速 + 套上限
    ├── VehicleSpeedMixin.java           重定向 simulateMoving 里的两个限速取值
    ├── access/                          @Accessor 访问私有成员
    │   ├── VehicleAccess.java               siding / deviationSpeedAdjustment
    │   ├── VehicleSchemaAccess.java         railProgress
    │   └── SavedRailScreenBaseAccess.java   savedRailBase / textWidth / sliderDwellTimeSec ...
    └── client/
        └── SidingScreenMixin.java       侧线界面四行配置区 + 「复制到车场」按钮

build.py                                 离线构建（javac + 自动生成 MC stub + zipfile 打包）
tools/check_targets.py                   离线校验 Mixin 注入点（javap 比对 MTR 字节码）
src/main/resources/fabric.mod.json       模组元数据
src/main/resources/mtr4-advanced.mixins.json  Mixin 配置（区分通用 / 仅客户端）
```

---

## 五、与 MTR 原版的差异一览

| 行为 | MTR 4.0.4 原版 | 装本模组后 |
| --- | --- | --- |
| 加速目标限速 | 车头所在轨段的限速 | 占用区间 `[车尾, 车头]` 内的**最低**限速 |
| 制动目标限速 | 车头前瞻制动曲线 `getUpcomingSlowerSpeed` | 同上，**再**与占用区间最低限速取 `min`（开启 `tail-controlled-acceleration` 时） |
| 速度上限 | 无（只有线路限速 × 晚点增益） | 侧线「列车最高时速」，默认硬顶、可选跟随晚点浮动 |
| 时刻表推算 | 车头质点模型，只看当前点限速 | 按**本侧线车型长度**取占用区间最低限速，再套上限 |
| 手动驾驶 | 走 `getMaxManualSpeed()` | **完全不受本模组影响**（见 [已知限制](已知限制与路线图.md)） |

数值例子（限速 40、上限 80、晚点增益 A = 1.25）：

| 模式 | 实现 | 最终目标速度 |
| --- | --- | --- |
| 硬顶（默认） | `min(限速, 上限 / A) × A` | `min(40 × 1.25, 80) = 50` |
| 跟随晚点浮动 | `min(限速, 上限) × A` | `min(40, 80) × 1.25 = 50` |
| 硬顶，限速 100 | 同上 | `min(100 × 1.25, 80) = 80`（上限守得住） |
| 浮动，限速 100 | 同上 | `min(100, 80) × 1.25 = 100`（上限被突破） |

---

## 六、当前状态与边界

- **只针对 MTR 4.0.4 的字节码做过校验**。换 MTR 版本必须重跑 `python tools/check_targets.py`（预期 18 项全 OK）。
- **手动驾驶模式下三项功能全部不参与**，这是明确取舍，不是 bug —— 理由见 [已知限制与路线图](已知限制与路线图.md)。
- 配置**只在启动时读一次**，改完要重启游戏 / 服务端。
- 侧线改完车型或最高时速后，要等列车重新生成路径（或重启存档）才会重算时刻表。
- 代码经过四轮外部审查（对照 MTR 4.0.4 字节码），记录在仓库根的 `CODE_REVIEW.md`；遗留项与功能想象见 [已知限制与路线图](已知限制与路线图.md)。

---

## 七、相关文档

- [README.md](../README.md) —— 面向玩家的长文说明（含四轮审查摘要）
- [CODE_REVIEW.md](../CODE_REVIEW.md) —— 完整审查记录（含字节码证据与「还能加些什么」清单）
- [wiki/README.md](README.md) —— 这份 wiki 怎么发布到 GitHub

## 许可

MIT。见仓库根的 [LICENSE](../LICENSE)。
