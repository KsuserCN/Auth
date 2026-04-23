#!/usr/bin/env python3
"""
Ksuser OAuth2.0 localhost demo.

Zero third-party dependencies. Compatible with Python 3.10+.

Environment variables:
  KSUSER_CLIENT_ID        Required. OAuth2 AppID.
  KSUSER_CLIENT_SECRET    Required. OAuth2 AppSecret.
  KSUSER_AUTH_BASE_URL    Default: http://localhost:5173
  KSUSER_API_BASE_URL     Default: http://localhost:8000
  KSUSER_BIND_HOST        Default: 127.0.0.1
  KSUSER_PUBLIC_HOST      Default: localhost
  KSUSER_DEMO_PORT        Default: 9000
  KSUSER_REDIRECT_PATH    Default: /callback
  KSUSER_SCOPE            Default: profile email
  KSUSER_CA_BUNDLE        Optional. Path to a custom CA bundle PEM file.
  KSUSER_INSECURE_SKIP_VERIFY Optional. true/1/yes to disable HTTPS verification (debug only).
"""

from __future__ import annotations

import atexit
import html
import json
import os
import secrets
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent
LOCAL_CA_BUNDLE = ROOT / "local-ca-bundle.pem"
MACOS_SYSTEM_ROOT_KEYCHAIN = "/System/Library/Keychains/SystemRootCertificates.keychain"
COMMON_CA_BUNDLE_CANDIDATES = (
    "/etc/ssl/cert.pem",
    "/private/etc/ssl/cert.pem",
    "/opt/homebrew/etc/openssl@3/cert.pem",
    "/usr/local/etc/openssl@3/cert.pem",
    "/opt/homebrew/etc/openssl/cert.pem",
    "/usr/local/etc/openssl/cert.pem",
)


def load_dotenv() -> None:
    env_path = ROOT / ".env"
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip("'").strip('"')
        if key and key not in os.environ:
            os.environ[key] = value


load_dotenv()


AUTH_BASE_URL = os.getenv("KSUSER_AUTH_BASE_URL", "http://localhost:5173").rstrip("/")
API_BASE_URL = os.getenv("KSUSER_API_BASE_URL", "http://localhost:8000").rstrip("/")
BIND_HOST = os.getenv("KSUSER_BIND_HOST", "127.0.0.1").strip() or "127.0.0.1"
PUBLIC_HOST = os.getenv("KSUSER_PUBLIC_HOST", "localhost").strip() or "localhost"
DEMO_PORT = int(os.getenv("KSUSER_DEMO_PORT", "9000"))
REDIRECT_PATH = os.getenv("KSUSER_REDIRECT_PATH", "/callback").strip() or "/callback"
CLIENT_ID = os.getenv("KSUSER_CLIENT_ID", "").strip()
CLIENT_SECRET = os.getenv("KSUSER_CLIENT_SECRET", "").strip()
SCOPE = os.getenv("KSUSER_SCOPE", "profile email").strip()
INSECURE_SKIP_VERIFY = os.getenv("KSUSER_INSECURE_SKIP_VERIFY", "").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}

STATE_TTL_SECONDS = 600
VALID_STATES: dict[str, float] = {}


def normalize_redirect_path(value: str) -> str:
    return value if value.startswith("/") else f"/{value}"


REDIRECT_PATH = normalize_redirect_path(REDIRECT_PATH)


def build_redirect_uri() -> str:
    return f"http://{PUBLIC_HOST}:{DEMO_PORT}{REDIRECT_PATH}"


def cleanup_temp_ca_bundle(path: str) -> None:
    try:
        os.remove(path)
    except OSError:
        pass


def export_macos_system_roots() -> str | None:
    if sys.platform != "darwin":
        return None
    commands = [
        ["security", "find-certificate", "-a", "-p"],
        ["security", "find-certificate", "-a", "-p", MACOS_SYSTEM_ROOT_KEYCHAIN],
    ]
    for command in commands:
        try:
            pem_data = subprocess.check_output(command, stderr=subprocess.STDOUT)
        except Exception:
            continue
        if b"BEGIN CERTIFICATE" not in pem_data:
            continue
        fd, path = tempfile.mkstemp(prefix="oauth-local-ca-", suffix=".pem")
        with os.fdopen(fd, "wb") as handle:
            handle.write(pem_data)
        atexit.register(cleanup_temp_ca_bundle, path)
        return path
    return None


