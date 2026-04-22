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
