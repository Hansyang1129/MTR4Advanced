# MTR4 Advanced 代码审查

审查范围：`src/main/java/cn/nansai/mtrspeed/**`（9 个类）、`build.py`、`tools/check_targets.py`、
`fabric.mod.json`、`mtr4-advanced.mixins.json`。
对照物：`[A]MTR-fabric-4.0.4.jar` 的字节码（javap 反汇编）。

结论：整体质量偏上。注入点选得准（`PathData#getSpeedLimitMetersPerMillisecond` 在
`simulateMoving` 里只有 1 处调用，无 ordinal 歧义）、坐标口径（车头 = `railProgress`、
占用区间 = `[railProgress - 车长, railProgress]`）与 MTR 一致、`unpackValue` 对缺失 key 不调
callback 所以单端安装/卸载不会清零字段。问题集中在**配置初始化的并发可见性**、
**一处 UI 状态丢失**、**一处文档与实现不符**。

> 以下是**首轮（18:41）**的审查结论。18:50–18:54 的改动已修掉其中大部分，
> **最新逐条状态见紧接着的「复审状态」节**。

---

## 复审状态（2026-09-19，三轮累计）

时间线：18:50–18:54（修首审问题）→ 19:28–19:32（修复审 N1–N5）→ 22:20–22:22（修 N6–N8）。
**首审 15 项 + 复审新增 8 项，共 23 项均已处理完毕**；唯一遗留是 `fabric.mod.json` 的 `icon`（可选，未加）。

| # | 问题 | 状态 | 说明 |
| --- | --- | --- | --- |
| P0-1 | `Config` 初始化竞态 | **已修** | `volatile` + 双重检查 + `loaded = true` 移到末尾 |
| P1-2 | 硬顶公式与 README 不符 | **已修**（文档侧，19:30） | 确认保留实现；README 第二节重写为准确公式表并写明理由 |
| P1-3 | 复选框 resize 丢状态 | **已修** | `setChecked` 移进 `== null` 分支 |
| P1-4 | 复制按钮单包无分批 | **已修** | 新增 `MTRSPEED$BATCH_SIZE = 40` |
| P2-5 | 循环内重复算车长 | **已修** | 新增 `mtrspeed$capturedVehicleLength` |
| P2-6 | `vehicleExtraData` 无 null 防御 | **已修** | |
| P2-7 | `Math.max(0, NaN)` 穿透 | **已修** | 显式挡 NaN / Infinity |
| P2-8 | 输入框截断小数 | **已修** | 新增 `mtrspeed$format` |
| P2-9 | `getSpeed` 死代码 | **已修** | 已删除 |
| P2-10 | `Config` 静默吞异常 | **已修** | 新增 `warn()` 走 `System.err` |
| P2-11 | README「三行」应为四行 | **已修**（19:12） | 改为「四行配置区」 |
| P2-12 | `row1Y` 兜底魔数 238 | **已修** | 补了来源注释 |
| P2-13 | `find_mtr_jar` 匹配过宽 | **已修** | 精确版本优先 + `sorted()` + 回退告警 |
| P2-14 | `check_targets.py` 不校验调用点 | **已修** | 新增 `call_site_count`，实测 4 处均为 1 |
| P2-15 | `fabric.mod.json` 缺 `breaks` | **已修**（19:30） | `breaks` 收窄为 `">=4.0.5 <4.0.6"`；`icon` 仍未加 |

**第二轮（19:28–19:32 改动 → 22:15 复审）**

| 条目 | 状态 | 说明 |
| --- | --- | --- |
| **P1-2** 硬顶公式 | **已修（文档侧）** | 保留实现；README 第二节重写为准确公式表，并写明替代改法与保留理由 |
| **N1** `check_targets.py` 打印类名 | **已修** | 改用 `owner`，现输出 `PathData#getSpeedLimitMetersPerMillisecond` |
| **N2** README 示例过时 | **已修** | 换成分批版 + 「为什么要分批」 |
| **N3** `breaks` 区间过宽 | **已修** | 收窄为 `">=4.0.5 <4.0.6"` |
| **N4** 部分成功提示 | **已修** | 新增 `packets`，多包时显示「已复制到 N 条（分 X 包发送）」 |
| **N5** `build.py` pattern 重复 | **已修** | 合并成一套；由此暴露的跨平台问题见 N6（22:20 已修） |
| **N6** Linux 上找不到 MTR jar | **已修**（22:20） | 改用 glob 字符类 `*[Mm][Tt][Rr]*`，各平台行为一致 |
| **N7** README 结论自相矛盾 | **已修**（22:22） | 与公式表口径对齐 |
| **N8** README 项数数错 | **已修**（22:22） | 「8 项」→「9 项」 |

第一轮复跑验证：

- `python build.py` → `compilation ok (round 3, 7 stub(s))` / `built: build/libs/MTR4Advanced-1.0.0.jar`
- `python tools/check_targets.py` → **18 项全 OK**，含新增的调用点检查（4 处目标方法在宿主方法内均为 1 个调用点）

第二轮（22:15）复跑验证：

- `python tools/check_targets.py` → **18 项全 OK**；调用点提示已正确显示 `PathData#getSpeedLimitMetersPerMillisecond`
- **部署已同步 ✓**：`build/libs/MTR4Advanced-1.0.0.jar` 与游戏
  `F:\mc\.minecraft\versions\Qinglan-2nd\mods\MTR4Advanced-1.0.0.jar` **md5 完全一致**
  （`980028cf…`，16111 B，19:30:25 / 19:30:26）。
  第一轮复审时标记的「部署是 18:05 旧 jar」已解决。
- 旧的 `mtr-train-speed-1.0.0.jar` 已不在 mods 目录 ✓

---

## P0 — 会导致配置不生效

### 1. 【已修】`Config.load()` 的 `loaded` 标志设置在字段赋值之前，且无内存屏障

`Config.java:34-53`

```java
public static void load() {
    if (loaded) {
        return;
    }
    loaded = true;          // ← 在读文件之前就置位
    File file = ...;
    if (file.exists()) {
        ... tailControlledAcceleration = getBoolean(...);   // 4 个字段在这里才被赋值
    }
}
```

