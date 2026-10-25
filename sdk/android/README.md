# Ksuser Android 授权 SDK / Android authorization SDK

原生 public client，最低 Android 7.0（API 24）。提供第三方 OAuth 和独立的官方 OIDC 模式。SDK 不包含 AppSecret，不获取 Ksuser App 的会话、Cookie 或账号密码。源码完成及本地产物不表示远程 Maven 或线上服务已经发布。

Native public client for Android 7.0+ (API 24), supporting third-party OAuth and separately registered official OIDC clients. It does not contain a client secret or access the Ksuser app's session. Local artifacts do not imply a hosted Maven release or enabled production service.

## 构建及安装 / Build and install

使用 JDK 21、Android SDK 36.1 和仓库内 Gradle wrapper；设置 `ANDROID_HOME`，或在此目录的未跟踪 `local.properties` 设置 `sdk.dir`。

Use JDK 21, Android SDK 36.1, and the included Gradle wrapper. Set `ANDROID_HOME`, or add `sdk.dir` to an untracked `local.properties` in this directory.

```sh
./gradlew :sdk:testDebugUnitTest :sdk:assembleRelease
./gradlew :sdk:publishReleasePublicationToLocalRepository
# Optional: install the same publication in your machine's Maven local repository.
./gradlew :sdk:publishToMavenLocal
```

- AAR: `sdk/build/outputs/aar/sdk-release.aar`
- Sources: `build/maven/cn/ksuser/ksuser-auth-android/0.1.0/ksuser-auth-android-0.1.0-sources.jar`
- Local Maven repository: `build/maven/`
- Local coordinate: `cn.ksuser:ksuser-auth-android:0.1.0`

推荐本地 Maven 安装以保留传递依赖；直接复制 AAR 时还需自行声明 POM 中的依赖。该坐标仅说明本地产物，不承诺 Maven Central 已发布。Demo 使用 Gradle composite build 引用源码，可独立打开 `demos/android-auth-demo`。

Prefer a local Maven repository so transitive dependencies are resolved. A raw AAR also requires its POM dependencies. These coordinates describe local artifacts, not a Maven Central publication. The independent `demos/android-auth-demo` consumes the SDK through a composite source build.

```kotlin
repositories {
    maven { url = uri("/absolute/path/to/auth/sdk/android/build/maven") }
    google()
    mavenCentral()
}
dependencies { implementation("cn.ksuser:ksuser-auth-android:0.1.0") }
```

## 登记和 HTTPS 回调 / Registration and HTTPS callback

第三方在开放平台创建“移动应用”，登记实际包名、分发签名证书 SHA-256 指纹和精确 HTTPS App Link 回调。在自己的回调域名部署 `/.well-known/assetlinks.json`，并在开放平台验证 Android。官方模式使用管理员独立登记的 public client；不能混用 AppID。平台资料改变会使旧 ticket 和授权码失效。

Register a mobile application with its actual package name, distribution signing certificate SHA-256 fingerprints, and precise HTTPS App Link callback. Host `/.well-known/assetlinks.json` on your callback domain and verify Android in the open platform. Official mode uses a separately registered public official client. Configuration changes invalidate old tickets and codes.

调用 App 的 Manifest 配置以下入口，并在同一个客户端实例转发 `onCreate` 和 `onNewIntent` 的 URI。不要用 Ksuser 域名作为调用 App 的回调。示例值必须替换为真实登记资料。

Declare this callback in the calling app, forwarding both `onCreate` and `onNewIntent` URLs to the same client. Use your own callback domain, not the Ksuser authorization domain. Replace all example values with actual registration data.

```xml
<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTop">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="login.example.app" android:path="/ksuser/callback" />
    </intent-filter>
</activity>
```

```json
[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"com.example.app","sha256_cert_fingerprints":["REPLACE_WITH_DISTRIBUTION_CERTIFICATE_SHA256"]}}]
```

关联文件必须为直接 HTTP 200 的 JSON，无重定向；证书要匹配实际安装包。平台验证不替代设备的域名关联验证；当前服务端验证器仅支持静态 assetlinks。

