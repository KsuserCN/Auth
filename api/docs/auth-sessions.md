# 在线设备/会话管理接口

## 概述
提供用户当前已连接设备（在线会话）的查询与撤销能力，返回包含 IP、地域、UA、设备类型等信息。

## 在线判定规则
- `online = true` 当 `lastSeenAt` 在最近 **10 分钟** 内
- 仅返回 **未过期且未撤销** 的会话

---

# 1) 获取在线会话列表

## 基本信息
- 方法：GET
- 路径：/auth/sessions
- 需要认证：是（AccessToken）
- 请求类型：无请求体

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/sessions
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "查询成功",
  "data": [
    {
      "id": 101,
      "ipAddress": "203.0.113.10",
      "ipLocation": "广东省深圳市",
      "userAgent": "Mozilla/5.0 ...",
      "browser": "Microsoft Edge 144.0.0.0",
      "deviceType": "Mac",
      "createdAt": "2026-02-09T10:00:00",
      "lastSeenAt": "2026-02-09T10:05:12",
      "expiresAt": "2026-02-16T10:00:00",
      "revokedAt": null,
      "online": true,
      "current": true
    }
  ]
}
```

## 字段说明
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 会话ID |
| ipAddress | String | 会话创建IP |
| ipLocation | String | IP属地 |
| userAgent | String | 原始User-Agent |
| browser | String | 浏览器信息 |
| deviceType | String | 设备类型/系统（Windows/Mac/Linux/Android/iOS/ChromeOS/Bot/Unknown） |
| createdAt | DateTime | 会话创建时间 |
| lastSeenAt | DateTime | 最后活跃时间（刷新或登录时更新） |
| expiresAt | DateTime | 会话过期时间 |
| revokedAt | DateTime | 会话撤销时间 |
| online | Boolean | 是否在线（最近10分钟活跃） |
| current | Boolean | 是否为当前会话 |

---

# 2) 撤销指定会话

## 基本信息
- 方法：POST
- 路径：/auth/sessions/{sessionId}/revoke
- 需要认证：是（AccessToken）
- 请求类型：无请求体

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/sessions/101/revoke
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "会话已取消"
}
```

## 失败响应
### 1) 未登录
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```

### 2) 会话不存在
- HTTP Status：404

```json
{
  "code": 404,
  "msg": "会话不存在"
}
```

### 3) 会话已失效
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "会话已失效"
}
```

---

# 数据库变更（如需手动迁移）

如果现有数据库已创建 `user_sessions` 表，需要执行以下 SQL 进行字段扩展：

```sql
ALTER TABLE user_sessions
  ADD COLUMN session_version INT NOT NULL DEFAULT 0 COMMENT '会话令牌版本（用于使旧AccessToken失效）',
  ADD COLUMN ip_address VARCHAR(45) DEFAULT NULL COMMENT '会话创建IP',
  ADD COLUMN ip_location VARCHAR(255) DEFAULT NULL COMMENT 'IP属地',
  ADD COLUMN user_agent TEXT DEFAULT NULL COMMENT 'User-Agent',
  ADD COLUMN browser VARCHAR(64) DEFAULT NULL COMMENT '浏览器信息',
  ADD COLUMN device_type VARCHAR(32) DEFAULT NULL COMMENT '设备类型/系统',
  ADD COLUMN last_seen_at DATETIME DEFAULT NULL COMMENT '最后活跃时间';
```

> 注意：新安装项目可直接使用最新的 [sql/init.sql](../sql/init.sql) 创建完整表结构。