def is_valid_pem_file(path: Path) -> bool:
    if not path.exists() or not path.is_file():
        return False
    try:
        with path.open("rb") as handle:
            return b"BEGIN CERTIFICATE" in handle.read(16384)
    except OSError:
        return False


def find_available_system_ca_bundle() -> str | None:
    verify_paths = ssl.get_default_verify_paths()
    dynamic_candidates = [verify_paths.cafile, verify_paths.openssl_cafile]
    all_candidates = dynamic_candidates + list(COMMON_CA_BUNDLE_CANDIDATES)
    checked: set[str] = set()
    for candidate in all_candidates:
        if not candidate:
            continue
        candidate_path = str(Path(candidate).expanduser())
        if candidate_path in checked:
            continue
        checked.add(candidate_path)
        if is_valid_pem_file(Path(candidate_path)):
            return candidate_path
    return None


def resolve_ca_bundle() -> tuple[str, str]:
    from_ksuser = os.getenv("KSUSER_CA_BUNDLE", "").strip()
    if from_ksuser:
        return from_ksuser, "KSUSER_CA_BUNDLE"
    from_ssl_env = os.getenv("SSL_CERT_FILE", "").strip()
    if from_ssl_env:
        return from_ssl_env, "SSL_CERT_FILE"
    if LOCAL_CA_BUNDLE.exists():
        return str(LOCAL_CA_BUNDLE), "local-ca-bundle.pem"
    from_system_bundle = find_available_system_ca_bundle()
    if from_system_bundle:
        return from_system_bundle, "detected-system-ca-bundle"
    exported = export_macos_system_roots()
    if exported:
        return exported, "macOS-system-keychain"
    return "", "system-default"


CA_BUNDLE, CA_BUNDLE_SOURCE = resolve_ca_bundle()


def build_ssl_context() -> ssl.SSLContext | None:
    if not API_BASE_URL.startswith("https://"):
        return None

    if INSECURE_SKIP_VERIFY:
        return ssl._create_unverified_context()

    if not CA_BUNDLE:
        return ssl.create_default_context()

    ca_file = Path(CA_BUNDLE).expanduser()
    if not ca_file.exists():
        raise FileNotFoundError(f"KSUSER_CA_BUNDLE file not found: {ca_file}")
    return ssl.create_default_context(cafile=str(ca_file))


SSL_CONTEXT: ssl.SSLContext | None = None
SSL_CONTEXT_ERROR: str | None = None
try:
    SSL_CONTEXT = build_ssl_context()
except Exception as exc:
    SSL_CONTEXT_ERROR = str(exc)


def urlopen_with_tls(request: urllib.request.Request, timeout: int = 20) -> Any:
    if SSL_CONTEXT is None:
        return urllib.request.urlopen(request, timeout=timeout)
    return urllib.request.urlopen(request, timeout=timeout, context=SSL_CONTEXT)


def cleanup_states() -> None:
    now = time.time()
    expired = [state for state, created_at in VALID_STATES.items() if now - created_at > STATE_TTL_SECONDS]
    for state in expired:
        VALID_STATES.pop(state, None)


