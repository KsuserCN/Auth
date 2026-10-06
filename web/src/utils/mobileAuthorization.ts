import request from '@/utils/request'
import type { SSOAuthorizeApproveRequest } from '@/api/sso'

export interface MobileAuthorizationTicket {
  ticket: string
  secret: string
  appLink: string
  expiresInSeconds: number
}
export interface MobileAuthorizationResult {
  status: 'pending' | 'completed'
  redirectUrl?: string
}

export const isIOSAuthorizationSupported = (): boolean =>
  /iPhone|iPad|iPod/i.test(navigator.userAgent) ||
  (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)

export const createMobileAuthorization = async (
  mode: 'oauth' | 'sso',
  authorization: SSOAuthorizeApproveRequest,
): Promise<MobileAuthorizationTicket> => {
  const response = await request.post('/auth/mobile-authorization/create', {
    mode,
    returnOrigin: window.location.origin,
    authorization,
  })
  return response.data as MobileAuthorizationTicket
}
export const consumeMobileAuthorization = async (
  ticket: Pick<MobileAuthorizationTicket, 'ticket' | 'secret'>,
): Promise<MobileAuthorizationResult> => {
  const response = await request.post('/auth/mobile-authorization/consume', ticket)
  return response.data as MobileAuthorizationResult
}
export const cancelMobileAuthorization = async (
  ticket: Pick<MobileAuthorizationTicket, 'ticket' | 'secret'>,
): Promise<void> => {
  await request.post('/auth/mobile-authorization/cancel', ticket)
}

export const readMobileAuthorizationReturn = (hash: string) => {
  const params = new URLSearchParams(hash.replace(/^#/, ''))
  const ticket = params.get('ticket') || ''
  const secret = params.get('secret') || ''
  return /^[A-Za-z0-9_-]{32}$/.test(ticket) && /^[A-Za-z0-9_-]{32}$/.test(secret)
    ? { ticket, secret }
    : null
}
export const continueMobileAuthorization = (result: MobileAuthorizationResult): boolean => {
  if (result.status !== 'completed' || !result.redirectUrl) return false
  // The destination comes exclusively from the server's registered redirect validation.
  window.location.replace(result.redirectUrl)
  return true
}

export const launchMobileAuthorization = (ticket: MobileAuthorizationTicket): void => {
  const appLink = new URL(ticket.appLink)
  // Pass only a browser we can explicitly reopen through its documented iOS URL scheme.
  if (/CriOS\//i.test(navigator.userAgent)) appLink.searchParams.set('returnBrowser', 'chrome')
  window.location.assign(appLink.toString())
}
