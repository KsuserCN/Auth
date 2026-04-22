# 确认 OAuth2 / OIDC 授权

- 路径：`POST /oauth2/authorize/approve`
- 作用：用户点击“同意授权”后，生成一次性授权码并拼好回跳地址
- 认证：需要登录
- 请求类型：`application/json`

## 请求体

```json
{
  "clientId": "ksapp_xxx",
  "redirectUri": "http://localhost:9002/callback",
  "responseType": "code",
  "scope": "openid profile email",
  "state": "demo-state",
  "nonce": "demo-nonce",
  "codeChallenge": "demo_challenge",
  "codeChallengeMethod": "S256"
}
```

## 成功示例

```json
{
  "code": 200,
  "msg": "授权成功",
  "data": {
    "redirectUrl": "http://localhost:9002/callback?code=kscode_xxx&state=demo-state"
  }
}
```
