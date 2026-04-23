#!/usr/bin/env python3
from __future__ import annotations

import atexit
import html
import json
import mimetypes
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
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
ASSETS_DIR = ROOT / "assets"
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
STATIC_FILES = {
    "/": ROOT / "index.html",
    "/index.html": ROOT / "index.html",
    "/style.css": ROOT / "style.css",
    "/app.js": ROOT / "app.js",
    "/binding.html": ROOT / "binding.html",
    "/binding.css": ROOT / "binding.css",
    "/binding.js": ROOT / "binding.js",
}
ENV_CANDIDATES = [ROOT / ".env", ROOT.parent / "oauth-local-demo" / ".env"]

STATE_TTL_SECONDS = 600
PENDING_REQUESTS: dict[str, dict[str, Any]] = {}
SESSION: dict[str, Any] = {"result": None, "error": None}


def load_dotenv() -> None:
    for env_path in ENV_CANDIDATES:
        if not env_path.exists():
            continue
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
DEMO_PORT = int(os.getenv("KSUSER_SSPU_DEMO_PORT", "9003"))
REDIRECT_PATH = os.getenv("KSUSER_SSPU_REDIRECT_PATH", "/oauth/callback").strip() or "/oauth/callback"
CLIENT_ID = os.getenv("KSUSER_CLIENT_ID", "").strip()
CLIENT_SECRET = os.getenv("KSUSER_CLIENT_SECRET", "").strip()
SCOPE = os.getenv("KSUSER_SCOPE", "profile email").strip() or "profile email"
INSECURE_SKIP_VERIFY = os.getenv("KSUSER_INSECURE_SKIP_VERIFY", "").strip().lower() in {
    "1",
    "true",
    "yes",
    "on",
}

NOTICE_CONTENT = (
    "🔐 为加强账户防护，请每180天更换一次登录密码，新密码需包含大小写字母、数字及特殊符号"
    "（如!@#），长度不低于8位，且避免与历史密码重复。<br>若手机号变更请点击右侧「账号激活」完成验证。"
)
FOOTER_LINKS = [
    {"name": "认证开发", "url": "https://id110.sspu.edu.cn/docs/authx/index.html#/"},
    {"name": "校园VPN", "url": "https://itc.sspu.edu.cn/2017/1011/c1662a117899/page.htm"},
    {"name": "电子邮箱", "url": "https://www.sspu.edu.cn/tzgq/94250.htm"},
    {"name": "信息服务", "url": "https://itc.sspu.edu.cn/2022/0709/c1662a30575/page.htm"},
]


def normalize_redirect_path(value: str) -> str:
    return value if value.startswith("/") else f"/{value}"


REDIRECT_PATH = normalize_redirect_path(REDIRECT_PATH)


def build_public_base() -> str:
    return f"http://{PUBLIC_HOST}:{DEMO_PORT}"


def build_redirect_uri() -> str:
    return f"{build_public_base()}{REDIRECT_PATH}"


def cleanup_requests() -> None:
    now = time.time()
    expired = [
        state
        for state, item in PENDING_REQUESTS.items()
        if now - float(item["created_at"]) > STATE_TTL_SECONDS
    ]
    for state in expired:
        PENDING_REQUESTS.pop(state, None)


def pretty_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2)


def json_response_bytes(payload: dict[str, Any]) -> bytes:
    return json.dumps(payload, ensure_ascii=False).encode("utf-8")


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
        fd, path = tempfile.mkstemp(prefix="sspu-ca-", suffix=".pem")
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
    if not API_BASE_URL.startswith("https://") and not AUTH_BASE_URL.startswith("https://"):
        return None
    if INSECURE_SKIP_VERIFY:
        return ssl._create_unverified_context()
    if not CA_BUNDLE:
        return ssl.create_default_context()
    ca_file = Path(CA_BUNDLE).expanduser()
    if not ca_file.exists():
        raise FileNotFoundError(f"CA bundle file not found: {ca_file}")
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


def http_json(url: str, method: str = "GET", data: bytes | None = None, headers: dict[str, str] | None = None) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
    request = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with urlopen_with_tls(request, timeout=8) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}, None
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
                f"{details}. Configure KSUSER_CA_BUNDLE, SSL_CERT_FILE, "
                f"or place a local-ca-bundle.pem file next to server.py."
            )
        return None, {"error": "network_error", "error_description": details}


def real_oauth_ready() -> bool:
    return bool(CLIENT_ID and CLIENT_SECRET and AUTH_BASE_URL and API_BASE_URL and not SSL_CONTEXT_ERROR)


