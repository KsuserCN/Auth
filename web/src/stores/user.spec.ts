import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { setLanguagePreference } from '@/i18n'
import { ApiError } from '@/utils/apiResult'
import { useUserStore } from './user'

const mocks = vi.hoisted(() => ({ getUserInfo: vi.fn(), getUserDetailsInfo: vi.fn() }))
vi.mock('@/api/auth', () => mocks)
beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('retained account load errors', () => {
  it('retains API metadata and renders the current language after switching', async () => {
    const error = new ApiError({ errorCode: 'NETWORK_ERROR' })
    mocks.getUserInfo.mockRejectedValue(error)
    const store = useUserStore()
    await expect(store.fetchUserInfo()).rejects.toBe(error)
    expect(store.error).toContain('网络')
    setLanguagePreference('en')
    expect(store.error).toBe(error.message)
    expect(store.error).not.toMatch(/[\u3400-\u9fff]/)
    store.clearUser()
    expect(store.error).toBe('')
  })

  it('uses a reactive local fallback for unexpected account detail failures', async () => {
    mocks.getUserDetailsInfo.mockRejectedValue(undefined)
    const store = useUserStore()
    await expect(store.fetchUserDetails()).rejects.toBeInstanceOf(Error)
    setLanguagePreference('en')
    expect(store.error).toBe('Could not load account details')
  })
})
