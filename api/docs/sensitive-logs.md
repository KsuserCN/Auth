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

## 登录方式 (`loginMethod` / `loginMethods`)

当操作类型为 `LOGIN` 时：

- `loginMethod` 保留原始字符串，兼容旧前端。
- `loginMethods` 返回规范化后的 token 数组，前端应优先使用该字段渲染标签。

### 基础登录方式（无MFA）
- `PASSWORD` - 密码登录（无MFA）
- `EMAIL_CODE` - 邮箱验证码登录（无MFA）
- `PASSKEY` - Passkey登录（无MFA）
- `QQ` - QQ 登录
- `GITHUB` - GitHub 登录
- `MICROSOFT` - Microsoft 登录
- `GOOGLE` - Google 登录
- `WECHAT` - 微信登录
- `BRIDGE_FROM_DESKTOP` - 当前网页登录态来自电脑端桥接
- `BRIDGE_FROM_WEB` - 当前电脑端登录态来自网页登录桥接

### MFA 登录方式（已进入二步验证并给出最终结果）
- `["password", "mfa"]` - 密码 + TOTP二步验证登录
- `["email", "mfa"]` - 邮箱验证码 + TOTP二步验证登录
- `["passkey", "mfa"]` - Passkey + TOTP二步验证登录
- `["google", "mfa"]` - Google + MFA 登录
- `["microsoft", "mfa"]` - Microsoft + MFA 登录
- `["github", "mfa"]` - GitHub + MFA 登录
- `["qq", "mfa"]` - QQ + MFA 登录

### MFA 记录规则

当用户启用了 MFA 后，敏感操作日志只记录一次最终登录结果：

- 第一因素通过、但尚未完成 MFA 时，不再写入单独的成功日志。
- MFA 最终成功时，记录一条 `LOGIN` 成功日志，例如 `["google", "mfa"]`。
- MFA 最终失败时，记录一条 `LOGIN` 失败日志，例如 `["password", "mfa"]` + 失败原因。

**示例：密码+MFA完整登录**
```json
[
  {
    "id": 124,
    "operationType": "LOGIN",
    "loginMethod": "[password, mfa]",
    "loginMethods": ["PASSWORD", "MFA"],
    "result": "SUCCESS",
    "failureReason": null,
    "createdAt": "2026-02-09T10:00:00",
    "riskScore": 5
  }
]
```

### 前端展示建议

**登录方式显示文案：**
```javascript
const LOGIN_METHOD_TEXT = {
  'PASSWORD': '密码登录',
  'EMAIL_CODE': '验证码登录',
  'PASSKEY': 'Passkey登录',
  'QQ': 'QQ',
  'GITHUB': 'GitHub',
  'MICROSOFT': 'Microsoft',
  'GOOGLE': 'Google',
  'WECHAT': '微信',
  'MFA': 'MFA',
  'BRIDGE_FROM_DESKTOP': '电脑端桥接',
  'BRIDGE_FROM_WEB': '网页端桥接'
};
```

**登录方式图标：**
```javascript
const LOGIN_METHOD_ICON = {
  'PASSWORD': '🔑',
  'EMAIL_CODE': '📧',
  'PASSKEY': '🔐',
  'QQ': '💬',
  'GITHUB': '🐙',
  'MICROSOFT': '🪟',
  'GOOGLE': '🔎',
  'WECHAT': '💚',
  'MFA': '🛡️',
  'BRIDGE_FROM_DESKTOP': '🖥️',
  'BRIDGE_FROM_WEB': '🌐'
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
        "loginMethod": "[google, mfa]",
        "loginMethods": ["GOOGLE", "MFA"],
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
| loginMethod | String | 登录方式原始值（仅登录操作有值，兼容旧前端） |
| loginMethods | Array<String> | 登录方式规范化 token 列表，前端应优先使用 |
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
2. **登录** - 所有登录方式（密码、验证码、Passkey、第三方 OAuth、桥接登录等）都会记录
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
- 对登录方式做规范化拆分，便于前端展示 provider / MFA / bridge 标签

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
