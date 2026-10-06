# MTR4 Advanced

MTR 4.0.4（Minecraft 1.20.1 / Fabric）的附属模组。当前包含：

1. **限速提高由车尾通过控制，限速降低由车头通过控制**（现实中的限速逻辑）
2. **侧线里增加「列车最高时速」**，线路限速高于列车最高时速时按列车最高时速走
3. **侧线时刻表按车型长度推算**（`apply-vehicle-length-to-time-segments`），
   把车尾控制提速带来的延迟加速算进计划时间，避免结构性晚点

> **改名记录（2026-09-19）**：原名 `MTR Train Speed`，现名 **MTR4 Advanced**。
> mod id 从 `mtr-train-speed` 改为 `mtr4-advanced`，随之：
> - 旧的 `mods/mtr-train-speed-1.0.0.jar` **必须删掉**，否则两份 mixin 会对同一目标重复注入
> - 配置文件改为 `config/mtr4-advanced.properties`，旧文件里的字段需手工搬过去
> - 产物名改为 `build/libs/MTR4Advanced-1.0.0.jar`
> - Java 包名 `cn.nansai.mtrspeed` 与 mixin 内部的 `mtrspeed$` 前缀**保持不变**（纯内部标识）

---

> 📖 **文档（[`wiki/`](wiki/Home.md)）**：[项目介绍](wiki/项目介绍.md) ·
> [快速开始](wiki/快速开始.md) ·
> [工作原理](wiki/工作原理.md) · [配置参考](wiki/配置参考.md) · [侧线界面](wiki/侧线界面.md) ·
> [构建与工具](wiki/构建与工具.md) · [开发指南](wiki/开发指南.md) · [常见问题](wiki/常见问题.md) ·
> [已知限制与路线图](wiki/已知限制与路线图.md) · [变更日志](wiki/变更日志.md)
>
> 本 README 是面向玩家的长文说明；`wiki/` 是按主题拆分的参考手册（原理推导、注入点总表、排错表）。

---

## 一、限速逻辑改了什么

原版 MTR 的 `Vehicle#simulateMoving` 里，加速目标速度取的是**车头所在轨段**的线路限速：

```java
speedTarget = pathData[currentIndex].getSpeedLimitMetersPerMillisecond() * deviationSpeedAdjustment;
```

也就是说：车头一进入 120 km/h 区段，整列车立刻开始加速，哪怕车尾还压在 40 km/h 的区段上。

本模组改成：

| 场景 | 控制点 | 实现 |
| --- | --- | --- |
| 限速**提高**（提速） | **车尾** | 目标速度取「列车占用区间 `[railProgress - 车长, railProgress]` 内的最低线路限速」。车尾没通过提速点，就不许加速 |
| 限速**降低**（制动） | **车头** | 沿用 MTR 原本的 `Siding#getUpcomingSlowerSpeed` 前瞻制动曲线（本来就是基于车头的），再叠加列车最高时速；开了 `tail-controlled-acceleration` 时，制动目标还会跟「占用区间内最低限速」再取一次 `min`（车尾没离开低速区，目标就不许抬上去） |
| 列车最高时速 | 侧线设置 | 硬上限，任何情况下都不突破（浮动模式下限的是**未被晚点增益放大**的那个值，见第二节） |

> 说明：MTR 内部本来就用 `[railProgress - 总车长, railProgress]` 表示列车占用区间
> （见 `Vehicle#writeVehiclePositions`），车头 = `railProgress`，车尾 = `railProgress - 总车长`，
> 反向行驶（`reversed`）也一样成立，所以不需要区分行驶方向。

> 关于「降低」那一行：`tail-controlled-acceleration` 同时作用在**加速**和**制动**两条目标上。
> 严格说这让车尾也参与了降速控制，与上面「降速由车头控制」的概括略有出入 ——
> 实际影响很小（车尾所在里程必然在车头之后，压出来的值通常 ≤ 已经在跑的速度），
> 但极端工况（车尾尚在低速区 + 前方又出现更低限速）会叠加出偏早的制动。
> 想彻底回到「降速只看车头」，把 `SpeedLogic#getDecelerationTarget` 里的那个 `if` 删掉即可。

