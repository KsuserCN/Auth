# Ksuser Auth API

Spring Boot (Java 21) authentication service powering Ksuser accounts.

- Default port: `8000`
- Main docs: `docs/README.md`

---

## Features

- Register / login (password, email code)
- Passkey (WebAuthn) login + sensitive operation verification
- TOTP MFA + recovery codes
- Session management (multi-device), refresh & revoke (rotation supported)
- OAuth sign-in / binding (GitHub / Google / Microsoft / QQ)
- Internal SSO + OIDC discovery endpoints
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

This project loads environment variables from the workspace root (the folder that contains both `auth/` and `api/`):

- `KSUSER_ENV` selects the env file: `.env.<KSUSER_ENV>`
- Default `KSUSER_ENV` is `production`

Recommended setup:

1. Copy the workspace root `.env.example` to `.env.development` (also in the workspace root)
2. Fill in at least: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET`
3. Start the API with `KSUSER_ENV=development`

Optional:

- Set `KSUSER_ROOT=/path/to/workspace-root` if you run the service from a different working directory.

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
- `KSUSER_ROOT`: override workspace root discovery
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

SSO / OIDC:

- `APP_SSO_ISSUER`
- `APP_SSO_AUTHORIZATION_ENDPOINT`
- `APP_OIDC_ISSUER`

## API Docs

- `docs/README.md` (documentation index)
- `docs/postman/` (Postman collections)

## Notes

- Do not commit real secrets. Use `.env.example` as a template and keep env files private.
- WebAuthn/Passkey requires correct `origin` and `rpId` to match your frontend host.

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
- 内部 SSO + OIDC discovery 端点
- 限流、风险评分、敏感操作日志

## 本地启动

### 1）前置依赖

- JDK `21`
- MySQL（或配置兼容的 `DB_URL`）
- Redis

### 2）环境变量

服务会从工作区根目录（同时包含 `auth/` 与 `api/` 的目录）加载 `.env.<KSUSER_ENV>`：

- `KSUSER_ENV` 用于选择 env 文件，默认是 `production`

推荐做法：

1. 将工作区根目录的 `.env.example` 复制为 `.env.development`（同样在工作区根目录）
2. 至少填写：`DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `JWT_SECRET`
3. 使用 `KSUSER_ENV=development` 启动

可选：

- 如果不在工作区根目录启动，可设置 `KSUSER_ROOT=/path/to/workspace-root` 指定根目录。

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

- `docs/README.md`（文档索引）
- `docs/postman/`（Postman 集合）

## 备注

- 不要提交真实密钥/密码；请以 `.env.example` 为模板，并确保 env 文件不对外公开。
- Passkey（WebAuthn）对 `origin` 与 `rpId` 要求严格，需与前端域名/端口一致。
