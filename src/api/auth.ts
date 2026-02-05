import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// 用户基本信息类型
export interface User {
  uuid: string
  username: string
  email: string
  avatarUrl?: string | null
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

// 登录响应类型
export interface LoginResponse {
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
 */
export const passwordLogin = async (data: PasswordLoginRequest): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<{ accessToken: string }>>('/auth/login', data)
  return response.data
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
 */
export interface LoginWithCodeRequest {
  email: string
  code: string
}

export const loginWithCode = async (data: LoginWithCodeRequest): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<{ accessToken: string }>>(
    '/auth/login-with-code',
    data,
  )
  return response.data
}

// ========== Passkey 登录 ==========

export interface PasskeyLoginOptionsRequest {
  username: string
}

export interface PasskeyLoginOptions {
  challenge: string
  timeout?: number
  rpId?: string
  rp?: {
    id: string
    name: string
  }
  allowCredentials?: Array<{
    type: string
    id: string
  }>
}

/**
 * 获取 Passkey 登录选项
 */
export const getPasskeyLoginOptions = async (
  data: PasskeyLoginOptionsRequest,
): Promise<PasskeyLoginOptions> => {
  const response = await request.post<ApiResponse<PasskeyLoginOptions>>(
    '/auth/passkey-login-options',
    data,
  )
  return response.data
}

export interface PasskeyCredential {
  id: string
  rawId: string
  response: {
    authenticatorData: string
    clientDataJSON: string
    signature: string
    userHandle?: string
  }
  type: string
}

/**
 * 验证 Passkey 登录
 */
export const verifyPasskeyLogin = async (data: PasskeyCredential): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>('/auth/passkey-login', data)
  return response.data
}

// ========== 刷新 Token ==========

/**
 * 刷新 Token
 * POST /auth/refresh
 */
export const refreshAccessToken = async (): Promise<{ accessToken: string }> => {
  const response = await request.post<ApiResponse<{ accessToken: string }>>('/auth/refresh')
  return response.data
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
  method: 'password' | 'email-code'
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
  return response.data as User
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

// ========== 退出登录 ==========

/**
 * 退出登录
 * POST /auth/logout
 */
export const logout = async (): Promise<void> => {
  await request.post('/auth/logout')
}
