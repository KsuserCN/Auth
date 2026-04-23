# SSPU 登录页第三方 OAuth 接入模拟

这个目录提供了一个本地高保真 demo，用来模拟 `https://id.sspu.edu.cn/cas/login` 的页面，并在“其它方式登录”中新增 `Ksuser OAuth` 入口。

特点：

- 页面结构、文案和主视觉接近目标登录页
- 保留原页的微信 / 企业微信占位入口
- 新增 `Ksuser OAuth` 图标入口
- 优先走你已配置的 OAuth2.0 应用
- 当真实 OAuth2.0 配置不可用时，自动回退到内置 mock provider，便于单独演示

## 启动

```bash
cd /Users/ksuserkqy/work/auth/demos/sspu-oauth-demo
python3 server.py
```

默认地址：

```text
http://localhost:9003/
```

## 真实 OAuth2.0 模式

`server.py` 会自动读取：

- `/Users/ksuserkqy/work/auth/demos/sspu-oauth-demo/.env`
- 或回退读取 `/Users/ksuserkqy/work/auth/demos/oauth-local-demo/.env`

需要的变量与 `test` 一致：

- `KSUSER_CLIENT_ID`
- `KSUSER_CLIENT_SECRET`
- `KSUSER_AUTH_BASE_URL`
- `KSUSER_API_BASE_URL`
- `KSUSER_SCOPE`
- `KSUSER_CA_BUNDLE`（可选）

TLS 说明：

- 默认直接使用系统证书信任库，不依赖仓库内 PEM 文件
- 在 macOS 上，如果 Python 默认信任链不可用，`server.py` 会自动从系统 Keychain 导出根证书并在运行时使用
- 如果你的本机 Python 校验证书失败，可以显式设置 `KSUSER_CA_BUNDLE`
- 或在当前目录放一个未入库的 `local-ca-bundle.pem`，`server.py` 会自动优先读取它

当前真实 OAuth2.0 路径使用的是：

- 授权页：`/oauth/authorize`
- token：`/oauth2/token`
- userinfo：`/oauth2/userinfo`

## 交互说明

- 点击“微信”或“企业微信”只保留占位反馈
- 点击新增的 `Ksuser OAuth` 图标会发起登录
- 成功后会直接跳转到本地模拟的“社交账号绑定”页面
- 在绑定页可继续查看 token / userinfo / OAuth2 集成信息
