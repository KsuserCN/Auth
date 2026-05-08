# Android 自动更新发布说明

App 会读取一个 HTTPS JSON 清单来判断是否有新版。默认地址由 `PASSKEY_ORIGIN_HINT`
推导为：

```text
https://auth.ksuser.cn/downloads/latest/android.json
```

如果生产环境需要换地址，在 `mobile/android/.env.production` 中设置：

```env
UPDATE_MANIFEST_URL=https://auth.ksuser.cn/downloads/latest/android.json
```

## 云服务器目录

建议把 release 包和版本清单放在站点静态目录：

```text
/var/www/auth.ksuser.cn/downloads/latest/
  android.json
  ksuser-auth-1.0.1.apk
  ksuser-auth-1.0.2.apk
```

Nginx 需要能直接访问这些文件：

```text
https://auth.ksuser.cn/downloads/latest/android.json
https://auth.ksuser.cn/downloads/latest/ksuser-auth-1.0.2.apk
```

## latest.json 格式

```json
{
  "versionCode": 2,
  "versionName": "1.0.1",
  "apkUrl": "https://auth.ksuser.cn/downloads/latest/ksuser-auth-1.0.1.apk",
  "sha256": "把 APK 的 sha256sum 放在这里",
  "releaseNotes": "修复已知问题，优化移动端登录体验。",
  "forceUpdate": false
}
```

只有当 `versionCode` 大于 App 当前版本时，客户端才会提示更新。

## 发布新版步骤

1. 在 `mobile/android/app/build.gradle.kts` 中递增 `versionCode`，并更新 `versionName`。
2. 构建 release 包：`npm run build:mobile:android:release`。
3. 将生成的 APK 上传到 `/var/www/auth.ksuser.cn/downloads/android/`。
4. 计算 APK 摘要：`sha256sum ksuser-auth-1.0.1.apk`。
5. 修改同目录的 `android.json`，把 `versionCode`、`versionName`、`apkUrl`、`sha256` 和 `releaseNotes` 改成最新版本。
6. 打开 `https://auth.ksuser.cn/downloads/latest/android.json` 确认浏览器能访问，App 启动和“关于应用”页面会检查更新。

Android 不允许普通应用静默安装 APK。当前实现会自动检查新版，并通过系统下载器下载 APK；下载完成后用户需要从通知栏或下载目录打开安装包完成安装。
