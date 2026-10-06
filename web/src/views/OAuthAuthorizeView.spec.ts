import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import OAuthAuthorizeView from './OAuthAuthorizeView.vue'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  ios: vi.fn(),
  create: vi.fn(),
  consume: vi.fn(),
  cancel: vi.fn(),
  launch: vi.fn(),
  continue: vi.fn(),
  oauthContext: vi.fn(),
  ssoContext: vi.fn(),
  desktopStatus: vi.fn(),
  route: {
    path: '/sso/authorize',
    fullPath: '/sso/authorize?client_id=client',
    query: {
      client_id: 'client',
      redirect_uri: 'https://client.example/callback',
      response_type: 'code',
      scope: 'openid profile email',
      state: 'original-state',
      nonce: 'original-nonce',
      code_challenge: 'original-challenge',
      code_challenge_method: 'S256',
      prompt: 'consent',
    },
  },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false) }))
vi.mock('pinia', () => ({ storeToRefs: () => ({ user: ref(null) }) }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ clearUser: vi.fn(), fetchUserInfo: vi.fn() }),
}))
vi.mock('@/utils/authSession', () => ({
  clearAuthSession: vi.fn(),
  getStoredAccessToken: () => null,
}))
vi.mock('@/api/auth', () => ({ logout: vi.fn() }))
vi.mock('@/api/oauth2', () => ({
  getOAuth2AuthorizeContext: mocks.oauthContext,
  approveOAuth2Authorize: vi.fn(),
}))
vi.mock('@/api/sso', () => ({
  getSSOAuthorizeContext: mocks.ssoContext,
  approveSSOAuthorize: vi.fn(),
}))
vi.mock('@/utils/desktopBridge', () => ({
  getDesktopBridgeStatus: mocks.desktopStatus,
  exchangeDesktopSessionToWeb: vi.fn(),
  finalizeWebLogin: vi.fn(),
}))
vi.mock('@/utils/mobileAuthorization', () => ({
  isIOSAuthorizationSupported: mocks.ios,
  createMobileAuthorization: mocks.create,
  consumeMobileAuthorization: mocks.consume,
  cancelMobileAuthorization: mocks.cancel,
  continueMobileAuthorization: mocks.continue,
  launchMobileAuthorization: mocks.launch,
}))
const context = {
  clientId: 'client',
  clientName: '校园日历',
  appName: '校园日历',
  redirectUri: 'https://client.example/callback',
  requestedScopes: ['openid', 'profile', 'email'],
  alreadyAuthorized: false,
  existingGrantMode: 'PERSISTENT',
}
const ticket = {
  ticket: 'a'.repeat(32),
  secret: 'b'.repeat(32),
  appLink: 'ksuserauth://authorize?ticket=test',
  expiresInSeconds: 300,
}
const mounted: ReturnType<typeof mount>[] = []
const render = () => {
  const wrapper = mount(OAuthAuthorizeView, {
    global: {
      stubs: {
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': true,
        'el-avatar': true,
        'el-radio-button': true,
        'el-radio-group': true,
        'el-option': true,
        'el-select': true,
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.path = '/sso/authorize'
  mocks.ios.mockReturnValue(true)
  mocks.ssoContext.mockResolvedValue(context)
  mocks.oauthContext.mockResolvedValue(context)
  mocks.create.mockResolvedValue(ticket)
  mocks.consume.mockResolvedValue({ status: 'pending' })
  mocks.continue.mockReturnValue(false)
  mocks.cancel.mockResolvedValue(undefined)
  mocks.desktopStatus.mockResolvedValue(null)
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('authorization handoff page', () => {
  it('preserves OIDC parameters and attempts app launch before browser login', async () => {
    const wrapper = render()
    await flushPromises()
    expect(mocks.create).toHaveBeenCalledWith(
      'sso',
      expect.objectContaining({
        state: 'original-state',
        nonce: 'original-nonce',
        codeChallenge: 'original-challenge',
        codeChallengeMethod: 'S256',
      }),
    )
    expect(mocks.create.mock.calls[0]?.[1]).not.toHaveProperty('prompt')
    expect(mocks.launch).toHaveBeenCalledWith(ticket)
    expect(wrapper.text()).toContain('在 Ksuser App 中继续授权')
    expect(mocks.consume).not.toHaveBeenCalled()
    const fallback = wrapper.findAll('button').find((button) => button.text() === '继续网页授权')!
    await fallback.trigger('click')
    await flushPromises()
    expect(mocks.cancel).toHaveBeenCalledWith(ticket)
    expect(mocks.replace).toHaveBeenCalledWith(
      expect.objectContaining({
        query: expect.objectContaining({ iosAuthorizationFallback: '1' }),
      }),
    )
    expect(mocks.create).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('当前浏览器尚未登录')
  })
  it('keeps browser consent available when handoff creation fails', async () => {
    mocks.create.mockRejectedValue(new Error('offline'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('当前浏览器尚未登录')
    expect(mocks.launch).not.toHaveBeenCalled()
  })
  it('retains ordinary OAuth web authorization on non-iOS devices', async () => {
    mocks.route.path = '/oauth/authorize'
    mocks.ios.mockReturnValue(false)
    const wrapper = render()
    await flushPromises()
    expect(mocks.oauthContext).toHaveBeenCalled()
    expect(mocks.create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('校园日历')
    expect(wrapper.text()).toContain('当前浏览器尚未登录')
  })
})
