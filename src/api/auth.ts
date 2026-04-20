import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// 用户基本信息类型
export interface User {
  uuid: string
  username: string
  email: string
  avatarUrl?: string | null
  verificationType?: VerificationType
  settings?: UserSettings
}

export type VerificationType = 'none' | 'personal' | 'enterprise' | 'admin'

// 用户详细信息类型
export interface UserDetails extends User {
  realName?: string
  gender?: string
  birthDate?: string
  region?: string
  bio?: string
  updatedAt?: string
}

// 用户设置类型
export interface UserSettings {
  mfaEnabled: boolean
  detectUnusualLogin: boolean
  notifySensitiveActionEmail: boolean
  subscribeNewsEmail: boolean
  preferredMfaMethod?: MFAMethod
  preferredSensitiveMethod?: 'password' | 'email-code' | 'passkey' | 'totp'
}

export type MFAMethod = 'totp' | 'passkey' | 'qr'

// 登录响应类型
export interface LoginResponse {
  accessToken: string
  user?: User
}

// MFA 验证挑战响应
export interface MFAChallenge {
  challengeId: string
  method: MFAMethod
  methods?: MFAMethod[]
}

// TOTP 验证请求
export type TOTPVerifyRequest =
  | {
      challengeId: string
      code: string
    }
  | {
      challengeId: string
      recoveryCode: string
    }

// TOTP 验证响应
export interface TOTPVerifyResponse {
  accessToken: string
  user?: User
}

// ========== 密码登录 ==========

export interface PasswordLoginRequest {
  email: string
  password: string
}

/**
 * 密码登录
 * POST /auth/login
 * 返回 200 表示直接登录成功，返回 201 表示需要 MFA 验证
 */
export const passwordLogin = async (
  data: PasswordLoginRequest,
): Promise<LoginResponse | MFAChallenge> => {
  const response = await request.post<ApiResponse<LoginResponse | MFAChallenge>>(
    '/auth/login',
    data,
  )
  const result = (response as unknown as ApiResponse<LoginResponse | MFAChallenge>).data

  // 检查响应中是否包含 challengeId（MFA 场景）
  if ('challengeId' in result) {
    return result as MFAChallenge
  }

  return result as LoginResponse
}

// ========== 邮箱验证码登录 ==========

/**
 * 发送登录验证码
 * POST /auth/send-code
 */
export interface SendLoginCodeRequest {
  email: string
}

export const sendLoginCode = async (data: SendLoginCodeRequest): Promise<void> => {
  await request.post('/auth/send-code', { email: data.email, type: 'login' })
}

/**
 * 邮箱验证码登录
 * POST /auth/login-with-code
 * 返回 200 表示直接登录成功，返回 201 表示需要 MFA 验证
 */
export interface LoginWithCodeRequest {
  email: string
  code: string
}

export const loginWithCode = async (
  data: LoginWithCodeRequest,
): Promise<LoginResponse | MFAChallenge> => {
  const response = await request.post<ApiResponse<LoginResponse | MFAChallenge>>(
    '/auth/login-with-code',
    data,
  )
  const result = (response as unknown as ApiResponse<LoginResponse | MFAChallenge>).data

  // 检查响应中是否包含 challengeId（MFA 场景）
  if ('challengeId' in result) {
    return result as MFAChallenge
  }

  return result as LoginResponse
}

// ========== Passkey 登录 ==========

/**
 * 获取 Passkey 认证选项（登录用）
 * POST /auth/passkey/authentication-options
 */
export interface PasskeyAuthenticationOptions {
  challenge: string
  challengeId: string
  timeout: string
  rpId: string
  userVerification: string
  allowCredentials?: string
}

export const getPasskeyAuthenticationOptions = async (): Promise<PasskeyAuthenticationOptions> => {
  const response = await request.post<ApiResponse<PasskeyAuthenticationOptions>>(
    '/auth/passkey/authentication-options',
  )
  return (response as any).data
}

/**
 * 验证 Passkey 认证（登录用）
 * POST /auth/passkey/authentication-verify
 * 返回 200 表示直接登录成功，返回 201 表示需要 MFA 验证
 */
export interface PasskeyAuthenticationRequest {
  credentialRawId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
}

export interface PasskeyMFAVerifyRequest extends PasskeyAuthenticationRequest {
  mfaChallengeId: string
  passkeyChallengeId: string
}

export const verifyPasskeyAuthentication = async (
  challengeId: string,
  data: PasskeyAuthenticationRequest,
): Promise<LoginResponse | MFAChallenge> => {
  const response = await request.post<ApiResponse<LoginResponse | MFAChallenge>>(
    `/auth/passkey/authentication-verify?challengeId=${challengeId}`,
    data,
  )
  const result = (response as any).data

  // 检查响应中是否包含 challengeId（MFA 场景）
  if ('challengeId' in result) {
    return result as MFAChallenge
  }

  return result as LoginResponse
}

/**
 * Passkey 验证（MFA 登录流程）
 * POST /auth/passkey/mfa-verify
 */
export const verifyPasskeyForLoginMFA = async (
  data: PasskeyMFAVerifyRequest,
): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>('/auth/passkey/mfa-verify', data)
  return (response as unknown as ApiResponse<LoginResponse>).data
}

// ========== Passkey 注册 ==========
export interface PasskeyRegistrationOptionsRequest {
  passkeyName: string
  authenticatorType?: 'auto'
}

export interface PasskeyRegistrationOptions {
  challenge: string
  rp: string
  user: string
  pubKeyCredParams: string
  timeout: string
  attestation: string
  authenticatorSelection: string
}