两个独立缺陷叠加：

1. **顺序错**：`loaded = true` 在真正读文件之前。线程 B 在 A 读完文件的窗口期内调用
   `load()`，会看到 `loaded == true` 直接返回，拿到的是**默认值**。
2. **没有可见性保证**：`loaded` 和 4 个配置字段都是普通静态字段，没有 `volatile`。
   JMM 下 B 线程没有义务看到 A 写入的值。

实际影响：`SpeedLogic.getAccelerationTarget` 每个 tick 都会调用 `Config.load()`（服务端线程），
`SidingScreenMixin` 也会（客户端渲染线程）。只要渲染线程抢先触发首次加载，服务端就可能
**永久**运行在默认配置上 —— 用户在 `mtr4-advanced.properties` 里写的
`tail-controlled-acceleration=false` 不生效，且没有任何报错。

单人游戏（客户端 + 集成服务端同进程、不同线程）必然踩中；专用服务器概率低但非零。

**修法**（三步都做）：

```java
private static volatile boolean loaded = false;

public static void load() {
    if (loaded) {
        return;
    }
    synchronized (Config.class) {
        if (loaded) {
            return;
        }
        File file = new File(new File(System.getProperty("user.dir"), "config"), "mtr4-advanced.properties");
        if (file.exists()) {
            ...读取...
        } else {
            writeDefault(file);
        }
        loaded = true;      // ← 移到末尾，且此时字段已全部赋值
    }
}
```

4 个配置字段本身不必加 `volatile`：只要 `loaded` 是 volatile，写 `loaded = true` 之前的
所有写操作对读到 `loaded == true` 的线程都可见（happens-before）。

---

## P1

### 2. 【未修】「硬顶」模式的实际公式与 README 承诺不符

`SpeedLogic.java:128-139`

```java
private static double applyHardCap(Vehicle vehicle, double limit) {
    final double cap = getTrainMaxSpeedMetersPerMillisecond(vehicle);
    if (cap == Double.MAX_VALUE) { return limit; }
    if (shouldFollowDeviation(vehicle)) { return Math.min(limit, cap); }
    final double adjustment = ...getDeviationSpeedAdjustment();
    final double safeAdjustment = adjustment > 0 ? adjustment : 1.0;
    return Math.min(limit, cap / safeAdjustment);
}
```

返回值会在上游被乘上 `deviationSpeedAdjustment`（已从字节码确认，
`Vehicle#simulateMoving` offset 434-441 与 465-473 两条路径都有 `dmul`）。所以：

| | 实际最终目标速度 | README 第 84-85 行的说法 |
| --- | --- | --- |
| 不勾（硬顶） | `min(线路限速 × 增益, cap)` | `min(线路限速, cap)` |
| 勾选（浮动） | `min(线路限速 × 增益, cap × 增益)` | `min(线路限速, cap) × 增益` |

浮动那一行实现与文档一致。**硬顶那一行不一致**：实现只保证「`cap` 不被增益放大」，
但**线路限速仍会被晚点增益放大 25%**。

可复现场景：侧线列车最高时速填 80，线路限速 40，`deviationSpeedAdjustment = 1.25`。
`min(40, 80/1.25=64) × 1.25 = 40 × 1.25 = 50`。列车跑 50，而不是 README 承诺的 40。

这不是「代码写错了」—— 作者注释（`SpeedLogic.java:122-127`）说明意图确实是「抵消增益对 cap 的作用」。
但如果你的本意是 README 那句字面语义，把最后一行改成先夹后除即可：

```java
return Math.min(limit, cap) / safeAdjustment;   // 最终 = min(线路限速, cap)，彻底禁掉超速
```

**这个需要你决策**，我不替你改。要「不勾就是不超速」选新写法，要「不影响 MTR 原版晚点追赶、
只给列车加个不受增益影响的天花板」保留现状（然后修 README）。

> **复审（19:07）：确认不修，保持现状。** 用户明确了设计意图就是**当前实现**：
> 硬顶模式只负责「列车最高时速」这个天花板不被晚点增益放大，
> 线路限速照旧走 MTR 原版的晚点追赶（最多 +25%）。
> 因此这不算功能 bug —— 只是 README 第 84 行的等式漏了「× 增益」这个因子，
> 属于**措辞与实现有出入**。用户选择两边都不动，已按此归档。
> （曾按字面语义改过一版 `min(limit, cap) / safeAdjustment`，已回滚。）
>
> **复审（22:15）：已按「保留实现、修正文档」处理完毕。** README 第二节重写为公式表 ——
> 实现 `min(限速, cap / A) × A` → 结果 `min(限速 × A, cap)`；并补了数值例子
> （限速 40 / cap 80 / A=1.25 → 50）与替代改法 `Math.min(limit, cap) / safeAdjustment` 及保留理由。
> 代码未动，`SpeedLogic.java:141` 仍是 `Math.min(limit, cap / safeAdjustment)` ✓。
> 顺带产生的措辞问题见 N7。

### 3. 【已修】复选框状态在窗口 resize 后丢失

`SidingScreenMixin.java:114-124`

```java
if (mtrspeed$checkboxFollowDeviation == null) {
    mtrspeed$checkboxFollowDeviation = new CheckboxWidgetExtension(..., true, value -> {});
}
mtrspeed$checkboxFollowDeviation.setChecked(          // ← 每次 init2 都无条件覆盖
        siding != null && ((SidingMaxSpeedAccess) (Object) siding).mtrspeed$getMaxSpeedFollowsDeviation()
);
```

Minecraft 的窗口 resize 会**复用同一个 Screen 实例**重新调 `init()` → `init2()`。正因如此，
作者对输入框做了保护（`SidingScreenMixin.java:106`）：

```java
if (mtrspeed$textFieldMaxSpeed.getText2().isEmpty()) {   // 只在空的时候填，保住用户输入
    mtrspeed$textFieldMaxSpeed.setText2(...);
}
```

复选框缺这一层保护：`setChecked` 在每次 `init2` 都从 `siding` 重读，而 `siding` 的值要等
`onClose2` 才写回。**用户勾上「跟随晚点浮动」→ 拖动窗口大小 → 勾选被静默清掉。**

