import axios, { type InternalAxiosRequestConfig } from 'axios'
import { apiResultMessagesByLocale } from '@/generated/apiResultCodes'

export interface ApiResult<T> {
  code: number
  errorCode: string
  data: T
  params?: Record<string, unknown>
}
export class ConsoleApiError extends Error {
  constructor(
    public readonly errorCode: string,
    public readonly status?: number,
  ) {
    super(apiResultMessagesByLocale['zh-Hans'][errorCode] ?? '请求失败，请重试')
    this.name = 'ConsoleApiError'
  }
}

export type SystemRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN'
export interface SessionUser {
  uuid: string
  username: string
  email: string
  avatarUrl?: string
  systemRole: SystemRole
}
export interface ConsoleSession {
  user: SessionUser
  expiresAt: number
  freshUntil: number
}
export interface ConsoleVerificationContext {
  requestId: string
  hasPassword: boolean
  hasTotp: boolean
  hasPasskey: boolean
}
export interface ConsoleVerificationStart {
  requestId: string
  authorizationUrl: string
}
export interface ConsoleQrChallenge {
  challengeId: string
  pollToken: string
  qrText: string
  expiresInSeconds: number
}
export interface ConsoleQrStatus {
  status: 'pending' | 'approved' | 'rejected' | 'expired'
  expiresInSeconds: number
  session?: ConsoleSession | null
}
export interface ConsolePasskeyOptions {
  challenge: string
  challengeId: string
  timeout: string | number
  rpId: string
  userVerification: string
  allowCredentials?: string
}
export interface ConsolePasskeyCredential {
  credentialRawId: string
  clientDataJSON: string
  authenticatorData: string
  signature: string
}
export interface ConsoleUser extends SessionUser {
  realName?: string
  gender?: string
  birthDate?: string
  region?: string
  bio?: string
  developerVerificationType: string
  createdAt: string
  updatedAt: string
}
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}
export type OfficialScope =
  | 'openid'
  | 'profile'
  | 'email'
  | 'real_name'
  | 'gender'
  | 'birth_date'
  | 'region'
  | 'bio'
export interface OfficialApplication {
  clientId: string
  clientName: string
  logoUrl?: string
  clientType: 'public' | 'confidential'
  redirectUris: string[]
  scopes: OfficialScope[]
  isActive: boolean
  reserved: boolean
  createdAt: string
  updatedAt: string
}
export interface ApplicationInput {
  clientId?: string
  clientName: string
  logoUrl?: string
  clientType?: 'public' | 'confidential'
  redirectUris: string[]
  scopes: OfficialScope[]
  isActive: boolean
}
export interface AuditLog {
  id: number
  actorUuid: string
  target: string
  action: string
  details: string
  ipAddress: string
  createdAt: string
}
export interface ApplicationSecretResult {
  application: OfficialApplication
  clientSecret?: string
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000',
  timeout: 30000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
  headers: { 'Content-Type': 'application/json', 'Accept-Language': 'zh-Hans' },
})

// Read CSRF from the API response so this client does not depend on cookie-domain visibility.
// It is kept in memory and is never an authentication credential.
let csrfToken = ''
let csrfRequest: Promise<string> | undefined
type CsrfRetryConfig = InternalAxiosRequestConfig & { _csrfRetried?: boolean }
export const refreshCsrfToken = async (): Promise<string> => {
  if (!csrfRequest) {
    csrfRequest = request
      .get<ApiResult<{ token: string }>>('/console/csrf-token')
      .then((response) => {
        if (response.data.code !== 200 || !response.data.data?.token)
          throw new ConsoleApiError(response.data.errorCode || 'INVALID_RESPONSE', response.status)
        csrfToken = response.data.data.token
        return csrfToken
      })
      .finally(() => {
        csrfRequest = undefined
      })
  }
  return csrfRequest
}

request.interceptors.request.use(async (config) => {
  if (!['get', 'head', 'options'].includes(config.method?.toLowerCase() ?? 'get')) {
    if (!csrfToken) await refreshCsrfToken()
    config.headers.set('X-XSRF-TOKEN', csrfToken)
  }
  return config
})
request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body.code !== 'number')
      throw new ConsoleApiError('INVALID_RESPONSE', response.status)
    if (body.code !== 200) throw new ConsoleApiError(body.errorCode || 'REQUEST_FAILED', body.code)
    return response
  },
  async (error) => {
    if (error instanceof ConsoleApiError) throw error
    const code =
      error.response?.data?.errorCode ||
      (error.code === 'ECONNABORTED'
        ? 'REQUEST_TIMEOUT'
        : error.response
          ? 'REQUEST_FAILED'
          : 'NETWORK_ERROR')
    if (code === 'SECURITY_CSRF_INVALID') {
      csrfToken = ''
      const originalRequest = error.config as CsrfRetryConfig | undefined
      if (originalRequest && !originalRequest._csrfRetried) {
        // CSRF rejection happens before the controller runs, so refreshing and replaying once
        // cannot duplicate a completed mutation.
        originalRequest._csrfRetried = true
        originalRequest.headers.set('X-XSRF-TOKEN', await refreshCsrfToken())
        return request(originalRequest)
      }
    }
    throw new ConsoleApiError(code, error.response?.status)
  },
)

