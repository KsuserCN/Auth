# 注销账号接口

## 基本信息
- 方法：POST
- 路径：/auth/delete
- 需要认证：是（使用 AccessToken）
- 请求类型：application/json
- 前置要求：必须先完成敏感操作验证（/auth/verify-sensitive）

## 用途
此接口用于注销用户账号。这是一个敏感操作，需要先通过身份验证。账号注销后，用户信息将被删除，所有会话将被清除。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 请求体
```json
{
  "confirmText": "DELETE"
}
```

## 字段说明
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| confirmText | string | 是 | 确认文本，必须为 "DELETE"（区分大小写），防止误删 |

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "账号已注销"
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

### 4) 确认文本错误
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "请输入正确的确认文本"
}
```

## 安全特性
1. **敏感操作验证**：必须先通过 `/auth/verify-sensitive` 完成身份验证
2. **双确认机制**：要求输入确切的 "DELETE" 文本，防止误操作
3. **完全删除**：删除用户账号、所有会话和相关数据
4. **Token 失效**：立即将使用的 AccessToken 加入黑名单，所有会话自动失效
5. **不可恢复**：账号删除后无法恢复

## 完整操作流程
```
1. 用户已登录（拥有 AccessToken）

2. 调用 /auth/verify-sensitive 完成敏感操作验证
   → 可选使用密码验证或邮箱验证码验证
   → 验证成功后在 15 分钟内可执行敏感操作

3. 调用 /auth/delete 注销账号
   → 确认文本必须为 "DELETE"
   → 账号立即被删除

4. 用户成为未登录状态
   → 所有会话全部失效
   → 无法再使用该账号登录
```

## 使用示例

### 注销账号
```bash
# 第1步：完成敏感操作验证（以密码验证为例）
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "password",
    "password": "userPassword123"
  }' \
  http://localhost:8000/auth/verify-sensitive

# 响应：
# {
#   "code": 200,
#   "msg": "验证成功"
# }

# 第2步：注销账号
curl -X POST \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "confirmText": "DELETE"
  }' \
  http://localhost:8000/auth/delete

# 响应：
# {
#   "code": 200,
#   "msg": "账号已注销"
# }
```

## 相关接口
- [敏感操作验证](/auth/verify-sensitive)
- [用户信息](/auth/info)
- [用户登录](/auth/login)
