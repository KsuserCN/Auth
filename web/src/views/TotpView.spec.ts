import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { ApiError } from '@/utils/apiResult'
import TotpView from './TotpView.vue'

const mocks = vi.hoisted(() => ({ status: vi.fn(), options: vi.fn(), confirm: vi.fn(), push: vi.fn() }))
vi.mock('@/api/auth', () => ({ getTotpStatus: mocks.status,
  getTotpRegistrationOptions: mocks.options, verifyTotpRegistration: mocks.confirm }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false) }))
vi.mock('qrcode', () => ({ default: { toCanvas: vi.fn().mockResolvedValue(undefined) } }))
vi.mock('@/components/SensitiveVerificationDialog.vue', () => ({ default: {
  name: 'SensitiveVerificationDialog', props: { modelValue: Boolean, enrollmentMode: Boolean, disableQrMethod: Boolean },
  template: '<div v-if="modelValue" class="enrollment-verification" />',
} }))

const mounted: ReturnType<typeof mount>[] = []
const render = () => {
  const wrapper = mount(TotpView, { global: { stubs: {
    'el-button': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    'el-form': { methods: { validate: () => Promise.resolve(true) }, template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    'el-input': { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    'el-icon': true, 'el-skeleton': true, 'el-checkbox': true,
  } } })
  mounted.push(wrapper); return wrapper
}
const button = (wrapper: ReturnType<typeof render>, label: string) =>
  wrapper.findAll('button').find((item) => item.text() === label)!
const start = async (wrapper: ReturnType<typeof render>) => {
  await flushPromises()
  wrapper.findComponent({ name: 'SensitiveVerificationDialog' }).vm.$emit('success', 'fresh-proof')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.status.mockResolvedValue({ enabled: false, recoveryCodesCount: 0 })
  mocks.options.mockResolvedValue({ secret: 'fixture-secret', qrCodeUrl: 'otpauth://fixture',
    recoveryCodes: ['ABCDEFGH'], enrollmentId: 'enrollment-one', expiresInSeconds: 600 })
  mocks.confirm.mockResolvedValue(undefined)
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('TOTP enrollment flow', () => {
  it('does not fetch a new secret until fresh enrollment verification succeeds', async () => {
    const wrapper = render(); await flushPromises()
    const dialog = wrapper.findComponent({ name: 'SensitiveVerificationDialog' })
    expect(dialog.props()).toMatchObject({ modelValue: true, enrollmentMode: true, disableQrMethod: true })
    expect(mocks.options).not.toHaveBeenCalled()
    await start(wrapper)
    expect(mocks.options).toHaveBeenCalledWith('fresh-proof')
  })

  it('confirms using the server enrollment ID without client recovery codes', async () => {
    const wrapper = render(); await start(wrapper)
    await button(wrapper, '下一步').trigger('click'); await flushPromises()
    await wrapper.find('input').setValue('123456')
    await button(wrapper, '下一步').trigger('click'); await flushPromises()
    expect(mocks.confirm).toHaveBeenCalledWith({ enrollmentId: 'enrollment-one', code: '123456' })
  })

  it.each(['TOTP_006', 'RATE_LIMITED'])('discards setup and requires a new proof after %s', async (errorCode) => {
    mocks.confirm.mockRejectedValue(new ApiError({ errorCode }, errorCode === 'RATE_LIMITED' ? 429 : 400))
    const wrapper = render(); await start(wrapper)
    await button(wrapper, '下一步').trigger('click'); await flushPromises()
    await wrapper.find('input').setValue('123456')
    await button(wrapper, '下一步').trigger('click'); await flushPromises()
    expect(wrapper.text()).not.toContain('fixture-secret')
    expect(wrapper.findComponent({ name: 'SensitiveVerificationDialog' }).props('modelValue')).toBe(true)
    expect(mocks.options).toHaveBeenCalledTimes(1)
  })
})
