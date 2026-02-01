# 发送注册验证码接口

## 基本信息
- 方法：POST
- 路径：/auth/send-code
- 需要认证：否

## 请求体
```json
{
  "email": "user@example.com"
}
```

## 请求示例
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}' \
  http://localhost:8000/auth/send-code
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "验证码已发送"
}
```

## 限流规则
- 同一IP/邮箱：每分钟最多发送1次
- 同一IP/邮箱：每小时最多发送6次
- 验证码错误超过5次：该邮箱锁定1小时，期间无法发送验证码

## 验证码规则
- 格式：6位数字
- 有效期：10分钟
- 仅可使用一次

## 失败响应
### 1) 邮箱为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

### 2) 邮箱已被注册
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已被注册"
}
```

### 3) 邮箱被锁定
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "验证码错误次数过多，该邮箱已被锁定1小时"
}
```

### 4) 发送过于频繁（分钟限制）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "发送过于频繁，请1分钟后再试"
}
```

### 5) 发送次数过多（小时限制）
- HTTP Status：429

```json
{
  "code": 429,
  "msg": "发送次数过多，每小时最多发送6次"
}
```

或

```json
{
  "code": 429,
  "msg": "该邮箱发送次数过多，每小时最多发送6次"
}
```

### 6) 邮件发送失败
- HTTP Status：500

```json
{
  "code": 500,
  "msg": "邮件发送失败，请稍后重试"
}
```
