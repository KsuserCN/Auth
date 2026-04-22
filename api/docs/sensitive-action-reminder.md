# 敏感操作提醒邮件功能集成说明

## 功能概述
当用户在 `user_settings` 表中 `notify_sensitive_action_email` 字段为 1（true）时，系统会在用户发生任一敏感操作（即 `user_sensitive_logs` 表有新记录）后，自动发送敏感操作提醒邮件至用户绑定邮箱。

## 邮件模板
- 模板文件：`src/main/resources/templates/sensitive-action-reminder.html`
- 邮件内容包含：操作类型、操作时间、IP及属地、设备类型、浏览器信息等。

## 主要代码改动

### 1. 邮件服务扩展
`EmailService.java` 新增方法：
```java
public void sendSensitiveActionReminder(String toEmail, String operation, UserSensitiveLog log)
```
用于渲染并发送敏感操作提醒邮件。

### 2. 敏感日志服务集成通知
`SensitiveLogService.java` 的 `logAsync` 和 `logSync` 方法中，新增如下逻辑：
- 检查用户设置（`notifySensitiveActionEmail`）
- 获取用户邮箱
- 调用 `EmailService.sendSensitiveActionReminder` 发送邮件

### 3. 依赖注入
新增注入：
- `EmailService`
- `UserSettingsRepository`
- `UserRepository`

## 触发条件
- 只要敏感操作日志记录（注册、登录、修改密码、绑定邮箱、Passkey管理、TOTP管理等）发生，且用户设置允许邮件通知，即会自动发送提醒。

## 邮件示例
见 `sensitive-action-reminder.html`，变量包括：
- `${operation}` 操作类型
- `${time}` 操作时间
- `${ip}` IP地址
- `${ipLocation}` IP属地
- `${deviceType}` 设备类型
- `${browser}` 浏览器

## 其他说明
- 邮件为系统自动发送，请勿回复。
- 如非本人操作，请及时修改密码并开启多因素认证。
- 如发现异常，请联系官方客服或冻结账户。

---
如需进一步扩展或自定义邮件内容，请修改模板文件和 `EmailService` 相关方法。
