# Desktop Passkey Bridge

## 背景

当前 `ksuser_auth_desktop` 仍处于本地开发阶段，约束条件有两个：

1. 桌面端 API 只能连接本机 `localhost`。
2. 当前没有 Apple Developer 账号，也没有可用的 Team ID / Associated Domains / AASA。

在这个前提下，macOS 原生 `AuthenticationServices` 无法完成真正的 App Passkey 验证；系统会直接报错：调用进程没有有效的 application identifier。

因此这次改动采用过渡方案：

- WebAuthn 仍然在浏览器中完成；
- Flutter 桌面端只负责发起桥接、接收结果、继续本地业务流程；
- 等后续拿到 Apple Developer 账号后，再切回原生 App Passkey。

## 这次改动了什么

### 1. 新增浏览器桥接页

新增页面：

- `auth/src/views/DesktopPasskeyBridgeView.vue`

新增路由：

- `auth/src/router/index.ts`
  - 新增 `/desktop/passkey-bridge`

这个桥接页接收桌面端传入的参数，然后在浏览器里直接调用现有 WebAuthn 流程。

支持 4 种模式：

- `login`：Passkey 登录
- `mfa`：Passkey 完成登录 MFA
- `sensitive`：Passkey 完成敏感操作验证
- `register`：新增 Passkey

### 2. request.ts 允许桥接页动态切换 API 地址

修改文件：

- `auth/src/utils/request.ts`

新增：

- `getRequestBaseUrl()`
- `setRequestBaseUrl()`

原因：桥接页不能假设 API 一定是线上地址。Flutter 调试时通常会把 API 指到 `http://localhost:8000`，桥接页必须跟着桌面端当前配置走。

### 3. Flutter 桌面端新增本地回调服务器

桌面端新增了一个浏览器桥接器：

- `ksuser_auth_desktop/lib/main.dart`
  - `BrowserPasskeyBridgeMode`
  - `BrowserPasskeyBridge`
  - `BrowserPasskeyBridgeResponse`

核心机制：

1. Flutter 在本机 `127.0.0.1` 随机端口启动临时 HTTP Server。
2. Flutter 生成一次性 `state`。
3. Flutter 打开浏览器访问：
   - `${FLUTTER_PASSKEY_ORIGIN}/desktop/passkey-bridge?...`
4. 浏览器完成 WebAuthn 后，桥接页把结果 `POST` 回本地回调地址。
5. Flutter 收到结果后关闭临时回调服务，并继续业务逻辑。

### 4. Flutter 里真正接通了这些能力

修改文件：

- `ksuser_auth_desktop/lib/main.dart`

已改为浏览器桥接的场景：

- 登录页 `Passkey 登录`
- 登录后 `Passkey MFA`
- 敏感操作验证里的 `Passkey`
- 安全页里的 `新增 Passkey`

其中“新增 Passkey”默认会直接生成设备名：

- `Ksuser Desktop (<hostname>)`

这样用户不需要再手工输入名称。

## 桥接参数说明

浏览器桥接页使用的参数如下。

### Query 参数

- `mode`
  - `login | mfa | sensitive | register`
- `callback`
  - Flutter 本地回调地址，例如 `http://127.0.0.1:54321/desktop-passkey-callback`
- `state`
  - Flutter 生成的一次性随机值，用于防止串请求
- `apiBaseUrl`
  - 当前桌面端实际使用的后端 API 地址
- `mfaChallengeId`
  - 仅 `mfa` 模式需要
- `passkeyName`
  - 仅 `register` 模式可选

### Hash 参数

- `accessToken`
  - 仅 `sensitive` / `register` 模式需要
  - 放在 URL hash 里，而不是 query 里，目的是避免被服务端日志直接记录

桥接页会在加载后立刻：

1. 读取 hash 中的 `accessToken`
2. 写入 `sessionStorage`
3. 立刻通过 `history.replaceState` 去掉 hash

执行结束后：

- 如果桥接页临时注入过 `accessToken`，会恢复原来的浏览器 `sessionStorage.accessToken`
- 不会永久覆盖用户原本的网页登录状态

## 回调数据格式

浏览器桥接页会向桌面端本地回调地址发送 JSON：

```json
{
  "status": "success",
  "state": "random-state",
  "message": "Passkey 登录成功",
  "accessToken": "...",
  "verified": true,
  "registered": true
}
```