def pretty_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def html_page(title: str, body: str) -> bytes:
    document = f"""<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{html.escape(title)}</title>
    <style>
      :root {{
        color-scheme: light;
        --bg: #f7f5ef;
        --card: #ffffff;
        --line: #e6dfd3;
        --text: #1f2937;
        --muted: #6b7280;
        --brand: #d97706;
        --brand-dark: #b45309;
        --danger: #b91c1c;
        --ok: #047857;
      }}
      * {{ box-sizing: border-box; }}
      body {{
        margin: 0;
        padding: 32px 16px;
        background:
          radial-gradient(circle at top left, rgba(217, 119, 6, 0.14), transparent 30%),
          radial-gradient(circle at bottom right, rgba(2, 132, 199, 0.12), transparent 35%),
          var(--bg);
        color: var(--text);
        font: 14px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Helvetica Neue", Arial, sans-serif;
      }}
      .container {{
        width: min(960px, 100%);
        margin: 0 auto;
      }}
      .card {{
        background: var(--card);
        border: 1px solid var(--line);
        border-radius: 20px;
        padding: 24px;
        box-shadow: 0 18px 50px rgba(15, 23, 42, 0.06);
      }}
      h1, h2, h3 {{ margin: 0 0 12px; line-height: 1.3; }}
      h1 {{ font-size: 28px; }}
      h2 {{ font-size: 18px; margin-top: 24px; }}
      p {{ margin: 0 0 12px; }}
      .muted {{ color: var(--muted); }}
      .notice {{
        border-left: 4px solid var(--brand);
        background: #fff8eb;
        padding: 12px 14px;
        border-radius: 12px;
        margin: 16px 0;
      }}
      .error {{
        border-left-color: var(--danger);
        background: #fff1f2;
      }}
      .success {{
        border-left-color: var(--ok);
        background: #ecfdf5;
      }}
      .actions {{
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin: 20px 0 0;
      }}
      .button {{
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-height: 42px;
        padding: 0 16px;
        border-radius: 999px;
        background: var(--brand);
        color: #fff;
        text-decoration: none;
        font-weight: 600;
      }}
      .button.secondary {{
        background: #fff;
        color: var(--text);
        border: 1px solid var(--line);
      }}
      .grid {{
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 14px;
        margin-top: 16px;
      }}
      .field {{
        padding: 14px;
        border: 1px solid var(--line);
        border-radius: 14px;
        background: #fffdfa;
      }}
      .label {{
        display: block;
        font-size: 12px;
        color: var(--muted);
        margin-bottom: 6px;
      }}
      code, pre {{
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      }}
      code {{
        word-break: break-all;
      }}
      pre {{
        overflow: auto;
        white-space: pre-wrap;
        word-break: break-word;
        margin: 0;
        padding: 16px;
        border-radius: 14px;
        background: #111827;
        color: #f9fafb;
      }}
      ul {{
        margin: 8px 0 0 18px;
        padding: 0;
      }}
      @media (max-width: 720px) {{
        .grid {{
          grid-template-columns: 1fr;
        }}
      }}
    </style>
  </head>
  <body>
    <div class="container">
      <div class="card">
        {body}
      </div>
    </div>
  </body>
</html>"""
    return document.encode("utf-8")


def render_error_page(title: str, message: str, details: str | None = None) -> bytes:
    body = [
        f"<h1>{html.escape(title)}</h1>",
        f"<div class='notice error'><strong>错误：</strong>{html.escape(message)}</div>",
    ]
    if details:
        body.append("<h2>详情</h2>")
        body.append(f"<pre>{html.escape(details)}</pre>")
    body.append(
        "<div class='actions'>"
        "<a class='button secondary' href='/'>返回首页</a>"
        "<a class='button' href='/login'>重新发起授权</a>"
        "</div>"
    )
    return html_page(title, "".join(body))


