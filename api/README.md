# Ksuser Auth API

Spring Boot (Java 21) authentication service powering Ksuser accounts.

- Default port: `8000`
- Main docs: `docs/README.md`
- [统一详细错误码与四端文案对照](docs/api-result-codes.md)：响应使用 `code + errorCode + params`，不再返回 `msg`。

---

## Features

- Register / login (password, email code)
- Passkey (WebAuthn) login + sensitive operation verification
- TOTP MFA + recovery codes
- Session management (multi-device), refresh & revoke (rotation supported)
- OAuth sign-in / binding (GitHub / Google / Microsoft / QQ)
- Official application OIDC login with explicit consent and scoped profile access
- Rate limiting + risk scoring + sensitive operation logs

## Tech Stack

- Spring Boot, Spring Security, Spring MVC
- JPA (MySQL) + Redis
- JWT (`jjwt`)
- WebAuthn (`webauthn4j`)
- `.env` loader (`dotenv-java`)

## Quickstart (Local Dev)

### 1) Prerequisites

- JDK `21`
- MySQL (or set a compatible `DB_URL`)
- Redis

### 2) Configure environment

This project loads environment variables from the `api/` directory:

- `KSUSER_ENV` selects the env file: `.env.<KSUSER_ENV>`
- Default `KSUSER_ENV` is `production`

Recommended setup:

1. Copy `api/.env.example` to `api/.env.development`
2. Fill in at least: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`
3. Start the API with `KSUSER_ENV=development`

Optional:

- Set `KSUSER_ENV_DIR=/path/to/api` if you want to override the env directory.

### 3) Run

```bash
cd api
KSUSER_ENV=development ./gradlew bootRun
```

### 4) Test

```bash
cd api
./gradlew test
```

## Configuration Reference (Env Vars)

Core:

- `KSUSER_ENV`: selects `.env.<env>` (default: `production`)
- `KSUSER_ENV_DIR`: override env directory
- `DEBUG`: `true|false`

Database / cache:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`

Security:

- `JWT_SECRET`
- `ENCRYPTION_MASTER_KEY` (32-byte Base64 key for TOTP secret encryption)

Mail:

- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`

Passkey (WebAuthn):

- `PASSKEY_RP_ID` (e.g. `localhost`)
- `PASSKEY_ORIGIN` (e.g. `http://localhost:5173`)

OAuth:

- `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`
- `MICROSOFT_OAUTH_CLIENT_ID`, `MICROSOFT_OAUTH_CLIENT_SECRET`, `MICROSOFT_OAUTH_TENANT_ID`

Official OIDC:

- `APP_OFFICIAL_ISSUER`
- `APP_OFFICIAL_AUTHORIZATION_URL`
- `APP_OIDC_ISSUER` (开放平台 OAuth2 令牌发行者)

## API Docs

iOS 会话安全推送使用 APNs，默认关闭。启用与迁移步骤见 [iOS 推送配置](../mobile/ios/PUSH_NOTIFICATIONS.md)。

- [Ksuser 官方应用接入指南](docs/official-app-integration.md)（客户端类型、OIDC 授权、scope、令牌校验与撤销）
- [EzBookKeep 官方应用迁移](ezbookkeep-official-migration.md)（原版客户端配置和分阶段发布）

- `docs/README.md` (documentation index)
- `docs/postman/` (Postman collections)

## Notes

- Do not commit real secrets. Use `.env.example` as a template and keep env files private.
- WebAuthn/Passkey requires correct `origin` and `rpId` to match your frontend host.
- Sensitive verification status and risk evaluation do not consume a one-time QR
  approval. Its remaining lifetime is returned in status responses, and the
  approval is consumed atomically when authorizing a protected operation. Passkey
  registration consumes it on final submission, after the options step.

OAuth registration (`POST /oauth/{provider}/register-bind`, where `provider` is
`qq`, `github`, `microsoft`, or `google`) requires `bindToken`, `username`, `email`,
`password`, and `code`. Obtain the email code through `POST /auth/send-code` with
`{"email":"you@example.com","type":"register"}`. Registration remains available
if the user's network changes after the code is sent.
The endpoint uses the same username/password checks and email verification failure
limits as `POST /auth/register`. A third-party OAuth identity alone does not verify
the submitted email address. Successful registrations no longer lock a shared IP;
historical registration IP and User-Agent lock keys are ignored and expire normally.
User-Agent is retained as client information for sessions and security logs.

