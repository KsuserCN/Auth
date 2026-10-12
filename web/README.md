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

## OAuth 文档

- [Apple 网页登录接入说明](./APPLE_LOGIN_ADAPTATION.md)

- [QQ OAuth 适配说明](./QQ_LOGIN_LATEST_ADAPTATION.md)
- [GitHub OAuth 适配说明](./GITHUB_OAUTH_ADAPTATION.md)
- [Microsoft OAuth 适配说明](./MICROSOFT_OAUTH_ADAPTATION.md)
- [Google OAuth 适配说明](./GOOGLE_OAUTH_ADAPTATION.md)

## Console Passkey 验证

Console 在 `console.ksuser.cn` 内完成身份验证。为继续使用注册在 `auth.ksuser.cn` RP ID 下的现有 Passkey，部署时需要将 `public/.well-known/webauthn` 发布到 `https://auth.ksuser.cn/.well-known/webauthn`，并确保响应 `Content-Type` 为 `application/json`。Nginx 部署可将 [`deploy/nginx-webauthn-location.conf`](./deploy/nginx-webauthn-location.conf) 包含在 `auth.ksuser.cn` 的 HTTPS server 块中。API 端的 WebAuthn origin allowlist 也已包含 Console origin。

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
