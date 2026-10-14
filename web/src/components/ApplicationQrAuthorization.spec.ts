import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import ApplicationQrAuthorization from './ApplicationQrAuthorization.vue'
import { setLanguagePreference } from '@/i18n'

const mocks = vi.hoisted(() => ({
  init: vi.fn(),
  poll: vi.fn(),
  exchange: vi.fn(),
  cancel: vi.fn(),
  image: vi.fn(),
  redirect: vi.fn(),
  webLogin: vi.fn(),
}))
vi.mock('@/utils/applicationQr', () => ({
  initApplicationQr: mocks.init,
  pollApplicationQr: mocks.poll,
  exchangeApplicationQr: mocks.exchange,
  cancelApplicationQr: mocks.cancel,
  finishApplicationQrWebLogin: mocks.webLogin,
  applicationQrScopeLabel: (_mode: string, scope: string) =>
    ({ openid: '真实用户 UUID', real_name: '真实姓名', region: '所在地区' })[
      scope as 'openid' | 'real_name' | 'region'
    ] || scope,
}))
vi.mock('qrcode', () => ({ default: { toDataURL: mocks.image } }))
const authorization = {
  clientId: 'public-app',
  redirectUri: 'https://public.example/callback?existing=1',
  responseType: 'code',
  state: 'original-state',
  nonce: 'original-nonce',
  scope: 'openid real_name region',
  codeChallenge: 'a'.repeat(43),
  codeChallengeMethod: 'S256' as const,
}
const payload = {
  challenge: {
    challengeId: 'first',
    pollToken: 'a'.repeat(32),
    qrText: 'KSUSER-AUTH-QR:phone-code',
    expiresInSeconds: 120,
  },
  application: {
    clientName: '公共校园服务',
    requestedScopes: ['openid', 'real_name', 'region'],
    consoleApplication: false,
  },
  redirectUri: authorization.redirectUri,
}
let wrapper: VueWrapper | undefined
function render() {
  wrapper = mount(ApplicationQrAuthorization, {
    props: { authorization },
    global: {
      stubs: {
        ElButton: {
          props: ['loading', 'disabled'],
          template: '<button :disabled="loading || disabled"><slot /></button>',
        },
        ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
        ElIcon: { template: '<span><slot /></span>' },
      },
    },
  })
  return wrapper
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-13T07:00:00Z'))
  vi.clearAllMocks()
  setLanguagePreference('zh-Hans')
  mocks.init.mockResolvedValue(payload)
  mocks.poll.mockResolvedValue({ status: 'pending', expiresInSeconds: 120 })
  mocks.exchange.mockResolvedValue({
    status: 'completed',
    redirectUrl: authorization.redirectUri + '&code=original-code&state=original-state',
  })
  mocks.cancel.mockResolvedValue(undefined)
  mocks.image.mockResolvedValue('data:image/png;base64,qr')
  mocks.webLogin.mockResolvedValue(undefined)
  const realWindow = window
  vi.stubGlobal(
    'window',
    new Proxy(realWindow, {
      get(target, key) {
        if (key === 'location') return { ...target.location, replace: mocks.redirect }
        return Reflect.get(target, key, target)
      },
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.unstubAllGlobals()
  vi.useRealTimers()
  setLanguagePreference('zh-Hans')
})

describe('application QR authorization', () => {
  it.each(['oauth', 'official'] as const)(
    'uses the %s protocol and links the iOS App in a new tab',
    async (mode) => {
      const english = mode === 'official'
      setLanguagePreference(english ? 'en' : 'zh-Hans')
      const view = render()
      await view.setProps({ mode })
      await flushPromises()
      expect(mocks.init).toHaveBeenLastCalledWith(mode, authorization)
      const link = view.find('a')
      expect(link.text()).toBe(english ? 'Ksuser Auth App' : 'Ksuser安全 App')
      expect(view.text()).toContain(
        english ? 'top-right corner of the screen' : '屏幕右上角的扫码图标',
      )
      expect(link.attributes('href')).toContain(
        english ? 'https://apps.apple.com/us/' : 'https://apps.apple.com/cn/',
      )
      expect(link.attributes('target')).toBe('_blank')
      expect(link.attributes('rel')).toContain('noopener')
    },
  )
  it('shows the public application and exact requested fields, then consumes one callback', async () => {
    const view = render()
    await flushPromises()
    expect(view.text()).toContain('公共校园服务')
    expect(view.text()).toContain('真实姓名')
    expect(view.text()).toContain('所在地区')
    expect(view.text()).not.toContain('仅允许超级管理员')
    expect(mocks.init).toHaveBeenCalledWith('official', authorization)
    expect(mocks.image).toHaveBeenCalledWith(
      payload.challenge.qrText,
      expect.objectContaining({ margin: 2 }),
    )
    mocks.poll.mockResolvedValue({ status: 'approved', expiresInSeconds: 118 })
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(mocks.exchange).toHaveBeenCalledOnce()
    expect(mocks.redirect).toHaveBeenCalledWith(
      authorization.redirectUri + '&code=original-code&state=original-state',
    )
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.exchange).toHaveBeenCalledOnce()
  })
  it('returns a mobile denial to the original application callback', async () => {
    mocks.poll.mockResolvedValue({ status: 'rejected', expiresInSeconds: 120 })
    const redirectUrl = authorization.redirectUri + '&error=access_denied&state=original-state'
    mocks.exchange.mockResolvedValue({ status: 'completed', redirectUrl })
    render()
    await flushPromises()
    expect(mocks.redirect).toHaveBeenCalledWith(redirectUrl)
  })
  it('displays administrator restrictions when specified by the server', async () => {
    mocks.init.mockResolvedValue({
      ...payload,
      application: { ...payload.application, consoleApplication: true },
    })
    expect(render()).toBeDefined()
    await flushPromises()
    expect(wrapper!.text()).toContain('仅允许超级管理员登录')
  })
  it('expires the QR, stops polling and refreshes with a new receipt', async () => {
    mocks.init.mockResolvedValue({
      ...payload,
      challenge: { ...payload.challenge, expiresInSeconds: 3 },
    })
    const view = render()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)
    expect(view.text()).toContain('二维码已过期')
    const polls = mocks.poll.mock.calls.length
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.poll).toHaveBeenCalledTimes(polls)
    await view.find('.qr-refresh').trigger('click')
    await flushPromises()
    expect(mocks.cancel).toHaveBeenCalledWith({ challengeId: 'first', pollToken: 'a'.repeat(32) })
    expect(mocks.init).toHaveBeenCalledTimes(2)
  })
  it('ignores old confirmation after refresh and cancels before web login', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.poll.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve
        }),
    )
    const view = render()
    await flushPromises()
    mocks.init.mockResolvedValue({
      ...payload,
      challenge: { ...payload.challenge, challengeId: 'second', pollToken: 'b'.repeat(32) },
    })
    await view.find('.qr-refresh').trigger('click')
    await flushPromises()
    resolveOld({ status: 'approved', expiresInSeconds: 100 })
    await flushPromises()
    expect(mocks.exchange).not.toHaveBeenCalled()
    await view.find('.qr-web-login').trigger('click')
    await flushPromises()
    expect(mocks.cancel).toHaveBeenLastCalledWith({
      challengeId: 'second',
      pollToken: 'b'.repeat(32),
    })
    expect(view.emitted('webLogin')).toHaveLength(1)
  })
  it('cancels a delayed initialization after leaving and never follows a foreign callback', async () => {
    let resolveInit!: (value: unknown) => void
    mocks.init.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveInit = resolve
        }),
    )
    render()
    await flushPromises()
    wrapper!.unmount()
    wrapper = undefined
    resolveInit(payload)
    await flushPromises()
    expect(mocks.cancel).toHaveBeenCalledWith({ challengeId: 'first', pollToken: 'a'.repeat(32) })
    expect(mocks.poll).not.toHaveBeenCalled()
    mocks.init.mockResolvedValue(payload)
    mocks.poll.mockResolvedValue({ status: 'approved', expiresInSeconds: 120 })
    mocks.exchange.mockResolvedValue({
      status: 'completed',
      redirectUrl: 'https://foreign.example/callback?code=bad',
    })
    const view = render()
    await flushPromises()
    expect(mocks.redirect).not.toHaveBeenCalled()
    expect(view.text()).toContain('扫码登录失败')
  })
})
