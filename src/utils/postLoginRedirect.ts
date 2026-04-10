const POST_LOGIN_REDIRECT_KEY = 'ksuser:post-login-redirect'

export const normalizePostLoginRedirect = (rawValue?: string | null): string | null => {
  if (!rawValue) {
    return null
  }

  const raw = rawValue.trim()
  if (!raw) {
    return null
  }

  if (raw.startsWith('/')) {
    return raw
  }

  try {
    const url = new URL(raw, window.location.origin)
    if (url.origin !== window.location.origin) {
      return null
    }
    return `${url.pathname}${url.search}${url.hash}`
  } catch {
    return null
  }
}

export const persistPostLoginRedirect = (rawValue?: string | null): void => {
  const normalized = normalizePostLoginRedirect(rawValue)
  if (!normalized) {
    sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
    return
  }
  sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, normalized)
}

export const consumePostLoginRedirect = (): string | null => {
  const normalized = normalizePostLoginRedirect(sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY))
  sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY)
  return normalized
}