实际字段按模式不同而变化：

- `login` / `mfa`
  - 返回 `accessToken`
- `sensitive`
  - 返回 `verified: true`
- `register`
  - 返回 `registered: true`

失败时：

```json
{
  "status": "error",
  "state": "random-state",
  "message": "错误信息"
}
```

Flutter 端会严格校验 `state`，不匹配直接拒绝。

## 桥接页内部复用了哪些现有 API

### 登录

- `getPasskeyAuthenticationOptions()`
- `verifyPasskeyAuthentication()`

### 登录 MFA

- `verifyPasskeyForLoginMFA()`
- 如果需要 TOTP 兜底，则调用 `verifyTOTPForLogin()`

### 敏感验证

- `getPasskeySensitiveVerificationOptions()`
- `verifyPasskeySensitiveOperation()`

### 新增 Passkey

- `getPasskeyRegistrationOptions()`
- `verifyPasskeyRegistration()`

也就是说，这次没有改动后端协议，只是把桌面端原本失败的原生调用，切换成浏览器里的同一套 WebAuthn 流程。

## 安全边界和当前限制

这是开发阶段可行的过渡方案，不是最终原生方案。当前边界如下：

1. 回调地址只接受 loopback：
   - `127.0.0.1`
   - `localhost`
   - `::1`
2. Flutter 会校验 `state`，防止浏览器结果串回错误的桌面实例。
3. `accessToken` 只在 `hash` 里传递，不放在 query。
4. 桥接页执行完成后会恢复原浏览器的 `sessionStorage.accessToken`。

但它依然有几个明确限制：

1. 用户会看到浏览器被拉起；这是方案 A 的既定取舍。
2. `localhost` 开发环境下，Passkey 依赖浏览器本身，而不是原生 App。
3. `sensitive` / `register` 模式仍然依赖桌面端当前持有的 `accessToken`，只是借浏览器执行 WebAuthn。

## 后续拿到 Apple Developer 后怎么切回原生 App Passkey

后续具备 Apple Developer 条件后，建议按下面步骤回切。

### 第一步：补齐 App 身份

需要先把 macOS 工程补成真实应用身份：

- 正式 Bundle ID
- 正式 Team ID
- 使用 Apple 签名，不再是 adhoc

需要修改的位置通常包括：

- `ksuser_auth_desktop/macos/Runner/Configs/AppInfo.xcconfig`
- Xcode Signing 配置

### 第二步：打开 Associated Domains

需要在 macOS entitlements 中增加：

- `com.apple.developer.associated-domains`

并添加：

- `webcredentials:你的域名`

通常会改：

- `ksuser_auth_desktop/macos/Runner/DebugProfile.entitlements`
- `ksuser_auth_desktop/macos/Runner/Release.entitlements`

### 第三步：部署 AASA 文件

需要在 Passkey 域名上提供：

- `https://<domain>/.well-known/apple-app-site-association`

其中要包含你的 App ID：

- `<TeamID>.<BundleID>`

### 第四步：切换 Flutter 回原生调用

当前桌面端的浏览器桥接入口在：

- `BrowserPasskeyBridge.start(...)`

切回原生后，可以逐步把下面这些入口改回 `PasskeyPlatform`：

- 登录页 `loginWithPasskey()`
- `completePasskeyMfa()`
- `verifySensitiveOperationWithPasskeyInBrowser()`
- `registerPasskeyInBrowser()`

建议做法不是一次性删桥接，而是分两步：

1. 保留浏览器桥接作为 fallback
2. 原生通了以后，再删桥接页和文档

### 第五步：删掉 bridge 页和路由

原生完全稳定后，可以删除：

- `auth/src/views/DesktopPasskeyBridgeView.vue`
- `auth/src/router/index.ts` 中的 `/desktop/passkey-bridge`
- `auth/src/utils/request.ts` 里为 bridge 增加的动态 baseURL 能力（如果其他功能不再需要）

## 建议的维护策略

在没有 Apple Developer 之前，继续保留这套 bridge，原因很直接：

- 它能在 `localhost` 上工作；
- 它不要求 Team ID / Associated Domains；
- 它可以覆盖登录、MFA、敏感验证、Passkey 新增这四类核心流程。

等 Apple Developer 条件具备后，再把它降级成 fallback，最后再彻底移除。这样风险最小。
