# Ksuser OIDC localhost 测试 Demo

这个目录提供了一个和 `demos/oauth-local-demo` 同风格的 OIDC 本地联调示例：

- 文件：`ksuser_oidc_local_demo.py`
- 语言：Python
- 依赖：仅 Python 标准库，无需 `pip install`
- 兼容版本：Python 3.10+

它会完成下面这条链路：

1. 本地打开一个 Demo 页面
2. 点击按钮跳转到 Ksuser 授权页
3. Demo 自动携带 `scope=openid profile email`、`nonce`、PKCE 参数
4. 用户登录并同意授权
5. Ksuser 回调到本地 `http://localhost:端口/callback`
6. Demo 使用 `code + code_verifier` 调 `/official/token`
7. 页面展示 `access_token`、`id_token`、`userinfo`

## 1. 启动依赖服务

后端：

```bash
cd api
./gradlew bootRun
```

前端：

```bash
cd .
npm run dev
```

## 2. 创建测试应用

在 Console 中由超级管理员创建 confidential 官方应用时，回调地址请填写：

```text
http://localhost:9002/callback
```

权限范围建议至少勾选：

- `openid`
- `profile`
- `email`

说明：

- 本地 HTTP 测试只允许 `http://localhost...`，不要写 `127.0.0.1`

## 3. 配置环境变量

```bash
cd ./demos/oidc-local-demo
cp env.example .env
```

至少需要改成你自己的：

- `KSUSER_CLIENT_ID`
- `KSUSER_CLIENT_SECRET`

TLS 说明：

- 默认会自动探测系统可用的 CA bundle
- 如果同目录存在 `local-ca-bundle.pem`，会优先使用
- 也可显式设置 `KSUSER_CA_BUNDLE` 或 `SSL_CERT_FILE`
- 仅调试时可设置 `KSUSER_INSECURE_SKIP_VERIFY=true` 关闭校验

## 4. 启动 Demo

```bash
python3 ksuser_oidc_local_demo.py
```

默认地址：

```text
http://localhost:9002/
```

## 5. 验证结果

授权成功后，页面会展示：

- `code`
- `sub` / `uuid`
- `id_token`
- `/official/userinfo` 响应
- `/official/.well-known/openid-configuration` 返回值

如果 `/official/token` 返回 `invalid_grant`，优先检查：

1. 回调地址是否和 Console 登记值完全一致
2. `code_verifier` 是否与本次授权使用的 `code_challenge` 对应
3. 授权码是否已经被消费过