def exchange_code_for_token(code: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    form = urllib.parse.urlencode(
        {
            "grant_type": "authorization_code",
            "code": code,
            "client_id": CLIENT_ID,
            "client_secret": CLIENT_SECRET,
            "redirect_uri": build_redirect_uri(),
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        f"{API_BASE_URL}/oauth2/token",
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )

    try:
        with urlopen_with_tls(request, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw), None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            return None, json.loads(raw)
        except json.JSONDecodeError:
            return None, {"error": f"HTTP {exc.code}", "error_description": raw}
    except urllib.error.URLError as exc:
        details = str(exc)
        if "CERTIFICATE_VERIFY_FAILED" in details:
            details = (
                f"{details}. You can set KSUSER_CA_BUNDLE to your CA PEM file, "
                "or place a local-ca-bundle.pem next to this script, "
                "or set KSUSER_INSECURE_SKIP_VERIFY=true for temporary debugging."
            )
        return None, {"error": "network_error", "error_description": details}


def fetch_userinfo(access_token: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    request = urllib.request.Request(
        f"{API_BASE_URL}/oauth2/userinfo",
        headers={"Authorization": f"Bearer {access_token}"},
        method="GET",
    )

    try:
        with urlopen_with_tls(request, timeout=20) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw), None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            return None, json.loads(raw)
        except json.JSONDecodeError:
            return None, {"error": f"HTTP {exc.code}", "error_description": raw}
    except urllib.error.URLError as exc:
        details = str(exc)
        if "CERTIFICATE_VERIFY_FAILED" in details:
            details = (
                f"{details}. You can set KSUSER_CA_BUNDLE to your CA PEM file, "
                "or place a local-ca-bundle.pem next to this script, "
                "or set KSUSER_INSECURE_SKIP_VERIFY=true for temporary debugging."
            )
        return None, {"error": "network_error", "error_description": details}


class DemoHandler(BaseHTTPRequestHandler):
    server_version = "KsuserLocalDemo/1.0"

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)

        if parsed.path == "/":
            self.handle_index()
            return

        if parsed.path == "/login":
            self.handle_login()
            return

        if parsed.path == REDIRECT_PATH:
            self.handle_callback(parsed)
            return

        if parsed.path == "/favicon.ico":
            self.send_response(204)
            self.end_headers()
            return

        self.send_response(404)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(render_error_page("页面不存在", "未找到对应路径"))

    def handle_index(self) -> None:
        redirect_uri = build_redirect_uri()
        missing = []
        if not CLIENT_ID:
            missing.append("KSUSER_CLIENT_ID")
        if not CLIENT_SECRET:
            missing.append("KSUSER_CLIENT_SECRET")

        body = [
            "<h1>Ksuser OAuth2.0 localhost 测试 Demo</h1>",
            "<p class='muted'>这个页面会把浏览器跳到 Ksuser 授权页，完成授权后自动在本地回调，随后展示授权码、Access Token 与 userinfo。</p>",
            "<div class='grid'>",
            f"<div class='field'><span class='label'>授权页</span><code>{html.escape(AUTH_BASE_URL + '/oauth/authorize')}</code></div>",
            f"<div class='field'><span class='label'>Token 接口</span><code>{html.escape(API_BASE_URL + '/oauth2/token')}</code></div>",
            f"<div class='field'><span class='label'>userinfo 接口</span><code>{html.escape(API_BASE_URL + '/oauth2/userinfo')}</code></div>",
            f"<div class='field'><span class='label'>当前回调地址</span><code>{html.escape(redirect_uri)}</code></div>",
            f"<div class='field'><span class='label'>client_id</span><code>{html.escape(CLIENT_ID or '未设置')}</code></div>",
            f"<div class='field'><span class='label'>scope</span><code>{html.escape(SCOPE or '(空)')}</code></div>",
            "</div>",
        ]

        if missing:
            body.append(
                "<div class='notice error'>"
                "<strong>缺少环境变量：</strong>"
                f"{html.escape(', '.join(missing))}<br>"
                "请先按 README 设置变量，再重新启动脚本。"
                "</div>"
            )
        else:
            body.append(
                "<div class='notice success'>"
                "如果你已经在开放平台创建了应用，并把回调地址精确填写为当前页面显示的地址，现在可以直接点击下面按钮发起测试。"
                "</div>"
            )

        body.append("<h2>测试前确认</h2>")
        body.append(
            "<ul>"
            "<li>开放平台里登记的回调地址必须与当前页面显示的回调地址完全一致。</li>"
            "<li>如果使用 HTTP，本系统只允许 <code>http://localhost</code>，不要填 <code>127.0.0.1</code>。</li>"
            "<li>如果你改了端口或回调路径，应用配置也必须一起改。</li>"
            "</ul>"
        )

        body.append(
            "<div class='actions'>"
            "<a class='button' href='/login'>开始 OAuth2 测试</a>"
            "<a class='button secondary' href='/'>刷新配置</a>"
            "</div>"
        )

        self.respond_ok("Ksuser OAuth2 localhost demo", "".join(body))

    def handle_login(self) -> None:
        if not CLIENT_ID or not CLIENT_SECRET:
            self.respond_bytes(render_error_page("配置不完整", "请先设置 AppID 与 AppSecret"))
            return

        cleanup_states()
        state = secrets.token_urlsafe(24)
        VALID_STATES[state] = time.time()

        params = {
            "response_type": "code",
            "client_id": CLIENT_ID,
            "redirect_uri": build_redirect_uri(),
            "state": state,
        }
        if SCOPE:
            params["scope"] = SCOPE

        authorize_url = f"{AUTH_BASE_URL}/oauth/authorize?{urllib.parse.urlencode(params)}"

        self.send_response(302)
        self.send_header("Location", authorize_url)
        self.end_headers()

    def handle_callback(self, parsed: urllib.parse.ParseResult) -> None:
        query = urllib.parse.parse_qs(parsed.query)
        state = (query.get("state") or [""])[0]
        code = (query.get("code") or [""])[0]
        error = (query.get("error") or [""])[0]
        error_description = (query.get("error_description") or [""])[0]

        cleanup_states()

        if error:
            self.respond_bytes(
                render_error_page(
                    "授权被拒绝或失败",
                    error,
                    error_description or None,
                )
            )
            return

        if not state or state not in VALID_STATES:
            self.respond_bytes(render_error_page("state 校验失败", "未找到有效 state，可能已过期或来自非法回调"))
            return
        VALID_STATES.pop(state, None)

        if not code:
            self.respond_bytes(render_error_page("缺少授权码", "回调里没有 code 参数"))
            return

        token_response, token_error = exchange_code_for_token(code)
        if token_error:
            self.respond_bytes(
                render_error_page(
                    "换取 Access Token 失败",
                    token_error.get("error", "unknown_error"),
                    pretty_json(token_error),
                )
            )
            return

        access_token = str(token_response.get("access_token", "")).strip() if token_response else ""
        userinfo_response = None
        userinfo_error = None
        if access_token:
            userinfo_response, userinfo_error = fetch_userinfo(access_token)

        body = [
            "<h1>OAuth2 测试成功</h1>",
            "<div class='notice success'>授权回调已完成，本页已经自动用授权码换取 Access Token，并请求了 userinfo。</div>",
            "<div class='grid'>",
            f"<div class='field'><span class='label'>state</span><code>{html.escape(state)}</code></div>",
            f"<div class='field'><span class='label'>code</span><code>{html.escape(code)}</code></div>",
            f"<div class='field'><span class='label'>openid</span><code>{html.escape(str(token_response.get('openid', '')))}</code></div>",
            f"<div class='field'><span class='label'>unionid</span><code>{html.escape(str(token_response.get('unionid', '')))}</code></div>",
            "</div>",
            "<h2>Token 响应</h2>",
            f"<pre>{html.escape(pretty_json(token_response))}</pre>",
        ]

        body.append("<h2>userinfo 响应</h2>")
        if userinfo_error:
            body.append(f"<pre>{html.escape(pretty_json(userinfo_error))}</pre>")
        else:
            body.append(f"<pre>{html.escape(pretty_json(userinfo_response))}</pre>")

        body.append(
            "<div class='actions'>"
            "<a class='button' href='/login'>再次测试</a>"
            "<a class='button secondary' href='/'>返回首页</a>"
            "</div>"
        )

        self.respond_ok("OAuth2 测试成功", "".join(body))

    def respond_ok(self, title: str, body: str) -> None:
        self.respond_bytes(html_page(title, body))

    def respond_bytes(self, content: bytes) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(content)

    def log_message(self, format: str, *args: Any) -> None:
        sys.stdout.write(
            "[%s] %s\n"
            % (self.log_date_time_string(), format % args)
        )


def main() -> None:
    redirect_uri = build_redirect_uri()
    print("Ksuser OAuth2.0 localhost demo")
    print(f"Bind address : http://{BIND_HOST}:{DEMO_PORT}")
    print(f"Public URL   : http://{PUBLIC_HOST}:{DEMO_PORT}/")
    print(f"Redirect URI : {redirect_uri}")
    print(f"Authorize URL: {AUTH_BASE_URL}/oauth/authorize")
    print(f"Token URL    : {API_BASE_URL}/oauth2/token")
    print(f"Userinfo URL : {API_BASE_URL}/oauth2/userinfo")
    if API_BASE_URL.startswith("https://"):
        if INSECURE_SKIP_VERIFY:
            print("TLS verify  : disabled by KSUSER_INSECURE_SKIP_VERIFY=true (debug only)")
        elif CA_BUNDLE:
            print(f"TLS CA file : {CA_BUNDLE_SOURCE} ({Path(CA_BUNDLE).expanduser()})")
        else:
            print("TLS CA file : system default trust store")
    if SSL_CONTEXT_ERROR:
        print(f"TLS config error: {SSL_CONTEXT_ERROR}")
        print("Please fix KSUSER_CA_BUNDLE or unset it, then restart the demo.")
        return
    if not CLIENT_ID or not CLIENT_SECRET:
        print("Warning: KSUSER_CLIENT_ID or KSUSER_CLIENT_SECRET is not set.")

    server = ThreadingHTTPServer((BIND_HOST, DEMO_PORT), DemoHandler)
    print("Server started. Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
