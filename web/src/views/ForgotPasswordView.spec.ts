import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { ElForm, ElFormItem, ElInput, ElButton } from 'element-plus'
import ForgotPasswordView from './ForgotPasswordView.vue'

const mocks = vi.hoisted(() => ({
  status: vi.fn(),
  complete: vi.fn(),
  passwordRequirement: vi.fn(),
  storeSession: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}))
vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDark: () => ref(false),
}))
vi.mock('@/api/auth', () => ({
  getAccountRecoveryStatus: mocks.status,
  completeAccountRecovery: mocks.complete,
  getPasswordRequirement: mocks.passwordRequirement,
  initQrAccountRecovery: vi.fn(),
  pollQrStatus: vi.fn(),
}))
vi.mock('@/utils/authSession', () => ({ storeAuthSession: mocks.storeSession }))
vi.mock('@/utils/request', () => ({ setRequestBaseUrl: vi.fn() }))

const recoveryCode = 'recovery-code-123456'
const mounted: ReturnType<typeof mount>[] = []
const render = async () => {
  const wrapper = mount(ForgotPasswordView, {
    global: {
      components: { ElForm, ElFormItem, ElInput, ElButton },
      stubs: { 'el-icon': true },
    },
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}
const submit = async (wrapper: Awaited<ReturnType<typeof render>>) => {
  const event = new Event('submit', { bubbles: true, cancelable: true })
  wrapper.get('form').element.dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
  await flushPromises()
}
const enter = async (wrapper: Awaited<ReturnType<typeof render>>, value: string) => {
  await wrapper.get('input').setValue(value)
  await submit(wrapper)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  mocks.passwordRequirement.mockResolvedValue({
    minLength: 6,
    maxLength: 66,
    requireUppercase: true,
    requireLowercase: true,
    requireDigits: true,
    requireSpecialChars: false,
    requirementCode: 'PASSWORD_REQUIREMENTS_7',
    requirementParams: { minLength: 6, maxLength: 66 },
  })
  mocks.status.mockResolvedValue({
    username: 'recovered-user',
    maskedEmail: 'e***@example.com',
    expiresInSeconds: 300,
  })
  mocks.complete.mockResolvedValue({ accessToken: 'recovered-session', user: { uuid: 'user' } })
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
})

describe('password recovery form submission', () => {
  it('prevents navigation and validates all three steps before completing recovery', async () => {
    const wrapper = await render()
    await enter(wrapper, 'invalid')
    expect(mocks.status).not.toHaveBeenCalled()
    expect(wrapper.find('.el-form-item.is-error').exists()).toBe(true)

    await enter(wrapper, recoveryCode)
    expect(mocks.status).toHaveBeenCalledWith(recoveryCode)
    expect(wrapper.find('.password-requirements').exists()).toBe(true)

    await enter(wrapper, 'weak')
    expect(wrapper.find('.password-requirements').exists()).toBe(true)
    await enter(wrapper, 'StrongPass123')
    expect(wrapper.find('.password-requirements').exists()).toBe(false)

    await enter(wrapper, 'DifferentPass123')
    expect(mocks.complete).not.toHaveBeenCalled()
    await enter(wrapper, 'StrongPass123')
    expect(mocks.complete).toHaveBeenCalledExactlyOnceWith({
      recoveryCode,
      newPassword: 'StrongPass123',
    })
    expect(mocks.storeSession).toHaveBeenCalledWith('recovered-session', { uuid: 'user' })
    expect(mocks.push).toHaveBeenCalledWith('/home/overview')
  })

  it('ignores repeated submits while loading the recovery context', async () => {
    let finishLoad!: (status: object) => void
    mocks.status.mockReturnValue(
      new Promise((resolve) => {
        finishLoad = resolve
      }),
    )
    const wrapper = await render()
    await enter(wrapper, recoveryCode)
    await submit(wrapper)
    expect(mocks.status).toHaveBeenCalledTimes(1)
    finishLoad({ username: 'user', maskedEmail: 'e***@example.com', expiresInSeconds: 300 })
    await flushPromises()
    expect(wrapper.get('input').attributes('type')).toBe('password')
  })
})
