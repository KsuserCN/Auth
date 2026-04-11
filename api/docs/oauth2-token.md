# 换取 Access Token / ID Token

- 路径：`POST /oauth2/token`
- 作用：使用授权码换取访问令牌；当 `scope` 包含 `openid` 时，同时返回 `id_token`
- 请求类型：`application/x-www-form-urlencoded`

## 表单参数

- `grant_type`：固定 `authorization_code`
- `code`：授权码
- `client_id`：应用 `AppID`
- `client_secret`：应用 `AppSecret`
- `redirect_uri`：必须与授权阶段完全一致
- `code_verifier`：可选，授权阶段使用 PKCE 时必须携带

## 成功示例

```bash
curl -X POST 'http://localhost:8000/oauth2/token' \
  -d 'grant_type=authorization_code' \
  -d 'code=kscode_xxx' \
  -d 'client_id=ksapp_xxx' \
  -d 'client_secret=kssecret_xxx' \
  -d 'redirect_uri=http://localhost:9002/callback' \
  -d 'code_verifier=your_code_verifier'
```

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "openid profile email",
  "openid": "oid_xxx",
  "unionid": "uid_xxx",
  "id_token": "eyJ..."
}
```

## 错误说明

- `invalid_client`：`client_id` 不存在、已停用，或 `client_secret` 错误
- `invalid_grant`：授权码失效、回调地址不匹配，或 PKCE 校验失败
- `unsupported_grant_type`：当前只支持授权码模式
