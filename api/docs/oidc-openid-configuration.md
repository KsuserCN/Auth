# OIDC 发现文档

- 路径：`GET /.well-known/openid-configuration`
- 作用：提供 OIDC 客户端自动发现所需的元数据
- 认证：无需登录

## 成功示例

```bash
curl 'http://localhost:8000/.well-known/openid-configuration'
```

```json
{
  "issuer": "http://localhost:8000",
  "authorization_endpoint": "http://localhost:5173/oauth/authorize",
  "token_endpoint": "http://localhost:8000/oauth2/token",
  "userinfo_endpoint": "http://localhost:8000/oauth2/userinfo",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code"],
  "subject_types_supported": ["pairwise"],
  "id_token_signing_alg_values_supported": ["HS256"],
  "token_endpoint_auth_methods_supported": ["client_secret_post"],
  "scopes_supported": ["openid", "profile", "email"],
  "claims_supported": ["sub", "openid", "unionid", "nickname", "preferred_username", "picture", "email"],
  "code_challenge_methods_supported": ["S256"],
  "response_modes_supported": ["query"]
}
```
