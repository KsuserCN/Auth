import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { ref } from 'vue'
import { setLanguagePreference } from '@/i18n'
import DeleteAccountView from './DeleteAccountView.vue'

const mocks = vi.hoisted(() => ({
  deleteAccount: vi.fn(),
  confirm: vi.fn(),
  push: vi.fn(),
  userStore: {
    user: { username: 'current-user' } as { username: string } | null,
    fetchUserInfo: vi.fn(),
  },
}))
vi.mock('@/utils/localizedMessageBox', () => ({ localizedMessageBox: { confirm: mocks.confirm } }))
vi.mock('@/api/auth', () => ({
  deleteAccount: mocks.deleteAccount,
  checkSensitiveVerification: vi.fn().mockResolvedValue({ verified: true }),
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => mocks.userStore }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@vueuse/core', () => ({ useDark: () => ref(false) }))
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), warning: vi.fn(), success: vi.fn() },
  ElMessageBox: { confirm: mocks.confirm },
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.userStore.user = { username: 'current-user' }
  mocks.userStore.fetchUserInfo.mockResolvedValue(undefined)
  mocks.deleteAccount.mockResolvedValue(undefined)
  mocks.confirm.mockResolvedValue(undefined)
})

const render = async () => {
  const wrapper = mount(DeleteAccountView, {
    global: {
      stubs: {
        'el-icon': true,
        'el-form': {
          setup: (_: unknown, { expose }: { expose: (value: object) => void }) => {
            expose({ validate: () => Promise.resolve() })
          },
          template: '<div><slot /></div>',
        },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template:
            '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-button': {
          props: ['disabled'],
          template: '<button :disabled="disabled"><slot /></button>',
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

const clickButton = async (wrapper: Awaited<ReturnType<typeof render>>, label: string) => {
  const button = wrapper.findAll('button').find((item) => item.text() === label)
  expect(button, label).toBeDefined()
  await button!.trigger('click')
  await flushPromises()
}

describe('localized account deletion confirmation', () => {
  it.each([
    ['en', 'Next', 'DELETE MY ACCOUNT', 'Delete account'],
    ['zh-Hans', '下一步', '我真的不想要我的号辣', '确认注销'],
  ] as const)(
    'validates %s copy and sends the stable protocol token',
    async (locale, next, phrase, confirm) => {
      setLanguagePreference(locale)
      const wrapper = await render()
      await clickButton(wrapper, next)
      expect(wrapper.text()).toContain(phrase)
      const button = wrapper.findAll('button').find((item) => item.text() === confirm)!
      expect(button.attributes('disabled')).toBeDefined()
      await wrapper.find('input').setValue(phrase)
      await clickButton(wrapper, confirm)
      expect(mocks.confirm).toHaveBeenCalledOnce()
      expect(mocks.deleteAccount).toHaveBeenCalledWith({ confirmText: '我真的不想要我的号辣' })
      wrapper.unmount()
    },
  )

  it('preserves input while switching language and applies the current confirmation phrase', async () => {
    setLanguagePreference('en')
    const wrapper = await render()
    await clickButton(wrapper, 'Next')
    await wrapper.find('input').setValue('DELETE MY ACCOUNT')
    setLanguagePreference('zh-Hans')
    await flushPromises()
    expect(wrapper.find('input').element.value).toBe('DELETE MY ACCOUNT')
    const button = wrapper.findAll('button').find((item) => item.text() === '确认注销')!
    expect(button.attributes('disabled')).toBeDefined()
    expect(mocks.deleteAccount).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
