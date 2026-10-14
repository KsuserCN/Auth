import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  safeReturnPath,
  preserveApplicationDraft,
  consumeApplicationDraft,
  preserveRoleDraft,
  consumeRoleDraft,
  beginVerification,
  consumeVerificationReturn,
} from './navigation'

const mocks = vi.hoisted(() => ({ verification: vi.fn(), assign: vi.fn() }))
vi.mock('@/api/console', () => ({
  consoleApi: { verification: mocks.verification },
}))
beforeEach(() => {
  sessionStorage.clear()
  vi.clearAllMocks()
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('console verification navigation', () => {
  it('restricts the verification return to a local protected page', () => {
    expect(safeReturnPath('/applications?tab=all')).toBe('/applications?tab=all')
    for (const path of [
      '//evil.example',
      'https://evil.example',
      '/\\evil',
      '/auth/callback',
      '/login',
      null,
    ])
      expect(safeReturnPath(path)).toBe('/users')
  })
  it('keeps only application form data and consumes it once', () => {
    preserveApplicationDraft({
      clientId: 'campus',
      clientName: '校园服务',
      logoUrl: '',
      clientType: 'public',
      allowMissingNonce: false,
      redirectText: 'https://campus.example/callback',
      scopes: ['openid'],
      isActive: true,
      editing: false,
      clientSecret: 'must-not-store',
    } as Parameters<typeof preserveApplicationDraft>[0])
    expect(JSON.stringify(sessionStorage)).not.toContain('must-not-store')
    expect(consumeApplicationDraft()).toMatchObject({
      clientId: 'campus',
      scopes: ['openid'],
    })
    expect(consumeApplicationDraft()).toBeNull()
  })
  it('preserves a role confirmation without storing profile records', () => {
    preserveRoleDraft('user-uuid', 'ADMIN')
    expect(consumeRoleDraft()).toEqual({
      uuid: 'user-uuid',
      systemRole: 'ADMIN',
    })
    expect(consumeRoleDraft()).toBeNull()
  })
  it('starts verification, then restores the local page without replaying any mutation', async () => {
    mocks.verification.mockResolvedValue({
      authorizationUrl: 'https://console.ksuser.cn/verification?request_id=flow',
    })
    vi.stubGlobal('window', { location: { assign: mocks.assign } })
    await beginVerification('/applications')
    expect(mocks.assign).toHaveBeenCalledWith(
      'https://console.ksuser.cn/verification?request_id=flow',
    )
    expect(consumeVerificationReturn()).toBe('/applications')
    expect(consumeVerificationReturn()).toBe('/users')
  })
})
