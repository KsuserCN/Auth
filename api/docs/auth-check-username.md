# 检查用户名接口

## 基本信息
- 方法：GET
- 路径：/auth/check-username
- 需要认证：否

## 请求参数
- username (query string, 必填): 用户名

## 请求示例
```bash
curl -X GET "http://localhost:8000/auth/check-username?username=test"
```

## 成功响应
- HTTP Status：200

### 用户名已存在
```json
{
  "code": 200,
  "msg": "用户名已存在",
  "data": {
    "exists": true
  }
}
```

### 用户名可用
```json
{
  "code": 200,
  "msg": "用户名可用",
  "data": {
    "exists": false
  }
}
```

## 失败响应
### 用户名为空
- HTTP Status：400

```json
{
  "code": 400,
  "msg": "用户名不能为空"
}
```
