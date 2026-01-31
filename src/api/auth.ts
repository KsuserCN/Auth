import request from '@/utils/request'

// 用户信息接口
export interface User {
  id: number
  uuid: string
  username: string
  email: string
}

// 登录响应接口
export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: User
}

// ========== 邮箱密码登录 ==========
export interface PasswordLoginRequest {
  email: string
  password: string
}

export const passwordLogin = (data: PasswordLoginRequest): Promise<LoginResponse> => {
  return request.post('/auth/login', data)
}

// ========== 邮箱验证码相关 ==========
export interface SendCodeRequest {
  email: string
}

export interface SendCodeResponse {
  message: string
  expiresIn: number
  code?: string // 仅开发环境返回
}

export const sendEmailCode = (data: SendCodeRequest): Promise<SendCodeResponse> => {
  return request.post('/auth/email/send-code', data)
}

export interface EmailCodeLoginRequest {
  email: string
  code: string
}

export const emailCodeLogin = (data: EmailCodeLoginRequest): Promise<LoginResponse> => {
  return request.post('/auth/email/login', data)
}

// ========== Passkey 登录相关 ==========
export interface PasskeyLoginOptionsRequest {
  username?: string
}

export interface PasskeyLoginOptionsResponse {
  challenge: string
  rp: {
    id: string
    name?: string
  }
  timeout?: number
  allowCredentials?: Array<{
    type: string
    id: string
  }>
}

export const getPasskeyLoginOptions = (
  data: PasskeyLoginOptionsRequest,
): Promise<PasskeyLoginOptionsResponse> => {
  return request.post('/auth/passkey/login/options', data)
}

export interface PasskeyLoginVerifyRequest {
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

export const verifyPasskeyLogin = (data: PasskeyLoginVerifyRequest): Promise<LoginResponse> => {
  return request.post('/auth/passkey/login/verify', data)
}

// ========== 用户信息 ==========
export const getUserInfo = (): Promise<User> => {
  return request.get('/auth/info')
}

// ========== Token 刷新 ==========
export const refreshToken = (): Promise<LoginResponse> => {
  return request.post('/auth/refresh')
}

// ========== 登出 ==========
export const logout = (): Promise<{ message: string }> => {
  return request.post('/auth/logout')
}
