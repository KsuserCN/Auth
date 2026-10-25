# Android 授权 Demo / Android authorization demo

与 iOS Demo 对齐的 Android 7.0+ 原生应用间授权示例，包名 `cn.ksuser.demo.auth`。界面分为应用配置、授权操作、授权结果；支持 OAuth／官方 OIDC、唤起 Ksuser App、同一 ticket 网页继续、冷启动恢复和取消。只显示用户标识、可选 UnionID 与有效期，不显示 Token，也不需要自己的业务后端或 AppSecret。

A native Android 7.0+ app-to-app authorization example matching the iOS Demo. Configuration, authorization controls, and results are grouped separately. It supports OAuth/official OIDC, Ksuser app launch, same-ticket browser continuation, cold-start recovery, and cancellation. Only identity, optional UnionID, and expiration are displayed. No business backend or AppSecret is needed.

## 配置与运行 / Configuration and build

在仓库根执行 `npm run dev:demo:android` 用 Android Studio 打开工程，或执行 `npm run build:demo:android` 构建 APK。构建使用 JDK 21、Android SDK 36.1。统一配置文件为 [DemoConfiguration.properties](DemoConfiguration.properties)，所有值均为公开客户端配置；也可通过下表的 Gradle `-P` 参数覆盖。

默认 OAuth AppID 和 `https://demos.ksuser.cn/ksuser/callback` 与 iOS Demo 共用；Android 还须在同一移动应用下登记自己的包名与实际 APK 签名，部署并验证 `assetlinks.json`。官方 Client ID 仍为占位值，未填写时官方授权按钮禁用。

Run `npm run dev:demo:android` from the repository root to open Android Studio, or `npm run build:demo:android` to build the APK. Use JDK 21 and Android SDK 36.1. Edit [DemoConfiguration.properties](DemoConfiguration.properties), or override its public values with Gradle `-P` properties.

The default OAuth AppID and `https://demos.ksuser.cn/ksuser/callback` are shared with the iOS Demo. Android still needs its own package/signing registration and a deployed, verified `assetlinks.json`. The official client ID remains a placeholder; official authorization is disabled until configured.

```sh
cd demos/android-auth-demo
./gradlew :app:assembleDebug \
  -PKSUSER_OAUTH_CLIENT_ID=YOUR_MOBILE_APP_ID \
  -PKSUSER_OFFICIAL_CLIENT_ID=YOUR_OFFICIAL_PUBLIC_CLIENT_ID \
  -PKSUSER_REDIRECT_URI=https://login.example.app/ksuser/callback
```

| Gradle property | Default |
| --- | --- |
| `KSUSER_OAUTH_CLIENT_ID` | `ksapp_GGHAd6l3Tr-g7NYvaSgrPxUp` |
| `KSUSER_OFFICIAL_CLIENT_ID` | `REPLACE_OFFICIAL_CLIENT_ID` |
| `KSUSER_REDIRECT_URI` | `https://demos.ksuser.cn/ksuser/callback` |
| `KSUSER_API_BASE_URL` | `https://api.ksuser.cn` |
| `KSUSER_WEB_BASE_URL` | `https://auth.ksuser.cn` |
| `KSUSER_OFFICIAL_ISSUER` | `https://api.ksuser.cn/official` |
| `KSUSER_PROVIDER_CERTIFICATES` | 空；生产域使用 SDK 内置可信提供方指纹 / empty; the production host uses built-in provider pins |
| `KSUSER_SDK_REPOSITORY` | 未设置时使用源码 composite build / source composite build when omitted |

验证已打包 AAR 的接入方式：先在仓库根运行 `npm run build:sdk:android`，再在 Demo 目录运行 `./gradlew :app:assembleDebug -PKSUSER_SDK_REPOSITORY=../../sdk/android/build/maven`。CI 使用此方式验证本地 Maven 元数据与传递依赖。

To verify the packaged AAR, run `npm run build:sdk:android` at the repository root, then build the Demo with `./gradlew :app:assembleDebug -PKSUSER_SDK_REPOSITORY=../../sdk/android/build/maven`. CI uses this path to check publication metadata and transitive dependencies.

