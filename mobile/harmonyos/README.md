# Ksuser 安全 · HarmonyOS

使用 ArkTS / ArkUI 的鸿蒙原生客户端。五个主栏目使用真实后端数据，启动时恢复本机会话，未登录时进入邮箱密码、邮箱验证码或 Passkey 登录页。

## 接入范围

- 账号：注册、邮箱验证码、密码策略、登录、TOTP/恢复码及 Passkey 双重验证、会话恢复、退出当前/全部设备。
- 资料：完整资料读取、逐项编辑、系统照片选择/编辑、方形头像预览与上传。
- 安全：保护偏好、风险状态、敏感身份验证、邮箱密码修改、Passkey 新增/重命名/删除、TOTP 绑定/停用/恢复码、账号恢复授权和删除账号。
- 活动：真实设备列表及会话撤销、安全日志服务端分页和日期/结果/操作类型筛选、应用授权列表及撤销。
- 扫码：系统 Scan Kit 单码扫描和相册识别；扫码登录本机、批准网页登录/MFA/敏感验证/Console 请求、网页 OAuth/OIDC 应用授权确认和拒绝。

QQ/Apple 原生登录和相关验证、安全推送、SDK、App Linking 唤起及原生应用间授权不在本次接入范围内。未登录的账号恢复在受信官方网页完成。

## 构建与本地配置

用 DevEco Studio 打开本目录，安装目标 SDK **26.0.0**；最低兼容 **HarmonyOS 6.1.1 / API 24**。真机运行需在 Signing Configs 中启用自己的有效签名，仓库不包含证书、私钥和口令。

默认地址：

| 配置 | 默认值 |
| --- | --- |
| API | `https://api.ksuser.cn` |
| 网页站点 | `https://auth.ksuser.cn` |
| Passkey RP ID | `auth.ksuser.cn` |

开发覆盖配置：

```sh
cd mobile/harmonyos
mkdir -p entry/src/main/resources/rawfile
cp environment.example.json entry/src/main/resources/rawfile/environment.local.json
```

编辑 `environment.local.json` 的三个公开配置项。此文件已忽略，只由 **debug** 版本读取；release 使用上述默认生产地址。API 与网页必须填写站点地址，不能含用户信息、查询参数或业务路径。开发 API 可以使用手机可访问的局域网 HTTP 地址；网页二维码仍限定受信 HTTPS 站点。服务端 RP ID 与原生应用配置必须一致。

局域网 HTTP 联调时，在后端开发环境配置 `DEBUG=true`，从 `api/` 使用 `KSUSER_ENV=development ./gradlew bootRun` 启动，使 refresh/CSRF Cookie 使用开发地址且不带 Secure 限制。手机上的 API 地址应使用这台 Mac 的局域网地址，不能填写 `localhost`。生产环境保持 `DEBUG=false` 和 HTTPS。

会话文件、加密密钥按 API 地址分区，切换开发/生产服务不会携带另一环境的凭据。开发设置页显示当前 API 及最近一次原生 Passkey 响应中的公开 Origin。

macOS 默认安装路径下构建：

```sh
DEVECO_SDK_HOME=/Applications/DevEco-Studio.app/Contents/sdk \
  /Applications/DevEco-Studio.app/Contents/tools/node/bin/node \
  /Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw.js \
  --mode module -p product=default -p module=entry@default assembleHap --no-daemon
```

未设置签名时输出 `entry/build/default/outputs/default/entry-default-unsigned.hap`。SDK 对 CryptoFramework 的设备能力提示由运行时 `canIUse` 检查处理，不支持时明确报错，不回退到明文保存。

## 会话与原生能力

`core/` 包含环境、类型、HTTP 客户端、Cookie 处理、仓库、账号状态、二维码解析和本地文案解析；`native/` 包含系统扫码、Passkey、图片处理和安全存储；`views/` 使用共享账号状态展示真实数据。

请求使用 Bearer accessToken，并独立保存后端返回的 refresh/CSRF Cookie；写请求先取得 CSRF 并发送 `X-XSRF-TOKEN`。多个 401 共享一次刷新，每个请求最多重放一次。网络错误不重放写请求，也不清除已有凭据。

HTTP 请求有 35 秒整体截止时间；超时会销毁原生请求，迟到响应不再更新会话。debug 日志中的 `KsuserAccountFlow` 只记录启动、网络和会话提交的阶段名称，不记录账号输入、令牌、Cookie 或 Passkey 签名数据。

登录先使用内存临时会话，完成 MFA 和完整资料读取后才替换当前账号。退出、取消验证和切换账号会使旧异步结果失效；切换期间不允许旧操作借用新账号凭据。

安全存储由 Asset Store 保存 32 字节 AES 密钥（本机、首次解锁后可用、不随卸载保留），完整快照经 AES-GCM 加密后通过临时文件加原子 rename 保存。令牌、Cookie、资料缓存不进入普通 Preferences/AppStorage；应用备份关闭。暂时锁屏或服务不可用只提示重试；损坏的本机凭据提供明确的清除按钮。

