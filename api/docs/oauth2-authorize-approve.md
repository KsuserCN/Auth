# 确认第三方 OAuth 授权

- 路径：`POST /oauth2/authorize/approve`
- 作用：用户点击“同意授权”后，生成一次性授权码并拼好回跳地址
- 认证：需要 Ksuser 通行证登录，同时需要有效的 CSRF cookie 和 `X-XSRF-TOKEN` 请求头
- 请求类型：`application/json`

## 请求体

```json
{
  "clientId": "ksapp_xxx",
  "redirectUri": "https://client.example/ksuser/callback",
  "responseType": "code",
  "scope": "profile email",
  "state": "demo-random-state",
  "codeChallenge": "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
  "codeChallengeMethod": "S256",
  "grantMode": "PERSISTENT"
}
```

public 必须携带 state 和完整 S256 参数；confidential 兼容旧的无 PKCE 请求，提交了 PKCE 就不能在兑换时省略 verifier。这里必须使用[加载上下文](oauth2-authorize-context.md)时的原始客户端、回调、范围、state 和 challenge；verifier 仅由发起应用保存，不发给授权确认页或 Ksuser App。

`grantMode` 可选，默认 `PERSISTENT`；还支持 `ONE_TIME`、`TIME_LIMITED`。仅限时授权使用 `grantTtlSeconds`，默认 3600 秒，范围 300–2592000 秒。资料范围仅支持 profile/email，不能提交 openid/nonce 以请求 ID Token。

本接口是通行证授权页面及受信任批准界面的接口。第三方应打开授权页或调用 [原生 ticket 协议](native-app-authorization.md)，不应收集用户的通行证凭据。

## 成功示例

```json
{
  "code": 200,
  "errorCode": "OAUTH2_007",
  "data": {
    "redirectUrl": "https://client.example/ksuser/callback?code=kscode_xxx&state=demo-random-state"
  }
}
```

授权码默认五分钟有效，只能兑换一次，并绑定 client_id、完整 redirect_uri、批准范围和原始 PKCE challenge。客户端先校验回调 state，再[兑换 access token](oauth2-token.md)。
