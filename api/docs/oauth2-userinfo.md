# 获取 OAuth2 / OIDC 用户信息

- 路径：`GET /oauth2/userinfo`
- 作用：通过 Access Token 读取当前授权用户信息
- 认证：`Authorization: Bearer <access_token>`

## 成功示例

```bash
curl 'http://localhost:8000/oauth2/userinfo' \
  -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'
```

```json
{
  "openid": "oid_xxx",
  "unionid": "uid_xxx",
  "sub": "oid_xxx",
  "nickname": "demo-user",
  "preferred_username": "demo-user",
  "avatar_url": "https://cdn.example.com/avatar.png",
  "picture": "https://cdn.example.com/avatar.png",
  "email": "demo@example.com"
}
```

说明：

- `openid`、`unionid` 始终返回
- 当授权范围包含 `openid` 时，额外返回 OIDC 标准字段 `sub`
- `nickname`、`preferred_username`、`avatar_url`、`picture` 需要 `profile`
- `email` 需要 `email`
