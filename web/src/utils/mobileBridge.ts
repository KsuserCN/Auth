import {
  createMobileBridgeChallenge,
  getMobileBridgeStatus,
  type MobileBridgeCreateResponse,
  type MobileBridgeStatusResponse,
} from '@/api/auth'
import { isIOSAuthorizationSupported } from '@/utils/mobileAuthorization'

const MOBILE_BRIDGE_CHALLENGE_QUERY_KEY = 'mobileBridgeChallengeId'
const MOBILE_BRIDGE_FALLBACK_QUERY_KEY = 'mobileBridgeFallback'
const ANDROID_APP_PACKAGE = 'cn.ksuser.auth'

export const isAndroidMobileBridgeSupported = (): boolean => {
  if (typeof navigator === 'undefined') {
    return false
  }
  return /Android/i.test(navigator.userAgent || '')
}

export const isIOSMobileBridgeSupported = (): boolean => {
  return typeof navigator !== 'undefined' && isIOSAuthorizationSupported()
}

export const buildIOSMobileBridgeAppUrl = (challengeId: string): string => {
  const url = new URL('ksuserauth://bridge-login')
  url.searchParams.set('challengeId', challengeId)
  if (/CriOS\//i.test(navigator.userAgent)) {
    url.searchParams.set('returnBrowser', 'chrome')
  }
  return url.toString()
}

export const isWeChatInAppBrowser = (): boolean => {
  if (typeof navigator === 'undefined') {
    return false
  }
  return /MicroMessenger/i.test(navigator.userAgent || '')
}

export const getMobileBridgeChallengeIdFromUrl = (url: URL): string => {
  return url.searchParams.get(MOBILE_BRIDGE_CHALLENGE_QUERY_KEY)?.trim() || ''
}

export const buildMobileBridgeReturnUrl = (challengeId: string): string => {
  const url = new URL(window.location.href)
  url.searchParams.set(MOBILE_BRIDGE_CHALLENGE_QUERY_KEY, challengeId)
  url.searchParams.delete(MOBILE_BRIDGE_FALLBACK_QUERY_KEY)
  url.searchParams.delete('transferCode')
  return url.toString()
}

export const createMobileBridgeLogin = async (): Promise<MobileBridgeCreateResponse> => {
  const clientNonce =
    window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
  const bootstrapReturnUrl = buildMobileBridgeReturnUrl('pending')
  const created = await createMobileBridgeChallenge(bootstrapReturnUrl, clientNonce)
  return created
}

export const replaceMobileBridgeChallengeInReturnUrl = (
  rawReturnUrl: string,
  challengeId: string,
): string => {
  const url = new URL(rawReturnUrl)
  url.searchParams.set(MOBILE_BRIDGE_CHALLENGE_QUERY_KEY, challengeId)
  url.searchParams.delete(MOBILE_BRIDGE_FALLBACK_QUERY_KEY)
  url.searchParams.delete('transferCode')
  return url.toString()
}

export const launchMobileBridgeApp = (appLink: string): void => {
  if (isIOSMobileBridgeSupported()) {
    const challengeId = new URL(appLink).searchParams.get('challengeId')
    if (!challengeId || !/^[A-Za-z0-9_-]{32}$/.test(challengeId)) {
      throw new Error('App 登录请求无效，请重新发起')
    }
    window.location.assign(buildIOSMobileBridgeAppUrl(challengeId))
    return
  }

  if (!isAndroidMobileBridgeSupported()) {
    window.location.assign(appLink)
    return
  }

  const intentUrl = buildAndroidIntentUrl(appLink)
  if (intentUrl) {
    window.location.assign(intentUrl)
    return
  }

  window.location.assign(appLink)
}

export const fetchMobileBridgeStatus = async (
  challengeId: string,
): Promise<MobileBridgeStatusResponse> => {
  return getMobileBridgeStatus(challengeId)
}

export const stripMobileBridgeQuery = (url: URL): URL => {
  const next = new URL(url.toString())
  next.searchParams.delete(MOBILE_BRIDGE_CHALLENGE_QUERY_KEY)
  next.searchParams.delete(MOBILE_BRIDGE_FALLBACK_QUERY_KEY)
  return next
}

export const readMobileBridgeFallbackFlag = (url: URL): boolean => {
  return url.searchParams.get(MOBILE_BRIDGE_FALLBACK_QUERY_KEY) === '1'
}

const buildAndroidIntentUrl = (rawUrl: string): string | null => {
  try {
    const url = new URL(rawUrl)
    const hostAndPath = `${url.host}${url.pathname}${url.search}`
    const fallbackUrl = encodeURIComponent(rawUrl)
    return `intent://${hostAndPath}#Intent;scheme=${url.protocol.replace(':', '')};package=${ANDROID_APP_PACKAGE};S.browser_fallback_url=${fallbackUrl};end`
  } catch {
    return null
  }
}
