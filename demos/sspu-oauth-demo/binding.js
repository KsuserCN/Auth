function firstNonEmpty(...values) {
  return values.find((value) => typeof value === "string" && value.trim())?.trim() || "";
}

function setAvatarImage(element, src, alt) {
  if (!element) {
    return;
  }

  element.alt = alt;
  element.onerror = () => {
    element.onerror = null;
    element.src = "/assets/avatar.svg";
  };
  element.src = src || "/assets/avatar.svg";
}

async function requestBootstrap() {
  const response = await fetch("/api/bootstrap", { cache: "no-store" });
  if (!response.ok) {
    throw new Error("bootstrap_failed");
  }
  return response.json();
}

async function bootstrap() {
  try {
    const bootstrapData = await requestBootstrap();
    if (!bootstrapData?.oauth?.session) {
      window.location.replace("/");
      return;
    }

    const session = bootstrapData.oauth.session;
    const userinfo =
      session.userinfo_response && typeof session.userinfo_response === "object"
        ? session.userinfo_response
        : {};

    const displayName = firstNonEmpty(
      userinfo.nickname,
      userinfo.name,
      userinfo.preferred_username,
      userinfo.username,
      userinfo.email,
      "金阳",
    );
    const avatarUrl = firstNonEmpty(
      userinfo.avatar_url,
      userinfo.avatar,
      userinfo.picture,
      "/assets/avatar.svg",
    );
    const email = firstNonEmpty(userinfo.email, session.email);
    const accountId = firstNonEmpty(email, userinfo.preferred_username, userinfo.username, displayName, "Ksuser 账号");

    const topbarUserName = document.getElementById("topbar-user-name");
    const sidebarName = document.getElementById("sidebar-name");
    const sidebarEmail = document.getElementById("sidebar-email");
    const topbarUserAvatar = document.getElementById("topbar-user-avatar");
    const sidebarAvatar = document.getElementById("sidebar-avatar");
    const ksuserDesc = document.getElementById("ksuser-binding-desc");

    if (topbarUserName) {
      topbarUserName.textContent = displayName;
    }

    if (sidebarName) {
      sidebarName.textContent = displayName;
    }

    if (sidebarEmail) {
      sidebarEmail.textContent = email;
      sidebarEmail.hidden = !email;
    }

    setAvatarImage(topbarUserAvatar, avatarUrl, `${displayName}头像`);
    setAvatarImage(sidebarAvatar, avatarUrl, displayName);

    if (ksuserDesc) {
      ksuserDesc.textContent = `您已绑定 ${accountId}，可通过 Ksuser 账号一键登录`;
    }

    const cleanUrl = new URL(window.location.href);
    cleanUrl.searchParams.delete("oauth");
    window.history.replaceState({}, "", cleanUrl.toString());
  } catch (error) {
    window.location.replace("/");
  }
}

bootstrap();
