# iOS 应用扫码授权（OAuth / 官方应用）

`/oauth/authorize`、`/official/authorize` 默认展示网页登录及授权。电脑浏览器可选择「使用扫码登录」，手机 UA（含 iPad 桌面模式）隐藏该按钮，授权页不自动唤起 App。App Store 超链接在新标签页打开，简体中文使用中国区、English 使用美国区。

App 的「扫码」识别 `approve_application`，从 `/auth/qr/preview` 获得临时授权 ticket，再从 `/auth/mobile-authorization/context` 读取真实应用、回调网站和完整范围，进入原生应用授权页。未登录先完成 App 登录。OAuth 用户可选择长期、一次性或限时授权；官方应用沿用可撤销的资料授权。公共官方应用允许普通账号，Console 的超级管理员权限限制仍有效。

手机提供「同时登录通行证网页端」开关，默认关闭。默认只完成此应用授权，不改变原浏览器的通行证账号；开启后原浏览器可用一次性凭据建立当前手机账号的通行证会话。拒绝授权不会登录网页。手机提交 `/auth/mobile-authorization/decide` 的 `loginWeb` 字段，确认后停留在 App，不打开第三方回调；App 自身会话保持不变。

二维码有效两分钟，仅包含手机审批码。原浏览器通过独立 ticket / secret 轮询 `/qr/status` 并一次性 `consume` 授权结果，随后回到已登记第三方回调。网页登录凭据仅在这份私有结果中提供，不写入二维码、手机链接或第三方回调。刷新或切换网页登录通过 `cancel` 取消旧请求。写请求仍要求 CSRF，`context` / `decide` 要求登录，网页来源沿用 `APP_MOBILE_BRIDGE_ALLOWED_RETURN_ORIGINS` 白名单。原始 state、nonce、PKCE 由服务端保存，第三方无需调整令牌兑换。

旧版 `approve_official` 和 `/auth/qr/official/approve`、`reject` 保留兼容；旧版通用扫码确认不能批准应用授权，避免未展示申请范围就完成授权。发布需同步更新 API、通行证 Web 和 iOS App，无新增数据库迁移。

# iOS Console 扫码登录

在 Console 登录页选择「App 扫码登录」，用 iOS App 的「扫码」扫描。App 通过现有 `/auth/qr/preview` 读取 `approve_console_login` 请求，展示服务端给出的应用、目标网站、当前账号和授权资料，用户点击「确认登录管理中心」后调用 `/auth/qr/approve`。未登录时先完成 App 登录，再继续扫码确认。只有超级管理员可批准登录。

二维码仅包含 `KSUSER-AUTH-QR:` 审批码，确认后浏览器通过独立的 Console Cookie 领取一次性后台会话，App 的账号与访问令牌保持原状态。二维码两分钟过期；刷新或取消后旧请求不能领取会话。API、Console 与 iOS App 需同步发布，无新增数据库结构。

# iOS 网页登录

iPhone、iPad 的网页登录页在 Passkey 下方显示“通过 Ksuser 安全 App 登录”。点击后创建两分钟临时网页登录请求，并通过已注册的 `ksuserauth://bridge-login?challengeId=…` 唤起 App。未登录的 App 会先要求用户登录，随后显示目标站点、当前账号与请求有效期，用户确认后返回浏览器完成网页登录。iOS Chrome 发起的请求附带 `returnBrowser=chrome`，优先回到 Chrome；其他浏览器使用系统默认浏览器。

网页登录复用 `/auth/mobile-bridge/create`、`status`、`approve`、`cancel` 与一次性 `/auth/session-transfer/exchange`，无需新增后端接口。App 从服务端读取目标站点及返回地址，忽略传入链接中的 `returnUrl`。现有 `/app/bridge-login` Universal Link 继续有效；URL scheme 用于同域网页的主动唤起。等待中可重新打开同一请求或取消，未安装新版 App 时网页保留其他登录方式。微信内打开时提示复制链接到系统浏览器。

上线时需同时更新网页和 iOS App；旧版 App 只识别网页登录 Universal Link，不能处理新增的 `bridge-login` URL scheme 路由。

# iOS 旧版应用授权链接兼容

App 继续接收 `ksuserauth://authorize?ticket=…` 与 `/app/authorize?ticket=…` Universal Link。旧版五分钟临时请求保留原生授权与 `/app/authorize-return` 浏览器回跳流程，网页不再自动发起此交接。`/app/authorize-return` 仍由网页承接，AASA 不应将该路径直接唤起 App。


验证覆盖 API `MobileAuthorizationServiceTest`、Web 授权页与扫码组件测试、iOS `CoreTests` 和 `testQRApplicationConsentWebLoginIsOffByDefaultAndCanBeSelected`。隔离 UI 预览使用 `--ui-testing --ui-test-authenticated --ui-test-application-consent --ui-test-qr-consent`。

# iOS 显示语言

原生 iOS App 支持简体中文和英语。“关于与设置 → 语言”提供跟随系统、简体中文和 English，登录前后均可更改。偏好保存在此设备的 `UserDefaults.language`，切换即时生效，保留当前输入、登录状态和已加载内容。默认读取设备的首选语言；任何中文语言变体显示简体中文，其他语言显示英语。回到前台时会重新读取系统语言。

界面文案维护在 `KsuserAuth/Localization/Localizable.xcstrings`，持久显示的提示保存文案标识与参数，展示时再翻译。API 结果码来自共享目录生成的双语表；每次 API 请求发送当前有效的 `Accept-Language`。用户资料、应用名称、历史审计原文及协议字段保持原值。带时区时间戳按当前显示语言及设备时区格式化；不带时区的日期和时间按浮动墙钟值本地化显示，不转换设备时区，非法日期保留原值。

App 图标名称、相机权限等系统界面的资源位于 `zh-Hans.lproj/InfoPlist.strings` 和 `en.lproj/InfoPlist.strings`，由 iOS 选择 App 语言；App 内语言选择无法即时覆盖系统权限弹窗或主屏幕 App 名称。App 内原生裁剪界面和动态快捷操作会跟随 App 内语言。

验证使用 `npm run test:mobile:ios`、`npm run build:mobile:ios`。已有中文 UI 回归测试显式指定中文系统语言；新增本地化测试覆盖语言解析、偏好存储、API 文案及请求语言、时间格式，以及切换时的输入与提示保留。
