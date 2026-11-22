# MTR4 Advanced 1.1.0 —— 发布产物

本文件夹是 **1.1.0** 的全部 10 个构建：5 个 MC 版本 × Fabric / Forge。
文件名自带版本与目标，直接挑与自己环境匹配的那个。

| 文件 | MC 版本 | 加载器 | 需要的 MTR |
| --- | --- | --- | --- |
| `MTR4Advanced-1.1.0-1.18.2Fabric.jar` | 1.18.2 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.18.2) |
| `MTR4Advanced-1.1.0-1.18.2Forge.jar` | 1.18.2 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.18.2) |
| `MTR4Advanced-1.1.0-1.19.2Fabric.jar` | 1.19.2 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.19.2) |
| `MTR4Advanced-1.1.0-1.19.2Forge.jar` | 1.19.2 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.19.2) |
| `MTR4Advanced-1.1.0-1.19.4Fabric.jar` | 1.19.4 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.19.4) |
| `MTR4Advanced-1.1.0-1.19.4Forge.jar` | 1.19.4 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.19.4) |
| `MTR4Advanced-1.1.0-1.20.1Fabric.jar` | 1.20.1 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.20.1) |
| `MTR4Advanced-1.1.0-1.20.1Forge.jar` | 1.20.1 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.20.1) |
| `MTR4Advanced-1.1.0-1.20.4Fabric.jar` | 1.20.4 | Fabric | MTR 4.0.4 / 4.0.5 (Fabric, 1.20.4) |
| `MTR4Advanced-1.1.0-1.20.4Forge.jar` | 1.20.4 | Forge | MTR 4.0.4 / 4.0.5 (Forge, 1.20.4) |

目录结构（每个模组版本一个文件夹）：

    releases/
    ├── 1.1.0/          ← 本文件夹
    │   ├── MTR4Advanced-1.1.0-<MC版本><加载器>.jar   ×10
    │   ├── README.md
    │   └── SHA1SUMS.txt
    └── <旧版本>/        ← 各版本各自一个文件夹

## 安装

1. 装 **MTR 4.0.4 或 4.0.5**：MC 版本和加载器都要与本模组那一份对上。
2. 把对应 jar 放进 `mods/`。
3. **客户端和服务端都要装，而且用完全相同的那一份**（行车限速计算在服务端，设置界面在客户端）。
4. 首次启动会在 `config/mtr4-advanced.properties` 生成配置，日志里会打印它的绝对路径。

## 1.1.0 新增

### 一、刷子界面里改线路限速

- 右键轨道打开的形状 / 样式 / 半径界面里新增一行「限速 xx km/h ▾」
- 点开是 **10 个原版档位**（20 / 40 / 60 / 80 / 100 / 120 / 140 / 160 / 200 / 300），直接点选
- 改的是已有轨道，不用重铺：重建 Rail（形状/半径/样式保留）后走原版 `PacketUpdateData.addRail` 提交
- 站台轨 / 侧线轨 / 折返轨显示「不可改」

### 二、站台交路行为（颗粒度 = 交路 × 站台）

- 站台线设置 → 「交路站台行为」按钮
- 每条停靠该站台的交路一行：
  - 延迟开门（输入框，秒）0 = 原版
  - 提前关门（输入框，秒）0 = 原版
  - 技术停车（是/否）：照常停站发车、车门全程不开
- 数据存在 `RoutePlatformData`，随交路序列化，单端安装不破坏存档

### 已知问题（1.1.1 已修复）

- 改限速时有概率把轨道的**单向（方向）属性**覆盖掉：判断单向用了会被 `reversePositions` 翻转的 getter。
  1.1.1 改为读原始字段并保留 0 的位置。

## 校验

`SHA1SUMS.txt` 是本文件夹所有 jar 的 SHA-1。

    sha1sum -c SHA1SUMS.txt                       # Linux / macOS
    Get-FileHash *.jar -Algorithm SHA1             # Windows PowerShell

## 验证程度（请如实看待）

每个目标都做过：

- ✅ 用**该版本该加载器的 MTR 4.0.4** 编译通过
- ✅ Mixin 注入点离线校验 **18/18 全 OK**
- ✅ 注入点在 MTR **4.0.5** 的 jar 上同样通过

**没有**做：在真实游戏 / 服务端里逐个版本启动测试。
目前唯一经过运行时验证的组合是 **1.20.1 + Fabric**。

## 许可

MIT。MTR 与本模组无隶属关系；本发布**不包含** MTR 本体。
