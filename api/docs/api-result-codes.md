# API 详细错误码与四端文案对照

本文件由 `npm run generate:api-codes` 从 [唯一源文件](../../shared/api-result-codes.json) 自动生成。新增结果必须分配永久稳定的码；已发布的码禁止重新编号、复用含义或删除，文案可以调整。

## 响应契约

普通业务接口统一返回 `code`（HTTP/流程数字码）、`errorCode`（详细结果码，成功和中间态同样提供）、可选 `params` 与可选 `data`。不返回 `msg`。HTTP 状态保持原有含义；`200` 成功，`201` MFA，`202` 需要绑定或验证，错误使用对应 4xx/5xx。

```json
{"code":400,"errorCode":"AUTH_020","params":{"min":8,"max":64}}
```

参数仅携带结构化业务值（次数、长度、提供商等），不得携带异常原文、上游错误详情或用户提示文字。文案使用 `{参数名}` 占位符。四端先查本地表，再替换参数；未知码、缺参数和不规范响应使用本地通用提示。不得退回显示服务器 `msg` 或 `error_description`。

OAuth2/官方应用的 token、userinfo、JWKS、OIDC discovery 成功响应保持协议原始数据格式；错误保留标准 `error` 字段，扩展 `errorCode` / `params`。标准可选字段 `error_description` 使用稳定结果码，不提供展示文案。安全过滤器和 Spring MVC 未处理异常也返回同样的详细码。

密码策略返回 `requirementCode` / `requirementParams`。自适应认证返回 `reasonCodes: [{errorCode, params?}]`、`recommendedActionCode`、`alertTitleCode`、`alertMessageCode`。OAuth 的嵌套 `data.message` / `data.reason` 和 TOTP `data.message` 不再作为提示来源，客户端统一读取外层详细码。

本表也定义网络故障、超时、用户取消 Passkey 等客户端结果码；流程状态（例如 pending/approved/rejected/expired）和数据字段保留原有含义。

历史敏感操作日志的 `failureReason` 属于持久审计记录：已知完整码在四端按本表显示，未知历史原文保留，不能用它判断流程。邮件/推送文本以及地理位置、名称等业务内容不是本次请求结果，保持原有格式。本地交互提示（已复制、步骤完成等）仍由客户端生成。

## 维护与发布

执行 `npm run generate:api-codes` 生成 Java 码枚举、Web/Swift/Kotlin/Dart 四端表和本文件；执行 `npm run check:api-codes` 验证无漂移。后端只能通过枚举或带码的业务异常构造响应，不依赖原因文字的运行时匹配。四端按码进行认证、敏感验证和桥接分支判断，不能比较显示文字。

这是一项响应契约变更：后端与四端客户端应配套发布。旧客户端依赖 `msg`，不能获取新版详细原因；新版客户端访问旧后端时使用本地通用提示。

## 客户端语言

Web、Desktop、iOS 按当前生效的 `zh-Hans` / `en` 选择本地结果表；Android 同样按当前显示语言选择本地结果表。源文件保留 `message` 并新增完整的 `messageEn`，生成时验证两种文案的参数一致。语言变化不改变结果码或响应格式。验证码邮件通过发送请求的 `Accept-Language` 单独选择文案。

## 对照表

模块编号码是永久标识；前缀仅用于分组，并非 HTTP 状态。HTTP 状态和是否继续流程始终读取 `code` / HTTP 状态及结构化业务字段。