export const getPasskeyRegistrationOptions = async (
  data: PasskeyRegistrationOptionsRequest,
): Promise<PasskeyRegistrationOptions> => {
  const response = await request.post<ApiResponse<PasskeyRegistrationOptions>>(
    '/auth/passkey/registration-options',
    data,
  )
  return (response as unknown as ApiResponse<PasskeyRegistrationOptions>).data
}

/**
 * 完成 Passkey 注册
 * POST /auth/passkey/registration-verify
 */
export interface PasskeyRegistrationRequest {
  credentialRawId: string
  clientDataJSON: string
  attestationObject: string
  passkeyName: string
  transports: string
}

export interface PasskeyInfo {
  passkeyId: number
  passkeyName: string
  createdAt: string
}

export const verifyPasskeyRegistration = async (
  data: PasskeyRegistrationRequest,
): Promise<PasskeyInfo> => {
  const response = await request.post<ApiResponse<PasskeyInfo>>(
    '/auth/passkey/registration-verify',
    data,
  )
  return (response as unknown as ApiResponse<PasskeyInfo>).data
}

// ========== Passkey 敏感操作验证 ==========

/**
 * 获取敏感操作验证选项（Passkey）
 * POST /auth/passkey/sensitive-verification-options
 */
export interface PasskeySensitiveVerificationOptions {
  challengeId: string
  challenge: string
  timeout: string
  rpId: string
  userVerification: string
  allowCredentials?: string
}

export const getPasskeySensitiveVerificationOptions =
  async (): Promise<PasskeySensitiveVerificationOptions> => {
    const response = await request.post<ApiResponse<PasskeySensitiveVerificationOptions>>(
      '/auth/passkey/sensitive-verification-options',
    )
    return (response as unknown as ApiResponse<PasskeySensitiveVerificationOptions>).data
  }

/**
 * 验证敏感操作（Passkey）
 * POST /auth/passkey/sensitive-verification-verify
 */
export interface PasskeySensitiveVerificationRequest {
  credentialRawId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
}

export const verifyPasskeySensitiveOperation = async (
  challengeId: string,
  data: PasskeySensitiveVerificationRequest,
): Promise<void> => {
  await request.post(`/auth/passkey/sensitive-verification-verify?challengeId=${challengeId}`, data)
}

// ========== Passkey 管理 ==========

/**
 * 获取 Passkey 列表
 * GET /auth/passkey/list
 */
export interface PasskeyListItem {
  id: number
  name: string
  transports: string
  lastUsedAt: string | null
  createdAt: string
}

export const getPasskeyList = async (): Promise<PasskeyListItem[]> => {
  const response =
    await request.get<ApiResponse<{ passkeys: PasskeyListItem[] }>>('/auth/passkey/list')
  return (response as unknown as ApiResponse<{ passkeys: PasskeyListItem[] }>).data.passkeys
}

/**
 * 删除 Passkey
 * DELETE /auth/passkey/{passkeyId}
 */
export const deletePasskey = async (passkeyId: number): Promise<void> => {
  await request.delete(`/auth/passkey/${passkeyId}`)
}

/**
 * 重命名 Passkey
 * PUT /auth/passkey/{passkeyId}/rename
 */
export const renamePasskey = async (passkeyId: number, newName: string): Promise<void> => {
  await request.put(`/auth/passkey/${passkeyId}/rename`, { newName })
}

// ========== 刷新 Token ==========

/**
 * 刷新 Token
 * POST /auth/refresh
 */
export const refreshAccessToken = async (): Promise<{ accessToken: string }> => {
  const response = await request.post<ApiResponse<{ accessToken: string }>>(
    '/auth/refresh',
    {},
    { _skipAuthRefresh: true, _suppressErrorToast: true } as any,
  )
  return response.data as unknown as { accessToken: string }
}

export type SessionTransferTarget = 'web' | 'desktop' | 'mobile'
export type SessionTransferPurpose = 'bridge_login' | 'session_sync' | 'auth_bridge_internal'

export interface SessionTransferResponse {
  transferCode: string
  expiresInSeconds: number
}

export const createSessionTransfer = async (
  target: SessionTransferTarget,
  purpose?: SessionTransferPurpose,
): Promise<SessionTransferResponse> => {
  const response = await request.post<ApiResponse<SessionTransferResponse>>(
    '/auth/session-transfer/create',
    purpose ? { target, purpose } : { target },
  )
  return response.data as unknown as SessionTransferResponse
}

export const exchangeSessionTransfer = async (
  transferCode: string,
  target: SessionTransferTarget,
): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>(
    '/auth/session-transfer/exchange',
    { transferCode, target },
  )
  return response.data as unknown as LoginResponse
}

export interface AccountRecoveryTicketResponse {
  recoveryCode: string
  expiresInSeconds: number
  username: string
  maskedEmail: string
  sponsorClientName: string
  sponsorBrowser: string
  sponsorSystem: string
  sponsorIpLocation: string
}

export interface AccountRecoveryStatusResponse {
  expiresInSeconds: number
  username: string
  maskedEmail: string
  sponsorClientName: string
  sponsorBrowser: string
  sponsorSystem: string
  sponsorIpLocation: string
}

export interface AccountRecoveryCompleteRequest {
  recoveryCode: string
  newPassword: string
}

export const issueAccountRecoveryTicket = async (): Promise<AccountRecoveryTicketResponse> => {
  const response = await request.post<ApiResponse<AccountRecoveryTicketResponse>>(
    '/auth/account-recovery/issue',
    {},
  )
  return (response as unknown as ApiResponse<AccountRecoveryTicketResponse>).data
}

