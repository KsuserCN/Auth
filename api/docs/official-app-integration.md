# Ksuser 官方应用接入指南

本文说明外部网站、后端服务、桌面端和移动端如何使用 Ksuser 通行证登录，并按用户同意的范围读取资料。这里的“官方应用”是由 Ksuser 管理员在 Console 注册的应用，使用独立的 `/official` OIDC 接口。

## 先选对接入类型

Ksuser 当前有三套独立的客户端登记和协议入口。创建或接入前，先确认使用哪一套：

| 接入类型 | 入口 | 适用情况 | 身份标识 |
| --- | --- | --- | --- |
| **官方应用** | `/official` | 经 Ksuser 管理员登记、需要用户明确同意并读取官方资料的服务 | `sub` / `uuid` 是用户真实 UUID |
| 开放平台 OAuth/OIDC | `/oauth2` | 开放平台中登记的一般第三方应用 | 使用应用专属的 `openid` 等标识；新令牌不提供真实 UUID |
| 内部 SSO | `/sso` | Ksuser 自己管理的内部服务 | 使用 SSO 客户端配置的应用专属标识 |

三套登记彼此独立。官方应用不能拿普通开放平台或 SSO 的 `client_id`、`client_secret` 来调用；旧的 [OIDC 本地 Demo](../../demos/oidc-local-demo/README.md) 使用 `/oauth2`，不能直接当作官方应用示例。

官方应用协议是**用户登录授权**：它只支持授权码模式，不提供机器到机器的 `client_credentials` 或 refresh token。没有用户参与的后台任务不适合使用此流程。

## 公开客户端和服务端客户端

官方应用的客户端类型在创建时选定，之后不能更改。两种类型都必须使用授权码模式、`state`、`nonce` 和 PKCE `S256`；`client_secret` 不能替代 PKCE。

| 类型 | 适用程序 | 换取令牌的位置 | `client_secret` | PKCE |
| --- | --- | --- | --- | --- |
| `public`（公开客户端 / SPA） | 浏览器 SPA、桌面或移动原生应用，以及不能安全保管密钥的程序 | 客户端可直接请求 token endpoint | 没有，也不得提交 | 必须 |
| `confidential`（服务端客户端） | 有受控后端、能安全保存服务端凭据的网站或服务 | 服务端请求 token endpoint | 必须在服务端保存并提交 | 必须 |

不要把 `confidential` 密钥放进网页 JavaScript、移动安装包、桌面安装包、公开仓库或日志。放进客户端程序的密钥可以被提取，因此不能让原生应用冒充服务端客户端。若一个服务同时有 SPA 和后端，通常以 `confidential` 应用让后端兑换令牌；如果客户端本身不可信，则注册为 `public`，全程依赖 PKCE。

## 1. 由管理员登记应用