| 详细结果码 | 简体中文文案 | 英文文案 | 参数 | 定义来源 |
|---|---|---|---|---|
| `REQUEST_SUCCESS` | 操作成功 | Operation completed | — | MobileAuthorizationController.java |
| `REQUEST_FAILED` | 请求失败，请重试 | Request failed. Please try again. | — | 公共/客户端 |
| `BAD_REQUEST` | 请求参数不正确 | Invalid request parameters | — | 公共/客户端 |
| `UNAUTHENTICATED` | 未登录或登录状态已失效，请重新登录 | You are signed out or your session has expired. Please sign in again. | — | AppleAuthController.java, AuthController.java, MobileAuthorizationController.java, Oauth2PlatformController.java, OauthController.java, PushDeviceController.java, retired-sso, TotpController.java, AppleAuthService.java |
| `FORBIDDEN` | 无权限 | You do not have permission to perform this action | — | 公共/客户端 |
| `SECURITY_CSRF_INVALID` | 安全校验失败，请刷新页面后重试 | Security check failed. Refresh the page and try again. | — | 公共/客户端 |
| `SENSITIVE_VERIFICATION_REQUIRED` | 请先完成敏感操作验证 | Verify your identity before continuing | — | AppleAuthController.java, AuthController.java, OauthController.java, TotpController.java |
| `AUTH_MFA_REQUIRED` | 需要 MFA 验证 | Multi-factor authentication is required | — | AppleAuthController.java, AuthController.java, OauthController.java |
| `OAUTH_BIND_REQUIRED` | 第三方账号尚未绑定，请创建或绑定账号 | This third-party account is not linked. Create or link an account. | — | AppleAuthController.java, OauthController.java |
| `SERVER_ERROR` | 服务暂时不可用，请稍后重试 | The service is temporarily unavailable. Please try again later. | — | OauthController.java |
| `NOT_FOUND` | 请求的资源不存在 | The requested resource was not found | — | 公共/客户端 |
| `METHOD_NOT_ALLOWED` | 请求方法不支持 | This request method is not supported | — | 公共/客户端 |
| `UNSUPPORTED_MEDIA_TYPE` | 不支持的请求类型 | This content type is not supported | — | 公共/客户端 |
| `RATE_LIMITED` | 请求过于频繁，请稍后重试 | Too many requests. Please try again later. | — | AppleAuthController.java, OauthController.java |
| `REQUEST_CONFLICT` | 请求状态冲突，请重试 | The request conflicts with the current state. Please try again. | — | 公共/客户端 |
| `NETWORK_ERROR` | 网络连接失败，请检查网络 | Could not connect. Check your network connection. | — | 公共/客户端 |
| `REQUEST_TIMEOUT` | 请求超时，请稍后重试 | The request timed out. Please try again later. | — | 公共/客户端 |
| `INVALID_RESPONSE` | 服务返回了无法解析的数据 | The service returned an unreadable response | — | 公共/客户端 |
| `PASSKEY_CANCELLED` | 用户取消了当前 Passkey 操作 | The Passkey operation was cancelled | — | 公共/客户端 |
| `PASSKEY_UNSUPPORTED` | 当前浏览器不支持该 Passkey 操作 | Your browser does not support this Passkey operation | — | 公共/客户端 |
| `PASSKEY_SECURITY_ERROR` | 浏览器安全校验失败，请确认当前使用受信任地址 | Browser security check failed. Make sure you are using a trusted address. | — | 公共/客户端 |
| `PASSKEY_CREDENTIAL_UNAVAILABLE` | 当前账号没有可用的 Passkey，或 Passkey 状态不正确 | No usable Passkey is available for this account, or its state is invalid | — | 公共/客户端 |
| `SESSION_NOT_AUTHENTICATED` | 尚未完成登录，请重新登录 | Sign-in is incomplete. Please sign in again. | — | 公共/客户端 |
| `INTERNAL_ERROR` | 服务暂时不可用，请稍后重试 | The service is temporarily unavailable. Please try again later. | — | 公共/客户端 |
| `AUTH_USERNAME_EXISTS` | 用户名已存在 | This username is already taken | — | 公共/客户端 |
| `AUTH_USERNAME_AVAILABLE` | 用户名可用 | This username is available | — | 公共/客户端 |
| `AUTH_RECOVERY_CODE_INVALID` | 恢复码错误或已使用 | The recovery code is incorrect or has already been used | — | 公共/客户端 |
| `AUTH_VERIFICATION_CODE_INVALID` | 验证码错误或已过期 | The verification code is incorrect or has expired | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_0` | 密码强度不足 | Password is too weak | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_1` | 密码强度不足：需包含大写字母 | Password is too weak: include uppercase letters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_2` | 密码强度不足：需包含小写字母 | Password is too weak: include lowercase letters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_3` | 密码强度不足：需包含大写字母、小写字母 | Password is too weak: include uppercase letters and lowercase letters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_4` | 密码强度不足：需包含数字 | Password is too weak: include numbers | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_5` | 密码强度不足：需包含大写字母、数字 | Password is too weak: include uppercase letters and numbers | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_6` | 密码强度不足：需包含小写字母、数字 | Password is too weak: include lowercase letters and numbers | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_7` | 密码强度不足：需包含大写字母、小写字母、数字 | Password is too weak: include uppercase letters, lowercase letters and numbers | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_8` | 密码强度不足：需包含特殊字符 | Password is too weak: include special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_9` | 密码强度不足：需包含大写字母、特殊字符 | Password is too weak: include uppercase letters and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_10` | 密码强度不足：需包含小写字母、特殊字符 | Password is too weak: include lowercase letters and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_11` | 密码强度不足：需包含大写字母、小写字母、特殊字符 | Password is too weak: include uppercase letters, lowercase letters and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_12` | 密码强度不足：需包含数字、特殊字符 | Password is too weak: include numbers and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_13` | 密码强度不足：需包含大写字母、数字、特殊字符 | Password is too weak: include uppercase letters, numbers and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_14` | 密码强度不足：需包含小写字母、数字、特殊字符 | Password is too weak: include lowercase letters, numbers and special characters | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_15` | 密码强度不足：需包含大写字母、小写字母、数字、特殊字符 | Password is too weak: include uppercase letters, lowercase letters, numbers and special characters | — | 公共/客户端 |
| `APPLE_001` | 挑战已创建 | Challenge created | — | AppleAuthController.java |
| `APPLE_002` | Apple 绑定成功 | Apple account linked | — | AppleAuthController.java |
| `APPLE_003` | 身份验证成功 | Identity verified | — | AppleAuthController.java |
| `APPLE_004` | 获取成功 | Retrieved successfully | — | AppleAuthController.java, AuthController.java, InfoController.java, Oauth2PlatformController.java, retired-sso |
| `APPLE_005` | Apple 解绑成功 | Apple account unlinked | — | AppleAuthController.java |
| `APPLE_006` | 已接收 | Received | — | AppleAuthController.java |
| `APPLE_007` | Apple 登录成功 | Signed in with Apple | — | AppleAuthController.java |
| `APPLE_008` | Apple 服务尚未配置或暂时不可用，请稍后重试 | Apple sign-in is not configured or is temporarily unavailable. Please try again later. | — | AppleAuthController.java |
| `APPLE_009` | 账号或 Apple 身份已存在，请重新登录或绑定 | This account or Apple identity already exists. Sign in again or link the account. | — | AppleAuthController.java |
| `APPLE_010` | Apple 通知处理暂时失败，请稍后重试 | Could not process the Apple notification. Please try again later. | — | AppleAuthController.java, AppleAuthService.java |
| `APPLE_011` | 账号不存在 | Account not found | — | AppleAuthController.java, AppleAuthService.java |
| `APPLE_012` | 会话已失效，请重新登录 | Your session has expired. Please sign in again. | — | AppleAuthController.java |
| `APPLE_013` | 当前会话已冻结，请重新登录 | Your session has been frozen. Please sign in again. | — | AppleAuthController.java |
| `AUTH_001` | 服务正常 | Service is available | — | AuthController.java |
| `AUTH_002` | 用户名不能为空 | Username is required | — | AuthController.java |
| `AUTH_003` | 类型不能为空 | Type is required | — | AuthController.java |
| `AUTH_004` | 类型只能是 register、login、change-email 或 sensitive-verification | Type must be register, login, change-email or sensitive-verification | — | AuthController.java |
| `AUTH_005` | 用户不存在 | User not found | — | AuthController.java, MobileAuthorizationController.java, Oauth2PlatformController.java, OauthController.java, SensitiveLogController.java, retired-sso, TotpController.java, AccountRecoveryService.java, MobileBridgeService.java |
| `AUTH_006` | 当前用户未绑定邮箱 | No email address is linked to the current user | — | AuthController.java |
| `AUTH_007` | 邮箱不能为空 | Email address is required | — | AuthController.java |
| `AUTH_008` | 邮箱格式不正确 | Invalid email address | — | AuthController.java |
| `AUTH_009` | 邮箱已被使用 | This email address is already in use | — | AuthController.java |
| `AUTH_010` | 验证码错误次数过多，该邮箱已被锁定1小时 | Too many incorrect verification codes. This email address has been locked for 1 hour. | — | AuthController.java |
| `AUTH_011` | 注册过于频繁，请稍后再试 | Too many registration attempts. Please try again later. | — | AuthController.java |
| `AUTH_012` | 发送过于频繁，请1分钟后再试 | Too many requests. Please try again in 1 minute. | — | AuthController.java |
| `AUTH_013` | 当前网络发送验证码次数过多，请稍后再试 | Too many codes sent from this network. Please try again later. | — | AuthController.java |
| `AUTH_014` | 该邮箱发送次数过多，每小时最多发送14次 | Too many codes sent to this email address. The limit is 14 per hour. | — | AuthController.java |
| `AUTH_015` | 邮件发送失败，请稍后重试 | Could not send the email. Please try again later. | — | AuthController.java |
| `AUTH_016` | 验证码已发送 | Verification code sent | — | AuthController.java |
| `AUTH_017` | 密码不能为空 | Password is required | — | AuthController.java |
| `AUTH_018` | 验证码不能为空 | Verification code is required | — | AuthController.java, TotpController.java |
| `AUTH_019` | 用户名格式不正确（3-20字符，仅字母数字下划线连字符） | Username must contain 3–20 characters: letters, numbers, underscores or hyphens | — | AuthController.java |
| `AUTH_020` | 密码长度必须在{min}-{max}个字符之间 | Password must contain between {min} and {max} characters | `min`, `max` | AuthController.java |
| `AUTH_021` | 密码过于简单，请使用更复杂的密码 | This password is too simple. Choose a stronger password. | — | AuthController.java |
| `AUTH_022` | 输入包含非法字符 | Your input contains invalid characters | — | AuthController.java |
| `AUTH_023` | 请先获取验证码 | Request a verification code first | — | AuthController.java |
| `AUTH_024` | 邮箱不匹配（{count}/5） | Email address does not match ({count}/5) | `count` | AuthController.java |
| `AUTH_025` | 发送验证码的设备与当前设备不匹配（{count}/5） | The device requesting the code does not match the current device ({count}/5) | `count` | AuthController.java |
| `AUTH_026` | 验证码已过期，请重新获取（{count}/5） | The verification code has expired. Request a new code ({count}/5). | `count` | AuthController.java |
| `AUTH_027` | 验证码错误（{count}/5） | Incorrect verification code ({count}/5) | `count` | AuthController.java |
| `AUTH_028` | 邮箱已被注册 | This email address is already registered | — | AuthController.java |
| `AUTH_029` | 用户名已存在 | This username is already taken | — | AuthController.java, OauthController.java |
| `AUTH_030` | key 不支持 | Unsupported key | — | AuthController.java |
| `AUTH_031` | 邮箱已存在 | This email address already exists | — | AuthController.java, OauthController.java |
| `AUTH_032` | 注册成功 | Account created | — | AuthController.java |
| `AUTH_033` | 邮箱未注册或验证码错误 | This email address is not registered or the verification code is incorrect | — | AuthController.java |
| `AUTH_034` | 登录成功 | Signed in | — | AuthController.java, OauthController.java |
| `AUTH_035` | 登录请求过于频繁，请稍后再试 | Too many sign-in attempts. Please try again later. | — | AuthController.java |
| `AUTH_036` | 邮箱或密码错误 | Incorrect email address or password | — | AuthController.java, OauthController.java |
| `AUTH_037` | 字段名不能为空 | Field name is required | — | AuthController.java |
| `AUTH_038` | 不支持的字段名 | Unsupported field name | — | AuthController.java |
| `AUTH_039` | 布尔字段 value 不能为空 | The boolean field value is required | — | AuthController.java |
| `AUTH_040` | 偏好字段 stringValue 不能为空 | The preference field stringValue is required | — | AuthController.java |
| `AUTH_041` | 请先启用 MFA 再设置登录MFA优先方式 | Enable MFA before choosing a preferred sign-in MFA method | — | AuthController.java |
| `AUTH_042` | 登录MFA优先方式只能是 totp 或 passkey | The preferred sign-in MFA method must be totp or passkey | — | AuthController.java |
| `AUTH_043` | 当前用户未启用所选 MFA 方式 | The selected MFA method is not enabled for this user | — | AuthController.java |
| `AUTH_044` | 敏感验证优先方式只能是 password、email-code、passkey、totp 或 apple | The preferred identity verification method must be password, email-code, passkey, totp or apple | — | AuthController.java |
| `AUTH_045` | 当前用户不可用所选敏感验证方式 | The selected identity verification method is not available for this user | — | AuthController.java |
| `AUTH_046` | 更新成功 | Updated successfully | — | AuthController.java, Oauth2PlatformController.java, retired-sso |
| `AUTH_047` | RefreshToken不存在 | Refresh token is missing | — | AuthController.java |
| `AUTH_048` | RefreshToken已失效 | The refresh token is no longer valid | — | AuthController.java |
| `AUTH_049` | Token类型无效，应为RefreshToken | Invalid token type. A refresh token is required. | — | AuthController.java |
| `AUTH_050` | RefreshToken无效或已过期 | The refresh token is invalid or has expired | — | AuthController.java |
| `AUTH_051` | 刷新成功 | Session refreshed | — | AuthController.java |
| `AUTH_052` | 跨端票据已创建 | Session transfer ticket created | — | AuthController.java |
| `AUTH_053` | 跨端票据创建失败 | Could not create the session transfer ticket | — | AuthController.java |
| `AUTH_054` | transferCode 不能为空 | transferCode is required | — | AuthController.java, SessionTransferService.java |
| `AUTH_055` | target 不能为空 | target is required | — | AuthController.java |
| `AUTH_056` | 跨端登录成功 | Signed in on the other device | — | AuthController.java |
| `AUTH_057` | 跨端登录失败 | Could not sign in on the other device | — | AuthController.java |
| `AUTH_058` | 请求体不能为空 | Request body is required | — | AuthController.java |
| `AUTH_059` | 移动桥接挑战已创建 | Mobile bridge challenge created | — | AuthController.java |
| `AUTH_060` | 移动桥接挑战创建失败 | Could not create the mobile bridge challenge | — | AuthController.java |
| `AUTH_061` | challengeId 不能为空 | challengeId is required | — | AuthController.java |
| `AUTH_062` | 网页登录确认成功 | Web sign-in confirmed | — | AuthController.java |
| `AUTH_063` | 网页登录确认失败 | Could not confirm web sign-in | — | AuthController.java |
| `AUTH_064` | 移动桥接挑战已取消 | Mobile bridge challenge cancelled | — | AuthController.java |
| `AUTH_065` | 移动桥接挑战取消失败 | Could not cancel the mobile bridge challenge | — | AuthController.java |
| `AUTH_066` | 移动桥接挑战已过期 | The mobile bridge challenge has expired | — | AuthController.java |
| `AUTH_067` | 获取移动桥接状态失败 | Could not retrieve the mobile bridge status | — | AuthController.java |
| `AUTH_068` | 当前会话风险过高，已被冻结，请重新登录 | This session has been frozen because its risk is too high. Please sign in again. | — | AuthController.java |
| `AUTH_069` | 当前登录会话无效，请重新登录后再试 | The current sign-in session is invalid. Sign in again and retry. | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_070` | 恢复授权已生成 | Recovery authorization created | — | AuthController.java |
| `AUTH_071` | 恢复授权生成失败 | Could not create the recovery authorization | — | AuthController.java |
| `AUTH_072` | recoveryCode 不能为空 | recoveryCode is required | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_073` | 恢复授权不存在、已失效或已被使用 | The recovery authorization does not exist, has expired or has already been used | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_074` | 发起恢复授权的设备已退出登录，请重新发起 | The device that issued this recovery authorization is signed out. Start again. | — | AuthController.java |
| `AUTH_075` | 查询成功 | Retrieved successfully | — | AuthController.java, OauthController.java |
| `AUTH_076` | 新密码不能为空 | New password is required | — | AuthController.java |
| `AUTH_077` | 恢复授权读取失败 | Could not read the recovery authorization | — | AuthController.java |
| `AUTH_078` | 账号恢复成功 | Account recovered | — | AuthController.java |
| `AUTH_079` | 账号恢复扫码背书已创建 | QR endorsement for account recovery created | — | AuthController.java |
| `AUTH_080` | target 只能是 web、desktop 或 mobile | target must be web, desktop or mobile | — | AuthController.java, SessionTransferService.java |
| `AUTH_081` | 二维码挑战已创建 | QR challenge created | — | AuthController.java |
| `AUTH_082` | mfaChallengeId 不能为空 | mfaChallengeId is required | — | AuthController.java |
| `AUTH_083` | mfaChallengeId 无效或已过期 | mfaChallengeId is invalid or has expired | — | AuthController.java |
| `AUTH_084` | 当前 challenge 不允许使用扫码验证 | This challenge does not support QR verification | — | AuthController.java |
| `AUTH_085` | approveCode 或 transferCode 不能为空 | approveCode or transferCode is required | — | AuthController.java |
| `AUTH_086` | 二维码挑战不存在或已过期 | The QR challenge does not exist or has expired | — | AuthController.java, QrChallengeService.java |
| `AUTH_087` | 跨端票据不存在或已过期 | The session transfer ticket does not exist or has expired | — | AuthController.java, SessionTransferService.java |
| `AUTH_088` | approveCode 不能为空 | approveCode is required | — | AuthController.java |
| `AUTH_089` | 二维码挑战状态不可用 | The QR challenge is not available in its current state | — | AuthController.java, QrChallengeService.java |
| `AUTH_090` | 二维码挑战类型无效 | Invalid QR challenge type | — | AuthController.java |
| `AUTH_091` | 当前账号未配置可用的非扫码 MFA 方式 | This account has no available MFA method other than QR verification | — | AuthController.java |
| `AUTH_092` | 二维码挑战用户不匹配 | The QR challenge user does not match | — | AuthController.java |
| `AUTH_093` | mfaChallengeId 缺失 | mfaChallengeId is missing | — | AuthController.java |
| `AUTH_094` | MFA 挑战用户不匹配 | The MFA challenge user does not match | — | AuthController.java |
| `AUTH_095` | 二维码挑战无效 | Invalid QR challenge | — | AuthController.java |
| `AUTH_096` | 二维码挑战已授权 | QR challenge approved | — | AuthController.java |
| `AUTH_097` | 二维码挑战授权失败 | Could not approve the QR challenge | — | AuthController.java |
| `AUTH_098` | challengeId 与 pollToken 不能为空 | challengeId and pollToken are required | — | AuthController.java |
| `AUTH_099` | 二维码挑战已过期 | The QR challenge has expired | — | AuthController.java |
| `AUTH_100` | pollToken 无效 | Invalid pollToken | — | AuthController.java |
| `AUTH_101` | 会话不存在 | Session not found | — | AuthController.java |
| `AUTH_102` | 会话已失效 | The session has expired | — | AuthController.java |
| `AUTH_103` | 会话已取消 | The session was cancelled | — | AuthController.java |
| `AUTH_104` | challengeId 和 code 或 recoveryCode 不能为空 | challengeId and either code or recoveryCode are required | — | AuthController.java |
| `AUTH_105` | challengeId 无效或已过期 | challengeId is invalid or has expired | — | AuthController.java |
| `AUTH_106` | 当前 challenge 不允许使用 TOTP | This challenge does not support TOTP | — | AuthController.java |
| `AUTH_107` | TOTP/恢复码校验失败，剩余尝试次数：{remaining} | TOTP or recovery code verification failed. Attempts remaining: {remaining} | `remaining` | AuthController.java |
| `AUTH_108` | TOTP/恢复码校验失败次数过多，请重新登录 | Too many failed TOTP or recovery code attempts. Please sign in again. | — | AuthController.java |
| `AUTH_109` | TOTP 验证处理失败 | Could not process TOTP verification | — | AuthController.java |
| `AUTH_110` | mfaChallengeId、passkeyChallengeId 及 Passkey 参数不能为空 | mfaChallengeId, passkeyChallengeId and Passkey parameters are required | — | AuthController.java |
| `AUTH_111` | 当前 challenge 不允许使用 Passkey | This challenge does not support Passkey | — | AuthController.java |
| `AUTH_112` | Passkey 校验失败 | Passkey verification failed | — | AuthController.java |
| `AUTH_113` | Passkey 校验失败，剩余尝试次数：{remaining} | Passkey verification failed. Attempts remaining: {remaining} | `remaining` | AuthController.java |
| `AUTH_114` | Passkey 校验失败次数过多，请重新登录 | Too many failed Passkey attempts. Please sign in again. | — | AuthController.java |
| `AUTH_115` | Passkey MFA 验证处理失败 | Could not process Passkey MFA verification | — | AuthController.java |
| `AUTH_116` | 退出成功 | Signed out | — | AuthController.java |
| `AUTH_117` | 已从所有设备退出登录 | Signed out of all devices | — | AuthController.java |
| `AUTH_118` | key 不能为空 | key is required | — | AuthController.java |
| `AUTH_119` | value 不能为空 | value is required | — | AuthController.java |
| `AUTH_120` | 用户名格式不正确（3-20字符，字母数字下划线连字符或简体中文） | Username must contain 3–20 characters: letters, numbers, underscores, hyphens or Simplified Chinese characters | — | AuthController.java |
| `AUTH_121` | birthDate 格式不正确，应为 YYYY-MM-DD | Invalid birthDate. Use YYYY-MM-DD. | — | AuthController.java |
| `AUTH_122` | 请选择头像文件 | Choose an avatar image | — | AuthController.java |
| `AUTH_123` | 头像文件不能超过 3MB | Avatar images must not exceed 3 MB | — | AuthController.java |
| `AUTH_124` | 仅支持图片文件 | Only image files are supported | — | AuthController.java, Oauth2PlatformController.java, retired-sso |
| `AUTH_125` | 不支持的图片格式 | Unsupported image format | — | AuthController.java, Oauth2PlatformController.java, retired-sso |
| `AUTH_126` | 头像上传成功 | Avatar uploaded | — | AuthController.java |
| `AUTH_127` | 头像上传失败 | Could not upload the avatar | — | AuthController.java |
| `AUTH_128` | 验证方式不能为空 | Verification method is required | — | AuthController.java |
| `AUTH_129` | 验证方式只能是 password、email-code、passkey 或 totp | Verification method must be password, email-code, passkey or totp | — | AuthController.java |
| `AUTH_130` | 密码错误 | Incorrect password | — | AuthController.java |
| `AUTH_131` | 验证成功，有效期15分钟 | Identity verified for 15 minutes | — | AuthController.java |
| `AUTH_132` | 验证码或恢复码不能为空 | Verification code or recovery code is required | — | AuthController.java |
| `AUTH_133` | 用户未启用 TOTP | TOTP is not enabled for this user | — | AuthController.java, TotpController.java |
| `AUTH_134` | TOTP 验证失败 | TOTP verification failed | — | AuthController.java |
| `AUTH_135` | Passkey 验证请使用 /auth/passkey/sensitive-verification-options 与 /auth/passkey/sensitive-verification-verify | Use /auth/passkey/sensitive-verification-options and /auth/passkey/sensitive-verification-verify for Passkey verification | — | AuthController.java |
| `AUTH_136` | 未知的验证方式 | Unknown verification method | — | AuthController.java |
| `AUTH_137` | 新邮箱不能为空 | New email address is required | — | AuthController.java |
| `AUTH_138` | 邮箱更新成功 | Email address updated | — | AuthController.java |
| `AUTH_139` | 请输入正确的确认文本 | Enter the correct confirmation text | — | AuthController.java |
| `AUTH_140` | 账号已注销 | Account deleted | — | AuthController.java |
| `AUTH_141` | 密码修改成功 | Password changed | — | AuthController.java |
| `AUTH_142` | 生成注册选项成功 | Passkey registration options created | — | AuthController.java |
| `AUTH_143` | 生成注册选项失败 | Could not create Passkey registration options | — | AuthController.java |
| `AUTH_144` | Passkey 注册成功 | Passkey registered | — | AuthController.java |
| `AUTH_145` | Passkey 注册失败 | Could not register the Passkey | — | AuthController.java |
| `AUTH_146` | 生成认证选项成功 | Passkey authentication options created | — | AuthController.java |
| `AUTH_147` | 生成认证选项失败 | Could not create Passkey authentication options | — | AuthController.java |
| `AUTH_148` | Passkey 验证失败 | Passkey verification failed | — | AuthController.java |
| `AUTH_149` | Passkey 认证失败 | Passkey authentication failed | — | AuthController.java |
| `AUTH_150` | 生成敏感操作验证选项成功 | Identity verification options created | — | AuthController.java |
| `AUTH_151` | 生成验证选项失败 | Could not create verification options | — | AuthController.java |
| `AUTH_152` | 敏感操作验证失败 | Identity verification failed | — | AuthController.java, PasskeyService.java |
| `AUTH_153` | 获取 Passkey 列表失败 | Could not retrieve the Passkey list | — | AuthController.java |
| `AUTH_154` | Passkey 删除成功 | Passkey deleted | — | AuthController.java |
| `AUTH_155` | 删除失败 | Could not delete the item | — | AuthController.java |
| `AUTH_156` | Passkey 重命名成功 | Passkey renamed | — | AuthController.java |
| `AUTH_157` | 重命名失败 | Could not rename the item | — | AuthController.java |
| `OAUTH2_001` | 创建成功，请妥善保存 AppSecret | Application created. Store your AppSecret securely. | — | Oauth2PlatformController.java |
| `OAUTH2_002` | 删除成功 | Deleted successfully | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_003` | 请选择 Logo 文件 | Choose a logo image | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_004` | Logo 文件不能超过 3MB | Logo images must not exceed 3 MB | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_005` | Logo 上传成功 | Logo uploaded | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_006` | Logo 上传失败 | Could not upload the logo | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_007` | 授权成功 | Authorization granted | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH2_008` | 撤销成功 | Authorization revoked | — | Oauth2PlatformController.java, retired-sso |
| `OAUTH_001` | QQ 登录成功 | Signed in with QQ | — | OauthController.java |
| `OAUTH_002` | 绑定并登录成功 | Account linked and signed in | — | OauthController.java |
| `OAUTH_003` | 注册并绑定并登录成功 | Account created, linked and signed in | — | OauthController.java |
| `OAUTH_004` | 未绑定 QQ 帐号 | No QQ account is linked | — | OauthController.java |
| `OAUTH_005` | 无法解绑最后登录方式 | You cannot unlink your last sign-in method | — | OauthController.java |
| `OAUTH_006` | 解绑成功 | Account unlinked | — | OauthController.java |
| `OAUTH_007` | 参数缺失 | Required parameters are missing | — | OauthController.java |
| `OAUTH_008` | QQ 移动应用 AppId 未配置或不匹配 | The QQ mobile app AppId is not configured or does not match | — | OauthController.java |
| `OAUTH_009` | QQ 绑定成功 | QQ account linked | — | OauthController.java |
| `OAUTH_010` | QQ 账号已被绑定，请刷新后重试 | This QQ account is already linked. Refresh and try again. | — | OauthController.java |
| `OAUTH_011` | code 不能为空 | code is required | — | OauthController.java |
| `OAUTH_012` | state 不能为空 | state is required | — | OauthController.java |
| `OAUTH_013` | state 格式不正确，需为: 校验参数;操作类型;prd/dev | Invalid state format. Expected: verification parameter;operation;prd/dev | — | OauthController.java |
| `OAUTH_014` | state 操作类型不支持，仅支持 login/bind/unbind | The state operation must be login, bind or unbind | — | OauthController.java |
| `OAUTH_015` | state 操作类型与当前接口不匹配 | The state operation does not match this endpoint | — | OauthController.java |
| `OAUTH_016` | state 环境标识不支持，仅支持 prd/dev | The state environment must be prd or dev | — | OauthController.java |
| `OAUTH_017` | QQ redirectUri 未配置 | QQ redirectUri is not configured | — | OauthController.java |
| `OAUTH_018` | {operation} 操作需要有效登录态 | The {operation} operation requires a valid signed-in session | `operation` | OauthController.java |
| `OAUTH_019` | 当前登录态对应用户不存在 | The user for the current session does not exist | — | OauthController.java |
| `OAUTH_020` | 授权码已使用，请重新发起 QQ 登录 | This authorization code has already been used. Start QQ sign-in again. | — | OauthController.java |
| `OAUTH_021` | 登录处理中，请勿重复提交 | Sign-in is being processed. Do not submit again. | — | OauthController.java |
| `OAUTH_022` | QQ token 接口无响应 | No response from the QQ token endpoint | — | OauthController.java |
| `OAUTH_023` | QQ token 错误 | QQ token request failed | — | OauthController.java |
| `OAUTH_024` | 未从 QQ 返回 access_token | QQ did not return an access_token | — | OauthController.java |
| `OAUTH_025` | QQ me 接口无响应 | No response from the QQ identity endpoint | — | OauthController.java |
| `OAUTH_026` | QQ me 错误 | QQ identity request failed | — | OauthController.java |
| `OAUTH_027` | 未从 QQ 返回 openid | QQ did not return an openid | — | OauthController.java |
| `OAUTH_028` | 未从 QQ 返回 unionid，请确认已开通 getUnionId 权限 | QQ did not return a unionid. Check that getUnionId permission is enabled. | — | OauthController.java |
| `OAUTH_029` | bind 操作需要有效登录态 | Linking an account requires a valid signed-in session | — | OauthController.java |
| `OAUTH_030` | unbind 操作需要有效登录态 | Unlinking an account requires a valid signed-in session | — | OauthController.java |
| `OAUTH_031` | 未从 QQ 返回可用于解绑校验的 unionid | QQ did not return a unionid for unlink verification | — | OauthController.java |
| `OAUTH_032` | 当前授权的 QQ 账号与已绑定账号不一致 | The authorized QQ account does not match the linked account | — | OauthController.java |
| `OAUTH_033` | {provider} 账号已被绑定 | This {provider} account is already linked | `provider` | OauthController.java |
| `OAUTH_034` | 注册失败 | Could not create the account | — | OauthController.java |
| `OAUTH_035` | {provider} 绑定成功 | {provider} account linked | `provider` | OauthController.java |
| `OAUTH_036` | state 格式不正确，需为: verifyToken;operation;env | Invalid state format. Expected: verifyToken;operation;env | — | OauthController.java |
| `OAUTH_037` | GitHub redirectUri 未配置 | GitHub redirectUri is not configured | — | OauthController.java |
| `OAUTH_038` | GitHub redirectUri 非法，请检查后端配置 | Invalid GitHub redirectUri. Check the server configuration. | — | OauthController.java |
| `OAUTH_039` | GitHub OAuth 配置缺失 | GitHub OAuth configuration is missing | — | OauthController.java |
| `OAUTH_040` | 授权码已使用，请重新发起 GitHub 登录 | This authorization code has already been used. Start GitHub sign-in again. | — | OauthController.java |
| `OAUTH_041` | GitHub token 接口无响应 | No response from the GitHub token endpoint | — | OauthController.java |
| `OAUTH_042` | GitHub token 错误 | GitHub token request failed | — | OauthController.java |
| `OAUTH_043` | 未从 GitHub 返回 access_token | GitHub did not return an access_token | — | OauthController.java |
| `OAUTH_044` | GitHub user 接口无响应 | No response from the GitHub user endpoint | — | OauthController.java |
| `OAUTH_045` | GitHub user 错误 | GitHub user request failed | — | OauthController.java |
| `OAUTH_046` | 未从 GitHub 返回用户 ID | GitHub did not return a user ID | — | OauthController.java |
| `OAUTH_047` | 当前账号已绑定 GitHub | This account is already linked to GitHub | — | OauthController.java |
| `OAUTH_048` | 该 GitHub 账号已被绑定 | This GitHub account is already linked | — | OauthController.java |
| `OAUTH_049` | GitHub 绑定成功 | GitHub account linked | — | OauthController.java |
| `OAUTH_050` | state 操作类型不支持，仅支持 login/bind | The state operation must be login or bind | — | OauthController.java |
| `OAUTH_051` | Google redirectUri 未配置 | Google redirectUri is not configured | — | OauthController.java |
| `OAUTH_052` | Google OAuth 配置缺失 | Google OAuth configuration is missing | — | OauthController.java |
| `OAUTH_053` | 授权码已使用，请重新发起 Google 登录 | This authorization code has already been used. Start Google sign-in again. | — | OauthController.java |
| `OAUTH_054` | Google token 错误 | Google token request failed | — | OauthController.java |
| `OAUTH_055` | Google token 接口无响应 | No response from the Google token endpoint | — | OauthController.java |
| `OAUTH_056` | Google token 错误: invalid_grant（授权码已使用/过期，或 redirect_uri 与授权请求不一致） | Google token request failed: invalid_grant. The code was used or expired, or redirect_uri does not match the authorization request. | — | OauthController.java |
| `OAUTH_057` | 未从 Google 返回 access_token | Google did not return an access_token | — | OauthController.java |
| `OAUTH_058` | 未从 Google 返回用户标识 | Google did not return a user identifier | — | OauthController.java |
| `OAUTH_059` | 当前账号已绑定 Google | This account is already linked to Google | — | OauthController.java |
| `OAUTH_060` | 该 Google 账号已被绑定 | This Google account is already linked | — | OauthController.java |
| `OAUTH_061` | Google 绑定成功 | Google account linked | — | OauthController.java |
| `OAUTH_062` | 未绑定 Google 帐号 | No Google account is linked | — | OauthController.java |
| `OAUTH_063` | Google 解绑成功 | Google account unlinked | — | OauthController.java |
| `OAUTH_064` | codeVerifier 不能为空 | codeVerifier is required | — | OauthController.java |
| `OAUTH_065` | Microsoft redirectUri 未配置 | Microsoft redirectUri is not configured | — | OauthController.java |
| `OAUTH_066` | Microsoft OAuth client_id 未配置 | Microsoft OAuth client_id is not configured | — | OauthController.java |
| `OAUTH_067` | 授权码已使用，请重新发起 Microsoft 登录 | This authorization code has already been used. Start Microsoft sign-in again. | — | OauthController.java |
| `OAUTH_068` | Microsoft token 接口无响应 | No response from the Microsoft token endpoint | — | OauthController.java |
| `OAUTH_069` | Microsoft token 错误 | Microsoft token request failed | — | OauthController.java |
| `OAUTH_070` | 未从 Microsoft 返回 access_token | Microsoft did not return an access_token | — | OauthController.java |
| `OAUTH_071` | 未从 Microsoft 返回用户标识 | Microsoft did not return a user identifier | — | OauthController.java |
| `OAUTH_072` | 当前账号已绑定 Microsoft | This account is already linked to Microsoft | — | OauthController.java |
| `OAUTH_073` | 该 Microsoft 账号已被绑定 | This Microsoft account is already linked | — | OauthController.java |
| `OAUTH_074` | Microsoft 绑定成功 | Microsoft account linked | — | OauthController.java |
| `OAUTH_075` | 未绑定 Microsoft 帐号 | No Microsoft account is linked | — | OauthController.java |
| `OAUTH_076` | 当前授权的 Microsoft 账号与已绑定账号不一致 | The authorized Microsoft account does not match the linked account | — | OauthController.java |
| `OAUTH_077` | Microsoft 解绑成功 | Microsoft account unlinked | — | OauthController.java |
| `OAUTH_078` | 未绑定 GitHub 帐号 | No GitHub account is linked | — | OauthController.java |
| `OAUTH_079` | GitHub 解绑成功 | GitHub account unlinked | — | OauthController.java |
| `OAUTH_080` | 当前账号已绑定 {provider} | This account is already linked to {provider} | `provider` | OauthController.java |
| `OAUTH_081` | 该 {provider} 账号已被绑定 | This {provider} account is already linked | `provider` | OauthController.java |
| `OAUTH_082` | QQ openid 校验失败 | QQ openid verification failed | — | OauthController.java |
| `OAUTH_083` | QQ 未返回 unionid，请确认已开通 getUnionId 权限 | QQ did not return a unionid. Check that getUnionId permission is enabled. | — | OauthController.java |
| `OAUTH_084` | QQ unionid 校验失败 | QQ unionid verification failed | — | OauthController.java |
| `OAUTH_085` | QQ AppId 校验失败 | QQ AppId verification failed | — | OauthController.java |
| `OAUTH_086` | 不支持的第三方账号类型 | Unsupported third-party account type | — | OauthController.java |
| `OAUTH_087` | 第三方登录状态已过期，请重新授权 | Third-party sign-in has expired. Please authorize again. | — | OauthController.java |
| `OAUTH_088` | 第三方登录状态无效，请重新授权 | Third-party sign-in is invalid. Please authorize again. | — | OauthController.java |
| `OAUTH_089` | 第三方登录状态缺失，请重新授权 | Third-party sign-in information is missing. Please authorize again. | — | OauthController.java |
| `OAUTH_090` | QQ unionid 缺失，请重新授权 | QQ unionid is missing. Please authorize again. | — | OauthController.java |
| `REQUEST_001` | 推送设置已保存 | Push notification settings saved | — | PushDeviceController.java |
| `REQUEST_002` | 此设备已停止接收推送 | This device will no longer receive push notifications | — | PushDeviceController.java |
| `REQUEST_003` | Access token不存在 | Access token is missing | — | SensitiveLogController.java |
| `REQUEST_004` | Access token无效或已过期 | The access token is invalid or has expired | — | SensitiveLogController.java |
| `REQUEST_005` | 开始日期格式不正确，请使用 YYYY-MM-DD | Invalid start date. Use YYYY-MM-DD. | — | SensitiveLogController.java |
| `REQUEST_006` | 结束日期格式不正确，请使用 YYYY-MM-DD | Invalid end date. Use YYYY-MM-DD. | — | SensitiveLogController.java |
| `REQUEST_007` | 获取敏感操作日志成功 | Security activity retrieved | — | SensitiveLogController.java |
| `SSO_001` | 创建成功，请妥善保存 ClientSecret | Client created. Store your ClientSecret securely. | — | retired-sso |
| `TOTP_001` | 用户已启用 TOTP | TOTP is already enabled for this user | — | TotpController.java |
| `TOTP_002` | 获取 TOTP 注册选项成功 | TOTP registration options retrieved | — | TotpController.java |
| `TOTP_003` | 生成 TOTP 秘钥失败 | Could not generate the TOTP secret | — | TotpController.java |
| `TOTP_004` | 恢复码不能为空，请使用 registration-options 返回的 recoveryCodes | Recovery codes are required. Use recoveryCodes returned by registration-options. | — | TotpController.java |
| `TOTP_005` | TOTP 未初始化，请先调用 registration-options | TOTP is not initialized. Request registration-options first. | — | TotpController.java |
| `TOTP_006` | 待确认秘钥已过期，请重新生成 | The pending secret has expired. Generate a new secret. | — | TotpController.java |
| `TOTP_007` | TOTP 注册成功 | TOTP enabled | — | TotpController.java |
| `TOTP_008` | 验证码无效或已过期 | The verification code is invalid or has expired | — | TotpController.java |
| `TOTP_009` | TOTP 注册失败 | Could not enable TOTP | — | TotpController.java |
| `TOTP_010` | TOTP 验证成功 | TOTP verified | — | TotpController.java |
| `TOTP_011` | 使用恢复码验证成功 | Verified using a recovery code | — | TotpController.java |
| `TOTP_012` | 验证失败 | Verification failed | — | TotpController.java |
| `TOTP_013` | 获取 TOTP 状态成功 | TOTP status retrieved | — | TotpController.java |
| `TOTP_014` | TOTP 禁用成功 | TOTP disabled | — | TotpController.java |
| `TOTP_015` | 恢复码已重新生成 | Recovery codes regenerated | — | TotpController.java |
| `TOTP_016` | 重新生成恢复码失败 | Could not regenerate recovery codes | — | TotpController.java |
| `TOTP_017` | 获取回复码成功 | Recovery codes retrieved | — | TotpController.java |
| `REQUEST_008` | 请求方法不支持: {method} | Unsupported request method: {method} | `method` | GlobalExceptionHandler.java |
| `REQUEST_009` | 不支持的请求类型: {contentType} | Unsupported content type: {contentType} | `contentType` | GlobalExceptionHandler.java |
| `RECOVERY_001` | 创建恢复授权失败 | Could not create the recovery authorization | — | AccountRecoveryService.java |
| `RECOVERY_002` | 读取恢复授权失败 | Could not read the recovery authorization | — | AccountRecoveryService.java |
| `RECOVERY_003` | 解析恢复授权失败 | Could not parse the recovery authorization | — | AccountRecoveryService.java |
| `APPLE_014` | Apple 验证用途无效 | Invalid Apple verification purpose | — | AppleAuthService.java |
| `APPLE_015` | Apple challenge 用途不匹配 | The Apple challenge purpose does not match | — | AppleAuthService.java |
| `APPLE_016` | Apple state 校验失败 | Apple state verification failed | — | AppleAuthService.java |
| `APPLE_017` | Apple 授权码身份不一致 | The Apple authorization code identity does not match | — | AppleAuthService.java |
| `APPLE_018` | 请使用当前账号绑定的 Apple 身份进行验证 | Verify using the Apple identity linked to the current account | — | AppleAuthService.java |
| `APPLE_019` | 绑定账号不存在 | The account to link does not exist | — | AppleAuthService.java |
| `APPLE_020` | 请先同意服务协议与隐私政策 | Accept the service agreement and privacy policy first | — | AppleAuthService.java |
| `APPLE_021` | 此 Apple 身份只能绑定已有账号 | This Apple identity can only be linked to an existing account | — | AppleAuthService.java |
| `APPLE_022` | Apple 身份已绑定，请重新登录 | This Apple identity is already linked. Please sign in again. | — | AppleAuthService.java |
| `APPLE_023` | 邮箱已存在，请登录已有账号后绑定 | This email address already exists. Sign in to the existing account before linking. | — | AppleAuthService.java |
| `APPLE_024` | 用户名格式无效或已被使用 | This username is invalid or already taken | — | AppleAuthService.java |
| `APPLE_025` | 请在登录后重新授权 Apple 以绑定账号 | After signing in, authorize Apple again to link your account | — | AppleAuthService.java |
| `APPLE_026` | 此账号或 Apple 身份已绑定 | This account or Apple identity is already linked | — | AppleAuthService.java |
| `APPLE_027` | 尚未绑定 Apple | No Apple account is linked | — | AppleAuthService.java |
| `APPLE_028` | 请先设置密码、验证邮箱或绑定其他登录方式 | Set a password, verify your email address or link another sign-in method first | — | AppleAuthService.java |
| `APPLE_029` | Apple 通知格式无效 | Invalid Apple notification format | — | AppleAuthService.java |
| `APPLE_030` | Apple 通知事件无效 | Invalid Apple notification event | — | AppleAuthService.java |
| `APPLE_031` | Apple 通知处理暂时不可用 | Apple notification processing is temporarily unavailable | — | AppleAuthService.java |
| `APPLE_032` | Apple 通知正在处理中，请重试 | The Apple notification is being processed. Please try again. | — | AppleAuthService.java |
| `APPLE_033` | Apple 验证会话不匹配，请重新授权 | The Apple verification session does not match. Please authorize again. | — | AppleAuthService.java |
| `APPLE_034` | Apple 验证票据无效 | Invalid Apple verification ticket | — | AppleAuthService.java |
| `APPLE_035` | Apple 验证已过期或已使用，请重新授权 | Apple verification has expired or was already used. Please authorize again. | — | AppleAuthService.java |
| `APPLE_036` | Apple 验证服务暂时不可用 | Apple verification is temporarily unavailable | — | AppleAuthService.java |
| `APPLE_037` | Apple nonce 校验失败，请重新授权 | Apple nonce verification failed. Please authorize again. | — | AppleTokenClient.java |
| `APPLE_038` | Apple 身份无效 | Invalid Apple identity | — | AppleTokenClient.java |
| `APPLE_039` | Apple 凭据无效 | Invalid Apple credentials | — | AppleTokenClient.java |
| `APPLE_040` | Apple audience 不受信任 | Untrusted Apple audience | — | AppleTokenClient.java |
| `APPLE_041` | Apple JWT 格式无效 | Invalid Apple JWT format | — | AppleTokenClient.java |
| `APPLE_042` | Apple JWT 算法无效 | Invalid Apple JWT algorithm | — | AppleTokenClient.java |
| `APPLE_043` | Apple JWT 声明无效 | Invalid Apple JWT claims | — | AppleTokenClient.java |
| `APPLE_044` | Apple 凭据校验失败，请重新授权 | Apple credential verification failed. Please authorize again. | — | AppleTokenClient.java |
| `APPLE_045` | Apple key id 无效 | Invalid Apple key ID | — | AppleTokenClient.java |
| `APPLE_046` | Apple 签名密钥未知 | Unknown Apple signing key | — | AppleTokenClient.java |
| `APPLE_047` | Apple 授权码缺失 | Apple authorization code is missing | — | AppleTokenClient.java |
| `APPLE_048` | Apple 未返回可撤销凭据，请重新授权 | Apple did not return revocable credentials. Please authorize again. | — | AppleTokenClient.java |
| `APPLE_049` | Apple 通知签名或 audience 无效 | Invalid Apple notification signature or audience | — | AppleTokenClient.java |
| `APPLE_050` | Apple 服务端私钥配置无效 | Invalid Apple server private key configuration | — | AppleTokenClient.java |
| `APPLE_051` | Apple 服务暂时不可用，请稍后重试 | Apple services are temporarily unavailable. Please try again later. | — | AppleTokenClient.java |
| `REQUEST_010` | 不支持的授权模式 | Unsupported authorization mode | — | AuthorizationGrantPolicy.java |
| `REQUEST_011` | 限时授权有效期必须在 {min}-{max} 秒之间 | Temporary authorization must last between {min} and {max} seconds | `min`, `max` | AuthorizationGrantPolicy.java |
| `MOBILE_AUTHORIZATION_001` | 授权请求不完整 | The authorization request is incomplete | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_002` | 授权请求过长 | The authorization request is too long | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_003` | 请先登录后授权 | Sign in before granting authorization | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_004` | 授权请求校验失败 | Authorization request verification failed | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_005` | 网页来源不受信任 | The web origin is not trusted | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_006` | 授权请求无效 | Invalid authorization request | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_007` | 请求正在处理中，请稍后重试 | The request is being processed. Please try again later. | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_008` | 授权已提交，请等待结果 | Authorization submitted. Please wait for the result. | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_009` | 授权请求已完成或过期，请返回网页重试 | This authorization request has finished or expired. Return to the web page and try again. | — | MobileAuthorizationService.java |
| `MOBILE_BRIDGE_001` | 创建移动桥接挑战失败 | Could not create the mobile bridge challenge | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_002` | 读取移动桥接挑战失败 | Could not read the mobile bridge challenge | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_003` | 移动桥接挑战不存在或已过期 | The mobile bridge challenge does not exist or has expired | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_004` | 移动桥接挑战已处理 | The mobile bridge challenge has already been processed | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_005` | 更新移动桥接挑战失败 | Could not update the mobile bridge challenge | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_006` | returnUrl 不能为空 | returnUrl is required | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_007` | returnUrl 格式不正确 | Invalid returnUrl format | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_008` | returnUrl 必须是完整地址 | returnUrl must be an absolute URL | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_009` | returnUrl 仅支持 http 或 https | returnUrl only supports http or https | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_010` | returnUrl 不在允许的域名白名单中 | returnUrl is not in the allowed domain list | — | MobileBridgeService.java |
| `OAUTH2_009` | AppID 不能为空 | AppID is required | — | Oauth2PlatformService.java |
| `OAUTH2_010` | 应用名称不能为空 | Application name is required | — | Oauth2PlatformService.java |
| `OAUTH2_011` | 联系方式不能为空 | Contact information is required | — | Oauth2PlatformService.java |
| `OAUTH2_012` | 回调地址不能为空 | Redirect URL is required | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_013` | 每位认证用户最多只能创建 {maxApps} 个 OAuth2 应用 | Each verified user may create at most {maxApps} OAuth2 applications | `maxApps` | Oauth2PlatformService.java |
| `OAUTH2_014` | 当前仅支持 authorization_code | Only authorization_code is supported | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_015` | AppSecret 不正确 | Incorrect AppSecret | — | Oauth2PlatformService.java |
| `OAUTH2_016` | 授权码无效、已过期或已使用 | The authorization code is invalid, expired or already used | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_017` | 授权码与当前应用或回调地址不匹配 | The authorization code does not match this application or redirect URL | — | Oauth2PlatformService.java |
| `OAUTH2_018` | 授权用户不存在 | The authorizing user does not exist | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_019` | 授权已撤销或已过期 | Authorization has been revoked or has expired | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_020` | Access Token 无效或已过期 | The access token is invalid or has expired | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_021` | Access Token 对应用户不存在 | The user associated with this access token does not exist | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_022` | 仅个人认证、企业认证或管理员账号可以创建 OAuth2 应用 | Only individually verified, enterprise-verified or administrator accounts may create OAuth2 applications | — | Oauth2PlatformService.java |
| `OAUTH2_023` | 应用不存在 | Application not found | — | Oauth2PlatformService.java |
| `OAUTH2_024` | AppID 不存在或已停用 | AppID does not exist or is disabled | — | Oauth2PlatformService.java |
| `OAUTH2_025` | 仅支持 response_type=code | Only response_type=code is supported | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_026` | redirect_uri 与应用登记信息不一致 | redirect_uri does not match the application's registered redirect URL | — | Oauth2PlatformService.java |
| `OAUTH2_027` | 应用名称长度需在 2-100 个字符之间 | Application names must contain 2–100 characters | — | Oauth2PlatformService.java |
| `OAUTH2_028` | 联系方式长度需在 3-120 个字符之间 | Contact information must contain 3–120 characters | — | Oauth2PlatformService.java |
| `OAUTH2_029` | 回调地址仅支持 https:// 或 http://localhost | Redirect URLs must use https:// or http://localhost | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_030` | 回调地址不允许包含 URL fragment | Redirect URLs must not contain a URL fragment | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_031` | 回调地址格式不正确 | Invalid redirect URL format | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_032` | 回调地址总长度不能超过 500 个字符 | Redirect URLs must not exceed 500 characters in total | — | Oauth2PlatformService.java |
| `OAUTH2_033` | 请求的 scope 超出了应用已登记的权限范围 | The requested scope exceeds the application's registered permissions | — | Oauth2PlatformService.java |
| `OAUTH2_034` | 暂仅支持 profile、email 两种 scope | Only profile and email scopes are supported | — | Oauth2PlatformService.java |
| `OAUTH2_035` | 生成 AppID 失败，请稍后重试 | Could not generate AppID. Please try again later. | — | Oauth2PlatformService.java |
| `OAUTH2_036` | 生成授权码失败 | Could not generate the authorization code | — | Oauth2PlatformService.java, retired-sso |
| `OAUTH2_037` | 生成用户标识失败 | Could not generate the user identifier | — | Oauth2PlatformService.java |
| `PASSKEY_001` | Challenge 已过期或不存在 | The challenge has expired or does not exist | — | PasskeyService.java |
| `PASSKEY_002` | Passkey 注册验证失败 | Passkey registration verification failed | — | PasskeyService.java |
| `PASSKEY_003` | AuthenticatorData 缺少 AttestedCredentialData | AuthenticatorData is missing AttestedCredentialData | — | PasskeyService.java |
| `PASSKEY_004` | 此 Passkey 已被注册 | This Passkey is already registered | — | PasskeyService.java |
| `PASSKEY_005` | Passkey 不存在 | Passkey not found | — | PasskeyService.java |
| `PASSKEY_006` | Passkey 认证验证失败 | Passkey authentication verification failed | — | PasskeyService.java |
| `PASSKEY_007` | Passkey 计数异常，可能是克隆的 Passkey | The Passkey counter is abnormal. This Passkey may have been cloned. | — | PasskeyService.java |
| `PASSKEY_008` | Passkey 不属于当前用户 | This Passkey does not belong to the current user | — | PasskeyService.java |
| `PASSKEY_009` | 无权限删除此 Passkey | You do not have permission to delete this Passkey | — | PasskeyService.java |
| `PASSKEY_010` | Passkey 名称不能为空 | Passkey name is required | — | PasskeyService.java |
| `PASSKEY_011` | Passkey 名称长度不能超过 50 个字符 | Passkey names must not exceed 50 characters | — | PasskeyService.java |
| `PASSKEY_012` | 无权限修改此 Passkey | You do not have permission to edit this Passkey | — | PasskeyService.java |
| `QR_001` | 保存二维码挑战失败 | Could not save the QR challenge | — | QrChallengeService.java |
| `SESSION_TRANSFER_001` | 创建跨端票据失败 | Could not create the session transfer ticket | — | SessionTransferService.java |
| `SESSION_TRANSFER_002` | 读取跨端票据失败 | Could not read the session transfer ticket | — | SessionTransferService.java |
| `SESSION_TRANSFER_003` | 跨端票据目标端不匹配 | The session transfer ticket does not match the target platform | — | SessionTransferService.java |
| `SESSION_TRANSFER_004` | 跨端票据数据无效 | Invalid session transfer ticket data | — | SessionTransferService.java |
| `SESSION_TRANSFER_005` | 解析跨端票据失败 | Could not parse the session transfer ticket | — | SessionTransferService.java |
| `SSO_002` | ClientID 不能为空 | ClientID is required | — | retired-sso |
| `SSO_003` | 客户端名称不能为空 | Client name is required | — | retired-sso |
| `SSO_004` | 当前环境最多只能创建 {maxClients} 个 SSO 内部服务应用 | This environment may contain at most {maxClients} internal SSO applications | `maxClients` | retired-sso |
| `SSO_005` | ClientSecret 不正确 | Incorrect ClientSecret | — | retired-sso |
| `SSO_006` | 授权码与当前客户端或回调地址不匹配 | The authorization code does not match this client or redirect URL | — | retired-sso |
| `SSO_007` | 只有管理员可以创建或管理 SSO 内部服务应用 | Only administrators may create or manage internal SSO applications | — | retired-sso |
| `SSO_008` | SSO 客户端不存在 | SSO client not found | — | retired-sso |
| `SSO_009` | SSO 客户端不存在或已停用 | The SSO client does not exist or is disabled | — | retired-sso |
| `SSO_010` | nonce 长度不能超过 255 个字符 | nonce must not exceed 255 characters | — | retired-sso |
| `SSO_011` | 当前 SSO 客户端要求必须提供 PKCE 参数 | This SSO client requires PKCE parameters | — | retired-sso |
| `SSO_012` | code_challenge 与 code_challenge_method 必须同时提供 | code_challenge and code_challenge_method must be provided together | — | retired-sso |
| `SSO_013` | 当前仅支持 code_challenge_method=S256 | Only code_challenge_method=S256 is supported | — | retired-sso |
| `SSO_014` | code_challenge 长度需在 43-128 个字符之间 | code_challenge must contain 43–128 characters | — | retired-sso |
| `SSO_015` | 缺少 PKCE 校验信息 | PKCE verification information is missing | — | retired-sso |
| `SSO_016` | 缺少 code_verifier | code_verifier is missing | — | retired-sso |
| `SSO_017` | code_verifier 长度无效 | Invalid code_verifier length | — | retired-sso |
| `SSO_018` | PKCE 校验失败 | PKCE verification failed | — | retired-sso |
| `SSO_019` | 客户端名称长度需在 2-120 个字符之间 | Client names must contain 2–120 characters | — | retired-sso |
| `SSO_020` | 至少需要配置一个 redirect_uri | Configure at least one redirect_uri | — | retired-sso |
| `SSO_021` | redirect_uri 与客户端登记信息不一致 | redirect_uri does not match the client's registered redirect URL | — | retired-sso |
| `SSO_022` | SSO 暂仅支持 openid、profile、email | SSO only supports openid, profile and email scopes | — | retired-sso |
| `SSO_023` | SSO 授权请求必须包含 openid | SSO authorization requests must include openid | — | retired-sso |
| `SSO_024` | 请求的 scope 超出了客户端登记范围 | The requested scope exceeds the client's registered permissions | — | retired-sso |
| `SSO_025` | 生成 sub 失败 | Could not generate sub | — | retired-sso |
| `SSO_026` | 生成 ClientID 失败，请稍后重试 | Could not generate ClientID. Please try again later. | — | retired-sso |
| `REQUEST_UPLOAD_TOO_LARGE` | 上传文件过大，请选择较小文件 | The file is too large. Choose a smaller file. | — | GlobalExceptionHandler.java |
| `ADAPTIVE_REASON_SESSION_MISSING` | 未找到当前活跃会话 | No active session was found | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IP_CHANGED` | 当前 IP 与会话建立时不一致 | Your current IP address differs from the IP used to start this session | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_LOCATION_CHANGED` | 当前位置与会话建立位置发生变化 | Your current location differs from the location where this session started | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_AGE_THREE_DAYS` | 当前会话已持续超过 3 天 | This session has been active for more than 3 days | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_AGE_TWELVE_HOURS` | 当前会话已持续超过 12 小时 | This session has been active for more than 12 hours | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IDLE_TWO_HOURS` | 当前会话空闲时间超过 2 小时 | This session has been idle for more than 2 hours | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IDLE_THIRTY_MINUTES` | 当前会话已空闲超过 30 分钟 | This session has been idle for more than 30 minutes | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_RECENT_FAILURES` | 24 小时内存在多次失败认证或敏感操作 | Multiple failed sign-ins or sensitive operations occurred in the last 24 hours | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_RECENT_HIGH_RISK` | 最近一次安全事件风险评分较高 | The most recent security event had a high risk score | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_STABLE` | 当前会话环境稳定，未发现需要额外认证的风险信号 | Your session environment is stable. No signals requiring additional verification were found. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_RELOGIN` | 当前会话不存在或已失效，请重新登录 | This session is missing or has expired. Please sign in again. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_STEP_UP` | 建议立即进行一次额外身份验证，再继续执行敏感操作 | Verify your identity again before continuing with sensitive operations | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_VERIFY` | 建议先完成一次敏感验证，以提升当前会话信任等级 | Complete identity verification to increase this session’s trust level | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_VERIFIED` | 当前会话处于已验证状态，可继续执行敏感操作 | This session is verified. You can continue with sensitive operations. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_SAFE` | 当前会话风险较低，可继续使用 | This session has low risk. You can continue using it. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_FROZEN` | 当前会话风险极高，系统已冻结会话，请重新登录并完成验证 | This session has been frozen due to very high risk. Sign in again and complete verification. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_POLICY_STEP_UP` | 当前会话风险中等，需完成额外身份验证后继续敏感操作 | This session has moderate risk. Verify your identity again before continuing with sensitive operations. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_FROZEN_TITLE` | 高风险会话已冻结 | High-risk session frozen | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_FROZEN_MESSAGE` | 当前会话已被冻结，请重新登录并完成安全验证 | This session has been frozen. Sign in again and complete security verification. | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_STEP_UP_TITLE` | 需要额外身份验证 | Additional identity verification required | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_STEP_UP_MESSAGE` | 当前会话出现风险信号，请先完成额外身份验证 | Risk signals were detected in this session. Verify your identity before continuing. | — | AdaptiveContinuousAuthService.java |
| `QR_REJECT_1` | 未配置可用的非扫码 MFA 方式 | No MFA method other than QR verification is available | — | AuthController.java |
| `QR_REJECT_2` | 当前登录会话无效 | The current sign-in session is invalid | — | AuthController.java |
| `QR_REJECT_3` | 二维码挑战缺少 webIp | The QR challenge is missing webIp | — | AuthController.java |
| `APPLE_BIND_EMAIL_CONFLICT` | 邮箱已存在，请登录已有账号后绑定 Apple | This email address already exists. Sign in to the existing account before linking Apple. | — | AppleAuthController.java |
| `APPLE_BIND_REQUIRED` | 请创建账号，或登录已有账号后绑定 Apple | Create an account, or sign in to an existing account and link Apple. | — | AppleAuthController.java |
| `PASSKEY_MFA_UNSUPPORTED` | 当前账号需要额外的 MFA 验证，但浏览器桥接页无法继续完成 | This account requires additional MFA verification, which cannot be completed on the browser bridge page | — | DesktopPasskeyBridgeView.vue |
| `OFFICIAL_REQUEST_INVALID` | 官方应用授权参数不正确 | Invalid official authorization parameters | — | 公共/客户端 |
| `OFFICIAL_CLIENT_INVALID` | 官方应用不存在或已停用 | Official application is unavailable | — | 公共/客户端 |
| `OFFICIAL_REDIRECT_INVALID` | 官方应用回调地址不匹配 | Official application callback does not match | — | 公共/客户端 |
| `OFFICIAL_SCOPE_INVALID` | 官方应用请求了未开放的资料范围 | Requested user data scope is not allowed | — | 公共/客户端 |
| `OFFICIAL_PKCE_INVALID` | 官方应用登录验证失败，请重新登录 | Official application PKCE verification failed. Sign in again. | — | 公共/客户端 |
| `OFFICIAL_GRANT_INVALID` | 官方应用授权已失效，请重新授权 | Official application authorization has expired or been revoked | — | 公共/客户端 |
| `OFFICIAL_TOKEN_INVALID` | 官方应用访问凭证无效或已过期 | Official application access token is invalid or expired | — | 公共/客户端 |
| `OFFICIAL_CONSENT_REQUIRED` | 请先确认官方应用的资料授权 | Confirm the official application data access first | — | 公共/客户端 |
| `OFFICIAL_ACCESS_DENIED` | 当前账号无权登录此官方应用 | This account cannot sign in to this official application | — | 公共/客户端 |
| `OFFICIAL_AUTHORIZED` | 官方应用授权成功 | Official application authorized | — | 公共/客户端 |
| `OFFICIAL_REVOKED` | 已撤销官方应用授权 | Official application authorization revoked | — | 公共/客户端 |
| `OFFICIAL_SUCCESS` | 操作成功 | Operation completed | — | 公共/客户端 |
| `CONSOLE_SESSION_INVALID` | 管理面板登录已失效，请重新登录 | Console session expired. Sign in again. | — | 公共/客户端 |
| `CONSOLE_ACCESS_DENIED` | 只有超级管理员可以访问管理面板 | Only super administrators can access the console | — | 公共/客户端 |
| `CONSOLE_VERIFICATION_REQUIRED` | 请前往通行证再次验证身份 | Verify your identity again in Ksuser Passport | — | 公共/客户端 |
| `CONSOLE_FLOW_INVALID` | 管理面板验证流程已失效，请重试 | Console verification flow is invalid or expired. Try again. | — | 公共/客户端 |
| `CONSOLE_APPLICATION_INVALID` | 官方应用配置不正确或操作不被允许 | Invalid official application configuration or operation | — | 公共/客户端 |
| `CONSOLE_USER_NOT_FOUND` | 未找到指定用户 | The requested user was not found | — | 公共/客户端 |
| `OAUTH2_CLIENT_TYPE_INVALID` | 应用客户端类型不正确 | Invalid application client type | — | Oauth2PlatformService.java |
| `OAUTH2_STATE_INVALID` | 授权状态参数不正确，请重新发起登录 | Invalid authorization state. Start sign-in again. | — | Oauth2PlatformService.java |
| `OAUTH2_PKCE_INVALID` | 应用登录验证失败，请重新发起登录 | Application PKCE verification failed. Start sign-in again. | — | Oauth2PlatformService.java |
| `NATIVE_AUTHORIZATION_UNAVAILABLE` | App 授权暂未开放，请稍后重试 | App authorization is not available. Try again later. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_INVALID` | App 授权参数不正确，请重新发起 | Invalid app authorization request. Start again. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_EXPIRED` | App 授权请求已过期或已取消，请重新发起 | App authorization expired or was cancelled. Start again. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_CONFLICT` | 此授权请求正在处理或已由其他操作完成 | This authorization is being processed or was completed by another action. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_CANCEL_INVALID` | 无法取消此授权请求 | Cannot cancel this authorization request. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_RATE_LIMITED` | App 授权请求过于频繁，请稍后重试 | Too many app authorization requests. Try again later. | — | NativeAuthorizationService.java |
| `NATIVE_AUTHORIZATION_FAILED` | App 授权未完成，请重新发起 | App authorization could not be completed. Start again. | — | NativeAuthorizationService.java |
| `OAUTH2_APPLICATION_TYPE_INVALID` | 应用类目或平台配置不正确 | Invalid application category or platform configuration | — | 公共/客户端 |
| `OAUTH2_UNIONID_REASON_INVALID` | 请填写有效的 UnionID 申请或审核理由（最多1000字） | Provide a valid UnionID application or review reason (up to 1000 characters) | — | 公共/客户端 |
| `OAUTH2_UNIONID_STATE_INVALID` | UnionID 申请状态已变化，请刷新后重试 | UnionID application state changed. Refresh and try again. | — | 公共/客户端 |
