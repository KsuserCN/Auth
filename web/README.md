# auth

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## 应用扫码授权

OAuth 和官方应用授权页默认使用网页登录，电脑端提供「使用扫码登录」，手机 UA（含 iPad 桌面模式）隐藏扫码按钮。扫码后在 Ksuser iOS App 核对应用、权限及当前账号；「同时登录通行证网页端」默认关闭，只有主动开启才建立通行证网页会话。第三方授权回调与原 state、适用的 nonce 和 PKCE 不变。

扫码组件复用 `/auth/mobile-authorization/qr/create`、`qr/status`、`consume` 和 `cancel`，写请求保留 CSRF；浏览器私有凭据不放进二维码。扫码页的 Ksuser iOS App 链接按当前语言选择 App Store 中国区或美国区，并在新标签页打开。API、Web 与 iOS App 需同步发布。

## OAuth 文档

- [Apple 网页登录接入说明](./APPLE_LOGIN_ADAPTATION.md)

- [QQ OAuth 适配说明](./QQ_LOGIN_LATEST_ADAPTATION.md)
- [GitHub OAuth 适配说明](./GITHUB_OAUTH_ADAPTATION.md)
- [Microsoft OAuth 适配说明](./MICROSOFT_OAUTH_ADAPTATION.md)
- [Google OAuth 适配说明](./GOOGLE_OAUTH_ADAPTATION.md)

## Console Passkey 验证

Console 在 `console.ksuser.cn` 内完成身份验证。为继续使用注册在 `auth.ksuser.cn` RP ID 下的现有 Passkey，部署时需要将 `public/.well-known/webauthn` 发布到 `https://auth.ksuser.cn/.well-known/webauthn`，并确保响应 `Content-Type` 为 `application/json`。Nginx 部署可将 [`deploy/nginx-webauthn-location.conf`](./deploy/nginx-webauthn-location.conf) 包含在 `auth.ksuser.cn` 的 HTTPS server 块中。API 端的 WebAuthn origin allowlist 也已包含 Console origin。

## Android 版本清单与安装包

公开的移动 App 介绍与下载页面为 `/downloads`，无需登录；登录页和已登录页面的侧栏均有入口。页面包含 Android 官网下载、按语言选择地区的 iOS App Store 链接、下载页二维码、功能介绍和安装问答。页面沿用 Web 的 Element Plus 明暗主题；Android 设备展示 Android App 的真实界面截图，其他设备统一展示 iOS 截图，并随站点主题切换截图。截图位于 `public/images/app-preview/`，来自各平台 App 的测试账号界面；后续更新界面时可替换同名的 `android-light.png`、`android-dark.png`、`ios-light.png`、`ios-dark.png`。Android 版本、构建号、更新说明及文件 SHA-256 实时读取 `/downloads/latest/android.json`，清单异常时提供重试入口，iOS 入口仍可使用。

