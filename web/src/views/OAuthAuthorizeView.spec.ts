import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import OAuthAuthorizeView from './OAuthAuthorizeView.vue'

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  ios: vi.fn(),
  mobile: vi.fn(),
  create: vi.fn(),
  consume: vi.fn(),
  cancel: vi.fn(),
  launch: vi.fn(),
  continue: vi.fn(),
  oauthContext: vi.fn(),
  officialContext: vi.fn(),
  officialApprove: vi.fn(),
  oauthApprove: vi.fn(),
  nativeContext: vi.fn(),
  nativeDecide: vi.fn(),
  token: null as string | null,
  account: null as { username: string; email: string } | null,
  desktopStatus: vi.fn(),
  route: {
    path: '/oauth/authorize',
    fullPath: '/oauth/authorize?client_id=client',
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
vi.mock('pinia', () => ({ storeToRefs: () => ({ user: ref(mocks.account) }) }))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ clearUser: vi.fn(), fetchUserInfo: vi.fn() }),
}))
vi.mock('@/utils/authSession', () => ({
  clearAuthSession: vi.fn(),
  getStoredAccessToken: () => mocks.token,
}))
vi.mock('@/api/auth', () => ({ logout: vi.fn() }))
vi.mock('@/api/oauth2', () => ({
  getOAuth2AuthorizeContext: mocks.oauthContext,
  approveOAuth2Authorize: mocks.oauthApprove,
}))
vi.mock('@/api/official', () => ({
  getOfficialAuthorizeContext: mocks.officialContext,
  approveOfficialAuthorize: mocks.officialApprove,
  officialScopeLabel: (scope: string) =>
    ({ openid: '真实用户 UUID', email: '邮箱', real_name: '真实姓名' })[
      scope as 'openid' | 'email' | 'real_name'
    ] || scope,
}))
vi.mock('@/api/nativeAuthorization', async (importOriginal) => ({
  ...await importOriginal<typeof import('@/api/nativeAuthorization')>(),
  getNativeAuthorizationContext: mocks.nativeContext,
  decideNativeAuthorization: mocks.nativeDecide,
}))
vi.mock('@/utils/desktopBridge', () => ({
  getDesktopBridgeStatus: mocks.desktopStatus,
  exchangeDesktopSessionToWeb: vi.fn(),
  finalizeWebLogin: vi.fn(),
}))
vi.mock('@/utils/mobileAuthorization', () => ({
  isIOSAuthorizationSupported: mocks.ios,
  isMobileAuthorizationBrowser: mocks.mobile,
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
        ApplicationQrAuthorization: true,
        'el-button': { template: '<button><slot /></button>' },
        'el-icon': true,
        'el-avatar': true,
        'el-tooltip': { template: '<div><slot /></div>' },
        'el-option': {
          props: ['value', 'label'],
          template: '<option :value="value">{{ label }}</option>',
        },
        'el-select': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: `<select :value="modelValue" @change="$emit('update:modelValue', Number($event.target.value))"><slot /></select>`,
        },
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.path = '/oauth/authorize'
  mocks.route.fullPath = '/oauth/authorize?client_id=client'
  Reflect.deleteProperty(mocks.route.query, 'login_method')
  Reflect.deleteProperty(mocks.route.query, 'ticket')
  mocks.token = null
  mocks.account = null
  mocks.ios.mockReturnValue(true)
  mocks.mobile.mockReturnValue(false)
  mocks.oauthContext.mockResolvedValue(context)
  mocks.create.mockResolvedValue(ticket)
  mocks.consume.mockResolvedValue({ status: 'pending' })
  mocks.continue.mockReturnValue(false)
  mocks.cancel.mockResolvedValue(undefined)
  mocks.desktopStatus.mockResolvedValue(null)
  mocks.oauthApprove.mockImplementation(() => new Promise(() => {}))
  mocks.nativeDecide.mockImplementation(() => new Promise(() => {}))
})

