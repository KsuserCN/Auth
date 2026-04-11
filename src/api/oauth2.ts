import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { VerificationType } from '@/api/auth'

export type OAuth2Scope = 'profile' | 'email'

export interface OAuth2App {
  appId: string
  appName: string
  logoUrl?: string
  redirectUri: string
  contactInfo: string
  scopes: OAuth2Scope[]
  createdAt: string
  updatedAt: string
}

export interface OAuth2AppsOverview {
  verificationType: VerificationType
  verified: boolean
  maxApps: number
  currentCount: number
  canCreate: boolean
  apps: OAuth2App[]
}

export interface CreateOAuth2AppRequest {
  appName: string
  redirectUri: string
  contactInfo: string
  scopes: OAuth2Scope[]
}

export interface UpdateOAuth2AppRequest {
  appName: string
  redirectUri: string
  contactInfo: string
}

export interface CreateOAuth2AppResponse extends OAuth2App {
  appSecret: string
}

export interface OAuth2AuthorizeContext {
  clientId: string
  appName: string
  logoUrl?: string
  contactInfo: string
  redirectUri: string
  requestedScopes: OAuth2Scope[]
}

export interface OAuth2AuthorizeApproveRequest {
  clientId: string
  redirectUri: string
  responseType: 'code'
  scope?: string
  state?: string
}

export interface OAuth2AuthorizeApproveResponse {
  redirectUrl: string
}

export const getOAuth2Apps = async (): Promise<OAuth2AppsOverview> => {
  const response = await request.get<any>('/oauth2/apps')
  return response.data as OAuth2AppsOverview
}

export const createOAuth2App = async (
  data: CreateOAuth2AppRequest,
): Promise<CreateOAuth2AppResponse> => {
  const response = await request.post<ApiResponse<CreateOAuth2AppResponse>>('/oauth2/apps', data)
  return (response as unknown as ApiResponse<CreateOAuth2AppResponse>).data
}

export const deleteOAuth2App = async (appId: string): Promise<void> => {
  await request.delete(`/oauth2/apps/${encodeURIComponent(appId)}`)
}

export const updateOAuth2App = async (
  appId: string,
  data: UpdateOAuth2AppRequest,
): Promise<OAuth2App> => {
  const response = await request.put<ApiResponse<OAuth2App>>(
    `/oauth2/apps/${encodeURIComponent(appId)}`,
    data,
  )
  return (response as unknown as ApiResponse<OAuth2App>).data
}

export const uploadOAuth2AppLogo = async (appId: string, file: Blob): Promise<OAuth2App> => {
  const formData = new FormData()
  formData.append('file', file, 'logo.png')
  const response = await request.post<ApiResponse<OAuth2App>>(
    `/oauth2/apps/${encodeURIComponent(appId)}/logo`,
    formData,
  )
  return (response as unknown as ApiResponse<OAuth2App>).data
}

export const getOAuth2AuthorizeContext = async (params: {
  clientId: string
  redirectUri: string
  responseType: string
  scope?: string
}): Promise<OAuth2AuthorizeContext> => {
  const response = await request.get<any>('/oauth2/authorize/context', {
    params: {
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      response_type: params.responseType,
      scope: params.scope,
    },
  })
  return response.data as OAuth2AuthorizeContext
}

export const approveOAuth2Authorize = async (
  data: OAuth2AuthorizeApproveRequest,
): Promise<OAuth2AuthorizeApproveResponse> => {
  const response = await request.post<any>('/oauth2/authorize/approve', data)
  return response.data as OAuth2AuthorizeApproveResponse
}
