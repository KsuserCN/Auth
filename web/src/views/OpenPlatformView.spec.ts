import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import { setLanguagePreference } from '@/i18n'
import OpenPlatformView from './OpenPlatformView.vue'

const mocks = vi.hoisted(() => ({
  user: vi.fn(),
  oauth: vi.fn(),
  sso: vi.fn(),
}))
vi.mock('@/api/auth', () => ({ getUserInfo: mocks.user, getUserDetailsInfo: vi.fn() }))
vi.mock('@/api/oauth2', () => ({
  getOAuth2Apps: mocks.oauth,
  createOAuth2App: vi.fn(),
  deleteOAuth2App: vi.fn(),
  uploadOAuth2AppLogo: vi.fn(),
  updateOAuth2App: vi.fn(),
}))
vi.mock('@/api/sso', () => ({
  getSSOClients: mocks.sso,
  createSSOClient: vi.fn(),
  deleteSSOClient: vi.fn(),
  uploadSSOClientLogo: vi.fn(),
  updateSSOClient: vi.fn(),
}))

const account = {
  uuid: 'owner-uuid',
  username: '开发者昵称',
  email: 'owner@example.com',
  verificationType: 'enterprise',
  developerVerificationType: 'enterprise',
  verificationEntityName: '  示例科技有限公司  ',
  systemRole: 'USER',
}
const oauthApp = {
  appId: 'app-123',
  appName: '校园服务',
  redirectUri: 'https://example.com/callback',
  contactInfo: 'support@example.com',
  scopes: ['profile'],
  createdAt: '2026-10-01T12:00:00',
  updatedAt: '2026-10-01T12:00:00',
}
const oauthOverview = {
  verificationType: 'enterprise',
  developerVerificationType: 'enterprise',
  systemRole: 'USER',
  verified: true,
  currentCount: 1,
  maxApps: 5,
  canCreate: true,
  apps: [oauthApp],
}
const ssoOverview = {
  verificationType: 'enterprise',
  developerVerificationType: 'enterprise',
  systemRole: 'USER',
  admin: false,
  currentCount: 0,
  maxClients: 10,
  canCreate: false,
  clients: [],
}
const render = async () => {
  const wrapper = mount(OpenPlatformView, {
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: {
        ElDialog: {
          props: ['modelValue', 'title'],
          template:
            '<section v-if="modelValue" role="dialog"><h2>{{ title }}</h2><slot /><slot name="footer" /></section>',
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.user.mockResolvedValue({ ...account })
  mocks.oauth.mockResolvedValue({ ...oauthOverview })
  mocks.sso.mockResolvedValue({ ...ssoOverview })
})

describe('service integration account identity and applications', () => {
  it.each([
    ['personal', '张三', '个人认证'],
    ['enterprise', '示例科技有限公司', '企业认证'],
  ])(
    'shows the registered %s owner separately from the account nickname',
    async (type, name, label) => {
      mocks.user.mockResolvedValue({ ...account, verificationEntityName: ` ${name} ` })
      mocks.oauth.mockResolvedValue({ ...oauthOverview, developerVerificationType: type })
      const wrapper = await render()
      expect(wrapper.get('.identity-name').text()).toBe(name)
      expect(wrapper.get('.identity-title-row').text()).toContain(label)
      expect(wrapper.get('.identity-account-name').text()).toContain('开发者昵称')
      await wrapper.get('button.app-title-wrap').trigger('click')
      expect(wrapper.get('[role="dialog"]').text()).toContain(oauthApp.redirectUri)
      wrapper.unmount()
    },
  )

  it('keeps organization verification visible for an administrator and switches SSO actions', async () => {
    mocks.user.mockResolvedValue({ ...account, systemRole: 'ADMIN', verificationType: 'admin' })
    mocks.oauth.mockResolvedValue({
      ...oauthOverview,
      systemRole: 'ADMIN',
      verificationType: 'admin',
    })
    mocks.sso.mockResolvedValue({
      ...ssoOverview,
      systemRole: 'ADMIN',
      admin: true,
      canCreate: true,
    })
    const wrapper = await render()
    expect(wrapper.get('.identity-title-row').text()).toContain('企业认证')
    expect(wrapper.get('.identity-account-name').text()).toContain('管理员')
    await wrapper.get('#tab-sso').trigger('click')
    expect(wrapper.get('.quota-text').text()).toContain('0 / 10')
    expect(wrapper.get('.docs-link').attributes('href')).toContain('/auth/sso')
    await wrapper.get('.toolbar-actions .el-button--primary').trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('创建 SSO 内部服务应用')
    wrapper.unmount()
  })

  it('does not substitute an editable real name or nickname for missing verified owner records', async () => {
    mocks.user.mockResolvedValue({
      ...account,
      verificationEntityName: '  ',
      realName: '可编辑姓名',
    })
    const wrapper = await render()
    expect(wrapper.get('.identity-name').text()).toBe('尚未登记认证主体')
    expect(wrapper.get('.identity-title-row').text()).toContain('企业认证')
    expect(wrapper.get('.identity-name').text()).not.toContain('开发者昵称')
    expect(wrapper.text()).not.toContain('可编辑姓名')
    wrapper.unmount()
  })

  it('explains why an unverified user cannot create OAuth or SSO applications', async () => {
    mocks.user.mockResolvedValue({
      ...account,
      developerVerificationType: 'none',
      verificationEntityName: null,
    })
    mocks.oauth.mockResolvedValue({
      ...oauthOverview,
      developerVerificationType: 'none',
      verified: false,
      canCreate: false,
      currentCount: 0,
      apps: [],
    })
    const wrapper = await render()
    expect(wrapper.get('.identity-title-row').text()).toContain('未认证')
    expect(wrapper.get('.permission-note').text()).toContain('个人或企业认证')
    expect(wrapper.get('.toolbar-actions .el-button--primary').attributes()).toHaveProperty(
      'disabled',
    )
    await wrapper.get('#tab-sso').trigger('click')
    expect(wrapper.get('.restricted-state').text()).toContain('仅管理员可管理内部服务')
    expect(wrapper.find('.quota-text').exists()).toBe(false)
    expect(wrapper.get('.toolbar-actions .el-button--primary').attributes()).toHaveProperty(
      'disabled',
    )
    expect(wrapper.text()).not.toContain('users.verification_type')
    wrapper.unmount()
  })

  it('distinguishes a failed request from an empty list and recovers on retry', async () => {
    mocks.oauth.mockRejectedValueOnce(new Error('Network unavailable'))
    const wrapper = await render()
    expect(wrapper.get('.applications-content [role="alert"]').text()).toContain('应用列表加载失败')
    expect(wrapper.text()).not.toContain('暂无 OAuth 应用')
    expect(wrapper.get('.toolbar-actions .el-button--primary').attributes()).toHaveProperty(
      'disabled',
    )
    await wrapper.get('.applications-content [role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.applications-content [role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.app-name').text()).toBe('校园服务')
    expect(
      wrapper.get('.toolbar-actions .el-button--primary').attributes('disabled'),
    ).toBeUndefined()
    wrapper.unmount()
  })

  it('explains quota limits and updates new copy when the language changes', async () => {
    mocks.oauth.mockResolvedValue({ ...oauthOverview, currentCount: 5, canCreate: false })
    const wrapper = await render()
    expect(wrapper.get('.permission-note').text()).toContain('应用数量已达上限')
    expect(wrapper.get('.toolbar-actions .el-button--primary').attributes()).toHaveProperty(
      'disabled',
    )
    setLanguagePreference('en')
    await flushPromises()
    expect(wrapper.get('.identity-label').text()).toBe('Registered owner')
    expect(wrapper.get('.permission-note').text()).toContain('Application limit reached')
    expect(wrapper.get('.quota-text').text()).toBe('5 / 5 applications created')
    wrapper.unmount()
  })

  it('retries account identity independently of the application list', async () => {
    mocks.user.mockRejectedValueOnce(new Error('Account unavailable'))
    const wrapper = await render()
    expect(wrapper.get('.identity-panel [role="alert"]').text()).toContain('账号认证信息加载失败')
    expect(wrapper.get('.app-name').text()).toBe('校园服务')
    await wrapper.get('.identity-panel [role="alert"] button').trigger('click')
    await flushPromises()
    expect(wrapper.get('.identity-name').text()).toBe('示例科技有限公司')
    expect(mocks.oauth).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