在 [Ksuser Console](https://console.ksuser.cn) 的「官方应用」页面创建应用。当前只有 `SUPER_ADMIN` 可以管理官方应用；接入团队需要向 Ksuser 管理员提供：

1. 展示给用户的应用名称和图标。
2. 客户端类型：`public` 或 `confidential`。
3. 每个环境使用的**完整回调 URI**，例如 `https://service.example.com/auth/ksuser/callback`。
4. 实际需要的资料范围。只申请业务必需范围；`openid` 必须启用。

回调地址要求：

- 必须是 `https://`，或开发时使用 `http://localhost`、`http://127.0.0.1`、`http://[::1]` 回环地址。
- 不得包含 URI userinfo 或片段（`#...`）。
- 授权和兑换时必须与登记值**逐字完全一致**，包括 scheme、host、port、path 和 query；不能用通配符，也不能在兑换时换成同一应用的另一个回调地址。
- 每个应用最多可登记 20 个回调 URI。生产和本地开发建议分别登记各自的精确地址。
- 自定义 scheme（例如 `myapp://callback`）当前不符合 Console 的回调校验要求。原生应用使用已配置的 HTTPS App Link / Universal Link；桌面端本地调试可使用 loopback 回调。

创建后记录 `client_id`。`public` 不生成密钥。`confidential` 的密钥只在创建或管理员轮换时显示一次，必须立即放入服务端密钥管理系统。数据库仅保存密钥哈希；忘记密钥时只能轮换，轮换会使旧密钥立即失效。`client_id` 和客户端类型创建后固定，管理员可以更新回调、资料范围、名称和启用状态。

## 2. 服务地址与 Discovery

生产环境通过 OIDC Discovery 获取元数据：

```text
GET https://api.ksuser.cn/official/.well-known/openid-configuration
```

生产环境当前地址：

| 用途 | URL |
| --- | --- |
| 授权页面 | `https://auth.ksuser.cn/official/authorize` |
| Token | `https://api.ksuser.cn/official/token` |
| UserInfo | `https://api.ksuser.cn/official/userinfo` |
| JWKS 公钥 | `https://api.ksuser.cn/official/jwks` |
| Issuer | `https://api.ksuser.cn/official` |

请优先读取 Discovery 中的 endpoint 和 issuer 配置，避免把环境地址散落在代码里。当前支持授权码 `code`、授权码 grant、PKCE `S256` 和 ID Token 签名算法 `RS256`；token endpoint 支持 `none`、`client_secret_post`、`client_secret_basic` 客户端认证方式。生产环境的示例地址不要与本地开发配置混用。

## 3. 生成一次性登录事务并跳转授权

每次登录都新生成下列值，并将它们绑定到当前登录事务：

- `state`：至少 128 bit 随机值，用于回调防伪；回调时必须与发起值一致。
- `nonce`：至少 128 bit 随机值，用于绑定并验证 ID Token。
- `code_verifier`：PKCE 随机字符串，长度 43–128 个字符，字符集为 `[A-Za-z0-9._~-]`。客户端暂存原值，兑换授权码时提交。
- `code_challenge`：`BASE64URL(SHA256(ASCII(code_verifier)))`，去掉 Base64 padding，结果为 43 个字符；方法固定为 `S256`。

将浏览器或系统浏览器导航到授权地址，并 URL 编码参数：

```text
https://auth.ksuser.cn/official/authorize
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=https%3A%2F%2Fservice.example.com%2Fauth%2Fksuser%2Fcallback
  &response_type=code
  &scope=openid%20profile%20email
  &state=RANDOM_STATE
  &nonce=RANDOM_NONCE
  &code_challenge=BASE64URL_SHA256_CHALLENGE
  &code_challenge_method=S256
```

实际请求必须是一条正常 URL；上面的换行只为阅读。`scope` 必须包含 `openid`，且不能超过该应用获准的范围。用户会在 Ksuser 通行证登录并看到授权说明。首次为此应用授权，或请求尚未授权的资料范围时，需要用户再次确认。

授权成功后 Ksuser 回到已登记回调并带上 `code` 和原 `state`。用户拒绝时回调带 `error=access_denied` 和 `state`，不会返回授权码。服务必须先校验 `state`，再处理 `code` 或错误；不得把 state、nonce 或 PKCE verifier 写入长期日志。

## 通过 iOS App 扫码快捷登录

所有通过 `/official` 注册的官方应用（`public` 和 `confidential`）均可使用统一扫码登录，无需为每个应用单独开发手机授权逻辑。继续按第 3 节生成原有的 `state`、`nonce` 和 PKCE 请求并导航到 `/official/authorize`：默认展示网页登录与授权，电脑浏览器提供「使用扫码登录」按钮，手机 UA（包括 iPad 桌面模式）不显示该按钮，也不会自动唤起 App。在电脑授权 URL 加上 `login_method=qr` 可直接展示扫码入口；这个参数只选择页面登录方式，不参与 token 兑换。

用户用 Ksuser iOS App 的「扫码」扫描二维码，核对服务端提供的应用名称、图标、回调网站、当前账号和具体资料范围后，选择同意或拒绝。手机确认后，原浏览器自动返回已登记回调，收到原 `state` 和一次性 `code`（拒绝则收到 `error=access_denied`）。应用按下文的原有流程兑换令牌，客户端密钥、nonce 校验和 PKCE 要求保持一致。手机确认页的「同时登录通行证网页端」默认关闭：默认只授权当前应用，不改变浏览器的通行证账号。用户主动开启后，原浏览器通过私有结果中的一次性凭据建立当前手机账号的通行证网页会话；凭据不会放入二维码、手机回跳链接或第三方回调。拒绝授权不会建立网页会话。

公共官方应用允许普通用户扫码登录；Console 仍要求 `SUPER_ADMIN`，手机扫码不会绕过应用既有的账号权限检查。创建、手机确认和令牌兑换都会校验应用是否启用、精确回调和资料范围；撤销授权与应用停用沿用原有规则。

二维码有效两分钟，刷新或切换网页登录会取消旧请求。二维码仅携带手机审批码；读取和领取回调的轮询凭据仅保存在发起浏览器，领取回调只能成功一次。OAuth、SSO 和官方应用统一复用 `/auth/mobile-authorization/qr/create`、`qr/status`、`consume`、`cancel`，手机读取 `context` 并提交 `decide`。写请求需要 CSRF，手机确认需要通行证登录；二维码应通过统一授权页展示，这些接口不对任意应用页面额外开放 CORS。旧版 `/official/qr/*` 协议仍保留兼容。

扫码页的「Ksuser iOS App」是新标签页 App Store 链接：当前语言为简体中文时使用中国区，为 English 时使用美国区。

发布此功能需同步更新 API、通行证 Web 和 iOS App，无新增数据库迁移。Console 自身的直接扫码入口继续可用。

## 4. 用授权码兑换令牌

通过 HTTPS 对 Discovery 的 token endpoint 发出 `application/x-www-form-urlencoded` POST。授权码五分钟有效且只能使用一次；`redirect_uri` 必须和授权请求、登记值完全一致。

### 公开客户端

```bash
curl -X POST 'https://api.ksuser.cn/official/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'code=CODE_FROM_CALLBACK' \
  --data-urlencode 'client_id=YOUR_CLIENT_ID' \
  --data-urlencode 'redirect_uri=https://service.example.com/auth/ksuser/callback' \
  --data-urlencode 'code_verifier=ORIGINAL_CODE_VERIFIER'
```

### 服务端客户端

只从可信服务端提交密钥：

```bash
curl -X POST 'https://api.ksuser.cn/official/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=authorization_code' \
  --data-urlencode 'code=CODE_FROM_CALLBACK' \
  --data-urlencode 'client_id=YOUR_CLIENT_ID' \
  --data-urlencode 'client_secret=SERVER_SIDE_SECRET' \
  --data-urlencode 'redirect_uri=https://service.example.com/auth/ksuser/callback' \
  --data-urlencode 'code_verifier=ORIGINAL_CODE_VERIFIER'
```

服务端也可以依 OIDC 库的支持，使用 `client_secret_basic`。公开客户端不可提交 `client_secret`；服务端客户端缺少或提交错误密钥都会收到 `invalid_client`。两种客户端都必须正确提交 verifier。

成功响应示意：

```json
{
  "access_token": "ofa_...",
  "id_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "openid profile email"
}
```

`access_token` 是不透明的 Bearer token，不是可由客户端自行解析的 JWT；有效期为 2 小时。当前响应不提供 `refresh_token`。到期后重新走授权码流程；如果用户的授权仍有效且请求范围没有扩大，通行证可以复用已记住的授权。

## 5. 验证 ID Token 并读取资料

ID Token 使用 Discovery 声明的 `RS256` 签名。用标准 OIDC 库和 Discovery/JWKS 验证签名及以下字段，不要只 Base64 解码后就信任 claims：

- `iss` 必须等于 Discovery 的 `issuer`。
- `aud` 必须包含当前应用的 `client_id`。
- `exp` 必须尚未到期。
- `nonce` 必须与此登录事务生成的 nonce 完全相同。
- `sub` 是用户真实 UUID；响应还包含相同 UUID 的 `uuid` claim。

拿到 ID Token 不代表之后的 API 请求都应当信任其过期状态。获取最新授权资料时，以 UserInfo endpoint 为准：

```bash
curl 'https://api.ksuser.cn/official/userinfo' \
  -H 'Authorization: Bearer ACCESS_TOKEN'
```

UserInfo 返回 `sub`、`uuid` 和请求 scope 允许的字段。当前资料范围如下：

| Scope | 返回字段 |
| --- | --- |
| `openid` | `sub`、`uuid`：Ksuser 用户真实 UUID |
| `profile` | `username`、`preferred_username`、`nickname`、`avatar_url`、`picture` |
| `email` | `email`、`email_verified` |
| `real_name` | `real_name` |
| `gender` | `gender` |
| `birth_date` | `birth_date`（日期字符串） |
| `region` | `region` |
| `bio` | `bio` |

如某个字段未申请对应 scope，不会因为 access token 中存在其他 scope 而返回。业务侧应以稳定的 `sub`/`uuid` 作为账号关联键，不要用可变的用户名或邮箱代替。

## 浏览器、桌面和移动端注意事项

- **浏览器 SPA**：使用 `public`；通过顶层导航打开通行证授权页；在当前浏览器会话中暂存 state、nonce、verifier；授权回调后由 SPA 用 verifier 请求 token。不要把用户的 Ksuser 密码直接收集到业务站点。
- **有后端的网站**：使用 `confidential`；回调由后端接收并兑换授权码，密钥只在后端保存。可由服务端设置站点自己的登录会话 Cookie。
- **Android / iOS / 桌面原生应用**：使用 `public`，通过系统浏览器或安全系统认证会话完成登录，不要嵌入 client secret。移动端配置 HTTPS App Link / Universal Link；桌面应用可在本地使用 loopback 回调。当前 Console 不接受自定义 URI scheme。
- `/official/token` 和 `/official/userinfo` 按应用登记的回调 URI 来源校验浏览器 `Origin` 并开放对应 CORS。浏览器应用的页面来源需与某个已登记回调的 origin 相同；加 CORS 头不会放宽回调 URI 的精确匹配。
- token endpoint 仅允许 `authorization_code`。如果服务端没有用户浏览器登录的环节，不要将 client secret 当成机器身份凭据来使用。

## 用户撤销与应用停用

用户可以在 Ksuser 通行证的「隐私」页面查看和撤销官方应用授权。撤销后，与授权关联的旧 access token 会立即失效；之后应用需重新发起授权。官方应用被管理员停用或删除后，令牌会失效；如果管理员缩小应用获准范围并移除了某个令牌所含的范围，该令牌也无法继续使用。应用应妥善处理 `invalid_token`，清理自己的本地登录会话并提示用户重新登录。

## 常见错误

| 错误 | 常见原因 | 检查方式 |
| --- | --- | --- |
| `invalid_client` | client_id 不存在或停用；服务端密钥缺失或错误；公开客户端错误提交密钥 | 确认使用 `/official` 登记的 ID 和对应类型；必要时由管理员轮换密钥 |
| `invalid_request` | `response_type`、`state`、`nonce` 等缺失或格式不符 | 授权请求始终发送 `response_type=code`、state、nonce 和完整 PKCE 参数 |
| `invalid_scope` | scope 不含 `openid`、不受支持或未在应用登记 | 只请求应用获准且业务必需的 scope |
| `invalid_grant` | code 过期/已使用、回调 URI 不完全相同或 verifier 不匹配 | 每个事务只兑换一次，核对原 callback 和 verifier |
| `invalid_token` | token 过期、用户已撤销授权或应用不可用 | 清理本地会话并重新发起登录授权 |
| `access_denied` | 用户拒绝授权，或请求不被允许 | 尊重拒绝结果，不要循环重定向；需要时向用户解释必要范围 |
| 浏览器 CORS 错误 | 页面 origin 未对应到该应用登记的回调来源，或请求发往了其他 API 路径 | 精确检查回调来源；开放应用 origin 的仅是 official token 和 userinfo 接口 |

本地开发时可按 [Console 文档的本地配置](../../console/README.md#本地开发)设置 API issuer、授权页地址和 localhost 回调；本地调试应用必须注册在本地开发数据库，不能把生产应用密钥带到本地环境。
