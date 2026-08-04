# 慧湖通门禁

一个非官方的 Android 门禁二维码客户端，用于加载并展示当前用户自己的慧湖通门禁二维码。

> [!IMPORTANT]
> 本项目不是西交利物浦大学或“慧湖通”的官方应用，也未得到其认可或授权。门禁属于安全敏感场景；请优先使用官方服务，并且只使用你本人有权使用的账号和门禁权限。

## 主要功能

- 显示用户、住址和门禁权限信息
- 生成并定时刷新门禁二维码
- 支持手动刷新二维码和用户信息
- 显示服务返回的余额不足提醒
- 打开二维码页面时自动提高屏幕亮度

## 使用前准备

你需要：

- Android 8.0（API 26）或更高版本的设备
- 可访问目标服务的网络连接
- 你本人合法取得的 `openId`，建议同时提供对应的 `unionId`

本应用不会为你创建、查找或提取这些参数，也不提供任何其他用户的凭据。

## 安装

目前仓库尚未发布预编译安装包。开发者或能够自行构建 Android 项目的用户可以按照下方的“从源代码构建”进行安装。

将来若发布安装包，请只从本仓库的 [Releases](https://github.com/Oooo00o0O/HuiHuTong/releases) 页面下载，并在安装前核对来源。不要安装他人重新打包的未知 APK。

## 使用方法

1. 安装并打开“慧湖通门禁”。
2. 首次启动时，在弹出的设置窗口中输入你自己的参数。
3. 点击“保存”，等待用户信息和二维码加载。
4. 应用会定时刷新二维码；需要时也可以点击“手动刷新”。
5. 如需更换账号参数，点击页面右上角的“设置”。

支持以下输入格式：

```text
openId=你的值&unionId=你的值
```

```json
{"openId":"你的值","unionId":"你的值"}
```

如果只有 `openId`，也可以直接粘贴其值，但部分账号或服务版本可能必须同时提供 `unionId`。

## 隐私与安全

- 仓库不包含 API 密钥、真实 `openId`、`unionId` 或登录令牌。
- 你输入的参数保存在 Android 的应用私有存储中，不会发送给本项目作者。
- 登录和二维码请求会通过 HTTPS 直接发送到应用所配置的目标服务（当前为 `api.215123.cn`）。
- 参数并未使用独立的应用级加密，请将设备锁屏并避免在 Root、共享或不受信任的设备上保存参数。
- 请勿截图、转发二维码或分享账号参数。发现参数泄露时，请停止使用并通过相应的官方渠道处理。

## 从源代码构建

构建需要最新版 Android Studio、Android SDK 36，以及 Android Studio 自带的兼容 JDK。

1. 克隆仓库：

   ```bash
   git clone https://github.com/Oooo00o0O/HuiHuTong.git
   cd HuiHuTong/HuihutongGate
   ```

2. 使用 Android Studio 打开 `HuihutongGate` 目录并等待 Gradle Sync 完成。
3. 在 Android Studio 中运行应用；也可以使用 Gradle Wrapper 构建 Debug APK：

   Windows：

   ```powershell
   .\gradlew.bat assembleDebug
   ```

   macOS/Linux：

   ```bash
   ./gradlew assembleDebug
   ```

4. 构建结果位于：

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

本机 Android SDK 路径应写在未纳入版本控制的 `local.properties` 中，不要提交该文件。

## 已知限制

- 依赖非公开且可能变化的服务接口，服务升级后应用可能失效。
- 目前没有正式发布或自动更新机制。
- 这不是官方凭据恢复工具，也不能为没有门禁权限的账号增加权限。

## 许可证

除第三方许可另有说明的代码外，本项目代码以 [GNU General Public License v3.0 only](LICENSE) 发布。分发本项目或其修改版本时，必须遵守 GPL-3.0 的源代码和许可证要求。

内置二维码编码器的部分实现改编自 MIT 许可的 Project Nayuki QR Code generator；完整版权和许可声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

GPL 仅适用于本项目作者有权许可的代码，不授予任何第三方服务、接口、名称、商标、数据或素材的权利。
