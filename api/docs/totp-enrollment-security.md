# TOTP 首次绑定与授权

TOTP 绑定分为“验证已有凭据 → 创建绑定流程 → 确认新验证器”。持有有效 access token 或通用敏感验证状态不足以创建绑定流程；新增 TOTP 及其恢复码不能为自己的绑定提供授权。

## 1. 验证已有凭据

以下接口均要求当前 Passport access token、有效会话和 CSRF。

- `POST /auth/totp/enrollment/verify`：`{method:"password",password:"..."}`，或者 `{method:"email-code",code:"..."}`。邮箱验证码通过现有发码接口发送 `type=sensitive-verification`，接收地址由当前账号绑定邮箱决定。
- `POST /auth/totp/enrollment/passkey/options`：获取已有 Passkey 的验证选项。
- `POST /auth/totp/enrollment/passkey/verify`：`{passkeyChallengeId,credential}`，credential 使用现有 WebAuthn assertion 字段。服务端验证签名、UV，并核对 challenge 的用户、会话与版本绑定。

成功返回 `data={proofId,expiresInSeconds:300}`。证明用途固定为 TOTP_ENROLL，仅限签发时的用户、会话及版本，一次消费。二维码证明、通用敏感授权、TOTP 和恢复码均不能替代此证明。凭据校验按账号限制每分钟5次、每小时30次；Redis异常时拒绝继续认证。

仅有第三方登录会话而没有可独立验证的密码、邮箱或已有Passkey的账号，需要先完成可验证的账号恢复/凭据配置流程，不能通过首绑TOTP自行建立可信身份。

## 2. 创建绑定流程

`POST /auth/totp/registration-options` 请求必须是 `{proofId}`。

服务端原子消费证明，再锁定用户及活跃会话，在数据库中创建10分钟有效的绑定流程。新流程取代该用户之前尚未完成的流程，不影响已启用的TOTP。证明消费后若数据库写入失败，需要重新进行已有凭据验证，不恢复证明。

响应仍包含 `secret/qrCodeUrl/recoveryCodes`，新增 `enrollmentId/expiresInSeconds`。密钥和恢复码的待确认数据在服务端加密保存，不能依赖客户端回传恢复码。

## 3. 确认绑定

`POST /auth/totp/registration-verify` 请求改为 `{enrollmentId,code}`，不再提交 `recoveryCodes`。

服务端检查同一用户、同一活跃会话及版本、期限、取消/完成状态，在用户与绑定记录锁内验证新TOTP。每个绑定流程最多5次错误尝试，账号确认请求另有每小时30次额度。错误次数必须提交，不能因为返回错误而回滚。

成功在同一数据库事务中启用密钥、写入服务端恢复码、记录实际匹配的时间步并完成绑定流程。数据库异常整笔回滚；重复/并发确认最多一次成功。确认不会生成通用敏感操作授权。

- `TOTP_008`：普通验证码错误或流程不匹配。
- `TOTP_006`（400）：绑定流程已过期。
- `RATE_LIMITED`（429）：尝试耗尽或账号额度限制。
- `SENSITIVE_VERIFICATION_REQUIRED`（403）：缺失、过期或不属于当前上下文的绑定证明。

过期或尝试耗尽后应清除客户端待绑定信息，并从已有凭据验证重新开始。旧的无 proofId 初始化、无 enrollmentId 确认请求必须失败，不提供旧协议回退。

## 数据库迁移与发布

先备份并在目标数据库执行增量迁移 `api/sql/migrations/20261027_totp_enrollment_authorization.sql`，再发布所有API实例和客户端。迁移新增 `totp_enrollments` 表，只清除旧的未启用pending密钥；保留现有已启用TOTP及恢复码。`api/sql/init.sql`仅适用于初始化数据库，升级已有数据库不要执行该文件。

摘除仍运行旧绑定接口的API实例，避免请求被路由到旧代码。Web、Android、iOS、HarmonyOS和桌面客户端均需更新。不能让旧客户端绕过新的证明或绑定流程检查。

桌面端仅在原生Passkey可用时提供该首绑验证方式；现有浏览器桥接只返回通用敏感验证状态，不能作为绑定证明。不支持原生Passkey的桌面平台需使用已有密码、邮箱，或通过Web完成绑定。

本次变更包含绑定记录实际TOTP时间步，并对实际匹配时间步检查顺序重放；通用TOTP认证的并发重放防护属于另外的加固事项。
