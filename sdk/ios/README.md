# Ksuser iOS 授权 SDK

SDK 最低 iOS 17.4（Xcode 15.3+），Ksuser 安全 App 本身仍最低 iOS 17.0。SDK 是原生 public client，不含 client secret，不共享 Ksuser App 的访问令牌、Cookie 或 Keychain。

## 安装

Xcode → File → Add Package Dependencies，添加 `https://github.com/KsuserCN/Auth.git`，选择包含 SDK 的分支/版本并链接 `KsuserAuthSDK`。仓库根 `Package.swift` 指向 `sdk/ios/Sources/KsuserAuthSDK`；本地联调用 Add Local 选择本仓库根目录。只有打包/验证代码支持 macOS；授权客户端面向 iOS。

## 注册及 HTTPS 回调

1. 在开放平台的“移动应用”类目注册应用；移动应用固定为 public client，不发放 AppSecret。同一个 AppID 可以分别登记 iOS 和 Android，SDK 的 `.oauth` 使用这个移动应用 AppID。“网站”类目的客户端不能发起原生授权。官方 `.official` 仍由管理员在独立的官方应用体系注册 public client。
2. 为移动应用登记 iOS Bundle ID、App ID 前缀以及**完整、精确的 HTTPS Universal Link 回调**，例如 `https://login.example.app/ksuser/callback`。不带查询、片段、凭据、非标准端口或 `..` 路径，也不能用 `auth.ksuser.cn`。URL Scheme 和 App Store ID 是可选资料；SDK 使用 HTTPS 回调。每个无关联的第三方 App 使用自己的回调 host；不同第三方不能共用一个回调域名。
3. 调用 App 的签名 entitlement 添加 `applinks:login.example.app` 和 `webcredentials:login.example.app`。在自己的 HTTPS host 部署 `.well-known/apple-app-site-association`，无重定向，Content-Type 为 `application/json`。将下方 `APP_ID_PREFIX` 和 Bundle ID 改为实际值，完整标识与签名中的 `application-identifier` 一致。App ID 前缀通常等于 Team ID，旧账号可能不同，应以签名配置为准，参见 [Apple App ID Prefix 说明](https://developer.apple.com/library/archive/qa/qa1879/_index.html)：

```json
{
  "applinks": {
    "details": [{
      "appIDs": ["APP_ID_PREFIX.com.example.app"],
      "components": [{"/": "/ksuser/callback"}]
    }]
  },
  "webcredentials": {"apps": ["APP_ID_PREFIX.com.example.app"]}
}
```

4. 在平台信息点击“验证”。服务端必须验证 AASA 中的应用标识、回调路径和 `webcredentials` 后，移动应用才能创建原生授权请求；修改关联资料后需要重新验证。Android 可在同一 AppID 下登记包名、签名证书 SHA-256 指纹和 HTTPS App Link 并验证，接入见 [Android SDK](../android/README.md)。

`applinks` 用于 Ksuser App 回跳，`webcredentials` 用于 iOS 17.4+ 的 ASWebAuthenticationSession HTTPS callback。SDK 在两种模式的 `/auth/native-authorization/create` 请求中固定发送顶层 `platform: "ios"`。平台字段、声称的 Bundle ID 或 sourceApplication 都不是调用方身份凭据；服务端应用登记、HTTPS 关联、精确 redirect URI、PKCE 和 state 共同约束授权。关联域名和签名要求参见 [Apple Associated Domains 文档](https://developer.apple.com/documentation/xcode/supporting-associated-domains)。

## OpenID 与 UnionID

第三方 OAuth 的 `openID` 按 AppID 隔离。网站和移动应用需要各自申请 UnionID：在应用的“接口权限”提交理由，管理员批准后，Token 与 `/oauth2/userinfo` 才返回 `unionid`。同一开发者账号下获批的应用为同一用户返回相同 UnionID，不同开发者账号相互隔离。

`KsuserTokenSet.unionID` 是可选字段，未申请、待审、驳回或已撤销时可以为空；登录逻辑应正常使用 OpenID。关闭能力后不再向后续请求提供 UnionID，UserInfo 会实时检查当前权限，已交付给应用的历史标识不能收回。官方 OIDC 的 subject 继续使用独立的官方用户标识。

## 调用及恢复

保留一个 `@MainActor KsuserAuthClient` 实例，并保留提供有效 UIWindow 的 `ASWebAuthenticationPresentationContextProviding`：

```swift
let client = try KsuserAuthClient(configuration: .init(
    clientID: "your-mobile-application-app-id",
    mode: .oauth, // Ksuser 官方应用使用 .official
    redirectURI: URL(string: "https://login.example.app/ksuser/callback")!
))
let result = try await client.authorize(
    scopes: ["profile"], // .official 默认使用 ["openid", "profile"]
    presentationContextProvider: presentationProvider
)
switch result {
case .oauth(let tokens):
    // tokens.accessToken 只能调用此 OAuth 应用的 API /oauth2/userinfo。
    // tokens.openID 是应用内用户标识；tokens.unionID 仅在已开通时提供。
    securelyStoreYourApplicationToken(tokens.accessToken)
case .official(let tokens, let subject):
    // subject 已通过 RS256/JWKS/iss/aud/exp/nonce 验证，是官方用户 UUID。
    securelyStoreYourApplicationToken(tokens.accessToken)
    showSignedInUser(subject)
}
```

在 SwiftUI `.onOpenURL` / `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)`，或 UIKit SceneDelegate / AppDelegate 的 Universal Link 入口转发 `client.handleCallback(url)`。仅匹配当前交易、精确地址和 state 的回调被接受；返回 `false` 的 URL 交其他路由处理。冷启动收到回调时，先 `handleCallback`，再执行：

```swift
if let result = try await client.resumePendingAuthorization(
    presentationContextProvider: presentationProvider
) {
    // 与 authorize 返回相同结果；保存调用 App 自己的登录状态。
}
```

SDK 在跳 App 前将 PKCE verifier、state、nonce、取消凭据和有效期存入调用 App 自己的 `WhenUnlockedThisDeviceOnly` Keychain。冷启动可恢复，过期会清理；令牌返回后由业务自行安全保存。回调只带一次性 code/state 或 error/state，不带 Ksuser 登录令牌或取消凭据。SDK 直接向 `/oauth2/token` 或 `/official/token` 兑换，不轮询授权码，也没有 consume 接口。

省略 scopes 时，OAuth 默认 ["profile"]，官方应用默认 ["openid", "profile"]。同一调用 App 的 SDK 实例共用并发锁和一份待完成 Keychain 交易，不同 client 配置不能覆盖未完成交易。

默认优先 Universal Link 唤起 Ksuser 安全 App，无法打开时用 ASWebAuthenticationSession 完成同一笔网页授权。旧 Ksuser App 无法处理新路由，或者用户希望继续网页授权时调用：

```swift
try await client.resumePendingAuthorization(
    presentationContextProvider: presentationProvider,
    useWebFallback: true
)
```

若原 `authorize` 仍等待，该调用只切换同一 ticket 到网页并返回 nil，原调用接收最终结果；冷启动恢复则由 resume 返回结果。不要新建第二笔授权替换当前交易。取消使用 `await client.cancel()`；本地立即结束，并尽力向服务端取消。已完成的授权需要使用授权管理页撤销。网络错误、HTTP 429 与 5xx 会保留尚未到期的交易供恢复（官方 token 已兑后会保存，JWKS 暂错不会二次兑 code）；重复回调不会重复兑换。

可直接打开的 SwiftUI 示例工程见 [NativeLoginDemo](../../demos/ios-auth-demo/README.md)，签名及 AASA 见同目录示例。必须使用自己真实登记和部署的域名/客户端才能完成双 App 真机联调。

## 发布与校验

先部署 API 与网页，并更新 Ksuser App 及 `auth.ksuser.cn` 的 AASA（含 `/app/sdk-authorize`，排除 `/app/sdk-authorize/web`）。API 默认关闭 `APP_NATIVE_AUTHORIZATION_ENABLED=false`；上线时启用，并用 `APP_NATIVE_AUTHORIZATION_ALLOWED_CLIENT_IDS` 对 client ID 灰度。回调域名 AASA 由调用 App 开发者部署。SDK 源码准备并不等于远程版本已经发布。

```sh
npm run test:sdk:ios
npm run build:sdk:ios
npm run build:demo:ios
npm run test:mobile:ios
npm run build:mobile:ios
```

`test:sdk:ios` 在 macOS 执行协议与密码学测试。SDK 的 UIKit 客户端生命周期测试需要 iOS 模拟器，使用 `xcodebuild -scheme KsuserAuthSDK -destination 'platform=iOS Simulator,name=你的模拟器名称' CODE_SIGNING_ALLOWED=NO test`，可先通过 `xcrun simctl list devices available` 查询设备名称。

覆盖 PKCE 标准向量、精确回调与 state、恶意启动 URL、两种模式的 iOS 平台请求、无 UnionID 的 Token、public token 请求、官方 RS256 签名和 issuer/audience/expiry/nonce 校验，以及过期交易清理、公钥服务临时故障恢复、冷启动、取消、拒绝和重复回调的一次性兑换。Host 测试覆盖 native 独立入口、登录后恢复、重试不重新签发 code 及 App 会话隔离。真机还需验证登记与 AASA 验证状态、已安装/未安装 Ksuser App、拒绝、取消、冷启动和旧 App 网页恢复。
