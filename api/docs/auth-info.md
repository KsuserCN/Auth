# 用户信息接口

## 基本信息
- 方法：GET
- 路径：/auth/info
- 需要认证：是（AccessToken）

## 查询参数
| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| type | string | 否 | basic | basic 仅返回基础字段；details 返回全部字段 |

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X GET \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  http://localhost:8000/auth/info
```

```bash
curl -X GET \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  "http://localhost:8000/auth/info?type=details"
```

## 成功响应
- HTTP Status：200

### type=basic（默认）
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "avatarUrl": null
  }
}
```

### type=details
```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "john_doe",
    "email": "john@example.com",
    "avatarUrl": null,
    "realName": "张三",
    "gender": "male",
    "birthDate": "1999-01-01",
    "region": "Beijing",
    "bio": "这里是个人简介",
    "updatedAt": "2026-02-03T12:00:00"
  }
}
```

## 失败响应
### 1) 未登录或 Token 无效
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录"
}
```