日常发布推荐在仓库根目录执行 `npm run release:android`：先修改 `mobile/android/app/build.gradle.kts` 中的 `versionName` / `versionCode`，命令会提示输入签名密码，自动构建并验证正式 APK、生成网站清单、构建 Web，最终得到 `build/release.apk` 和 `build/web/`。网站清单的版本与 SHA-256 从最终 APK 提取；无需修改 Web 的 `package.json` 版本。操作步骤见 [Android 一键发布说明](../mobile/android/RELEASE.md#日常发布改两行运行一个命令)。下方保留从已有签名 APK 生成静态文件的手动命令。

Web 静态目录中的 `public/downloads/latest/android.json` 随构建复制到 `dist/downloads/latest/android.json`，根目录运行 `npm run build:web` 后也会出现在 `build/web/downloads/latest/android.json`。部署后的固定地址为：

```text
https://auth.ksuser.cn/downloads/latest/android.json
https://auth.ksuser.cn/downloads/latest/android.apk
```

在仓库根目录，从最终签名 APK 生成配套清单与安装包，再构建 Web：

```sh
npm run prepare:mobile:android:web -- \
  --apk mobile/android/app/build/outputs/apk/release/app-release.apk \
  --certificate-sha256 RELEASE_CERTIFICATE_SHA256
npm run build:web
```

将 `--apk` 换成实际的正式签名 APK 路径，`RELEASE_CERTIFICATE_SHA256` 换成签名证书 SHA-256。生成器会验证签名和网站域关联指纹，从 APK 提取版本并计算文件 SHA-256；不要只改 JSON 中的版本号。可添加 `--release-notes-file notes.txt` 和 `--force-update`。APK 是被 Git 忽略的本地产物，在其他机器或 CI 发布前需要重新生成；完整上传同一次构建的 `build/web/`，并原子切换站点目录，保证清单与安装包配套。

在 `auth.ksuser.cn` 现有的 HTTPS `server` 块中加入 [`deploy/nginx-android-downloads-location.conf`](./deploy/nginx-android-downloads-location.conf) 的内容。`root` 应放在 `server` 层级并指向部署后的 `build/web/` 内容所在目录。例如：

```nginx
# 以下内容放入现有 HTTPS server，保留已有监听、证书和其他配置。
root /var/www/auth;

location = /downloads {
    try_files /index.html =404;
    expires -1;
}

location = /downloads/ {
    return 301 /downloads$is_args$args;
}

location ^~ /downloads/ {
    try_files $uri =404;
    types {
        application/json json;
        application/vnd.android.package-archive apk;
    }
    default_type application/octet-stream;
    expires -1;
}

# 已有此 location 时修改原块，不要重复添加。
location / {
    try_files $uri $uri/ /index.html;
}
```

将示例 `/var/www/auth` 换成实际站点目录。`^~` 防止其他静态扩展名正则规则接管下载请求；`expires -1` 自动设置 `Cache-Control: no-cache`，使固定的 latest 地址重新验证缓存。清单以 `application/json` 返回，APK 以 `application/vnd.android.package-archive` 返回，文件不存在时返回 404。若现有配置在 `server` 层使用 `error_page 404 /index.html` 或 `error_page 404 =200 /index.html`，移除该 SPA 回退规则，改用上面的 `location /` 内 `try_files`，避免下载失败被改写为 HTML。[Nginx location / try_files 文档](https://nginx.org/en/docs/http/ngx_http_core_module.html#location)、[expires 文档](https://nginx.org/en/docs/http/ngx_http_headers_module.html#expires)。

`/downloads` 的精确匹配用于提供 Vue 下载页面，`/downloads/` 规范化到该页面；这两条规则需要与下载文件目录规则一起更新。由于构建产物中确实存在 `downloads/` 目录，只有通用的 `$uri/` 规则可能把页面访问重定向到静态目录并返回 403。精确页面规则避免该问题，同时保留 `/downloads/latest/*.json`、`*.apk` 的静态下载行为。

在服务器检查配置并重载，然后验证实际响应：

```sh
sudo nginx -t
sudo systemctl reload nginx
curl --fail --head https://auth.ksuser.cn/downloads
curl --fail --silent --show-error https://auth.ksuser.cn/downloads/latest/android.json | python3 -m json.tool
curl --fail --head https://auth.ksuser.cn/downloads/latest/android.apk
curl --silent --output /dev/null --write-out '%{http_code}\n' https://auth.ksuser.cn/downloads/latest/not-found.json
```

最后一条应输出 `404`。Android 默认检查地址已经是该 JSON URL；升级判断比较整数 `versionCode`，下载后的文件散列、包名、版本与签名仍由 App 验证。完整发布流程见 [`mobile/android/RELEASE.md`](../mobile/android/RELEASE.md)。

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Type-Check, Compile and Minify for Production

```sh
npm run build
```

### Run Unit Tests with [Vitest](https://vitest.dev/)

```sh
npm run test:unit
```

### Run End-to-End Tests with [Playwright](https://playwright.dev)

```sh
# Install browsers for the first run
npx playwright install

# When testing on CI, must build the project first
npm run build

# Runs the end-to-end tests
npm run test:e2e
# Runs the tests only on Chromium
npm run test:e2e -- --project=chromium
# Runs the tests of a specific file
npm run test:e2e -- tests/example.spec.ts
# Runs the tests in debug mode
npm run test:e2e -- --debug
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```

### 界面语言（i18n）

Web 支持简体中文（`zh-Hans`）与英语（`en`）。首次打开默认跟随浏览器首选语言：中文语言标签显示简体中文，其他语言显示英语。登录前可在页面右上角切换，登录后可在「使用偏好 → 界面语言」设置；支持随时恢复「跟随浏览器」。选择只保存在当前浏览器，刷新页面、退出登录后仍保留，不与账号或其他设备同步。

`src/i18n/index.ts` 管理语言与实时切换，`zh-Hans.json` / `en.json` 提供完整 UI 文案。新增文案使用稳定键与具名参数，通过 `t(key, params)` 在模板、computed 或操作执行时渲染。表单规则、菜单与选项使用 computed，避免在 setup 中保存已翻译文案。需要保留的状态提示使用 `uiMessage` 描述符并在显示时调用 `renderMessage`；客户端错误使用 `LocalizedError`，API 错误保留 `errorCode` / `params` / 状态并动态解析。业务值、用户内容、协议字段与未知历史审计原文保持原值。确认与输入弹窗使用 `localizedMessageBox` 并传入描述符，已打开的弹窗实时更新文案且保留输入。

API 结果文案来自仓库根目录 `shared/api-result-codes.json`（`message` / `messageEn`），修改后运行 `npm run generate:api-codes`。请求的 `Accept-Language`、Element Plus 组件语言、HTML `lang`、时间展示与恢复码下载说明均跟随当前有效语言。注销账户的界面确认短语会翻译，提交时仍使用后端规定的固定确认 token。

验证：`npm --prefix web run test:unit -- --run`、`npm --prefix web run type-check` 与 `npm --prefix web run build`。本地化测试检查两份资源键/参数完整性、浏览器语言与跨标签页变更、实时切换不丢失表单、API 结果回退与固定注销 token。