export const getAccountRecoveryStatus = async (
  recoveryCode: string,
): Promise<AccountRecoveryStatusResponse> => {
  const response = await request.get<ApiResponse<AccountRecoveryStatusResponse>>(
    '/auth/account-recovery/status',
    { params: { recoveryCode } },
  )
  return (response as unknown as ApiResponse<AccountRecoveryStatusResponse>).data
}

export const completeAccountRecovery = async (
  data: AccountRecoveryCompleteRequest,
): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>(
    '/auth/account-recovery/complete',
    data,
  )
  return (response as unknown as ApiResponse<LoginResponse>).data
}

export interface QrChallengeInitResponse {
  challengeId: string
  pollToken: string
  qrText: string
  expiresInSeconds: number
}

export interface QrChallengeStatusResponse {
  status: 'pending' | 'approved' | 'rejected' | 'expired'
  expiresInSeconds: number
  transferCode?: string
  recoveryCode?: string
  mfaChallengeId?: string
  method?: MFAMethod
  methods?: MFAMethod[]
  verified?: boolean
}

export const initQrLogin = async (): Promise<QrChallengeInitResponse> => {
  const response = await request.post<ApiResponse<QrChallengeInitResponse>>('/auth/qr/login/init', {})
  return (response as unknown as ApiResponse<QrChallengeInitResponse>).data
}

export const initQrMfa = async (mfaChallengeId: string): Promise<QrChallengeInitResponse> => {
  const response = await request.post<ApiResponse<QrChallengeInitResponse>>('/auth/qr/mfa/init', {
    mfaChallengeId,
  })
  return (response as unknown as ApiResponse<QrChallengeInitResponse>).data
}

export const initQrSensitive = async (): Promise<QrChallengeInitResponse> => {
  const response = await request.post<ApiResponse<QrChallengeInitResponse>>(
    '/auth/qr/sensitive/init',
    {},
  )
  return (response as unknown as ApiResponse<QrChallengeInitResponse>).data
}

export const initQrAccountRecovery = async (): Promise<QrChallengeInitResponse> => {
  const response = await request.post<ApiResponse<QrChallengeInitResponse>>(
    '/auth/account-recovery/qr/init',
    {},
  )
  return (response as unknown as ApiResponse<QrChallengeInitResponse>).data
}

export const pollQrStatus = async (
  challengeId: string,
  pollToken: string,
): Promise<QrChallengeStatusResponse> => {
  const response = await request.get<ApiResponse<QrChallengeStatusResponse>>('/auth/qr/status', {
    params: { challengeId, pollToken },
  })
  return (response as unknown as ApiResponse<QrChallengeStatusResponse>).data
}

// ========== 获取用户信息 ==========

/**
 * 获取当前用户基本信息
 * GET /auth/info?type=basic
 */
export const getUserInfo = async (): Promise<User> => {
  const response = await request.get<any>('/auth/info', { params: { type: 'basic' } })
  // 响应拦截器返回的是 { code, msg, data: {...} }
  // response.data 就是真实的用户数据
  return response.data as User
}

/**
 * 获取当前用户详细信息
 * GET /auth/info?type=details
 */
export const getUserDetailsInfo = async (): Promise<UserDetails> => {
  const response = await request.get<any>('/auth/info', { params: { type: 'details' } })
  // 响应拦截器返回的是 { code, msg, data: {...} }
  // response.data 就是真实的用户详细数据
  return response.data as UserDetails
}

// ========== 更新用户设置 ==========

/**
 * 更新用户设置
 * POST /auth/update/setting
 */
export interface UpdateUserSettingRequest {
  field:
    | 'mfa_enabled'
    | 'mfaEnabled'
    | 'preferred_mfa_method'
    | 'preferredMfaMethod'
    | 'preferred_sensitive_method'
    | 'preferredSensitiveMethod'
    | 'detect_unusual_login'
    | 'detectUnusualLogin'
    | 'notify_sensitive_action_email'
    | 'notifySensitiveActionEmail'
    | 'subscribe_news_email'
    | 'subscribeNewsEmail'
  value?: boolean
  stringValue?: string
}

export const updateUserSetting = async (data: UpdateUserSettingRequest): Promise<UserSettings> => {
  const response = await request.post<ApiResponse<UserSettings>>('/auth/update/setting', data)
  return (response as unknown as ApiResponse<UserSettings>).data
}

// ========== 更新用户信息 ==========

/**
 * 更新用户信息
 * POST /auth/update/profile
 */
export type UpdateProfileKey =
  | 'username'
  | 'avatarUrl'
  | 'realName'
  | 'gender'
  | 'birthDate'
  | 'region'
  | 'bio'

export interface UpdateProfileRequest {
  key: UpdateProfileKey
  value: string
}

export const updateUserProfile = async (data: UpdateProfileRequest): Promise<UserDetails> => {
  const response = await request.post<any>('/auth/update/profile', data)
  // 响应拦截器返回的是 { code, msg, data: {...} }
  return response.data as UserDetails
}

/**
 * 上传头像
 * POST /auth/upload/avatar
 */
export const uploadAvatar = async (file: Blob): Promise<UserDetails> => {
  const formData = new FormData()
  formData.append('file', file, 'avatar.jpg')
  const response = await request.post<any>('/auth/upload/avatar', formData)
  return response.data as UserDetails
}

// ========== 敏感操作验证 ==========

/**
 * 敏感操作验证
 * POST /auth/verify-sensitive
 */
export interface VerifySensitivePasswordRequest {
  method: 'password'
  password: string
}

