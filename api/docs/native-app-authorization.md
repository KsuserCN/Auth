# 原生应用 App-to-App 授权 API

调用应用通过此协议唤起 Ksuser iOS 或 Android App，由用户核对账号、应用和资料范围并明确同意或拒绝。已安装的 App 使用 HTTPS Universal Link / App Link；网页备用入口复用同一请求。Ksuser 只向登记的调用应用回调交付授权码，不把通行证会话或用户密码交给调用应用。

## 接入前提和协议选择

| mode | 注册位置 | 客户端 | 兑换和身份 |
| --- | --- | --- | --- |
| `oauth` | 通行证开放平台 → 移动应用 | `public`＋已验证的对应 iOS / Android 平台 | `/oauth2/token`、`/oauth2/userinfo`；scope 仅 profile/email；返回应用专属 openid；UnionID 经管理员开通后返回同开发者范围 unionid，不返回 ID Token/真实 UUID |
| `official` | Console 官方应用 | `public` | `/official/token`、`/official/userinfo`；OIDC；按 scope 返回官方身份资料，必须验证 ID Token |

两套 client_id 和登记表彼此独立，不能混用。原生协议不接受 confidential，不支持在 App 中嵌入 client_secret。第三方应用与官方应用的身份隔离保持不变。

调用应用必须拥有回调 HTTPS 域名。iOS 配置 Associated Domains 和该域名的 `apple-app-site-association`（AASA）；Android 配置带 `autoVerify` 的 App Link Intent filter，并在回调域名部署 `/.well-known/assetlinks.json`，声明实际包名和全部登记的签名证书 SHA-256 指纹。API 校验已登记地址；回调路由还依赖操作系统验证域名与实际安装包的关联。回调须满足以下要求：

- 与所选注册表中的某个完整 redirect_uri 精确匹配。
- 使用 `https`，端口省略或明确为 443，包含以 `/` 开头的规范路径。
- 不含 URI userinfo、query、fragment，不含需 `normalize()` 消除的相对路径段。
- hostname 不能等于 `APP_MOBILE_BRIDGE_APP_LINK_ORIGIN` 的 hostname（默认 `auth.ksuser.cn`），避免回到通行证授权入口形成循环。

第三方须先完成[移动应用登记与平台验证](open-platform-applications.md)。`platform` 必须为 `ios` 或 `android`，校验该平台自己的验证状态、精确回调和配置修订；不能借另一个平台的验证结果发起授权。平台配置修订绑定 ticket 和授权码；修改后旧请求失效，即使重新验证成功也不能恢复旧请求。官方 public 应用仍走独立注册表，不要求第三方平台登记。

## 1. 创建请求

`POST /auth/native-authorization/create`，JSON，无需通行证登录或 CSRF。客户端先生成独立的随机 state、PKCE verifier；official 还须生成 nonce。每次请求使用新值，verifier 只保存在调用应用中。

```json
{
  "mode": "oauth",
  "platform": "ios",
  "authorization": {
    "clientId": "ksapp_xxx",
    "redirectUri": "https://client.example/ksuser/callback",
    "responseType": "code",
    "scope": "profile email",
    "state": "fM6uYq2X8pRq7HbvN0sZ4g",
    "codeChallenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    "codeChallengeMethod": "S256"
  }
}
```

Android SDK 使用相同请求结构，将顶层 `platform` 改为 `"android"`；不能省略平台。两平台可在同一 AppID 登记相同 HTTPS 回调，native 请求仍只绑定所选平台。

state 必须为 22–128 位 `[A-Za-z0-9_-]`。challenge 为 verifier 的 SHA-256 结果做无填充 Base64URL，恰好 43 位；method 固定 S256。verifier 为 43–128 位 `[A-Za-z0-9._~-]`。official 的 `authorization` 增加非空 `nonce`（最多 512 字符），并使用包含 openid 且已为应用开放的 scope；第三方 OAuth 不使用 nonce/openid scope。

成功返回统一业务响应，`data` 为：

