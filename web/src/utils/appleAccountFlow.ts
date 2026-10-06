import type { AppleLoginResponse } from '@/api/auth'

const STORAGE_KEY = 'ksuser:pending-apple-account'
const BIND_AFTER_LOGIN_KEY = 'ksuser:apple-bind-after-login'

export interface PendingAppleAccount {
  token: string
  canRegister: boolean
  emailConflict: boolean
}

export const savePendingAppleAccount = (response: AppleLoginResponse): void => {
  if (!response.needBind || !response.oauthBindToken?.trim()) {
    throw new Error('Apple 授权信息缺失，请重新登录')
  }
  sessionStorage.removeItem(BIND_AFTER_LOGIN_KEY)
  sessionStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      token: response.oauthBindToken,
      canRegister: response.canRegister === true && !response.emailConflict,
      emailConflict: response.emailConflict === true,
    }),
  )
}

export const readPendingAppleAccount = (): PendingAppleAccount | null => {
  try {
    const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null')
    if (typeof value?.token !== 'string' || !value.token.trim()) return null
    return {
      token: value.token,
      canRegister: value.canRegister === true && value.emailConflict !== true,
      emailConflict: value.emailConflict === true,
    }
  } catch {
    return null
  }
}

export const clearPendingAppleAccount = (): void => {
  sessionStorage.removeItem(STORAGE_KEY)
  sessionStorage.removeItem(BIND_AFTER_LOGIN_KEY)
}

export const requestAppleAccountBinding = (): void => {
  sessionStorage.setItem(BIND_AFTER_LOGIN_KEY, '1')
}

export const isAppleAccountBindingRequested = (): boolean =>
  sessionStorage.getItem(BIND_AFTER_LOGIN_KEY) === '1'
