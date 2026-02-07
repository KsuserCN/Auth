import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// 用户基本信息类型
export interface User {
  uuid: string
  username: string
  email: string
  avatarUrl?: string | null
  settings?: UserSettings
}

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
}

// 登录响应类型
export interface LoginResponse {
  accessToken: string
  user?: User
}

// MFA 验证挑战响应
export interface MFAChallenge {
  challengeId: string
  method: 'totp' | 'backup_code'
}

// TOTP 验证请求
export interface TOTPVerifyRequest {
  challengeId: string
  code?: string
  recoveryCode?: string
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

// ========== Passkey 注册 ==========
export interface PasskeyRegistrationOptionsRequest {
  passkeyName: string
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
  const response = await request.post<ApiResponse<{ accessToken: string }>>('/auth/refresh')
  return response.data as unknown as { accessToken: string }
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
    | 'detect_unusual_login'
    | 'detectUnusualLogin'
    | 'notify_sensitive_action_email'
    | 'notifySensitiveActionEmail'
    | 'subscribe_news_email'
    | 'subscribeNewsEmail'
  value: boolean
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

// ========== 敏感操作验证 ==========

/**
 * 敏感操作验证
 * POST /auth/verify-sensitive
 */
export interface VerifySensitiveRequest {
  method: 'password' | 'email-code' | 'totp'
  password?: string
  code?: string
}

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
}

export const checkSensitiveVerification = async (): Promise<SensitiveVerificationStatus> => {
  const response = await request.get<any>('/auth/check-sensitive-verification')
  return response.data as SensitiveVerificationStatus
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

export type SensitiveLoginMethod = 'PASSWORD' | 'EMAIL_CODE' | 'PASSKEY' | 'PASSKEY_MFA'

export interface SensitiveLogItem {
  id: number
  operationType: SensitiveOperationType
  loginMethod: SensitiveLoginMethod | null
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
 */
export interface TotpRegistrationVerifyRequest {
  code: string
}

export const verifyTotpRegistration = async (
  data: TotpRegistrationVerifyRequest,
): Promise<void> => {
  await request.post('/auth/totp/registration-verify', data)
}

/**
 * TOTP 验证（MFA 登录流程）
 * POST /auth/totp/mfa-verify
 * 用于在登录时完成 MFA 验证，需传入 challengeId 和 code
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
  code?: string
  recoveryCode?: string
}

export interface TotpVerifyResponse {
  success: boolean
  message: string
}

export const verifyTotp = async (data: TotpVerifyRequest): Promise<TotpVerifyResponse> => {
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