export interface VerifySensitiveEmailCodeRequest {
  method: 'email-code'
  code: string
}

export interface VerifySensitiveTotpCodeRequest {
  method: 'totp'
  code: string
}

export interface VerifySensitiveRecoveryCodeRequest {
  method: 'totp'
  recoveryCode: string
}

export type VerifySensitiveRequest =
  | VerifySensitivePasswordRequest
  | VerifySensitiveEmailCodeRequest
  | VerifySensitiveTotpCodeRequest
  | VerifySensitiveRecoveryCodeRequest

export const verifySensitiveOperation = async (data: VerifySensitiveRequest): Promise<void> => {
  await request.post('/auth/verify-sensitive', data)
}

/**
 * 发送敏感操作验证码
 * POST /auth/send-code
 */
export const sendSensitiveVerificationCode = async (): Promise<void> => {
  await request.post('/auth/send-code', { type: 'sensitive-verification' })
}

/**
 * 检查敏感操作验证状态
 * GET /auth/check-sensitive-verification
 */
export interface SensitiveVerificationStatus {
  verified: boolean
  remainingSeconds: number
  preferredMethod?: 'password' | 'email-code' | 'passkey' | 'totp'
  methods?: Array<'password' | 'email-code' | 'passkey' | 'totp' | 'qr'>
}

export const checkSensitiveVerification = async (): Promise<SensitiveVerificationStatus> => {
  const response = await request.get<any>('/auth/check-sensitive-verification')
  return response.data as SensitiveVerificationStatus
}

export interface AdaptiveAuthStatus {
  sessionId: number | null
  riskScore: number
  riskLevel: 'low' | 'medium' | 'high'
  trusted: boolean
  requiresStepUp: boolean
  sensitiveVerified: boolean
  sensitiveVerificationRemainingSeconds: number
  authAgeSeconds: number
  idleSeconds: number
  currentIp: string | null
  currentLocation: string | null
  sessionIp: string | null
  sessionLocation: string | null
  browser: string | null
  deviceType: string | null
  recommendedAction: string
  reasons: string[]
}

export const getAdaptiveAuthStatus = async (): Promise<AdaptiveAuthStatus> => {
  const response = await request.get<any>('/auth/adaptive-auth/status')
  return response.data as AdaptiveAuthStatus
}

// ========== 敏感操作日志 ==========

export type SensitiveOperationType =
  | 'REGISTER'
  | 'LOGIN'
  | 'SENSITIVE_VERIFY'
  | 'CHANGE_PASSWORD'
  | 'CHANGE_EMAIL'
  | 'ADD_PASSKEY'
  | 'DELETE_PASSKEY'
  | 'ENABLE_TOTP'
  | 'DISABLE_TOTP'

export type SensitiveLoginMethod =
  | 'PASSWORD'
  | 'PASSWORD_MFA'
  | 'EMAIL'
  | 'EMAIL_CODE'
  | 'EMAIL_CODE_MFA'
  | 'PASSKEY'
  | 'PASSKEY_MFA'
  | 'QR'
  | 'QR_MFA'
  | 'QQ'
  | 'GITHUB'
  | 'MICROSOFT'
  | 'GOOGLE'
  | 'WECHAT'
  | 'WEIXIN'
  | 'MFA'
  | 'BRIDGE'
  | 'BRIDGE_FROM_DESKTOP'
  | 'BRIDGE_FROM_WEB'
  | 'BRIDGE_TO_MOBILE'
  | 'ACCOUNT_RECOVERY'

export interface SensitiveLogItem {
  id: number
  operationType: SensitiveOperationType
  loginMethod: SensitiveLoginMethod | string | null
  loginMethods?: Array<SensitiveLoginMethod | string>
  ipAddress: string
  ipLocation: string | null
  browser: string | null
  deviceType: 'Desktop' | 'Mobile' | 'Tablet' | 'Bot' | string
  result: 'SUCCESS' | 'FAILURE'
  failureReason: string | null
  riskScore: number
  actionTaken: 'ALLOW' | 'BLOCK' | 'FREEZE'
  triggeredMultiErrorLock: boolean
  triggeredRateLimitLock: boolean
  durationMs: number
  createdAt: string
}

