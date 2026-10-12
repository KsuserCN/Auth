import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ load: vi.fn(), clear: vi.fn() }))
vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({ load: mocks.load, clear: mocks.clear }),
}))
beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()
  window.history.replaceState({}, '', '/')
})
describe('console router access', () => {
  it('checks the current session for every protected navigation', async () => {
    mocks.load.mockResolvedValue({ user: { systemRole: 'SUPER_ADMIN' } })
    const router = (await import('./index')).default
    await router.push('/users')
    await router.push('/audit')
    expect(router.currentRoute.value.path).toBe('/audit')
    expect(mocks.load).toHaveBeenCalledTimes(2)
  })
  it('denies a logged-in ordinary administrator', async () => {
    mocks.load.mockResolvedValue({ user: { systemRole: 'ADMIN' } })
    const router = (await import('./index')).default
    await router.push('/applications')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.error).toBe('CONSOLE_ACCESS_DENIED')
    expect(mocks.clear).toHaveBeenCalledOnce()
  })
  it('shows an explicit login error when the session request fails', async () => {
    const { ConsoleApiError } = await import('@/api/console')
    mocks.load.mockRejectedValue(new ConsoleApiError('NETWORK_ERROR'))
    const router = (await import('./index')).default
    await router.push('/users')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.error).toBe('NETWORK_ERROR')
  })
})
