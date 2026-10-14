# EzBookKeep 改用 Ksuser 官方应用

此迁移保留原版 EzBookKeep OIDC 客户端。旧 SSO 客户端凭据与官方应用凭据不能互用；旧 EzBookKeep 账号、账本和 SSO 授权不会自动搬迁。若 EzBookKeep 已有相同邮箱的账号，请在验收时核对其自身账号匹配结果。

当前工作区包含兼容能力和 SSO 退役的最终代码。发布时须拆成两个版本：第一阶段仅部署官方应用 nonce 兼容、`name` claim 及 Console 开关，继续保留旧 `/sso` API 和 Web 管理入口；EzBookKeep 登录验收成功后，才部署本工作区其余的 SSO 退役改动。不能将当前完整工作区作为第一阶段版本直接发布。

## 第一阶段：提供官方应用兼容能力

1. 先备份数据库，执行 `api/sql/migrations/20261014_official_optional_nonce.sql`，再发布支持兼容开关的 API 与 Console。不要先删除旧客户端。
2. 超级管理员在 Console 的「官方应用」新建 `confidential` 应用，名称可设为 `EzBookKeep`，精确登记实际 `server.root_url` 拼接的 `oauth2/callback`。例如 `root_url=https://books.example.com/` 时，回调是 `https://books.example.com/oauth2/callback`。若部署在子路径，连同子路径逐字登记。范围选 `openid profile email`，启用“允许省略 nonce”。创建后立即安全保存一次性展示的 `client_id` 和 `client_secret`。
3. 确认 `https://api.ksuser.cn/official/.well-known/openid-configuration` 返回的 `issuer` 是 `https://api.ksuser.cn/official`，且 EzBookKeep 服务器能访问其中的 Token、UserInfo 与 JWKS 地址。

## 第二阶段：切换原版 EzBookKeep

在 EzBookKeep 的 `ezbookkeeping.ini` 设置以下配置；环境变量也可按其 `EBK_{SECTION}_{OPTION}` 规则配置。替换占位符，保持实际 `root_url` 与 Console 登记的回调一致。

```ini
[server]
root_url = https://books.example.com/

[auth]
enable_oauth2_auth = true
oauth2_provider = oidc
oauth2_client_id = <Console 新建的官方应用 Client ID>
oauth2_client_secret = <仅保存在 EzBookKeep 服务端的密钥>
oauth2_use_pkce = true
oauth2_user_identifier = email
oauth2_auto_register = true
oidc_provider_base_url = https://api.ksuser.cn/official
oidc_provider_check_issuer_url = true
enable_oidc_display_name = true
oidc_custom_display_name = Ksuser

[user]
enable_register = true
```

EzBookKeep 文档说明自动注册还需要 `user.enable_register=true`，并需要可用邮箱；Ksuser `email` scope 返回 `email` 与 `email_verified`。原版客户端未发送 `nonce`，所以该官方应用必须开启兼容开关；其他官方应用继续要求 nonce。EzBookKeep 仍发送 `state` 和 S256 PKCE，并在服务端提交客户端密钥。

用未注册过 EzBookKeep 且有可用邮箱的 Ksuser 账号完成一次登录，检查回调未报错、Issuer 校验通过、自动注册成功、用户名与邮箱正确。若已有相同邮箱的旧 EzBookKeep 用户，检查其账本归属是否符合预期。随后测试再次登录、退出登录以及 Ksuser「隐私」中的官方应用授权查看与撤销。

## 第三阶段：退役旧 SSO

仅在上述验收成功后，删除旧 EzBookKeep SSO 客户端。确认 `SELECT COUNT(*) FROM oidc_clients` 为 `0`，再发布各端 SSO 退役版本，并执行 `api/sql/migrations/20261014_retire_sso.sql`。迁移会在仍有客户端时拒绝删除；检查通过后清理旧授权和两张旧表。验证 `/sso` 授权、Token、UserInfo、JWKS、Discovery 及移动扫码 SSO 模式均不可用，各端仅显示 OAuth 与官方应用授权，官方应用授权仍可撤销。

本配置键、自动注册条件和回调规则参照 [EzBookKeep 官方配置说明](https://ezbookkeeping.mayswind.net/zh_Hans/configuration/) 与[回调实现](https://github.com/mayswind/ezbookkeeping/blob/main/pkg/auth/oauth2/oauth2_authentication.go)。