回调属性同时生成 Manifest 中的精确 host/path。使用下方命令生成实际调试 APK 的 [assetlinks 文件](association/README.md)，部署到回调域名 `/.well-known/assetlinks.json`，并在开放平台完成 Android 平台登记和验证。官方 client 使用独立管理员登记，不能复用第三方 AppID。

The callback property generates the Manifest host/path. Generate [association JSON](association/README.md) using the installed debug APK's signing certificate, publish it at your callback host's `/.well-known/assetlinks.json`, and register and verify Android in the open platform. Official clients require separate administrator registration.

SDK 唤起前会验证 Ksuser 提供方的签名。自部署可用 `KSUSER_PROVIDER_CERTIFICATES` 配置可信提供方证书 SHA-256，多个指纹用逗号分隔；没有可信匹配时使用同一 ticket 网页授权。此项填写提供方 APK 的证书，回调 assetlinks 填写 Demo 自己的证书。

The SDK verifies the Ksuser provider's signature before launching it. Self-hosted builds can set `KSUSER_PROVIDER_CERTIFICATES` to comma-separated trusted provider SHA-256 fingerprints. Without a trusted match the same ticket continues in the browser. These pins describe the provider APK; callback assetlinks describe the Demo's own signing certificate.

```sh
./gradlew :app:generateDebugAssetLinks
./gradlew :app:signingReport
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Android 12+
adb shell pm verify-app-links --re-verify cn.ksuser.demo.auth
adb shell pm get-app-links cn.ksuser.demo.auth
```

生成文件位于 `build/association/debug/assetlinks.json`，应以 HTTP 200、`application/json`、无重定向托管；命令不会自动上传文件或修改开放平台。

The generated file is `build/association/debug/assetlinks.json`; serve it directly with HTTP 200 and `application/json`. The command does not upload it or modify platform registration.

API、Web 和新版 Ksuser App 应先部署；由服务端开关及 allowlist 放行真实 client。未安装／旧版 Ksuser 时可网页继续。切换模式会先取消原请求；模式本身保存用于恢复，Verifier 等秘密只由 SDK 加密保存。`DemoLoginModel` 保留原等待任务，Activity 冷热启动先转发回调，再在没有等待者时恢复。

Deploy matching API, Web, and Ksuser app versions and enable the real client through the server flag/allowlist. Browser continuation handles absent or older Ksuser apps. Changing mode cancels the original request. Only the selected mode is saved by the Demo; the SDK encrypts transaction secrets. `DemoLoginModel` retains an active waiter; Activity launches forward callbacks before resuming when no waiter exists.

## 操作与测试 / Controls and tests

- “使用 Ksuser 账号授权”生成 PKCE/state 并打开提供方 App；不能唤起时使用网页备用。
- 等待期间禁用重复授权及模式切换；可继续同一笔网页授权或取消。切换模式先结束旧事务。取消只结束当前等待并尽力取消服务端请求；已完成的授权需在授权管理中撤销。
- 冷／热回调只交给原等待者；进程重启后通过保存的模式恢复配置，再接收回调与 resume。
- 结果展示 OpenID／官方已验证 subject、可选 UnionID 和有效期；拒绝、过期、网络故障和配置错误分别提示。

Authorize starts PKCE/state and opens Ksuser or browser fallback. While waiting, duplicate authorization and mode changes are disabled; browser continuation and cancellation remain available. A callback is delivered to the existing waiter, or resumes with the saved mode after process death. Results show identity and expiry; denial, expiry, network failures, and configuration errors have distinct messages. Cancellation ends local waiting and attempts server cancellation; completed grants require separate revocation.

```sh
# From the repository root
npm run test:demo:android
npm run build:demo:android
npm run prepare:demo:android:links
```

实际双 App 验收仍须真实回调域名、登记 client 和正确签名；构建通过不代表线上能力已开放。参阅 [SDK 文档](../../sdk/android/README.md) 和 [原生协议](../../api/docs/native-app-authorization.md)。

Live end-to-end acceptance requires your registered client, real callback domain, and actual signing certificates. Build success does not announce hosted access. See the [SDK guide](../../sdk/android/README.md) and [protocol](../../api/docs/native-app-authorization.md).
