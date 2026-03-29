# GitHub OAuth 接入与后端适配说明

## 更新日期

2026年3月25日

## 功能范围

本次前端已接入 GitHub OAuth 全链路，并与 QQ 复用同一个回调页面：

- 登录回调：`POST /oauth/github/callback/login`
- 绑定回调：`POST /oauth/github/callback/bind`
- 解绑回调：`POST /oauth/github/callback/unbind`

前端回调路径：`/oauth/github/callback`

## 前端关键约定

### 1. 授权地址

前端会跳转到 GitHub 授权端点：

`https://github.com/login/oauth/authorize`

参数：

- `client_id`: 来自 `VITE_OAUTH_GITHUB_CLIENT_ID`
- `redirect_uri`: `https://auth.ksuser.cn/oauth/github/callback`
- `state`: 见下方 state 约定
- `scope`: `read:user user:email`

### 2. state 格式（必须保持一致）

前端 state 采用三段格式：

`verifyToken;operation;env`

示例：

`4b9f...a12c;login;dev`

字段说明：

- `verifyToken`: 随机校验串，用于 CSRF 防护
- `operation`: `login` | `bind` | `unbind`
- `env`: `dev` | `prd`

后端应原样回传 GitHub 回调中的 `state`，前端会做严格比对。

## 后端接口契约

### 1) 登录回调

- 路径：`POST /oauth/github/callback/login`
- 请求体：

```json
{
  "code": "github_auth_code",
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
    "openid": "github_user_id_or_subject",
    "message": "该 GitHub 账号尚未绑定，请先绑定或注册账号"
  }
}
```

### 2) 绑定回调

- 路径：`POST /oauth/github/callback/bind`
- 请求体：

```json
{
  "code": "github_auth_code",
  "state": "verifyToken;bind;prd"
}
```

成功响应示例：

```json
{
  "code": 200,
  "data": {
    "bound": true,
    "provider": "github",
    "openid": "github_user_id_or_subject",
    "message": "GitHub 绑定成功"
  }
}
```

### 3) 解绑回调

- 路径：`POST /oauth/github/callback/unbind`
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
    "message": "GitHub 解绑校验完成"
  }
}
```

失败响应示例：

```json
{
  "code": 400,
  "message": "当前账号仅剩一种登录方式，无法解绑",
  "data": {
    "canUnbind": false,
    "reason": "LAST_LOGIN_METHOD"
  }
}
```

## 前端行为映射

- `operation=login`:
  - 200 -> 写入 `accessToken/user`，跳转 `/home`
  - 201 -> 跳转 `/login?challengeId=...&method=...&mfaFrom=github`
  - 202 -> 提示绑定，保存 `github_openid_pending`，跳转 `/login`

- `operation=bind`:
  - `bound === true` -> 跳转 `/home/security`

- `operation=unbind`:
  - `canUnbind !== false` -> 跳转 `/home/security`

## 会话存储键（前端）

- OAuth state: `github_oauth_state`
- 未绑定标识: `github_openid_pending`

说明：回调处理结束后会清理 `github_oauth_state`。

## GitHub 应用配置建议

在 GitHub Developer settings 中配置 OAuth App：

- Homepage URL：`https://auth.ksuser.cn`
- Authorization callback URL：`https://auth.ksuser.cn/oauth/github/callback`

开发联调建议：

- 使用真实可访问域名回调，或通过网关将回调转发到前端开发环境
- 确保 `state` 被完整透传，避免 URL 解码导致分号丢失

## 联调检查清单

- 后端支持并区分 200/201/202 三种响应语义
- 回调接口返回体字段名与前端契约一致（`accessToken`、`challengeId`、`needBind`、`openid`）
- 回调时原样回传 `state`
- 未绑定场景返回可读 `message`
- `provider` 建议返回 `github`（绑定场景）