---

## 二、列车最高时速

- 打开侧线（Siding）编辑界面，最下面多出来**四行**：

  | 行 | 内容 |
  | --- | --- |
  | 1 | `列车最高时速 km/h` 标签 + 数字输入框（只填数字） |
  | 2 | `0 = 不限制，留空同 0` 说明 |
  | 3 | `[ ] 跟随晚点浮动` 勾选框 |
  | 4 | `[ 复制到车场其他侧线 ]` 按钮 |

- 布局是**相对的**，不是写死坐标：
  - 列：标签贴左边距 `20`，输入框跟 MTR 自己那列对齐（`20 + textWidth + 2`）
  - 行：以 MTR 界面上最后一个原生控件 `sliderDwellTimeSec` 的底部为锚点往下排
  - 所以切语言（`textWidth` 变化）、MTR 增删控件时这块会自动跟着走

### 「复制到车场其他侧线」按钮

把界面上当前的「列车最高时速」+「跟随晚点浮动」一次性刷到**同一个车场（Depot）里的其他所有侧线**，
不用一条一条手动改。点完按钮自己会变成「已提交 N 条，等待服务端确认」。

实现要点（写同步代码时容易漏）：MTR 的 `SidingScreen#onClose2` 只会把**当前这一条**侧线打包发给服务端，
给别的侧线改字段不会落库。所以这里走 MTR 自己的更新通道：

```java
UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
request.addSiding(siding);                              // 自己
int pending = 1;
for (Siding other : depot.savedRails) {
    ...; request.addSiding(other);
    if (++pending >= MTRSPEED$BATCH_SIZE) {             // 满 40 条发一包
        InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
        request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
        pending = 0;
    }
}
if (pending > 0) {
    InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
}
```

**为什么要分批**：一条 `Siding` 序列化出来含 `pathDistances` / `timeSegments` 等数组，
体积随线路长度增长，而 Fabric 自定义 payload 有单包上限（1 MiB）。
不分批的话大车场一旦超限，**整个包会被拒，连玩家自己这条都存不进去**。
所以按 **`MTRSPEED$BATCH_SIZE = 40`** 条一批发送（`pending` 从 1 起算，含当前这条侧线）。

#### 点完到底算不算改成了

这里是**先改本地对象、再发请求**，而且拿不到回执：

```java
InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));   // 无返回值
```

`UpdateDataRequest#update()` 那个返回 `UpdateDataResponse` 的同步 API 只有**服务端**才走，
客户端这边的 `sendPacketToServer` 不会告诉你成功与否。于是：

| 场景 | 结果 |
| --- | --- |
| 单人世界 / 局域网主机 / OP | 服务端接受，落库 ✓ |
| 多人服非 OP（或没进 dashboard 编辑模式） | 服务端**直接丢包** ✗ —— 本地显示改成了，重进存档 / 重拉 dashboard 数据后静默回滚 |

所以按钮文案刻意写「**已提交**」而不是「已复制」：它只承诺「包发出去了」。
多人服上改完想确认有没有真生效，关掉侧线界面重开一次，值还在才算落库。

另外分批发包存在「部分成功」的中间状态（某一包超时/被拒时，车场里一半改了一半没改），
所以文案在多包时会把包数一起报出来（`已提交 N 条（X 包）`），不让人误以为一包全成。

点上之后**本地立刻生效**，不需要先关界面（真正重要的是服务端有没有接受）。
- 最终限速 = `min(线路限速, 列车最高时速)`，**但晚点增益另算**，见下节

### 「跟随晚点浮动」是什么意思