Serve JSON directly with HTTP 200 and no redirects. The certificate must match the installed build. Server registration verification does not replace device App Link verification. The current server verifier supports static assetlinks only.

SDK 在唤起 `cn.ksuser.auth` 前校验已安装包的真实 SHA-256 签名。默认 `auth.ksuser.cn` 使用仓库关联文件中的两枚可信指纹；签名不符、查询失败或旧包不处理路由时，继续同一笔网页授权。Android 28+ 支持系统验证的签名轮换历史，多签者必须全部可信。

Before opening `cn.ksuser.auth`, the SDK checks its installed signing certificates. The default `auth.ksuser.cn` origin uses the two trusted SHA-256 fingerprints in the repository's association file. A mismatch, lookup failure, or unsupported route continues the same transaction in the browser. Android 28+ accepts an OS-verified rotation lineage; concurrent signers must all be trusted.

自部署时通过 `trustedProviderCertificateSha256 = listOf("真实签名的64位SHA256")` 显式配置提供方签名指纹，接受纯十六进制或标准冒号分隔。未配置时仅使用网页。该参数是 Ksuser 提供方 App 的签名，区别于平台注册中调用 App 自己的签名；修改它后应先取消旧事务。显式配置会替代默认指纹列表。

For self-hosting, pass `trustedProviderCertificateSha256 = listOf("ACTUAL_PROVIDER_CERTIFICATE_SHA256")` using 64 hexadecimal digits or standard colon separators. Without explicit trust pins, self-hosted configurations use browser authorization only. These pins identify the Ksuser provider app, distinct from the calling app's certificates registered in the open platform. Explicit pins replace defaults and are bound to pending transactions; cancel an existing transaction before changing them.

## Kotlin

```kotlin
import cn.ksuser.auth.sdk.*

val client = KsuserAuthClient(applicationContext, KsuserAuthConfiguration(
    clientId = "your-mobile-app-id",
    mode = KsuserAuthorizationMode.OAUTH,
    redirectUri = "https://login.example.app/ksuser/callback",
))

// From an Activity coroutine. Retain the client in your application/ViewModel.
val result = client.authorize(this, listOf("profile"))
when (result) {
    is KsuserAuthorizationResult.OAuth -> securelyStore(result.tokens.accessToken)
    is KsuserAuthorizationResult.Official -> signInWithVerifiedSubject(result.subject)
}
```

`securelyStore`、`signInWithVerifiedSubject` 为业务自己的函数，不属于 SDK。OAuth 默认 scope 为 `profile`；官方默认 `openid profile`，并验证 RS256/JWKS、issuer、audience/azp、exp/iat/nbf 和 nonce 后才返回 subject。`tokens.unionId` 为可选值，未开通时不影响 OpenID 登录。

`securelyStore` and `signInWithVerifiedSubject` represent your own application logic. Default scopes are `profile` for OAuth and `openid profile` for official mode. Official results are returned only after RS256/JWKS, issuer, audience/azp, exp/iat/nbf, and nonce validation. Optional `tokens.unionId` must not be required for normal OpenID sign-in.

## Java

```java
// In a method that handles KsuserAuthException (configuration/storage errors).
KsuserAuthClient client = new KsuserAuthClient(getApplicationContext(),
    new KsuserAuthConfiguration("your-mobile-app-id", KsuserAuthorizationMode.OAUTH,
        "https://login.example.app/ksuser/callback"));
client.authorize(this, java.util.Arrays.asList("profile"),
    new KsuserAuthCallback<KsuserAuthorizationResult>() {
        @Override public void onSuccess(KsuserAuthorizationResult result) {
            // Store your application token; do not print tokens or callbacks.
        }
        @Override public void onFailure(KsuserAuthException error) {
            showErrorCode(error.getCode().name());
        }
    });
```

Java 回调在主线程调用。`showErrorCode` 是业务函数。稳定错误字段为 `code`、可选 `httpStatus`、可选 `serverErrorCode`；错误消息不会带原始响应或 URL。

