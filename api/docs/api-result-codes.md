# API 详细错误码与四端文案对照

本文件由 `npm run generate:api-codes` 从 [唯一源文件](../../shared/api-result-codes.json) 自动生成。新增结果必须分配永久稳定的码；已发布的码禁止重新编号、复用含义或删除，文案可以调整。

## 响应契约

普通业务接口统一返回 `code`（HTTP/流程数字码）、`errorCode`（详细结果码，成功和中间态同样提供）、可选 `params` 与可选 `data`。不返回 `msg`。HTTP 状态保持原有含义；`200` 成功，`201` MFA，`202` 需要绑定或验证，错误使用对应 4xx/5xx。

```json
{"code":400,"errorCode":"AUTH_020","params":{"min":8,"max":64}}
```

参数仅携带结构化业务值（次数、长度、提供商等），不得携带异常原文、上游错误详情或用户提示文字。文案使用 `{参数名}` 占位符。四端先查本地表，再替换参数；未知码、缺参数和不规范响应使用本地通用提示。不得退回显示服务器 `msg` 或 `error_description`。

OAuth2/SSO 的 token、userinfo、JWKS、OIDC discovery 成功响应保持协议原始数据格式；错误保留标准 `error` 字段，扩展 `errorCode` / `params`。标准可选字段 `error_description` 使用稳定结果码，不提供展示文案。安全过滤器和 Spring MVC 未处理异常也返回同样的详细码。

密码策略返回 `requirementCode` / `requirementParams`。自适应认证返回 `reasonCodes: [{errorCode, params?}]`、`recommendedActionCode`、`alertTitleCode`、`alertMessageCode`。OAuth 的嵌套 `data.message` / `data.reason` 和 TOTP `data.message` 不再作为提示来源，客户端统一读取外层详细码。

本表也定义网络故障、超时、用户取消 Passkey 等客户端结果码；流程状态（例如 pending/approved/rejected/expired）和数据字段保留原有含义。

历史敏感操作日志的 `failureReason` 属于持久审计记录：已知完整码在四端按本表显示，未知历史原文保留，不能用它判断流程。邮件/推送文本以及地理位置、名称等业务内容不是本次请求结果，保持原有格式。本地交互提示（已复制、步骤完成等）仍由客户端生成。

## 维护与发布

执行 `npm run generate:api-codes` 生成 Java 码枚举、Web/Swift/Kotlin/Dart 四端表和本文件；执行 `npm run check:api-codes` 验证无漂移。后端只能通过枚举或带码的业务异常构造响应，不依赖原因文字的运行时匹配。四端按码进行认证、敏感验证和桥接分支判断，不能比较显示文字。

这是一项响应契约变更：后端与四端客户端应配套发布。旧客户端依赖 `msg`，不能获取新版详细原因；新版客户端访问旧后端时使用本地通用提示。

## 对照表

模块编号码是永久标识；前缀仅用于分组，并非 HTTP 状态。HTTP 状态和是否继续流程始终读取 `code` / HTTP 状态及结构化业务字段。