修法（与输入框对齐，只在首次创建时读一次）：

```java
if (mtrspeed$checkboxFollowDeviation == null) {
    mtrspeed$checkboxFollowDeviation = new CheckboxWidgetExtension(..., true, value -> {});
    mtrspeed$checkboxFollowDeviation.setChecked(
            siding != null && ((SidingMaxSpeedAccess) (Object) siding).mtrspeed$getMaxSpeedFollowsDeviation()
    );
}
mtrspeed$checkboxFollowDeviation.setX2(MTRSPEED$LEFT);
...
```

> **复审：已修。** `setChecked(...)` 已移进 `if (mtrspeed$checkboxFollowDeviation == null)` 分支，
> 并补了「resize 会复用 Screen 实例重跑 `init2`」的注释，与输入框的保护口径对齐。

### 4. 【已修】「复制到车场其他侧线」把所有侧线塞进同一个包，无分批

`SidingScreenMixin.java:249-267`

```java
final UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
request.addSiding(siding);
for (Object entry : depot.savedRails) { ...; request.addSiding((Siding) entry); count++; }
InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
```

一条 `Siding` 的序列化包含 `pathDistances` / `timeSegments` 等数组，体积随线路长度增长。
Fabric 自定义 payload 有单包上限（1.20.1 下 1 MiB）。大车场（数百条侧线）下如果超限，
**整个包会被拒，连玩家当前改的那条也保存不了** —— 表现为「点复制没反应 / 侧线设置丢失」。

未实测，标注为**待验证风险**。稳妥做法是分批（例如每 50 条一个包）或只提交字段有变化的侧线。
要实测的话，比较省事的办法是往 `mtrspeed$copyToOtherSidingsInDepot` 里临时打印
`request` 序列化后的字节长度。

> **复审：已修。** 现改为 `MTRSPEED$BATCH_SIZE = 40` 分批发送：`pending` 从 1（含自身）起算，
> 满 40 即发包并重置，循环结束补发剩余。计数逻辑核对无误（40 条其他侧线 → 40+1 正好一包；
> 100 条 → 40 / 40 / 21 三包；0 条 → 只发自身一包）。行大小仍**未实测**，副作用见 N4。

---

## P2 — 质量与可维护性

> **复审状态**：本组全部已修（P2-11 于 19:12 补修；P2-15 的 `breaks` 区间于 19:30 收窄为
> `">=4.0.5 <4.0.6"`）。仅 `icon` 未加（可选）。逐条状态见顶部汇总表。

| # | 位置 | 问题 | 建议 |
| --- | --- | --- | --- |
| 5 | `SidingTimeSegmentsMixin.java:85` | `Siding.getTotalVehicleLength(getVehicleCars())` 在**每个轨段**的 `getSpeedLimitMetersPerMillisecond` 回调里都算一次；原版在方法开头只算一次（字节码 offset 142）。长路径下是 O(n) 次重复遍历 | 缓存进 `@Unique` 字段，在捕获 `capturedPath` 时一并刷新 |
| 6 | `SpeedLogic.java:84` | `vehicle.vehicleExtraData.immutablePath` 未判 `vehicleExtraData == null`。`immutablePath` 本身是 `public final`（不会是 null），但 `vehicleExtraData` 没有保证 | 加 `if (vehicle.vehicleExtraData == null) return fallback;`。当前调用路径安全，属于防御性加固 |
| 7 | `SidingMixin.java:36-38` | `Math.max(0, NaN)` 返回 `NaN`，会穿透 setter | 显式判 `Double.isNaN`。当前被输入框的 `\D` 正则挡住，但网络/配置来源没有防护 |
| 8 | `SidingScreenMixin.java:107` | `String.valueOf((long) current)` 对小数截断（80.9 → "80"），关闭时写回 80 | 用 `Math.round`，或允许一位小数 |
| 9 | `VehicleSchemaAccess.java:13-14` | `mtrspeed$getSpeed()` 无任何调用点 | 删除 |
| 10 | `Config.java:48, 69` | 读/写失败全部 `catch (Exception ignored)`，用户无法察觉 | 接 MTR 的 `LOGGER` 打一行 warn（`org.mtr.core.Main.LOGGER`） |
| 11 | `README.md:178` | 文件结构注释写「侧线界面**三**行配置区」，正文第 45-52 行和实现都是**四**行 | 改注释 |
| 12 | `SidingScreenMixin.java:197` | 兜底坐标 `238 + ROW_HEIGHT + TOP_GAP` 是魔数，来源不明 | 注释写清依据，或改为遍历 `children` 取最大 `y + height` |
| 13 | `build.py:47-51` | fallback pattern `*mtr*.jar` 过宽，工作区同时存在 4.0.4 和 4.0.5 时取 `hits[0]`，顺序依赖文件系统 | 精确优先 + 结果排序；或强制要求 `MTR_JAR` |
| 14 | `tools/check_targets.py:96-114` | 只校验「目标方法存在于目标类」，不校验「该方法在宿主方法内被调用几次」。MTR 改版后如果新增调用点，`@Redirect` 会静默命中错误位置 | 增加调用点计数校验（`javap -c` 数 `invoke*` 出现次数，与预期 `ordinal` 比对） |
| 15 | `fabric.mod.json:16-20` | 只声明 `mtr: ">=4.0.4"`。README 明确说 4.0.5 有幽灵停车回归 | 考虑 `"breaks": {"mtr": ">=4.0.5 <4.1.0"}`；另外可补 `icon` |

---

## 复审新出现的问题

> **N1–N8 现已全部修完**（N1–N5 见 19:30 改动，N6–N8 见 22:20–22:22 改动）。
> 本节保留作为问题记录，不再有未处理项。

### N1. `check_targets.py` 调用点提示里的类名写错了（仅日志，不影响校验）

`tools/check_targets.py:153`

```python
sites = call_site_count(args.javap, jar, target, host_method, method)
...
print("  %s  调用点 %d 处（%s#%s）" % ("OK  " if unique else "WARN", sites, target, method))
```

