import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import LoginView from './LoginView.vue'

const mocks = vi.hoisted(() => ({
  ios: vi.fn(),
  android: vi.fn(),
  wechat: vi.fn(),
  create: vi.fn(),
  poll: vi.fn(),
  launch: vi.fn(),
  cancel: vi.fn(),
  exchange: vi.fn(),
  finalize: vi.fn(),
  replace: vi.fn(),
  route: { path: '/login', query: {} as Record<string, string> },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false) }))
vi.mock('@/api/auth', () => ({
  cancelMobileBridgeChallenge: mocks.cancel,
  exchangeSessionTransfer: mocks.exchange,
  refreshAccessToken: vi.fn().mockRejectedValue(new Error('No browser session')),
}))
vi.mock('@/utils/desktopBridge', () => ({
  getDesktopBridgeStatus: vi.fn().mockResolvedValue(null),
  finalizeWebLogin: mocks.finalize,
  storeWebSession: vi.fn(),
  syncCurrentWebSessionToDesktop: vi.fn(),
}))
vi.mock('@/utils/appleSignIn', () => ({ prepareAppleSignIn: vi.fn().mockResolvedValue(undefined) }))
vi.mock('@/utils/authSession', () => ({ getStoredAccessToken: () => null }))
vi.mock('@/utils/mobileBridge', () => ({
  isIOSMobileBridgeSupported: mocks.ios,
  isAndroidMobileBridgeSupported: mocks.android,
  isWeChatInAppBrowser: mocks.wechat,
  createMobileBridgeLogin: mocks.create,
  fetchMobileBridgeStatus: mocks.poll,
  launchMobileBridgeApp: mocks.launch,
  getMobileBridgeChallengeIdFromUrl: (url: URL) =>
    url.searchParams.get('mobileBridgeChallengeId') || '',
  readMobileBridgeFallbackFlag: () => false,
  buildMobileBridgeReturnUrl: (id: string) => {
    const url = new URL(window.location.href)
    url.searchParams.set('mobileBridgeChallengeId', id)
    return url.toString()
  },
  stripMobileBridgeQuery: (url: URL) => {
    url.searchParams.delete('mobileBridgeChallengeId')
    return url
  },
}))
vi.mock('@/utils/request', () => ({ setRequestBaseUrl: vi.fn() }))
vi.mock('@/utils/webauthn', () => ({ isWebAuthnSupported: () => true }))

