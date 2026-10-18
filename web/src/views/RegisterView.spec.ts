import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { ElForm, ElFormItem, ElInput, ElButton } from 'element-plus'
import RegisterView from './RegisterView.vue'

const mocks = vi.hoisted(() => ({
  checkUsername: vi.fn(),
  sendCode: vi.fn(),
  register: vi.fn(),
  passwordRequirement: vi.fn(),
  finalize: vi.fn(),
  push: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDark: () => ref(false),
}))
vi.mock('@/api/auth', () => ({
  checkUsername: mocks.checkUsername,
  sendRegisterCode: mocks.sendCode,
  register: mocks.register,
  getPasswordRequirement: mocks.passwordRequirement,
  bindPendingOAuthAccount: vi.fn(),
}))
vi.mock('@/utils/desktopBridge', () => ({ finalizeWebLogin: mocks.finalize }))

const mounted: ReturnType<typeof mount>[] = []
const render = async () => {
  const wrapper = mount(RegisterView, {
    global: {
      components: { ElForm, ElFormItem, ElInput, ElButton },
      stubs: { 'router-link': true, 'el-icon': true },
    },
  })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

// Browsers dispatch this cancelable event when Enter implicitly submits a form.
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
  mocks.checkUsername.mockResolvedValue(true)
  mocks.sendCode.mockResolvedValue(undefined)
  mocks.register.mockResolvedValue({
    accessToken: 'new-session',
    uuid: 'new-user',
    username: 'enter_user',
    email: 'enter@example.com',
  })
  mocks.finalize.mockResolvedValue(false)
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
})

describe('registration form submission', () => {
  it('prevents navigation and validates the current step throughout registration', async () => {
    const wrapper = await render()
    await enter(wrapper, 'ab')
    expect(mocks.checkUsername).not.toHaveBeenCalled()
    expect(wrapper.find('.el-form-item.is-error').exists()).toBe(true)

    await enter(wrapper, 'enter_user')
    expect(mocks.checkUsername).toHaveBeenCalledWith('enter_user')
    expect(wrapper.get('input').attributes('type')).toBe('password')

    await enter(wrapper, 'weak')
    expect(wrapper.find('.password-requirements').exists()).toBe(true)
    await enter(wrapper, 'StrongPass123')
    expect(wrapper.find('.password-requirements').exists()).toBe(false)

    await enter(wrapper, 'DifferentPass123')
    expect(wrapper.get('input').attributes('type')).toBe('password')
    await enter(wrapper, 'StrongPass123')
    expect(wrapper.get('input').attributes('type')).toBe('email')

    await enter(wrapper, 'invalid-email')
    expect(mocks.sendCode).not.toHaveBeenCalled()
    await enter(wrapper, 'enter@example.com')
    expect(mocks.sendCode).toHaveBeenCalledWith({ email: 'enter@example.com' })

    await enter(wrapper, '123')
    expect(mocks.register).not.toHaveBeenCalled()
    await enter(wrapper, '123456')
    expect(mocks.register).toHaveBeenCalledExactlyOnceWith({
      username: 'enter_user',
      password: 'StrongPass123',
      email: 'enter@example.com',
      code: '123456',
    })
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'new-session',
      user: { uuid: 'new-user', username: 'enter_user', email: 'enter@example.com' },
    })
    expect(mocks.push).toHaveBeenCalledWith('/home')
  })

  it('ignores repeated submits while checking the username', async () => {
    let finishCheck!: (available: boolean) => void
    mocks.checkUsername.mockReturnValue(
      new Promise((resolve) => {
        finishCheck = resolve
      }),
    )
    const wrapper = await render()
    await enter(wrapper, 'enter_user')
    await submit(wrapper)
    expect(mocks.checkUsername).toHaveBeenCalledTimes(1)
    finishCheck(true)
    await flushPromises()
    expect(wrapper.get('input').attributes('type')).toBe('password')
  })
})
