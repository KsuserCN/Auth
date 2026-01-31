# 刷新Token接口

## 基本信息
- 方法：POST
- 路径：/auth/refresh
- 需要认证：否（使用 RefreshToken Cookie）

## 请求头
```
Cookie: refreshToken=<refreshToken>
```

## 请求示例
```bash
curl -X POST \
  -b "refreshToken=<refreshToken>" \
  http://localhost:8000/auth/refresh
```

## 成功响应
- HTTP Status：200

> 说明：刷新成功后，旧的 AccessToken 立即失效

```json
{
  "code": 200,
  "msg": "刷新成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

## 失败响应
### 1) RefreshToken 不存在
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "RefreshToken不存在"
}
```

### 2) RefreshToken 无效或已过期
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "RefreshToken无效或已过期"
}
```