`target` 是**宿主类**（`org.mtr.core.data.Vehicle` / `Siding`），`method` 是**被调用方法**
（`getSpeedLimitMetersPerMillisecond`）。于是实际输出成：

```
OK    调用点 1 处（org.mtr.core.data.Vehicle#getSpeedLimitMetersPerMillisecond）
```

但 `getSpeedLimitMetersPerMillisecond` 是 `PathData` 的方法，不是 `Vehicle` 的 —— 这个组合不存在，
读日志的人会以为是 Vehicle 自己的方法。

`call_site_count(..., target, ...)` 那一处传 `target` 是**对的**（要在宿主方法体内数调用），
只有 print 该换成同分支里已经解析出来的 `owner`：

```python
print("  %s  调用点 %d 处（%s#%s）" % ("OK  " if unique else "WARN", sites, owner, method))
```

### N2. README 的同步示例代码已过时

`README.md:67-72` 还是改动前的单包写法：

```java
UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
request.addSiding(siding);            // 自己
for (Siding other : depot.savedRails) { ...; request.addSiding(other); }
InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
```

实际代码已改成 `MTRSPEED$BATCH_SIZE = 40` 分批 + 抽出的 `mtrspeed$sendUpdate(request)`。
建议同步更新，并把「为什么要分批」写进去（Fabric payload 单包上限）。

> **19:12 已修**：README 的示例代码已换成含 `pending` / `MTRSPEED$BATCH_SIZE` 的分批版本，
> 并补了「为什么要分批」段落。

### N3. `fabric.mod.json` 新增的 `breaks` 会让游戏直接拒绝启动

`src/main/resources/fabric.mod.json:21-23`

```json
"breaks": { "mtr": ">=4.0.5 <4.1.0" }
```

Fabric 的 `breaks` 是**硬冲突**语义：命中时加载器直接报错、**游戏起不来**，不是警告。两点要注意：

- 区间上界 `4.1.0` 很宽 —— 如果哪天出了修好幽灵停车的 4.0.6，用户会被一起拦住。
- 想在 4.0.5 上做对照测试（验证幽灵停车到底修没修）时，会被硬拦。

只是想避开 4.0.5 的话，收窄成 `">=4.0.5 <4.0.6"` 更稳。

### N4. 分批发送带来「部分成功」的中间状态（设计代价）

第一批成功、第二批超时/被拒时，车场里会出现「一部分侧线改了、一部分没改」。
比整包被拒好得多，但按钮文案只报总数（"已复制到 N 条侧线"），不区分发了几包、成功几包。
如果要更稳，可以按包统计成功数再提示，或在失败时提示重试。当前实现不算错，只是不够精确。

### N5. `build.py` 的 pattern 有重复项（nit）

`build.py:49-50` 的 `*MTR*4.0.4*.jar` 与 `*mtr*4.0.4*.jar` 在 Windows 上等价（glob 不区分大小写），
第 53-54 行同理。无害，只是可以合并。→ **已修（19:30）**：合并成一套，删掉了小写 pattern。

### N6. 【已修 22:20】`build.py` 合并 pattern 后，Linux 上会找不到 MTR jar

`build.py:43-62`（19:29 改动）

```python
# Windows 的 glob 不区分大小写，MTR / mtr 两套 pattern 等价，留一套即可。
patterns = [
    os.path.join(WORKSPACE, "*MTR*4.0.4*.jar"),
    os.path.join(ROOT, "libs", "*MTR*4.0.4*.jar"),
    os.path.join(WORKSPACE, "*MTR*.jar"),
    os.path.join(ROOT, "libs", "*MTR*.jar"),
]
```

注释里的理由**只对 Windows 成立**。而 `build.py` 的 docstring 第一行写的是
「离线构建脚本（Windows / Linux 通用）」，README 也没限定平台。

**Linux / macOS 的 `glob` 区分大小写**：MTR 官方 release 的文件名是小写
（`mtr-fabric-4.0.4.jar`），四个 pattern 全都匹配不到 → `find_mtr_jar()` 返回 `None`
→ 直接打印「找不到 MTR jar」并退出。Windows 上无感（本机文件名是 `[A]MTR-fabric-4.0.4.jar`，
大写 MTR，能命中），但 Linux / WSL / CI 会构建失败。

修法（二选一）：

```python
# 方案 A：小写 pattern 加回来。Windows 上冗余无害，Linux 上必需
os.path.join(WORKSPACE, "*MTR*4.0.4*.jar"),
os.path.join(WORKSPACE, "*mtr*4.0.4*.jar"),
...

# 方案 B：绕开 glob 的大小写差异（更彻底）
def _match(dir_, needle):
    if not os.path.isdir(dir_):
        return []
    return [os.path.join(dir_, n) for n in os.listdir(dir_)
            if needle in n.lower() and n.lower().endswith(".jar")]
```

> **22:20 已修 —— 实际采用方案 C（glob 字符类）**：一套 pattern 覆盖所有大小写组合，
> 各平台行为完全一致，不用再维护两套。
>
> ```python
> os.path.join(WORKSPACE, "*[Mm][Tt][Rr]*4.0.4*.jar"),
> os.path.join(ROOT, "libs", "*[Mm][Tt][Rr]*4.0.4*.jar"),
> os.path.join(WORKSPACE, "*[Mm][Tt][Rr]*.jar"),
> os.path.join(ROOT, "libs", "*[Mm][Tt][Rr]*.jar"),
> ```
>
> 实测（用 `fnmatch.fnmatchcase` 绕开 Windows 的 normcase，等价于 Linux 的区分大小写行为）：
>
> | 文件名 | 新 `*[Mm][Tt][Rr]*4.0.4*.jar` | 旧 `*MTR*4.0.4*.jar` |
> | --- | --- | --- |
> | `[A]MTR-fabric-4.0.4.jar` | HIT | HIT |
> | `mtr-fabric-4.0.4.jar`（官方 release 名） | **HIT** | **MISS** ← 就是 N6 |
> | `Mtr-fabric-4.0.4.jar` | **HIT** | MISS |
> | `mtr-fabric-4.0.5.jar` | MISS（正确，会落到兜底 pattern） | MISS |
> | `mixin-0.8.7.jar` | MISS（另有 `endswith` 双保险） | MISS |
>
> 本机 Windows 的实际 `glob` 也复测通过：命中 `[A]MTR-fabric-4.0.4.jar` ✓
> 只改了 `build.py` 的查找逻辑，**不涉及 class 产物，无需重新构建**。

