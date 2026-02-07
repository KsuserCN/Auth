````markdown
# MFA 登录（TOTP）适配说明

## 概述
- 当用户启用了 MFA（TOTP）并存在已配置的 TOTP 密钥时，首次通过密码 / 验证码 / Passkey 完成第一因素认证后，服务不会立即下发最终的登录 token（Access/Refresh）。
  - 服务会返回 HTTP 201 表示进入 MFA 流（需要进行 TOTP 验证），前端应引导用户输入一次性 TOTP 码并调用 `/auth/totp/mfa-verify` 完成登录。

## 流程图（简要）
1. 客户端 POST `/auth/login` 或 `/auth/login-with-code` 或 `/auth/passkey/authentication-verify`（完成第一因素）
2. 如果用户启用 MFA 且有 TOTP，服务返回 201，响应包含 `challengeId` 与 `method: "totp"`
3. 客户端展示 TOTP 输入界面，用户输入 TOTP 验证码
4. 客户端 POST `/auth/totp/mfa-verify`，携带 `challengeId` 和 `code`
5. 服务验证通过后，创建会话、设置 `refreshToken` Cookie（HttpOnly），并返回 200 + `accessToken`

## 第一步：可能返回 201 的接口
- `POST /auth/login`
- `POST /auth/login-with-code`
- `POST /auth/passkey/authentication-verify`

### 201 响应（进入 MFA）
- HTTP Status: 201

```json
{
  "code": 201,
  "msg": "需要 TOTP 验证",
  "data": {
    "challengeId": "0a1b2c3d-...",
    "method": "totp"
  }
}
```

前端收到该响应后，应保存 `challengeId`（通常在内存或短期 localStorage），并引导用户输入 TOTP。

## 第二步：完成 TOTP 验证并下发 token

### 请求
- 方法：POST
- 路径：`/auth/totp/mfa-verify`
- Content-Type：`application/json`

请求体：

```json
{
  "challengeId": "0a1b2c3d-...",
  "code": "123456"
}
```

### 成功响应
- HTTP Status: 200
- Set-Cookie: `refreshToken`（HttpOnly），由服务设置以便后续刷新

响应体示例：

```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJ..."
  }
}
```

说明：
- `accessToken` 用于后续带 `Authorization: Bearer <accessToken>` 调用需要认证的接口
- `refreshToken` 自动写入 HttpOnly Cookie（同域/Secure 等属性由后端及 nginx 配置控制）

### 失败场景
- challengeId 无效或已过期 -> HTTP 400

```json
{
  "code": 400,
  "msg": "challengeId 无效或已过期"
}
```

- TOTP 校验失败 -> HTTP 400

```json
{
  "code": 400,
  "msg": "TOTP 校验失败"
}
```

## 前端实现要点
- 在收到 201 后，不要将任何 token 存储或标记为登录成功；必须先完成 `/auth/totp/verify`。
- `challengeId` 的有效期为短时（默认 5 分钟），请在 UI 中提示用户尽快输入。
- 建议在 `challengeId` 生命周期内绑定客户端信息（例如当前 IP 或 User-Agent），防止被滥用；后端已做部分校验。
- 当 TOTP 验证成功并拿到 `accessToken` 后，使用 `Authorization` 头发送后续请求，并依赖浏览器自动包含的 HttpOnly `refreshToken` Cookie 做刷新。

## 兼容与回退
- 对于未启用 MFA 的用户，登录接口仍然会返回 HTTP 200 并立即下发 token，流程不变。

````
