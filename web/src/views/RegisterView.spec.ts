import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { ElForm, ElFormItem, ElInput, ElButton } from 'element-plus'
import { ApiError } from '@/utils/apiResult'
import RegisterView from './RegisterView.vue'

const mocks = vi.hoisted(() => ({
  route: { query: {} as Record<string, string> },
  checkUsername: vi.fn(),
  sendCode: vi.fn(),
  register: vi.fn(),
  passwordRequirement: vi.fn(),
  finalize: vi.fn(),
  bind: vi.fn(),
  push: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('@vueuse/core', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@vueuse/core')>()),
  useDark: () => ref(false),
}))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: mocks.success, warning: mocks.warning, error: mocks.error },
}))
vi.mock('@/api/auth', () => ({
  checkUsername: mocks.checkUsername,
  sendRegisterCode: mocks.sendCode,
  register: mocks.register,
  getPasswordRequirement: mocks.passwordRequirement,
  bindPendingOAuthAccount: mocks.bind,
}))
vi.mock('@/utils/desktopBridge', () => ({ finalizeWebLogin: mocks.finalize }))

const account = {
  username: 'enter_user',
  email: 'enter@example.com',
  password: 'StrongPass123',
}
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
type RegistrationWrapper = Awaited<ReturnType<typeof render>>

