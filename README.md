# Auth

`Auth` 是一个多项目身份认证仓库，覆盖后端 API、Web 认证门户、桌面端、Android 移动端以及本地联调 Demo。

核心目标：

- 统一账号认证与会话管理
- 支持密码、邮箱验证码、Passkey、TOTP MFA 等认证能力
- 支持 OAuth 登录/绑定与内部 SSO / OIDC
- 为 Web、桌面端、移动端提供统一认证基础设施

## 仓库结构

| 目录 | 作用 | 主要技术 |
| --- | --- | --- |
| `api/` | 认证后端服务，默认端口 `8000` | Spring Boot, Spring Security, JPA, MySQL, Redis |
| `web/` | Web 认证中心与 OAuth/SSO 授权页，开发环境默认 `5173` | Vue 3, Vite, TypeScript, Element Plus |
| `desktop/` | 桌面客户端，当前支持 `macOS` / `Windows` | Flutter |
| `mobile/android/` | Android 客户端 | Kotlin, Jetpack Compose, Retrofit |
| `demos/` | 本地 OAuth / OIDC / SSPU 接入演示 | Python, HTML/JS |
| `.github/workflows/` | 分模块 CI 构建工作流 | GitHub Actions |

## 功能概览

- 注册 / 登录（密码、邮箱验证码）
- Passkey（WebAuthn）登录与敏感操作验证
- TOTP 双因素认证与恢复码
- 多设备会话管理、刷新与撤销
- OAuth 登录 / 绑定（GitHub / Google / Microsoft / QQ）
- 内部 SSO 与 OIDC Discovery
- 风控、限流、敏感操作日志

## 技术栈

- 后端：Java 21, Spring Boot, Spring Security, JPA, MySQL, Redis
- 前端：Vue 3, Vite, TypeScript, Pinia, Element Plus
- 桌面端：Flutter
- 移动端：Kotlin, Jetpack Compose
- Demo：Python 3

## 环境要求

按你要运行的模块准备环境：

- 通用：`git`
- API：`JDK 21`、MySQL、Redis
- Web：`Node.js 20+`、`npm`
- Desktop：Flutter Stable，对应桌面平台工具链
- Mobile Android：Android SDK、JDK 17/21、Gradle
- Demos：`Python 3`

## 快速开始

### 1. 准备后端环境变量

API 从 `api/` 目录读取 `.env.<KSUSER_ENV>`：

```bash
cp api/.env.example api/.env.development
```

至少需要补全这些配置：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `JWT_SECRET`

### 2. 安装 Web 依赖

```bash
cd web
npm install
```

### 3. 启动核心开发环境

仓库根目录提供了统一脚本，会同时启动 API 和 Web：

```bash
npm run dev:core
```

这个命令等价于：

- 在 `api/` 中以 `KSUSER_ENV=development` 启动 Spring Boot
- 在 `web/` 中启动 Vite 开发服务

如果你更喜欢分开运行，可以使用：

```bash
npm run dev:api
npm run dev:web
```

## 常用命令

在仓库根目录执行：

| 命令 | 说明 |
| --- | --- |
| `npm run help` | 查看根任务说明 |
| `npm run dev:core` | 同时启动 API + Web |
| `npm run build:core` | 构建 API + Web |
| `npm run dev:api` | 单独启动 API |
| `npm run test:api` | 运行 API 测试 |
| `npm run build:api` | 构建 API |
| `npm run dev:web` | 单独启动 Web |
| `npm run test:web` | 运行 Web 单元测试 |
| `npm run lint:web` | 检查并修复 Web lint |
| `npm run build:web` | 构建 Web |
| `npm run dev:desktop:macos` | 启动 macOS 桌面端 |
| `npm run dev:desktop:windows` | 启动 Windows 桌面端 |
| `npm run build:desktop:macos` | 构建 macOS 桌面端 |
| `npm run build:desktop:windows` | 构建 Windows 桌面端 |
| `npm run dev:mobile:android` | 构建 Android Debug 包 |
| `npm run build:mobile:android:release` | 构建 Android Release 包 |
| `npm run dev:demo:oauth` | 启动 OAuth localhost Demo |
| `npm run dev:demo:oidc` | 启动 OIDC localhost Demo |
| `npm run dev:demo:sspu` | 启动 SSPU OAuth Demo |

## 配置说明

### API

- 环境文件目录：`api/`
- 环境选择变量：`KSUSER_ENV`
- 默认端口：`8000`
- 详细说明见 `api/README.md` 与 `api/docs/README.md`

### Web

- 环境文件：`web/.env.development`、`web/.env.production`
- 本地开发默认通过 `VITE_API_BASE_URL=http://localhost:8000` 连接 API
- Vite 配置未自定义端口时，开发环境通常为 `5173`

### Desktop

- 环境文件：`desktop/.env.development`、`desktop/.env.production`
- 关键配置：
  - `FLUTTER_API_BASE_URL`
  - `FLUTTER_PASSKEY_ORIGIN`
  - `FLUTTER_OIDC_CLIENT_ID`

### Mobile Android

- 可选环境文件：`mobile/android/.env.development`、`mobile/android/.env.production`
- 支持配置：
  - `API_BASE_URL`
  - `PASSKEY_RP_ID`
  - `PASSKEY_ORIGIN_HINT`
  - `APP_ENV`
  - `ENABLE_HTTP_LOGGING`
- 若未提供环境文件，Gradle 脚本会回落到默认值

## 子项目文档

- [API 文档](./api/README.md)
- [API 详细接口索引](./api/docs/README.md)
- [Web 文档](./web/README.md)
- [Desktop 文档](./desktop/README.md)
- [Demo 索引](./demos/README.md)

## CI / 构建工作流

仓库当前按模块拆分了 GitHub Actions：

- `.github/workflows/build-api.yml`
- `.github/workflows/build-web.yml`
- `.github/workflows/build-desktop.yml`
- `.github/workflows/build-mobile-android.yml`

这些工作流会在对应目录变更时触发，也支持手动触发。

## 目录协作建议

- 后端接口或认证逻辑变更，优先同步更新 `api/docs/`
- Web 授权页或登录流程变更，优先检查 `web/` 对 API 地址与 OAuth 参数的依赖
- 桌面端与移动端默认通过环境文件切换联调地址，不要把真实密钥直接写入仓库
- Demo 目录适合做本地联调与第三方接入验证，不建议作为生产代码入口

## License

本项目使用 Apache License 2.0：

- 许可证正文：`LICENSE`
- 署名通知：`NOTICE`

分发或二次修改时，请保留协议和通知文件中的版权与署名信息。
