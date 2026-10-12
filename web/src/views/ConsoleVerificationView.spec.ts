import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ConsoleVerificationView from './ConsoleVerificationView.vue'

const mocks = vi.hoisted(() => ({
  token: vi.fn(), replace: vi.fn(), context: vi.fn(), status: vi.fn(), complete: vi.fn(),
  verify: vi.fn(), options: vi.fn(), credential: vi.fn(), passkey: vi.fn(), extract: vi.fn(),
}))
vi.mock('@/i18n', () => ({ t: (key: string) => key }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { request_id: 'console-challenge' }, fullPath: '/console/verify?request_id=console-challenge' }),
  useRouter: () => ({ replace: mocks.replace }),
}))
vi.mock('@/utils/authSession', () => ({ getStoredAccessToken: mocks.token }))
vi.mock('@/api/consoleVerification', () => ({
  getConsoleVerificationContext: mocks.context, completeConsoleVerification: mocks.complete,
}))
vi.mock('@/api/auth', () => ({
  checkSensitiveVerification: mocks.status, verifySensitiveOperation: mocks.verify,
  getPasskeySensitiveVerificationOptions: mocks.options, verifyPasskeySensitiveOperation: mocks.passkey,
}))
vi.mock('@/utils/webauthn', () => ({
  isWebAuthnSupported: () => true, getPasskeyCredential: mocks.credential, extractAuthenticationData: mocks.extract,
}))

const render = async () => {
  const wrapper = mount(ConsoleVerificationView, { global: { stubs: {
    'el-card': { template: '<section><slot /></section>' },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    'el-radio-group': { template: '<div><slot /></div>' },
    'el-radio-button': { template: '<span><slot /></span>' },
    'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
    'el-button': { props: ['nativeType', 'disabled'], template: '<button :type="nativeType || \'button\'" :disabled="disabled"><slot /></button>' },
    'el-input': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-checkbox': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
    'el-skeleton': true,
  } } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.token.mockReturnValue('passport-token')
  mocks.context.mockResolvedValue({ requestId: 'console-challenge', hasPassword: true })
  mocks.status.mockResolvedValue({ verified: true, methods: ['password', 'email'], preferredMethod: 'password' })
  mocks.verify.mockResolvedValue(undefined)
  mocks.passkey.mockResolvedValue(undefined)
  mocks.complete.mockResolvedValue({ redirectUrl: 'https://untrusted.example/auth/verification-callback' })
})

describe('Console session verification', () => {
  it('requires fresh credentials even when the passport generic verification is already valid', async () => {
    const wrapper = await render()
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('email')
    expect(mocks.verify).not.toHaveBeenCalled()
    expect(mocks.complete).not.toHaveBeenCalled()
    await wrapper.find('input').setValue('actual-password')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.verify).toHaveBeenCalledWith({ method: 'password', password: 'actual-password', consoleVerificationId: 'console-challenge' })
    expect(mocks.complete).toHaveBeenCalledWith('console-challenge')
    expect(wrapper.find('input').element.value).toBe('')
    expect(wrapper.get('[role="alert"]').text()).toBe('ui.console.verifyInvalid')
    wrapper.unmount()
  })

  it('binds Passkey proof to this Console request', async () => {
    mocks.status.mockResolvedValue({ verified: true, methods: ['passkey'], preferredMethod: 'passkey' })
    mocks.options.mockResolvedValue({ challengeId: 'passkey-challenge' })
    mocks.credential.mockResolvedValue({ id: 'credential' })
    mocks.extract.mockReturnValue({ id: 'signed-credential' })
    const wrapper = await render()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.passkey).toHaveBeenCalledWith('passkey-challenge', { id: 'signed-credential' }, 'console-challenge')
    expect(mocks.complete).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('sends a recovery code with the Console challenge', async () => {
    mocks.status.mockResolvedValue({ methods: ['totp'], preferredMethod: 'totp' })
    const wrapper = await render()
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('input:not([type="checkbox"])').setValue('abc12345')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.verify).toHaveBeenCalledWith({ method: 'totp', recoveryCode: 'ABC12345', consoleVerificationId: 'console-challenge' })
    wrapper.unmount()
  })

  it('does not complete when credential verification fails', async () => {
    mocks.verify.mockRejectedValue(new Error('Invalid password'))
    const wrapper = await render()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.complete).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toBe('Invalid password')
    wrapper.unmount()
  })

  it('hides the form when the Console challenge is invalid', async () => {
    mocks.context.mockRejectedValue(new Error('Expired request'))
    const wrapper = await render()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(mocks.status).not.toHaveBeenCalled()
    expect(mocks.complete).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('returns through passport login when the user has no passport session', async () => {
    mocks.token.mockReturnValue(null)
    const wrapper = await render()
    expect(mocks.replace).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/console/verify?request_id=console-challenge' } })
    expect(mocks.context).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
