import { apiResultMessages } from '@/generated/apiResultCodes'

export interface ApiResultPayload {
  errorCode?: string
  params?: Record<string, unknown>
  code?: number
}

const fallbackCode = (status?: number): string => {
  if (status != null && status >= 200 && status < 300) return 'REQUEST_SUCCESS'
  if (status === 401) return 'UNAUTHENTICATED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  if (status === 405) return 'METHOD_NOT_ALLOWED'
  if (status === 408) return 'REQUEST_TIMEOUT'
  if (status === 409) return 'REQUEST_CONFLICT'
  if (status === 413) return 'REQUEST_UPLOAD_TOO_LARGE'
  if (status === 415) return 'UNSUPPORTED_MEDIA_TYPE'
  if (status === 429) return 'RATE_LIMITED'
  if (status != null && status >= 500) return 'SERVER_ERROR'
  if (status != null && status >= 400) return 'BAD_REQUEST'
  return 'REQUEST_FAILED'
}

/** Resolve only locally maintained copy; server-provided text is never displayed. */
export const resolveApiResult = (payload?: ApiResultPayload | null, status?: number): string => {
  const resultCode = typeof payload?.errorCode === 'string' ? payload.errorCode : undefined
  const template =
    (resultCode && Object.prototype.hasOwnProperty.call(apiResultMessages, resultCode)
      ? apiResultMessages[resultCode]
      : undefined) || apiResultMessages[fallbackCode(status ?? payload?.code)] || '请求失败，请重试'

  let invalidParams = false
  const message = template.replace(/\{([a-zA-Z0-9_]+)\}/g, (_placeholder: string, key: string) => {
    const params = payload?.params
    const value = params && Object.prototype.hasOwnProperty.call(params, key) ? params[key] : undefined
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      return String(value)
    }
    invalidParams = true
    return ''
  })
  return invalidParams
    ? apiResultMessages[fallbackCode(status ?? payload?.code)] || '请求失败，请重试'
    : message
}

export class ApiError extends Error {
  readonly errorCode: string
  readonly params: Record<string, unknown>
  readonly status: number | undefined

  constructor(payload?: ApiResultPayload | null, status?: number) {
    super(resolveApiResult(payload, status))
    this.name = 'ApiError'
    this.status = status ?? payload?.code
    this.errorCode = typeof payload?.errorCode === 'string' && payload.errorCode
      ? payload.errorCode
      : fallbackCode(this.status)
    this.params = payload?.params || {}
  }
}

/** Preserve envelope result metadata when an API helper unwraps an object payload. */
export const unwrapApiResult = <T extends object>(
  response: ApiResultPayload & { data: T },
): T & ApiResultPayload => ({
  ...response.data,
  code: response.code,
  errorCode: response.errorCode,
  params: response.params,
})

/** Audit records can contain historical prose or an exact result code. */
export const resolveAuditReason = (reason: string | null): string | null =>
  reason && Object.prototype.hasOwnProperty.call(apiResultMessages, reason)
    ? resolveApiResult({ errorCode: reason })
    : reason