export interface SensitiveLogsResponse {
  data: SensitiveLogItem[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}

export interface SensitiveLogsQuery {
  page?: number
  pageSize?: number
  startDate?: string
  endDate?: string
  operationType?: SensitiveOperationType
  result?: 'SUCCESS' | 'FAILURE'
}

export const getSensitiveLogs = async (
  params: SensitiveLogsQuery = {},
): Promise<SensitiveLogsResponse> => {
  const response = await request.get<ApiResponse<SensitiveLogsResponse>>('/auth/sensitive-logs', {
    params,
  })
  return (response as unknown as ApiResponse<SensitiveLogsResponse>).data
}

// ========== 更改邮箱 ==========

/**
 * 发送新邮箱验证码
 * POST /auth/send-code
 */
export interface SendChangeEmailCodeRequest {
  email: string
}

export const sendChangeEmailCode = async (data: SendChangeEmailCodeRequest): Promise<void> => {
  await request.post('/auth/send-code', { email: data.email, type: 'change-email' })
}

/**
 * 更改邮箱
 * POST /auth/update/email
 */
export interface ChangeEmailRequest {
  newEmail: string
  code: string
}

export const changeEmail = async (data: ChangeEmailRequest): Promise<User> => {
  const response = await request.post<ApiResponse<User>>('/auth/update/email', data)
  return response.data as unknown as User
}

// ========== 修改密码 ==========

/**
 * 修改密码
 * POST /auth/update/password
 */
export interface ChangePasswordRequest {
  newPassword: string
}

export const changePassword = async (data: ChangePasswordRequest): Promise<void> => {
  await request.post('/auth/update/password', data)
}

// ========== 密码强度要求 ==========

/**
 * 密码强度要求
 */
export interface PasswordRequirement {
  minLength: number
  maxLength: number
  requireUppercase: boolean
  requireLowercase: boolean
  requireDigits: boolean
  requireSpecialChars: boolean
  rejectCommonWeakPasswords: boolean
  requirementMessage: string
}

/**
 * 获取密码强度要求
 * GET /info/password-requirement
 */
export const getPasswordRequirement = async (): Promise<PasswordRequirement> => {
  const response = await request.get<any>('/info/password-requirement')
  return response.data as PasswordRequirement
}

// ========== 注册 ==========

/**
 * 检查用户名是否可用
 * GET /auth/check-username
 */
export const checkUsername = async (username: string): Promise<boolean> => {
  try {
    const response = await request.get<ApiResponse<{ exists: boolean }>>('/auth/check-username', {
      params: { username },
    })
    return !(response.data as unknown as { exists: boolean }).exists
  } catch (error) {
    return false
  }
}

/**
 * 发送注册验证码
 * POST /auth/send-code
 */
export interface SendRegisterCodeRequest {
  email: string
}

export const sendRegisterCode = async (data: SendRegisterCodeRequest): Promise<void> => {
  await request.post('/auth/send-code', { email: data.email, type: 'register' })
}

/**
 * 注册
 * POST /auth/register
 */
export interface RegisterRequest {
  username: string
  email: string
  password: string
  code: string
}

export interface RegisterResponse {
  uuid: string
  username: string
  email: string
  accessToken: string
  createdAt: string
}

export const register = async (data: RegisterRequest): Promise<RegisterResponse> => {
  const response = await request.post<ApiResponse<RegisterResponse>>('/auth/register', data)
  return response.data as unknown as RegisterResponse
}

// ========== 删除账号 ==========

/**
 * 删除账号
 * POST /auth/delete
 */
export interface DeleteAccountRequest {
  confirmText: string
}

export const deleteAccount = async (data: DeleteAccountRequest): Promise<void> => {
  await request.post('/auth/delete', data)
}

// ========== 退出登录 ==========

/**
 * 退出登录
 * POST /auth/logout
 */
export const logout = async (): Promise<void> => {
  await request.post('/auth/logout')
}

// ========== TOTP 相关 ==========

/**
 * 获取 TOTP 状态
 * GET /auth/totp/status
 */
export interface TotpStatusResponse {
  enabled: boolean
  recoveryCodesCount: number
}

export const getTotpStatus = async (): Promise<TotpStatusResponse> => {
  const response = await request.get<ApiResponse<TotpStatusResponse>>('/auth/totp/status')
  return (response as unknown as ApiResponse<TotpStatusResponse>).data
}

/**
 * 获取 TOTP 注册选项
 * POST /auth/totp/registration-options
 */
export interface TotpRegistrationOptionsResponse {
  secret: string
  qrCodeUrl: string
  recoveryCodes: string[]
}

export const getTotpRegistrationOptions = async (): Promise<TotpRegistrationOptionsResponse> => {
  const response = await request.post<ApiResponse<TotpRegistrationOptionsResponse>>(
    '/auth/totp/registration-options',
  )
  return (response as unknown as ApiResponse<TotpRegistrationOptionsResponse>).data
}

/**
 * 确认 TOTP 注册
 * POST /auth/totp/registration-verify
 * 必须提交 6 位动态码 + registration-options 返回的 recoveryCodes
 */
export interface TotpRegistrationVerifyRequest {
  code: string
  recoveryCodes: string[]
}

export const verifyTotpRegistration = async (
  data: TotpRegistrationVerifyRequest,
): Promise<void> => {
  await request.post('/auth/totp/registration-verify', data)
}

/**
 * TOTP 验证（MFA 登录流程）
 * POST /auth/totp/mfa-verify
 * 用于在登录时完成 MFA 验证：
 * - 动态码模式：challengeId + code(6位数字)
 * - 恢复码模式：challengeId + recoveryCode(8位大写字母)
 */
export const verifyTOTPForLogin = async (data: TOTPVerifyRequest): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>('/auth/totp/mfa-verify', data)
  return (response as unknown as ApiResponse<LoginResponse>).data
}

/**
 * TOTP 验证
 * POST /auth/totp/verify
 */
export interface TotpVerifyRequest {
  code: string
}

export interface TotpRecoveryCodeVerifyRequest {
  recoveryCode: string
}

export type TotpVerifyRequestPayload = TotpVerifyRequest | TotpRecoveryCodeVerifyRequest

export interface TotpVerifyResponse {
  success: boolean
  message: string
}

export const verifyTotp = async (data: TotpVerifyRequestPayload): Promise<TotpVerifyResponse> => {
  const response = await request.post<ApiResponse<TotpVerifyResponse>>('/auth/totp/verify', data)
  return (response as unknown as ApiResponse<TotpVerifyResponse>).data
}

/**
 * 获取回复码列表
 * GET /auth/totp/recovery-codes
 */
export const getRecoveryCodes = async (): Promise<string[]> => {
  const response = await request.get<ApiResponse<string[]>>('/auth/totp/recovery-codes')
  return (response as unknown as ApiResponse<string[]>).data
}

/**
 * 重新生成回复码
 * POST /auth/totp/recovery-codes/regenerate
 */
