import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import ElementPlus, { ElPopconfirm } from 'element-plus'
import PrivacyView from './PrivacyView.vue'

const mocks = vi.hoisted(() => ({
  official: vi.fn(),
  revoke: vi.fn(),
  oauth: vi.fn(),
  sso: vi.fn(),
}))
vi.mock('@/api/official', () => ({
  getOfficialAuthorizations: mocks.official,
  revokeOfficialAuthorization: mocks.revoke,
  officialScopeLabel: (scope: string) => (scope === 'openid' ? '真实用户 UUID' : '邮箱'),
}))
vi.mock('@/api/oauth2', () => ({
  getOAuth2Authorizations: mocks.oauth,
  revokeOAuth2Authorization: vi.fn(),
}))
vi.mock('@/api/sso', () => ({ getSSOAuthorizations: mocks.sso, revokeSSOAuthorization: vi.fn() }))
vi.mock('@/api/auth', () => ({ checkSensitiveVerification: vi.fn() }))
vi.mock('@/api/privacyExport', () => ({
  downloadPrivacyExportFile: vi.fn(),
  fetchPrivacyExportData: vi.fn(),
}))
vi.mock('@/components/SensitiveVerificationDialog.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('@/components/DangerZoneCard.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({}) }))
vi.mock('pinia', () => ({
  storeToRefs: () => ({ user: ref({ systemRole: 'USER', developerVerificationType: 'none' }) }),
}))
const grant = {
  clientId: 'ksuser_console',
  clientName: 'Ksuser Console',
  scopes: ['openid', 'email'],
  authorizedAt: '2026-10-12T00:00:00',
  lastAuthorizedAt: '2026-10-12T00:00:00',
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.oauth.mockResolvedValue([])
  mocks.sso.mockResolvedValue([])
  mocks.official.mockResolvedValue([grant])
  mocks.revoke.mockResolvedValue(undefined)
})
describe('official privacy grants', () => {
  it('shows real UUID and authorized scopes, then removes the revoked grant', async () => {
    const wrapper = mount(PrivacyView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('Ksuser Console')
    expect(wrapper.text()).toContain('真实用户 UUID')
    expect(wrapper.text()).toContain('邮箱')
    wrapper.findComponent(ElPopconfirm).vm.$emit('confirm', new MouseEvent('click'))
    await flushPromises()
    expect(mocks.revoke).toHaveBeenCalledWith('ksuser_console')
    expect(wrapper.text()).not.toContain('Ksuser Console')
    expect(wrapper.text()).toContain('暂无官方应用授权')
    wrapper.unmount()
  })
  it('shows an official grants error without hiding the ordinary grants', async () => {
    mocks.official.mockRejectedValue(new Error('官方服务暂时不可用'))
    const wrapper = mount(PrivacyView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('官方服务暂时不可用')
    expect(mocks.oauth).toHaveBeenCalled()
    expect(mocks.sso).toHaveBeenCalled()
    wrapper.unmount()
  })
})
