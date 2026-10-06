import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import AppleRegisterView from './AppleRegisterView.vue'
import { readPendingAppleAccount, savePendingAppleAccount } from '@/utils/appleAccountFlow'

const mocks = vi.hoisted(() => ({
  route: { query: {} as Record<string, string> },
  push: vi.fn(),
  replace: vi.fn(),
  register: vi.fn(),
  bind: vi.fn(),
  sensitive: vi.fn(),
  apple: vi.fn(),
  finalize: vi.fn(),
  token: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => mocks }))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false) }))
vi.mock('@/api/auth', () => ({
  registerPendingApple: mocks.register,
  bindPendingApple: mocks.bind,
  checkSensitiveVerification: mocks.sensitive,
  checkUsername: vi.fn().mockResolvedValue(true),
}))
vi.mock('@/utils/appleSignIn', () => ({
  prepareAppleSignIn: vi.fn().mockResolvedValue(undefined),
  signInWithApple: mocks.apple,
}))
vi.mock('@/utils/desktopBridge', () => ({ finalizeWebLogin: mocks.finalize }))
vi.mock('@/utils/authSession', () => ({ getStoredAccessToken: mocks.token }))
vi.mock('@/components/SensitiveVerificationDialog.vue', () => ({
  default: {
    name: 'SensitiveVerificationDialog',
    props: ['modelValue'],
    template: '<div v-if="modelValue" class="verification" />',
  },
}))

const mounted: ReturnType<typeof mount>[] = []
const render = () => {
  const wrapper = mount(AppleRegisterView, {
    global: {
      stubs: {
        'el-button': {
          props: ['disabled', 'loading'],
          template: '<button :disabled="disabled || loading"><slot /></button>',
        },
        'el-icon': true,
        'el-alert': { props: ['title'], template: '<p>{{ title }}</p>' },
        'el-form': {
          template: '<form @submit.prevent="$emit(\'submit\', $event)"><slot /></form>',
        },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': true,
        'el-checkbox': {
          props: ['modelValue'],
          template:
            '<input type="checkbox" @change="$emit(\'update:modelValue\', $event.target.checked)" />',
        },
      },
    },
  })
  mounted.push(wrapper)
  return wrapper
}
const button = (wrapper: ReturnType<typeof render>, label: string) =>
  wrapper.findAll('button').find((item) => item.text() === label)!
beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  mocks.route.query = {}
  mocks.token.mockReturnValue('existing-session')
  mocks.sensitive.mockResolvedValue({ verified: true })
  mocks.apple.mockResolvedValue({ needBind: true, oauthBindToken: 'fresh-bind-ticket' })
  mocks.bind.mockResolvedValue(undefined)
  mocks.register.mockResolvedValue({ accessToken: 'new-session', user: { uuid: 'new-user' } })
  savePendingAppleAccount({ needBind: true, oauthBindToken: 'login-ticket', canRegister: true })
})
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe('Apple account association', () => {
  it('offers both choices without registering automatically', async () => {
    const wrapper = render()
    expect(button(wrapper, '绑定已有 Ksuser 账号').exists()).toBe(true)
    expect(button(wrapper, '注册并绑定 Apple 账号').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('form').exists()).toBe(false)
    expect(mocks.register).not.toHaveBeenCalled()
    await button(wrapper, '绑定已有 Ksuser 账号').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/login',
      query: { oauthBindProvider: 'apple' },
    })
  })

  it('keeps existing account binding available when the Apple email conflicts', async () => {
    savePendingAppleAccount({
      needBind: true,
      oauthBindToken: 'ticket',
      canRegister: true,
      emailConflict: true,
    })
    const wrapper = render()
    expect(button(wrapper, '注册并绑定 Apple 账号').attributes('disabled')).toBeDefined()
    expect(button(wrapper, '绑定已有 Ksuser 账号').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('此 Apple 邮箱已有 Ksuser 账号')
  })

  it('registers only after choosing registration and accepting terms', async () => {
    const wrapper = render()
    await button(wrapper, '注册并绑定 Apple 账号').trigger('click')
    expect(mocks.register).not.toHaveBeenCalled()
    await wrapper.find('input[type="checkbox"]').setValue(true)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(mocks.register).toHaveBeenCalledWith('login-ticket', true, '')
    expect(mocks.finalize).toHaveBeenCalledWith({
      accessToken: 'new-session',
      user: { uuid: 'new-user' },
    })
    expect(readPendingAppleAccount()).toBeNull()
  })

  it('verifies first and binds using a fresh authenticated Apple credential', async () => {
    mocks.route.query = { mode: 'bind' }
    sessionStorage.setItem('ksuser:post-login-redirect', '/home/security')
    const wrapper = render()
    await button(wrapper, '验证并绑定 Apple').trigger('click')
    await flushPromises()
    expect(mocks.sensitive).toHaveBeenCalledOnce()
    expect(mocks.apple).toHaveBeenCalledWith('bind')
    expect(mocks.bind).toHaveBeenCalledWith('fresh-bind-ticket')
    expect(mocks.replace).toHaveBeenCalledWith('/home/security')
    expect(readPendingAppleAccount()).toBeNull()
  })

  it('waits for required sensitive verification before Apple authorization', async () => {
    mocks.route.query = { mode: 'bind' }
    mocks.sensitive.mockResolvedValue({ verified: false })
    const wrapper = render()
    await button(wrapper, '验证并绑定 Apple').trigger('click')
    await flushPromises()
    expect(wrapper.find('.verification').exists()).toBe(true)
    expect(mocks.apple).not.toHaveBeenCalled()
    wrapper.findComponent({ name: 'SensitiveVerificationDialog' }).vm.$emit('success')
    await flushPromises()
    expect(mocks.bind).toHaveBeenCalledWith('fresh-bind-ticket')
  })

  it('keeps binding failures visible and allows a fresh authorization retry', async () => {
    mocks.route.query = { mode: 'bind' }
    mocks.bind.mockRejectedValueOnce(new Error('Apple 身份已绑定'))
    const wrapper = render()
    await button(wrapper, '验证并绑定 Apple').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Apple 身份已绑定')
    expect(mocks.replace).not.toHaveBeenCalled()
    await button(wrapper, '验证并绑定 Apple').trigger('click')
    await flushPromises()
    expect(mocks.apple).toHaveBeenCalledTimes(2)
    expect(mocks.replace).toHaveBeenCalledWith('/home/overview')
  })

  it('handles missing or corrupted authorization without showing registration', () => {
    sessionStorage.setItem('ksuser:pending-apple-account', '{broken')
    const wrapper = render()
    expect(wrapper.text()).toContain('Apple 授权已过期')
    expect(wrapper.find('.account-choices').exists()).toBe(false)
  })
})
