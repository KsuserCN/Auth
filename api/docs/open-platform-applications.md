# 网站、移动应用与 UnionID 申请

开放平台以 `applicationType` 区分 `website` 和 `mobile`，创建后不可更改。网站使用 confidential＋AppSecret；移动应用固定 public＋PKCE S256，不返回 AppSecret。两类共用现有认证要求和总配额，官方 OIDC 应用继续由 Console 独立管理。

## 应用接口

`GET/POST /oauth2/apps`、`PUT/DELETE /oauth2/apps/{appId}` 沿用通行证登录及 CSRF 规则。旧创建请求不传 `applicationType` 时按网站处理。返回中增加 `applicationType`、`platforms`、`unionidStatus`、`unionidReason`、`unionidReviewReason`。`clientType` 是协议属性，不能据此判断应用类别，也不能通过更新改变类别或客户端类型。

移动应用创建示例（名称、联系方式及 scopes 沿用现有校验）：

```json
{
  "applicationType": "mobile",
  "appName": "示例移动应用",
  "contactInfo": "support@example.com",
  "scopes": ["profile"],
  "platforms": {
    "ios": {
      "bundleId": "com.example.app",
      "appIdPrefix": "PREFIX1234",
      "universalLink": "https://login.example.app/ksuser/callback",
      "urlScheme": "exampleapp",
      "appStoreId": "1234567890"
    },
    "android": {
      "packageName": "com.example.app",
      "sha256CertFingerprints": ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"],
      "appLink": "https://login.example.app/android/callback"
    }
  }
}
```

两平台共用 AppID；至少登记一个平台。移动应用不填写网站回调字段，`redirectUri` 返回值由平台回调派生。更新移动应用时提交完整 `platforms`，未包含的平台会删除；平台验证字段只读。iOS 的 App ID 前缀通常等于 Team ID，历史开发者账号可能不同，应使用实际 Application Identifier entitlement 的前缀。URL Scheme 仅作资料，SDK 只接受 HTTPS 回调。Android 指纹为 SHA-256，支持多个正式证书，不使用截图中的 MD5。

## 平台验证

`POST /oauth2/apps/{appId}/platforms/{ios|android}/verify` 返回最新应用详情；所有权受服务端校验。平台 `verificationStatus` 为 `unverified / verified / failed`，并提供 `verificationMessage`、`verifiedAt`。每应用每平台一分钟只能尝试一次。

- iOS：回调域名 `/.well-known/apple-app-site-association` 必须在 applinks 中授权 `appIdPrefix.bundleId` 及回调路径，并在 webcredentials 中授权相同应用，以支持 SDK 网页继续。
- Android：本版验证器要求静态关联；包含 Android 15 动态 App Link 规则时拒绝验证，避免误判排除规则。`/.well-known/assetlinks.json` 必须声明 `delegate_permission/common.handle_all_urls`、对应包名及全部登记的 SHA-256 证书指纹。
- 关联文件必须直接返回 HTTP 200，Content-Type 为 `application/json`（可带 charset）。
- 服务端仅访问公网 HTTPS 443；固定解析后的目标 IP，校验 TLS 域名，禁用重定向，设置总时限及 128 KiB 响应上限。
- 平台关联资料变更后必须重新验证；旧验证结果不会覆盖新配置。验证文件不是运行时调用方认证，实际关联还由操作系统验证，PKCE/state 和精确回调仍不可省略。

`platform=ios` 和 `platform=android` 均可发起原生授权，必须验证所选平台自己的精确回调。两平台可共用一个 AppID 和 HTTPS 回调，原生 ticket 和授权码仍仅绑定所选平台修订。Android-only 应用也可通过网页备用、普通网页和扫码授权，所有入口均校验匹配回调的已验证平台；网站和未验证平台不能绕过移动登记。SDK、Ksuser App、API 和网页需使用相互兼容的版本，源码支持不代表远程 SDK 或线上能力已经开放。

## UnionID 能力

`POST /oauth2/apps/{appId}/unionid/apply`，请求 `{"reason":"用于网站和移动应用统一账号"}`。申请理由必填、最多 1000 字。状态为 `none / pending / approved / rejected / revoked`；只有认证开发者或管理员可提交。已开通或待审时禁止重复提交。

`DELETE /oauth2/apps/{appId}/unionid`：待审时撤回到 `none`；已开通时关闭到 `revoked`。可在驳回、撤回或关闭后重新申请。关闭不影响普通登录和 OpenID。

Console 接口使用独立管理员会话：

- `GET /console/unionid-applications?page=0&size=20&status=pending`：支持 all、pending、approved、rejected、revoked、withdrawn；返回现有 PageView。
- `POST /console/unionid-applications/{id}/review`：`{"decision":"approve|reject|revoke","reason":"审核意见"}`；理由必填、最多 1000 字，操作要求新鲜管理员验证，并写入审计日志。

审核与申请、撤回按应用串行处理。旧记录不能批准或撤销新一轮申请。申请记录的 `withdrawn` 对应应用能力的 `none`。

同一开发者**账号**下获批应用共享同一用户的 UnionID，不按企业名称合并不同账号。OpenID 始终按 AppID 隔离。仅已开通应用在 Token 和 UserInfo 中获得 `unionid`，未开通字段缺省；读取 UserInfo 时实时检查权限。撤销停止后续提供，但已交付的 JWT 和第三方保存的标识不能收回。

## 数据库更新

执行尚未部署的 `sql/migrations/20261019_oauth_public_pkce.sql` 最终版即可。它增加类别、平台配置独立表、UnionID 状态和申请历史表。历史应用按网站迁移，保留密钥、回调、原有 UnionID 值与已开通能力，并生成历史开通记录；新建默认未开通。脚本重复执行不重置已审核或已撤销状态。全新环境使用同步后的 `sql/init.sql`。