// Enter dispatches this cancelable event when the browser implicitly submits a form.
const submit = async (wrapper: RegistrationWrapper) => {
  const event = new Event('submit', { bubbles: true, cancelable: true })
  wrapper.get('form').element.dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
  await flushPromises()
}
const fillDetails = async (
  wrapper: RegistrationWrapper,
  values: Partial<typeof account> = account,
) => {
  for (const [name, value] of Object.entries(values)) {
    await wrapper.get(`input[name="${name}"]`).setValue(value)
  }
}
const openCodeStep = async (wrapper: RegistrationWrapper) => {
  await fillDetails(wrapper)
  await submit(wrapper)
  expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
}
const submitCode = async (wrapper: RegistrationWrapper, code = '123456') => {
  await wrapper.get('input[name="code"]').setValue(code)
  await submit(wrapper)
}
const useCountdownClock = () => {
  vi.useFakeTimers({
    toFake: ['Date', 'setInterval', 'clearInterval', 'setTimeout', 'clearTimeout'],
  })
  vi.setSystemTime(new Date('2026-10-18T12:00:00Z'))
}
const expireCountdown = async () => {
  await vi.advanceTimersByTimeAsync(60_000)
  await flushPromises()
}
const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((finish) => {
    resolve = finish
  })
  return { promise, resolve }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => {})
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  mocks.route.query = {}
  mocks.passwordRequirement.mockResolvedValue({
    minLength: 6,
    maxLength: 66,
    requireUppercase: true,
    requireLowercase: true,
    requireDigits: true,
    requireSpecialChars: false,
    rejectCommonWeakPasswords: true,
    requirementCode: 'PASSWORD_REQUIREMENTS_7',
    requirementParams: { minLength: 6, maxLength: 66 },
  })
  mocks.checkUsername.mockResolvedValue(true)
  mocks.sendCode.mockResolvedValue(undefined)
  mocks.register.mockResolvedValue({
    accessToken: 'new-session',
    uuid: 'new-user',
    username: account.username,
    email: account.email,
  })
  mocks.finalize.mockResolvedValue(false)
  mocks.bind.mockResolvedValue({ errorCode: 'REQUEST_SUCCESS' })
  mocks.push.mockResolvedValue(undefined)
})
afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('two-step registration', () => {
  it('collects three labeled fields once and completes registration using native form submission', async () => {
    const wrapper = await render()
    const details = wrapper.get('[data-testid="details-form"]')
    expect(details.findAll('input').map((input) => input.attributes('name'))).toEqual([
      'username',
      'email',
      'password',
    ])
    expect(details.findAll('label')).toHaveLength(3)
    expect(wrapper.get('.next-btn').attributes('type')).toBe('submit')
    expect(wrapper.get('input[name="password"]').attributes('autocomplete')).toBe('new-password')

    await openCodeStep(wrapper)
    expect(mocks.checkUsername).toHaveBeenCalledExactlyOnceWith(account.username)
    expect(mocks.sendCode).toHaveBeenCalledExactlyOnceWith({ email: account.email })
    expect(mocks.checkUsername.mock.invocationCallOrder[0]).toBeLessThan(
      mocks.sendCode.mock.invocationCallOrder[0]!,
    )
    const code = wrapper.get('input[name="code"]')
    expect(code.attributes('inputmode')).toBe('numeric')
    expect(code.attributes('autocomplete')).toBe('one-time-code')
    expect(code.attributes('maxlength')).toBe('6')
    expect(wrapper.get('.next-btn').attributes('type')).toBe('submit')

    await submitCode(wrapper, '123')
    expect(mocks.register).not.toHaveBeenCalled()
    await submitCode(wrapper)
    expect(mocks.register).toHaveBeenCalledExactlyOnceWith({ ...account, code: '123456' })
    expect(mocks.finalize).toHaveBeenCalledExactlyOnceWith({
      accessToken: 'new-session',
      user: { uuid: 'new-user', username: account.username, email: account.email },
    })
    expect(mocks.bind).not.toHaveBeenCalled()
    expect(mocks.success).toHaveBeenLastCalledWith('注册成功')
    expect(mocks.push).toHaveBeenCalledWith('/home')
  })

  it.each([
    { username: 'ab' },
    { username: 'invalid user' },
    { email: 'invalid-email' },
    { password: 'weak' },
  ])('validates every details field before making requests: %j', async (invalid) => {
    const wrapper = await render()
    await fillDetails(wrapper, { ...account, ...invalid })
    await submit(wrapper)
    expect(wrapper.find('.el-form-item.is-error').exists()).toBe(true)
    expect(wrapper.find('[data-testid="details-form"]').exists()).toBe(true)
    expect(mocks.checkUsername).not.toHaveBeenCalled()
    expect(mocks.sendCode).not.toHaveBeenCalled()
  })

  it('rejects an unavailable username and clears its availability hint when edited', async () => {
    mocks.checkUsername.mockResolvedValueOnce(false)
    const wrapper = await render()
    await fillDetails(wrapper)
    await submit(wrapper)
    expect(mocks.sendCode).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('用户名暂不可用')
    await wrapper.get('input[name="username"]').setValue('another_user')
    expect(wrapper.text()).not.toContain('用户名暂不可用')
    await submit(wrapper)
    expect(mocks.checkUsername).toHaveBeenLastCalledWith('another_user')
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('honors the fetched password policy and its allowed special characters', async () => {
    mocks.passwordRequirement.mockResolvedValueOnce({
      minLength: 10,
      maxLength: 20,
      requireUppercase: true,
      requireLowercase: true,
      requireDigits: true,
      requireSpecialChars: true,
      rejectCommonWeakPasswords: true,
      requirementCode: 'PASSWORD_REQUIREMENTS_15',
      requirementParams: { minLength: 10, maxLength: 20 },
    })
    const wrapper = await render()
    await fillDetails(wrapper, { ...account, password: 'StrongPass1:' })
    await submit(wrapper)
    expect(mocks.checkUsername).not.toHaveBeenCalled()
    expect(wrapper.find('.el-form-item.is-error').exists()).toBe(true)
    await wrapper.get('input[name="password"]').setValue('StrongPass1&')
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('uses the server default password rules if fetching its policy fails', async () => {
    mocks.passwordRequirement.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await render()
    await fillDetails(wrapper, { ...account, password: 'lowercase1' })
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('locks the details and ignores repeated submits during username checking and email sending', async () => {
    const check = deferred<boolean>()
    const send = deferred<void>()
    mocks.checkUsername.mockReturnValueOnce(check.promise)
    mocks.sendCode.mockReturnValueOnce(send.promise)
    const wrapper = await render()
    await fillDetails(wrapper)
    await submit(wrapper)
    expect(
      wrapper.findAll('input').every((input) => input.attributes('disabled') !== undefined),
    ).toBe(true)
    await submit(wrapper)
    expect(mocks.checkUsername).toHaveBeenCalledTimes(1)
    check.resolve(true)
    await flushPromises()
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    expect(
      wrapper.findAll('input').every((input) => input.attributes('disabled') !== undefined),
    ).toBe(true)
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    send.resolve(undefined)
    await flushPromises()
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('preserves details after a network failure and allows retry without duplicate error toasts', async () => {
    mocks.sendCode.mockRejectedValueOnce(new ApiError({ errorCode: 'NETWORK_ERROR' }))
    const wrapper = await render()
    await fillDetails(wrapper)
    await submit(wrapper)
    expect(wrapper.find('[data-testid="details-form"]').exists()).toBe(true)
    for (const [name, value] of Object.entries(account)) {
      expect((wrapper.get(`input[name="${name}"]`).element as HTMLInputElement).value).toBe(value)
    }
    expect(wrapper.get('.next-btn').attributes('disabled')).toBeUndefined()
    expect(mocks.error).not.toHaveBeenCalled()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('respects the reserved cooldown after initial email delivery fails', async () => {
    useCountdownClock()
    mocks.sendCode.mockRejectedValueOnce(new ApiError({ errorCode: 'AUTH_015' }, 500))
    const wrapper = await render()
    await fillDetails(wrapper)
    await submit(wrapper)
    expect(wrapper.find('[data-testid="details-form"]').exists()).toBe(true)
    expect(wrapper.get('.next-btn').attributes('disabled')).toBeDefined()
    expect(mocks.error).not.toHaveBeenCalled()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    await expireCountdown()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
  })

  it('preserves details and the resend cooldown when returning to edit the same email', async () => {
    useCountdownClock()
    const wrapper = await render()
    await openCodeStep(wrapper)
    await wrapper.get('input[name="code"]').setValue('654321')
    await vi.advanceTimersByTimeAsync(20_000)
    await wrapper.get('.change-email-btn').trigger('click')
    for (const [name, value] of Object.entries(account)) {
      expect((wrapper.get(`input[name="${name}"]`).element as HTMLInputElement).value).toBe(value)
    }
    await submit(wrapper)
    expect(mocks.checkUsername).toHaveBeenCalledTimes(2)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    expect(wrapper.get('.resend-btn').attributes('disabled')).toBeDefined()
    await vi.advanceTimersByTimeAsync(40_000)
    await flushPromises()
    expect(wrapper.get('.resend-btn').attributes('disabled')).toBeUndefined()
    await wrapper.get('.resend-btn').trigger('click')
    await flushPromises()
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('')
  })

  it('sends a new code for a changed email without allowing switching back to bypass its cooldown', async () => {
    useCountdownClock()
    const wrapper = await render()
    await openCodeStep(wrapper)
    await wrapper.get('input[name="code"]').setValue('654321')
    await wrapper.get('.back-btn').trigger('click')
    await wrapper.get('input[name="email"]').setValue('other@example.com')
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenLastCalledWith({ email: 'other@example.com' })
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('')
    await wrapper.get('.back-btn').trigger('click')
    await wrapper.get('input[name="email"]').setValue(account.email)
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    expect(wrapper.get('.resend-btn').attributes('disabled')).toBeDefined()
  })

  it('retains an entered code on network failure, clears it on success, and blocks concurrent sends', async () => {
    useCountdownClock()
    const wrapper = await render()
    await openCodeStep(wrapper)
    await wrapper.get('input[name="code"]').setValue('654321')
    await expireCountdown()
    mocks.sendCode.mockRejectedValueOnce(new ApiError({ errorCode: 'NETWORK_ERROR' }))
    await wrapper.get('.resend-btn').trigger('click')
    await flushPromises()
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('654321')
    const send = deferred<void>()
    mocks.sendCode.mockReturnValueOnce(send.promise)
    await wrapper.get('.resend-btn').trigger('click')
    await flushPromises()
    expect(wrapper.get('input[name="code"]').attributes('disabled')).toBeDefined()
    await submit(wrapper)
    expect(mocks.register).not.toHaveBeenCalled()
    expect(mocks.sendCode).toHaveBeenCalledTimes(3)
    send.resolve(undefined)
    await flushPromises()
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('.resend-btn').attributes('disabled')).toBeDefined()
  })

  it('invalidates the replaced code and reserves a new cooldown after resend delivery fails', async () => {
    useCountdownClock()
    const wrapper = await render()
    await openCodeStep(wrapper)
    await wrapper.get('input[name="code"]').setValue('654321')
    await expireCountdown()
    mocks.sendCode.mockRejectedValueOnce(new ApiError({ errorCode: 'AUTH_015' }, 500))
    await wrapper.get('.resend-btn').trigger('click')
    await flushPromises()
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('')
    expect(wrapper.get('.next-btn').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.resend-btn').attributes('disabled')).toBeDefined()
    await submitCode(wrapper)
    expect(mocks.register).not.toHaveBeenCalled()
    await wrapper.get('.back-btn').trigger('click')
    expect(wrapper.get('.next-btn').attributes('disabled')).toBeDefined()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    await expireCountdown()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
    await submitCode(wrapper)
    expect(mocks.register).toHaveBeenCalledTimes(1)
  })

  it('keeps wrong-code failures on the code step and allows retry', async () => {
    mocks.register.mockRejectedValueOnce(
      new ApiError({ errorCode: 'AUTH_027', params: { count: 1 } }, 400),
    )
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
    expect(mocks.finalize).not.toHaveBeenCalled()
    expect(mocks.push).not.toHaveBeenCalled()
    expect(mocks.error).not.toHaveBeenCalled()
    await submitCode(wrapper, '654321')
    expect(mocks.register).toHaveBeenCalledTimes(2)
    expect(mocks.push).toHaveBeenCalledWith('/home')
  })

  it('requires a resend after code expiry without losing the filled account details', async () => {
    useCountdownClock()
    mocks.register.mockRejectedValueOnce(
      new ApiError({ errorCode: 'AUTH_026', params: { count: 1 } }, 400),
    )
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
    expect(wrapper.get('.next-btn').attributes('disabled')).toBeDefined()
    await submit(wrapper)
    expect(mocks.register).toHaveBeenCalledTimes(1)
    await expireCountdown()
    await wrapper.get('.resend-btn').trigger('click')
    await flushPromises()
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    await submitCode(wrapper, '654321')
    expect(mocks.register).toHaveBeenLastCalledWith({ ...account, code: '654321' })
  })

  it('returns username conflicts to details and requires a new code after the cooldown', async () => {
    useCountdownClock()
    mocks.register.mockRejectedValueOnce(new ApiError({ errorCode: 'AUTH_029' }, 400))
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.find('[data-testid="details-form"]').exists()).toBe(true)
    expect(wrapper.text()).toMatch(/用户名.*(已|使用|占用)/)
    expect(mocks.finalize).not.toHaveBeenCalled()
    await wrapper.get('input[name="username"]').setValue('another_user')
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="details-form"]').exists()).toBe(true)
    await expireCountdown()
    await submit(wrapper)
    expect(mocks.sendCode).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="code-form"]').exists()).toBe(true)
    expect((wrapper.get('input[name="code"]').element as HTMLInputElement).value).toBe('')
    await submitCode(wrapper, '654321')
    expect(mocks.register).toHaveBeenLastCalledWith({
      ...account,
      username: 'another_user',
      code: '654321',
    })
  })

  it('ignores repeated final submits and locks controls through session finalization', async () => {
    const registration = deferred<object>()
    const finalize = deferred<boolean>()
    mocks.register.mockReturnValueOnce(registration.promise)
    mocks.finalize.mockReturnValueOnce(finalize.promise)
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.get('input[name="code"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.back-btn').attributes('disabled')).toBeDefined()
    await submit(wrapper)
    expect(mocks.register).toHaveBeenCalledTimes(1)
    registration.resolve({
      accessToken: 'new-session',
      uuid: 'new-user',
      username: account.username,
      email: account.email,
    })
    await flushPromises()
    expect(wrapper.find('.created-account').exists()).toBe(true)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('.continue-account-btn').attributes('disabled')).toBeDefined()
    await wrapper.get('.continue-account-btn').trigger('click')
    await flushPromises()
    expect(mocks.register).toHaveBeenCalledTimes(1)
    expect(mocks.finalize).toHaveBeenCalledTimes(1)
    expect(mocks.push).not.toHaveBeenCalled()
    finalize.resolve(true)
    await flushPromises()
    expect(mocks.success).toHaveBeenLastCalledWith('注册成功，已同步到桌面端')
    expect(mocks.push).toHaveBeenCalledWith('/home')
  })

  it('retries failed session finalization from the created account without registering again', async () => {
    mocks.route.query = { oauthBindProvider: 'github', oauthBindToken: 'bind-ticket' }
    mocks.finalize.mockRejectedValueOnce(new Error('storage unavailable'))
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.find('.created-account').exists()).toBe(true)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('.continue-account-btn').attributes('disabled')).toBeUndefined()
    expect(mocks.finalize).toHaveBeenCalledTimes(1)
    expect(mocks.bind).not.toHaveBeenCalled()
    expect(mocks.push).not.toHaveBeenCalled()
    await wrapper.get('.continue-account-btn').trigger('click')
    await flushPromises()
    expect(mocks.register).toHaveBeenCalledTimes(1)
    expect(mocks.finalize).toHaveBeenCalledTimes(2)
    expect(mocks.bind).toHaveBeenCalledExactlyOnceWith({
      provider: 'github',
      bindToken: 'bind-ticket',
    })
    expect(mocks.push).toHaveBeenCalledExactlyOnceWith('/home')
  })

  it('retries failed navigation without repeating registration, finalization or binding', async () => {
    mocks.route.query = { oauthBindProvider: 'github', oauthBindToken: 'bind-ticket' }
    mocks.push.mockRejectedValueOnce(new Error('navigation failed'))
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(wrapper.find('.created-account').exists()).toBe(true)
    expect(wrapper.find('form').exists()).toBe(false)
    expect(wrapper.get('.continue-account-btn').attributes('disabled')).toBeUndefined()
    expect(mocks.finalize).toHaveBeenCalledTimes(1)
    expect(mocks.bind).toHaveBeenCalledTimes(1)
    await wrapper.get('.continue-account-btn').trigger('click')
    await flushPromises()
    expect(mocks.register).toHaveBeenCalledTimes(1)
    expect(mocks.finalize).toHaveBeenCalledTimes(1)
    expect(mocks.bind).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenCalledTimes(2)
    expect(mocks.push).toHaveBeenLastCalledWith('/home')
  })

  it.each(['qq', 'github', 'microsoft', 'google'])(
    'preserves the pending %s account binding',
    async (provider) => {
      mocks.route.query = { oauthBindProvider: provider, oauthBindToken: ' bind-ticket ' }
      const wrapper = await render()
      expect(wrapper.text()).toContain('注册并绑定账号')
      await openCodeStep(wrapper)
      await submitCode(wrapper)
      expect(mocks.bind).toHaveBeenCalledExactlyOnceWith({ provider, bindToken: 'bind-ticket' })
      expect(mocks.finalize.mock.invocationCallOrder[0]).toBeLessThan(
        mocks.bind.mock.invocationCallOrder[0]!,
      )
      expect(mocks.push).toHaveBeenCalledWith('/home')
    },
  )

  it('preserves OAuth query parameters when canceling back to login', async () => {
    mocks.route.query = { oauthBindProvider: 'github', oauthBindToken: 'bind-ticket' }
    const wrapper = await render()
    await wrapper.get('.back-btn').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({ path: '/login', query: mocks.route.query })
    expect(mocks.register).not.toHaveBeenCalled()
  })

  it('warns after binding failure but still completes login and prevents registering twice', async () => {
    mocks.route.query = { oauthBindProvider: 'github', oauthBindToken: 'bind-ticket' }
    mocks.bind.mockRejectedValueOnce(new ApiError({ errorCode: 'OAUTH_029' }, 400))
    const wrapper = await render()
    await openCodeStep(wrapper)
    await submitCode(wrapper)
    expect(mocks.finalize).toHaveBeenCalledTimes(1)
    expect(mocks.warning).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenCalledWith('/home')
    await wrapper.get('.continue-account-btn').trigger('click')
    await flushPromises()
    expect(mocks.register).toHaveBeenCalledTimes(1)
    expect(mocks.bind).toHaveBeenCalledTimes(1)
  })
})