| 详细结果码 | 前端中文文案 | 参数 | 定义来源 |
|---|---|---|---|
| `REQUEST_SUCCESS` | 操作成功 | — | MobileAuthorizationController.java |
| `REQUEST_FAILED` | 请求失败，请重试 | — | 公共/客户端 |
| `BAD_REQUEST` | 请求参数不正确 | — | 公共/客户端 |
| `UNAUTHENTICATED` | 未登录或登录状态已失效，请重新登录 | — | AppleAuthController.java, AuthController.java, MobileAuthorizationController.java, Oauth2PlatformController.java, OauthController.java, PushDeviceController.java, SsoPlatformController.java, TotpController.java, AppleAuthService.java |
| `FORBIDDEN` | 无权限 | — | 公共/客户端 |
| `SECURITY_CSRF_INVALID` | 安全校验失败，请刷新页面后重试 | — | 公共/客户端 |
| `SENSITIVE_VERIFICATION_REQUIRED` | 请先完成敏感操作验证 | — | AppleAuthController.java, AuthController.java, OauthController.java, TotpController.java |
| `AUTH_MFA_REQUIRED` | 需要 MFA 验证 | — | AppleAuthController.java, AuthController.java, OauthController.java |
| `OAUTH_BIND_REQUIRED` | 第三方账号尚未绑定，请创建或绑定账号 | — | AppleAuthController.java, OauthController.java |
| `SERVER_ERROR` | 服务暂时不可用，请稍后重试 | — | OauthController.java |
| `NOT_FOUND` | 请求的资源不存在 | — | 公共/客户端 |
| `METHOD_NOT_ALLOWED` | 请求方法不支持 | — | 公共/客户端 |
| `UNSUPPORTED_MEDIA_TYPE` | 不支持的请求类型 | — | 公共/客户端 |
| `RATE_LIMITED` | 请求过于频繁，请稍后重试 | — | AppleAuthController.java, OauthController.java |
| `REQUEST_CONFLICT` | 请求状态冲突，请重试 | — | 公共/客户端 |
| `NETWORK_ERROR` | 网络连接失败，请检查网络 | — | 公共/客户端 |
| `REQUEST_TIMEOUT` | 请求超时，请稍后重试 | — | 公共/客户端 |
| `INVALID_RESPONSE` | 服务返回了无法解析的数据 | — | 公共/客户端 |
| `PASSKEY_CANCELLED` | 用户取消了当前 Passkey 操作 | — | 公共/客户端 |
| `PASSKEY_UNSUPPORTED` | 当前浏览器不支持该 Passkey 操作 | — | 公共/客户端 |
| `PASSKEY_SECURITY_ERROR` | 浏览器安全校验失败，请确认当前使用受信任地址 | — | 公共/客户端 |
| `PASSKEY_CREDENTIAL_UNAVAILABLE` | 当前账号没有可用的 Passkey，或 Passkey 状态不正确 | — | 公共/客户端 |
| `SESSION_NOT_AUTHENTICATED` | 尚未完成登录，请重新登录 | — | 公共/客户端 |
| `INTERNAL_ERROR` | 服务暂时不可用，请稍后重试 | — | 公共/客户端 |
| `AUTH_USERNAME_EXISTS` | 用户名已存在 | — | 公共/客户端 |
| `AUTH_USERNAME_AVAILABLE` | 用户名可用 | — | 公共/客户端 |
| `AUTH_RECOVERY_CODE_INVALID` | 恢复码错误或已使用 | — | 公共/客户端 |
| `AUTH_VERIFICATION_CODE_INVALID` | 验证码错误或已过期 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_0` | 密码强度不足 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_1` | 密码强度不足：需包含大写字母 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_2` | 密码强度不足：需包含小写字母 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_3` | 密码强度不足：需包含大写字母、小写字母 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_4` | 密码强度不足：需包含数字 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_5` | 密码强度不足：需包含大写字母、数字 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_6` | 密码强度不足：需包含小写字母、数字 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_7` | 密码强度不足：需包含大写字母、小写字母、数字 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_8` | 密码强度不足：需包含特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_9` | 密码强度不足：需包含大写字母、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_10` | 密码强度不足：需包含小写字母、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_11` | 密码强度不足：需包含大写字母、小写字母、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_12` | 密码强度不足：需包含数字、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_13` | 密码强度不足：需包含大写字母、数字、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_14` | 密码强度不足：需包含小写字母、数字、特殊字符 | — | 公共/客户端 |
| `PASSWORD_REQUIREMENTS_15` | 密码强度不足：需包含大写字母、小写字母、数字、特殊字符 | — | 公共/客户端 |
| `APPLE_001` | 挑战已创建 | — | AppleAuthController.java |
| `APPLE_002` | Apple 绑定成功 | — | AppleAuthController.java |
| `APPLE_003` | 身份验证成功 | — | AppleAuthController.java |
| `APPLE_004` | 获取成功 | — | AppleAuthController.java, AuthController.java, InfoController.java, Oauth2PlatformController.java, SsoPlatformController.java |
| `APPLE_005` | Apple 解绑成功 | — | AppleAuthController.java |
| `APPLE_006` | 已接收 | — | AppleAuthController.java |
| `APPLE_007` | Apple 登录成功 | — | AppleAuthController.java |
| `APPLE_008` | Apple 服务尚未配置或暂时不可用，请稍后重试 | — | AppleAuthController.java |
| `APPLE_009` | 账号或 Apple 身份已存在，请重新登录或绑定 | — | AppleAuthController.java |
| `APPLE_010` | Apple 通知处理暂时失败，请稍后重试 | — | AppleAuthController.java, AppleAuthService.java |
| `APPLE_011` | 账号不存在 | — | AppleAuthController.java, AppleAuthService.java |
| `APPLE_012` | 会话已失效，请重新登录 | — | AppleAuthController.java |
| `APPLE_013` | 当前会话已冻结，请重新登录 | — | AppleAuthController.java |
| `AUTH_001` | 服务正常 | — | AuthController.java |
| `AUTH_002` | 用户名不能为空 | — | AuthController.java |
| `AUTH_003` | 类型不能为空 | — | AuthController.java |
| `AUTH_004` | 类型只能是 register、login、change-email 或 sensitive-verification | — | AuthController.java |
| `AUTH_005` | 用户不存在 | — | AuthController.java, MobileAuthorizationController.java, Oauth2PlatformController.java, OauthController.java, SensitiveLogController.java, SsoPlatformController.java, TotpController.java, AccountRecoveryService.java, MobileBridgeService.java |
| `AUTH_006` | 当前用户未绑定邮箱 | — | AuthController.java |
| `AUTH_007` | 邮箱不能为空 | — | AuthController.java |
| `AUTH_008` | 邮箱格式不正确 | — | AuthController.java |
| `AUTH_009` | 邮箱已被使用 | — | AuthController.java |
| `AUTH_010` | 验证码错误次数过多，该邮箱已被锁定1小时 | — | AuthController.java |
| `AUTH_011` | 注册过于频繁，请稍后再试 | — | AuthController.java |
| `AUTH_012` | 发送过于频繁，请1分钟后再试 | — | AuthController.java |
| `AUTH_013` | 发送次数过多，每小时最多发送14次 | — | AuthController.java |
| `AUTH_014` | 该邮箱发送次数过多，每小时最多发送14次 | — | AuthController.java |
| `AUTH_015` | 邮件发送失败，请稍后重试 | — | AuthController.java |
| `AUTH_016` | 验证码已发送 | — | AuthController.java |
| `AUTH_017` | 密码不能为空 | — | AuthController.java |
| `AUTH_018` | 验证码不能为空 | — | AuthController.java, TotpController.java |
| `AUTH_019` | 用户名格式不正确（3-20字符，仅字母数字下划线连字符） | — | AuthController.java |
| `AUTH_020` | 密码长度必须在{min}-{max}个字符之间 | `min`, `max` | AuthController.java |
| `AUTH_021` | 密码过于简单，请使用更复杂的密码 | — | AuthController.java |
| `AUTH_022` | 输入包含非法字符 | — | AuthController.java |
| `AUTH_023` | 请先获取验证码 | — | AuthController.java |
| `AUTH_024` | 邮箱不匹配（{count}/5） | `count` | AuthController.java |
| `AUTH_025` | 发送验证码的设备与当前设备不匹配（{count}/5） | `count` | AuthController.java |
| `AUTH_026` | 验证码已过期，请重新获取（{count}/5） | `count` | AuthController.java |
| `AUTH_027` | 验证码错误（{count}/5） | `count` | AuthController.java |
| `AUTH_028` | 邮箱已被注册 | — | AuthController.java |
| `AUTH_029` | 用户名已存在 | — | AuthController.java, OauthController.java |
| `AUTH_030` | key 不支持 | — | AuthController.java |
| `AUTH_031` | 邮箱已存在 | — | AuthController.java, OauthController.java |
| `AUTH_032` | 注册成功 | — | AuthController.java |
| `AUTH_033` | 邮箱未注册或验证码错误 | — | AuthController.java |
| `AUTH_034` | 登录成功 | — | AuthController.java, OauthController.java |
| `AUTH_035` | 登录请求过于频繁，请稍后再试 | — | AuthController.java |
| `AUTH_036` | 邮箱或密码错误 | — | AuthController.java, OauthController.java |
| `AUTH_037` | 字段名不能为空 | — | AuthController.java |
| `AUTH_038` | 不支持的字段名 | — | AuthController.java |
| `AUTH_039` | 布尔字段 value 不能为空 | — | AuthController.java |
| `AUTH_040` | 偏好字段 stringValue 不能为空 | — | AuthController.java |
| `AUTH_041` | 请先启用 MFA 再设置登录MFA优先方式 | — | AuthController.java |
| `AUTH_042` | 登录MFA优先方式只能是 totp 或 passkey | — | AuthController.java |
| `AUTH_043` | 当前用户未启用所选 MFA 方式 | — | AuthController.java |
| `AUTH_044` | 敏感验证优先方式只能是 password、email-code、passkey、totp 或 apple | — | AuthController.java |
| `AUTH_045` | 当前用户不可用所选敏感验证方式 | — | AuthController.java |
| `AUTH_046` | 更新成功 | — | AuthController.java, Oauth2PlatformController.java, SsoPlatformController.java |
| `AUTH_047` | RefreshToken不存在 | — | AuthController.java |
| `AUTH_048` | RefreshToken已失效 | — | AuthController.java |
| `AUTH_049` | Token类型无效，应为RefreshToken | — | AuthController.java |
| `AUTH_050` | RefreshToken无效或已过期 | — | AuthController.java |
| `AUTH_051` | 刷新成功 | — | AuthController.java |
| `AUTH_052` | 跨端票据已创建 | — | AuthController.java |
| `AUTH_053` | 跨端票据创建失败 | — | AuthController.java |
| `AUTH_054` | transferCode 不能为空 | — | AuthController.java, SessionTransferService.java |
| `AUTH_055` | target 不能为空 | — | AuthController.java |
| `AUTH_056` | 跨端登录成功 | — | AuthController.java |
| `AUTH_057` | 跨端登录失败 | — | AuthController.java |
| `AUTH_058` | 请求体不能为空 | — | AuthController.java |
| `AUTH_059` | 移动桥接挑战已创建 | — | AuthController.java |
| `AUTH_060` | 移动桥接挑战创建失败 | — | AuthController.java |
| `AUTH_061` | challengeId 不能为空 | — | AuthController.java |
| `AUTH_062` | 网页登录确认成功 | — | AuthController.java |
| `AUTH_063` | 网页登录确认失败 | — | AuthController.java |
| `AUTH_064` | 移动桥接挑战已取消 | — | AuthController.java |
| `AUTH_065` | 移动桥接挑战取消失败 | — | AuthController.java |
| `AUTH_066` | 移动桥接挑战已过期 | — | AuthController.java |
| `AUTH_067` | 获取移动桥接状态失败 | — | AuthController.java |
| `AUTH_068` | 当前会话风险过高，已被冻结，请重新登录 | — | AuthController.java |
| `AUTH_069` | 当前登录会话无效，请重新登录后再试 | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_070` | 恢复授权已生成 | — | AuthController.java |
| `AUTH_071` | 恢复授权生成失败 | — | AuthController.java |
| `AUTH_072` | recoveryCode 不能为空 | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_073` | 恢复授权不存在、已失效或已被使用 | — | AuthController.java, AccountRecoveryService.java |
| `AUTH_074` | 发起恢复授权的设备已退出登录，请重新发起 | — | AuthController.java |
| `AUTH_075` | 查询成功 | — | AuthController.java, OauthController.java |
| `AUTH_076` | 新密码不能为空 | — | AuthController.java |
| `AUTH_077` | 恢复授权读取失败 | — | AuthController.java |
| `AUTH_078` | 账号恢复成功 | — | AuthController.java |
| `AUTH_079` | 账号恢复扫码背书已创建 | — | AuthController.java |
| `AUTH_080` | target 只能是 web、desktop 或 mobile | — | AuthController.java, SessionTransferService.java |
| `AUTH_081` | 二维码挑战已创建 | — | AuthController.java |
| `AUTH_082` | mfaChallengeId 不能为空 | — | AuthController.java |
| `AUTH_083` | mfaChallengeId 无效或已过期 | — | AuthController.java |
| `AUTH_084` | 当前 challenge 不允许使用扫码验证 | — | AuthController.java |
| `AUTH_085` | approveCode 或 transferCode 不能为空 | — | AuthController.java |
| `AUTH_086` | 二维码挑战不存在或已过期 | — | AuthController.java, QrChallengeService.java |
| `AUTH_087` | 跨端票据不存在或已过期 | — | AuthController.java, SessionTransferService.java |
| `AUTH_088` | approveCode 不能为空 | — | AuthController.java |
| `AUTH_089` | 二维码挑战状态不可用 | — | AuthController.java, QrChallengeService.java |
| `AUTH_090` | 二维码挑战类型无效 | — | AuthController.java |
| `AUTH_091` | 当前账号未配置可用的非扫码 MFA 方式 | — | AuthController.java |
| `AUTH_092` | 二维码挑战用户不匹配 | — | AuthController.java |
| `AUTH_093` | mfaChallengeId 缺失 | — | AuthController.java |
| `AUTH_094` | MFA 挑战用户不匹配 | — | AuthController.java |
| `AUTH_095` | 二维码挑战无效 | — | AuthController.java |
| `AUTH_096` | 二维码挑战已授权 | — | AuthController.java |
| `AUTH_097` | 二维码挑战授权失败 | — | AuthController.java |
| `AUTH_098` | challengeId 与 pollToken 不能为空 | — | AuthController.java |
| `AUTH_099` | 二维码挑战已过期 | — | AuthController.java |
| `AUTH_100` | pollToken 无效 | — | AuthController.java |
| `AUTH_101` | 会话不存在 | — | AuthController.java |
| `AUTH_102` | 会话已失效 | — | AuthController.java |
| `AUTH_103` | 会话已取消 | — | AuthController.java |
| `AUTH_104` | challengeId 和 code 或 recoveryCode 不能为空 | — | AuthController.java |
| `AUTH_105` | challengeId 无效或已过期 | — | AuthController.java |
| `AUTH_106` | 当前 challenge 不允许使用 TOTP | — | AuthController.java |
| `AUTH_107` | TOTP/恢复码校验失败，剩余尝试次数：{remaining} | `remaining` | AuthController.java |
| `AUTH_108` | TOTP/恢复码校验失败次数过多，请重新登录 | — | AuthController.java |
| `AUTH_109` | TOTP 验证处理失败 | — | AuthController.java |
| `AUTH_110` | mfaChallengeId、passkeyChallengeId 及 Passkey 参数不能为空 | — | AuthController.java |
| `AUTH_111` | 当前 challenge 不允许使用 Passkey | — | AuthController.java |
| `AUTH_112` | Passkey 校验失败 | — | AuthController.java |
| `AUTH_113` | Passkey 校验失败，剩余尝试次数：{remaining} | `remaining` | AuthController.java |
| `AUTH_114` | Passkey 校验失败次数过多，请重新登录 | — | AuthController.java |
| `AUTH_115` | Passkey MFA 验证处理失败 | — | AuthController.java |
| `AUTH_116` | 退出成功 | — | AuthController.java |
| `AUTH_117` | 已从所有设备退出登录 | — | AuthController.java |
| `AUTH_118` | key 不能为空 | — | AuthController.java |
| `AUTH_119` | value 不能为空 | — | AuthController.java |
| `AUTH_120` | 用户名格式不正确（3-20字符，字母数字下划线连字符或简体中文） | — | AuthController.java |
| `AUTH_121` | birthDate 格式不正确，应为 YYYY-MM-DD | — | AuthController.java |
| `AUTH_122` | 请选择头像文件 | — | AuthController.java |
| `AUTH_123` | 头像文件不能超过 3MB | — | AuthController.java |
| `AUTH_124` | 仅支持图片文件 | — | AuthController.java, Oauth2PlatformController.java, SsoPlatformController.java |
| `AUTH_125` | 不支持的图片格式 | — | AuthController.java, Oauth2PlatformController.java, SsoPlatformController.java |
| `AUTH_126` | 头像上传成功 | — | AuthController.java |
| `AUTH_127` | 头像上传失败 | — | AuthController.java |
| `AUTH_128` | 验证方式不能为空 | — | AuthController.java |
| `AUTH_129` | 验证方式只能是 password、email-code、passkey 或 totp | — | AuthController.java |
| `AUTH_130` | 密码错误 | — | AuthController.java |
| `AUTH_131` | 验证成功，有效期15分钟 | — | AuthController.java |
| `AUTH_132` | 验证码或恢复码不能为空 | — | AuthController.java |
| `AUTH_133` | 用户未启用 TOTP | — | AuthController.java, TotpController.java |
| `AUTH_134` | TOTP 验证失败 | — | AuthController.java |
| `AUTH_135` | Passkey 验证请使用 /auth/passkey/sensitive-verification-options 与 /auth/passkey/sensitive-verification-verify | — | AuthController.java |
| `AUTH_136` | 未知的验证方式 | — | AuthController.java |
| `AUTH_137` | 新邮箱不能为空 | — | AuthController.java |
| `AUTH_138` | 邮箱更新成功 | — | AuthController.java |
| `AUTH_139` | 请输入正确的确认文本 | — | AuthController.java |
| `AUTH_140` | 账号已注销 | — | AuthController.java |
| `AUTH_141` | 密码修改成功 | — | AuthController.java |
| `AUTH_142` | 生成注册选项成功 | — | AuthController.java |
| `AUTH_143` | 生成注册选项失败 | — | AuthController.java |
| `AUTH_144` | Passkey 注册成功 | — | AuthController.java |
| `AUTH_145` | Passkey 注册失败 | — | AuthController.java |
| `AUTH_146` | 生成认证选项成功 | — | AuthController.java |
| `AUTH_147` | 生成认证选项失败 | — | AuthController.java |
| `AUTH_148` | Passkey 验证失败 | — | AuthController.java |
| `AUTH_149` | Passkey 认证失败 | — | AuthController.java |
| `AUTH_150` | 生成敏感操作验证选项成功 | — | AuthController.java |
| `AUTH_151` | 生成验证选项失败 | — | AuthController.java |
| `AUTH_152` | 敏感操作验证失败 | — | AuthController.java, PasskeyService.java |
| `AUTH_153` | 获取 Passkey 列表失败 | — | AuthController.java |
| `AUTH_154` | Passkey 删除成功 | — | AuthController.java |
| `AUTH_155` | 删除失败 | — | AuthController.java |
| `AUTH_156` | Passkey 重命名成功 | — | AuthController.java |
| `AUTH_157` | 重命名失败 | — | AuthController.java |
| `OAUTH2_001` | 创建成功，请妥善保存 AppSecret | — | Oauth2PlatformController.java |
| `OAUTH2_002` | 删除成功 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_003` | 请选择 Logo 文件 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_004` | Logo 文件不能超过 3MB | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_005` | Logo 上传成功 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_006` | Logo 上传失败 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_007` | 授权成功 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH2_008` | 撤销成功 | — | Oauth2PlatformController.java, SsoPlatformController.java |
| `OAUTH_001` | QQ 登录成功 | — | OauthController.java |
| `OAUTH_002` | 绑定并登录成功 | — | OauthController.java |
| `OAUTH_003` | 注册并绑定并登录成功 | — | OauthController.java |
| `OAUTH_004` | 未绑定 QQ 帐号 | — | OauthController.java |
| `OAUTH_005` | 无法解绑最后登录方式 | — | OauthController.java |
| `OAUTH_006` | 解绑成功 | — | OauthController.java |
| `OAUTH_007` | 参数缺失 | — | OauthController.java |
| `OAUTH_008` | QQ 移动应用 AppId 未配置或不匹配 | — | OauthController.java |
| `OAUTH_009` | QQ 绑定成功 | — | OauthController.java |
| `OAUTH_010` | QQ 账号已被绑定，请刷新后重试 | — | OauthController.java |
| `OAUTH_011` | code 不能为空 | — | OauthController.java |
| `OAUTH_012` | state 不能为空 | — | OauthController.java |
| `OAUTH_013` | state 格式不正确，需为: 校验参数;操作类型;prd/dev | — | OauthController.java |
| `OAUTH_014` | state 操作类型不支持，仅支持 login/bind/unbind | — | OauthController.java |
| `OAUTH_015` | state 操作类型与当前接口不匹配 | — | OauthController.java |
| `OAUTH_016` | state 环境标识不支持，仅支持 prd/dev | — | OauthController.java |
| `OAUTH_017` | QQ redirectUri 未配置 | — | OauthController.java |
| `OAUTH_018` | {operation} 操作需要有效登录态 | `operation` | OauthController.java |
| `OAUTH_019` | 当前登录态对应用户不存在 | — | OauthController.java |
| `OAUTH_020` | 授权码已使用，请重新发起 QQ 登录 | — | OauthController.java |
| `OAUTH_021` | 登录处理中，请勿重复提交 | — | OauthController.java |
| `OAUTH_022` | QQ token 接口无响应 | — | OauthController.java |
| `OAUTH_023` | QQ token 错误 | — | OauthController.java |
| `OAUTH_024` | 未从 QQ 返回 access_token | — | OauthController.java |
| `OAUTH_025` | QQ me 接口无响应 | — | OauthController.java |
| `OAUTH_026` | QQ me 错误 | — | OauthController.java |
| `OAUTH_027` | 未从 QQ 返回 openid | — | OauthController.java |
| `OAUTH_028` | 未从 QQ 返回 unionid，请确认已开通 getUnionId 权限 | — | OauthController.java |
| `OAUTH_029` | bind 操作需要有效登录态 | — | OauthController.java |
| `OAUTH_030` | unbind 操作需要有效登录态 | — | OauthController.java |
| `OAUTH_031` | 未从 QQ 返回可用于解绑校验的 unionid | — | OauthController.java |
| `OAUTH_032` | 当前授权的 QQ 账号与已绑定账号不一致 | — | OauthController.java |
| `OAUTH_033` | {provider} 账号已被绑定 | `provider` | OauthController.java |
| `OAUTH_034` | 注册失败 | — | OauthController.java |
| `OAUTH_035` | {provider} 绑定成功 | `provider` | OauthController.java |
| `OAUTH_036` | state 格式不正确，需为: verifyToken;operation;env | — | OauthController.java |
| `OAUTH_037` | GitHub redirectUri 未配置 | — | OauthController.java |
| `OAUTH_038` | GitHub redirectUri 非法，请检查后端配置 | — | OauthController.java |
| `OAUTH_039` | GitHub OAuth 配置缺失 | — | OauthController.java |
| `OAUTH_040` | 授权码已使用，请重新发起 GitHub 登录 | — | OauthController.java |
| `OAUTH_041` | GitHub token 接口无响应 | — | OauthController.java |
| `OAUTH_042` | GitHub token 错误 | — | OauthController.java |
| `OAUTH_043` | 未从 GitHub 返回 access_token | — | OauthController.java |
| `OAUTH_044` | GitHub user 接口无响应 | — | OauthController.java |
| `OAUTH_045` | GitHub user 错误 | — | OauthController.java |
| `OAUTH_046` | 未从 GitHub 返回用户 ID | — | OauthController.java |
| `OAUTH_047` | 当前账号已绑定 GitHub | — | OauthController.java |
| `OAUTH_048` | 该 GitHub 账号已被绑定 | — | OauthController.java |
| `OAUTH_049` | GitHub 绑定成功 | — | OauthController.java |
| `OAUTH_050` | state 操作类型不支持，仅支持 login/bind | — | OauthController.java |
| `OAUTH_051` | Google redirectUri 未配置 | — | OauthController.java |
| `OAUTH_052` | Google OAuth 配置缺失 | — | OauthController.java |
| `OAUTH_053` | 授权码已使用，请重新发起 Google 登录 | — | OauthController.java |
| `OAUTH_054` | Google token 错误 | — | OauthController.java |
| `OAUTH_055` | Google token 接口无响应 | — | OauthController.java |
| `OAUTH_056` | Google token 错误: invalid_grant（授权码已使用/过期，或 redirect_uri 与授权请求不一致） | — | OauthController.java |
| `OAUTH_057` | 未从 Google 返回 access_token | — | OauthController.java |
| `OAUTH_058` | 未从 Google 返回用户标识 | — | OauthController.java |
| `OAUTH_059` | 当前账号已绑定 Google | — | OauthController.java |
| `OAUTH_060` | 该 Google 账号已被绑定 | — | OauthController.java |
| `OAUTH_061` | Google 绑定成功 | — | OauthController.java |
| `OAUTH_062` | 未绑定 Google 帐号 | — | OauthController.java |
| `OAUTH_063` | Google 解绑成功 | — | OauthController.java |
| `OAUTH_064` | codeVerifier 不能为空 | — | OauthController.java |
| `OAUTH_065` | Microsoft redirectUri 未配置 | — | OauthController.java |
| `OAUTH_066` | Microsoft OAuth client_id 未配置 | — | OauthController.java |
| `OAUTH_067` | 授权码已使用，请重新发起 Microsoft 登录 | — | OauthController.java |
| `OAUTH_068` | Microsoft token 接口无响应 | — | OauthController.java |
| `OAUTH_069` | Microsoft token 错误 | — | OauthController.java |
| `OAUTH_070` | 未从 Microsoft 返回 access_token | — | OauthController.java |
| `OAUTH_071` | 未从 Microsoft 返回用户标识 | — | OauthController.java |
| `OAUTH_072` | 当前账号已绑定 Microsoft | — | OauthController.java |
| `OAUTH_073` | 该 Microsoft 账号已被绑定 | — | OauthController.java |
| `OAUTH_074` | Microsoft 绑定成功 | — | OauthController.java |
| `OAUTH_075` | 未绑定 Microsoft 帐号 | — | OauthController.java |
| `OAUTH_076` | 当前授权的 Microsoft 账号与已绑定账号不一致 | — | OauthController.java |
| `OAUTH_077` | Microsoft 解绑成功 | — | OauthController.java |
| `OAUTH_078` | 未绑定 GitHub 帐号 | — | OauthController.java |
| `OAUTH_079` | GitHub 解绑成功 | — | OauthController.java |
| `OAUTH_080` | 当前账号已绑定 {provider} | `provider` | OauthController.java |
| `OAUTH_081` | 该 {provider} 账号已被绑定 | `provider` | OauthController.java |
| `OAUTH_082` | QQ openid 校验失败 | — | OauthController.java |
| `OAUTH_083` | QQ 未返回 unionid，请确认已开通 getUnionId 权限 | — | OauthController.java |
| `OAUTH_084` | QQ unionid 校验失败 | — | OauthController.java |
| `OAUTH_085` | QQ AppId 校验失败 | — | OauthController.java |
| `OAUTH_086` | 不支持的第三方账号类型 | — | OauthController.java |
| `OAUTH_087` | 第三方登录状态已过期，请重新授权 | — | OauthController.java |
| `OAUTH_088` | 第三方登录状态无效，请重新授权 | — | OauthController.java |
| `OAUTH_089` | 第三方登录状态缺失，请重新授权 | — | OauthController.java |
| `OAUTH_090` | QQ unionid 缺失，请重新授权 | — | OauthController.java |
| `REQUEST_001` | 推送设置已保存 | — | PushDeviceController.java |
| `REQUEST_002` | 此设备已停止接收推送 | — | PushDeviceController.java |
| `REQUEST_003` | Access token不存在 | — | SensitiveLogController.java |
| `REQUEST_004` | Access token无效或已过期 | — | SensitiveLogController.java |
| `REQUEST_005` | 开始日期格式不正确，请使用 YYYY-MM-DD | — | SensitiveLogController.java |
| `REQUEST_006` | 结束日期格式不正确，请使用 YYYY-MM-DD | — | SensitiveLogController.java |
| `REQUEST_007` | 获取敏感操作日志成功 | — | SensitiveLogController.java |
| `SSO_001` | 创建成功，请妥善保存 ClientSecret | — | SsoPlatformController.java |
| `TOTP_001` | 用户已启用 TOTP | — | TotpController.java |
| `TOTP_002` | 获取 TOTP 注册选项成功 | — | TotpController.java |
| `TOTP_003` | 生成 TOTP 秘钥失败 | — | TotpController.java |
| `TOTP_004` | 恢复码不能为空，请使用 registration-options 返回的 recoveryCodes | — | TotpController.java |
| `TOTP_005` | TOTP 未初始化，请先调用 registration-options | — | TotpController.java |
| `TOTP_006` | 待确认秘钥已过期，请重新生成 | — | TotpController.java |
| `TOTP_007` | TOTP 注册成功 | — | TotpController.java |
| `TOTP_008` | 验证码无效或已过期 | — | TotpController.java |
| `TOTP_009` | TOTP 注册失败 | — | TotpController.java |
| `TOTP_010` | TOTP 验证成功 | — | TotpController.java |
| `TOTP_011` | 使用恢复码验证成功 | — | TotpController.java |
| `TOTP_012` | 验证失败 | — | TotpController.java |
| `TOTP_013` | 获取 TOTP 状态成功 | — | TotpController.java |
| `TOTP_014` | TOTP 禁用成功 | — | TotpController.java |
| `TOTP_015` | 恢复码已重新生成 | — | TotpController.java |
| `TOTP_016` | 重新生成恢复码失败 | — | TotpController.java |
| `TOTP_017` | 获取回复码成功 | — | TotpController.java |
| `REQUEST_008` | 请求方法不支持: {method} | `method` | GlobalExceptionHandler.java |
| `REQUEST_009` | 不支持的请求类型: {contentType} | `contentType` | GlobalExceptionHandler.java |
| `RECOVERY_001` | 创建恢复授权失败 | — | AccountRecoveryService.java |
| `RECOVERY_002` | 读取恢复授权失败 | — | AccountRecoveryService.java |
| `RECOVERY_003` | 解析恢复授权失败 | — | AccountRecoveryService.java |
| `APPLE_014` | Apple 验证用途无效 | — | AppleAuthService.java |
| `APPLE_015` | Apple challenge 用途不匹配 | — | AppleAuthService.java |
| `APPLE_016` | Apple state 校验失败 | — | AppleAuthService.java |
| `APPLE_017` | Apple 授权码身份不一致 | — | AppleAuthService.java |
| `APPLE_018` | 请使用当前账号绑定的 Apple 身份进行验证 | — | AppleAuthService.java |
| `APPLE_019` | 绑定账号不存在 | — | AppleAuthService.java |
| `APPLE_020` | 请先同意服务协议与隐私政策 | — | AppleAuthService.java |
| `APPLE_021` | 此 Apple 身份只能绑定已有账号 | — | AppleAuthService.java |
| `APPLE_022` | Apple 身份已绑定，请重新登录 | — | AppleAuthService.java |
| `APPLE_023` | 邮箱已存在，请登录已有账号后绑定 | — | AppleAuthService.java |
| `APPLE_024` | 用户名格式无效或已被使用 | — | AppleAuthService.java |
| `APPLE_025` | 请在登录后重新授权 Apple 以绑定账号 | — | AppleAuthService.java |
| `APPLE_026` | 此账号或 Apple 身份已绑定 | — | AppleAuthService.java |
| `APPLE_027` | 尚未绑定 Apple | — | AppleAuthService.java |
| `APPLE_028` | 请先设置密码、验证邮箱或绑定其他登录方式 | — | AppleAuthService.java |
| `APPLE_029` | Apple 通知格式无效 | — | AppleAuthService.java |
| `APPLE_030` | Apple 通知事件无效 | — | AppleAuthService.java |
| `APPLE_031` | Apple 通知处理暂时不可用 | — | AppleAuthService.java |
| `APPLE_032` | Apple 通知正在处理中，请重试 | — | AppleAuthService.java |
| `APPLE_033` | Apple 验证会话不匹配，请重新授权 | — | AppleAuthService.java |
| `APPLE_034` | Apple 验证票据无效 | — | AppleAuthService.java |
| `APPLE_035` | Apple 验证已过期或已使用，请重新授权 | — | AppleAuthService.java |
| `APPLE_036` | Apple 验证服务暂时不可用 | — | AppleAuthService.java |
| `APPLE_037` | Apple nonce 校验失败，请重新授权 | — | AppleTokenClient.java |
| `APPLE_038` | Apple 身份无效 | — | AppleTokenClient.java |
| `APPLE_039` | Apple 凭据无效 | — | AppleTokenClient.java |
| `APPLE_040` | Apple audience 不受信任 | — | AppleTokenClient.java |
| `APPLE_041` | Apple JWT 格式无效 | — | AppleTokenClient.java |
| `APPLE_042` | Apple JWT 算法无效 | — | AppleTokenClient.java |
| `APPLE_043` | Apple JWT 声明无效 | — | AppleTokenClient.java |
| `APPLE_044` | Apple 凭据校验失败，请重新授权 | — | AppleTokenClient.java |
| `APPLE_045` | Apple key id 无效 | — | AppleTokenClient.java |
| `APPLE_046` | Apple 签名密钥未知 | — | AppleTokenClient.java |
| `APPLE_047` | Apple 授权码缺失 | — | AppleTokenClient.java |
| `APPLE_048` | Apple 未返回可撤销凭据，请重新授权 | — | AppleTokenClient.java |
| `APPLE_049` | Apple 通知签名或 audience 无效 | — | AppleTokenClient.java |
| `APPLE_050` | Apple 服务端私钥配置无效 | — | AppleTokenClient.java |
| `APPLE_051` | Apple 服务暂时不可用，请稍后重试 | — | AppleTokenClient.java |
| `REQUEST_010` | 不支持的授权模式 | — | AuthorizationGrantPolicy.java |
| `REQUEST_011` | 限时授权有效期必须在 {min}-{max} 秒之间 | `min`, `max` | AuthorizationGrantPolicy.java |
| `MOBILE_AUTHORIZATION_001` | 授权请求不完整 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_002` | 授权请求过长 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_003` | 请先登录后授权 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_004` | 授权请求校验失败 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_005` | 网页来源不受信任 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_006` | 授权请求无效 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_007` | 请求正在处理中，请稍后重试 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_008` | 授权已提交，请等待结果 | — | MobileAuthorizationService.java |
| `MOBILE_AUTHORIZATION_009` | 授权请求已完成或过期，请返回网页重试 | — | MobileAuthorizationService.java |
| `MOBILE_BRIDGE_001` | 创建移动桥接挑战失败 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_002` | 读取移动桥接挑战失败 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_003` | 移动桥接挑战不存在或已过期 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_004` | 移动桥接挑战已处理 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_005` | 更新移动桥接挑战失败 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_006` | returnUrl 不能为空 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_007` | returnUrl 格式不正确 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_008` | returnUrl 必须是完整地址 | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_009` | returnUrl 仅支持 http 或 https | — | MobileBridgeService.java |
| `MOBILE_BRIDGE_010` | returnUrl 不在允许的域名白名单中 | — | MobileBridgeService.java |
| `OAUTH2_009` | AppID 不能为空 | — | Oauth2PlatformService.java |
| `OAUTH2_010` | 应用名称不能为空 | — | Oauth2PlatformService.java |
| `OAUTH2_011` | 联系方式不能为空 | — | Oauth2PlatformService.java |
| `OAUTH2_012` | 回调地址不能为空 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_013` | 每位认证用户最多只能创建 {maxApps} 个 OAuth2 应用 | `maxApps` | Oauth2PlatformService.java |
| `OAUTH2_014` | 当前仅支持 authorization_code | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_015` | AppSecret 不正确 | — | Oauth2PlatformService.java |
| `OAUTH2_016` | 授权码无效、已过期或已使用 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_017` | 授权码与当前应用或回调地址不匹配 | — | Oauth2PlatformService.java |
| `OAUTH2_018` | 授权用户不存在 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_019` | 授权已撤销或已过期 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_020` | Access Token 无效或已过期 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_021` | Access Token 对应用户不存在 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_022` | 仅个人认证、企业认证或管理员账号可以创建 OAuth2 应用 | — | Oauth2PlatformService.java |
| `OAUTH2_023` | 应用不存在 | — | Oauth2PlatformService.java |
| `OAUTH2_024` | AppID 不存在或已停用 | — | Oauth2PlatformService.java |
| `OAUTH2_025` | 仅支持 response_type=code | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_026` | redirect_uri 与应用登记信息不一致 | — | Oauth2PlatformService.java |
| `OAUTH2_027` | 应用名称长度需在 2-100 个字符之间 | — | Oauth2PlatformService.java |
| `OAUTH2_028` | 联系方式长度需在 3-120 个字符之间 | — | Oauth2PlatformService.java |
| `OAUTH2_029` | 回调地址仅支持 https:// 或 http://localhost | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_030` | 回调地址不允许包含 URL fragment | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_031` | 回调地址格式不正确 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_032` | 回调地址总长度不能超过 500 个字符 | — | Oauth2PlatformService.java |
| `OAUTH2_033` | 请求的 scope 超出了应用已登记的权限范围 | — | Oauth2PlatformService.java |
| `OAUTH2_034` | 暂仅支持 profile、email 两种 scope | — | Oauth2PlatformService.java |
| `OAUTH2_035` | 生成 AppID 失败，请稍后重试 | — | Oauth2PlatformService.java |
| `OAUTH2_036` | 生成授权码失败 | — | Oauth2PlatformService.java, SsoPlatformService.java |
| `OAUTH2_037` | 生成用户标识失败 | — | Oauth2PlatformService.java |
| `PASSKEY_001` | Challenge 已过期或不存在 | — | PasskeyService.java |
| `PASSKEY_002` | Passkey 注册验证失败 | — | PasskeyService.java |
| `PASSKEY_003` | AuthenticatorData 缺少 AttestedCredentialData | — | PasskeyService.java |
| `PASSKEY_004` | 此 Passkey 已被注册 | — | PasskeyService.java |
| `PASSKEY_005` | Passkey 不存在 | — | PasskeyService.java |
| `PASSKEY_006` | Passkey 认证验证失败 | — | PasskeyService.java |
| `PASSKEY_007` | Passkey 计数异常，可能是克隆的 Passkey | — | PasskeyService.java |
| `PASSKEY_008` | Passkey 不属于当前用户 | — | PasskeyService.java |
| `PASSKEY_009` | 无权限删除此 Passkey | — | PasskeyService.java |
| `PASSKEY_010` | Passkey 名称不能为空 | — | PasskeyService.java |
| `PASSKEY_011` | Passkey 名称长度不能超过 50 个字符 | — | PasskeyService.java |
| `PASSKEY_012` | 无权限修改此 Passkey | — | PasskeyService.java |
| `QR_001` | 保存二维码挑战失败 | — | QrChallengeService.java |
| `SESSION_TRANSFER_001` | 创建跨端票据失败 | — | SessionTransferService.java |
| `SESSION_TRANSFER_002` | 读取跨端票据失败 | — | SessionTransferService.java |
| `SESSION_TRANSFER_003` | 跨端票据目标端不匹配 | — | SessionTransferService.java |
| `SESSION_TRANSFER_004` | 跨端票据数据无效 | — | SessionTransferService.java |
| `SESSION_TRANSFER_005` | 解析跨端票据失败 | — | SessionTransferService.java |
| `SSO_002` | ClientID 不能为空 | — | SsoPlatformService.java |
| `SSO_003` | 客户端名称不能为空 | — | SsoPlatformService.java |
| `SSO_004` | 当前环境最多只能创建 {maxClients} 个 SSO 内部服务应用 | `maxClients` | SsoPlatformService.java |
| `SSO_005` | ClientSecret 不正确 | — | SsoPlatformService.java |
| `SSO_006` | 授权码与当前客户端或回调地址不匹配 | — | SsoPlatformService.java |
| `SSO_007` | 只有管理员可以创建或管理 SSO 内部服务应用 | — | SsoPlatformService.java |
| `SSO_008` | SSO 客户端不存在 | — | SsoPlatformService.java |
| `SSO_009` | SSO 客户端不存在或已停用 | — | SsoPlatformService.java |
| `SSO_010` | nonce 长度不能超过 255 个字符 | — | SsoPlatformService.java |
| `SSO_011` | 当前 SSO 客户端要求必须提供 PKCE 参数 | — | SsoPlatformService.java |
| `SSO_012` | code_challenge 与 code_challenge_method 必须同时提供 | — | SsoPlatformService.java |
| `SSO_013` | 当前仅支持 code_challenge_method=S256 | — | SsoPlatformService.java |
| `SSO_014` | code_challenge 长度需在 43-128 个字符之间 | — | SsoPlatformService.java |
| `SSO_015` | 缺少 PKCE 校验信息 | — | SsoPlatformService.java |
| `SSO_016` | 缺少 code_verifier | — | SsoPlatformService.java |
| `SSO_017` | code_verifier 长度无效 | — | SsoPlatformService.java |
| `SSO_018` | PKCE 校验失败 | — | SsoPlatformService.java |
| `SSO_019` | 客户端名称长度需在 2-120 个字符之间 | — | SsoPlatformService.java |
| `SSO_020` | 至少需要配置一个 redirect_uri | — | SsoPlatformService.java |
| `SSO_021` | redirect_uri 与客户端登记信息不一致 | — | SsoPlatformService.java |
| `SSO_022` | SSO 暂仅支持 openid、profile、email | — | SsoPlatformService.java |
| `SSO_023` | SSO 授权请求必须包含 openid | — | SsoPlatformService.java |
| `SSO_024` | 请求的 scope 超出了客户端登记范围 | — | SsoPlatformService.java |
| `SSO_025` | 生成 sub 失败 | — | SsoPlatformService.java |
| `SSO_026` | 生成 ClientID 失败，请稍后重试 | — | SsoPlatformService.java |
| `REQUEST_UPLOAD_TOO_LARGE` | 上传文件过大，请选择较小文件 | — | GlobalExceptionHandler.java |
| `ADAPTIVE_REASON_SESSION_MISSING` | 未找到当前活跃会话 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IP_CHANGED` | 当前 IP 与会话建立时不一致 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_LOCATION_CHANGED` | 当前位置与会话建立位置发生变化 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_AGE_THREE_DAYS` | 当前会话已持续超过 3 天 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_AGE_TWELVE_HOURS` | 当前会话已持续超过 12 小时 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IDLE_TWO_HOURS` | 当前会话空闲时间超过 2 小时 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_IDLE_THIRTY_MINUTES` | 当前会话已空闲超过 30 分钟 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_RECENT_FAILURES` | 24 小时内存在多次失败认证或敏感操作 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_RECENT_HIGH_RISK` | 最近一次安全事件风险评分较高 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_REASON_STABLE` | 当前会话环境稳定，未发现需要额外认证的风险信号 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_RELOGIN` | 当前会话不存在或已失效，请重新登录 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_STEP_UP` | 建议立即进行一次额外身份验证，再继续执行敏感操作 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_VERIFY` | 建议先完成一次敏感验证，以提升当前会话信任等级 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_VERIFIED` | 当前会话处于已验证状态，可继续执行敏感操作 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_SAFE` | 当前会话风险较低，可继续使用 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_FROZEN` | 当前会话风险极高，系统已冻结会话，请重新登录并完成验证 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ACTION_POLICY_STEP_UP` | 当前会话风险中等，需完成额外身份验证后继续敏感操作 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_FROZEN_TITLE` | 高风险会话已冻结 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_FROZEN_MESSAGE` | 当前会话已被冻结，请重新登录并完成安全验证 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_STEP_UP_TITLE` | 需要额外身份验证 | — | AdaptiveContinuousAuthService.java |
| `ADAPTIVE_ALERT_STEP_UP_MESSAGE` | 当前会话出现风险信号，请先完成额外身份验证 | — | AdaptiveContinuousAuthService.java |
| `QR_REJECT_1` | 未配置可用的非扫码 MFA 方式 | — | AuthController.java |
| `QR_REJECT_2` | 当前登录会话无效 | — | AuthController.java |
| `QR_REJECT_3` | 二维码挑战缺少 webIp | — | AuthController.java |
| `APPLE_BIND_EMAIL_CONFLICT` | 邮箱已存在，请登录已有账号后绑定 Apple | — | AppleAuthController.java |
| `APPLE_BIND_REQUIRED` | 请创建账号，或登录已有账号后绑定 Apple | — | AppleAuthController.java |
| `PASSKEY_MFA_UNSUPPORTED` | 当前账号需要额外的 MFA 验证，但浏览器桥接页无法继续完成 | — | DesktopPasskeyBridgeView.vue |
