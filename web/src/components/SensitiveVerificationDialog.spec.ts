import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import SensitiveVerificationDialog from './SensitiveVerificationDialog.vue'

const mocks = vi.hoisted(() => ({
  status: vi.fn(), apple: vi.fn(), genericVerify: vi.fn(), verify: vi.fn(),
  passkeyOptions: vi.fn(), passkeyVerify: vi.fn(), credential: vi.fn(),
}))
vi.mock('@/api/auth', () => ({
  checkSensitiveVerification: mocks.status, getAppleStatus: mocks.apple,
  getPasskeySensitiveVerificationOptions: vi.fn(), initQrSensitive: vi.fn(), pollQrStatus: vi.fn(),
  sendSensitiveVerificationCode: vi.fn(), verifyPasskeySensitiveOperation: vi.fn(),
  verifySensitiveOperation: mocks.genericVerify, verifyTotpEnrollment: mocks.verify,
  getTotpEnrollmentPasskeyOptions: mocks.passkeyOptions, verifyTotpEnrollmentPasskey: mocks.passkeyVerify,
}))
vi.mock('@/utils/appleSignIn', () => ({ prepareAppleSignIn: vi.fn(), verifySensitiveWithApple: vi.fn() }))
vi.mock('@/utils/webauthn', () => ({
  isWebAuthnSupported: () => true, getPasskeyCredential: mocks.credential,
  extractAuthenticationData: () => ({ credentialRawId: 'id', clientDataJSON: 'client', authenticatorData: 'auth', signature: 'sig' }),
}))

const render = () => mount(SensitiveVerificationDialog, {
  props: { modelValue: false, enrollmentMode: true },
  global: { stubs: {
    'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /></div>' },
    'el-button': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    'el-form': { methods: { validate: () => Promise.resolve(true) },
      template: '<form @submit.prevent="$emit(\'submit\', $event)"><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    'el-input': { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-icon': { template: '<span><slot /></span>' }, 'el-skeleton': true,
  } },
})

beforeEach(() => {
  vi.clearAllMocks()
  mocks.status.mockResolvedValue({ verified: true, remainingSeconds: 900,
    methods: ['password', 'email-code', 'passkey', 'totp', 'qr'], preferredMethod: 'password' })
  mocks.verify.mockResolvedValue({ proofId: 'fresh-enrollment-proof', expiresInSeconds: 300 })
})

describe('TOTP enrollment verification dialog', () => {
  it('requires a new credential despite an already verified generic session', async () => {
    const wrapper = render()
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(wrapper.emitted('success')).toBeUndefined()
    expect(mocks.apple).not.toHaveBeenCalled()
    await wrapper.find('input').setValue('current-password')
    await wrapper.find('form').trigger('submit'); await flushPromises()
    expect(mocks.verify).toHaveBeenCalledWith({ method: 'password', password: 'current-password' })
    expect(mocks.genericVerify).not.toHaveBeenCalled()
    expect(wrapper.emitted('success')).toEqual([['fresh-enrollment-proof']])
    wrapper.unmount()
  })

  it('cannot select TOTP, recovery codes or QR as the proof for its own enrollment', async () => {
    const wrapper = render()
    await wrapper.setProps({ modelValue: true }); await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '返回')!.trigger('click')
    const methods = wrapper.findAll('.method-item')
    expect(methods).toHaveLength(5)
    expect(methods[3]!.attributes('disabled')).toBeDefined()
    expect(methods[4]!.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
