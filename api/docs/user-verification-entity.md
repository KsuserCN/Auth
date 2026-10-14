# 认证实体与数据库维护

`users.verification_entity_name` 保存管理员核验过的认证实体，最多 200 个字符。

| 账号类型 | 认证实体内容 | 认证状态 |
| --- | --- | --- |
| 个人认证 | 真实姓名 | `verification_type = 'personal'` |
| 企业认证 | 企业名称 | `verification_type = 'enterprise'` |
| 管理员 / 超级管理员 | 真实姓名 | `system_role = 'ADMIN' / 'SUPER_ADMIN'` |

系统角色仍与开发者认证状态分开维护。管理员的授权页认证类型显示为 `admin`。

## 升级现有数据库

现有数据库按以下顺序执行增量迁移，不使用会重建表的 `init.sql`：

1. `sql/migrations/20261012_console_official.sql`：分离系统角色与开发者认证。
2. `sql/migrations/20261013_user_verification_entity.sql`：增加用户认证实体名称。
3. `sql/migrations/20261014_official_optional_nonce.sql`：增加官方应用 nonce 兼容开关。EzBookKeep 验收后执行 `20261014_retire_sso.sql` 清理旧表。

新建数据库使用最新 `sql/init.sql`。认证实体名称与官方应用兼容开关分别保存。

迁移不会从 `username` 或 `real_name` 自动补录认证实体：这两个字段都可以由用户编辑。
历史已认证账号需要管理员核验后手动补录；缺失名称时，授权页显示“主体资料未登记”。

## Console 认证管理

超级管理员可在 Console「用户管理 → 认证设置」设置个人认证、企业认证或撤销开发者认证，并填写核验后的姓名 / 企业名称。管理员姓名也可以在此补录。个人、企业和管理员主体名称必填，最长 200 个字符；普通用户撤销认证时清空旧主体名称。

开发者认证与系统角色独立。任免普通管理员使用用户行上的「任命普通管理员 / 撤销管理员」，保留已有开发者认证；认证设置不会改变系统角色，也不能授予超级管理员。普通管理员仍不能登录 Console。

`PUT /console/users/{uuid}/verification` 接收 `developerVerificationType`（`none / personal / enterprise`）与 `verificationEntityName`。接口仅接受超级管理员的 Console 会话，要求 CSRF 和有效的再次验证；通过显式数据库更新写入认证字段，并与 `user.verification.update` 操作记录一同提交，记录失败时整体回滚。

「认证用户应用」页列出个人认证、企业认证和管理员账号，支持用户名、邮箱、UUID 搜索与认证类型筛选。`GET /console/users?verification=verified` 返回这些账号；`GET /console/users/{uuid}/applications` 按实际 `owner_user_id` 返回该账号的 OAuth 应用，以及名称、客户端 ID、启用状态、回调地址、权限范围、联系信息和时间。应用密钥及哈希不会返回。官方应用继续在独立页面管理。

认证实体字段继续沿用；官方应用 nonce 兼容开关需另行迁移。

## SQL 手动认证

以下是维护模板，请替换 UUID 和已核验的姓名 / 企业名称后执行。认证状态与实体名称应在同一条语句中更新。

```sql
-- 个人认证
UPDATE users
SET verification_type = 'personal', verification_entity_name = '已核验的真实姓名'
WHERE uuid = '目标账号UUID';

-- 企业认证
UPDATE users
SET verification_type = 'enterprise', verification_entity_name = '已核验的企业名称'
WHERE uuid = '目标账号UUID';

-- 管理员 / 超级管理员姓名补录，不改变已有系统角色
UPDATE users
SET verification_entity_name = '已核验的真实姓名'
WHERE uuid = '目标管理员UUID' AND system_role IN ('ADMIN', 'SUPER_ADMIN');

-- 查询仍未补录认证实体的已认证账号
SELECT uuid, username, verification_type, system_role
FROM users
WHERE (verification_type IN ('personal', 'enterprise') OR system_role IN ('ADMIN', 'SUPER_ADMIN'))
  AND (verification_entity_name IS NULL OR TRIM(verification_entity_name) = '');
```

## 对外读取与写入限制

- `/auth/info` 与资料更新响应通过 `verificationEntityName` 返回当前认证实体；未认证账号返回 `null`。
- OAuth 的授权上下文 `subjectName` 读取应用归属账号的认证实体，扫码授权也使用相同字段。
- OAuth 的已授权应用列表 `creatorName` 使用同一认证实体，保持授权页与授权管理列表的主体一致。
- 官方应用继续显示“Ksuser官方应用”标签。
- `/auth/update/profile` 不接受 `verificationEntityName`、`verification_type` 或其他认证字段；不支持的 key 返回 HTTP 400。
- `User` 实体不提供该名称的 setter，JSON 输入忽略该字段，JPA 插入与更新均不会写入该列。
- `verification_type` 的 JPA 普通更新也禁用写入，避免从旧用户实例保存资料时覆盖管理员刚设置的认证状态；Console 使用显式更新查询维护它。
- 用户编辑资料里的姓名、用户名、头像，不会改变认证实体。认证实体仅通过上述 Console 管理接口的显式数据库更新或管理员 SQL 维护。