```json
{
  "ticket": "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcY",
  "cancelSecret": "GRobHB0eHyAhIiMkJSYnKCkqKywtLi8w",
  "appLink": "https://auth.ksuser.cn/app/sdk-authorize?ticket=...",
  "webAuthorizationUrl": "https://auth.ksuser.cn/app/sdk-authorize/web?ticket=...",
  "expiresInSeconds": 300
}
```

ticket 和 cancelSecret 实际均为 32 位随机 Base64URL 字符串，以上值仅示意。App Link 仅携带 ticket，通过 iOS AASA 或 Android Intent filter 的精确路径匹配交给 Ksuser App，不依赖该路径存在网页路由。调用应用保留 cancelSecret 用于取消，不放进唤起链接或第三方网页。打开 appLink 交由系统唤起 Ksuser；需要网页备用方式时使用返回的 webAuthorizationUrl，不重复创建同一次请求。

服务端在创建时冻结请求，并验证应用启用状态、移动应用类别、public 类型、已验证的所选平台、登记回调、资料范围和 PKCE；不会信任链接携带的应用名称或改写后的授权字段。

## 2. 查看与批准

下列接口供 Ksuser App 或通行证网页批准界面使用。需要有效通行证登录；`decide` 还须先取得 `/auth/csrf-token` 的 CSRF cookie，并使用同一会话的 `X-XSRF-TOKEN` 请求头。第三方 SDK 不直接批准用户授权。

`GET /auth/native-authorization/context?ticket=...` 返回 `data`：

```json
{
  "ticket": "...",
  "appName": "示例应用",
  "logoUrl": "https://client.example/logo.png",
  "contactInfo": "support@client.example",
  "redirectUri": "https://client.example/ksuser/callback",
  "requestedScopes": ["profile", "email"],
  "alreadyAuthorized": false,
  "existingGrantMode": "PERSISTENT",
  "expiresInSeconds": 240,
  "officialApplication": false,
  "consoleApplication": false,
  "subjectName": null,
  "subjectVerificationType": null,
  "nativeAuthorization": true
}
```

界面使用服务端信息展示目标应用、回调、开发者认证、当前账号和请求范围，用户确认后提交 `POST /auth/native-authorization/decide`：

```json
{
  "ticket": "...",
  "approve": true,
  "grantMode": "PERSISTENT"
}
```

OAuth 的 grantMode 支持 PERSISTENT（默认）、ONE_TIME、TIME_LIMITED。限时模式增加 grantTtlSeconds，默认 3600，允许 300–2592000 秒。official 按现有官方授权策略持久记录，不使用这些期限参数。拒绝时提交 `approve: false`。

成功的 `data` 为 `{"returnUrl":"https://client.example/ksuser/callback?code=...&state=...","completed":true}`；拒绝时同一回调携带 `error=access_denied&state=...`。Ksuser 批准界面只打开服务端返回的 returnUrl。没有面向匿名发起方的结果轮询或 code 领取接口。

调用 App 必须先验证回调属于自己的待处理请求、state 完全一致且请求尚未完成，再用原 verifier 兑换一次性授权码。OAuth 见 [token 接口](oauth2-token.md)；官方流程还要按 [官方接入指南](official-app-integration.md)验证 ID Token 签名、iss、aud、exp 和原 nonce。token 兑换使用应用自己的 client_id，不携带通行证 Authorization 请求头。

## 3. 取消、重试和错误

`POST /auth/native-authorization/cancel`，JSON `{"ticket":"...","cancelSecret":"..."}`，无需通行证登录或 CSRF。成功 data 的 status 为 cancelled、expired 或 decided。cancelled 表示取消赢得竞争；decided 表示批准/拒绝已占有该请求，取消不能撤销已发放的 code。cancelSecret 不具有登录、批准或读取 code 的能力。

请求固定五分钟有效；查看、重试、取消都不延长期限。Redis 原子决策保证同一 ticket 的批准、拒绝与取消不能同时获胜。相同用户和相同决定在结果已保存时重试会返回同一 callback，不会新发 code；不同用户/决定或仍在处理的请求返回冲突。已兑换的 code 不能通过重试再次使用。

