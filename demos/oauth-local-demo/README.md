# Ksuser OAuth2.0 localhost 测试 Demo

这个目录提供了一个最简单的 OAuth2.0 测试示例：

- 文件：`ksuser_oauth_local_demo.py`
- 语言：Python
- 依赖：仅 Python 标准库，无需 `pip install`
- 兼容版本：Python 3.10+

它会完成下面这条完整链路：

1. 本地打开一个 Demo 页面
2. 点击按钮跳转到 Ksuser 授权页
3. 用户登录并同意授权
4. Ksuser 回调到本地 `http://localhost:端口/callback`
5. Demo 自动拿 `code` 去换 `access_token`
6. Demo 再调用 `/oauth2/userinfo`
7. 页面展示 `code`、`access_token` 返回值、`userinfo`

---

## 1. 目录说明

- `ksuser_oauth_local_demo.py`
  - 本地测试服务
- `env.example`
  - 环境变量示例

---

## 2. 使用前提

你至少需要准备好下面 3 样东西：

1. OAuth2 功能代码已经在后端和前端可用
2. 你有一个已登录的测试账号
3. 该测试账号在数据库中已经被管理员设为：
   - `personal`
   - 或 `enterprise`

也就是：

```sql
UPDATE users
SET verification_type = 'personal'
WHERE uuid = '你的测试用户UUID';
```

或者：

```sql
UPDATE users
SET verification_type = 'enterprise'
WHERE uuid = '你的测试用户UUID';
```

---

## 3. 本地联调推荐方式

如果你要测试你刚刚实现的本地代码，推荐用下面这套地址：

- 认证前端：`http://localhost:5173`
- 认证后端：`http://localhost:8000`
- 本地 Demo：`http://localhost:9000`

对应关系：

- 授权页走前端路由：`http://localhost:5173/oauth/authorize`
- token 接口走后端：`http://localhost:8000/oauth2/token`
- userinfo 接口走后端：`http://localhost:8000/oauth2/userinfo`
- 回调地址写到 Demo：`http://localhost:9000/callback`

---

## 4. 先启动你的项目

### 4.1 启动后端 `/api`

先确保数据库已经同步了这次 OAuth2.0 相关 SQL。

然后启动：

```bash
cd /Users/ksuserkqy/work/api
./gradlew bootRun
```

默认监听：

```text
http://localhost:8000
```

### 4.2 启动前端 `/auth`

```bash
cd /Users/ksuserkqy/work/auth
npm install
npm run dev
```

默认监听：

```text
http://localhost:5173
```

---

## 5. 在开放平台创建测试应用

### 5.1 登录认证中心

在浏览器里打开：

```text
http://localhost:5173/login
```

用你已经完成认证的测试账号登录。

### 5.2 进入开放平台

进入：

```text
设置中心 -> 开放平台
```

或者直接访问：

```text
http://localhost:5173/home/open-platform
```

### 5.3 创建应用时这样填写

推荐填写示例：

- 应用名称：`Local OAuth Demo`
- 回调地址：`http://localhost:9000/callback`
- 联系方式：`test@example.com`
- 权限范围：
  - `profile`
  - `email`

### 5.4 关键注意事项

#### 5.4.1 回调地址必须完全一致

这里的回调地址必须和 Demo 实际运行时使用的地址完全一致。

比如：

- 你创建应用时填的是 `http://localhost:9000/callback`
- 那么 Demo 就必须真的监听在这个地址

下面这些都算不一致：

- `http://localhost:9001/callback`
- `http://localhost:9000/oauth/callback`
- `http://127.0.0.1:9000/callback`

#### 5.4.2 不要写 127.0.0.1

你当前实现里只允许：

- `http://localhost...`
- `https://...`

所以本地 HTTP 测试时必须写：

```text
http://localhost:9000/callback
```

不要写：

```text
http://127.0.0.1:9000/callback
```

### 5.5 保存 AppID 与 AppSecret

创建成功后页面会返回：

- `AppID`
- `AppSecret`

注意：

- `AppSecret` 只展示一次
- 需要立刻保存
- 如果丢了，目前只能重新建应用

### 5.6 如果应用信息填错了

现在开放平台已经支持编辑以下字段：

- 应用名称
- 回调地址
- 联系方式

所以如果你端口、回调路径或者联系人填错了，可以直接在开放平台列表里点击“编辑”修改。

但要注意：

- 改了回调地址后，Demo 实际运行地址也必须同步改成完全一致
- 目前不能在线修改 `AppSecret`

---

