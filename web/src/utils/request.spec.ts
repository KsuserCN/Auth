import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiResultMessages } from '@/generated/apiResultCodes'

const toast = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: toast }))

const rejectedResponse = (config: InternalAxiosRequestConfig, status: number, data: object) => {
  throw new AxiosError('transport error prose', 'ERR_BAD_RESPONSE', config, undefined, {
    config,
    status,
    statusText: 'Error',
    headers: {},
    data,
  })
}

const createRequest = async (adapter: AxiosAdapter) => {
  const { default: request } = await import('./request')
  request.defaults.adapter = adapter
  return request
}

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()
  localStorage.clear()
  sessionStorage.clear()
  document.cookie = 'XSRF-TOKEN=test; path=/'
})

describe('API response interceptor', () => {
  it('uses detailed local copy for login 401 and does not attempt refresh', async () => {
    const adapter = vi.fn<AxiosAdapter>(async (config) =>
      rejectedResponse(config, 401, {
        code: 401,
        errorCode: 'UNAUTHENTICATED',
        msg: 'raw backend text must not appear',
      }),
    )
    const request = await createRequest(adapter)
    await expect(request.post('/auth/login', {})).rejects.toMatchObject({
      errorCode: 'UNAUTHENTICATED',
      status: 401,
      message: apiResultMessages.UNAUTHENTICATED,
    })
    expect(adapter).toHaveBeenCalledTimes(1)
    expect(toast.error).toHaveBeenCalledWith(apiResultMessages.UNAUTHENTICATED)
  })

  it('keeps 201/202 result metadata available for MFA and verification flows', async () => {
    const data = {
      code: 201,
      errorCode: 'AUTH_MFA_REQUIRED',
      data: { challengeId: 'mfa-challenge' },
    }
    const request = await createRequest(async (config) => ({
      data,
      config,
      status: 201,
      statusText: 'Created',
      headers: {},
    }))
    await expect(request.post('/auth/login', {})).resolves.toEqual(data)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('uses HTTP fallback for unrecognized codes and honors suppressed error toasts', async () => {
    const request = await createRequest(async (config) =>
      rejectedResponse(config, 503, { errorCode: 'NEW_UNKNOWN_CODE', msg: 'untrusted prose' }),
    )
    await expect(
      request.get('/auth/health', { _suppressErrorToast: true } as never),
    ).rejects.toMatchObject({ message: apiResultMessages.SERVER_ERROR, status: 503 })
    expect(toast.error).not.toHaveBeenCalled()
  })

  it.each([
    ['ERR_NETWORK', 'NETWORK_ERROR'],
    ['ECONNABORTED', 'REQUEST_TIMEOUT'],
  ])('normalizes %s transport failures to local copy', async (transportCode, errorCode) => {
    const request = await createRequest(async (config) => {
      throw new AxiosError('untrusted transport message', transportCode, config)
    })
    await expect(request.get('/auth/info')).rejects.toMatchObject({
      errorCode,
      message: apiResultMessages[errorCode],
    })
  })

  it('rejects malformed success envelopes using local copy', async () => {
    const request = await createRequest(async (config) => ({
      config,
      status: 200,
      statusText: 'OK',
      headers: {},
      data: null,
    }))
    await expect(request.get('/auth/info')).rejects.toMatchObject({
      errorCode: 'INVALID_RESPONSE',
      message: apiResultMessages.INVALID_RESPONSE,
    })
  })

  it('refreshes and replays protected 401 requests once', async () => {
    sessionStorage.setItem('accessToken', 'old-token')
    const urls: string[] = []
    const request = await createRequest(async (config) => {
      urls.push(config.url!)
      if (config.url === '/auth/refresh') {
        return {
          config,
          status: 200,
          statusText: 'OK',
          headers: {},
          data: { code: 200, errorCode: 'REQUEST_SUCCESS', data: { accessToken: 'new-token' } },
        }
      }
      if (urls.length === 1) {
        return rejectedResponse(config, 401, { code: 401, errorCode: 'UNAUTHENTICATED' })
      }
      expect(config.headers.Authorization).toBe('Bearer new-token')
      return {
        config,
        status: 200,
        statusText: 'OK',
        headers: {},
        data: { code: 200, errorCode: 'REQUEST_SUCCESS', data: { uuid: 'user-1' } },
      }
    })
    await expect(request.get('/auth/userinfo')).resolves.toMatchObject({
      code: 200,
      data: { uuid: 'user-1' },
    })
    expect(urls).toEqual(['/auth/userinfo', '/auth/refresh', '/auth/userinfo'])
    expect(toast.error).not.toHaveBeenCalled()
  })
})