MTR 原版有「晚点追赶」机制：列车晚点时把目标速度乘上 `deviationSpeedAdjustment`（默认最多 **×1.25**）。
这个增益是在**取完限速之后**才乘的，所以列车最高时速怎么被对待，有两种口径：

设 `增速 A = deviationSpeedAdjustment`（≥ 1.0，晚点最多 1.25），`cap` = 列车最高时速。

| 勾选状态 | 实现 | 最终目标速度 |
| --- | --- | --- |
| **不勾**（默认，硬顶） | `min(限速, cap / A) × A` | **`min(限速 × A, cap)`** —— `cap` 绝对不被突破；限速没顶到 `cap` 时，MTR 原版的晚点追赶照常生效（限速本身仍会被放大最多 1.25×） |
| **勾选**（浮动） | `min(限速, cap) × A` | **`min(限速, cap) × A`** —— 连 `cap` 一起上浮，最高可到 `cap` 的 1.25 倍 |

两种模式的差别**只在于 `cap` 自己跟不跟着增益上浮**：不勾时 `cap` 是字面的硬上限（任何情况下都超不过），
勾选时它可以被放大到 1.25 倍。

- 不勾：限速 40、cap 80、A=1.25 → `min(40×1.25, 80) = 50`。**列车的天花板 80 守得住**，
  但它确实跑到了 50，比线路限速 40 高 —— 这是 MTR 原版晚点追赶的行为，本模组没有干预。
- 勾选：限速 40、cap 80、A=1.25 → `min(40, 80) × 1.25 = 50`（本例一样）；
  若限速 100、cap 80 → `min(100, 80) × 1.25 = 100`，**超过 cap**，用来追回晚点。

想要「最高时速 80 就是 80，晚点也别想破」→ 不勾。
想要「晚点时允许短暂冲到 100 把时间追回来」→ 勾上。

> 如果你想要的是「连原版晚点追赶也一起禁掉、最终严格 = `min(限速, cap)`」，
> 把 `SpeedLogic#applyHardCap` 最后一行改成 `Math.min(limit, cap) / safeAdjustment` 即可。
> 目前保留现状的理由是：晚点追赶是 MTR 原版机制，本模组只想给列车加一个**不受增益放大**的天花板，
> 不想顺手改掉原版的追点行为。

另外默认会把这个上限也套用到侧线的时刻表推算（`generatePathDistancesAndTimeSegments`），
否则线路限速 120、列车限速 80 时，时刻表按 120 算，列车会永远显示晚点。
（时刻表推算时没有「晚点」这个概念，所以那边永远按硬顶处理。）

---

## 二·五、时刻表按「发车侧线的车型长度」推算

这是**晚点问题的根因**：

- 运行时（`Vehicle#simulateMoving`）是**车尾控制提速**：整列车通过提速点才允许加速
- 原版时刻表（`Siding#generatePathDistancesAndTimeSegments`）是**质点模型**：只看车头那一点的限速

两者不一致 → 计划时间永远比实际跑得短 → **结构性晚点**，且晚点只会累积不会消掉。

本模组在推算时刻表时，改用**这条侧线自己的列车长度**做占用区间计算：

```
占用区间 = [railProgress - 该侧线列车总长, railProgress]
目标速度 = min(占用区间内最低线路限速, 列车最高时速)
```

- 列车长度取自 `Siding#getVehicleCars()` → `Siding.getTotalVehicleLength(...)`
- 所以**不同车型（不同编组长度）的侧线，各自算各自的时刻表**，互不干扰
- 坐标口径与运行时一致：MTR 里 `railProgress` 就是**车头**里程
  （时刻表初值 = `(railLength + 车长) / 2`，即列车居中停在侧线上时的车头位置；
   运行时 `Vehicle#simulateInDepot` 也是 `railProgress = vehicleExtraData.getDefaultPosition()`）

开关：`apply-vehicle-length-to-time-segments`（默认 `true`）。

