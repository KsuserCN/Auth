import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import OAuthQQCallbackView from './OAuthQQCallbackView.vue'
import { requestAppleAccountBinding } from '@/utils/appleAccountFlow'

const mocks = vi.hoisted(() => ({
  route: { path: '/oauth/qq/callback', meta: { provider: 'qq' } },
  callback: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
  finalize: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => mocks }))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false), useStorage: () => ref('light') }))
vi.mock('@/api/auth', () => ({
  handleQQCallbackByOperation: mocks.callback,
  handleGithubCallbackByOperation: mocks.callback,
  handleMicrosoftCallbackByOperation: mocks.callback,
  handleGoogleCallbackByOperation: mocks.callback,
}))
vi.mock('@/utils/desktopBridge', () => ({ finalizeWebLogin: mocks.finalize }))

const mounted: ReturnType<typeof mount>[] = []
const render = (provider: string) => {
  mocks.route.path = `/oauth/${provider}/callback`
  mocks.route.meta.provider = provider
  const state = 'verified;login;prd'
  sessionStorage.setItem(`${provider}_oauth_state`, state)
  sessionStorage.setItem(`${provider}_oauth_code_verifier`, 'pkce-verifier')
  window.history.replaceState({}, '', `${mocks.route.path}?code=authorization-code&state=${state}`)
  const wrapper = mount(OAuthQQCallbackView, {
    global: {
      stubs: {
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': true,
        'el-skeleton-item': true,
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  vi.useFakeTimers()
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})

describe.each(['qq', 'github', 'microsoft', 'google'])('%s account routing', (provider) => {
  it('offers existing-account binding and new-account registration for an unlinked identity', async () => {
    mocks.callback.mockResolvedValue({ needBind: true, oauthBindToken: 'pending-ticket' })
    const wrapper = render(provider)
    await flushPromises()
    expect(wrapper.text()).toContain('完成账号关联')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '绑定已有 Ksuser 账号')!
      .trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/login',
      query: { oauthBindProvider: provider, oauthBindToken: 'pending-ticket' },
    })
    await wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('注册并绑定'))!
      .trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/register',
      query: { oauthBindProvider: provider, oauthBindToken: 'pending-ticket' },
    })
    expect(mocks.finalize).not.toHaveBeenCalled()
  })

  it('logs in an already bound identity directly', async () => {
    mocks.callback.mockResolvedValue({
      accessToken: 'linked-session',
      user: { uuid: 'linked-user' },
    })
    const wrapper = render(provider)
    await flushPromises()
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'linked-session',
      user: { uuid: 'linked-user' },
    })
    expect(wrapper.text()).toContain('登录成功')
    expect(wrapper.text()).not.toContain('完成账号关联')
    await vi.advanceTimersByTimeAsync(1000)
    expect(mocks.replace).toHaveBeenCalledWith('/home/overview')
  })
  it('continues a pending Apple binding when this provider authenticates the existing account', async () => {
    requestAppleAccountBinding()
    sessionStorage.setItem('ksuser:post-login-redirect', '/home/security')
    mocks.callback.mockResolvedValue({
      accessToken: 'existing-session',
      user: { uuid: 'existing-user' },
    })
    render(provider)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    expect(mocks.replace).toHaveBeenCalledWith('/oauth/apple/continue?mode=bind')
    expect(sessionStorage.getItem('ksuser:post-login-redirect')).toBe('/home/security')
  })
})
