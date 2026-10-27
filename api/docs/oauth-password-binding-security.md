# OAuth 绑定密码限速与 MFA 提交

`POST /auth/login` 与 QQ、GitHub、Google、Microsoft 的 `POST /oauth/{provider}/bind-existing` 共用 `PasswordAuthenticationService`。控制器不能直接调用 `UserService.login` 绕过密码尝试额度。

## 密码额度

服务先查找目标账号，再通过一次 Redis Lua 调用原子预留所有额度，最后校验密码。现有账号使用稳定的用户 ID 计数，数据库认为等价的邮箱写法共享额度；不存在的账号使用去除首尾空白、转小写后的邮箱哈希。请求参数不会直接作为 Redis 账号键。

额度沿用 `app.rate-limit` 的现有登录配置：

| 维度 | 每分钟 | 每小时 |
| --- | --- | --- |
| 账号 | 5 | 60 |
| IP | 10 | 120 |
| OAuth 待绑定令牌 | 整个令牌期限最多 5 次 | 同左 |

只有所有额度都允许时才一起递增，密码校验只计数一次；被拒绝的请求不会消耗其他账号或网络的额度。成功密码登录、验证码登录和第三方登录都不能清空这些密码额度。`clearAllLimits` 兼容方法已废弃并改为空操作。

密码额度使用独立的 `ratelimit:password:v1:` 命名空间并自然到期；请求不会延长已有窗口。超额返回 HTTP 429 / `AUTH_035`；Redis 故障、无返回值或异常额度数据返回 HTTP 503 / `INTERNAL_ERROR`，不继续校验密码。

## 待绑定令牌

第三方回调签发的 `oauthBindToken` 保持现有响应字段，但服务端改用 `oauth:pending-bind:v2:` 记录。旧版令牌不再接受；客户端必须重新发起第三方登录获取新令牌。令牌和密码计数器在同一次 Lua 中创建，均固定 15 分钟有效。错误请求、领取、消费、密码校验均不能续期。

状态仅允许 `READY → CLAIMED → CONSUMED`。`bind-existing` 只在密码正确后领取，领取时固定目标账号和随机拥有者。`bind-existing`、`register-bind`、`bind-pending` 都使用同一领取操作；一个入口领取后，其余入口无法用同一令牌绑定另一个账号。

领取记录只保存在服务端；客户端不提交拥有者或待完成的第三方身份。最终消费要求目标、拥有者、提供方和第三方身份全部匹配，且原期限未过。失败后不恢复令牌，应重新进行第三方回调。

## MFA 与事务

`bind-existing` 的目标账号启用 MFA 时，只返回现有的 HTTP 201 / `AUTH_MFA_REQUIRED` 响应，并保存服务端绑定 Claim，尚不写入 `user_oauth_accounts` 或签发会话。此类挑战只允许已有 TOTP（含其恢复码）或 Passkey，不接受扫码 MFA；没有可用的独立因子时拒绝继续。挑战还校验原 IP 和规范化后的 User-Agent。

MFA 成功后原子领取挑战的完成结果，再调用统一完成服务：先消费待绑定令牌，然后在数据库事务中锁定目标用户、检查冲突并写入绑定。已有数据库的 `uk_oauth_provider_user`、`uk_oauth_provider_union` 唯一索引负责跨用户竞争的最终约束。写入成功后才签发登录会话；冲突或存储错误不退回普通登录。存储失败会回滚绑定，已消费的令牌不恢复。

MFA 的内存挑战沿用现有架构：重启或切换到不持有挑战的 API 实例时会拒绝完成，客户端需重新开始。MFA 取消、耗尽或超时不写绑定；取消后重新绑定也需要新的第三方回调。`register-bind` 的账号注册和最终绑定仍是两个事务边界，若令牌在注册期间被其他入口领取，可能保留已注册但未绑定的账号；不会将第三方身份写到该账号。

## 验证与发布

无需新增数据库表或增量迁移，实体上的身份唯一约束与现有初始化 SQL 对齐。发布时统一替换所有 API 实例，摘除仍支持旧待绑定令牌或未限速密码校验的实例；不要在混合版本中继续开放绑定。请求与响应字段保持兼容，客户端需处理旧令牌失效并重新登录。

普通单元测试通过 `npm run test:api` 执行。完整 Lua 集成测试需 Java 21 和本地 Redis 可执行文件；测试会在随机本地端口创建独立 Redis 进程，禁用持久化并自动清理，不连接业务 Redis：

```sh
cd api
KSUSER_ENV=security-test \
KSUSER_ENV_DIR=/tmp/ksuser-api-security-test \
KSUSER_TEST_REDIS_SERVER=/absolute/path/to/redis-server \
./gradlew test --offline --no-daemon
```

`KSUSER_ENV_DIR` 应指向没有业务环境文件的测试目录。未提供 `KSUSER_TEST_REDIS_SERVER` 时，真实 Redis 集成测试会标记跳过；发布验收应提供它，并确认没有跳过。回归覆盖共享账号/IP/令牌预算、并发密码校验上限、固定期限、Redis 故障、MFA 完成前不写绑定、一次完成与数据库回滚。
