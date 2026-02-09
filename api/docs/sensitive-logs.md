# 敏感操作日志 API

## 概述

敏感操作日志功能用于记录和查询用户的所有敏感操作，包括注册、登录、密码修改、邮箱修改、Passkey管理、TOTP管理等。系统会自动记录操作详情、IP属地、设备信息、风险评分等。

## 操作类型

系统支持以下敏感操作类型：

- `REGISTER` - 用户注册
- `LOGIN` - 用户登录
- `SENSITIVE_VERIFY` - 敏感操作认证
- `CHANGE_PASSWORD` - 修改密码
- `CHANGE_EMAIL` - 修改邮箱
- `ADD_PASSKEY` - 新增Passkey
- `DELETE_PASSKEY` - 删除Passkey
- `ENABLE_TOTP` - 启用TOTP
- `DISABLE_TOTP` - 禁用TOTP

## 登录方式 (login_method)

当操作类型为 `LOGIN` 时，会记录具体的登录方式：

### 基础登录方式（无MFA）
- `PASSWORD` - 密码登录（无MFA）
- `EMAIL_CODE` - 邮箱验证码登录（无MFA）
- `PASSKEY` - Passkey登录（无MFA）

### MFA登录方式（需要或已完成二步验证）
- `PASSWORD_MFA` - 密码 + TOTP二步验证登录
- `EMAIL_CODE_MFA` - 邮箱验证码 + TOTP二步验证登录
- `PASSKEY_MFA` - Passkey + TOTP二步验证登录

### MFA登录流程说明

当用户启用了MFA（TOTP二步验证）后，登录流程会分为两个阶段：

**阶段1：第一因素验证** - 记录 `_MFA` 后缀的登录方式
- 用户完成密码/验证码/Passkey验证
- 系统记录日志：`PASSWORD_MFA`、`EMAIL_CODE_MFA` 或 `PASSKEY_MFA`
- 状态：`SUCCESS`（表示第一因素验证通过，等待TOTP验证）

**阶段2：TOTP验证** - 再次记录 `_MFA` 后缀的登录方式
- 用户输入TOTP验证码
- 系统记录日志：相同的 `_MFA` 登录方式
- 状态：`SUCCESS`（TOTP验证通过）或 `FAILURE`（TOTP验证失败）

**示例：密码+MFA完整登录**
```json
[
  {
    "id": 124,
    "operationType": "LOGIN",
    "loginMethod": "PASSWORD_MFA",
    "result": "SUCCESS",
    "failureReason": null,
    "createdAt": "2026-02-09T10:00:00",
    "riskScore": 5
  },
  {
    "id": 125,
    "operationType": "LOGIN",
    "loginMethod": "PASSWORD_MFA",
    "result": "SUCCESS",
    "failureReason": null,
    "createdAt": "2026-02-09T10:00:05",
    "riskScore": 0
  }
]
```

### 前端展示建议

**登录方式显示文案：**
```javascript
const LOGIN_METHOD_TEXT = {
  'PASSWORD': '密码登录',
  'PASSWORD_MFA': '密码 + 二步验证',
  'EMAIL_CODE': '验证码登录',
  'EMAIL_CODE_MFA': '验证码 + 二步验证',
  'PASSKEY': 'Passkey登录',
  'PASSKEY_MFA': 'Passkey + 二步验证'
};
```

**登录方式图标：**
```javascript
const LOGIN_METHOD_ICON = {
  'PASSWORD': '🔑',
  'PASSWORD_MFA': '🔑🛡️',
  'EMAIL_CODE': '📧',
  'EMAIL_CODE_MFA': '📧🛡️',
  'PASSKEY': '🔐',
  'PASSKEY_MFA': '🔐🛡️'
};
```

## 查询敏感操作日志

### 接口

```
GET /auth/sensitive-logs
```

### 请求头

```
Authorization: Bearer <access_token>
```

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认为1 |
| pageSize | Integer | 否 | 每页数量，默认为20，最大100 |
| startDate | String | 否 | 开始日期，格式：YYYY-MM-DD |
| endDate | String | 否 | 结束日期，格式：YYYY-MM-DD |
| operationType | String | 否 | 操作类型，见上方操作类型列表 |
| result | String | 否 | 操作结果：SUCCESS 或 FAILURE |