## 6. 启动 Demo

### 6.1 进入目录

```bash
cd /Users/ksuserkqy/work/auth/demos/oauth-local-demo
```

### 6.2 加载环境变量

先复制 `env.example` 的内容，或者直接手动执行：

```bash
export KSUSER_CLIENT_ID="你的AppID"
export KSUSER_CLIENT_SECRET="你的AppSecret"

export KSUSER_AUTH_BASE_URL="http://localhost:5173"
export KSUSER_API_BASE_URL="http://localhost:8000"

export KSUSER_BIND_HOST="127.0.0.1"
export KSUSER_PUBLIC_HOST="localhost"
export KSUSER_DEMO_PORT="9000"
export KSUSER_REDIRECT_PATH="/callback"
export KSUSER_SCOPE="profile email"
export KSUSER_CA_BUNDLE=""
export KSUSER_INSECURE_SKIP_VERIFY="false"
```

说明：

- `KSUSER_BIND_HOST`
  - Python 服务实际绑定地址
  - 默认 `127.0.0.1`
- `KSUSER_PUBLIC_HOST`
  - 回调地址写出去时使用的主机名
  - 本地测试必须是 `localhost`
- `KSUSER_DEMO_PORT`
  - Demo 端口
- `KSUSER_REDIRECT_PATH`
  - 回调路径
- `KSUSER_SCOPE`
  - 可选
  - 如果留空，就只测 `openid / unionid`
- `KSUSER_CA_BUNDLE`
  - 可选
  - 自定义 CA 证书链文件（PEM）路径
  - 用于 Python 默认信任链无法覆盖你的服务证书时
  - 如果脚本同目录存在 `local-ca-bundle.pem`，会自动优先使用
- `KSUSER_INSECURE_SKIP_VERIFY`
  - 可选
  - 设为 `true/1/yes` 可临时关闭 HTTPS 证书校验（仅建议本地调试）

### 6.3 启动

```bash
python3 ksuser_oauth_local_demo.py
```

启动后会看到类似输出：

```text
Ksuser OAuth2.0 localhost demo
Bind address : http://127.0.0.1:9000
Public URL   : http://localhost:9000/
Redirect URI : http://localhost:9000/callback
Authorize URL: http://localhost:5173/oauth/authorize
Token URL    : http://localhost:8000/oauth2/token
Userinfo URL : http://localhost:8000/oauth2/userinfo
Server started. Press Ctrl+C to stop.
```

---

## 7. 实际测试流程

### 7.1 打开 Demo 首页

浏览器访问：

```text
http://localhost:9000/
```

页面会显示：

- 当前授权页地址
- token 接口地址
- userinfo 接口地址
- 当前回调地址
- 当前 `client_id`
- 当前 `scope`

### 7.2 点击“开始 OAuth2 测试”

Demo 会把你跳转到：

```text
http://localhost:5173/oauth/authorize?response_type=code&client_id=...&redirect_uri=...&scope=profile%20email&state=...
```

### 7.3 在授权页登录并授权

如果当前未登录，系统会先跳转登录页。

登录成功后会自动回到授权页。

然后点击：

```text
同意授权
```

### 7.4 回调到本地 Demo

授权成功后会回到：

```text
http://localhost:9000/callback?code=xxxx&state=xxxx
```

Demo 收到这个请求后会自动做两件事：

1. 调用：
   - `POST /oauth2/token`
2. 调用：
   - `GET /oauth2/userinfo`

然后把结果直接显示在页面上。

---

## 8. 成功后你应该看到什么

成功页面通常会显示：

- `state`
- `code`
- `openid`
- `unionid`
- token 响应 JSON
- userinfo 响应 JSON

如果 scope 选了 `profile email`，则 `userinfo` 里通常会有：

```json
{
  "openid": "...",
  "unionid": "...",
  "nickname": "...",
  "avatar_url": "...",
  "email": "..."
}
```

如果你没有勾选某个 scope，则对应字段不会返回。

例如：

- 没选 `profile`
  - 不返回 `nickname`
  - 不返回 `avatar_url`
- 没选 `email`
  - 不返回 `email`

---

## 9. 如果你想改端口或路径

### 9.1 改成 9001 端口

环境变量改成：

```bash
export KSUSER_DEMO_PORT="9001"
```

那你创建应用时的回调地址也必须改成：

```text
http://localhost:9001/callback
```

### 9.2 改成别的回调路径

环境变量改成：

```bash
export KSUSER_REDIRECT_PATH="/oauth/callback"
```

那你创建应用时的回调地址也必须改成：

