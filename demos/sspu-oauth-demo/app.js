const state = {
  bootstrap: null,
  toastTimer: null,
};

async function requestBootstrap() {
  const response = await fetch("/api/bootstrap", { cache: "no-store" });
  if (!response.ok) {
    throw new Error("bootstrap_failed");
  }
  return response.json();
}

function showToast(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.hidden = false;
  window.clearTimeout(state.toastTimer);
  state.toastTimer = window.setTimeout(() => {
    toast.hidden = true;
  }, 2600);
}

function formatJson(value) {
  return JSON.stringify(value ?? {}, null, 2);
}

function updateSessionView() {
  const oauth = state.bootstrap?.oauth;
  const status = document.getElementById("oauth-status");
  const otherLoginWays = document.getElementById("other-login-ways");
  const text = document.getElementById("oauth-status-text");
  const modalSubtitle = document.getElementById("oauth-modal-subtitle");
  const summary = document.getElementById("oauth-summary");
  const tokenJson = document.getElementById("token-json");
  const userinfoJson = document.getElementById("userinfo-json");
  const metaJson = document.getElementById("meta-json");

  if (oauth?.session) {
    status.hidden = false;
    otherLoginWays?.classList.add("active");
    text.textContent = `已接入 ${oauth.entryLabel}（${oauth.session.mode === "real" ? "真实回调" : "本地模拟"}）`;
    modalSubtitle.textContent = oauth.session.mode === "real" ? "已完成真实 OAuth2 回调" : "当前使用内置 mock provider";
    summary.innerHTML = `
      <div><strong>Provider：</strong>${oauth.session.provider}</div>
      <div><strong>Mode：</strong>${oauth.session.mode === "real" ? "real" : "mock"}</div>
      <div><strong>State：</strong>${oauth.session.state}</div>
      <div><strong>回调时间：</strong>${new Date((oauth.session.received_at || 0) * 1000).toLocaleString("zh-CN")}</div>
    `;
    tokenJson.textContent = formatJson(oauth.session.token_response);
    userinfoJson.textContent = formatJson(oauth.session.userinfo_response);
    metaJson.textContent = formatJson(oauth.session.oauth_meta);
  } else {
    status.hidden = true;
    otherLoginWays?.classList.remove("active");
    summary.textContent = "";
    tokenJson.textContent = "";
    userinfoJson.textContent = "";
    metaJson.textContent = "";
  }
}

function renderFooter(links, copyright) {
  const footerLinks = document.getElementById("footer-links");
  footerLinks.innerHTML = "";
  links.forEach((item) => {
    const wrapper = document.createElement("div");
    const link = document.createElement("a");
    link.href = item.url;
    link.target = "_blank";
    link.rel = "noreferrer";
    link.textContent = item.name;
    wrapper.appendChild(link);
    footerLinks.appendChild(wrapper);
  });
  document.getElementById("footer-copyright").textContent = copyright;
}

function renderNotice(html) {
  document.getElementById("notice-content").innerHTML = html;
}

function setupTabs() {
  const tabs = document.querySelectorAll(".tab");
  const panels = document.querySelectorAll(".tab-panel");
  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      tabs.forEach((item) => {
        item.classList.toggle("is-active", item === tab);
        item.setAttribute("aria-selected", String(item === tab));
      });
      panels.forEach((panel) => {
        panel.classList.toggle("is-active", panel.dataset.panel === tab.dataset.tab);
      });
    });
  });
}

function setupPasswordToggle() {
  const passwordInput = document.getElementById("password-input");
  const toggleIcon = document.getElementById("password-toggle-icon");
  document.querySelector(".toggle-password")?.addEventListener("click", () => {
    const isPassword = passwordInput.type === "password";
    passwordInput.type = isPassword ? "text" : "password";
    toggleIcon?.classList.toggle("su-icon-hide", !isPassword);
    toggleIcon?.classList.toggle("su-icon-display", isPassword);
  });
}

function setupFormMocks() {
  document.getElementById("password-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    showToast("本页仅模拟统一身份认证 UI，账号密码提交未接线，第三方 OAuth 入口已可用。");
  });
  document.getElementById("sms-form")?.addEventListener("submit", (event) => {
    event.preventDefault();
    showToast("手机验证码登录在此页保留原版占位，演示重点是“其它方式登录”中的第三方接入。");
  });
  document.querySelector(".secondary-inline")?.addEventListener("click", () => {
    showToast("这是本地模拟页，短信发送能力未接线。");
  });
}

function setupThirdPartyActions() {
  document.querySelectorAll(".login-way[data-provider]").forEach((button) => {
    button.addEventListener("click", () => {
      const provider = button.dataset.provider;
      if (provider === "wechat") {
        showToast("已保留微信入口占位；新增的 Ksuser OAuth 入口已接入本地演示流程。");
      } else if (provider === "workweixin") {
        showToast("已保留企业微信入口占位；新增的 Ksuser OAuth 入口已接入本地演示流程。");
      }
    });
  });

  document.getElementById("ksuser-entry")?.addEventListener("click", () => {
    const oauth = state.bootstrap?.oauth;
    const modeText = oauth?.upstreamAvailable && oauth?.configured ? "真实 OAuth" : "mock OAuth";
    showToast(`正在跳转到 ${modeText} 流程…`);
    window.setTimeout(() => {
      window.location.href = oauth?.startUrl || "/oauth/start";
    }, 220);
  });
}

function setModalOpen(open) {
  document.getElementById("oauth-modal").hidden = !open;
}

function setupModal() {
  document.getElementById("view-oauth-result")?.addEventListener("click", () => {
    setModalOpen(true);
  });
  document.querySelectorAll("[data-close-modal='true']").forEach((element) => {
    element.addEventListener("click", () => setModalOpen(false));
  });
  document.getElementById("clear-session")?.addEventListener("click", async () => {
    await fetch("/api/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{}",
    });
    state.bootstrap.oauth.session = null;
    state.bootstrap.oauth.error = null;
    updateSessionView();
    setModalOpen(false);
    showToast("OAuth 状态已清除。");
  });
}

function processUrlState() {
  const params = new URLSearchParams(window.location.search);
  if (params.get("oauth") === "success" && state.bootstrap?.oauth?.session) {
    setModalOpen(true);
    showToast(`已完成 ${state.bootstrap.oauth.session.mode === "real" ? "真实" : "模拟"} OAuth 回调。`);
  }
  if (params.get("oauth") === "error" && state.bootstrap?.oauth?.error) {
    const error = state.bootstrap.oauth.error;
    showToast(`OAuth 登录失败：${error.error_description || error.error || "unknown_error"}`);
  }
  if (params.has("oauth")) {
    const cleanUrl = new URL(window.location.href);
    cleanUrl.searchParams.delete("oauth");
    window.history.replaceState({}, "", cleanUrl.toString());
  }
}

async function bootstrap() {
  try {
    state.bootstrap = await requestBootstrap();
    renderNotice(state.bootstrap.app.noticeHtml);
    renderFooter(state.bootstrap.app.footerLinks, state.bootstrap.app.copyright);
    if (state.bootstrap.oauth?.sslContextError) {
      showToast(`OAuth TLS 配置异常：${state.bootstrap.oauth.sslContextError}`);
    }
    updateSessionView();
    processUrlState();
  } catch (error) {
    showToast("页面初始化失败，请刷新后重试。");
  }
}

setupTabs();
setupPasswordToggle();
setupFormMocks();
setupThirdPartyActions();
setupModal();
bootstrap();