### N7. 【已修 22:22】README 的结论与上一行自相矛盾

`README.md:104`（19:32 新增）

> 也就是说**两种模式下「列车最高时速」都不是字面意义上的硬上限**，差别只在于 `cap` 本身跟不跟着增益上浮：

但同一张表第 101 行写的是：不勾时「`cap` **绝对不被突破**」。两处冲突。

实际语义：**不勾时 `cap` 恰恰就是字面的硬上限**（`min(…, cap)`，无论增益多大都不会超），
只有**勾选**时 `cap` 才会被一起放大到 1.25 倍。建议改成：

> 两种模式的**结果公式只差在 `cap` 跟不跟着增益上浮** —— 不勾时 `cap` 是字面的硬上限，
> 勾选时它也能被放大到 1.25 倍。

> **22:22 已修**：采纳上述措辞，README:104 现已与 101/102 行的公式表一致。

### N8. 【已修 22:22】README 项数数错

`README.md:240`（19:32 新增）

> - **P2 其余 8 项**全部已改：车长重复计算、`vehicleExtraData` null 防御、`NaN` 穿透 setter、
>   小数截断、无用 accessor、配置读写静默失败、README 行数不符、兜底魔数注释、`build.py` 找 jar 顺序依赖。

冒号后面列了 **9 项**，写的是「8 项」。改成「9 项」即可。

> **22:22 已修**：改为「P2 其余 9 项」。已核对，冒号后确实是 9 项。

### N9. 【已修 22:29】`check_targets.py` 在本机开箱即崩：PATH 上没有 `javap`

复跑 N6 的构建时发现。`tools/check_targets.py:100` 的 `--javap` 默认值是裸的 `"javap"`，
而本机：

- `JAVA_HOME` **未设置**
- PATH 里只有一个 `C:\Program Files\Common Files\Oracle\Java\javapath` —— 里面只有
  `java.exe` / `javac.exe` / `javaw.exe`，**没有 `javap.exe`**

→ `python tools/check_targets.py` 直接 `FileNotFoundError: [WinError 2]`。
（之前能跑是因为我一直手动传 `--javap C:\Program Files\Java\jdk-21\bin\javap.exe`，
这个前提没写进任何文档，等于脚本对外不可用。）

修法：新增 `resolve_tool()`，优先级 **显式参数 > PATH > `JAVA_HOME/bin` > 常见 JDK 安装目录**。

> **坑中坑**：JDK 目录按字符串反序排会把 `jdk1.8.0_202` 排到 `jdk-21` **前面**
> （`1` > `-`）。javap 8 读不了 `--release 17` 编出来的 class file，**不报错、直接返回空**，
> 于是脚本会打印 `ALL OK (0 项检查)` —— 全绿但一项都没查，比崩掉更危险。
> → 用 `jdk_version_of()` 按真实版本号降序（`jdk1.8.0_202` → 8）。
>
> 修完实测：`picked: C:\Program Files\Java\jdk-21\bin\javap.exe`，18 项全 OK。

顺带确认：`build.py` 用的裸 `javac` 在本机**能**跑（javapath 里有），暂不改；
但它有同样的可移植性隐患，换机器若报 `javac 不是内部或外部命令`，照 N9 的思路加探测即可。

---

## 已验证正确的部分（不要重复怀疑）

1. **单端安装 / 卸载不会清零字段。** `ReaderBase.unpackValue` 反汇编为
   `if (value == null) return; consumer.accept(value);` —— key 缺失时 callback 不被调用，
   `SidingMixin.mtrspeed$updateData` 的 TAIL 注入不会把已有值覆盖成 0。
2. **`@Redirect` 无 ordinal 歧义。** `simulateMoving` 里 `PathData#getSpeedLimitMetersPerMillisecond`
   和 `Siding#getUpcomingSlowerSpeed` 各只出现 1 次；
   `generatePathDistancesAndTimeSegments` 里也各只有 1 次。
3. **`SidingTimeSegmentsMixin` 的 `capturedRailProgress` 不存在跨迭代脏读。**
   字节码显示 `getSpeedLimitMetersPerMillisecond` 的调用点在
   `getUpcomingSlowerSpeed` 之后（offset 777 → 819），且到达 819 的唯一路径必经 777，
   所以每次使用前都必然刷新。之前担心的「跳过捕获直接用旧值」不成立。
4. **减速目标的 cap 处理与上游 `× adjustment` 一致。** `min(upcoming, cap/adj) × adj = min(upcoming×adj, cap)`。
5. **`deviationSpeedAdjustment` 恒 ≥ 1.0**（无晚点走 `putfield 1.0` 分支），
   `safeAdjustment` 的 `> 0` 兜底不会误伤。
6. **二分 + 区间扫描的边界正确。** `indexOfStartDistance` 返回「最后一个 `startDistance <= value`」，
   `tail < 0`（车尾未入路径）时回落到 `startIndex = 0`，不会越界。
7. **`@Mixin` 到 `final class Siding` 合法**（字节码合并，不涉及继承）。
8. **`SidingSchemaMixin` 的 `instanceof Siding` 过滤有效** —— `Siding extends SidingSchema`，
   非 Siding 的 SidingSchema 实例不会被写入这两个字段。
9. **`zipfile` 打包的条目名正确** —— CPython 的 `ZipInfo.from_file` 在 `os.sep == '\\'` 时会把
   反斜杠换成 `/`，不会产出 `cn\nansai\...` 这种坏路径。
10. **`javac -J-Duser.language=en`** 是在为「class file for X not found」的正则匹配兜底，
    换 locale 也不会破 —— 这个处理很到位。
11. **本轮（复审）复跑结果**：`python build.py` → `compilation ok (round 3, 7 stub(s))`；
    `python tools/check_targets.py --javap <jdk17>javap.exe` → 18 项全 OK，
    其中新增的调用点检查 4/4 通过。
