# 注册接口

## 基本信息
- 方法：POST
- 路径：/auth/register
- 需要认证：否

## 注册流程
1. 前端调用 `/auth/check-username` 检查用户名是否可用
2. 用户填写密码（6-66个字符）、重复密码（前端校验）、邮箱
3. 前端调用 `/auth/send-code` 发送邮箱验证码
4. 用户收到验证码后，将用户名、密码、邮箱、验证码一起提交到此接口

## 请求体
```json
{
  "username": "test",
  "email": "test@example.com",
  "password": "password123",
  "code": "123456"
}
```

## 字段说明
- username: 用户名，不能为空
- email: 邮箱，不能为空
- password: 密码，6-66个字符
- code: 邮箱验证码，6位数字

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"password123","code":"123456"}' \
  http://localhost:8000/auth/register
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "注册成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "test",
    "email": "test@example.com",
    "avatarUrl": null,
    "createdAt": "2026-01-31T17:00:00"
  }
}
```

## 失败响应
### 1) 参数为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "密码不能为空"
}
```

或

```json
{
  "code": 400,
  "msg": "验证码不能为空"
}
```

### 2) 密码长度不符
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "密码长度必须在6-66个字符之间"
}
```

### 3) 验证码错误或已过期
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "验证码错误或已过期（3/5）"
}
```

### 4) 验证码错误次数过多
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 5) 用户名已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "用户名已存在"
}
```

### 6) 邮箱已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已存在"
}
```

## 安全说明
- 密码使用 Argon2id 算法哈希后存储
- 验证码错误超过5次将锁定邮箱1小时
- 邮箱被锁定期间，无法发送验证码和验证，1小时后自动解锁
- 邮箱验证码10分钟内有效，仅可使用一次
