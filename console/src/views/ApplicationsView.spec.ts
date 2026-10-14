import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent } from 'vue'
import ApplicationsView from './ApplicationsView.vue'

const mocks = vi.hoisted(() => ({
  applications: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  consume: vi.fn(),
  preserve: vi.fn(),
  verification: vi.fn(),
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ fullPath: '/applications' }),
}))
vi.mock('@/api/console', () => ({
  ConsoleApiError: class extends Error {
    constructor(public errorCode: string) {
      super(errorCode)
    }
  },
  consoleApi: {
    applications: mocks.applications,
    createApplication: mocks.create,
    updateApplication: mocks.update,
  },
}))
vi.mock('@/utils/navigation', () => ({
  beginVerification: mocks.verification,
  consumeApplicationDraft: mocks.consume,
  preserveApplicationDraft: mocks.preserve,
  clearApplicationDraft: vi.fn(),
}))
const Button = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})
const Slot = defineComponent({
  template: '<div><slot /><slot name="footer" /></div>',
})
const Dialog = defineComponent({
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
})
const stubs = {
  ElButton: Button,
  ElDialog: Dialog,
  ElAlert: { props: ['title'], template: '<p>{{ title }}</p>' },
  ElAvatar: true,
  ElTag: true,
  ElForm: Slot,
  ElFormItem: Slot,
  ElInput: true,
  ElCheckboxGroup: true,
  ElCheckbox: true,
  ElSwitch: true,
  ElRadioGroup: true,
  ElRadio: true,
  ElTable: defineComponent({
    setup(_props, { expose }) { expose({ clearSelection: vi.fn() }) },
    template: '<div />',
  }),
  ElTableColumn: true,
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.applications.mockResolvedValue([])
  mocks.consume.mockReturnValue(null)
  mocks.create.mockResolvedValue({ application: { clientName: '校园服务' } })
})
describe('official application verification return', () => {
  it('restores a draft and waits for an explicit save', async () => {
    mocks.consume.mockReturnValue({
      clientId: 'campus',
      clientName: '校园服务',
      logoUrl: '',
      clientType: 'public',
      allowMissingNonce: false,
      redirectText: 'https://campus.example/callback',
      scopes: ['openid', 'email'],
      isActive: true,
      editing: false,
    })
    const wrapper = mount(ApplicationsView, {
      global: { stubs, directives: { loading: {} } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('已恢复未提交的配置，请核对后再次保存。')
    expect(mocks.create).not.toHaveBeenCalled()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '保存配置')!
      .trigger('click')
    await flushPromises()
    expect(mocks.create).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({
        clientId: 'campus',
        redirectUris: ['https://campus.example/callback'],
        scopes: ['openid', 'email'],
      }),
    )
    wrapper.unmount()
  })
  it('shows load failure explicitly instead of reporting no applications', async () => {
    mocks.applications.mockRejectedValue(new Error('无权限'))
    const wrapper = mount(ApplicationsView, {
      global: { stubs, directives: { loading: {} } },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('无权限')
    wrapper.unmount()
  })
})
