import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import LoginView from './LoginView.vue'

const mocks = vi.hoisted(() => ({
  init: vi.fn(), poll: vi.fn(), exchange: vi.fn(), cancel: vi.fn(), login: vi.fn(),
  replace: vi.fn(), image: vi.fn(), store: { session: null as unknown },
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ replace: mocks.replace }) }))
vi.mock('@/stores/session', () => ({ useSessionStore: () => mocks.store }))
vi.mock('qrcode', () => ({ default: { toDataURL: mocks.image } }))
vi.mock('@/api/console', () => ({
  ConsoleApiError: class extends Error {},
  consoleApi: { initQrLogin: mocks.init, pollQrLogin: mocks.poll, exchangeQrLogin: mocks.exchange, cancelQrLogin: mocks.cancel, login: mocks.login },
}))
const challenge = { challengeId: 'first', pollToken: 'poll-first', qrText: 'KSUSER-AUTH-QR:approve-first', expiresInSeconds: 120 }
const session = { user: { uuid: 'admin', systemRole: 'SUPER_ADMIN' }, expiresAt: 10000, freshUntil: 0 }
const pending = { status: 'pending', expiresInSeconds: 120 }
let wrapper: VueWrapper | undefined
function render() {
  wrapper = mount(LoginView, { global: { stubs: {
    ElButton: { props: ['loading'], template: '<button :disabled="loading"><slot /></button>' },
    ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
    ElIcon: { template: '<span><slot /></span>' },
  } } })
  return wrapper
}
beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-13T00:00:00Z'))
  vi.clearAllMocks()
  mocks.store.session = null
  mocks.init.mockResolvedValue(challenge)
  mocks.poll.mockResolvedValue(pending)
  mocks.exchange.mockResolvedValue(session)
  mocks.cancel.mockResolvedValue(undefined)
  mocks.replace.mockResolvedValue(undefined)
  mocks.image.mockResolvedValue('data:image/png;base64,qr')
})
afterEach(() => { wrapper?.unmount(); wrapper = undefined; vi.useRealTimers() })

describe('Console iOS QR login', () => {
  it('shows a QR on entry and exchanges mobile approval exactly once before entering Console', async () => {
    const view = render()
    await flushPromises()
    expect(view.text()).toContain('使用 Ksuser安全 App 扫码')
    expect(view.find('.qr-image').attributes('src')).toBe('data:image/png;base64,qr')
    expect(mocks.image).toHaveBeenCalledWith(challenge.qrText, expect.objectContaining({ margin: 2 }))
    expect(mocks.poll).toHaveBeenCalledWith('first', 'poll-first')
    mocks.poll.mockResolvedValue({ status: 'approved', expiresInSeconds: 118 })
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(mocks.exchange).toHaveBeenCalledOnce()
    expect(mocks.exchange).toHaveBeenCalledWith('first', 'poll-first')
    expect(mocks.store.session).toEqual(session)
    expect(mocks.replace).toHaveBeenCalledWith('/users')
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.exchange).toHaveBeenCalledOnce()
  })

  it('expires against elapsed time, stops polling and allows a fresh QR', async () => {
    mocks.init.mockResolvedValue({ ...challenge, expiresInSeconds: 3 })
    const view = render()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(3000)
    expect(view.text()).toContain('二维码已过期')
    const polls = mocks.poll.mock.calls.length
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.poll).toHaveBeenCalledTimes(polls)
    expect(mocks.exchange).not.toHaveBeenCalled()
    mocks.init.mockResolvedValue({ ...challenge, challengeId: 'new', pollToken: 'new-poll' })
    await view.find('.qr-overlay button').trigger('click')
    await flushPromises()
    expect(mocks.poll).toHaveBeenLastCalledWith('new', 'new-poll')
    expect(view.text()).toContain('等待手机确认')
  })

  it('ignores old approval after refreshing the QR', async () => {
    let resolveOld!: (value: unknown) => void
    mocks.poll.mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }))
    const view = render()
    await flushPromises()
    mocks.init.mockResolvedValue({ ...challenge, challengeId: 'new', pollToken: 'new-poll' })
    await view.find('.qr-refresh').trigger('click')
    await flushPromises()
    resolveOld({ status: 'approved', expiresInSeconds: 100 })
    await flushPromises()
    expect(mocks.exchange).not.toHaveBeenCalled()
    expect(mocks.poll).toHaveBeenLastCalledWith('new', 'new-poll')
  })

  it('cancels QR login when switching to Passport and ignores a pending poll', async () => {
    let resolvePoll!: (value: unknown) => void
    mocks.poll.mockImplementationOnce(() => new Promise((resolve) => { resolvePoll = resolve }))
    const view = render()
    await flushPromises()
    await view.findAll('.login-methods button')[1]!.trigger('click')
    await flushPromises()
    expect(mocks.cancel).toHaveBeenCalledOnce()
    expect(mocks.cancel).toHaveBeenCalledWith('first')
    expect(view.find('.login-button').exists()).toBe(true)
    resolvePoll({ status: 'approved', expiresInSeconds: 100 })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.exchange).not.toHaveBeenCalled()
    expect(mocks.poll).toHaveBeenCalledOnce()
  })

  it('shows a recoverable error without leaving a polling timer running', async () => {
    mocks.poll.mockRejectedValue(new Error('offline'))
    const view = render()
    await flushPromises()
    expect(view.text()).toContain('无法连接服务器')
    expect(view.find('.qr-overlay button').text()).toBe('刷新二维码')
    await vi.advanceTimersByTimeAsync(10000)
    expect(mocks.poll).toHaveBeenCalledOnce()
  })

  it('does not start polling if an initialization returns after leaving the page', async () => {
    let resolveInit!: (value: unknown) => void
    mocks.init.mockImplementationOnce(() => new Promise((resolve) => { resolveInit = resolve }))
    render().unmount()
    wrapper = undefined
    resolveInit(challenge)
    await flushPromises()
    expect(mocks.poll).not.toHaveBeenCalled()
    expect(mocks.exchange).not.toHaveBeenCalled()
    expect(mocks.cancel).toHaveBeenCalledOnce()
    expect(mocks.cancel).toHaveBeenCalledWith('first')
  })
})
