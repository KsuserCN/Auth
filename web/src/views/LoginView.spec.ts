import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { ElForm, ElFormItem, ElInput, ElButton } from 'element-plus'
import LoginView from './LoginView.vue'
import { setLanguagePreference } from '@/i18n'

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
  push: vi.fn(),
  apple: vi.fn(),
  passwordLogin: vi.fn(),
  sendLoginCode: vi.fn(),
  loginWithCode: vi.fn(),
  verifyTotp: vi.fn(),
  route: { path: '/login', query: {} as Record<string, string> },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}))
vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDark: () => ref(false),
}))
vi.mock('@/api/auth', () => ({
  passwordLogin: mocks.passwordLogin,
  sendLoginCode: mocks.sendLoginCode,
  loginWithCode: mocks.loginWithCode,
  verifyTOTPForLogin: mocks.verifyTotp,
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
vi.mock('@/utils/appleSignIn', () => ({
  prepareAppleSignIn: vi.fn().mockResolvedValue(undefined),
  signInWithApple: mocks.apple,
}))
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
const render = (realForms = false) => {
  const wrapper = mount(LoginView, {
    global: {
      components: { ElForm, ElFormItem, ElInput, ElButton },
      stubs: {
        'router-link': true,
        'el-skeleton': {
          props: ['loading'],
          template: '<div><slot v-if="!loading" /><slot v-else name="template" /></div>',
        },
        'el-button': realForms
          ? false
          : {
              props: ['disabled', 'loading'],
              template: '<button :disabled="disabled || loading"><slot /></button>',
            },
        'el-icon': true,
        'el-skeleton-item': true,
        'el-form': realForms ? false : { template: '<div><slot /></div>' },
        'el-form-item': realForms ? false : { template: '<div><slot /></div>' },
        'el-input': !realForms,
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
  mocks.passwordLogin.mockResolvedValue({ accessToken: 'password-session', user: { uuid: 'user' } })
  mocks.sendLoginCode.mockResolvedValue(undefined)
  mocks.loginWithCode.mockResolvedValue({ accessToken: 'code-session', user: { uuid: 'user' } })
  mocks.verifyTotp.mockResolvedValue({ accessToken: 'mfa-session', user: { uuid: 'user' } })
  mocks.replace.mockImplementation(async (target) => {
    if (typeof target !== 'string') {
      mocks.route.query = target.query
      window.history.replaceState({}, '', target.path + '?' + new URLSearchParams(target.query))
    }
  })
})

describe('login form submission', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  const submit = async (wrapper: ReturnType<typeof render>) => {
    const event = new Event('submit', { bubbles: true, cancelable: true })
    wrapper.get('form').element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
    await flushPromises()
  }
  const chooseMethod = async (methodIndex: number) => {
    mocks.ios.mockReturnValue(false)
    const wrapper = render(true)
    await flushPromises()
    await wrapper.get('input').setValue('enter@example.com')
    await submit(wrapper)
    await wrapper.findAll('.method-option')[methodIndex]!.trigger('click')
    await vi.waitFor(() => expect(wrapper.find('form').exists()).toBe(true))
    return wrapper
  }

  it('prevents native submission and keeps invalid email addresses on the first step', async () => {
    const wrapper = render(true)
    await flushPromises()
    await wrapper.get('input').setValue('invalid-email')
    await submit(wrapper)
    expect(wrapper.find('.el-form-item.is-error').exists()).toBe(true)
    expect(wrapper.find('.method-list').exists()).toBe(false)
    await wrapper.get('input').setValue('enter@example.com')
    await submit(wrapper)
    expect(wrapper.find('.method-list').exists()).toBe(true)
  })

  it('submits the password once and ignores repeated submits while login is pending', async () => {
    let finishLogin!: (response: object) => void
    mocks.passwordLogin.mockReturnValue(
      new Promise((resolve) => {
        finishLogin = resolve
      }),
    )
    const wrapper = await chooseMethod(0)
    await wrapper.get('input').setValue('StrongPass123')
    await submit(wrapper)
    await submit(wrapper)
    expect(mocks.passwordLogin).toHaveBeenCalledExactlyOnceWith({
      email: 'enter@example.com',
      password: 'StrongPass123',
    })
    finishLogin({ accessToken: 'password-session', user: { uuid: 'user' } })
    await flushPromises()
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'password-session',
      user: { uuid: 'user' },
    })
    expect(mocks.replace).toHaveBeenCalledWith('/home/security')
  })

  it('validates and submits the email verification code without navigating the page', async () => {
    const wrapper = await chooseMethod(1)
    expect(mocks.sendLoginCode).toHaveBeenCalledWith({ email: 'enter@example.com' })
    await wrapper.get('input').setValue('123')
    await submit(wrapper)
    expect(mocks.loginWithCode).not.toHaveBeenCalled()
    await wrapper.get('input').setValue('123456')
    await submit(wrapper)
    expect(mocks.loginWithCode).toHaveBeenCalledExactlyOnceWith({
      email: 'enter@example.com',
      code: '123456',
    })
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'code-session',
      user: { uuid: 'user' },
    })
  })

  it('validates and submits the current MFA challenge without navigating the page', async () => {
    mocks.route.query = { challengeId: 'mfa-challenge', method: 'totp', redirect: '/home/security' }
    const wrapper = render(true)
    await flushPromises()
    await wrapper.get('input').setValue('123')
    await submit(wrapper)
    expect(mocks.verifyTotp).not.toHaveBeenCalled()
    await wrapper.get('input').setValue('123456')
    await submit(wrapper)
    expect(mocks.verifyTotp).toHaveBeenCalledExactlyOnceWith({
      challengeId: 'mfa-challenge',
      code: '123456',
    })
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'mfa-session',
      user: { uuid: 'user' },
    })
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

describe('Apple login account routing', () => {
  const loginWithApple = async () => {
    const wrapper = render()
    await flushPromises()
    const button = wrapper.findAll('button').find((item) => item.find('.fa-apple').exists())!
    await button.trigger('click')
    await flushPromises()
    return wrapper
  }

  it('logs in an already linked identity and returns to the requested destination', async () => {
    mocks.apple.mockResolvedValue({ accessToken: 'apple-session', user: { uuid: 'linked-user' } })
    await loginWithApple()
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'apple-session',
      user: { uuid: 'linked-user' },
    })
    expect(mocks.replace).toHaveBeenCalledWith('/home/security')
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it.each([false, true])(
    'offers account choices for unlinked Apple identities (email conflict: %s)',
    async (conflict) => {
      mocks.apple.mockResolvedValue({
        needBind: true,
        oauthBindToken: 'apple-ticket',
        canRegister: !conflict,
        emailConflict: conflict,
      })
      await loginWithApple()
      expect(mocks.push).toHaveBeenCalledWith('/oauth/apple/continue')
      expect(mocks.finalize).not.toHaveBeenCalled()
    },
  )

  it('retains binding intent when Apple login requires MFA', async () => {
    mocks.route.query = { oauthBindProvider: 'apple' }
    mocks.apple.mockResolvedValue({ challengeId: 'apple-mfa', method: 'totp', methods: ['totp'] })
    await loginWithApple()
    expect(mocks.push).toHaveBeenCalledWith(
      expect.objectContaining({
        query: expect.objectContaining({
          oauthBindProvider: 'apple',
          challengeId: 'apple-mfa',
          mfaFrom: 'apple',
        }),
      }),
    )
    expect(mocks.finalize).not.toHaveBeenCalled()
  })

  it('continues binding after authenticating the existing account', async () => {
    mocks.route.query = { oauthBindProvider: 'apple' }
    sessionStorage.setItem('ksuser:post-login-redirect', '/home/security')
    mocks.apple.mockResolvedValue({ accessToken: 'existing-account', user: { uuid: 'user' } })
    await loginWithApple()
    expect(mocks.replace).toHaveBeenCalledWith('/oauth/apple/continue?mode=bind')
    expect(sessionStorage.getItem('ksuser:post-login-redirect')).toBe('/home/security')
  })
})

describe('live sign-in localization', () => {
  it('updates copy and validation rules without resetting the form or current step', async () => {
    mocks.ios.mockReturnValue(false)
    mocks.android.mockReturnValue(false)
    const wrapper = render()
    await flushPromises()
    const state = wrapper.vm as unknown as {
      emailInput: { email: string }
      step: string
      emailRules: { email: Array<{ message: string }> }
    }
    state.emailInput.email = 'existing-input@example.com'
    const currentStep = state.step
    setLanguagePreference('en')
    await flushPromises()
    expect(wrapper.text()).toContain('Get started')
    expect(state.emailInput.email).toBe('existing-input@example.com')
    expect(state.step).toBe(currentStep)
    expect(state.emailRules.email[0]?.message).toBe('Please enter an email address')
    setLanguagePreference('zh-Hans')
    await flushPromises()
    expect(state.emailRules.email[0]?.message).toBe('请输入邮箱地址')
  })
})