def build_authorize_url(state: str, force_mock: bool) -> tuple[str, str]:
    common_params = {
        "response_type": "code",
        "client_id": CLIENT_ID or "sspu-local-mock-client",
        "redirect_uri": build_redirect_uri(),
        "scope": SCOPE,
        "state": state,
    }
    upstream_ready = real_oauth_ready()
    if upstream_ready and not force_mock:
        return f"{AUTH_BASE_URL}/oauth/authorize?{urllib.parse.urlencode(common_params)}", "real"
    mock_params = common_params | {"mode": "mock"}
    return f"{build_public_base()}/mock-provider/authorize?{urllib.parse.urlencode(mock_params)}", "mock"


def create_mock_auth_result(state: str) -> dict[str, Any]:
    now = int(time.time())
    userinfo = {
        "openid": "ksuser-openid-demo-10001",
        "unionid": "ksuser-unionid-demo-10001",
        "preferred_username": "20230010001",
        "name": "Ksuser Demo",
        "email": "demo@ksuser.cn",
        "email_verified": True,
        "picture": "",
        "tenant": "Shanghai Polytechnical University",
    }
    token_response = {
        "access_token": secrets.token_urlsafe(32),
        "token_type": "Bearer",
        "expires_in": 3600,
        "scope": SCOPE,
        "refresh_token": secrets.token_urlsafe(32),
        "openid": userinfo["openid"],
        "unionid": userinfo["unionid"],
    }
    return {
        "provider": "Ksuser OAuth",
        "mode": "mock",
        "received_at": now,
        "state": state,
        "token_response": token_response,
        "userinfo_response": userinfo,
        "oauth_meta": {
            "authorization_endpoint": f"{build_public_base()}/mock-provider/authorize",
            "token_endpoint": f"{build_public_base()}/mock-provider/token",
            "userinfo_endpoint": f"{build_public_base()}/mock-provider/userinfo",
            "scope": SCOPE,
            "redirect_uri": build_redirect_uri(),
        },
    }


def html_page(title: str, body: str) -> bytes:
    document = f"""<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>{html.escape(title)}</title>
    <link rel="icon" href="/assets/favicon.ico">
    <style>
      :root {{
        color-scheme: light;
        --bg: #f2f6fb;
        --card: rgba(255,255,255,.86);
        --line: rgba(12,71,132,.14);
        --text: #1f2d3d;
        --muted: #6b7785;
        --brand: #0e6ccb;
        --brand-dark: #09539a;
      }}
      * {{ box-sizing: border-box; }}
      body {{
        margin: 0;
        min-height: 100vh;
        background:
          linear-gradient(180deg, rgba(240,247,255,.95), rgba(240,247,255,.72)),
          url("/assets/wenli.png") center/420px auto repeat;
        color: var(--text);
        font: 15px/1.7 "PingFang SC", "Microsoft YaHei", sans-serif;
        display: grid;
        place-items: center;
        padding: 24px;
      }}
      .dialog {{
        width: min(560px, 100%);
        background: var(--card);
        border: 1px solid var(--line);
        border-radius: 18px;
        padding: 28px;
        box-shadow: 0 24px 80px rgba(14, 67, 126, .12);
        backdrop-filter: blur(12px);
      }}
      .brand {{
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 20px;
      }}
      .brand img {{ height: 54px; }}
      h1 {{
        margin: 0 0 8px;
        font-size: 26px;
      }}
      p {{
        margin: 0 0 10px;
        color: var(--muted);
      }}
      .box {{
        padding: 16px 18px;
        border-radius: 14px;
        background: rgba(255,255,255,.72);
        border: 1px solid var(--line);
        margin: 18px 0;
      }}
      .actions {{
        display: flex;
        gap: 12px;
        margin-top: 22px;
      }}
      .button {{
        display: inline-flex;
        justify-content: center;
        align-items: center;
        min-width: 122px;
        min-height: 42px;
        padding: 0 20px;
        border-radius: 999px;
        border: 1px solid transparent;
        text-decoration: none;
        font-weight: 600;
        cursor: pointer;
      }}
      .button.primary {{
        color: #fff;
        background: var(--brand);
      }}
      .button.secondary {{
        color: var(--brand);
        background: rgba(255,255,255,.9);
        border-color: rgba(14,108,203,.18);
      }}
      code, pre {{
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      }}
      pre {{
        white-space: pre-wrap;
        word-break: break-word;
        background: #0f172a;
        color: #f8fafc;
        padding: 16px;
        border-radius: 12px;
        font-size: 13px;
      }}
    </style>
  </head>
  <body>
    {body}
  </body>
</html>"""
    return document.encode("utf-8")


