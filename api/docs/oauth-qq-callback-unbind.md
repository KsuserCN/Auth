# QQ OAuth 解绑回调接口

## 基本信息
- 方法：POST
- 路径：`/oauth/qq/callback/unbind`
- 需要认证：是（AccessToken）
- 请求类型：`application/json`

## 请求头
```http
Authorization: Bearer <accessToken>
```

## 用途
用于已登录用户解绑 QQ 账号。该接口会先通过 QQ 授权回调确认本次授权身份，再执行解绑。

## state 规范
- 格式：`校验参数;操作类型;prd/dev`
- 本接口要求：`操作类型=unbind`
- 示例：`5501171622cef638d3851ad5a2e8ebc1;unbind;dev`

## 解绑前检查规则
1. 必须是有效登录态
2. 需要已通过敏感操作验证（同 `/oauth/qq/unbind`）
3. 当前账号必须已绑定 QQ
4. 当前授权的 QQ 身份必须与已绑定身份一致
- 优先比较 `unionid`（若已绑定记录存在 unionid）
- 否则比较 `openid`
5. 若 QQ 是最后登录方式（无密码且无 Passkey），禁止解绑

## 请求参数（JSON）
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | string | 是 | QQ 返回的一次性授权码 |
| redirectUri | string | 是 | 前端回调地址，必须在白名单中 |
| state | string | 是 | 状态参数，操作类型必须是 unbind |

## 请求示例
```json
{
  "code": "AUTH_CODE_FROM_QQ",
  "redirectUri": "https://auth.ksuser.cn/oauth/qq/callback",
  "state": "5501171622cef638d3851ad5a2e8ebc1;unbind;prd"
}
```

## 成功响应（HTTP 200）
```json
{
  "code": 200,
  "msg": "解绑成功"
}
```

## 失败响应
### 1) 未登录或登录态无效（HTTP 403）
```json
{
  "code": 403,
  "msg": "unbind 操作需要有效登录态"
}
```

### 2) 需要敏感操作验证（HTTP 202）
```json
{
  "code": 202,
  "msg": "需要完成敏感操作验证",
  "data": {
    "needVerification": true,
    "message": "解绑 QQ 帐号属于敏感操作，需要先完成身份验证"
  }
}
```

### 3) 未绑定 QQ（HTTP 404）
```json
{
  "code": 404,
  "msg": "未绑定 QQ 帐号"
}
```

### 4) 回调 QQ 与绑定 QQ 不一致（HTTP 409）
```json
{
  "code": 409,
  "msg": "当前授权的 QQ 账号与已绑定账号不一致"
}
```

### 5) 最后登录方式保护（HTTP 202）
```json
{
  "code": 202,
  "msg": "无法解绑最后登录方式",
  "data": {
    "canUnbind": false,
    "reason": "last_login_method",
    "message": "无法解绑，这是您的最后一种登录方式"
  }
}
```

### 6) 其他错误
- 参数/状态错误：HTTP 400（同登录回调）
- 请求频繁：HTTP 429
- 上游异常：HTTP 502
- 服务异常：HTTP 500