| HTTP / errorCode | 处理 |
| --- | --- |
| 400 / NATIVE_AUTHORIZATION_INVALID | 修正请求格式、state、PKCE 或回调；注册表验证错误也可能返回相应 OAuth/official 错误码 |
| 401 / UNAUTHENTICATED | 批准界面先登录，再读取原请求 |
| 403 / SECURITY_CSRF_INVALID | 批准界面刷新同一会话 CSRF 凭据 |
| 403 / NATIVE_AUTHORIZATION_CANCEL_INVALID | 取消密钥错误，不改动事务 |
| 410 / NATIVE_AUTHORIZATION_EXPIRED | 已过期或取消，生成新 state/verifier 并重新创建 |
| 409 / NATIVE_AUTHORIZATION_CONFLICT | 决策冲突或仍在处理；同一待处理决定可重试，不能改变已获胜决定 |
| 409 / NATIVE_AUTHORIZATION_FAILED | 发码/存储结果不确定，不能重放旧请求；重新创建完整授权事务 |
| 429 / NATIVE_AUTHORIZATION_RATE_LIMITED | 等待配额窗口恢复，避免循环创建 |
| 503 / NATIVE_AUTHORIZATION_UNAVAILABLE | 功能关闭、client_id 未开放、配置错误或暂不可用，停止本次原生唤起 |

收到发码或存储失败时，服务端保留独占决定直到原期限，避免因重试发出第二个 code。无法恢复或已经失败的事务须重新创建；不要复用旧 state/verifier。日志不得记录 code、verifier、ticket、cancelSecret、回调完整 query 或用户 token。

## 配置与上线

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| APP_NATIVE_AUTHORIZATION_ENABLED | false | 总开关，关闭时不允许创建、预览或决定 |
| APP_NATIVE_AUTHORIZATION_ALLOWED_CLIENT_IDS | 空 | 逗号分隔的 client_id；启用后空列表允许所有符合类别、平台验证和协议要求的客户端，非空仅放行列出的 ID |
| APP_NATIVE_AUTHORIZATION_CREATE_IP_PER_MINUTE | 30 | 每个 IP 的创建配额 |
| APP_NATIVE_AUTHORIZATION_CREATE_CLIENT_PER_MINUTE | 120 | 每个 client_id 的创建配额 |
| APP_MOBILE_BRIDGE_APP_LINK_ORIGIN | https://auth.ksuser.cn | App Link 和网页备用入口来源；须为 HTTPS 443 根来源，无 query/fragment |

create/cancel 的 CSRF 豁免仅作用于这两个无会话授权能力的入口。context/decide 继续受身份认证保护，decide 保留 CSRF；不为任意第三方网页开放额外 CORS。功能关闭后仍允许持正确 cancelSecret 的调用方清理请求。

已有移动平台注册表的环境增加 Android 授权无需新数据库迁移；尚未部署移动应用分类的环境先备份并执行 `sql/migrations/20261019_oauth_public_pkce.sql`。

先在总开关关闭时完成全部 API 节点升级，再部署 Web 备用入口、提供方 AASA / assetlinks，并发布包含对应链接入口和批准逻辑的 App。新授权码增加平台修订快照，旧 API 节点不能读取其 JSON；使用蓝绿整体切流或维护窗口完成 API 升级，不能让新旧 API 同时处理发码和兑码，也不能仅依赖 native 总开关阻止普通网页发出新格式授权码。新 API 可在原授权码有效期（默认 300 秒）内继续读取旧格式：只有 `mobileRevision` 的旧移动授权码严格按 iOS 修订验证，绝不回退尝试 Android。

随后为调用应用登记所用平台资料和精确 HTTPS 回调，发布关联文件并验证；只将试点 client_id 加入 allowed-client-ids 并开启总开关。验证 App 唤起、网页备用、拒绝、取消、过期、重复回调和两套身份结果，再扩大放行范围。回退时关闭总开关；已有网页登录、扫码和 confidential 接入继续使用原协议。

原生授权码只冻结指定平台；普通网页和扫码移动授权冻结发码时所有已验证且匹配回调的平台修订，兑码须至少有一个原修订仍有效。新登记的平台不能恢复旧授权码，另一平台仍有效也不能恢复已失效的原生平台授权码。
