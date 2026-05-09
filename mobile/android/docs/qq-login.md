# Android QQ 登录配置

Android 端使用 QQ 互联官方 SDK `3.5.19`。SDK 的 JAR 放在：

```text
mobile/android/app/libs/open_sdk_3.5.19_r9483ffc7_lite.jar
```

## 环境变量

本地开发构建读取：

```text
mobile/android/.env.development
```

发布构建读取：

```text
mobile/android/.env.production
```

在对应文件中加入：

```env
QQ_MOBILE_APP_ID=你的QQ互联移动应用AppId
```

`QQ_MOBILE_APP_ID` 会写入 Android `BuildConfig` 与 Manifest 的 `tencent{AppId}` 回调 scheme。
不要把 `QQ_MOBILE_APP_SECRET` 放进 Android `.env`，客户端 APK 可以被反编译，Secret 应只放后端。

API 端同时需要配置：

```env
QQ_MOBILE_APP_ID=你的QQ互联移动应用AppId
QQ_MOBILE_APP_SECRET=你的QQ互联移动应用AppKey或Secret
```

如果移动应用和 Web QQ OAuth 共用同一个应用，API 默认会回退到 `QQ_OAUTH_APP_ID` / `QQ_OAUTH_APP_KEY`。