### 响应示例

#### 成功响应

```json
{
  "status": "success",
  "message": "Sensitive logs retrieved successfully",
  "data": {
    "data": [
      {
        "id": 123,
        "operationType": "LOGIN",
        "loginMethod": "PASSWORD",
        "ipAddress": "203.208.60.1",
        "ipLocation": "广东省深圳市",
        "browser": "Chrome 120",
        "deviceType": "Desktop",
        "result": "SUCCESS",
        "failureReason": null,
        "riskScore": 10,
        "actionTaken": "ALLOW",
        "triggeredMultiErrorLock": false,
        "triggeredRateLimitLock": false,
        "durationMs": 245,
        "createdAt": "2026-02-07T14:30:00"
      },
      {
        "id": 122,
        "operationType": "CHANGE_PASSWORD",
        "loginMethod": null,
        "ipAddress": "203.208.60.1",
        "ipLocation": "广东省深圳市",
        "browser": "Chrome 120",
        "deviceType": "Desktop",
        "result": "SUCCESS",
        "failureReason": null,
        "riskScore": 15,
        "actionTaken": "ALLOW",
        "triggeredMultiErrorLock": false,
        "triggeredRateLimitLock": false,
        "durationMs": 189,
        "createdAt": "2026-02-07T10:15:30"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 45,
    "totalPages": 3
  }
}
```

#### 失败响应

```json
{
  "status": "error",
  "message": "Invalid or expired token",
  "data": null
}
```

### 请求示例

#### 查询所有日志（第一页）

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 查询特定日期范围的日志

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs?startDate=2026-02-01&endDate=2026-02-07" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 查询登录操作日志

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs?operationType=LOGIN" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 查询失败的操作

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs?result=FAILURE" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 分页查询

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs?page=2&pageSize=50" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 组合查询

```bash
curl -X GET "https://api.example.com/auth/sensitive-logs?operationType=LOGIN&result=FAILURE&startDate=2026-02-01&page=1&pageSize=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

## 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 日志ID |
| operationType | String | 操作类型 |
| loginMethod | String | 登录方式（仅登录操作有值） |
| ipAddress | String | 客户端IP地址 |
| ipLocation | String | IP属地（如：广东省深圳市） |
| browser | String | 浏览器信息 |
| deviceType | String | 设备类型/操作系统：Windows/Mac/Linux/Android/iOS/ChromeOS/Bot/Unknown |
| result | String | 操作结果：SUCCESS/FAILURE |
| failureReason | String | 失败原因（成功时为null） |
| riskScore | Integer | 风险评分（0-100，0为最低风险） |
| actionTaken | String | 处置动作：ALLOW（放行）/BLOCK（阻止）/FREEZE（冻结） |
| triggeredMultiErrorLock | Boolean | 是否触发多次错误锁定 |
| triggeredRateLimitLock | Boolean | 是否触发限速锁定 |
| durationMs | Integer | 操作耗时（毫秒） |
| createdAt | DateTime | 操作时间 |

## 自动记录说明

系统会在以下操作中自动记录敏感操作日志：

1. **注册** - 用户注册时自动记录
2. **登录** - 所有登录方式（密码、验证码、Passkey等）都会记录
3. **敏感操作认证** - 进行敏感操作前的身份验证
4. **修改密码** - 修改密码操作
5. **修改邮箱** - 绑定或修改邮箱操作
6. **Passkey管理** - 新增或删除Passkey
7. **TOTP管理** - 启用或禁用TOTP

日志记录是异步的，不会影响主业务流程的性能。系统会自动：
- 获取客户端真实IP地址
- 查询IP属地信息
- 解析User-Agent获取浏览器和设备信息
- 计算操作耗时
- 评估风险等级

## 状态码

- `200` - 成功
- `400` - 请求参数错误
- `401` - 未授权（token无效或过期）
- `500` - 服务器内部错误

## 注意事项

1. 必须提供有效的access token才能查询日志
2. 只能查询当前登录用户的日志，无法查询其他用户
3. 日期参数必须使用 `YYYY-MM-DD` 格式
4. 每页最多返回100条记录
5. 日志按创建时间倒序排列（最新的在前）
6. IP属地信息可能因第三方API限制而为空
7. 本地IP（如127.0.0.1）的属地会显示为"内网IP"