Java callbacks run on the main thread. `showErrorCode` is application code. Safe diagnostics are `code`, optional `httpStatus`, and optional `serverErrorCode`; messages contain no raw responses or URLs.

## 回调、恢复和取消 / Callback, recovery, and cancellation

```kotlin
// Forward this before resuming a transaction on cold start.
intent?.data?.let { uri ->
    if (!client.handleCallback(uri)) routeOtherDeepLink(uri)
}
// Only start a resume waiter when the original authorize call is no longer waiting.
val recovered = client.resumePendingAuthorization(this)
// Continue the SAME ticket in the browser. With an active original waiter this returns null.
client.resumePendingAuthorization(this, useWebFallback = true)
client.cancel()
```

`onNewIntent` 收到回调且原 `authorize` 仍等待时，只调用 `handleCallback`；由原任务接收结果，不再启动 `resume(false)`，否则返回 `AUTHORIZATION_IN_PROGRESS`。业务应在长期存活的模型中跟踪原任务；只有冷启动或原任务已经结束时才建立恢复任务。

When `onNewIntent` receives a callback while `authorize` is still waiting, call only `handleCallback`. The original task receives the result. Calling `resume(false)` concurrently returns `AUTHORIZATION_IN_PROGRESS`. Track the original task in a retained model, and create a resume waiter only after cold start or after that task has ended.

SDK 在跳转前将 verifier、state、nonce、ticket、取消密钥和五分钟有效期保存于调用 App 自己的 Keystore AES-GCM 加密、不参与备份的文件中。令牌暂存仅用于官方 JWKS 故障恢复，成功返回后清理。每个调用 App 只有一笔未完成交易；SDK 强制在应用默认进程初始化，其他进程返回 `INVALID_CONFIGURATION`。不同 AppID/环境不能覆盖旧交易，先恢复、取消或等待过期。

Before launching, the SDK persists verifier, state, nonce, ticket, cancellation secret, and expiry using app-private Android Keystore AES-GCM storage excluded from backup. Tokens are temporarily retained for official JWKS recovery and cleared on completion. All instances share a single pending transaction. Initialization is enforced in the application's default process; other processes receive `INVALID_CONFIGURATION`. Resume, cancel, or expire an existing transaction before changing configuration.

返回前台不自动视为取消。网络错误、429、5xx 及启动失败保留尚未到期的交易；服务端确认授权码失效则重新授权。若 token 响应丢失，不能保证 code 能再次兑换。官方 Token 已取得时，JWKS 暂错只重试验证，不重复兑码。取消立即结束本地等待，尽力在三秒内取消服务端；已完成授权需在授权管理撤销。

Returning to the foreground is not cancellation. Network failures, HTTP 429/5xx, and launch failures preserve unexpired transactions. Restart authorization if the server confirms the code is no longer usable; a lost token response cannot guarantee replay. Saved official tokens allow JWKS retries without another exchange. Cancellation ends local waiting and makes a best-effort server request bounded to three seconds. Revoke completed grants through authorization management.

## 验收 / Acceptance

单元测试覆盖协议、恶意 URL、PKCE、ID Token 验证、并发、重复回调、取消、冷启动、过期与网络/JWKS 恢复。构建通过不证明域关联或线上开关生效。真实试点还须验证两 App、正式签名、已安装/未安装/旧版 Ksuser、未登录/MFA 后继续、同意/拒绝、进程回收及浏览器继续。

Unit tests cover protocol validation, malicious URLs, PKCE, ID tokens, concurrency, duplicate callbacks, cancellation, cold starts, expiry, and network/JWKS recovery. A successful build does not prove App Link verification or production availability. Complete real two-app acceptance with registered clients and signed builds, including installed/missing/old Ksuser, login/MFA continuation, approval/denial, process death, and browser fallback.

日志和界面不得显示 code、verifier、ticket、cancelSecret、完整回调或 Token。SDK 对象的 `toString` 已脱敏，但业务仍应只显示错误码和必要身份信息。

Never display or log codes, verifiers, tickets, cancellation secrets, full callbacks, or tokens. SDK token/result `toString` methods are redacted; application diagnostics should still use only safe error codes and necessary identity information.