describe('compact authorization mode controls', () => {
  beforeEach(() => {
    mocks.token = 'passport-session'
    mocks.account = { username: 'Ksuser', email: 'ksuser@example.com' }
  })

  it.each([
    ['/oauth/authorize', 'PERSISTENT'],
    ['/oauth/authorize', 'ONE_TIME'],
    ['/oauth/authorize', 'TIME_LIMITED'],
  ])('keeps the grant payload intact for %s with %s', async (path, mode) => {
    mocks.route.path = path
    const wrapper = render()
    await flushPromises()
    expect(mocks.oauthContext).toHaveBeenCalledWith(expect.objectContaining({ state: 'original-state', codeChallenge: 'original-challenge', codeChallengeMethod: 'S256' }))
    expect((wrapper.get('input[value="PERSISTENT"]').element as HTMLInputElement).checked).toBe(
      true,
    )
    await wrapper.get(`input[value="${mode}"]`).setValue(true)
    if (mode === 'TIME_LIMITED') {
      expect(
        wrapper.findAll('.grant-ttl-select option').map((option) => option.attributes('value')),
      ).toEqual(['300', '3600', '86400', '604800'])
      expect((wrapper.get('.grant-ttl-select').element as HTMLSelectElement).value).toBe('3600')
      await wrapper.get('.grant-ttl-select').setValue('604800')
    } else {
      expect(wrapper.find('.grant-ttl-select').exists()).toBe(false)
    }
    await wrapper.get('.consent-primary-btn').trigger('click')
    const approve = mocks.oauthApprove
    expect(approve).toHaveBeenCalledWith(
      expect.objectContaining({
        clientId: 'client',
        state: 'original-state',
        grantMode: mode,
        codeChallenge: 'original-challenge',
        codeChallengeMethod: 'S256',
        grantTtlSeconds: mode === 'TIME_LIMITED' ? 604800 : undefined,
      }),
    )
  })

  it('keeps existing authorization distinct from the newly selected mode', async () => {
    mocks.oauthContext.mockResolvedValue({
      ...context,
      alreadyAuthorized: true,
      existingGrantMode: 'TIME_LIMITED',
      existingGrantExpiresAt: '2099-01-01T00:00:00Z',
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.grant-policy-existing').text()).toContain('已有：限时')
    await wrapper.get('input[value="ONE_TIME"]').setValue(true)
    expect(wrapper.get('.grant-policy-existing').text()).toContain('已有：限时')
    expect(wrapper.get('.grant-policy-note').text()).toContain('仅本次有效')
  })
})

describe('native SDK web fallback', () => {
  const nativeTicket = 'n'.repeat(32)
  beforeEach(() => {
    mocks.route.path = '/app/sdk-authorize/web'
    mocks.route.fullPath = '/app/sdk-authorize/web?ticket=' + nativeTicket
    Object.assign(mocks.route.query, { ticket: nativeTicket })
    mocks.token = 'passport-session'
    mocks.account = { username: 'Ksuser', email: 'ksuser@example.com' }
    mocks.nativeContext.mockResolvedValue({ ...context, ticket: nativeTicket, nativeAuthorization: true, officialApplication: false, consoleApplication: false, expiresInSeconds: 300 })
  })

  it('keeps the ticket through browser login and does not create another authorization', async () => {
    mocks.token = null
    render()
    await flushPromises()
    expect(mocks.replace).toHaveBeenCalledWith({ path: '/login', query: { redirect: mocks.route.fullPath } })
    expect(mocks.nativeContext).not.toHaveBeenCalled()
    expect(mocks.oauthContext).not.toHaveBeenCalled()
    expect(mocks.create).not.toHaveBeenCalled()
  })

  it('uses server metadata and sends only the frozen ticket and selected policy', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('校园日历')
    expect(wrapper.find('.qr-login-toggle').exists()).toBe(false)
    await wrapper.get('input[value="TIME_LIMITED"]').setValue(true)
    await wrapper.get('.grant-ttl-select').setValue('604800')
    await wrapper.get('.consent-primary-btn').trigger('click')
    expect(mocks.nativeContext).toHaveBeenCalledWith(nativeTicket)
    expect(mocks.nativeDecide).toHaveBeenCalledWith({ ticket: nativeTicket, approve: true, grantMode: 'TIME_LIMITED', grantTtlSeconds: 604800 })
    expect(mocks.oauthApprove).not.toHaveBeenCalled()
    expect(mocks.officialApprove).not.toHaveBeenCalled()
  })

  it('uses the same native decision endpoint for denial', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '拒绝')!.trigger('click')
    expect(mocks.nativeDecide).toHaveBeenCalledWith({ ticket: nativeTicket, approve: false, grantMode: 'PERSISTENT', grantTtlSeconds: undefined })
    expect(mocks.oauthApprove).not.toHaveBeenCalled()
  })

  it('preserves official application scope labels and hides OAuth grant policy', async () => {
    mocks.nativeContext.mockResolvedValue({ ...context, ticket: nativeTicket, nativeAuthorization: true, officialApplication: true, consoleApplication: true, expiresInSeconds: 300, requestedScopes: ['openid', 'real_name'] })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('真实用户 UUID')
    expect(wrapper.text()).toContain('真实姓名')
    expect(wrapper.find('.grant-policy-options').exists()).toBe(false)
    expect(mocks.officialContext).not.toHaveBeenCalled()
  })

  it('blocks a server response that points outside the frozen callback', async () => {
    mocks.nativeDecide.mockResolvedValue({ completed: true, returnUrl: 'https://evil.example/callback?code=stolen&state=s' })
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '拒绝')!.trigger('click')
    await flushPromises()
    expect(wrapper.get('.error-panel').text()).toContain('授权请求无效')
  })

  it('rejects duplicate ticket parameters before accessing the request', async () => {
    Object.assign(mocks.route.query, { ticket: [nativeTicket, nativeTicket] })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('.error-panel').text()).toContain('授权链接无效')
    expect(mocks.nativeContext).not.toHaveBeenCalled()
  })
})

