import request from '@/utils/request'
import { effectiveLocale } from '@/i18n'

export type OfficialScope =
  | 'openid'
  | 'profile'
  | 'email'
  | 'real_name'
  | 'gender'
  | 'birth_date'
  | 'region'
  | 'bio'

export interface OfficialAuthorizeRequest {
  clientId: string
  redirectUri: string
  responseType: string
  scope?: string
  state?: string
  nonce?: string
  codeChallenge?: string
  codeChallengeMethod?: 'S256'
}

export interface OfficialAuthorizeContext {
  clientId: string
  clientName: string
  logoUrl?: string
  requestedScopes: OfficialScope[]
  authorizedScopes: OfficialScope[]
  consentRequired: boolean
  loggedIn: boolean
  consoleApplication: boolean
}

export interface OfficialAuthorization {
  clientId: string
  clientName: string
  logoUrl?: string
  scopes: OfficialScope[]
  authorizedAt: string
  lastAuthorizedAt: string
}

export const officialScopeLabel = (scope: string) => {
  const labels: Record<OfficialScope, [string, string]> = {
    openid: ['真实用户 UUID', 'Real user UUID'],
    profile: ['用户名和头像', 'Username and avatar'],
    email: ['邮箱', 'Email address'],
    real_name: ['真实姓名', 'Real name'],
    gender: ['性别', 'Gender'],
    birth_date: ['出生日期', 'Birth date'],
    region: ['所在地区', 'Region'],
    bio: ['个人简介', 'Biography'],
  }
  return labels[scope as OfficialScope]?.[effectiveLocale.value === 'en' ? 1 : 0] ?? scope
}

export const getOfficialAuthorizeContext = async (
  params: OfficialAuthorizeRequest,
): Promise<OfficialAuthorizeContext> => {
  const response = await request.get('/official/authorize/context', {
    params: {
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      response_type: params.responseType,
      scope: params.scope,
      state: params.state,
      nonce: params.nonce,
      code_challenge: params.codeChallenge,
      code_challenge_method: params.codeChallengeMethod,
    },
  })
  return response.data
}

export const approveOfficialAuthorize = async (
  data: OfficialAuthorizeRequest & { approved: boolean },
): Promise<{ redirectUrl: string }> => {
  const response = await request.post('/official/authorize/approve', data)
  return response.data
}

export const getOfficialAuthorizations = async (): Promise<OfficialAuthorization[]> => {
  const response = await request.get('/official/authorizations')
  return response.data
}

export const revokeOfficialAuthorization = async (clientId: string): Promise<void> => {
  await request.delete(`/official/authorizations/${encodeURIComponent(clientId)}`)
}
