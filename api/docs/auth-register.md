# 注册接口

## 基本信息
- 方法：POST
- 路径：/auth/register
- Content-Type：application/json

## 请求参数（JSON）
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| username | string | 是 | 用户名 |
| email | string | 是 | 邮箱 |
| password | string | 是 | 密码 |

## 请求示例
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123"
}
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "注册成功",
  "data": {
    "id": 1,
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "passwordHash": "***",
    "avatarUrl": null,
    "createdAt": "2026-01-31T13:10:00"
  }
}
```

## 失败响应
### 1) 参数缺失或为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名不能为空"
}
```

```json
{
  "code": 400,
  "msg": "邮箱不能为空"
}
```

```json
{
  "code": 400,
  "msg": "密码不能为空"
}
```

### 2) 用户名已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "用户名已存在"
}
```

### 3) 邮箱已存在
- HTTP Status：409

```json
{
  "code": 409,
  "msg": "邮箱已存在"
}
```

## 调用示例（curl）
```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"john_doe","email":"john@example.com","password":"password123"}' \
  http://localhost:8000/auth/register
```