---

## 三、安装

1. 前置：**Minecraft 1.18.2 / 1.19.2 / 1.19.4 / 1.20.1 / 1.20.4** 之一 + 对应加载器（Fabric 或 Forge）+ **MTR 4.0.4**（选对应 MC 版本、对应加载器的构建）
2. 挑对 jar：`MTR4Advanced-1.0.1-<MC版本><加载器>.jar`
   （例：`MTR4Advanced-1.0.1-1.20.1Fabric.jar`）丢进 `mods` 文件夹
3. 客户端和服务端都要装，**且两边用同一份**（限速计算在服务端，设置界面在客户端）

| MC 版本 | Fabric | Forge |
| --- | --- | --- |
| 1.18.2 | `MTR4Advanced-1.0.1-1.18.2Fabric.jar` | `MTR4Advanced-1.0.1-1.18.2Forge.jar` |
| 1.19.2 | `MTR4Advanced-1.0.1-1.19.2Fabric.jar` | `MTR4Advanced-1.0.1-1.19.2Forge.jar` |
| 1.19.4 | `MTR4Advanced-1.0.1-1.19.4Fabric.jar` | `MTR4Advanced-1.0.1-1.19.4Forge.jar` |
| 1.20.1 | `MTR4Advanced-1.0.1-1.20.1Fabric.jar` | `MTR4Advanced-1.0.1-1.20.1Forge.jar` |
| 1.20.4 | `MTR4Advanced-1.0.1-1.20.4Fabric.jar` | `MTR4Advanced-1.0.1-1.20.4Forge.jar` |

> 装错版本（例如把 1.20.1 的 jar 丢进 1.20.4）会被加载器的依赖校验直接拒绝启动，不会静默出错。
> Forge 版的 mod id 是 `mtr4advanced`（Forge 的 modId 不允许连字符），Fabric 版是 `mtr4-advanced`。

---

## 三·五、服务端部署（兼容性）

- **两端都要装，但职责不同**：本模组 `environment: "*"`（通用端），mixin 配置里界面相关的两条
  （`client.SidingScreenMixin`、`access.SavedRailScreenBaseAccess`）放在 `"client"` 数组里，
  **专用服务端不会加载它们** —— 所以服务端不需要任何 Minecraft 客户端类，不会 `NoClassDefFoundError`。
- **服务端的配置才是权威**：限速（`Vehicle#simulateMoving`）和时刻表
  （`Siding#generatePathDistancesAndTimeSegments`）都只在服务端跑，客户端那份基本不参与行车计算。
  调参请改**服务端**的 `config/mtr4-advanced.properties`。
- **配置目录解析**：优先 `FabricLoader#getConfigDir()`（Fabric 认的游戏目录），
  取不到才回退 `user.dir/config`。写死 `user.dir` 的坑：用面板（Pterodactyl 等）/ systemd /
  从别的目录执行启动脚本时，进程工作目录往往**不是**游戏目录 → 配置被写到别处，
  服务端静默跑默认值（表现是「配置改了没生效」，日志里还什么都不报）。
- 首次启动会在日志里打一行 `[MTR4 Advanced] 已生成默认配置: <绝对路径>`，找不到配置文件时先看这行。
- Java：编译用 `--release 17`，服务端 Java 17 / 21 均可；不依赖 Fabric API，只依赖 MTR + Fabric Loader。
- 版本约束：`depends mtr >=4.0.4`；`breaks mtr >=4.0.5 <4.0.6`（4.0.5 是硬冲突，装了直接拒绝启动）。
- 只装一端的后果：
  - 只有客户端 → 界面能改，但服务端没有这个字段，值存不进去（`ReaderBase.unpackValue` 会跳过不认识的 key，不会崩）
  - 只有服务端 → 行车按配置走，但客户端没有界面可改

---

## 四、配置

首次启动会在**游戏目录的** `config/mtr4-advanced.properties` 生成配置（自带说明）：

