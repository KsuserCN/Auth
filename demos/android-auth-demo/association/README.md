# Android Demo 域关联 / Domain association

`assetlinks.json` 对应 `cn.ksuser.demo.auth`，当前示例包含本机调试签名的公开 SHA-256 指纹。每台电脑的调试证书可能不同；以实际分发 APK 的签名为准。

`assetlinks.json` describes `cn.ksuser.demo.auth` using this machine's public debug certificate fingerprint. Debug certificates can differ between machines. Always use the certificate signing the APK being installed.

在仓库根执行以下命令，为本机调试签名重新生成文件：

From the repository root, regenerate the file from the local debug signing configuration:

```sh
npm run prepare:demo:android:links
```

输出为 `demos/android-auth-demo/build/association/debug/assetlinks.json`。将完整 JSON 部署到 `https://demos.ksuser.cn/.well-known/assetlinks.json`，直接返回 `application/json`、HTTP 200，无重定向；若域名已关联其他 App，合并各条声明。然后在开放平台相同 AppID 的 Android 平台登记包名、指纹、精确回调并验证。

The output is `demos/android-auth-demo/build/association/debug/assetlinks.json`. Serve the complete JSON at `https://demos.ksuser.cn/.well-known/assetlinks.json` with HTTP 200 and `application/json`, without redirects. Merge existing statements if other apps already use the domain. Register the package, certificate, and exact callback under the mobile AppID's Android platform, then verify it.

此任务只生成本地文件，不修改域名托管或生产应用登记。更换签名或域名后须重新生成、部署和验证；正式发布使用正式证书，不分发调试私钥。

This task only generates a local file; it does not modify hosted content or production registration. Changing signing or domains requires regeneration, deployment, and verification. Use production certificates for releases and do not distribute debug private keys.
