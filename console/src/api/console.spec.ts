import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosHeaders } from 'axios'

type RequestConfig = { method: string; headers: AxiosHeaders }
type ResultResponse = {
  data: { code: number; errorCode: string }
  status: number
}
const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  requestInterceptor: undefined as ((config: RequestConfig) => Promise<RequestConfig>) | undefined,
  responseInterceptor: undefined as ((response: ResultResponse) => ResultResponse) | undefined,
}))
vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return { ...actual, default: { create: mocks.create } }
})
const result = <T>(data: T) => ({
  data: { code: 200, errorCode: 'REQUEST_SUCCESS', data },
  status: 200,
})
beforeEach(async () => {
  vi.resetModules()
  vi.clearAllMocks()
  mocks.create.mockReturnValue({
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.del,
    interceptors: {
      request: {
        use: (fn: (config: RequestConfig) => Promise<RequestConfig>) => {
          mocks.requestInterceptor = fn
        },
      },
      response: {
        use: (fn: (response: ResultResponse) => ResultResponse) => {
          mocks.responseInterceptor = fn
        },
      },
    },
  })
  mocks.get.mockResolvedValue(result({ token: 'csrf-token' }))
})
describe('console API boundary', () => {
  it('uses cookies and places a server CSRF value on mutating requests without adding a bearer token', async () => {
    await import('./console')
    expect(mocks.create).toHaveBeenCalledWith(
      expect.objectContaining({
        withCredentials: true,
        xsrfHeaderName: 'X-XSRF-TOKEN',
      }),
    )
    const config = await mocks.requestInterceptor!({
      method: 'post',
      headers: new AxiosHeaders(),
    })
    expect(mocks.get).toHaveBeenCalledWith('/console/csrf-token')
    expect(config.headers.get('X-XSRF-TOKEN')).toBe('csrf-token')
    expect(config.headers.has('Authorization')).toBe(false)
  })
  it('exchanges login codes and verification tickets through distinct session APIs', async () => {
    const { consoleApi } = await import('./console')
    mocks.post.mockResolvedValue(
      result({
        user: { systemRole: 'SUPER_ADMIN' },
        expiresAt: 10,
        freshUntil: 0,
      }),
    )
    await consoleApi.exchange('code', 'login-state')
    await consoleApi.verificationExchange('ticket', 'verification-state')
    expect(mocks.post).toHaveBeenNthCalledWith(1, '/console/auth/exchange', {
      code: 'code',
      state: 'login-state',
    })
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/console/verification/exchange', {
      ticket: 'ticket',
      state: 'verification-state',
    })
  })
  it('rejects result codes that require verification without replaying a write', async () => {
    const { ConsoleApiError } = await import('./console')
    expect(() =>
      mocks.responseInterceptor!({
        data: { code: 403, errorCode: 'SENSITIVE_VERIFICATION_REQUIRED' },
        status: 403,
      }),
    ).toThrow(ConsoleApiError)
    expect(mocks.post).not.toHaveBeenCalled()
    expect(mocks.put).not.toHaveBeenCalled()
  })
  it('keeps QR polling separate from the one-time cookie session exchange', async () => {
    const { consoleApi } = await import('./console')
    mocks.post.mockResolvedValue(result({ status: 'pending' }))
    await consoleApi.initQrLogin()
    await consoleApi.pollQrLogin('challenge', 'poll-secret')
    await consoleApi.exchangeQrLogin('challenge', 'poll-secret')
    await consoleApi.cancelQrLogin()
    await consoleApi.cancelQrLogin('challenge')
    expect(mocks.post).toHaveBeenNthCalledWith(1, '/console/auth/qr/init')
    expect(mocks.get).toHaveBeenCalledWith('/console/auth/qr/status', { params: { challengeId: 'challenge', pollToken: 'poll-secret' } })
    expect(mocks.post).toHaveBeenNthCalledWith(2, '/console/auth/qr/exchange', { challengeId: 'challenge', pollToken: 'poll-secret' })
    expect(mocks.del).toHaveBeenCalledWith('/console/auth/qr')
    expect(mocks.del).toHaveBeenCalledWith('/console/auth/qr', { params: { challengeId: 'challenge' } })
  })
})