```properties
# MTR4 Advanced —— MTR 4.0.4 附属模组
# 改完要重启游戏 / 服务端才生效（配置只在启动时读一次）
#
# tail-controlled-acceleration
#   提速由车尾控制。true = 车尾通过提速点后才允许加速（现实逻辑）
#   false = MTR 原版逻辑（车头通过即加速）
# default-max-speed-kmh
#   侧线没填「列车最高时速」时用的默认值。0 = 不限制
# apply-max-speed-to-time-segments
#   把列车最高时速套进侧线的时刻表推算（不勾的话限列车速的车会结构性晚点）
# apply-vehicle-length-to-time-segments
#   时刻表按发车侧线自己的列车长度，取 [车尾, 车头] 区间内最低限速

tail-controlled-acceleration=true
default-max-speed-kmh=0.0
apply-max-speed-to-time-segments=true
apply-vehicle-length-to-time-segments=true
```

改完重启生效（键的顺序由 `Properties` 决定，不影响读取）。

---

## 五、自己构建

```bash
# 日常开发：只构建一个目标（自动找上级目录 / libs 里的 MTR jar）
python build.py
MTR_JAR=/path/to/MTR.jar python build.py

# 发布：一次产出 5 个 MC 版本 × Fabric/Forge 共 10 个 jar
#   先把各版本的 MTR jar 放进 _mtr_jars/，命名 MTR-<loader>-4.0.4+<MC>.jar
python build_release.py --jars _mtr_jars --out ../releases
```

只需要 JDK 17+，不需要 Gradle / Fabric Loom / 联网。
脚本会：

1. 用 MTR 官方 jar 当 classpath 直接 `javac`（MTR 类名稳定，不需要重映射）
2. 自动生成少量 `net.minecraft.*` 空壳 stub 供 javac 解析继承链（stub 不进最终产物）
3. 用 `zipfile` 打包成 jar

---

## 六、文件结构

```
src/main/java/cn/nansai/mtrspeed/
├── Config.java                     配置文件读写（纯 JDK）
├── SpeedLogic.java                 限速核心逻辑
├── api/SidingMaxSpeedAccess.java   侧线字段访问器（必须在 mixin 包外）
└── mixin/
    ├── SidingMixin.java            侧线新增「列车最高时速」字段（读取 + 实现接口）
    ├── SidingSchemaMixin.java      把该字段写进序列化（注入 SidingSchema#serializeData）
    ├── SidingTimeSegmentsMixin.java  时刻表推算：按车型长度取占用区间最低限速 + 套列车最高时速
    ├── VehicleSpeedMixin.java      重定向 simulateMoving 里的两个限速取值
    ├── access/                     @Accessor 访问器（私有/protected 字段）
    └── client/SidingScreenMixin.java  侧线界面四行配置区（相对布局）
```

---

## 七、踩过的坑

1. **普通类不能放进 mixin 包**
   mixin 配置里 `package` 指向的包（`cn.nansai.mtrspeed.mixin.*`）下的类全都会被 Mixin 当 mixin 处理。
   把纯接口放进去，运行时会崩：
   `IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly`
   → 接口放 `cn.nansai.mtrspeed.api`。

2. **Mixin 不会为目标类生成父类方法的 override**
   想注入 `Siding#serializeData` 是不行的 —— 这个方法声明在 `SidingSchema` 上，Siding 只是继承，
   Mixin 0.8.7 会报
   `could not find any targets matching 'serializeData' in org/mtr/core/data/Siding`
   → 改成注入 `SidingSchema#serializeData`，在 handler 里 `instanceof Siding` 判断。
   （`Siding#serializeFullData` 会经由 `SerializedDataBase` 的默认实现转调 `serializeData`，
   所以存档和网络同步两条路都会被覆盖到。）

## 八、代码审查

