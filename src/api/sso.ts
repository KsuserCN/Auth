import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { VerificationType } from '@/api/auth'

export type SSOScope = 'openid' | 'profile' | 'email'

export interface SSOClient {
  clientId: string
  clientName: string
  logoUrl?: string
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: SSOScope[]
  audiences: string[]
  requirePkce: boolean
  createdAt: string
  updatedAt: string
}

export interface SSOClientsOverview {
  verificationType: VerificationType
  admin: boolean
  maxClients: number
  currentCount: number
  canCreate: boolean
  clients: SSOClient[]
}

export interface CreateSSOClientRequest {
  clientName: string
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  scopes: SSOScope[]
  audiences: string[]
  requirePkce: boolean
}

export interface UpdateSSOClientRequest extends CreateSSOClientRequest {}

export interface CreateSSOClientResponse extends SSOClient {
  clientSecret: string
}

export interface SSOAuthorizeContext {
  clientId: string
  clientName: string
  logoUrl?: string
  redirectUri: string
  requestedScopes: SSOScope[]
}

export interface SSOAuthorizeApproveRequest {
  clientId: string
  redirectUri: string
  responseType: 'code'
  scope?: string
  state?: string
  nonce?: string
  codeChallenge?: string
  codeChallengeMethod?: 'S256'
}

export interface SSOAuthorizeApproveResponse {
  redirectUrl: string
}

export const getSSOClients = async (): Promise<SSOClientsOverview> => {
  const response = await request.get<any>('/sso/clients')
  return response.data as SSOClientsOverview
}

export const createSSOClient = async (
  data: CreateSSOClientRequest,
): Promise<CreateSSOClientResponse> => {
  const response = await request.post<ApiResponse<CreateSSOClientResponse>>('/sso/clients', data)
  return (response as unknown as ApiResponse<CreateSSOClientResponse>).data
}

export const updateSSOClient = async (
  clientId: string,
  data: UpdateSSOClientRequest,
): Promise<SSOClient> => {
  const response = await request.put<ApiResponse<SSOClient>>(
    `/sso/clients/${encodeURIComponent(clientId)}`,
    data,
  )
  return (response as unknown as ApiResponse<SSOClient>).data
}

export const deleteSSOClient = async (clientId: string): Promise<void> => {
  await request.delete(`/sso/clients/${encodeURIComponent(clientId)}`)
}

export const uploadSSOClientLogo = async (clientId: string, file: Blob): Promise<SSOClient> => {
  const formData = new FormData()
  formData.append('file', file, 'logo.png')
  const response = await request.post<ApiResponse<SSOClient>>(
    `/sso/clients/${encodeURIComponent(clientId)}/logo`,
    formData,
  )
  return (response as unknown as ApiResponse<SSOClient>).data
}

export const getSSOAuthorizeContext = async (params: {
  clientId: string
  redirectUri: string
  responseType: string
  scope?: string
  nonce?: string
  codeChallenge?: string
  codeChallengeMethod?: 'S256'
}): Promise<SSOAuthorizeContext> => {
  const response = await request.get<any>('/sso/authorize/context', {
    params: {
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      response_type: params.responseType,
      scope: params.scope,
      nonce: params.nonce,
      code_challenge: params.codeChallenge,
      code_challenge_method: params.codeChallengeMethod,
    },
  })
  return response.data as SSOAuthorizeContext
}

export const approveSSOAuthorize = async (
  data: SSOAuthorizeApproveRequest,
): Promise<SSOAuthorizeApproveResponse> => {
  const response = await request.post<any>('/sso/authorize/approve', data)
  return response.data as SSOAuthorizeApproveResponse
}