export const consoleApi = {
  async session(): Promise<ConsoleSession> {
    return (await request.get<ApiResult<ConsoleSession>>('/console/session')).data.data
  },
  async login(): Promise<{ authorizationUrl: string }> {
    return (await request.post<ApiResult<{ authorizationUrl: string }>>('/console/auth/start')).data
      .data
  },
  async exchange(code: string, state: string): Promise<ConsoleSession> {
    return (
      await request.post<ApiResult<ConsoleSession>>('/console/auth/exchange', {
        code,
        state,
      })
    ).data.data
  },
  async logout(): Promise<void> {
    await request.delete('/console/session')
    csrfToken = ''
  },
  async verification(): Promise<ConsoleVerificationStart> {
    return (
      await request.post<ApiResult<ConsoleVerificationStart>>('/console/verification/start')
    ).data.data
  },
  async verificationExchange(ticket: string, state: string): Promise<ConsoleSession> {
    return (
      await request.post<ApiResult<ConsoleSession>>('/console/verification/exchange', {
        ticket,
        state,
      })
    ).data.data
  },
  async verificationContext(requestId: string): Promise<ConsoleVerificationContext> {
    return (
      await request.get<ApiResult<ConsoleVerificationContext>>('/console/verification/context', {
        params: { request_id: requestId },
      })
    ).data.data
  },
  async verifyPassword(requestId: string, password: string): Promise<ConsoleSession> {
    return (
      await request.post<ApiResult<ConsoleSession>>('/console/verification/password', {
        requestId,
        password,
      })
    ).data.data
  },
  async verifyTotp(requestId: string, input: { code?: string; recoveryCode?: string }): Promise<ConsoleSession> {
    return (
      await request.post<ApiResult<ConsoleSession>>('/console/verification/totp', {
        requestId,
        ...input,
      })
    ).data.data
  },
  async passkeyVerificationOptions(requestId: string): Promise<ConsolePasskeyOptions> {
    return (
      await request.post<ApiResult<ConsolePasskeyOptions>>('/console/verification/passkey/options', {
        requestId,
      })
    ).data.data
  },
  async verifyPasskey(
    requestId: string,
    challengeId: string,
    credential: ConsolePasskeyCredential,
  ): Promise<ConsoleSession> {
    return (
      await request.post<ApiResult<ConsoleSession>>('/console/verification/passkey/verify', {
        requestId,
        challengeId,
        credential,
      })
    ).data.data
  },
  async initQrVerification(requestId: string): Promise<ConsoleQrChallenge> {
    return (
      await request.post<ApiResult<ConsoleQrChallenge>>('/console/verification/qr/init', { requestId })
    ).data.data
  },
  async pollQrVerification(
    requestId: string,
    challengeId: string,
    pollToken: string,
  ): Promise<ConsoleQrStatus> {
    return (
      await request.get<ApiResult<ConsoleQrStatus>>('/console/verification/qr/status', {
        params: { request_id: requestId, challengeId, pollToken },
      })
    ).data.data
  },
  async users(params: {
    page: number
    size: number
    search?: string
    role?: string
    sort?: 'username' | 'uuid' | 'systemRole' | 'createdAt'
    direction?: 'asc' | 'desc'
  }): Promise<PageResult<ConsoleUser>> {
    return (
      await request.get<ApiResult<PageResult<ConsoleUser>>>('/console/users', {
        params,
      })
    ).data.data
  },
  async user(uuid: string): Promise<ConsoleUser> {
    return (await request.get<ApiResult<ConsoleUser>>(`/console/users/${encodeURIComponent(uuid)}`))
      .data.data
  },
  async setRole(uuid: string, systemRole: 'USER' | 'ADMIN'): Promise<ConsoleUser> {
    return (
      await request.put<ApiResult<ConsoleUser>>(`/console/users/${encodeURIComponent(uuid)}/role`, {
        systemRole,
      })
    ).data.data
  },
  async deleteUser(uuid: string): Promise<void> {
    await request.delete(`/console/users/${encodeURIComponent(uuid)}`)
  },
  async applications(): Promise<OfficialApplication[]> {
    return (await request.get<ApiResult<OfficialApplication[]>>('/console/official-applications'))
      .data.data
  },
  async uploadOfficialApplicationLogo(file: File): Promise<{ logoUrl: string }> {
    const body = new FormData()
    body.append('file', file)
    return (
      await request.post<ApiResult<{ logoUrl: string }>>(
        '/console/official-applications/logo',
        body,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
    ).data.data
  },
  async createApplication(input: ApplicationInput): Promise<ApplicationSecretResult> {
    return (
      await request.post<ApiResult<ApplicationSecretResult>>(
        '/console/official-applications',
        input,
      )
    ).data.data
  },
  async updateApplication(clientId: string, input: ApplicationInput): Promise<OfficialApplication> {
    return (
      await request.put<ApiResult<OfficialApplication>>(
        `/console/official-applications/${encodeURIComponent(clientId)}`,
        input,
      )
    ).data.data
  },
  async rotateSecret(clientId: string): Promise<ApplicationSecretResult> {
    return (
      await request.post<ApiResult<ApplicationSecretResult>>(
        `/console/official-applications/${encodeURIComponent(clientId)}/rotate-secret`,
      )
    ).data.data
  },
  async audit(
    page: number,
    size: number,
    sort: 'createdAt' | 'action' | 'actorUuid' | 'target' | 'ipAddress' = 'createdAt',
    direction: 'asc' | 'desc' = 'desc',
  ): Promise<PageResult<AuditLog>> {
    return (
      await request.get<ApiResult<PageResult<AuditLog>>>('/console/audit-logs', {
        params: { page, size, sort, direction },
      })
    ).data.data
  },
}