扫码使用 `scanBarcode.startScanForResult`，该系统默认界面管理相机访问。头像使用系统照片选择器授予的 URI 访问权限，不申请整库权限。

## Passkey 与网页扫码联调

Passkey 使用 `@kit.OnlineAuthenticationKit` 的 `fido2.register/authenticate`。服务端挑战和凭据标识按 Base64URL 转为字节；原生返回的 clientDataJSON、签名、认证器数据保持原字节，提交现有 `/auth/passkey/*` 接口。

后端新增可选 `app.passkey.harmony-origins` / `PASSKEY_HARMONY_ORIGINS`，默认空列表。签名真机准备好后：

1. 先用邮箱登录，进入安全页，通过敏感身份验证后尝试添加 Passkey。
2. 在 debug「关于与设置 → 开发配置」查看原生返回的 Origin；仅查看这个公开值，不记录或上传完整认证载荷。
3. 若该 Origin 尚未被服务端放行，将其**完整精确值**写入对应后端环境的 `PASSKEY_HARMONY_ORIGINS`（多个值用逗号分隔）；保持 RP ID 一致。禁止填写通配符，禁止根据包名或证书猜格式。
4. 重新执行注册，再验证登录、MFA、敏感验证、重命名和删除。用户取消、拒绝、超时、无凭据和设备不支持均有明确反馈。

网页扫码沿用现有协议：

| 内容/类型 | 客户端行为 |
| --- | --- |
| `KSUSER-AUTH-XFER:v1:` | 预览并明确确认，通过 `/auth/session-transfer/exchange`、`target=mobile` 创建本机独立会话 |
| `KSUSER-AUTH-QR:` 普通请求 | `/auth/qr/preview` 后通过 `/auth/qr/approve` 确认网页登录/MFA/敏感验证/Console |
| `approve_application` | 获取 `/auth/mobile-authorization/context`，要求 `qr=true` 且非原生授权；展示账号、应用、网站、权限与期限，再调用 `/decide` |
| 旧 `approve_official` | 使用 `/auth/qr/official/approve` 或 `/reject` 兼容既有网页请求 |

“同时登录通行证网页端”默认关闭。结果由发起二维码的原浏览器领取，鸿蒙不打开第三方回调，不接收 `/app/authorize`、`/app/sdk-authorize` 等非二维码授权入口。

## 验证

从本目录运行生产核心代码的 host 测试：

```sh
/Applications/DevEco-Studio.app/Contents/tools/node/bin/node --test \
  entry/src/test/core/*.test.mjs \
  entry/src/test/native/native-adapters.test.mjs
```

测试通过 SDK 自带 TypeScript 转译实际 ArkTS 源码，仅替换 ArkUI 装饰器、网络、文件及系统 Kit 适配。自定义安装路径时设置 `HARMONY_TYPESCRIPT_PATH` 指向 TypeScript 模块目录。覆盖 Cookie/CSRF、并发刷新、轮换保存失败、账号隔离、迟到响应、网页二维码约束、授权参数、原生数据映射和 AES-GCM 密文/篡改/原子性；这不能替代真机 CryptoFramework/FIDO2 验收。

连接并解锁模拟器或真机后，可构建 `entry@ohosTest` 的 `assembleHap`，在已安装主应用的设备上安装 `entry/build/default/outputs/ohosTest/entry-ohosTest-unsigned.hap`，再运行：

```sh
hdc -t <设备ID> shell aa test -b cn.ksuser.auth.hmos -m entry_test \
  -s unittest OpenHarmonyTestRunner -s class HarmonyNativeIntegration \
  -s timeout 20000 -w 150
```

这组集成测试调用真实系统 Kit：独立虚构会话的 Asset Store/AES-GCM 往返与篡改拒绝、公开密码策略和 CSRF 请求，以及非交互的 FIDO2 能力诊断。Cookie 的实际响应头和系统 Cookie 列表格式均有覆盖；诊断只记录格式元数据。FIDO2 用例记录能力查询结果或公开错误码，测试通过不代表 Passkey 注册/认证已验收。例如当前 API 26 模拟器能力查询返回 `801`，仍需在有效签名设备上完成交互验证。测试不使用账号密码，测试数据使用独立随机存储 scope 并在结束时清理。

共享文案检查（在仓库根目录）：

```sh
node scripts/generate-api-result-codes.mjs --check
node scripts/generate-security-log-catalog.mjs --check
```

后端回归（在 `api/`）：

```sh
./gradlew test --tests cn.ksuser.api.service.PasskeyServiceTest \
  --tests cn.ksuser.api.service.UserAgentParserServiceTest
```

真机验收需要连接并授权 USB 调试、当前工程签名、可用测试账号与已配置的真实 Origin。检查登录/重启恢复、Passkey 全流程、跨端扫码、网页应用同意/拒绝、浏览器结果领取，以及资料、头像、会话、日志和授权撤销。此版本不自动提交或部署服务端环境配置。
