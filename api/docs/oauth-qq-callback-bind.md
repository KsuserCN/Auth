# QQ OAuth 绑定回调接口

## 基本信息
- 方法：POST
- 路径：`/oauth/qq/callback/bind`
- 需要认证：是（AccessToken）
- 请求类型：`application/json`

## 请求头
```http
Authorization: Bearer <accessToken>
```

## 用途
用于已登录用户绑定 QQ 账号。后端会先向 QQ 获取 `openid/unionid`，再写入 `user_oauth_accounts`。
后端会根据 `state` 中的环境标识（`prd/dev`）自动从配置 `app.qq.oauth.redirect-uris` 选择 `redirectUri`，不依赖前端传入。

## state 规范
- 格式：`校验参数;操作类型;prd/dev`
- 本接口要求：`操作类型=bind`
- 示例：`5501171622cef638d3851ad5a2e8ebc1;bind;dev`

## 绑定前检查规则
1. 必须是有效登录态
2. 当前账号不能已绑定 QQ
3. 目标 QQ 账号不能被其他账号占用：
- 优先按 `unionid` 检查
- 若 `unionid` 为空则按 `openid` 检查

## 请求参数（JSON）
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | QQ 返回的一次性授权码 |
| state | string | 是 | 状态参数，操作类型必须是 bind |

## 请求示例
```json
{
  "code": "AUTH_CODE_FROM_QQ",
  "state": "5501171622cef638d3851ad5a2e8ebc1;bind;prd"
}
```

## 成功响应（HTTP 200）
```json
{
  "code": 200,
  "msg": "QQ 绑定成功",
  "data": {
    "bound": true,
    "provider": "qq",
    "openid": "...",
    "unionid": "..."
  }
}
```

## 失败响应
### 1) 未登录或登录态无效（HTTP 403）
```json
{
  "code": 403,
  "msg": "bind 操作需要有效登录态"
}
```

### 2) 登录态对应用户不存在（HTTP 403）
```json
{
  "code": 403,
  "msg": "当前登录态对应用户不存在"
}
```

### 3) 当前账号已绑定 QQ（HTTP 409）
```json
{
  "code": 409,
  "msg": "当前账号已绑定 QQ"
}
```

### 4) 该 QQ 已被其他账号绑定（HTTP 409）
```json
{
  "code": 409,
  "msg": "该 QQ 账号已被绑定"
}
```

### 5) 其他错误
- 参数/状态错误：HTTP 400（同登录回调）
- 请求频繁：HTTP 429
- 上游异常：HTTP 502
- 服务异常：HTTP 500

## 移动端 SDK 绑定接口

移动端可使用 `POST /oauth/qq/mobile-bind` 将腾讯 SDK 授权的 QQ 身份绑定到**当前登录账号**。该接口需要 AccessToken、CSRF 令牌及已完成的敏感操作验证，不签发新的登录令牌或会话。

Web 回调接收 `code`、`state`，移动端接收 SDK 的令牌与身份字段。两种入口分别校验凭据，随后共用已有的账号归属检查和绑定关系写入方法。

请求体与 `/oauth/qq/mobile-login` 相同：

```json
{
  "appId": "1903977704",
  "accessToken": "QQ_SDK_ACCESS_TOKEN",
  "openid": "QQ_OPENID",
  "unionid": "QQ_UNIONID",
  "expiresIn": "3600"
}
```

服务端先核对受信任的移动应用 AppId，再向 QQ 校验凭据与 `openid`、`unionid`。成功返回 HTTP 200、`data.bound=true`；未完成敏感验证返回 HTTP 202；当前账号或其他账号已有 QQ 绑定返回 HTTP 409。调用方只有在响应 `code=200` 时才能显示绑定成功。
