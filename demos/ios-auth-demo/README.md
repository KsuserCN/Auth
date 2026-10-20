# Ksuser iOS 授权 Demo

一个通过本地 `KsuserAuthSDK` 发起授权的 SwiftUI App，最低支持 iOS 17.4。可在界面切换移动应用 OAuth 和官方应用模式，体验唤起 Ksuser App、网页继续、恢复待完成请求和取消授权。示例只显示结果与身份信息，不显示或记录令牌，也不需要自己的业务后端或客户端密钥。

## 打开与配置

在仓库根目录运行：

```bash
npm run dev:demo:ios
```

也可以用 Xcode 打开本目录的 `NativeLoginDemo.xcodeproj`，选择共享 scheme `NativeLoginDemo`。工程通过 `../..` 引用仓库根 `Package.swift` 中的 SDK，不需要重新添加依赖。

1. 在开放平台“移动应用”创建应用，将 [DemoConfiguration.swift](DemoConfiguration.swift) 的 `oauthClientID` 替换为 AppID。移动应用固定 public＋PKCE，没有 AppSecret；网站类目不能用于这个 Demo 的原生授权。同一 AppID 可同时登记 iOS、Android，本次使用 iOS；Android 登记与域名验证已纳入模型，授权入口和 SDK 后续提供。要测试官方模式，再填写 Console 独立注册的 public `officialClientID`。两种 ID 不能混用，不要添加 `client_secret`。
2. 将 `redirectURI` 改为自己控制、已经在相应客户端登记的精确 HTTPS 地址，例如 `https://login.your-domain.example/ksuser/callback`。回调无 query、fragment，端口省略或为 443，回调域名不能与 Ksuser 授权入口域名相同。两个模式可以登记同一 Demo 回调。
3. `apiBaseURL`、`webBaseURL` 和 `officialIssuer` 默认指向生产环境。联调自部署服务时一并修改为对应 HTTPS 地址；SDK 不接受 HTTP 环境。`scopes(for:)` 默认第三方使用 `profile`，官方使用 `openid profile`。
4. 在 Xcode 的 Signing & Capabilities 中选择自己的 Team。Demo 的 Bundle ID 固定为 `cn.ksuser.demo.oauth`；开放平台登记时使用相同值。工程使用 Automatic Signing，最低系统保持 iOS 17.4。
5. 在移动应用的 iOS 平台填写实际 Bundle ID、App ID 前缀和上述 Universal Link 回调；URL Scheme、App Store ID 可选。按下面配置部署 AASA，在平台信息点击“验证”，通过后再在已签名的真实设备上运行。修改关联资料后需要重新验证。

示例会记住所选模式，冷启动时通过该模式建立 SDK 实例，以便接收回调和恢复同一笔交易。修改客户端 ID、回调或环境前，先在旧配置中取消待完成请求，或等待五分钟过期；SDK 不允许新配置覆盖尚未完成的交易。

## 域名关联与服务端

Demo 使用 `https://demos.ksuser.cn/ksuser/callback` 作为 Universal Link 回调。`NativeLoginDemo.entitlements` 中的 `applinks:demos.ksuser.cn` 用于 Ksuser App 返回 Demo，`webcredentials:demos.ksuser.cn` 用于系统网页认证会话的 HTTPS 回调。

本 Demo 的 Team ID / App ID 前缀为 `8LDARH2S3X`，Bundle ID 为 `cn.ksuser.demo.oauth`。本目录 [apple-app-site-association](apple-app-site-association) 示例与生产托管文件保持一致。AASA 必须以无扩展名文件部署到 `https://demos.ksuser.cn/.well-known/apple-app-site-association`，通过 HTTPS 直接返回，Content-Type 为 `application/json`。该文件由域名托管，不打包进 App。更新后重新安装已签名的 Demo，以刷新系统关联。服务端验证失败、平台尚未登记或修改后未重新验证时，不能发起移动应用授权。

SDK 自动在创建请求顶层发送 `platform: "ios"`，Demo 无需自行填平台参数。平台参数不代表调用方身份凭据，授权仍依赖已验证的 HTTPS 关联、精确回调、PKCE 和 state。

还需要部署支持原生授权事务的 API、Web 备用入口和 Ksuser iOS App，以及 Ksuser 授权域名的 AASA。API 设置 `APP_NATIVE_AUTHORIZATION_ENABLED=true`，并将当前客户端 ID 加入 `APP_NATIVE_AUTHORIZATION_ALLOWED_CLIENT_IDS`。第三方和官方模式分别配置所用客户端。详见 [SDK 接入说明](../../sdk/ios/README.md)及[后端原生授权合同](../../api/docs/native-app-authorization.md)。

## 操作与验证

- 选择第三方或官方模式，点击授权按钮。已安装并关联 Ksuser App 时打开 App，无法唤起时自动使用系统网页认证会话。
- “改用网页继续”沿用当前 ticket，适合旧版 Ksuser App 或希望继续网页授权的情况。
- “恢复待完成授权”恢复 SDK 保存在 Demo 自身 Keychain 中的事务；可在授权期间终止 Demo，随后让 Ksuser App 返回，验证冷启动恢复。
- “取消当前请求”结束本地等待并尽力取消服务端事务，不撤销已经完成的授权。取消或完成后可以切换模式再授权。
- 第三方结果显示应用内 OpenID；UnionID 需要在应用的“接口权限”单独提交理由并经管理员批准后才提供。同一开发者账号下获批的网站和移动应用共享同一用户的 UnionID。未获批或能力撤销后 Token／UserInfo 不提供该字段，Demo 仍可正常登录，并显示未开通提示。关闭能力不能收回已交付的历史标识。

从仓库根目录执行无需签名的模拟器构建：

```bash
npm run build:demo:ios
```

等价命令：

```bash
xcodebuild \
  -project demos/ios-auth-demo/NativeLoginDemo.xcodeproj \
  -scheme NativeLoginDemo \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath demos/ios-auth-demo/DerivedData \
  CODE_SIGNING_ALLOWED=NO build
```

模拟器构建验证 Demo 与本地 SDK 能共同编译。完整授权验证需要有效签名、已登记客户端、已部署 AASA 和可访问的 Ksuser 服务；真机覆盖同意、拒绝、取消、网页继续、冷启动恢复，以及未安装 Ksuser App 时的回退。
