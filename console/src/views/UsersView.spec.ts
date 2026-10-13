import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import UsersView from './UsersView.vue'
import type { ConsoleUser } from '@/api/console'
import { componentStubs } from '@/test/componentStubs'

const mocks = vi.hoisted(() => ({
  users: vi.fn(),
  user: vi.fn(),
  setVerification: vi.fn(),
  verify: vi.fn(),
  push: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('@/api/console', () => ({
  consoleApi: { users: mocks.users, user: mocks.user, setVerification: mocks.setVerification },
}))
vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({ session: { user: { uuid: 'super' } } }),
}))
vi.mock('@/utils/navigation', () => ({
  consumeRoleDraft: () => null,
  consumeUserDeleteDraft: () => null,
}))
vi.mock('@/utils/consoleVerification', () => ({
  withConsoleVerification: mocks.verify,
  VerificationCancelledError: class extends Error {},
}))
const user: ConsoleUser = {
  uuid: 'target',
  username: '测试用户',
  email: 'user@example.test',
  systemRole: 'USER',
  developerVerificationType: 'none',
  createdAt: '2026-10-13T08:00:00',
  updatedAt: '2026-10-13T08:00:00',
}
const create = () =>
  mount(UsersView, { global: { stubs: componentStubs, directives: { loading: {} } } })
const click = async (wrapper: VueWrapper, label: string) => {
  await wrapper
    .findAll('button')
    .find((button) => button.text() === label)!
    .trigger('click')
  await flushPromises()
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.users.mockResolvedValue({ items: [{ ...user }], total: 1 })
  mocks.setVerification.mockResolvedValue({
    ...user,
    developerVerificationType: 'enterprise',
    verificationEntityName: '测试企业',
  })
  mocks.verify.mockImplementation((operation: () => Promise<unknown>) => operation())
})
describe('Console user certification', () => {
  it('requires a verified name and saves the selected certification through identity verification', async () => {
    const wrapper = create()
    await flushPromises()
    await click(wrapper, '认证设置')
    await wrapper.get('[aria-label="开发者认证类型"]').setValue('enterprise')
    await click(wrapper, '保存认证')
    expect(wrapper.text()).toContain('请输入已核验的企业名称')
    expect(mocks.setVerification).not.toHaveBeenCalled()
    await wrapper.get('[aria-label="认证主体名称"]').setValue('  测试企业  ')
    await click(wrapper, '保存认证')
    expect(mocks.verify).toHaveBeenCalledOnce()
    expect(mocks.setVerification).toHaveBeenCalledExactlyOnceWith('target', {
      developerVerificationType: 'enterprise',
      verificationEntityName: '测试企业',
    })
    wrapper.unmount()
  })
  it('clears the former entity when revoking an ordinary user certification', async () => {
    mocks.users.mockResolvedValue({
      items: [
        { ...user, developerVerificationType: 'personal', verificationEntityName: '已核验姓名' },
      ],
      total: 1,
    })
    const wrapper = create()
    await flushPromises()
    await click(wrapper, '认证设置')
    await wrapper.get('[aria-label="开发者认证类型"]').setValue('none')
    expect(wrapper.find('[aria-label="认证主体名称"]').exists()).toBe(false)
    await click(wrapper, '保存认证')
    expect(mocks.setVerification).toHaveBeenCalledExactlyOnceWith('target', {
      developerVerificationType: 'none',
      verificationEntityName: null,
    })
    wrapper.unmount()
  })
  it('registers an administrator name while retaining their separate developer certification state', async () => {
    mocks.users.mockResolvedValue({ items: [{ ...user, systemRole: 'ADMIN' }], total: 1 })
    const wrapper = create()
    await flushPromises()
    expect(wrapper.text()).toContain('普通管理员')
    await click(wrapper, '认证设置')
    await wrapper.get('[aria-label="认证主体名称"]').setValue('管理员姓名')
    await click(wrapper, '保存认证')
    expect(mocks.setVerification).toHaveBeenCalledExactlyOnceWith('target', {
      developerVerificationType: 'none',
      verificationEntityName: '管理员姓名',
    })
    wrapper.unmount()
  })
  it('keeps the certification form and error visible when saving fails', async () => {
    mocks.setVerification.mockRejectedValue(new Error('没有权限'))
    const wrapper = create()
    await flushPromises()
    await click(wrapper, '认证设置')
    await wrapper.get('[aria-label="开发者认证类型"]').setValue('personal')
    await wrapper.get('[aria-label="认证主体名称"]').setValue('已核验姓名')
    await click(wrapper, '保存认证')
    expect(wrapper.text()).toContain('没有权限')
    expect((wrapper.get('[aria-label="认证主体名称"]').element as HTMLInputElement).value).toBe(
      '已核验姓名',
    )
    wrapper.unmount()
  })
})
