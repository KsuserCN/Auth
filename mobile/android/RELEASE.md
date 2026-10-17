# Android APK 更新与发布

在仓库根目录执行以下命令。流程生成本地构建与发布文件；上传、部署和上线验证需要单独执行。

## 本次状态（2026-10-17）

- Debug APK 与 unsigned Release APK 已构建通过；未提供正式签名密钥，未生成可发布的正式签名安装包。
- 发布脚本 4 项自动测试通过，使用模拟 SDK 工具验证脚本合同；Debug/Release 单元测试各 69 项通过；未用正式 APK 完成签名校验或覆盖升级。
- 中英协议页面及域关联 JSON 可访问。`https://auth.ksuser.cn/downloads/latest/android.json` 返回 HTTP 200，但正文为 SPA HTML；真实清单与新版正式 APK 尚未部署。
- 签名 CI 作业已配置，但作业配置不表示已实际运行并发布。本次没有上传或部署操作。

以下步骤全部完成后，才能把本轮标记为“正式发布验收通过”。

## 签名构建

需要 Java 21、Android 36.1 SDK 和 36.0.0 Build Tools。通过受保护的环境提供 `ANDROID_KEYSTORE_PATH`（绝对路径）、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`，然后执行：

```sh
npm run build:mobile:android:release
```

密钥及密码不得提交到仓库。发布前确认 `versionCode` 高于线上及待覆盖安装版本；当前源码为 13，不代表一定满足线上升级条件。

未配置签名时生成的 unsigned APK 仅供构建检查，不能进入更新发布流程。正式签名证书须与 `web/public/.well-known/assetlinks.json` 中 `cn.ksuser.auth` 的指纹一致；脚本还会拒绝 Android Debug 证书和 debuggable APK。

## 生成更新目录

只接受最终签名 APK；签名完成后不要再修改或重新压缩该文件。

```sh
node scripts/generate-android-release.mjs \
  --apk mobile/android/app/build/outputs/apk/release/app-release.apk \
  --certificate-sha256 RELEASE_CERTIFICATE_SHA256 \
  --base-url https://auth.ksuser.cn \
  --output build/android-release
```

`RELEASE_CERTIFICATE_SHA256` 替换为正式签名证书 SHA-256，可包含冒号，不能使用 APK 文件散列。脚本先将此指纹与网站域关联文件比对，再校验 APK 的实际签名，两个检查都通过才生成输出。域关联文件默认路径为 `web/public/.well-known/assetlinks.json`，可通过 `--asset-links` 指定其他经审核的文件。

设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 可自动寻找 Build Tools，也可使用 `--apksigner`、`--aapt` 指定工具路径。`--version-code`、`--version-name` 可断言最终 APK 版本；`--release-notes-file` 读取 UTF-8 说明，`--force-update` 启用强制更新。

输出目录：

```text
build/android-release/downloads/latest/android.apk
build/android-release/downloads/latest/android.json
```

脚本对输入和复制后的 APK 都执行 `apksigner verify --min-sdk-version 24 --verbose --print-certs`；包名和版本通过 `aapt dump badging` 从实际 APK 获取。清单字段保留 `versionCode`、`versionName`、`apkUrl`、`sha256`、`releaseNotes`、`forceUpdate`。相同输入产生相同清单；未签名、证书不匹配或版本断言失败不会覆盖已有发布文件。

部署时原子切换整个 `downloads/latest` 目录，以免新 APK 和旧清单混用。通过 HTTPS 提供文件，清单设置 `Cache-Control: no-cache`。上线前检查实际下载地址、文件 SHA-256 与证书，正式包无法覆盖使用其他签名的安装版本。

## App 更新行为

App 通过独立的公共 HTTP 客户端读取 HTTPS 清单，强制要求合法 SHA-256。下载由系统 DownloadManager 执行，原始文件保存在 App 专属外部目录，并且不提供未经验证的安装操作。

下载完成后，文件复制到私有 `files/updates`，检查散列、包名、准确版本和与当前 App 兼容的签名。API 24–27 要求相同签名集合；API 28 起允许系统已验证的签名轮换历史。损坏、版本或签名不匹配时删除文件并阻止安装。

App 在前台时，验证完成后继续打开系统安装界面；在后台时保留状态，返回 App 后继续。Android 8 起的未知来源安装权限由系统设置页授予，返回后再次验证文件。取消或拒绝后保留已验证文件，通过应用更新中的“继续安装已验证更新”重试；重新点击相同更新的下载操作也会复用该文件。系统安装界面始终要求用户确认，不静默安装、不在后台启动安装器。成功升级后清理旧文件。

进程结束不会绕过验证，安装状态和 APK 文件均排除在系统备份之外。系统下载进度由 DownloadManager 管理。

## 自动化检查

```sh
node --test scripts/generate-android-release.test.mjs
cd mobile/android
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
# Release 变体测试（本轮已运行，正式签名发布前再次执行）
./gradlew :app:testReleaseUnitTest
```

Node 测试模拟 SDK 工具，覆盖版本提取、网站指纹校验、签名拒绝、确定性输出以及失败时保留旧文件。Kotlin 单测覆盖 URL、散列、包名、版本和签名策略；不替代实际 APK 的 `apksigner` 验证。

## 发布验收清单

- [ ] 在受保护的环境配置正式密钥；构建签名 Release，并执行 Release 变体单元测试。记录版本、包名、证书 SHA-256、APK SHA-256。
- [ ] 用最终 APK 实际运行 `apksigner verify --min-sdk-version 24 --verbose --print-certs`，核对正式证书、包名和版本；不能用测试脚本的模拟工具结果替代。
- [ ] 用正式证书重新核对网站域关联文件并在设备验证 HTTPS 链接和 Passkey 权限；文件可访问不能代替关联成功。
- [ ] 对最终签名 APK 运行生成器。确认 `android.json` 的版本与 APK 一致，`apkUrl` 为目标 HTTPS 地址，`sha256` 为最终 APK 的文件散列；检查发布说明及是否确实需要强制更新。
- [ ] 先部署配套 API 与 Web 回退路由，再部署域关联和整个 `downloads/latest` 目录；更新路径须排除 SPA 的 HTML 回退规则。
- [ ] 检查线上清单和 APK 的 HTTP 状态、内容类型和实际正文。清单必须为可解析 JSON，不能仅凭 HTTP 200 判定成功：

  ```sh
  curl --fail --silent --show-error https://auth.ksuser.cn/downloads/latest/android.json | python3 -m json.tool
  ```

- [ ] 从线上地址获取实际 APK，重新核对文件散列、包名、版本和签名，确认与部署产物一致；在应用内执行检查更新，不能再出现 HTML 解析错误。
- [ ] 在 Android 24/25 与目标 API 36 设备，使用同一正式签名的旧版和新版执行真实升级。分别测试前台下载、后台下载后返回、进程结束后恢复、安装权限拒绝后重试、取消安装后重试；升级后账号与设置保留，旧更新文件清理。
- [ ] 在受控测试地址提供损坏文件、错误哈希/包名/版本/签名，确认每种情况阻止安装并可重新下载；不要替换正式线上清单进行负向测试。
- [ ] 记录设备、原版本、新版本、证书、部署地址、执行结果和脱敏截图。签名或部署条件不足时保持“待验收”，不要把 unsigned Release 或 Debug 构建作为正式发布交付。

功能与设备矩阵见 [ACCEPTANCE.md](ACCEPTANCE.md)。

官方参考：[apksigner](https://developer.android.com/tools/apksigner)、[SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo)、[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)。
