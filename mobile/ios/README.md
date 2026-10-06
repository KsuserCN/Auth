# iOS 第三方应用授权（OAuth / OIDC）

网页 `/oauth/authorize` 和 `/sso/authorize` 在 iPhone、iPad 上校验授权请求后，会尝试通过 `ksuserauth://authorize?ticket=…` 打开原生 App。未安装 App、浏览器限制跳转或用户希望在网页操作时，可以选择“继续网页授权”。网页继续前会取消临时 App 请求，避免同时提交两份授权。

App 接收服务端生成的五分钟临时请求，登录后读取真实的应用图标、名称、联系方式、返回网站及授权范围，并明确确认使用的账号。用户可选择长期、一次性或限时授权。OAuth 的联系方式来自注册应用；OIDC 客户端目前没有单独的联系方式字段，可用 API 的 `APP_SSO_CONTACT_INFO` 配置统一联系方式，未配置时页面显示“应用未提供联系方式”。

同意或拒绝后，App 自动打开可信网页 `/app/authorize-return`。网页从 URL fragment 读取临时请求及校验密钥，立即移除 fragment，再一次性领取服务端结果并跳转至已注册的第三方回调地址。网页不会登录到 App 账号，也不会接收 App 的访问令牌。原始 `state`、OIDC `nonce`、PKCE challenge 与 method 全程由服务端保存。

- API `/auth/mobile-authorization/create`、`consume`、`cancel` 可在浏览器未登录时使用，写请求仍要求 CSRF 校验；`context`、`decide` 要求登录。
- 网页来源复用 `APP_MOBILE_BRIDGE_ALLOWED_RETURN_ORIGINS` 白名单。生产 App 允许回跳 `https://auth.ksuser.cn`、`https://www.ksuser.cn` 的固定 `/app/authorize-return` 路径。
- 保持 `/app/authorize-return` 由网页承接，网站 AASA 配置不要将此回跳路径直接唤起 App。App 同时识别 `/app/authorize?ticket=…` Universal Link，网页默认使用已注册的 URL scheme。
- 自动唤起是否成功取决于 iOS 浏览器与是否安装新版 App。网页保留“打开 Ksuser App”“已在 App 完成，继续”和网页授权入口。自动领取仅发生于回跳页，避免原标签页和回跳页争抢同一份结果。

本地验证：`npm --prefix web run test:unit -- --run`、API 的 `MobileAuthorizationServiceTest`，以及 iOS 的 `CoreTests` / `testApplicationConsentShowsMetadataScopesAndDuration`。UI 测试通过 `--ui-testing --ui-test-authenticated --ui-test-application-consent` 启用隔离的界面预览数据。
