const ACCESS_TOKEN_KEY = 'accessToken'
const USER_KEY = 'user'

export const getStoredAccessToken = (): string | null => {
  return sessionStorage.getItem(ACCESS_TOKEN_KEY) || localStorage.getItem(ACCESS_TOKEN_KEY)
}

export const setStoredAccessToken = (accessToken: string): void => {
  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
}

export const clearStoredAccessToken = (): void => {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(ACCESS_TOKEN_KEY)
}

export const getStoredUser = (): string | null => {
  return sessionStorage.getItem(USER_KEY) || localStorage.getItem(USER_KEY)
}

export const setStoredUser = (user: unknown): void => {
  const serialized = JSON.stringify(user)
  sessionStorage.setItem(USER_KEY, serialized)
  localStorage.setItem(USER_KEY, serialized)
}

export const clearStoredUser = (): void => {
  sessionStorage.removeItem(USER_KEY)
  localStorage.removeItem(USER_KEY)
}

export const storeAuthSession = (accessToken: string, user?: unknown): void => {
  setStoredAccessToken(accessToken)
  if (user != null) {
    setStoredUser(user)
    return
  }
  clearStoredUser()
}

export const clearAuthSession = (): void => {
  clearStoredAccessToken()
  clearStoredUser()
}

export const hydrateSessionStorageFromSharedStorage = (): boolean => {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_KEY)
  if (!accessToken) {
    return false
  }

  sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  const user = localStorage.getItem(USER_KEY)
  if (user != null) {
    sessionStorage.setItem(USER_KEY, user)
  }
  return true
}
