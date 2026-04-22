# 退出登录接口

## 基本信息
- 方法：POST
- 路径：/auth/logout
- 需要认证：否（使用 RefreshToken Cookie）
- 请求类型：无请求体（仅使用Cookie）

## 请求头
```
Cookie: refreshToken=<refreshToken>
```

## 请求示例
```bash
curl -X POST \
  -b "refreshToken=<refreshToken>" \
  http://localhost:8000/auth/logout
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "退出成功"
}
```

**说明**：
- 本地开发（app.debug=true）时 Cookie 为非 Secure；生产环境会自动设置 Secure
- 退出后会自动清除该用户的敏感操作验证状态（如果存在）

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
