# Microsoft OAuth 接入与后端适配说明

## 更新日期

2026年3月28日

## 功能范围

本次前端已接入 Microsoft OAuth 全链路，并与 QQ/GitHub 复用同一个回调页面：

- 登录回调：`POST /oauth/microsoft/callback/login`
- 绑定回调：`POST /oauth/microsoft/callback/bind`
- 解绑回调：`POST /oauth/microsoft/callback/unbind`

前端回调路径：`/oauth/microsoft/callback`

## 前端关键约定

### 1. 授权地址

前端跳转 Microsoft Entra ID 授权端点：

`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize`

参数：

- `client_id`: 来自 `VITE_OAUTH_MICROSOFT_CLIENT_ID`
- `redirect_uri`: `https://auth.ksuser.cn/oauth/microsoft/callback`
- `response_type`: `code`
- `response_mode`: `query`
- `scope`: `openid profile email`
- `state`: 见下方 state 约定
- `code_challenge`: 前端基于 `code_verifier` 生成（PKCE）
- `code_challenge_method`: 固定 `S256`

### 2. tenant 规则

- 优先使用环境变量 `VITE_OAUTH_MICROSOFT_TENANT_ID`
- 未配置时默认使用 `common`
- 若应用仅允许个人 Microsoft 账号（MSA），必须使用 `consumers`

推荐配置：

- `VITE_OAUTH_MICROSOFT_TENANT_ID=consumers`

### 3. state 格式（必须保持一致）

前端 state 采用三段格式：

`verifyToken;operation;env`

示例：

`0a1b2c3d4e...;login;prd`

字段说明：

- `verifyToken`: 随机校验串，用于 CSRF 防护
- `operation`: `login` | `bind` | `unbind`
- `env`: `dev` | `prd`

后端需在回调处理流程中保留并回传原始 state，前端会做严格比对。

## 后端接口契约

### PKCE 说明（必须支持）

Microsoft 当前要求授权码流程启用 PKCE。前端会：

- 发起授权时带上 `code_challenge` 和 `code_challenge_method=S256`
- 回调后将 `code_verifier` 随请求体发送给后端

后端在调用 Microsoft token 交换接口时需要透传 `code_verifier`。

### 1) 登录回调

- 路径：`POST /oauth/microsoft/callback/login`
- 请求体：

```json
{
  "code": "microsoft_auth_code",
  "state": "verifyToken;login;prd",
  "codeVerifier": "pkce_code_verifier"
}
```

响应语义：

- HTTP 200：已绑定用户，直接登录
- HTTP 201：需要 MFA
- HTTP 202：未绑定，需要先绑定账号

#### HTTP 200 示例

```json
{
  "code": 200,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "user": {
      "id": "123",
      "username": "alice",
      "email": "alice@example.com"
    }
  }
}
```

#### HTTP 201 示例

```json
{
  "code": 201,
  "data": {
    "challengeId": "mfa_challenge_xxx",
    "method": "totp"
  }
}
```

#### HTTP 202 示例

```json
{
  "code": 202,
  "data": {
    "needBind": true,
    "openid": "microsoft_subject_or_oid",
    "message": "该 Microsoft 账号尚未绑定，请先绑定或注册账号"
  }
}
```

### 2) 绑定回调

- 路径：`POST /oauth/microsoft/callback/bind`
- 请求体：

```json
{
  "code": "microsoft_auth_code",
  "state": "verifyToken;bind;prd",
  "codeVerifier": "pkce_code_verifier"
}
```

成功响应示例：

```json
{
  "code": 200,
  "data": {
    "bound": true,
    "provider": "microsoft",
    "openid": "microsoft_subject_or_oid",
    "message": "Microsoft 绑定成功"
  }
}
```

### 3) 解绑回调

- 路径：`POST /oauth/microsoft/callback/unbind`
- 请求体（前端仅传 state）：

```json
{
  "code": "microsoft_auth_code",
  "state": "verifyToken;unbind;prd",
  "codeVerifier": "pkce_code_verifier"
}
```

成功响应示例：

```json
{
  "code": 200,
  "data": {
    "canUnbind": true,
    "message": "Microsoft 解绑校验完成"
  }
}
```

## 前端行为映射

- `operation=login`:
  - 200 -> 写入 `accessToken/user`，跳转 `/home`
  - 201 -> 跳转 `/login?challengeId=...&method=...&mfaFrom=microsoft`
  - 202 -> 提示绑定，保存 `microsoft_openid_pending`，跳转 `/login`

- `operation=bind`:
  - `bound === true` -> 跳转 `/home/security`

- `operation=unbind`:
  - `canUnbind !== false` -> 跳转 `/home/security`

## 会话存储键（前端）

- OAuth state: `microsoft_oauth_state`
- 未绑定标识: `microsoft_openid_pending`

说明：回调处理结束后会清理 `microsoft_oauth_state`。

## 独立解绑接口

登录选项页解绑按钮调用：

- `POST /oauth/microsoft/unbind`

建议后端与 QQ/GitHub 保持相同鉴权与风控策略（例如敏感操作验证、最后登录方式限制）。

## 联调检查清单

- 后端支持并区分 200/201/202 三种响应语义
- 回调接口返回字段名与前端契约一致（`accessToken`、`challengeId`、`needBind`、`openid`）
- 回调时原样回传 `state`
- 未绑定场景返回可读 `message`
- 绑定场景建议返回 `provider: "microsoft"`
- 确认 Microsoft 应用中的 Redirect URI 与前端固定值一致
- 后端换取 token 时必须使用 `grant_type=authorization_code` 并携带 `code_verifier`
- 后端应保证 callback 接口对同一 `state` 幂等（避免用户重复刷新回调页导致重复处理）
- 对 `code` / `state` 做 URL 解码后再处理，防止特殊字符导致校验失败

## 后端换取 Token 参考

Microsoft token 端点：

`POST https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`

关键表单参数：

- `client_id`
- `client_secret`（若应用类型要求）
- `grant_type=authorization_code`
- `code`
- `redirect_uri`（必须与前端授权请求一致）
- `code_verifier`

建议：

- 使用短超时和有限重试（避免外部依赖抖动导致登录雪崩）
- 记录 Microsoft `trace_id` / `correlation_id` 便于排查

## 常见错误排查

### AADSTS9002346

现象：

- 回调地址包含 `error=invalid_request`
- `error_description` 中提示：应用仅允许 Microsoft Account users，请使用 `/consumers` 端点

原因：

- 应用类型是“仅个人微软账号”，但请求走了租户 GUID 或 `common`/`organizations` 端点

处理：

- 将 `VITE_OAUTH_MICROSOFT_TENANT_ID` 设为 `consumers`
- 重新发起登录，确认授权地址为：

`https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize`
