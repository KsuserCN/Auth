import { createAppleChallenge, submitAppleCredential, verifyAppleSensitive } from '@/api/auth'
import type { AppleLoginResponse } from '@/api/auth'

type AppleAuthorization = {
  authorization?: { code?: string; id_token?: string; state?: string }
  user?: { name?: { firstName?: string; lastName?: string } }
}

type AppleIDApi = {
  auth: {
    init: (config: Record<string, unknown>) => void
    signIn: () => Promise<AppleAuthorization>
  }
}

declare global {
  interface Window {
    AppleID?: AppleIDApi
  }
}

let scriptPromise: Promise<AppleIDApi> | null = null

const loadAppleID = (): Promise<AppleIDApi> => {
  if (window.AppleID) return Promise.resolve(window.AppleID)
  if (!scriptPromise) {
    scriptPromise = new Promise<AppleIDApi>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = 'https://appleid.cdn-apple.com/appleauth/static/jsapi/appleid/1/en_US/appleid.auth.js'
      script.async = true
      script.onload = () => window.AppleID ? resolve(window.AppleID) : reject(new Error('Apple 登录组件加载失败'))
      script.onerror = () => reject(new Error('Apple 登录组件加载失败'))
      document.head.appendChild(script)
    }).catch((error) => {
      scriptPromise = null
      throw error
    })
  }
  return scriptPromise!
}

export const prepareAppleSignIn = async (): Promise<void> => {
  if (import.meta.env.VITE_APPLE_CLIENT_ID && import.meta.env.VITE_APPLE_REDIRECT_URI) {
    await loadAppleID()
  }
}

const authorizeWithApple = async (purpose: 'login' | 'bind' | 'sensitive') => {
  const clientId = import.meta.env.VITE_APPLE_CLIENT_ID?.trim()
  const redirectURI = import.meta.env.VITE_APPLE_REDIRECT_URI?.trim()
  if (!clientId || !redirectURI) throw new Error('Apple 网页登录尚未配置')

  const appleID = await loadAppleID()
  const challenge = await createAppleChallenge(purpose)
  appleID.auth.init({
    clientId,
    redirectURI,
    scope: 'name email',
    state: challenge.state,
    nonce: challenge.nonce,
    usePopup: true,
  })

  const result = await appleID.auth.signIn()
  const authorization = result.authorization
  if (!authorization?.code || !authorization.id_token || authorization.state !== challenge.state) {
    throw new Error('Apple 授权信息不完整或状态校验失败，请重试')
  }
  return {
    challengeId: challenge.challengeId,
    authorizationCode: authorization.code,
    identityToken: authorization.id_token,
    state: authorization.state,
    givenName: result.user?.name?.firstName,
    familyName: result.user?.name?.lastName,
  }
}

export const signInWithApple = async (purpose: 'login' | 'bind'): Promise<AppleLoginResponse> =>
  submitAppleCredential(await authorizeWithApple(purpose))

export const verifySensitiveWithApple = async (): Promise<void> => {
  await verifyAppleSensitive(await authorizeWithApple('sensitive'))
}