describe('official application consent', () => {
  beforeEach(() => {
    mocks.route.path = '/official/authorize'
    mocks.token = 'passport-session'
    mocks.account = { username: 'Ksuser', email: 'ksuser@example.com' }
    mocks.officialContext.mockResolvedValue({
      clientId: 'client',
      clientName: '官方校园服务',
      requestedScopes: ['openid', 'email', 'real_name'],
      authorizedScopes: [],
      loggedIn: true,
      consentRequired: true,
      consoleApplication: false,
    })
    // Keep navigation pending while asserting the request sent before redirect.
    mocks.officialApprove.mockImplementation(() => new Promise(() => {}))
  })
  it('defaults to web login and exposes a QR button without creating a request', async () => {
    mocks.token = null
    mocks.account = null
    const wrapper = render()
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ApplicationQrAuthorization' }).exists()).toBe(false)
    expect(wrapper.find('.qr-login-toggle').attributes('aria-label')).toBe('使用扫码登录')
    expect(mocks.replace).not.toHaveBeenCalled()
    expect(mocks.create).not.toHaveBeenCalled()
  })
  it('allows an existing browser account to switch to phone confirmation', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.find('.qr-login-toggle').trigger('click')
    expect(wrapper.findComponent({ name: 'ApplicationQrAuthorization' }).exists()).toBe(true)
    expect(mocks.officialApprove).not.toHaveBeenCalled()
  })
  it('removes forced QR mode when returning to Passport web login without losing OIDC parameters', async () => {
    Object.assign(mocks.route.query, { login_method: 'qr' })
    mocks.route.fullPath =
      '/official/authorize?client_id=client&state=original-state&nonce=original-nonce&login_method=qr'
    const wrapper = render()
    await flushPromises()
    wrapper.findComponent({ name: 'ApplicationQrAuthorization' }).vm.$emit('webLogin')
    await flushPromises()
    expect(mocks.replace).toHaveBeenCalledWith({
      path: '/login',
      query: {
        redirect: '/official/authorize?client_id=client&state=original-state&nonce=original-nonce',
      },
    })
  })
  it('displays exact profile fields and hides the ordinary grant policy', async () => {
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('官方校园服务')
    expect(wrapper.text()).toContain('真实用户 UUID')
    expect(wrapper.text()).toContain('真实姓名')
    expect(wrapper.find('.grant-policy-options').exists()).toBe(false)
    expect(mocks.create).not.toHaveBeenCalled()
    expect(mocks.officialApprove).not.toHaveBeenCalled()
  })
  it('shows remembered scopes while leaving web and QR authorization selectable', async () => {
    const originalPrompt = mocks.route.query.prompt
    mocks.route.query.prompt = ''
    mocks.officialContext.mockResolvedValue({
      clientId: 'client',
      clientName: '官方校园服务',
      requestedScopes: ['openid', 'email'],
      authorizedScopes: ['openid', 'email'],
      loggedIn: true,
      consentRequired: false,
      consoleApplication: false,
    })
    render()
    await flushPromises()
    expect(mocks.officialApprove).not.toHaveBeenCalled()
    mocks.route.query.prompt = originalPrompt
  })
  it('denies official consent through the validated server callback', async () => {
    const wrapper = render()
    await flushPromises()
    const deny = wrapper.findAll('button').find((button) => button.text() === '拒绝')!
    await deny.trigger('click')
    expect(mocks.officialApprove).toHaveBeenCalledWith(
      expect.objectContaining({
        approved: false,
        state: 'original-state',
        nonce: 'original-nonce',
      }),
    )
  })
  it('requires consent again when an application requests an additional profile field', async () => {
    const originalPrompt = mocks.route.query.prompt
    mocks.route.query.prompt = ''
    mocks.officialContext.mockResolvedValue({
      clientId: 'client',
      clientName: '官方校园服务',
      requestedScopes: ['openid', 'email', 'real_name'],
      authorizedScopes: ['openid', 'email'],
      loggedIn: true,
      consentRequired: true,
      consoleApplication: false,
    })
    const wrapper = render()
    await flushPromises()
    expect(wrapper.text()).toContain('真实姓名')
    expect(wrapper.text()).toContain('首次授权后将记住已同意的范围')
    expect(mocks.officialApprove).not.toHaveBeenCalled()
    mocks.route.query.prompt = originalPrompt
  })
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('authorization login methods', () => {
  it.each(['/oauth/authorize', '/official/authorize'])(
    'defaults to the web on %s and starts QR only after selecting it',
    async (path) => {
      mocks.route.path = path
      mocks.officialContext.mockResolvedValue({
        clientId: 'client',
        clientName: '校园日历',
        requestedScopes: ['openid', 'profile', 'email'],
        loggedIn: false,
        consentRequired: true,
        consoleApplication: false,
      })
      const wrapper = render()
      await flushPromises()
      expect(wrapper.text()).toContain('当前浏览器尚未登录')
      expect(wrapper.findComponent({ name: 'ApplicationQrAuthorization' }).exists()).toBe(false)
      expect(mocks.create).not.toHaveBeenCalled()
      expect(mocks.launch).not.toHaveBeenCalled()
      await wrapper.find('.qr-login-toggle').trigger('click')
      const qr = wrapper.findComponent({ name: 'ApplicationQrAuthorization' })
      expect(qr.props('mode')).toBe(
        path.startsWith('/oauth') ? 'oauth' : 'official',
      )
      expect(qr.props('authorization')).toEqual(
        expect.objectContaining({
          state: 'original-state',
          nonce: 'original-nonce',
          codeChallenge: 'original-challenge',
          codeChallengeMethod: 'S256',
        }),
      )
    },
  )
  it.each(['/oauth/authorize', '/official/authorize'])(
    'does not show or automatically launch QR for mobile UA on %s',
    async (path) => {
      mocks.route.path = path
      mocks.mobile.mockReturnValue(true)
      mocks.officialContext.mockResolvedValue({
        clientId: 'client',
        clientName: '校园日历',
        requestedScopes: ['openid'],
        loggedIn: false,
        consentRequired: true,
        consoleApplication: false,
      })
      const wrapper = render()
      await flushPromises()
      expect(wrapper.find('.qr-login-toggle').exists()).toBe(false)
      expect(wrapper.findComponent({ name: 'ApplicationQrAuthorization' }).exists()).toBe(false)
      expect(mocks.launch).not.toHaveBeenCalled()
      expect(mocks.create).not.toHaveBeenCalled()
    },
  )
})