`CODE_REVIEW.md` 是多轮外部审查（对照 MTR 4.0.4 **字节码**做的），结论已处理完毕：

- **P0 `Config.load()` 并发**：`loaded` 置位太早 + 无内存屏障 → 渲染线程抢先触发加载时服务端会永久跑在默认配置上。
  已改为 `volatile` + `synchronized` 双检锁，`loaded = true` 移到字段全部赋值之后。
- **P1 复选框 resize 后丢失**：Minecraft resize 复用 Screen 实例重跑 `init2`，`setChecked` 无条件从 `siding` 重读
  会把用户刚勾的状态清掉。已改为只在首次创建时读一次（与输入框的保护方式对齐）。
- **P1 复制包无分批**：已按 40 条一批发送，防止大车场单包超限导致整包被拒。
- **P1 硬顶公式与文档不符**：判定为**保留实现、修正文档** —— 详见第二节的公式表
  （理由：晚点追赶是 MTR 原版机制，本模组只想给列车加一个不受增益放大的天花板，不想顺手改掉原版行为）。
- **P2 其余 9 项**全部已改：车长重复计算、`vehicleExtraData` null 防御、`NaN` 穿透 setter、
  小数截断、无用 accessor、配置读写静默失败、README 行数不符、兜底魔数注释、`build.py` 找 jar 顺序依赖。
- **`tools/check_targets.py` 增加调用点计数校验**：不止查「方法存在」，还查「宿主方法内该调用点恰好 1 处」，
  防止 MTR 改版新增调用点后 `@Redirect` 静默命中错误位置。当前 18 项检查全 OK。

### 复审新增项（N1–N9）

| # | 问题 | 处理 |
| --- | --- | --- |
| N1 | `check_targets.py` 调用点提示把宿主类当成了方法所属类（打印成 `Vehicle#getSpeedLimitMetersPerMillisecond`） | 已改用 `owner`，现输出 `PathData#getSpeedLimitMetersPerMillisecond` |
| N2 | README 同步示例还是单包写法 | 已换成 `MTRSPEED$BATCH_SIZE` 分批版 + 「为什么要分批」 |
| N3 | `breaks` 区间 `<4.1.0` 过宽，会把将来修好问题的 4.0.6 一起拦死，也没法在 4.0.5 上做对照测试 | 收窄为 `">=4.0.5 <4.0.6"` |
| N4 | 分批后存在「部分成功」中间状态，按钮只报总数 | 文案改为多包时显示「已复制到 N 条（分 X 包发送）」，如实反映（**第四轮又把「已复制」改成了「已提交」**，见下） |
| N5 | `build.py` 里 `MTR` / `mtr` 两套 pattern 在 Windows 上等价 | 合并成一套 |
| N6 | 合并成 `*MTR*` 后，**Linux / macOS 的 glob 区分大小写**，官方 release 名是小写 `mtr-fabric-4.0.4.jar` → 找不到 jar 直接退出 | 改用 glob 字符类 `*[Mm][Tt][Rr]*.jar`，一套 pattern 全平台通吃 |
| N7 | README 第 104 行「两种模式都不是字面硬上限」与上一行「`cap` 绝对不被突破」自相矛盾 | 改为「差别只在于 `cap` 跟不跟着增益上浮」 |
| N8 | README「P2 其余 8 项」，冒号后实际列了 9 项 | 改为 9 项 |
| N9 | `check_targets.py` 默认写死 `javap`。**PATH 里只有 Oracle 的 javapath（java/javac），没有 javap**，且 `JAVA_HOME` 未设置 → 裸跑直接 `FileNotFoundError` | 新增 `resolve_tool()`：显式参数 > PATH > `JAVA_HOME` > 常见 JDK 目录按**真实版本号降序**探测（按字符串反序会把 `jdk1.8.0_202` 排到 `jdk-21` 前面，javap 8 读不了 release 17 的 class，会静默返回 0 项检查） |

