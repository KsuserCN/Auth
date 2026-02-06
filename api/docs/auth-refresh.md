# 刷新Token接口

## 基本信息
- 方法：POST
- 路径：/auth/refresh
- 需要认证：否（使用 RefreshToken Cookie）
- 请求类型：无请求体（仅使用Cookie）

## Token轮换机制
- ✅ **安全增强**：每次刷新都会生成新的RefreshToken
- ✅ **自动失效**：旧的RefreshToken会立即加入黑名单
- ✅ **Cookie更新**：新的RefreshToken自动设置到Cookie
- ✅ **防止重放**：旧Token无法再次使用

## 请求头
```
Cookie: refreshToken=<refreshToken>
```

## 请求示例
```bash![microsoft.svg](../../auth/public/oauth-icons/microsoft.svg)
curl -X POST \
  -b "refreshToken=<refreshToken>" \
  http://localhost:8000/auth/refresh
```

## 成功响应
- HTTP Status：200
- Set-Cookie：refreshToken（新的RefreshToken，HttpOnly，7天过期）

> **重要说明**：
> 1. 刷新成功后，同一会话（同设备）的旧 AccessToken 立即失效
> 2. 旧的 RefreshToken 也会立即失效并加入黑名单
> 3. 新的 RefreshToken 自动设置到 Cookie 中，客户端无需处理
> 4. 本地开发（app.debug=true）时 Cookie 为非 Secure；生产环境会自动设置 Secure

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

### 3) RefreshToken 已失效
- HTTP Status：401

**触发原因**：
- RefreshToken已被使用过（轮换后旧Token失效）
- 用户已退出登录
- 检测到可能的Token盗用

```json
{
  "code": 401,
  "msg": "RefreshToken已失效"
}
```

## 安全特性

### 1. Token轮换（Rotation）
- 每次刷新都生成新的RefreshToken
- 旧的RefreshToken立即失效（加入黑名单7天）
- 数据库session记录同步更新

### 2. 防止Token重放攻击
- 已使用的RefreshToken无法再次使用
- 如果检测到旧Token被使用，说明可能存在盗用

### 3. SessionVersion机制
- 每次刷新增加sessionVersion
- 旧的AccessToken即使未过期也会因版本不匹配而失效

## 使用示例

### 示例1：正常刷新流程

```bash
# 第一次刷新
curl -X POST \
  -b "refreshToken=old_token_abc123" \
  http://localhost:8000/auth/refresh

# 响应（Cookie会自动更新为new_token_xyz789）
# {
#   "code": 200,
#   "msg": "刷新成功",
#   "data": {
#     "accessToken": "eyJhbGc..."
#   }
# }

# 如果再次使用旧Token刷新（会失败）
curl -X POST \
  -b "refreshToken=old_token_abc123" \
  http://localhost:8000/auth/refresh

# 响应
# {
#   "code": 401,
#   "msg": "RefreshToken已失效"
# }
```
