# QQ OAuth 解绑回调接口

## 基本信息
- 方法：POST
- 路径：`/oauth/qq/callback/unbind`
- 需要认证：是（AccessToken）
- 请求类型：无请求体

## 请求头
```http
Authorization: Bearer <accessToken>
```

## 用途
用于已登录用户解绑 QQ 账号。
当前逻辑为本地解绑：只校验登录态和敏感操作验证，不需要再跳转 QQ 做二次授权。

## 前端适配说明
1. 用户点击“解绑 QQ”
2. 先调用敏感验证相关接口完成验证（如密码/TOTP/Passkey）
3. 验证通过后，直接调用 `POST /oauth/qq/callback/unbind`（或 `POST /oauth/qq/unbind`）
4. 不再需要拉起 QQ OAuth 页面，也不需要处理 `code/state/redirectUri`

## 解绑前检查规则
1. 必须是有效登录态
2. 需要已通过敏感操作验证（同 `/oauth/qq/unbind`）
3. 当前账号必须已绑定 QQ
4. 若 QQ 是最后登录方式（无密码且无 Passkey），禁止解绑

## 请求参数
无请求体。

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/oauth/qq/callback/unbind
```

## 成功响应（HTTP 200）
```json
{
  "code": 200,
  "msg": "解绑成功"
}
```

## 失败响应
### 1) 未认证或登录态无效（HTTP 401）
```json
{
  "code": 401,
  "msg": "未登录或Token已过期"
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

### 4) 最后登录方式保护（HTTP 202）
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

### 5) 其他错误
- 服务异常：HTTP 500