### 第四轮（2026-09-28）：顺着字节码往下挖一层

`tools/check_targets.py` 复跑仍是 18/18 OK（javap 用本机 jdk-21）。
本轮新增的问题是靠 `javap -p -c` / `javap -p -l` 反编译宿主才看出来的，静态通读代码发现不了：

| # | 问题 | 处理 |
| --- | --- | --- |
| P1-1 | **手动驾驶分支完全绕过本模组**：`simulateMoving` 里 `isCurrentlyManual()` 分支排在 ATO 路径之前且 `goto` 跳过它，手动时三项功能全部失效 | **不改**（已知取舍）。手动是给人开的，硬夹目标速度手感会很怪，做 ATP 又超出当前范围。已把范围限制写进第十节 |
| P1-2 | 「复制到其他侧线」是假成功：先改本地再发包，客户端拿不到回执，非 OP 时服务端丢包 → 本地显示成功、重拉数据后静默回滚 | **已修**：按钮文案改成「已提交 N 条，等待服务端确认」，多包时报包数；README 第二节新增「点完到底算不算改成了」小节写明生效前提（单人 / 主机 / OP） |
| P2-1 | 制动目标又跟占用区间最低限速取了一次 `min` ⇒ 车尾实际也参与了降速，与 README「降速由车头控制」的概括不符 | **已同步文档**：第一节表格改写成实际行为，并附「想彻底只看车头就删掉那个 `if`」的做法 |
| P2-2 | 输入框 filter 吞小数点（只能整数），但格式化留了小数分支 | 记录在案，未改（不影响 99% 的整数用法） |
| P2-3 | 固定布局无滚动，GUI scale 大 / 窗口矮时第 3/4 行会画出屏幕 | 记录在案 |
| P2-4 | Config 无热重载，面板服改一个布尔要重启整服 | 记录在案（低成本可做，未动） |
| P3-1~4 | 每 tick 二分+扫描开销、`breaks` 拦不住 4.1.x、无 lang 文件、`BATCH_SIZE=40` 无依据 | 记录在案 |

`CODE_REVIEW.md` 末尾还有一份「还能加些什么」的四档清单（ATP / 惰行 coasting / 起停附加时分 /
晚点归因日志 / 调试 HUD 等），需要的时候按那个顺序做。

## 九、部署

产物拷到 `mods/` 即可（客户端 + 服务端都要装，且版本与加载器要对上）。

```bash
# 单目标（开发）
python build.py                                   # 产出 build/libs/MTR4Advanced-1.0.1.jar
python tools/check_targets.py                     # 校验注入点，必须 ALL OK
cp build/libs/MTR4Advanced-1.0.1.jar "<游戏目录>/mods/"

# 全矩阵（发布）：10 个目标分别编译、分别校验注入点
python build_release.py --jars _mtr_jars --out ../releases
```

输出（`releases/`）：

```
MTR4Advanced-1.0.1-1.18.2Fabric.jar   MTR4Advanced-1.0.1-1.18.2Forge.jar
MTR4Advanced-1.0.1-1.19.2Fabric.jar   MTR4Advanced-1.0.1-1.19.2Forge.jar
MTR4Advanced-1.0.1-1.19.4Fabric.jar   MTR4Advanced-1.0.1-1.19.4Forge.jar
MTR4Advanced-1.0.1-1.20.1Fabric.jar   MTR4Advanced-1.0.1-1.20.1Forge.jar
MTR4Advanced-1.0.1-1.20.4Fabric.jar   MTR4Advanced-1.0.1-1.20.4Forge.jar
```

两个脚本都 **不会自动部署** —— 改完记得手动拷，否则游戏里跑的还是旧版。

### 平台差异（`build_release.py` 会自动处理）