12. **新加的 `mtrspeed$capturedVehicleLength` 哨兵 `-1` 不会误用** ——
    到达 `mtrspeed$capRailSpeedLimit` 的唯一路径必经 `mtrspeed$captureAndCapUpcomingSlowerSpeed`
    （字节码 777 → 819），每轮循环都会刷新，不存在「用上一轮车长」的问题。
13. **README（19:32 版）新增的公式与数值例子核对无误** ——
    实现 `min(限速, cap / A) × A` → 最终 `min(限速 × A, cap)` ✓；
    例：限速 40 / cap 80 / A=1.25 → `min(40×1.25, 80) = 50` ✓；
    限速 100 / cap 80 → `min(100, 80) × 1.25 = 100` ✓；
    时刻表那条（无增益）`min(占用区间最低限速, cap)` ✓ 与 `mtrspeed$maxSpeedMetersPerMillisecond` 一致。
14. **分批计数的 `packets` 与 `count` 语义正确** —— `count` 只数「其他侧线」（不含自身），
    `packets` 数实际发出的包数；只有自身时 `packets = 1`、文案回落到不带「分 X 包」的短句 ✓。

---

# 第四轮审查（2026-09-28 · 字节码实证 + 功能想象）

工具：`C:\Program Files\Java\jdk-21\bin\javap.exe` 对 `[A]MTR-fabric-4.0.4.jar` 反编译；
`tools/check_targets.py` 在本轮实跑输出 **18/18 OK**（含 4 项调用点检查）。
本轮重点不是重复前三轮已确认的结论，而是**顺着数据流往下挖一层宿主实现**，找「静态阅读看不出来」的问题。

## 零、复跑与复验

| 项 | 结果 |
| --- | --- |
| `check_targets.py` 全量 | 18/18 OK，调用点 4/4 唯一 |
| `generatePathDistancesAndTimeSegments` 调用顺序 | `getUpcomingSlowerSpeed`(bc 777) 先于 `getSpeedLimitMetersPerMillisecond`(bc 819)，且到达 819 的唯一路径必经 777 ✓（复验第三轮第 3 条） |
| 局部变量表 `-l` | slot 9 = `railProgress`、slot 20 = `pathData(当前轨段)`、slot 2 = `totalVehicleLength` ⇒ `SidingTimeSegmentsMixin` 抓的车头里程/车长口径正确 ✓ |
| `simulateMoving` 乘 gain | bc 434-441（制动路径）与 bc 465-473（ATO 路径）都有 `dmul` ✓（复验第三轮第 4 条） |

---

## 一、新问题（按严重度）

### P1-1【新 · 已决策：不改，缩小承诺】手动驾驶分支完全绕过本模组 —— 所有限速在 manual 模式下失效

> **2026-09-28 决策**：指挥官明确「手动绕过就绕过吧」，ATP 不做。
> 按三条建议里的第 1 条处理 —— 代码不动，把适用范围写进 README 第十节
> （顺带把原来只提「最高时速不受限」的一句话扩写成「三项全部不参与 + 为什么」）。

`Vehicle#simulateMoving` 的控制流（javap 实测）：

| 字节码区间 | 分支 | 是否经过我们的注入点 |
| --- | --- | --- |
| 0–34 | `isClientside == true` → 直接用服务端下发的 `speedTarget/powerLevel`，`goto 515` | 不经过（正常，客户端只跟随） |
| 37–305 | 制动距离计算 + `atoOverride` | 部分 |
| **308–385** | **`if (isCurrentlyManual())`** → target 直接取 `VehicleExtraData.getMaxManualSpeed()`，`goto 488` | **不经过** |
| 388–488 | ATO 自动路径（我们 hook 的 `getUpcomingSlowerSpeed` / `getSpeedLimitMetersPerMillisecond` 在这） | 经过 ✓ |

manual 分支比 ATO 分支**靠前**，且结尾 `goto 488` 直接跳过 388 那一段。实测结论：

- 「车尾控制提速」失效
- 「列车最高时速」硬顶失效
- 「跟随晚点浮动」失效

即：**侧线一旦切成手动（或 `atoOverride` 的手动派生状态），本模组等于没装。**
这与 README 第一节的承诺不符 —— 那里表格没有限定模式。

处理建议（三选一，倾向第 2 个）：

1. 在 README 明确写「仅对 ATO 自动运行生效」，缩小承诺范围。
2. **ATP 语义**（推荐）：manual 下不限制「目标速度」（否则手感像开在棉花上），而是
    `@Redirect` 那个 `getMaxManualSpeed()` 的返回值保持原样，另外在 manual 分支之后
    做一次「当前速度 > cap 则强制置 -5 功率（制动）」的兜底。真实地铁本来就是
    「司机怎么推手柄都行，超速 ATP 自己会制动」。
3. 加 `apply-to-manual`（默认 false）配置，为 true 时连目标速度一起夹。

> 注意 1：这一条**不能**直接套 `applyHardCap`，manual 分支没有 `× deviationSpeedAdjustment`，
> 用了 `cap / adjustment` 会让手动车的目标速度凭空低一截。
> 注意 2：改这里等于新增注入点，`check_targets.py` 的 `MIXIN_TARGETS` 要同步。

### P1-2【新 · 已修】「复制到车场其他侧线」在非 OP 多人服是「假成功」

> **2026-09-28 已修**：采纳建议第 1 条（把语义说真）。
> - 代码 `SidingScreenMixin#mtrspeed$copyToOtherSidingsInDepot`：
>   `count == 0` → 「该车场没有其他侧线」；多包 → 「已提交 N 条（X 包）」；单包 → 「已提交 N 条，等待服务端确认」。
>   注释里写清了「为什么不能说『已复制』」。
> - README 第二节新增「点完到底算不算改成了」小节，列出单人/主机/OP vs 多人非 OP 的结果差异。
> - 复跑：`build.py` → `compilation ok (round 3)`；`check_targets.py` → 18/18 OK。
> - 建议第 2 条（客户端先判权限再禁用按钮）**未做** —— 需要额外引 Minecraft 权限 API，
>   成本比收益高；先靠文案 + 文档解决。

