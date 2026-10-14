# Ksuser Console

Vue 3 Web 管理面板，生产地址为 **https://console.ksuser.cn**。登录和再次验证都由 Ksuser 通行证完成；只有 `SUPER_ADMIN` 能进入面板。普通 `ADMIN` 保留通行证开放平台中的 OAuth 管理资格。

## 用户认证与用户应用

「用户管理」支持个人认证、企业认证、认证主体名称登记，以及任免普通管理员。开发者认证与系统角色独立：认证设置保留角色，任免管理员保留认证。个人 / 企业认证需填写已核验的姓名 / 企业名称，管理员可以在认证设置中登记真实姓名。认证和角色修改均沿用 Console 的再次验证，并写入操作记录。

「认证用户应用」列出个人认证、企业认证和管理员账号，支持搜索与类型筛选。点击「查看应用」可查看 OAuth 应用的该账号创建的启用状态、回调地址和权限范围；用户管理中的「应用」按钮也可直达该账号。应用归属以数据库记录为准。

部署数据库需已执行 `20261013_user_verification_entity.sql`；新增兼容开关需执行 `20261014_official_optional_nonce.sql`。字段升级、手动维护和接口说明见[认证实体与数据库维护](../api/docs/user-verification-entity.md)。

## iOS App 扫码登录

登录页默认展示扫码入口。打开已登录的 Ksuser iOS App，点击「扫码」，扫描后核对 **Ksuser Console**、目标网站和当前账号，点击「确认登录管理中心」，浏览器自动进入管理面板。也可切换到原有通行证登录。

二维码有效期为两分钟，过期或网络异常可刷新；刷新、取消和切换登录方式会使旧请求失效。扫码确认授予官方应用 `ksuser_console` 的 `openid profile email` 范围，沿用既有的应用启用状态、回调白名单和超级管理员权限检查。领取后台会话必须同时提供发起浏览器的 HttpOnly Cookie、challenge ID 和轮询凭据，且仅可领取一次；手机和 Console 之间不传递通行证访问令牌。扫码登录不授予敏感写操作的再次验证权限。

本功能接入 iOS App，Android 不在本次接入范围。发布时同步更新 API、Console 和 iOS App，无新增数据库迁移；官方应用仍需按下文完成初始化。

## 数据库升级和首次初始化

API 默认使用 Hibernate `validate`，部署新版 API 前必须先升级数据库。备份现有数据库，然后在仓库根目录执行以下命令；`-p` 会提示输入密码，不把密码写入命令。

```bash
mysql -h <数据库地址> -u <数据库用户> -p <数据库名> \
  < api/sql/migrations/20261012_console_official.sql

mysql -h <数据库地址> -u <数据库用户> -p <数据库名> \
  < api/sql/migrations/20261012_console_bootstrap.sql
```

第一份 SQL 新增系统角色、官方应用注册/授权和审计表，并将旧 `verification_type=admin` 迁移到 `system_role=ADMIN`。第二份在事务中检查现有账号，将 UUID 为 `e92ca6f9-67ea-4e43-b715-0b37affba178` 的用户任命为初始超级管理员，并注册以下应用：

| 配置 | 初始值 |
|---|---|
| Client ID | `ksuser_console` |
| 客户端类型 | `public`，无需 client secret，强制 PKCE S256 |
| 回调 | `https://console.ksuser.cn/auth/callback` |
| 可申请范围 | `openid profile email` |
| 状态 | 启用，保留应用 |

脚本可重复执行。目标用户不存在或已有 Client ID 的关键配置冲突时，初始化失败并回滚。该用户必须先通过正常通行证注册存在于 `users` 表；脚本不创建账号。数据库账号需具备建表、修改表以及创建和删除临时存储过程的权限。

```sql
SELECT uuid, username, system_role FROM users
WHERE uuid = 'e92ca6f9-67ea-4e43-b715-0b37affba178';

SELECT client_id, client_type, redirect_uris, scopes, is_active, reserved
FROM official_applications WHERE client_id = 'ksuser_console';
```

全新数据库可以先使用 `api/sql/init.sql` 创建结构、注册指定账号，再执行初始化 SQL。**现有数据库升级不能重跑 `init.sql`，它包含删表语句。** 执行初始化 SQL 后发布新版 API、通行证 Web 和 Console。这里提供 SQL 交付；开发工具不会自动连接或修改生产数据库。

## 本地开发

