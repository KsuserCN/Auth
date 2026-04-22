# Google OAuth 接入与后端适配说明

## 更新日期

2026年3月29日

## 功能范围

本次前端已接入 Google OAuth 全链路，并与 QQ/GitHub/Microsoft 复用同一个回调页面：

- 登录回调：`POST /oauth/google/callback/login`
- 绑定回调：`POST /oauth/google/callback/bind`
- 解绑回调：`POST /oauth/google/callback/unbind`

前端回调路径：`/oauth/google/callback`

## 前端关键约定

### 1. 授权地址

前端会跳转到 Google 授权端点：

`https://accounts.google.com/o/oauth2/v2/auth`

参数：

- `client_id`: 来自 `VITE_OAUTH_GOOGLE_CLIENT_ID`
- `redirect_uri`: `https://auth.ksuser.cn/oauth/google/callback`
- `response_type`: `code`
- `scope`: `openid email profile`
- `state`: 见下方 state 约定
- `access_type`: `offline`
- `prompt`: `consent`

### 2. state 格式（必须保持一致）

前端 state 采用三段格式：

`verifyToken;operation;env`

示例：

`6a9d...e23b;login;dev`

字段说明：

- `verifyToken`: 随机校验串，用于 CSRF 防护
- `operation`: `login` | `bind` | `unbind`
- `env`: `dev` | `prd`

后端应原样回传 Google 回调中的 `state`，前端会做严格比对。

## 后端接口契约

### 1) 登录回调

- 路径：`POST /oauth/google/callback/login`
- 请求体：

```json
{
  "code": "google_auth_code",
  "state": "verifyToken;login;prd"
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
    "openid": "google_sub",
    "message": "该 Google 账号尚未绑定，请先绑定或注册账号"
  }
}
```

### 2) 绑定回调

- 路径：`POST /oauth/google/callback/bind`
- 请求体：

```json
{
  "code": "google_auth_code",
  "state": "verifyToken;bind;prd"
}
```

成功响应示例：

```json
{
  "code": 200,
  "data": {
    "bound": true,
    "provider": "google",
    "openid": "google_sub",
    "message": "Google 绑定成功"
  }
}
```

### 3) 解绑回调

- 路径：`POST /oauth/google/callback/unbind`
- 请求体（前端仅传 state）：

```json
{
  "state": "verifyToken;unbind;prd"
}
```

成功响应示例：

```json
{
  "code": 200,
  "data": {
    "canUnbind": true,
    "message": "Google 解绑校验完成"
  }
}
```

## 前端行为映射

- `operation=login`:
  - 200 -> 写入 `accessToken/user`，跳转 `/home`
  - 201 -> 跳转 `/login?challengeId=...&method=...&mfaFrom=google`
  - 202 -> 提示绑定，保存 `google_openid_pending`，跳转 `/login`

- `operation=bind`:
  - `bound === true` -> 跳转 `/home/security`

- `operation=unbind`:
  - `canUnbind !== false` -> 跳转 `/home/security`

## 会话存储键（前端）

- OAuth state: `google_oauth_state`
- 未绑定标识: `google_openid_pending`

说明：回调处理结束后会清理 `google_oauth_state`。

## 独立解绑接口

登录选项页解绑按钮调用：

- `POST /oauth/google/unbind`

建议后端与 QQ/GitHub/Microsoft 保持相同鉴权与风控策略（例如敏感操作验证、最后登录方式限制）。

## Google 应用配置建议

在 Google Cloud Console 中配置 OAuth 客户端：

- Authorized JavaScript origins：`https://auth.ksuser.cn`
- Authorized redirect URIs：`https://auth.ksuser.cn/oauth/google/callback`

开发联调建议：

- 使用真实可访问域名回调，或通过网关将回调转发到前端开发环境
- 确保 `state` 完整透传，避免 URL 解码导致分号丢失

## 联调检查清单

- 后端支持并区分 200/201/202 三种响应语义
- 回调接口返回字段名与前端契约一致（`accessToken`、`challengeId`、`needBind`、`openid`）
- 回调时原样回传 `state`
- 未绑定场景返回可读 `message`
- 绑定场景建议返回 `provider: "google"`
- 确认 Google 应用中的 Redirect URI 与前端固定值一致
- 后端用 `code` 换 token 时，`redirect_uri` 必须与授权请求一致
- 后端应保证 callback 接口对同一 `state` 幂等（避免用户重复刷新回调页导致重复处理）
- 对 `code` / `state` 做 URL 解码后再处理，防止特殊字符导致校验失败

## 后端换取 Token 参考

Google token 端点：

`POST https://oauth2.googleapis.com/token`

关键表单参数：

- `client_id`
- `client_secret`
- `grant_type=authorization_code`
- `code`
- `redirect_uri`（必须与前端授权请求一致）

建议：

- 使用短超时和有限重试（避免外部依赖抖动导致登录雪崩）
- 对 Google 返回错误码做分类记录，便于快速定位问题

## 常见错误排查

### redirect_uri_mismatch

现象：

- Google 返回 `error=redirect_uri_mismatch`

原因：

- 后端换 token 或 Google 应用配置中的 redirect_uri 与前端请求不一致

处理：

- 保证授权请求与 token 请求使用完全一致的 redirect_uri：
  `https://auth.ksuser.cn/oauth/google/callback`

### invalid_grant

现象：

- Google token 端点返回 `invalid_grant`

常见原因：

- 授权码已被使用
- 授权码过期
- redirect_uri 不一致
- code 与客户端不匹配

处理：

- 前端重新发起授权流程
- 后端避免重复消费同一个 code（可基于 state 幂等控制）