export const regenerateRecoveryCodes = async (): Promise<string[]> => {
  const response = await request.post<ApiResponse<string[]>>('/auth/totp/recovery-codes/regenerate')
  return (response as unknown as ApiResponse<string[]>).data
}

/**
 * 禁用 TOTP
 * POST /auth/totp/disable
 */
export const disableTotp = async (): Promise<void> => {
  await request.post('/auth/totp/disable')
}

// ========== 会话管理 ==========

/**
 * 会话信息
 */
export interface SessionItem {
  id: number
  ipAddress: string
  ipLocation: string | null
  userAgent: string | null
  browser: string | null
  deviceType: string | null
  createdAt: string
  lastSeenAt: string
  expiresAt: string
  revokedAt: string | null
  online: boolean
  current: boolean
}

/**
 * 获取在线会话列表
 * GET /auth/sessions
 */
export const getSessions = async (): Promise<SessionItem[]> => {
  const response = await request.get<ApiResponse<SessionItem[]>>('/auth/sessions')
  return (response as unknown as ApiResponse<SessionItem[]>).data
}

/**
 * 撤销指定会话
 * POST /auth/sessions/{sessionId}/revoke
 */
export const revokeSession = async (sessionId: number): Promise<void> => {
  await request.post(`/auth/sessions/${sessionId}/revoke`)
}

/**
 * 从所有设备退出登录
 * POST /auth/logout/all
 */
export const logoutAll = async (): Promise<void> => {
  await request.post('/auth/logout/all')
}

// ========== OAuth QQ 登录 ==========

export type QQOAuthOperation = 'login' | 'bind' | 'unbind'
export type GithubOAuthOperation = 'login' | 'bind' | 'unbind'
export type MicrosoftOAuthOperation = 'login' | 'bind' | 'unbind'
export type GoogleOAuthOperation = 'login' | 'bind' | 'unbind'

/**
 * 构建 QQ 授权 URL
 * 用于生成 QQ OAuth 授权链接
 */
export const buildQQAuthorizationUrl = (state: string): string => {
  const clientId = import.meta.env.VITE_OAUTH_QQ_APPID
  const redirectUri = 'https://auth.ksuser.cn/oauth/qq/callback'

  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: redirectUri,
    state: state,
  })

  return `https://graph.qq.com/oauth2.0/authorize?${params.toString()}`
}

/**
 * QQ OAuth 回调请求参数
 */
export interface QQCallbackRequest {
  code: string
  state: string
}

export interface OAuthLoginCallbackResponse {
  // 已绑定用户，直接登录成功 (HTTP 200)
  accessToken?: string
  user?: {
    id: string
    username: string
    email: string
    [key: string]: unknown
  }
  // 未绑定用户 (HTTP 202)
  needBind?: boolean
  openid?: string
  message?: string
  // MFA 场景 (HTTP 201)
  challengeId?: string
  method?: MFAMethod
  methods?: MFAMethod[]
}

export interface OAuthBindCallbackResponse {
  bound?: boolean
  provider?: string
  openid?: string
  unionid?: string
  message?: string
}

export interface OAuthUnbindCallbackResponse {
  canUnbind?: boolean
  reason?: string
  message?: string
}

export type QQLoginCallbackResponse = OAuthLoginCallbackResponse
export type QQBindCallbackResponse = OAuthBindCallbackResponse
export type QQUnbindCallbackResponse = OAuthUnbindCallbackResponse

export type QQCallbackResponse =
  | QQLoginCallbackResponse
  | QQBindCallbackResponse
  | QQUnbindCallbackResponse

export type GithubCallbackRequest = QQCallbackRequest
export type GithubLoginCallbackResponse = OAuthLoginCallbackResponse
export type GithubBindCallbackResponse = OAuthBindCallbackResponse
export type GithubUnbindCallbackResponse = OAuthUnbindCallbackResponse

export type GithubCallbackResponse =
  | GithubLoginCallbackResponse
  | GithubBindCallbackResponse
  | GithubUnbindCallbackResponse

export type GoogleCallbackRequest = QQCallbackRequest
export type GoogleLoginCallbackResponse = OAuthLoginCallbackResponse
export type GoogleBindCallbackResponse = OAuthBindCallbackResponse
export type GoogleUnbindCallbackResponse = OAuthUnbindCallbackResponse

export type GoogleCallbackResponse =
  | GoogleLoginCallbackResponse
  | GoogleBindCallbackResponse
  | GoogleUnbindCallbackResponse

export interface MicrosoftCallbackRequest {
  code: string
  state: string
  codeVerifier?: string
}
export type MicrosoftLoginCallbackResponse = OAuthLoginCallbackResponse
export type MicrosoftBindCallbackResponse = OAuthBindCallbackResponse
export type MicrosoftUnbindCallbackResponse = OAuthUnbindCallbackResponse

export type MicrosoftCallbackResponse =
  | MicrosoftLoginCallbackResponse
  | MicrosoftBindCallbackResponse
  | MicrosoftUnbindCallbackResponse

/**
 * QQ OAuth 登录回调处理
 * POST /oauth/qq/callback/login
 */
export const handleQQLoginCallback = async (
  data: QQCallbackRequest,
): Promise<QQLoginCallbackResponse> => {
  const response = await request.post<ApiResponse<QQLoginCallbackResponse>>(
    '/oauth/qq/callback/login',
    data,
  )
  return (response as unknown as ApiResponse<QQLoginCallbackResponse>).data
}

/**
 * QQ OAuth 绑定回调处理
 * POST /oauth/qq/callback/bind
 */