const challengeId = 'a'.repeat(32)
const appLink = `https://auth.ksuser.cn/app/bridge-login?challengeId=${challengeId}`
const mounted: ReturnType<typeof mount>[] = []
const render = () => {
  const wrapper = mount(LoginView, {
    global: {
      stubs: {
        'router-link': true,
        'el-skeleton': {
          props: ['loading'],
          template: '<div><slot v-if="!loading" /><slot v-else name="template" /></div>',
        },
        'el-button': {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading"><slot /></button>',
        },
        'el-icon': true,
        'el-skeleton-item': true,
        'el-form': { template: '<div><slot /></div>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': true,
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  mocks.route.query = { redirect: '/home/security' }
  window.history.replaceState({}, '', '/login?redirect=%2Fhome%2Fsecurity')
  vi.stubGlobal('PublicKeyCredential', class {})
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
  mocks.ios.mockReturnValue(true)
  mocks.android.mockReturnValue(false)
  mocks.wechat.mockReturnValue(false)
  mocks.create.mockResolvedValue({ challengeId, appLink })
  mocks.poll.mockResolvedValue({ status: 'pending' })
  mocks.cancel.mockResolvedValue(undefined)
  mocks.finalize.mockResolvedValue(undefined)
  mocks.exchange.mockResolvedValue({ accessToken: 'web-token', user: { uuid: 'app-account' } })
  mocks.replace.mockImplementation(async (target) => {
    if (typeof target !== 'string') {
      mocks.route.query = target.query
      window.history.replaceState({}, '', target.path + '?' + new URLSearchParams(target.query))
    }
  })
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('iOS app login entry', () => {
  it('appears below Passkey and is absent on desktop and Android', async () => {
    const wrapper = render()
    await flushPromises()
    const passkey = wrapper.find('.extra-actions .extra-btn')
    expect(passkey.text()).toBe('Passkey 登录')
    expect(
      passkey.element.compareDocumentPosition(wrapper.find('.ios-app-login-btn').element) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy()
    for (const android of [false, true]) {
      mocks.ios.mockReturnValue(false)
      mocks.android.mockReturnValue(android)
      const other = render()
      await flushPromises()
      expect(other.find('.ios-app-login-btn').exists()).toBe(false)
      expect(other.find('.mobile-bridge-card').exists()).toBe(android)
    }
  })

  it('preserves the return destination and reopens the same pending request', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    expect(mocks.replace).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/home/security', mobileBridgeChallengeId: challengeId },
    })
    expect(mocks.launch).toHaveBeenCalledWith(appLink)
    expect(wrapper.find('.ios-app-login-hint').text()).toContain('请在 App 中登录并确认')
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    expect(mocks.create).toHaveBeenCalledTimes(1)
    expect(mocks.launch).toHaveBeenCalledTimes(2)
  })

  it('completes the web session after returning from app approval', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    mocks.poll.mockResolvedValue({ status: 'approved', transferCode: 'one-time-transfer' })
    window.dispatchEvent(new Event('pageshow'))
    await flushPromises()
    expect(mocks.exchange).toHaveBeenCalledWith('one-time-transfer', 'web')
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'web-token',
      user: { uuid: 'app-account' },
      syncDesktop: false,
    })
    expect(mocks.replace).toHaveBeenCalledWith('/home/security')
    window.dispatchEvent(new Event('pageshow'))
    await flushPromises()
    expect(mocks.exchange).toHaveBeenCalledTimes(1)
  })

  it('cancels pending login and removes the challenge from the browser URL', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    await wrapper.find('.mobile-bridge-cancel').trigger('click')
    await flushPromises()
    expect(mocks.cancel).toHaveBeenCalledWith(challengeId)
    expect(wrapper.find('.ios-app-login-hint').exists()).toBe(false)
    expect(new URL(window.location.href).searchParams.has('mobileBridgeChallengeId')).toBe(false)
  })

  it('waits while the browser is hidden and polls immediately when it becomes visible', async () => {
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    expect(mocks.poll).not.toHaveBeenCalled()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(mocks.poll).toHaveBeenCalledWith(challengeId)
  })

  it('ignores an approval returned after the browser cancels the request', async () => {
    let approve: (status: { status: string; transferCode: string }) => void = () => {}
    mocks.poll.mockReturnValue(
      new Promise((resolve) => {
        approve = resolve
      }),
    )
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    await wrapper.find('.mobile-bridge-cancel').trigger('click')
    await flushPromises()
    approve({ status: 'approved', transferCode: 'cancelled-transfer' })
    await flushPromises()
    expect(mocks.exchange).not.toHaveBeenCalled()
  })

  it('lets an expired request be restarted without consuming a session', async () => {
    mocks.poll.mockResolvedValue({ status: 'expired' })
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    expect(wrapper.find('.ios-app-login-btn').text()).toBe('通过 Ksuser 安全 App 登录')
    expect(mocks.exchange).not.toHaveBeenCalled()
  })

  it('restores the pending request after the app opens the browser return URL', async () => {
    window.history.replaceState(
      {},
      '',
      `/login?redirect=%2Fhome%2Fsecurity&mobileBridgeChallengeId=${challengeId}`,
    )
    const wrapper = render()
    await flushPromises()
    expect(mocks.poll).toHaveBeenCalledWith(challengeId)
    await wrapper.find('.ios-app-login-btn').trigger('click')
    await flushPromises()
    expect(mocks.create).not.toHaveBeenCalled()
    expect(mocks.launch).toHaveBeenCalledWith(expect.stringContaining(`challengeId=${challengeId}`))
  })
})
