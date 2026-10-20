import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'
import type { VerificationType, SystemRole } from '@/api/auth'

export type OAuth2Scope = 'profile' | 'email'
export type OAuth2ClientType = 'public' | 'confidential'
export type ApplicationType = 'website' | 'mobile'
export type MobilePlatform = 'ios' | 'android'
export type UnionidStatus = 'none' | 'pending' | 'approved' | 'rejected' | 'revoked'
export interface PlatformVerification {
  verificationStatus?: 'unverified' | 'verified' | 'failed'
  verificationMessage?: string | null
  verifiedAt?: string | null
}
export interface IOSApplicationPlatform extends PlatformVerification {
  bundleId: string
  appIdPrefix: string
  universalLink: string
  urlScheme?: string
  appStoreId?: string
}
export interface AndroidApplicationPlatform extends PlatformVerification {
  packageName: string
  sha256CertFingerprints: string[]
  appLink: string
  downloadUrl?: string
}
export interface ApplicationPlatforms {
  ios?: IOSApplicationPlatform
  android?: AndroidApplicationPlatform
}
export type AuthorizationGrantMode = 'PERSISTENT' | 'ONE_TIME' | 'TIME_LIMITED'

export interface OAuth2App {
  appId: string
  appName: string
  clientType?: OAuth2ClientType
  applicationType?: ApplicationType
  platforms?: ApplicationPlatforms
  unionidStatus?: UnionidStatus
  unionidReason?: string | null
  unionidReviewReason?: string | null
  logoUrl?: string
  redirectUri: string
  contactInfo: string
  scopes: OAuth2Scope[]
  createdAt: string
  updatedAt: string
}

export interface OAuth2AppsOverview {
  verificationType: VerificationType
  developerVerificationType?: Exclude<VerificationType, 'admin'>
  systemRole?: SystemRole
  verified: boolean
  maxApps: number
  currentCount: number
  canCreate: boolean
  apps: OAuth2App[]
}

export interface CreateOAuth2AppRequest {
  appName: string
  clientType?: OAuth2ClientType
  applicationType?: ApplicationType
  platforms?: ApplicationPlatforms
  redirectUri?: string
  contactInfo: string
  scopes: OAuth2Scope[]
}

export interface UpdateOAuth2AppRequest {
  appName: string
  redirectUri?: string
  platforms?: ApplicationPlatforms
  contactInfo: string
}

export interface CreateOAuth2AppResponse extends OAuth2App {
  appSecret: string | null
}

export interface OAuth2AuthorizeContext {
  clientId: string
  appName: string
  logoUrl?: string
  contactInfo: string
  redirectUri: string
  requestedScopes: OAuth2Scope[]
  subjectName?: string | null
  subjectVerificationType?: VerificationType | null
  alreadyAuthorized: boolean
  existingGrantMode: AuthorizationGrantMode
  existingGrantExpiresAt?: string | null
}

export interface OAuth2AuthorizeApproveRequest {
  clientId: string
  redirectUri: string
  responseType: 'code'
  scope?: string
  state?: string
  codeChallenge?: string
  codeChallengeMethod?: 'S256'
  grantMode?: AuthorizationGrantMode
  grantTtlSeconds?: number
}

export interface OAuth2AuthorizeApproveResponse {
  redirectUrl: string
}

export interface OAuth2AuthorizedApp {
  appId: string
  appName: string
  logoUrl?: string
  creatorName?: string
  creatorVerificationType?: VerificationType
  contactInfo: string
  redirectUri: string
  scopes: OAuth2Scope[]
  authorizedAt: string
  lastAuthorizedAt: string
  grantMode: AuthorizationGrantMode
  expiresAt?: string | null
}

export const getOAuth2Apps = async (): Promise<OAuth2AppsOverview> => {
  const response = await request.get<OAuth2AppsOverview>('/oauth2/apps')
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

export const verifyApplicationPlatform = async (appId: string, platform: MobilePlatform): Promise<OAuth2App> => {
  const response = await request.post<ApiResponse<OAuth2App>>(`/oauth2/apps/${encodeURIComponent(appId)}/platforms/${platform}/verify`)
  return (response as unknown as ApiResponse<OAuth2App>).data
}

export const applyApplicationUnionid = async (appId: string, reason: string): Promise<OAuth2App> => {
  const response = await request.post<ApiResponse<OAuth2App>>(`/oauth2/apps/${encodeURIComponent(appId)}/unionid/apply`, { reason })
  return (response as unknown as ApiResponse<OAuth2App>).data
}

export const closeApplicationUnionid = async (appId: string): Promise<OAuth2App> => {
  const response = await request.delete<ApiResponse<OAuth2App>>(`/oauth2/apps/${encodeURIComponent(appId)}/unionid`)
  return (response as unknown as ApiResponse<OAuth2App>).data
}

export const getOAuth2AuthorizeContext = async (params: {
  clientId: string
  redirectUri: string
  responseType: string
  scope?: string
  state?: string
  codeChallenge?: string
  codeChallengeMethod?: 'S256'
}): Promise<OAuth2AuthorizeContext> => {
  const response = await request.get<OAuth2AuthorizeContext>('/oauth2/authorize/context', {
    params: {
      client_id: params.clientId,
      redirect_uri: params.redirectUri,
      response_type: params.responseType,
      scope: params.scope,
      state: params.state,
      code_challenge: params.codeChallenge,
      code_challenge_method: params.codeChallengeMethod,
    },
  })
  return response.data as OAuth2AuthorizeContext
}

export const approveOAuth2Authorize = async (
  data: OAuth2AuthorizeApproveRequest,
): Promise<OAuth2AuthorizeApproveResponse> => {
  const response = await request.post<OAuth2AuthorizeApproveResponse>('/oauth2/authorize/approve', data)
  return response.data as OAuth2AuthorizeApproveResponse
}

export const getOAuth2Authorizations = async (): Promise<OAuth2AuthorizedApp[]> => {
  const response = await request.get<OAuth2AuthorizedApp[]>('/oauth2/authorizations')
  return response.data as OAuth2AuthorizedApp[]
}

export const revokeOAuth2Authorization = async (appId: string): Promise<void> => {
  await request.delete(`/oauth2/authorizations/${encodeURIComponent(appId)}`)
}