| | Fabric | Forge |
| --- | --- | --- |
| 元数据 | `fabric.mod.json` | `META-INF/mods.toml` |
| mixin 注册 | `fabric.mod.json` 的 `mixins` 数组 | jar **MANIFEST** 的 `MixinConfigs` 属性（Forge 靠它加载 mixin 配置） |
| mod id | `mtr4-advanced` | `mtr4advanced`（Forge 的 modId 不允许连字符） |
| 映射层 | Minecraft 类型藏在占位符 `class_339` 后面 | 直接用真实类名（`net.minecraft.client.gui.components.AbstractWidget`），所以编译期 stub 必须带正确继承层级 |

## 十、已知限制

- **手动驾驶（`manual`）模式下本模组整体不生效**（已知取舍，不改）。
  `Vehicle#simulateMoving` 的控制流是：
  `isClientside` 分支 → 制动距离计算 → **`isCurrentlyManual()` 分支** → ATO 自动路径。
  手动分支排在 ATO **之前**，而且结尾 `goto` 直接跳过 ATO 那一段 ——
  本模组重定向的两个取值点（`Siding#getUpcomingSlowerSpeed`、`PathData#getSpeedLimitMetersPerMillisecond`）
  都在被跳过的 ATO 路径里，所以手动时目标速度走的是 `VehicleExtraData#getMaxManualSpeed()`，
  **车尾控制提速 / 列车最高时速 / 跟随晚点浮动三项全部不参与**。
  想让手动也受限，得在手动分支另加注入点（可以做「超速才强制制动」的 ATP 语义，也可以直接夹目标速度），
  目前没这个计划 —— 手动本来就是给人开的，硬夹目标速度手感会很怪。
- **时刻表推算与 manual 无关**：时刻表永远按 ATO 自动运行的口径算（占用区间最低限速再套 cap），手动开的车不会改写它
- 侧线改完最高时速 / 车型编组后，需要等列车重新生成路径（或重启存档）才会重算时刻表
- 时刻表只按侧线**当前**配置的车型长度算；中途换车型不会回溯修改已生成的计划时间
- 只针对 MTR 4.0.4 的字节码做过校验，换 MTR 版本需要重新用 `tools/check_targets.py` 确认注入点

## 十一、文档与许可

| 文档 | 内容 |
| --- | --- |
| [wiki/项目介绍.md](wiki/项目介绍.md) | 项目介绍：解决什么问题、技术看点、适用人群、English Introduction |
| [wiki/Home.md](wiki/Home.md) | 文档入口：特性总览、30 秒上手、与原版差异一览 |
| [wiki/快速开始.md](wiki/快速开始.md) | 安装、验证生效、从旧版迁移、卸载、上架前仓库卫生 |
| [wiki/工作原理.md](wiki/工作原理.md) | 占用区间、两个 `@Redirect`、公式推导、时刻表推算、数据流 |
| [wiki/配置参考.md](wiki/配置参考.md) | 四个配置项逐个说明、配置文件解析规则的坑 |
| [wiki/侧线界面.md](wiki/侧线界面.md) | 四行控件、相对布局、「复制到车场」的真实语义 |
| [wiki/构建与工具.md](wiki/构建与工具.md) | `build.py` / `check_targets.py` 的原理、预期输出与排错 |
| [wiki/开发指南.md](wiki/开发指南.md) | 注入点总表、访问器清单、加新字段的完整清单、踩坑 |
| [wiki/常见问题.md](wiki/常见问题.md) | 症状 → 原因 → 处理 |
| [wiki/已知限制与路线图.md](wiki/已知限制与路线图.md) | 明确不做 / 记录在案 / 行为边界 / 路线图 |
| [wiki/变更日志.md](wiki/变更日志.md) | 1.0.0、改名记录、四轮审查时间线 |

`wiki/` 目录既可以直接在仓库里浏览，也可以整体拷进 GitHub Wiki（`<repo>.wiki.git`）。

许可：**MIT**，见 [LICENSE](LICENSE)（与 `fabric.mod.json` 的 `license` 字段一致）。