先给个**好消息**：本模组构造包的方式与 MTR 官方完全一致 —— `SidingScreen#onClose2` bc 303-333
也是 `new UpdateDataRequest(getDashboardInstance())` → `addSiding(...)` → `PacketUpdateData`
→ `sendPacketToServer`，**不需要额外 `addDepot`**，这点实现是对的，不用怀疑。

问题在回执：`UpdateDataRequest.update()` 返回 `UpdateDataResponse` 是**服务端**才走的 API，
客户端 `sendPacketToServer` 拿不到任何确认。所以：

| 场景 | 本地 UI | 服务端 |
| --- | --- | --- |
| 单人 / OP | 「已复制到 N 条」 | 已改 ✓ |
| 多人服非 OP（或没在 dashboard 编辑模式） | 「已复制到 N 条」 | **丢弃** ✗（本地已改，重进/重拉数据后回滚） |

本地对象被改了，界面立刻反映成新值，但服务端那边可能根本没落库。等玩家重进存档 / 重拉 dashboard 数据时会「静默回滚」—— 最难查的那类不一致。

建议优先级从高到低：

1. 文案改成「已提交 N 条，等待服务端确认」，先把语义说真；README 的「复制」小节补一句生效前提。
2. 客户端发之前先判一次权限 —— 反编译 MTR 里 dashboard 的编辑判定（通常是 op 等级 /
   `Permissions`/单人），拿不到就把按钮置灰并提示。
3. 发完后 n tick 再向服务端拉一次 siding 数据做 diff（成本最高，不推荐先做）。

### P2-1【新 · 已同步文档】`getDecelerationTarget` 里的车尾控制与 README 的「降速由车头控制」自相矛盾

> **2026-09-28 已同步文档**：README 第一节那个表格改写成实际行为（注明 `tail-controlled-acceleration`
> 同时作用在加速与制动两条目标上），并附「想彻底只看车头就删掉那个 `if`」的做法。代码未动。

`SpeedLogic.java:73-75`：

```java
if (Config.tailControlledAcceleration) {
    upcomingSlowerSpeed = Math.min(upcomingSlowerSpeed, getOccupiedMinimumSpeedLimit(vehicle, upcomingSlowerSpeed));
}
```

`upcomingSlowerSpeed` 是**基于车头**的前瞻制动曲线结果，这里再用「[车尾, 车头] 占用区间最低限速」
压一遍 = **让车尾参与了降速**，和 README 表格里「降速｜车头｜沿用原版前瞻制动曲线」的表述冲突。

运行时影响不大（车尾所处的里程必然在车头之后，压出来的值通常 ≤ 当前已经在跑的速度），
但在「车尾尚未驶出低速区 + 前方又出现更低限速」时会二次叠加，表现为**提前/过度制动**。

建议：拆成独立开关 `tail-controlled-deceleration`（默认 `false`），
README 表格改成按两个开关描述；或直接删掉这三行（语义更干净）。

### P2-2【新】UI 输入无法表达小数，但 `mtrspeed$format` 留了小数分支

`SidingScreenMixin.java:105`：`new TextFieldWidgetExtension(..., 4, TextCase.DEFAULT, "\\D", null)`
—— filter 把小数点也吞了 + 最多 4 位 ⇒ 只能输入 `0–9999` 的整数。

而 `mtrspeed$format`（:215-219）专门处理了小数（保留 3 位），注释还说「万一存过 80.5」。
结果是：**这个分支只有在别处写入小数值（API/旧数据）时才会亮一次，玩家一关界面必然被回写成整数 80。**

建议：要么把 filter 放宽成 `"[^0-9.]"` 并 sanitize（最多一个小数点，最多一位小数），要么删掉小数分支。
推荐前者 —— 现实里的车型确实有 80.5 这类值，而且成本只有几行。

### P2-3【新】布局没有溢出保护

`row1Y ≈ 262`、`row4 底部 ≈ 334`（锚点 `sliderDwellTimeSec` y=238+高 20 起算）。
MTR 的 `SidingScreen` 是**固定布局、不带滚动**，GUI scale 调大 / 窗口很矮时，
新增的第 3/4 行会画到屏幕外面，而第 4 行正是「复制到车场」那个按钮 —— 看不见就等于没有。

建议：拿 `ScreenExtension` 的 height 做 clamp —— `row4Y + ROW_HEIGHT > height` 时
先砍掉第 2 行说明（12px），还不够就把整块往上挤到贴着 `sliderDwellTimeSec` 底部。
十几行的事，顺手把「不同 GUI scale 下点不到复制按钮」这类反馈一次性消掉。

### P2-4【新】Config 无热重载，生产服改一次要重启整个服务端

`Config.load()` 的 `loaded` 是一次性栅栏 —— 注释也明说了「改完要重启」。
对面板服（简幻欢）意味着：改一个布尔要重启整服、玩家全掉。

建议（改动很小，零新依赖）：`load()` 里记 `lastModified`，用一个 5 秒节流的时间戳比对
（`System.nanoTime()`），变了就重读并打印一行日志。
注意别在 per-tick 路径上直接 `stat` 文件 —— `getAccelerationTarget` 每 tick 每列车都调。

### P3-1【新】每 tick 两次二分 + 区间线性扫描，高密度服有累计开销

`getOccupiedMinimumSpeedLimit` 单次 O(log n + k)。平时无所谓，
但 active 列车上到几百列时，每 tick 会叠出几十万次这种调用 —— 值得先看一眼 Spark 再决定要不要动。

两条路：

- **轻量**：按 `(siding, 取整后的 head/tail 区间)` 做 LRU 缓存（Map 上限几十条），命中率会很高。
- **彻底**：路径生成后不变 ⇒ 可在 path 写入时预计算 Sparse Table，区间最小值 O(1)。
  但要额外 hook path 赋值点，触发点确认成本高，收益也低，建议先看是否真的见到 CPU 占用。

### P3-2【新】`breaks` 拦不住 4.1.x

