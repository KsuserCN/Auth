# 所有设备退出登录接口

## 基本信息
- 方法：POST
- 路径：/auth/logout/all
- 需要认证：是（使用 AccessToken）

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求示例
```bash
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  http://localhost:8000/auth/logout/all
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "已从所有设备退出登录"
}
```

## 说明
- 调用此接口后，该用户所有设备的 AccessToken 和 RefreshToken 都将失效
- 当前请求的 RefreshToken Cookie 也会被清除

## 失败响应
### 1) 未登录或 AccessToken 过期
- HTTP Status：401

```json
{
  "code": 401,
  "msg": "未登录或Token已过期"
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
