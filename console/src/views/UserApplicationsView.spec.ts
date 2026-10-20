import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import UserApplicationsView from './UserApplicationsView.vue'
import { componentStubs } from '@/test/componentStubs'

const mocks = vi.hoisted(() => ({ users: vi.fn(), userApplications: vi.fn(), replace: vi.fn() }))
const route = reactive({ query: {} as Record<string, string> })
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: mocks.replace }),
}))
vi.mock('@/api/console', () => ({
  consoleApi: { users: mocks.users, userApplications: mocks.userApplications },
}))
const owner = {
  uuid: 'first-user',
  username: '认证用户',
  email: 'owner@example.test',
  systemRole: 'USER',
  developerVerificationType: 'personal',
  verificationEntityName: '认证姓名',
}
const applications = ['oauth2'].map((protocol) => ({
  protocol,
  clientType: 'public',
  clientId: `${protocol}-app`,
  clientName: `${protocol} 应用`,
  redirectUris: ['https://example.test/callback'],
  scopes: ['openid', 'email'],
  isActive: true,
  createdAt: '2026-10-13T08:00:00',
  updatedAt: '2026-10-13T08:00:00',
}))
const create = () =>
  mount(UserApplicationsView, { global: { stubs: componentStubs, directives: { loading: {} } } })
beforeEach(() => {
  vi.clearAllMocks()
  route.query = {}
  mocks.users.mockResolvedValue({ items: [owner], total: 1 })
  mocks.userApplications.mockResolvedValue({ user: owner, applications })
})
describe('certified user applications', () => {
  it('lists only certified users and sends the selected search and certification filters', async () => {
    const wrapper = create()
    await flushPromises()
    expect(mocks.users).toHaveBeenCalledWith(
      expect.objectContaining({ verification: 'verified', page: 0 }),
    )
    await wrapper.get('[aria-label="搜索认证用户"]').setValue('企业用户')
    await wrapper.get('[aria-label="认证用户类型"]').setValue('enterprise')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(mocks.users).toHaveBeenLastCalledWith(
      expect.objectContaining({ verification: 'enterprise', search: '企业用户', page: 0 }),
    )
    wrapper.unmount()
  })
  it('opens a linked owner and shows their OAuth applications', async () => {
    route.query = { user: 'first-user' }
    const wrapper = create()
    await flushPromises()
    expect(mocks.userApplications).toHaveBeenCalledExactlyOnceWith('first-user')
    expect(wrapper.text()).toContain('oauth2 应用')
    expect(wrapper.text()).toContain('https://example.test/callback')
    expect(wrapper.text()).toContain('公开客户端 / S256 PKCE')
    wrapper.unmount()
  })
  it('ignores an older response when switching owners', async () => {
    let resolveFirst!: (value: unknown) => void
    mocks.userApplications.mockImplementation((uuid: string) =>
      uuid === 'first-user'
        ? new Promise((resolve) => {
            resolveFirst = resolve
          })
        : Promise.resolve({ user: { ...owner, uuid, username: '另一认证用户' }, applications: [] }),
    )
    route.query = { user: 'first-user' }
    const wrapper = create()
    route.query = { user: 'second-user' }
    await flushPromises()
    resolveFirst({ user: owner, applications })
    await flushPromises()
    expect(wrapper.text()).toContain('另一认证用户')
    expect(wrapper.text()).not.toContain('oauth2 应用')
    wrapper.unmount()
  })
  it('shows application failures explicitly instead of displaying a successful empty list', async () => {
    mocks.userApplications.mockRejectedValue(new Error('用户不存在'))
    route.query = { user: 'deleted-user' }
    const wrapper = create()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('用户不存在')
    expect(wrapper.text()).not.toContain('共 0 个应用')
    wrapper.unmount()
  })
})
