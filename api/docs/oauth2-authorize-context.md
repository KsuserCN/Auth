# 获取 OAuth2 / OIDC 授权上下文

- 路径：`GET /oauth2/authorize/context`
- 作用：在授权确认页加载应用信息，并预校验 `scope`、`nonce`、PKCE 参数
- 认证：无需登录，但真正授权前前端仍需确保用户已登录

## 查询参数

- `client_id`：必填，应用 `AppID`
- `redirect_uri`：必填，必须与应用登记的任一回调地址完全一致；应用可登记多个地址，使用英文分号 `;` 分隔
- `response_type`：必填，当前固定 `code`
- `scope`：可选，支持 `openid profile email`
- `nonce`：可选，OIDC 请求建议传入
- `code_challenge`：可选，启用 PKCE 时传入
- `code_challenge_method`：可选，当前仅支持 `S256`

## 成功示例

```bash
curl 'http://localhost:8000/oauth2/authorize/context?client_id=ksapp_xxx&redirect_uri=http%3A%2F%2Flocalhost%3A9002%2Fcallback&response_type=code&scope=openid%20profile%20email&nonce=demo-nonce&code_challenge=demo_challenge&code_challenge_method=S256'
```

```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "clientId": "ksapp_xxx",
    "appName": "Local OIDC Demo",
    "contactInfo": "test@example.com",
    "redirectUri": "http://localhost:9002/callback",
    "requestedScopes": ["profile", "email"],
    "oidcRequest": true
  }
}
```
