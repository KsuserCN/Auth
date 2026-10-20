# 换取第三方 OAuth Access Token

- 路径：`POST /oauth2/token`
- 作用：使用一次性授权码换取访问令牌及应用专属身份标识
- 请求类型：`application/x-www-form-urlencoded`

## 表单参数

- `grant_type`：固定 `authorization_code`
- `code`：授权码
- `client_id`：应用 `AppID`
- `client_secret`：confidential 必须在表单中提交；public 不得提交，空字符串也会被拒绝
- `redirect_uri`：必须与授权阶段完全一致
- `code_verifier`：public 必填；confidential 授权阶段使用了 PKCE 时必填，43–128 字符，字符集 `[A-Za-z0-9._~-]`

客户端认证仅支持 `none`（public）和 `client_secret_post`（confidential）。任何 `Authorization` 请求头都会返回 `invalid_client`，包括 Basic、Bearer、空值及同时使用表单密钥的混合认证；不能把通行证登录 token 当作应用凭据。token 接口不需要 CSRF。

历史 confidential 客户端及已签发的无 PKCE 授权码继续使用原有表单密钥流程。原生 App 应新建“移动应用”（固定 public）并验证 iOS 平台回调，不在安装包中保存 secret。`/oauth2` 仅支持 profile/email，不提供 ID Token、refresh token 或 OIDC Discovery；官方应用的 OIDC 接口与密钥策略见 [官方应用接入](official-app-integration.md)。

## public 成功示例

```bash
curl -X POST 'http://localhost:8000/oauth2/token' \
  -d 'grant_type=authorization_code' \
  -d 'code=kscode_xxx' \
  -d 'client_id=ksapp_xxx' \
  -d 'redirect_uri=https://client.example/ksuser/callback' \
  -d 'code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'
```

```json
{
  "access_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 7200,
  "scope": "profile email",
  "openid": "oid_xxx",
  "unionid": "uid_xxx"
}
```

客户端可使用 `Authorization: Bearer <access_token>` 请求 `/oauth2/userinfo` 读取获准资料。第三方身份为应用专属 openid，不提供真实 UUID。`unionid` 仅在应用已获管理员批准时提供；同一开发者账号下获批应用的同一用户 UnionID 一致。未开通或已撤销时，响应和新 JWT 都省略该字段；UserInfo 会重新检查当前权限，不能依靠旧 JWT 的声明绕过撤销。已经交付的旧值无法收回。历史应用迁移保留已有能力和 ID。参见[申请与审核](open-platform-applications.md)。客户端不要把 access token 当作 ID Token 验证；是否仍有效及授权是否撤销由 UserInfo 校验。默认 token 有效期 7200 秒，到期重新授权。

## 错误说明

- `invalid_client`：`client_id` 不存在、已停用、public 提交密钥、confidential 缺少或提交错误密钥，或使用了 Authorization 请求头
- `invalid_grant`：授权码失效、回调地址不匹配，或 PKCE 校验失败
- `unsupported_grant_type`：当前只支持授权码模式
