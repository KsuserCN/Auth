# 敏感操作与账号恢复扫码审批

敏感操作（`approve_sensitive`）和账号恢复（`approve_recovery`）二维码必须先在审批设备重新验证已有凭据，再携带服务端签发的独立证明批准。登录令牌、用户/IP敏感验证状态或另一个二维码的审批结果不能替代独立证明。

## 发起敏感操作二维码

`POST /auth/qr/sensitive/init?purpose=change-password`，要求有效 Passport access token 和 CSRF。

`purpose` 必填，支持：

| 值 | 用途 |
| --- | --- |
| `change-password` | 修改密码 |
| `change-email` | 修改邮箱 |
| `delete-account` | 注销账号 |
| `login-options` | 管理 Passkey、TOTP 和第三方登录方式 |
| `session-transfer` | 账号恢复授权 |

服务端从已认证令牌取得发起会话 ID 与版本，记录用途及固定 120 秒期限。客户端不能提交或指定发起会话。响应维持 `challengeId/pollToken/qrText/expiresInSeconds` 结构。Console 沿用自己的初始化接口和绑定的 verificationRequestId。

## 审批设备验证凭据

先读取现有二维码预览，向用户展示操作及来源。随后调用以下接口之一，均要求当前 Passport access token 和 CSRF。

- `POST /auth/qr/approval/verify`：请求包含 `approveCode`、`method`。`method=password` 提交 `password`；`email-code` 提交发往当前已绑定邮箱的敏感验证 `code`；`totp` 提交 `code`；恢复码可以使用 `method=recovery-code` 和 `recoveryCode`，也兼容 `method=totp` 配合 `recoveryCode`。Console 验证不接受邮箱方式，保持原有管理员认证策略。
- `POST /auth/qr/approval/passkey/options`：请求 `{approveCode}`，返回现有 Passkey options 结构。
- `POST /auth/qr/approval/passkey/verify`：请求 `{approveCode, passkeyChallengeId, credential}`，credential 使用原有 assertion 字段。服务器验证签名和用户验证，并检查 Passkey challenge 与当前用户、会话版本、二维码一致。

验证成功返回 `data={proofId,expiresInSeconds}`。证明绑定用户、审批会话及版本、二维码和用途，有效期不超过二维码剩余时间。凭据尝试按用户和二维码计数，初始为每分钟5次、每小时30次、每个二维码5次。Redis不可用时关闭验证入口，不降级放行。

## 批准与执行

`POST /auth/qr/approve` 请求增加 `{approveCode,proofId}`。普通登录、MFA 和官方应用授权沿用其原有入口；仅敏感和恢复扫码强制独立证明。

Redis 原子检查证明和挑战期限、消费证明、将 pending 转为 approved，并发布对应授权。并发或重复批准只能成功一次，过期挑战不能复活。

- 普通敏感授权绑定发起用户、会话 ID、会话版本、用途及发起 IP。受保护操作消费一次；状态查询不消费。其他会话、版本或用途不能使用。
- Console 只完成其关联的验证请求，不产生 Passport 敏感授权。
- 账号恢复授权与二维码批准在同一个原子操作中发布；失败审批不能生成可用恢复授权。

## 发布与兼容

本改动不需要数据库迁移。二维码、独立证明和一次授权使用版本化 Redis key，旧待处理二维码需要重新生成，旧用户/IP扫码资格失效。不要清空 Redis 或删除现有用户凭据。

后端与 Web、桌面和移动客户端应配套发布。旧扫码客户端缺少 proofId 时返回403与 `SENSITIVE_VERIFICATION_REQUIRED`，应提示更新或改用直接验证方式，不允许回退到旧审批。旧 Web/桌面缺少 purpose 时初始化失败。新移动客户端即使已有一般敏感验证状态，也必须为本次扫码实际重新验证。

切换时应先从负载均衡摘除旧版 API 实例，再统一启用新版；不能让旧实例继续承接敏感和恢复扫码请求。仅更换 Redis key 不会修复仍在运行的旧代码。

这次改动针对扫码审批信任边界。TOTP 首绑的前置认证、OAuth bind-existing 的密码限速属于另外两项修复。
