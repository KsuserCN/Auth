# Ksuser 安全 Android

Kotlin / Jetpack Compose 原生客户端。最低 Android 7.0（API 24），编译 SDK 36.1，目标 API 36，Java 21 构建。当前源码版本为 1.0.0.13（13）。

本轮以仓库内 iOS 实现为基线接入功能并补充回归。Debug 与 unsigned Release 已构建通过，单元测试和 API 37 模拟器基础界面检查已通过。真实邮件/QQ/硬件密钥、Android 24/25 与目标版本设备、正式签名升级仍待验收，详见 [ACCEPTANCE.md](ACCEPTANCE.md)。

## 开发与验证

在仓库根目录执行：

```sh
npm run test:mobile:android
npm run dev:mobile:android
npm run build:mobile:android:release
npm run test:android-release
```

项目显式保留 Debug/Release 两种单元测试组件，以支持发布 CI；参见 [AGP 测试默认行为](https://developer.android.com/build/releases/agp-9-0-0-release-notes#behavior_changes)。

正式发布使用 `npm run release:android`：版本只改 `app/build.gradle.kts` 的 `versionName` 和 `versionCode`，命令会提示签名密码，测试并生成 `build/release.apk` 和配套网站目录，详见 [日常发布操作](RELEASE.md#日常发布改两行运行一个命令)。

设备或模拟器连接后，可在 `mobile/android` 执行 `./gradlew :app:connectedDebugAndroidTest`。当前界面测试使用本地合成状态，不操作生产账号；它们不代表全部真实接口流程已完成设备验收。

界面采用统一的原生分组样式，保留五栏导航并支持深浅色、中英文及大字体。右上角操作与 iOS 一致，扫码在左、纯齿轮设置在最右。品牌名称显示为中文 `Ksuser安全`、英文 `Ksuser Auth`。

`.env.development` / `.env.production` 可配置 `API_BASE_URL`、`PASSKEY_RP_ID`、`PASSKEY_ORIGIN_HINT`、`UPDATE_MANIFEST_URL`、`QQ_MOBILE_APP_ID`、`APP_ENV`、`ENABLE_HTTP_LOGGING`；缺省连接 `https://api.ksuser.cn` 与 `https://auth.ksuser.cn`。不要把服务器密钥放入客户端。发布签名与安装包准备见 [RELEASE.md](RELEASE.md)；未配置签名时 Release 构建只产出 unsigned APK。

## 已接入功能

- 密码、邮箱验证码、QQ，以及统一的 Passkey 登录入口；Passkey 入口支持选择设备/密码管理器或 USB/NFC 安全密钥。快捷登录使用上下排列的 QQ、Passkey 宽按钮。登录页需主动勾选协议后才能登录、发送验证码或启动快捷登录；五步注册、MFA、第三方账号关联与登录后继续原请求。
- 登录、MFA、敏感操作、恢复授权、会话转移、Console、OAuth 及官方应用扫码；相机和相册识别；应用身份及权限展示、长期/一次性/限时授权、明确拒绝。
- HTTPS 或 `ksuserauth` scheme 唤起 App。网页登录和非扫码应用授权按服务端结果返回受信任浏览器地址；扫码应用授权完成留在 App，默认只授权应用，网页登录需另行主动选择。原生 SDK 应用间授权使用独立 HTTPS 入口与精确登记的第三方回调。
- 第三方/官方已授权应用列表、详情、刷新和确认撤销；QQ 绑定/解绑；已有 Apple 状态和解绑、无密码及无邮箱账号兼容。Apple 登录、新绑定和重新认证不在本轮范围。
- 资料、性别、生日、头像本地裁剪与压缩；TOTP、恢复码、本地生成恢复二维码；敏感验证、安全偏好、风险信息、会话撤销、日志筛选分页与详情、账号注销。
- 跟随系统/简体中文/English，跟随系统/浅色/深色主题；登录前后设置、文档、反馈和恢复入口。切换语言保留当前输入与会话，每次 API 请求发送对应 `Accept-Language`。
- 加密会话缓存、临时断网保留、并发刷新、暂存登录与原子换号；服务不可用提示与前台恢复探测。APK 更新使用独立公共客户端，验证文件后交系统确认安装。

## 链接与后端

入口严格匹配 `/app/bridge-login`、`/app/authorize` 或对应 scheme。原生授权另用精确的 HTTPS `/app/sdk-authorize?ticket=...`，不支持对应自定义 scheme；`/app/sdk-authorize/web` 与 `/app/authorize-return` 由网页处理，不由 App 捕获。票据、重复参数、域名、端口和返回地址均校验。

### 原生 SDK 应用间授权

调用 App 使用 `sdk/android` 创建 `platform=android` 的 public＋PKCE 请求，再打开服务端返回的 App Link。Ksuser App 只从 `/auth/native-authorization/context` 读取应用、开发者、账号和 scope，复用登录/MFA 后继续和明确同意/拒绝界面；第三方 OAuth 支持长期、一次性、限时授权，官方 OIDC 使用独立官方策略。

批准与拒绝提交 `/auth/native-authorization/decide`。App 只打开与 context 的登记 HTTPS 回调精确匹配、包含单一 code/state 或 error/state 的服务端返回值，不将 native 回调交给旧网页登录返回规则。成功决定后显示“返回应用继续”；回跳失败或手动重试仅重新打开同一回调，不再次决定或发码。

未完成 ticket 与已决定回调保存在 App 现有加密、排除备份的会话存储中。进程重建可继续原 ticket 或恢复返回页；已决定回调绑定当前批准账号，退出登录、清除会话及到期后不再恢复。日志不记录 ticket、授权码或完整回调 query。

这项能力需要配套 API、网页备用页、新版 Ksuser App 与调用 App 自有域名的正式签名关联。通过 Debug 构建和本地合成 UI 测试不代表实际域关联或线上 client 已放行。完整开发者接入见仓库 `sdk/android/README.md` 和文档站的中英文 Android SDK 指南。

大部分功能复用已有 API。日志详情新增 `GET /auth/sensitive-logs/{id}`，只返回当前账号的记录，其他账号或不存在的 ID 返回 404。联调及发布时需部署配套 API、Web 回退路由、域关联文件与新版 APK；本次不需要新增数据库迁移。

`web/public/.well-known/assetlinks.json` 必须包含最终正式签名证书。HTTP 200 和 JSON 格式正确不能替代证书匹配及设备域关联检查；随机生成的 debug 签名不会自动获得生产域凭据权限。外部凭据提供方与 USB/NFC 密钥需要真机验证。

2026-10-17 公开链接实测：中英服务协议、隐私政策、第三方信息清单及 `assetlinks.json` 返回 HTTP 200；协议为页面，域关联为 JSON。`/downloads/latest/android.json` 虽返回 HTTP 200，正文实际为 SPA HTML，真实更新清单和正式 APK 尚未部署。