```bash
npm --prefix console ci
npm run dev:core
```

API 为 `http://localhost:8000`，通行证为 `http://localhost:5173`，Console 为 `http://localhost:5174`。也可以分别运行 `dev:api`、`dev:web` 和 `dev:console`。

如果本地页面请求 `api.ksuser.cn` 并出现 CORS 错误，确认用 `npm run dev:console` 启动（Vite development mode），且 `console/.env.development` 指向 `http://localhost:8000`；修改 env 后重启 Vite。`api/.env.development` 应启用 `DEBUG=true`。不要把 localhost 源加入生产 CORS 来规避本地配置问题。

仅在本地开发数据库中为已初始化的应用加入回调：

```sql
UPDATE official_applications
SET redirect_uris = CONCAT(redirect_uris, CHAR(10), 'http://localhost:5174/auth/callback')
WHERE client_id = 'ksuser_console'
  AND INSTR(CONCAT(';', REPLACE(redirect_uris, CHAR(10), ';'), ';'),
            ';http://localhost:5174/auth/callback;') = 0;
```

在 API 的本地 `.env.development` 中增加以下非敏感配置：

```dotenv
DEBUG=true
APP_OFFICIAL_ISSUER=http://localhost:8000/official
APP_OFFICIAL_AUTHORIZATION_URL=http://localhost:5173/official/authorize
APP_CONSOLE_ORIGIN=http://localhost:5174
APP_CONSOLE_PASSPORT_ORIGIN=http://localhost:5173
```

Console 的 `.env.development` 和 `.env.production` 分别连接本地 API 和 `https://api.ksuser.cn`。所有域名需要保持环境一致；生产数据库仅保留生产回调。

## 生产发布

```bash
npm --prefix console ci
npm run build:console
```

发布 `build/console/` 静态文件到 `console.ksuser.cn`。参考 `deploy/nginx.conf` 配置 HTTPS 和 Vue Router 回退，并替换站点目录、证书路径。Console 请求现有 `api.ksuser.cn`，API 的 CORS 已开放 Console 的精确生产源；官方应用的其他浏览器源只在官方 token/userinfo 接口开放，并再次按具体客户端校验。

API 生产环境设置 `DEBUG=false`；可显式指定：

```dotenv
APP_OFFICIAL_ISSUER=https://api.ksuser.cn/official
APP_OFFICIAL_AUTHORIZATION_URL=https://auth.ksuser.cn/official/authorize
APP_CONSOLE_ORIGIN=https://console.ksuser.cn
APP_CONSOLE_PASSPORT_ORIGIN=https://auth.ksuser.cn
```

Console Cookie 为 API 主机下的 HttpOnly Cookie，路径 `/console`，生产开启 Secure 和 SameSite=Strict。客户端携带 Cookie 和 CSRF 请求头。后台会话最长 8 小时，退出不注销通行证；写操作验证绑定当前后台会话，有效 15 分钟。执行写操作的账号需已设置密码、Passkey 或 TOTP。

## 官方应用接入

超级管理员在「官方应用」中创建应用，配置精确回调和最小资料范围。`public` 不使用密钥；`confidential` 的密钥仅在创建和轮换时显示，数据库只保存哈希。完整的接入步骤、客户端类型对照、授权码 + PKCE 流程、scope 和令牌验证方法见[官方应用接入指南](../api/docs/official-app-integration.md)。

官方应用使用独立的 `/official` OIDC 接口，ID Token 和 UserInfo 中的 `sub` / `uuid` 是真实 UUID。开放平台 OAuth `/oauth2` 使用不同的登记及标识规则；不要混用它们的客户端凭据。

## 检查

SQL 回归使用独立的临时 MySQL 8 实例，关闭网络监听，覆盖空库、旧库、重复执行、缺失 UUID、应用冲突和事务回滚。提供已安装或解压的 MySQL 二进制即可运行：

```bash
python3 api/sql/tests/test_console_migrations.py --mysql-bin-dir /path/to/mysql/bin
```

详见 [SQL 测试说明](../api/sql/tests/README.md)。该脚本不会连接既有数据库。

```bash
npm run test:api
npm --prefix web run test:unit -- --run
npm run test:console
npm run build:web
npm run build:console
npm run check:api-codes
```

生产验收：确认指定超级管理员可登录，普通管理员被拒绝；任免管理员需要再次验证；官方应用范围变化会再次要求用户同意；退出 Console 后通行证仍可使用。