`fabric.mod.json:22` 的 `">=4.0.5 <4.0.6"` 对 `4.1.0-beta.x` 求值为 **false** ⇒ 放行。
4.1.0 的两个注入点没验过，最坏是 `InvalidInjectionException` 直接拒服。
你自己已经排除 4.1.0，但别人装上会炸。建议改成 `">4.0.4"`。

### P3-3【新】四个 UI 字符串硬编码中文，无 lang 文件

MTR 自己走 `TranslationProvider`。本模组 `MTRSPEED$LABEL/HINT/FOLLOW_LABEL/COPY_LABEL`
全是字面量，非中文客户端也显示中文。优先级低（你的服都是中文圈），
什么时候要出英文再说：`assets/mtr4-advanced/lang/*.json` + 启动时 reload 一次。

### P3-4【新】`MTRSPEED$BATCH_SIZE = 40` 是拍脑袋值

注释解释了「为什么要分批」，但 40 没有依据。单条 Siding 的序列化体积随 path 长度涨得很凶
（`pathDistances` / `timeSegments` 数组都在里面）。
建议提到 Config：`batch-size`（默认保持 40），超大车场自己调小即可，不用改代码重编。

---

## 二、还能加些什么（功能想象）

按「跟现有主线是否同源」分四档。**加粗**的是我推荐的下一步。

### A. 速度模型（继续把 README 那句「现实」做实）

| # | 想法 | 难度 | 价值 |
| --- | --- | --- | --- |
| A1 | **手动/ATO 下的 ATP 超速防护**（见 P1-1）：超过 cap 就强制制动（而不是限制目标速度），可选超速报警提示 | 中 | 高 —— 顺手把最大的功能漏洞补上 |
| A2 | **惰行（coasting）**：`powerLevel` 插入 0 档。当前 MTR 只有 ±5（全加速/全制动），接近降速点或停车点时先滑行，既现实又省起步。实现位置就是 `simulateMoving` bc 475-486 那句 `Double.compare` | 中 | 高 —— 观感提升最直接的一条 |
| A3 | **牵引力随速度衰减**：`accel(v) = a0 · (1 - v/vmax)`，电车真实特性（现在 MTR 是全速度区段同一个恒定加速度） | 中 | 中高 |
| A4 | **曲线/道岔自动限速**：从相邻 `PathData` 的起终点向量算转角，夹角超过阈值自动降到一个固定值（现实中过岔必减速） | 高 | 中高 —— 但和已有的 per-siding cap 语义重叠，要设计优先级 |
| A5 | 坡度影响（用 rail 的 y 算坡度，上坡加时、下坡增速） | 高 | 低 —— 地铁基本平坡，收益有限 |

### B. 时刻表与运营

| # | 想法 | 难度 | 价值 |
| --- | --- | --- | --- |
| B1 | **起停附加时分**：现在时刻表只按 cruise 速度积分，现实的时刻表要加「启动附加 + 停车附加」（国铁口径通常各 1-2 分钟级，地铁几秒级）。加进去后推算时间会更接近实跑 | 中 | 高 —— 这是「结构性晚点」的最后一公里，和现有 `applyVehicleLengthToTimeSegments` 一脉相承 |
| B2 | **Siding UI 直接显示推算出的总运行时分**（现在要自己跑一趟才知道） | 低 | 中 —— 调 cap 时的手感差别很大 |
| B3 | 时刻表 ↔ 实跑偏差对比表（跑一趟输出「每站计划/实际/偏差」），用来校准 cap | 中 | 中高 —— 排障利器 |

### C. 运维 / 排障（对服主最有用的一档）

| # | 想法 | 难度 | 价值 |
| --- | --- | --- | --- |
| C1 | **晚点归因日志**：列车持续晚点超过阈值时打一行原因（前车占用 / 本侧线 cap 限制 / 时刻表本身不准 / ATO 限速被压），直接解决「到底是我 mod 还是 MTR 幽灵停车」这个你最头疼的扯皮 | 中 | 高 |
| C2 | **调试 HUD**：F3 风格叠加层，实时显示 `target / railLimit / occupiedMin / cap / deviation / powerLevel`。做 A1/A2 时有没有这个，开发效率差 3 倍 | 低 | 高 —— 建议做别的之前先做它 |
| C3 | 配置热重载（见 P2-4） | 低 | 中高 |

### D. 工程与交互

| # | 想法 | 难度 | 价值 |
| --- | --- | --- | --- |
| D1 | **`build.py` 结尾自动跑 `check_targets.py`**，失败非零退出 —— 现在是两步手工，迟早会忘 | 低 | 中高 |
| D2 | **版本号单一来源**：`fabric.mod.json` / jar 名 / README 三处手工同步 → 让 `build.py` 读 mod json 里的 version | 低 | 中 |
| D3 | 「复制到车场」升级：支持「复制到**所有**车场」「按线路筛选」「按车型一样的侧线」，以及一次撤销 | 中 | 中 |
| D4 | Depot 列表加一列「最高时速」，一眼看出哪几条配过、哪几条漏了 | 中 | 中 |
| D5 | 车型预设下拉（80/100/120/160/350），省得每次手输 | 低 | 低中 |

---

## 三、本轮审查的一句话结论

前三轮把「静态能看出来的坑」基本扫干净了 —— 代码质量在这个体量里属于上游水平：
单例 Active 慢加载的 volatile 栅栏、`@Unique` 命名、注入点离线校验脚本、
连 `-l` 局部变量表都拿去核对口径……都很扎实，没什么可挑的。

剩下的问题基本只有两类：
**① 宿主方法内部还有没走到的分支**（手动驾驶 P1-1 是目前唯一的功能级漏洞，**已决策：不改，改文档**）；
**② 对外承诺的范围写得比实现宽**（manual 适用性、「复制」等于成功、「降速由车头控制」）。

2026-09-28 已按上面的决策收敛一轮：**P1-2 修了文案 + 文档，P1-1 / P2-1 改成用文档写实**。
剩下的 P2-2 ~ P3-4 全部记录在案未动，什么时候顺手什么时候做。
下一件最有性价比的事：先做 C2 调试 HUD，再考虑 A2 惰行 coasting（ATP 已排除）。
