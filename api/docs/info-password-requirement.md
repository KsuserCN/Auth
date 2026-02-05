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