export const handleQQBindCallback = async (
  data: QQCallbackRequest,
): Promise<QQBindCallbackResponse> => {
  const response = await request.post<ApiResponse<QQBindCallbackResponse>>(
    '/oauth/qq/callback/bind',
    data,
  )
  return (response as unknown as ApiResponse<QQBindCallbackResponse>).data
}

/**
 * QQ OAuth 解绑回调处理
 * POST /oauth/qq/callback/unbind
 */
export const handleQQUnbindCallback = async (data?: {
  state?: string
}): Promise<QQUnbindCallbackResponse> => {
  const response = await request.post<ApiResponse<QQUnbindCallbackResponse>>(
    '/oauth/qq/callback/unbind',
    data,
  )
  return (response as unknown as ApiResponse<QQUnbindCallbackResponse>).data
}

/**
 * 按 state 中的 operation 将回调请求路由到对应端点
 */
export const handleQQCallbackByOperation = async (
  operation: QQOAuthOperation,
  data: QQCallbackRequest,
): Promise<QQCallbackResponse> => {
  if (operation === 'login') {
    return handleQQLoginCallback(data)
  }

  if (operation === 'bind') {
    return handleQQBindCallback(data)
  }

  if (operation === 'unbind') {
    return handleQQUnbindCallback({ state: data.state })
  }

  throw new Error('不支持的 QQ 回调操作类型')
}

/**
 * 构建 GitHub 授权 URL
 * 用于生成 GitHub OAuth 授权链接
 */
export const buildGithubAuthorizationUrl = (state: string): string => {
  const clientId = import.meta.env.VITE_OAUTH_GITHUB_CLIENT_ID
  const redirectUri = 'https://auth.ksuser.cn/oauth/github/callback'

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    state,
    scope: 'read:user user:email',
  })

  return `https://github.com/login/oauth/authorize?${params.toString()}`
}

/**
 * GitHub OAuth 登录回调处理
 * POST /oauth/github/callback/login
 */
export const handleGithubLoginCallback = async (
  data: GithubCallbackRequest,
): Promise<GithubLoginCallbackResponse> => {
  const response = await request.post<ApiResponse<GithubLoginCallbackResponse>>(
    '/oauth/github/callback/login',
    data,
  )
  return (response as unknown as ApiResponse<GithubLoginCallbackResponse>).data
}

/**
 * GitHub OAuth 绑定回调处理
 * POST /oauth/github/callback/bind
 */
export const handleGithubBindCallback = async (
  data: GithubCallbackRequest,
): Promise<GithubBindCallbackResponse> => {
  const response = await request.post<ApiResponse<GithubBindCallbackResponse>>(
    '/oauth/github/callback/bind',
    data,
  )
  return (response as unknown as ApiResponse<GithubBindCallbackResponse>).data
}

/**
 * GitHub OAuth 解绑回调处理
 * POST /oauth/github/callback/unbind
 */
export const handleGithubUnbindCallback = async (data?: {
  state?: string
}): Promise<GithubUnbindCallbackResponse> => {
  const response = await request.post<ApiResponse<GithubUnbindCallbackResponse>>(
    '/oauth/github/callback/unbind',
    data,
  )
  return (response as unknown as ApiResponse<GithubUnbindCallbackResponse>).data
}

/**
 * 按 state 中的 operation 将 GitHub 回调请求路由到对应端点
 */
export const handleGithubCallbackByOperation = async (
  operation: GithubOAuthOperation,
  data: GithubCallbackRequest,
): Promise<GithubCallbackResponse> => {
  if (operation === 'login') {
    return handleGithubLoginCallback(data)
  }

  if (operation === 'bind') {
    return handleGithubBindCallback(data)
  }

  if (operation === 'unbind') {
    return handleGithubUnbindCallback({ state: data.state })
  }

  throw new Error('不支持的 GitHub 回调操作类型')
}

/**
 * 构建 Google 授权 URL
 * 用于生成 Google OAuth 授权链接
 */