```text
http://localhost:9000/oauth/callback
```

结论就是：

- 端口变了，应用配置也要变
- 路径变了，应用配置也要变
- 必须逐字符一致

---

## 10. 线上环境怎么测

如果你已经把这套 OAuth2.0 部署到了正式环境，也可以直接改成线上地址：

```bash
export KSUSER_AUTH_BASE_URL="https://auth.ksuser.cn"
export KSUSER_API_BASE_URL="https://api.ksuser.cn"
```

然后保留本地回调：

```bash
export KSUSER_PUBLIC_HOST="localhost"
export KSUSER_DEMO_PORT="9000"
export KSUSER_REDIRECT_PATH="/callback"
```

此时你在开放平台里创建应用时，回调地址仍然填：

```text
http://localhost:9000/callback
```

注意前提是：

- 线上前端的 `/oauth/authorize` 已经部署
- 线上后端的 `/oauth2/token` 与 `/oauth2/userinfo` 已经部署

---

## 11. 常见问题排查

### 11.1 提示 `redirect_uri 与应用登记信息不一致`

说明你传给授权接口或 token 接口的回调地址，和开放平台登记的不一致。

重点检查：

- 主机名是不是 `localhost`
- 端口是不是一致
- 路径是不是一致
- 是否多了 `/`

### 11.2 提示 `AppSecret 不正确`

说明：

- `KSUSER_CLIENT_SECRET` 填错了
- 或你复制时少了一部分
- 或你用了旧应用的 secret

### 11.3 提示 `授权码无效、已过期或已使用`

说明：

- 一个 `code` 被重复使用了
- 或授权码已经过期
- 正常做法是重新点击“开始 OAuth2 测试”

### 11.4 页面回到登录页后没继续授权

现在你的认证前端已经支持登录后跳回原授权页。

如果仍异常，检查：

- 你是不是直接清了浏览器 `sessionStorage`
- 浏览器是否禁用了站点存储
- 是否跨标签页反复跳转导致状态丢失

### 11.5 回调地址写成了 127.0.0.1

当前实现不允许 `http://127.0.0.1`

请改成：

```text
http://localhost:9000/callback
```

### 11.6 提示 `CERTIFICATE_VERIFY_FAILED`

常见原因：

- 服务器没有完整下发证书链（缺中间证书）
- 本机 Python 运行时信任链不完整
- 使用了公司内网代理，代理证书不在 Python 信任链内

建议按顺序处理：

1. 先确认服务端链路是否完整（可用浏览器或 `curl` 验证）
2. 如果系统和浏览器能访问，但 Python 失败，给脚本指定 CA 文件：

```bash
export KSUSER_CA_BUNDLE="/absolute/path/to/ca-bundle.pem"
```

3. 仅用于临时联调，可关闭校验（有中间人风险，不建议长期使用）：

```bash
export KSUSER_INSECURE_SKIP_VERIFY="true"
```

---

## 12. 建议的最小测试用例

建议按下面顺序测：

### 用例 1：只测基础身份标识

创建应用时 scope 不勾选任何项，或脚本里：

```bash
export KSUSER_SCOPE=""
```

预期：

- token 响应里有 `openid`、`unionid`
- userinfo 响应里至少有 `openid`、`unionid`
- 没有 `nickname`、`avatar_url`、`email`

### 用例 2：测 `profile`

```bash
export KSUSER_SCOPE="profile"
```

预期：

- userinfo 返回 `nickname`
- userinfo 返回 `avatar_url`（如果用户设置了头像）
- 不返回 `email`

### 用例 3：测 `email`

```bash
export KSUSER_SCOPE="email"
```

预期：

- userinfo 返回 `email`
- 不返回 `nickname`

### 用例 4：测 `profile email`

```bash
export KSUSER_SCOPE="profile email"
```

预期：

- userinfo 返回 `nickname`
- userinfo 返回 `email`
- userinfo 返回 `openid`、`unionid`

---

## 13. 关闭 Demo

回到运行脚本的终端，按：

```bash
Ctrl + C
```

---

## 14. 当前 Demo 的适用范围

这个 Demo 主要用于：

- 验证授权页是否能正常打开
- 验证登录后是否能回跳授权页
- 验证 `code` 是否能正常签发
- 验证 `/oauth2/token` 是否能正常换 token
- 验证 `/oauth2/userinfo` 返回字段是否正确
- 验证 `scope`、`openid`、`unionid` 行为是否符合预期

它不是生产 SDK，只是最小测试脚手架。
