import request from '@/utils/request'
import { officialScopeLabel, type OfficialAuthorizeRequest } from '@/api/official'
import { exchangeSessionTransfer } from '@/api/auth'
import { finalizeWebLogin } from '@/utils/desktopBridge'
import { effectiveLocale } from '@/i18n'

export type ApplicationAuthorizationMode = 'oauth' | 'sso' | 'official'
export interface ApplicationQrPayload {
  challenge: { challengeId: string; pollToken: string; qrText: string; expiresInSeconds: number }
  application: {
    clientName: string
    logoUrl?: string
    contactInfo?: string
    requestedScopes: string[]
    consoleApplication: boolean
    subjectName?: string | null
    subjectVerificationType?: string | null
  }
  redirectUri: string
}
export interface ApplicationQrBrowserRequest {
  challengeId: string
  pollToken: string
}
export interface ApplicationQrResult {
  status: 'pending' | 'completed'
  redirectUrl?: string
  webLoginTransferCode?: string | null
}

const browserRequest = (input: ApplicationQrBrowserRequest) => ({
  ticket: input.challengeId,
  secret: input.pollToken,
})
export const initApplicationQr = async (
  mode: ApplicationAuthorizationMode,
  authorization: OfficialAuthorizeRequest,
): Promise<ApplicationQrPayload> => {
  const { data } = await request.post('/auth/mobile-authorization/qr/create', {
    mode,
    returnOrigin: window.location.origin,
    authorization,
  })
  return {
    challenge: {
      challengeId: data.ticket,
      pollToken: data.secret,
      qrText: data.qrText,
      expiresInSeconds: data.expiresInSeconds,
    },
    application: {
      clientName: data.application.appName,
      logoUrl: data.application.logoUrl,
      contactInfo: data.application.contactInfo,
      requestedScopes: data.application.requestedScopes,
      consoleApplication: data.application.consoleApplication,
      subjectName: data.application.subjectName,
      subjectVerificationType: data.application.subjectVerificationType,
    },
    redirectUri: data.application.redirectUri,
  }
}
export const pollApplicationQr = async (
  input: ApplicationQrBrowserRequest,
): Promise<{ status: 'pending' | 'approved' | 'rejected' | 'expired'; expiresInSeconds: number }> =>
  (await request.get('/auth/mobile-authorization/qr/status', { params: browserRequest(input) }))
    .data
export const exchangeApplicationQr = async (
  input: ApplicationQrBrowserRequest,
): Promise<ApplicationQrResult> =>
  (await request.post('/auth/mobile-authorization/consume', browserRequest(input))).data
export const cancelApplicationQr = async (input: ApplicationQrBrowserRequest): Promise<void> => {
  await request.post('/auth/mobile-authorization/cancel', browserRequest(input))
}
export const finishApplicationQrWebLogin = async (result: ApplicationQrResult): Promise<void> => {
  // The phone controls this capability. In the default case the Passport browser account is untouched.
  if (!result.webLoginTransferCode) return
  const token = await exchangeSessionTransfer(result.webLoginTransferCode, 'web')
  await finalizeWebLogin({ accessToken: token.accessToken, user: token.user, syncDesktop: false })
}
export const applicationQrScopeLabel = (
  mode: ApplicationAuthorizationMode,
  scope: string,
): string => {
  if (mode === 'official') return officialScopeLabel(scope)
  const labels: Record<string, [string, string]> = {
    openid: ['确认账号身份', 'Confirm account identity'],
    profile: ['用户名和头像', 'Username and avatar'],
    email: ['邮箱', 'Email address'],
  }
  return labels[scope]?.[effectiveLocale.value === 'en' ? 1 : 0] ?? scope
}
