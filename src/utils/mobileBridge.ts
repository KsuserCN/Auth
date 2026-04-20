import {
  createMobileBridgeChallenge,
  getMobileBridgeStatus,
  type MobileBridgeCreateResponse,
  type MobileBridgeStatusResponse,
} from '@/api/auth'

const MOBILE_BRIDGE_CHALLENGE_QUERY_KEY = 'mobileBridgeChallengeId'
const MOBILE_BRIDGE_FALLBACK_QUERY_KEY = 'mobileBridgeFallback'

export const isAndroidMobileBridgeSupported = (): boolean => {
  if (typeof navigator === 'undefined') {
    return false
  }
  return /Android/i.test(navigator.userAgent || '')
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