class DemoHandler(BaseHTTPRequestHandler):
    server_version = "SspuOauthMock/1.0"

    def do_GET(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        if path in STATIC_FILES:
            self.serve_file(STATIC_FILES[path])
            return
        if path.startswith("/assets/"):
            self.serve_file(ASSETS_DIR / path.removeprefix("/assets/"))
            return
        if path == "/api/bootstrap":
            self.handle_bootstrap()
            return
        if path == "/oauth/start":
            self.handle_oauth_start(parsed)
            return
        if path == REDIRECT_PATH:
            self.handle_oauth_callback(parsed)
            return
        if path == "/security-center/login-config":
            self.handle_binding_page()
            return
        if path == "/mock-provider/authorize":
            self.handle_mock_authorize(parsed)
            return
        if path == "/session/clear":
            SESSION["result"] = None
            SESSION["error"] = None
            self.redirect("/")
            return
        if path == "/favicon.ico":
            self.redirect("/assets/favicon.ico")
            return

        self.send_error(HTTPStatus.NOT_FOUND, "Not Found")

    def do_POST(self) -> None:
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path == "/api/logout":
            SESSION["result"] = None
            SESSION["error"] = None
            self.respond_json({"ok": True})
            return
        self.send_error(HTTPStatus.NOT_FOUND, "Not Found")

    def handle_bootstrap(self) -> None:
        result = SESSION["result"]
        error = SESSION["error"]
        upstream_available = real_oauth_ready()
        payload = {
            "app": {
                "title": "统一身份认证",
                "schoolName": "上海第二工业大学",
                "copyright": "上海第二工业大学信息技术中心",
                "noticeHtml": NOTICE_CONTENT,
                "footerLinks": FOOTER_LINKS,
            },
            "oauth": {
                "configured": bool(CLIENT_ID and CLIENT_SECRET),
                "upstreamAvailable": upstream_available,
                "mode": "real" if upstream_available else "mock",
                "entryLabel": "Ksuser OAuth",
                "startUrl": "/oauth/start",
                "session": result,
                "error": error,
                "sslContextError": SSL_CONTEXT_ERROR,
            },
        }
        self.respond_json(payload)

    def handle_oauth_start(self, parsed: urllib.parse.ParseResult) -> None:
        cleanup_requests()
        query = urllib.parse.parse_qs(parsed.query)
        force_mock = (query.get("mode") or [""])[0] == "mock"

        state = secrets.token_urlsafe(24)
        authorize_url, mode = build_authorize_url(state, force_mock)

        PENDING_REQUESTS[state] = {
            "created_at": time.time(),
            "mode": mode,
        }
        self.redirect(authorize_url)

    def handle_oauth_callback(self, parsed: urllib.parse.ParseResult) -> None:
        cleanup_requests()
        query = urllib.parse.parse_qs(parsed.query)
        state = (query.get("state") or [""])[0]
        code = (query.get("code") or [""])[0]
        error = (query.get("error") or [""])[0]
        error_description = (query.get("error_description") or [""])[0]

        SESSION["error"] = None

        if error:
            SESSION["result"] = None
            SESSION["error"] = {"error": error, "error_description": error_description or "OAuth 登录被取消或失败。"}
            self.redirect("/?oauth=error")
            return
        if not state or state not in PENDING_REQUESTS:
            SESSION["result"] = None
            SESSION["error"] = {"error": "invalid_state", "error_description": "state 校验失败，可能已过期。"}
            self.redirect("/?oauth=error")
            return
        if not code:
            SESSION["result"] = None
            SESSION["error"] = {"error": "missing_code", "error_description": "回调中缺少授权码。"}
            self.redirect("/?oauth=error")
            return

        request_context = PENDING_REQUESTS.pop(state)
        if str(request_context["mode"]) == "mock" or code.startswith("mock-"):
            SESSION["result"] = create_mock_auth_result(state)
            self.redirect("/security-center/login-config?oauth=success")
            return

        token_response, token_error = self.exchange_code_for_token(code)
        if token_error:
            SESSION["result"] = None
            SESSION["error"] = token_error
            self.redirect("/?oauth=error")
            return

        access_token = str((token_response or {}).get("access_token", "")).strip()
        userinfo_response, userinfo_error = self.fetch_userinfo(access_token) if access_token else (None, {"error": "missing_access_token"})

        SESSION["result"] = {
            "provider": "Ksuser OAuth",
            "mode": "real",
            "received_at": int(time.time()),
            "state": state,
            "token_response": token_response,
            "userinfo_response": userinfo_error or userinfo_response,
            "oauth_meta": {
                "authorization_endpoint": f"{AUTH_BASE_URL}/oauth/authorize",
                "token_endpoint": f"{API_BASE_URL}/oauth2/token",
                "userinfo_endpoint": f"{API_BASE_URL}/oauth2/userinfo",
                "scope": SCOPE,
                "redirect_uri": build_redirect_uri(),
            },
        }
        self.redirect("/security-center/login-config?oauth=success")

    def handle_binding_page(self) -> None:
        if not SESSION["result"]:
            self.redirect("/")
            return
        self.serve_file(ROOT / "binding.html")

    def handle_mock_authorize(self, parsed: urllib.parse.ParseResult) -> None:
        query = urllib.parse.parse_qs(parsed.query)
        client_id = (query.get("client_id") or ["sspu-local-mock-client"])[0]
        scope = (query.get("scope") or [SCOPE])[0]
        redirect_uri = (query.get("redirect_uri") or [build_redirect_uri()])[0]
        state = (query.get("state") or [""])[0]
        action = (query.get("action") or [""])[0]

        if action == "approve":
            approval_url = f"{redirect_uri}?{urllib.parse.urlencode({'code': 'mock-' + secrets.token_urlsafe(12), 'state': state})}"
            self.redirect(approval_url)
            return
        if action == "cancel":
            cancel_url = f"{redirect_uri}?{urllib.parse.urlencode({'error': 'access_denied', 'error_description': '用户取消了模拟授权', 'state': state})}"
            self.redirect(cancel_url)
            return

        body = f"""
        <div class="dialog">
          <div class="brand">
            <img src="/assets/logo.png" alt="上海第二工业大学">
            <div>
              <h1>模拟第三方授权</h1>
              <p>当前未检测到本地 OAuth 服务，已自动切换为内置 mock provider。</p>
            </div>
          </div>
          <div class="box">
            <strong>应用名称：</strong>SSPU 第三方登录接入演示<br>
            <strong>Client ID：</strong>{html.escape(client_id)}<br>
            <strong>Scope：</strong>{html.escape(scope)}<br>
            <strong>Redirect URI：</strong>{html.escape(redirect_uri)}
          </div>
          <div class="box">
            将模拟以 <strong>Ksuser Demo（20230010001）</strong> 的身份授权，并回传 OAuth2 token 与 userinfo。
          </div>
          <div class="actions">
            <a class="button primary" href="/mock-provider/authorize?{urllib.parse.urlencode({'client_id': client_id, 'scope': scope, 'redirect_uri': redirect_uri, 'state': state, 'action': 'approve'})}">同意并继续</a>
            <a class="button secondary" href="/mock-provider/authorize?{urllib.parse.urlencode({'client_id': client_id, 'scope': scope, 'redirect_uri': redirect_uri, 'state': state, 'action': 'cancel'})}">取消</a>
          </div>
        </div>
        """
        self.respond_bytes(html_page("模拟第三方授权", body), "text/html; charset=utf-8")

    def exchange_code_for_token(self, code: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
        form = urllib.parse.urlencode(
            {
                "grant_type": "authorization_code",
                "code": code,
                "client_id": CLIENT_ID,
                "client_secret": CLIENT_SECRET,
                "redirect_uri": build_redirect_uri(),
            }
        ).encode("utf-8")
        return http_json(
            f"{API_BASE_URL}/oauth2/token",
            method="POST",
            data=form,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )

    def fetch_userinfo(self, access_token: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
        return http_json(
            f"{API_BASE_URL}/oauth2/userinfo",
            headers={"Authorization": f"Bearer {access_token}"},
        )

    def serve_file(self, path: Path) -> None:
        if not path.exists() or not path.is_file():
            self.send_error(HTTPStatus.NOT_FOUND, "Not Found")
            return
        mime, _ = mimetypes.guess_type(str(path))
        self.respond_bytes(path.read_bytes(), mime or "application/octet-stream")

    def respond_json(self, payload: dict[str, Any], status: int = 200) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(json_response_bytes(payload))

    def respond_bytes(self, content: bytes, content_type: str) -> None:
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(content)

    def redirect(self, location: str) -> None:
        self.send_response(302)
        self.send_header("Location", location)
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def log_message(self, format: str, *args: Any) -> None:
        sys.stdout.write("[%s] %s\n" % (self.log_date_time_string(), format % args))


def main() -> None:
    server = ThreadingHTTPServer((BIND_HOST, DEMO_PORT), DemoHandler)
    print("SSPU OAuth mock demo")
    print(f"Bind address : http://{BIND_HOST}:{DEMO_PORT}")
    print(f"Public URL   : {build_public_base()}/")
    print(f"Redirect URI : {build_redirect_uri()}")
    print(f"Auth base    : {AUTH_BASE_URL}")
    print(f"API base     : {API_BASE_URL}")
    print(f"Real OAuth   : {'enabled' if CLIENT_ID and CLIENT_SECRET else 'disabled, using mock fallback'}")
    if CA_BUNDLE:
        print(f"CA bundle    : {CA_BUNDLE_SOURCE} ({CA_BUNDLE})")
    else:
        print("CA bundle    : system default")
    if SSL_CONTEXT_ERROR:
        print(f"SSL warning  : {SSL_CONTEXT_ERROR}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
