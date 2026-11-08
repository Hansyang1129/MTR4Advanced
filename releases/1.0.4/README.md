# MTR4 Advanced 1.0.4 —— 发布产物

本文件夹是 **1.0.4** 的全部 10 个构建：5 个 MC 版本 × Fabric / Forge。
文件名自带版本与目标，直接挑与自己环境匹配的那个。

| 文件 | MC 版本 | 加载器 | 需要的 MTR |
| --- | --- | --- | --- |
| `MTR4Advanced-1.0.4-1.18.2Fabric.jar` | 1.18.2 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.18.2) |
| `MTR4Advanced-1.0.4-1.18.2Forge.jar` | 1.18.2 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.18.2) |
| `MTR4Advanced-1.0.4-1.19.2Fabric.jar` | 1.19.2 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.19.2) |
| `MTR4Advanced-1.0.4-1.19.2Forge.jar` | 1.19.2 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.19.2) |
| `MTR4Advanced-1.0.4-1.19.4Fabric.jar` | 1.19.4 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.19.4) |
| `MTR4Advanced-1.0.4-1.19.4Forge.jar` | 1.19.4 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.19.4) |
| `MTR4Advanced-1.0.4-1.20.1Fabric.jar` | 1.20.1 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.20.1) |
| `MTR4Advanced-1.0.4-1.20.1Forge.jar` | 1.20.1 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.20.1) |
| `MTR4Advanced-1.0.4-1.20.4Fabric.jar` | 1.20.4 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.20.4) |
| `MTR4Advanced-1.0.4-1.20.4Forge.jar` | 1.20.4 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.20.4) |

目录结构（每个模组版本一个文件夹）：

    releases/
    ├── 1.0.4/          ← 本文件夹
    │   ├── MTR4Advanced-1.0.4-<MC版本><加载器>.jar   ×10
    │   ├── README.md
    │   └── SHA1SUMS.txt
    └── <旧版本>/        ← 各版本各自一个文件夹

## 安装

1. 装 **MTR 4.0.4 或 4.0.5**：MC 版本和加载器都要与本模组那一份对上。
2. 把对应 jar 放进 `mods/`。
3. **客户端和服务端都要装，而且用完全相同的那一份**（行车限速计算在服务端，设置界面在客户端）。
4. 首次启动会在 `config/mtr4-advanced.properties` 生成配置，日志里会打印它的绝对路径。

> 1.0.4 起 **MTR 4.0.5 已解锁**：`breaks` 已移除，`depends` 只要求 `mtr >= 4.0.4`。
> 低于 4.0.4 的 MTR 仍会被依赖校验挡下。装错 MC 版本同理，不会静默出错。

## 1.0.4 改了什么

**只解除版本锁，功能与 1.0.3 完全一致，不影响任何现有存档 / 配置。**

| 项 | 1.0.3 | 1.0.4 |
| --- | --- | --- |
| Fabric `fabric.mod.json` | `"breaks": { "mtr": ">=4.0.5 <4.0.6" }` | 无 `breaks` |
| Forge `mods.toml` | `versionRange = "[4.0.4,4.0.5)"` | `versionRange = "[4.0.4,)"` |

依据：用 1.0.3 已编译的 class 对 **MTR-fabric-4.0.5+1.20.1** 的 jar 跑 `tools/check_targets.py`，
18/18 注入点（含影子字段、`@ModifyConstant` / `@Redirect` 调用点唯一性）全部通过，与 4.0.4 结果一致。

## 校验

`SHA1SUMS.txt` 是本文件夹所有 jar 的 SHA-1。

    sha1sum -c SHA1SUMS.txt                       # Linux / macOS
    Get-FileHash *.jar -Algorithm SHA1             # Windows PowerShell

## 验证程度（请如实看待）

每个目标都做过：

- ✅ 用**该版本该加载器的 MTR 4.0.4** 编译通过
- ✅ Mixin 注入点离线校验 **18/18 全 OK**
- ✅ 注入点另在 **MTR 4.0.5（fabric 1.20.1）** 的 jar 上复跑一遍，同样 18/18
- ✅ 产物自检：12 个 class、元数据版本 1.0.4、Fabric 无 `breaks`、Forge 区间 `[4.0.4,)`

**没有**做：在真实游戏 / 服务端里逐个版本启动测试。
目前唯一经过运行时验证的组合是 **1.20.1 + Fabric + MTR 4.0.4**；4.0.5 尚未实机跑过。

## 许可

MIT。MTR 与本模组无隶属关系；本发布**不包含** MTR 本体。
