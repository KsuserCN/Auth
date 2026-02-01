# 更改邮箱接口

## 基本信息
- 方法：POST
- 路径：/auth/change-email
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json
- 前置要求：必须先完成敏感操作验证（/auth/verify-sensitive）

## 用途
此接口用于更改用户的绑定邮箱。这是一个敏感操作，需要先通过身份验证。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "newEmail": "newemail@example.com",
  "code": "123456"
}
```

## 字段说明
- newEmail: 新邮箱地址，不能为空，不能与其他用户重复
- code: 发送到新邮箱的验证码，6位数字

## 完整操作流程

### 第一步：敏感操作验证
用户必须先完成身份验证，可以选择以下任一方式：

#### 方式1：密码验证
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"myPassword123"}' \
  http://localhost:8000/auth/verify-sensitive
```

#### 方式2：邮箱验证码验证
```bash
# 1. 发送验证码到当前邮箱
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"current@example.com","type":"login"}' \
  http://localhost:8000/auth/send-code

# 2. 提交验证码
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"method":"email-code","code":"123456"}' \
  http://localhost:8000/auth/verify-sensitive
```

### 第二步：发送新邮箱验证码
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"newemail@example.com","type":"change-email"}' \
  http://localhost:8000/auth/send-code
```

### 第三步：提交新邮箱和验证码
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"newEmail":"newemail@example.com","code":"654321"}' \
  http://localhost:8000/auth/change-email
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "邮箱更新成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john",
    "email": "newemail@example.com",
    "avatarUrl": "https://example.com/avatar.jpg"
  }
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

### 2) 用户不存在
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "用户不存在"
}
```

### 3) 未完成敏感操作验证
- HTTP Status：403

```json
{
  "code": 403,
  "msg": "请先完成敏感操作验证"
}
```

**解决方法**：先调用 `/auth/verify-sensitive` 完成身份验证

### 4) 验证已过期或在其他设备
- HTTP Status：403

```json
{
  "code": 403,
  "msg": "请先完成敏感操作验证"
}
```

**原因**：
- 距离上次验证已超过15分钟
- 当前设备（IP）与验证时的设备不同

**解决方法**：重新调用 `/auth/verify-sensitive` 进行验证

### 5) 新邮箱为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "新邮箱不能为空"
}
```

### 6) 验证码为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码不能为空"
}
```

### 7) 邮箱已被使用
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已被使用"
}
```

### 8) 验证码错误
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误（X/5）"
}
```

### 9) 验证码已过期
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码已过期，请重新获取（X/5）"
}
```

### 10) 未发送验证码
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "请先获取验证码"
}
```

### 11) 邮箱不匹配
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不匹配（X/5）"
}
```

**原因**：验证码发送的邮箱与请求中的新邮箱不一致

### 12) IP不匹配
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "发送验证码的设备与当前设备不匹配（X/5）"
}
```

**原因**：发送验证码和提交请求不在同一设备（IP）

### 13) 邮箱被锁定
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 14) 请求类型错误
- HTTP Status：415

```json
{
  "code": 415,
  "msg": "不支持的请求类型: text/plain。请使用 Content-Type: application/json"
}
```

## 验证码系统约束

此接口的验证码验证遵循所有验证码系统设计约束：

### 1. 邮箱校验
- 发送验证码时记录邮箱
- 验证时必须使用相同的邮箱

### 2. IP 校验
- 发送验证码时记录 IP
- 验证时必须在同一设备（IP）

### 3. 频率限制
- 同一邮箱：每分钟最多 1 次
- 同一 IP：每分钟最多 3 次
- 同一 IP/邮箱：每小时最多 14 次

### 4. 生命周期管理
- 验证码有效期：10 分钟
- 一次性使用：验证成功后自动删除
- 错误计数：5 次错误后锁定 1 小时

## 安全说明

### 双重验证
1. **第一重**：敏感操作验证（密码或当前邮箱验证码）
2. **第二重**：新邮箱验证码验证

### 设备绑定
- 敏感操作验证绑定设备（IP）
- 新邮箱验证码也绑定设备（IP）
- 确保整个流程在同一设备完成

### 时效性
- 敏感操作验证：15 分钟有效期
- 新邮箱验证码：10 分钟有效期
- 建议用户尽快完成整个流程

### 邮箱唯一性
- 新邮箱不能已被其他用户使用
- 系统自动检查邮箱唯一性

## 使用场景
用户需要更改绑定邮箱时，常见原因：
- 原邮箱不再使用
- 原邮箱账号被盗
- 更换工作/学校邮箱
- 需要使用新的邮箱接收通知

## 前端实现建议

### 推荐的用户体验流程
```
1. 用户点击"更改邮箱"按钮
2. 弹出敏感操作验证对话框
   - 选项1：输入密码
   - 选项2：发送验证码到当前邮箱
3. 验证成功后，显示"更改邮箱"表单
4. 用户输入新邮箱，点击"发送验证码"
5. 用户输入收到的验证码
6. 提交表单完成邮箱更改
7. 显示成功提示
```

### 注意事项
- 在验证成功后的15分钟内引导用户完成操作
- 显示倒计时提醒用户验证有效期
- 如果验证过期，自动重新引导用户进行验证
- 提供清晰的错误提示和解决方案

## 测试用例

### 正常流程
```bash
# 1. 密码验证
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"method":"password","password":"mypass"}' \
  http://localhost:8000/auth/verify-sensitive

# 2. 发送新邮箱验证码
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"new@example.com","type":"change-email"}' \
  http://localhost:8000/auth/send-code

# 3. 更改邮箱
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"newEmail":"new@example.com","code":"123456"}' \
  http://localhost:8000/auth/change-email
```

### 异常流程
```bash
# 未验证直接更改（应返回403）
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"newEmail":"new@example.com","code":"123456"}' \
  http://localhost:8000/auth/change-email
```

## 相关文档
- [敏感操作验证接口](auth-verify-sensitive.md)
- [发送验证码接口](auth-send-code.md)
- [验证码系统设计约束](VERIFICATION_CODE_DESIGN.md)