export const buildGoogleAuthorizationUrl = (state: string): string => {
  const clientId = import.meta.env.VITE_OAUTH_GOOGLE_CLIENT_ID
  const redirectUri = 'https://auth.ksuser.cn/oauth/google/callback'

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'openid email profile',
    state,
    access_type: 'offline',
    prompt: 'consent',
  })

  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`
}

/**
 * Google OAuth 登录回调处理
 * POST /oauth/google/callback/login
 */
export const handleGoogleLoginCallback = async (
  data: GoogleCallbackRequest,
): Promise<GoogleLoginCallbackResponse> => {
  const response = await request.post<ApiResponse<GoogleLoginCallbackResponse>>(
    '/oauth/google/callback/login',
    data,
  )
  return (response as unknown as ApiResponse<GoogleLoginCallbackResponse>).data
}

/**
 * Google OAuth 绑定回调处理
 * POST /oauth/google/callback/bind
 */
export const handleGoogleBindCallback = async (
  data: GoogleCallbackRequest,
): Promise<GoogleBindCallbackResponse> => {
  const response = await request.post<ApiResponse<GoogleBindCallbackResponse>>(
    '/oauth/google/callback/bind',
    data,
  )
  return (response as unknown as ApiResponse<GoogleBindCallbackResponse>).data
}

/**
 * Google OAuth 解绑回调处理
 * POST /oauth/google/callback/unbind
 */
export const handleGoogleUnbindCallback = async (data?: {
  state?: string
}): Promise<GoogleUnbindCallbackResponse> => {
  const response = await request.post<ApiResponse<GoogleUnbindCallbackResponse>>(
    '/oauth/google/callback/unbind',
    data,
  )
  return (response as unknown as ApiResponse<GoogleUnbindCallbackResponse>).data
}

/**
 * 按 state 中的 operation 将 Google 回调请求路由到对应端点
 */
export const handleGoogleCallbackByOperation = async (
  operation: GoogleOAuthOperation,
  data: GoogleCallbackRequest,
): Promise<GoogleCallbackResponse> => {
  if (operation === 'login') {
    return handleGoogleLoginCallback(data)
  }

  if (operation === 'bind') {
    return handleGoogleBindCallback(data)
  }

  if (operation === 'unbind') {
    return handleGoogleUnbindCallback({ state: data.state })
  }

  throw new Error('不支持的 Google 回调操作类型')
}

/**
 * 构建 Microsoft 授权 URL
 * 用于生成 Microsoft OAuth 授权链接
 */
export const buildMicrosoftAuthorizationUrl = (state: string, codeChallenge: string): string => {
  const clientId = import.meta.env.VITE_OAUTH_MICROSOFT_CLIENT_ID
  const tenantId = import.meta.env.VITE_OAUTH_MICROSOFT_TENANT_ID || 'common'
  const redirectUri = 'https://auth.ksuser.cn/oauth/microsoft/callback'

  const params = new URLSearchParams({
    client_id: clientId,
    response_type: 'code',
    redirect_uri: redirectUri,
    response_mode: 'query',
    scope: 'openid profile email',
    state,
  })

  params.set('code_challenge', codeChallenge)
  params.set('code_challenge_method', 'S256')

  return `https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/authorize?${params.toString()}`
}

/**
 * Microsoft OAuth 登录回调处理
 * POST /oauth/microsoft/callback/login
 */
export const handleMicrosoftLoginCallback = async (
  data: MicrosoftCallbackRequest,
): Promise<MicrosoftLoginCallbackResponse> => {
  const response = await request.post<ApiResponse<MicrosoftLoginCallbackResponse>>(
    '/oauth/microsoft/callback/login',
    data,
  )
  return (response as unknown as ApiResponse<MicrosoftLoginCallbackResponse>).data
}

/**
 * Microsoft OAuth 绑定回调处理
 * POST /oauth/microsoft/callback/bind
 */
export const handleMicrosoftBindCallback = async (
  data: MicrosoftCallbackRequest,
): Promise<MicrosoftBindCallbackResponse> => {
  const response = await request.post<ApiResponse<MicrosoftBindCallbackResponse>>(
    '/oauth/microsoft/callback/bind',
    data,
  )
  return (response as unknown as ApiResponse<MicrosoftBindCallbackResponse>).data
}

/**
 * Microsoft OAuth 解绑回调处理
 * POST /oauth/microsoft/callback/unbind
 */
export const handleMicrosoftUnbindCallback = async (data?: {
  code?: string
  state?: string
  codeVerifier?: string
}): Promise<MicrosoftUnbindCallbackResponse> => {
  const response = await request.post<ApiResponse<MicrosoftUnbindCallbackResponse>>(
    '/oauth/microsoft/callback/unbind',
    data,
  )
  return (response as unknown as ApiResponse<MicrosoftUnbindCallbackResponse>).data
}

/**
 * 按 state 中的 operation 将 Microsoft 回调请求路由到对应端点
 */
export const handleMicrosoftCallbackByOperation = async (
  operation: MicrosoftOAuthOperation,
  data: MicrosoftCallbackRequest,
): Promise<MicrosoftCallbackResponse> => {
  if (operation === 'login') {
    return handleMicrosoftLoginCallback(data)
  }

  if (operation === 'bind') {
    return handleMicrosoftBindCallback(data)
  }

  if (operation === 'unbind') {
    return handleMicrosoftUnbindCallback({
      code: data.code,
      state: data.state,
      codeVerifier: data.codeVerifier,
    })
  }

  throw new Error('不支持的 Microsoft 回调操作类型')
}

// 兼容旧调用（默认按登录回调处理）
export const handleQQCallback = async (
  data: QQCallbackRequest,
): Promise<QQLoginCallbackResponse> => {
  const response = await request.post<ApiResponse<QQLoginCallbackResponse>>(
    '/oauth/qq/callback/login',
    data,
  )
  return (response as unknown as ApiResponse<QQLoginCallbackResponse>).data
}

export interface OAuthAccountStatusItem {
  provider: 'wechat' | 'qq' | 'microsoft' | 'github' | 'google'
  bound: boolean
  lastLoginAt: string | null
}

/**
 * 获取当前用户第三方账号绑定状态
 * GET /oauth/accounts/status
 */
export const getOAuthAccountsStatus = async (): Promise<OAuthAccountStatusItem[]> => {
  const response =
    await request.get<ApiResponse<OAuthAccountStatusItem[]>>('/oauth/accounts/status')
  return (response as unknown as ApiResponse<OAuthAccountStatusItem[]>).data
}

/**
 * 解绑 QQ 账号
 * POST /oauth/qq/unbind
 */
export const unbindQQ = async (): Promise<void> => {
  await request.post('/oauth/qq/unbind')
}

/**
 * 解绑 GitHub 账号
 * POST /oauth/github/unbind
 */
export const unbindGithub = async (): Promise<void> => {
  await request.post('/oauth/github/unbind')
}

/**
 * 解绑 Microsoft 账号
 * POST /oauth/microsoft/unbind
 */
export const unbindMicrosoft = async (): Promise<void> => {
  await request.post('/oauth/microsoft/unbind')
}

/**
 * 解绑 Google 账号
 * POST /oauth/google/unbind
 */
export const unbindGoogle = async (): Promise<void> => {
  await request.post('/oauth/google/unbind')
}
