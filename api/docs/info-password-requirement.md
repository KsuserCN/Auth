# 密码强度要求查询

## 基本信息
- 方法：GET
- 路径：/info/password-requirement
- 需要认证：否

## 用途
用于前端动态获取当前系统的密码强度要求，以便进行提示和校验。

## 请求示例
```bash
curl -X GET \
  http://localhost:8000/info/password-requirement
```

## 成功响应
- HTTP Status：200

```json
{
  "code": 200,
  "msg": "获取成功",
  "data": {
    "minLength": 6,
    "maxLength": 66,
    "requireUppercase": true,
    "requireLowercase": true,
    "requireDigits": true,
    "requireSpecialChars": false,
    "rejectCommonWeakPasswords": true,
    "requirementMessage": "密码强度不足：需包含大写字母、小写字母、数字"
  }
}
```

## 字段说明
| 字段 | 类型 | 说明 |
|------|------|------|
| minLength | number | 最小长度 |
| maxLength | number | 最大长度 |
| requireUppercase | boolean | 是否要求大写字母 |
| requireLowercase | boolean | 是否要求小写字母 |
| requireDigits | boolean | 是否要求数字 |
| requireSpecialChars | boolean | 是否要求特殊字符 |
| rejectCommonWeakPasswords | boolean | 是否拒绝常见弱密码 |
| requirementMessage | string | 组合后的密码强度提示文案 |

## 密码预校验

- 方法：`POST`
- 路径：`/info/validate-password`
- 需要认证：否；需要现有 Cookie / Header CSRF 校验。
- 请求体：`{"password":"用户输入的原始密码"}`。密码放在 JSON 请求体中，不放入 URL，不裁剪首尾空白。

注册页面在设置密码后、进入确认密码及邮箱验证前调用。接口与最终注册共用
`RegistrationValidationService.validatePassword`，检查当前长度、字符要求及常见弱密码规则，
无需客户端维护弱密码名单。该接口不创建账号、不发送邮件、不校验或消耗验证码。

通过时返回 HTTP 200：`{"code":200,"errorCode":"REQUEST_SUCCESS"}`。
失败时返回 HTTP 400 及与正式注册一致的 `errorCode` / `params`，例如
`{"code":400,"errorCode":"AUTH_021"}` 表示常见弱密码；`AUTH_020` 携带 `min` / `max` 长度参数。
响应带 `Cache-Control: no-store`，不回传密码。

客户端仅在预校验成功后进入下一步；网络异常时保留输入并允许重试。预校验不签发免检凭据，
最终注册仍执行全部校验，以覆盖流程中规则变更及绕过客户端的请求。