Verification-code sending keeps a limit for each email address (default: 1 per
minute and 14 per hour), with a broader shared-IP limit (default: 60 per minute and
600 per hour). Configure these through `app.rate-limit.send-code-email-per-minute`,
`app.rate-limit.send-code-email-per-hour`, `app.rate-limit.send-code-ip-per-minute`,
and `app.rate-limit.send-code-ip-per-hour`. Redis checks and reserves both quotas
atomically before sending, so concurrent requests cannot exceed them. A failed
email send still uses its reserved quota; wait for the current window to expire
before retrying. A successful login does not reset verification-code quotas.

---

# Ksuser Auth API（中文）

基于 Spring Boot（Java 21）的身份认证服务。

- 默认端口：`8000`
- 主要文档入口：`docs/README.md`

## 功能概览

- 注册 / 登录（密码、邮箱验证码）
- Passkey（WebAuthn）登录 + 敏感操作验证
- TOTP 双因素认证 + 恢复码
- 会话管理（多设备）、刷新与撤销（支持 refresh rotation）
- OAuth 登录/绑定（GitHub / Google / Microsoft / QQ）
- 官方应用 OIDC 登录（用户明确授权及按 scope 返回资料）
- 限流、风险评分、敏感操作日志

## 本地启动

### 1）前置依赖

- JDK `21`
- MySQL（或配置兼容的 `DB_URL`）
- Redis

### 2）环境变量

服务会从 `api/` 目录加载 `.env.<KSUSER_ENV>`：

- `KSUSER_ENV` 用于选择 env 文件，默认是 `production`

推荐做法：

1. 将 `api/.env.example` 复制为 `api/.env.development`
2. 至少填写：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `JWT_SECRET`
3. 使用 `KSUSER_ENV=development` 启动

可选：

- 如果你想覆盖默认目录，可设置 `KSUSER_ENV_DIR=/path/to/api`。

### 3）启动命令

```bash
cd api
KSUSER_ENV=development ./gradlew bootRun
```

### 4）运行测试

```bash
cd api
./gradlew test
```

## 文档

- [Ksuser 官方应用接入指南](docs/official-app-integration.md)（客户端类型、OIDC 授权、scope、令牌校验与撤销）
- [EzBookKeep 官方应用迁移](ezbookkeep-official-migration.md)（原版客户端配置和分阶段发布）
- `docs/README.md`（文档索引）
- `docs/postman/`（Postman 集合）

## 备注

- 不要提交真实密钥/密码；请以 `.env.example` 为模板，并确保 env 文件不对外公开。
- Passkey（WebAuthn）对 `origin` 与 `rpId` 要求严格，需与前端域名/端口一致。
- 敏感验证状态查询与风险评估不会消费一次性扫码资格，状态会返回其剩余有效期；
  执行受保护操作前原子消费资格。Passkey 注册在最终提交时消费，获取注册选项不会消费。

OAuth 注册并绑定接口 `POST /oauth/{provider}/register-bind`（`qq`、`github`、
`microsoft`、`google`）必须提交 `bindToken`、`username`、`email`、`password` 和
`code`。先通过 `/auth/send-code` 发送 `type=register` 的邮箱验证码，接收验证码后
更换网络仍可完成注册。接口与 `/auth/register` 共用用户名、密码校验和邮箱验证码失败次数限制。
不能仅凭第三方 OAuth 身份证明所填写邮箱的归属。注册成功不再锁定共享 IP；历史注册 IP 和
User-Agent 锁不再读取，等待其自然过期。User-Agent 继续作为会话和安全日志的客户端信息。

验证码发送保留每个邮箱的额度（默认每分钟 1 次、每小时 14 次），共享 IP 使用较宽松的总额度
（默认每分钟 60 次、每小时 600 次）。可通过 `app.rate-limit.send-code-email-per-minute`、
`app.rate-limit.send-code-email-per-hour`、`app.rate-limit.send-code-ip-per-minute` 和
`app.rate-limit.send-code-ip-per-hour` 调整。发送前由 Redis 原子检查并预留邮箱和 IP 额度，
并发请求也受相同限制。邮件发送失败仍占用已预留的额度，需要等待当前限流窗口恢复后重试；
成功登录不会重置验证码发送额度。
