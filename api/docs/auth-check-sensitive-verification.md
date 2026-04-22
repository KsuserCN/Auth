# 检查敏感操作验证状态接口

## 基本信息
- 方法：GET
- 路径：/auth/check-sensitive-verification
- 需要认证：是（AccessToken）

## 用途
查询当前用户敏感验证状态，并返回后端建议的优先验证方式与可选方式列表，供前端默认跳转使用。

## 请求头
```
Authorization: Bearer <accessToken>
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "查询成功",
  "data": {
    "verified": false,
    "remainingSeconds": 0,
    "preferredMethod": "passkey",
    "methods": ["passkey", "password", "email-code", "totp"]
  }
}
```

## 响应字段说明
- verified：是否已完成敏感验证（boolean）
- remainingSeconds：剩余有效秒数
- preferredMethod：后端推荐优先方式（由用户设置偏好 + 当前可用性计算）
- methods：当前可用的敏感验证方式（已按推荐顺序排序）

## methods 取值
- password：密码
- email-code：邮箱验证码
- passkey：Passkey
- totp：TOTP

## 前端使用建议
1. 打开敏感操作弹窗时先调用本接口。
2. 默认展示 preferredMethod 对应 tab 或步骤。
3. 同时展示 methods 全部可选项，提供“选择其他验证方式”。
4. passkey 方式走专用接口：
   - /auth/passkey/sensitive-verification-options
   - /auth/passkey/sensitive-verification-verify
5. 其余方式走 /auth/verify-sensitive。

## 失败响应
- 401 未登录
- 401 用户不存在
