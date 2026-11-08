# MTR4 Advanced 1.0.3 —— 发布产物

本文件夹是 **1.0.3** 的全部 10 个构建：5 个 MC 版本 × Fabric / Forge。
文件名自带版本与目标，直接挑与自己环境匹配的那个。

| 文件 | MC 版本 | 加载器 | 需要的 MTR |
| --- | --- | --- | --- |
| `MTR4Advanced-1.0.3-1.18.2Fabric.jar` | 1.18.2 | Fabric | MTR 4.0.4 (Fabric, 1.18.2) |
| `MTR4Advanced-1.0.3-1.18.2Forge.jar` | 1.18.2 | Forge | MTR 4.0.4 (Forge, 1.18.2) |
| `MTR4Advanced-1.0.3-1.19.2Fabric.jar` | 1.19.2 | Fabric | MTR 4.0.4 (Fabric, 1.19.2) |
| `MTR4Advanced-1.0.3-1.19.2Forge.jar` | 1.19.2 | Forge | MTR 4.0.4 (Forge, 1.19.2) |
| `MTR4Advanced-1.0.3-1.19.4Fabric.jar` | 1.19.4 | Fabric | MTR 4.0.4 (Fabric, 1.19.4) |
| `MTR4Advanced-1.0.3-1.19.4Forge.jar` | 1.19.4 | Forge | MTR 4.0.4 (Forge, 1.19.4) |
| `MTR4Advanced-1.0.3-1.20.1Fabric.jar` | 1.20.1 | Fabric | MTR 4.0.4 (Fabric, 1.20.1) |
| `MTR4Advanced-1.0.3-1.20.1Forge.jar` | 1.20.1 | Forge | MTR 4.0.4 (Forge, 1.20.1) |
| `MTR4Advanced-1.0.3-1.20.4Fabric.jar` | 1.20.4 | Fabric | MTR 4.0.4 (Fabric, 1.20.4) |
| `MTR4Advanced-1.0.3-1.20.4Forge.jar` | 1.20.4 | Forge | MTR 4.0.4 (Forge, 1.20.4) |

目录结构（每个模组版本一个文件夹）：

    releases/
    ├── 1.0.3/          ← 本文件夹
    │   ├── MTR4Advanced-1.0.3-<MC版本><加载器>.jar   ×10
    │   ├── README.md
    │   └── SHA1SUMS.txt
    └── <旧版本>/        ← 各版本各自一个文件夹

## 安装

1. 装 **MTR 4.0.4**：MC 版本和加载器都要与本模组那一份对上。
2. 把对应 jar 放进 `mods/`。
3. **客户端和服务端都要装，而且用完全相同的那一份**（行车限速计算在服务端，设置界面在客户端）。
4. 首次启动会在 `config/mtr4-advanced.properties` 生成配置，日志里会打印它的绝对路径。

> MTR **4.0.5 会被拒绝启动**（`breaks` 依赖声明），这是有意为之。
> 装错 MC 版本同样会被加载器的依赖校验挡下，不会静默出错。

## 1.0.3 改了什么

**界面文案支持中英双语；功能与 1.0.2 完全一致，不影响任何现有存档 / 配置。**

| 位置 | 旧 | 新 |
| --- | --- | --- |
| 第 1 行标签 | 硬编码 `列车最高时速 km/h` | 语言键 `mtr4a.gui.max_speed` |
| 第 2 行提示 | 硬编码 `0 = 不限制，留空同 0` | 语言键 `mtr4a.gui.max_speed.hint` |
| 第 3 行复选框 | 硬编码 `跟随晚点浮动` | 语言键 `mtr4a.gui.follow_deviation` |
| 第 4 行按钮 | 硬编码 `复制到车场其他侧线` | 语言键 `mtr4a.gui.copy_to_depot` |

* 语言包在 jar 内 `assets/mtr4advanced/lang/{zh_cn,en_us}.json`，随客户端语言切换，其它语言回落英文。
* 走 MTR 标准翻译（`TextHelper.translatable`），切换语言后界面立即刷新，不用重进。
* 顺带修了两个构建脚本问题：发布构建原先只复制 `mixins.json`（语言包会丢），
  开发构建原先会整体清空 `build/libs`（会误删其它版本的开发 jar）。

## 校验

`SHA1SUMS.txt` 是本文件夹所有 jar 的 SHA-1。

    sha1sum -c SHA1SUMS.txt                       # Linux / macOS
    Get-FileHash *.jar -Algorithm SHA1             # Windows PowerShell

## 验证程度（请如实看待）

每个目标都做过：

- ✅ 用**该版本该加载器的 MTR 4.0.4** 编译通过
- ✅ Mixin 注入点离线校验 **18/18 全 OK**
- ✅ 产物自检：12 个 class（新增 `I18n`）、两个语言包都在 jar 内、元数据版本 1.0.3 与依赖区间正确

**没有**做：在真实游戏 / 服务端里逐个版本启动测试。
目前唯一经过运行时验证的组合是 **1.20.1 + Fabric**。

## 许可

MIT。MTR 与本模组无隶属关系；本发布**不包含** MTR 本体。
