# ksuser_auth_desktop

Ksuser 桌面认证中心，当前支持 `macOS` 和 `Windows`。

## 平台说明

- `macOS`：支持完整桌面体验，包括菜单栏驻留；Passkey 继续保留原来的浏览器桥接流程。
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
