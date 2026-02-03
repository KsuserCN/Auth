import request from '@/utils/request'
import type { ApiResponse } from '@/utils/request'

// 用户信息类型
export interface User {
  uuid: string
  username: string
  email: string
  avatarUrl?: string | null
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

export interface SendEmailCodeRequest {
  email: string
}

/**
 * 发送邮箱验证码
 */
export const sendEmailCode = async (data: SendEmailCodeRequest): Promise<void> => {
  await request.post('/auth/send-email-code', data)
}

export interface EmailCodeLoginRequest {
  email: string
  code: string
}

/**
 * 邮箱验证码登录
 */
export const emailCodeLogin = async (data: EmailCodeLoginRequest): Promise<LoginResponse> => {
  const response = await request.post<ApiResponse<LoginResponse>>('/auth/email-login', data)
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
 * 获取当前用户信息
 * GET /auth/info
 */
export const getUserInfo = async (): Promise<User> => {
  const response = await request.get<ApiResponse<User>>('/auth/info')
  return response.data
}

// ========== 退出登录 ==========

/**
 * 退出登录
 * POST /auth/logout
 */
export const logout = async (): Promise<void> => {
  await request.post('/auth/logout')
}
