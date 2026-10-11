# ksuser_auth_desktop

Ksuser 桌面认证中心，当前支持 `macOS` 和 `Windows`。

## 平台说明

- `macOS`：支持完整桌面体验，包括菜单栏驻留；Passkey 登录、MFA、敏感验证和新增登记改为调用系统原生 Passkey。
- `Windows`：支持账号登录、二维码登录、网页登录回流、工作台管理；Passkey 登录、MFA 和敏感验证改为调用系统原生 Passkey。
- “手机扫码登录” 使用系统本地认证保护：
  `macOS` 继续走原来的系统本地认证，`Windows` 走系统身份验证（Windows Hello / PIN / 其他可用系统解锁方式）。
- `Windows` 仍不启用 macOS 专属能力：菜单栏驻留。

## 本地运行

```bash
flutter pub get
flutter run -d macos
```

```bash
flutter pub get
flutter run -d windows
```

## 发行构建

```bash
flutter build macos --release
```

```bash
flutter build windows --release
```

## GitHub Actions

仓库内置工作流 [`.github/workflows/build-desktop.yml`](.github/workflows/build-desktop.yml)，会在 `macOS` 和 `Windows` runner 上分别构建桌面产物，并上传构建结果：

- `ksuser-auth-desktop-macos`
- `ksuser-auth-desktop-windows-x64`

可通过 `Actions -> Build Desktop` 手动触发，也会在推送到 `main` 或发起 `pull_request` 时执行。

## 界面语言

桌面端支持简体中文和英语，首次启动跟随设备首选语言；中文的所有地区/字形变体使用简体中文，其余语言默认使用英语。设置 → 语言可选择跟随系统、简体中文或 English。选择保存在本机应用支持目录的 `language_preference.json`，退出账号不会清除它；恢复默认会重新跟随系统。

语言切换即时更新界面、已打开的弹窗、API 结果文案以及原生菜单/托盘，保留登录状态与输入内容。请求会携带当前有效语言的 `Accept-Language`。系统身份验证弹窗仍由操作系统提供。

界面文案维护于 `lib/l10n/app_en.arb` 与中文 ARB 文件；修改后执行 `flutter gen-l10n`。API 详细结果文案通过仓库共享目录和 `npm run generate:api-codes` 维护。执行 `flutter analyze` 与 `flutter test` 验证；英文布局测试覆盖桌面最小窗口宽度。

Windows 原生托盘与 Passkey 桥接同样支持语言切换；Windows C++ 构建仍需在 Windows runner 或 Windows 开发设备验证。
