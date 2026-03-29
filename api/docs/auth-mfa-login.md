# MFA 登录适配说明（TOTP + Passkey）

## 概述
- 当用户启用 MFA 后，第一因子登录成功时不会立即签发最终 token，而是返回 `201` 并下发 `challengeId`。
- 当前支持的二次验证方式：`totp`、`passkey`。
- 新增约束：如果第一因子本身就是 Passkey（`/auth/passkey/authentication-verify`），则二次验证只允许 `totp`。

## 一因子接口返回 201 的场景
- `POST /auth/login`
- `POST /auth/login-with-code`
- `POST /auth/passkey/authentication-verify`
- 各 OAuth 登录回调接口（QQ/GitHub/Google/Microsoft）

### 201 响应示例（密码/验证码/OAuth 一因子）
```json
{
  "code": 201,
  "msg": "需要 MFA 验证",
  "data": {
    "challengeId": "0a1b2c3d-...",
    "method": "totp",
    "methods": ["totp", "passkey"]
  }
}
```

### 201 响应示例（Passkey 一因子）
```json
{
  "code": 201,
  "msg": "需要 MFA 验证",
  "data": {
    "challengeId": "0a1b2c3d-...",
    "method": "totp",
    "methods": ["totp"]
  }
}
```

## 二次验证方式 1：TOTP

### 请求
- 方法：`POST`
- 路径：`/auth/totp/mfa-verify`

```json
{
  "challengeId": "0a1b2c3d-...",
  "code": "123456"
}
```

### 成功响应
```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJ..."
  }
}
```

## 二次验证方式 2：Passkey

前端需要先获取 WebAuthn 认证选项：
- `POST /auth/passkey/authentication-options`

然后提交 MFA Passkey 验证：
- `POST /auth/passkey/mfa-verify`

```json
{
  "mfaChallengeId": "0a1b2c3d-...",
  "passkeyChallengeId": "webauthn-challenge-id",
  "credentialRawId": "base64url...",
  "clientDataJSON": "base64url...",
  "authenticatorData": "base64url...",
  "signature": "base64url..."
}
```

### 成功响应
```json
{
  "code": 200,
  "msg": "登录成功",
  "data": {
    "accessToken": "eyJ..."
  }
}
```

## 约束与注意事项
- `challengeId` 默认有效期 5 分钟，成功后会被消费（删除）。
- MFA 验证失败会累计尝试次数，超过阈值后需要重新走登录流程。
- `methods` 字段用于前端动态渲染可选 MFA 方式。
- 若 `methods` 不包含 `passkey`，前端不得调用 `/auth/passkey/mfa-verify`。
