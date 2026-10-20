import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { setLanguagePreference } from '@/i18n'
import type { OAuth2App } from '@/api/oauth2'
import ApplicationPermissions from './ApplicationPermissions.vue'

const mocks = vi.hoisted(() => ({ apply: vi.fn(), close: vi.fn() }))
vi.mock('@/api/oauth2', () => ({ applyApplicationUnionid: mocks.apply, closeApplicationUnionid: mocks.close }))
const app: OAuth2App = { appId: 'web', appName: 'Website', redirectUri: 'https://example.test/callback', contactInfo: 'owner@example.test', scopes: ['profile'], createdAt: '', updatedAt: '', unionidStatus: 'none' }
beforeEach(() => { vi.clearAllMocks(); setLanguagePreference('zh-Hans') })
describe('UnionID permission lifecycle', () => {
  it('requires a reason then applies and publishes the returned status', async () => {
    mocks.apply.mockResolvedValue({ ...app, unionidStatus: 'pending' })
    const wrapper = mount(ApplicationPermissions, { props: { app }, global: { plugins: [ElementPlus] } })
    await wrapper.get('button').trigger('click')
    const submit = wrapper.findAll('button').find((button) => button.text() === '提交申请')!
    await submit.trigger('click')
    expect(mocks.apply).not.toHaveBeenCalled()
    await wrapper.get('textarea').setValue('统一网站与 App 的校园账号')
    await submit.trigger('click')
    await flushPromises()
    expect(mocks.apply).toHaveBeenCalledWith('web', '统一网站与 App 的校园账号')
    expect(wrapper.emitted('updated')?.[0]?.[0]).toEqual(expect.objectContaining({ unionidStatus: 'pending' }))
    wrapper.unmount()
  })
  it('shows review feedback and allows withdrawing a pending request', async () => {
    mocks.close.mockResolvedValue({ ...app, unionidStatus: 'none' })
    const wrapper = mount(ApplicationPermissions, { props: { app: { ...app, unionidStatus: 'pending', unionidReason: '账号互通', unionidReviewReason: '待补充说明' } }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('待补充说明')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(mocks.close).toHaveBeenCalledWith('web')
    expect(wrapper.emitted('updated')?.[0]?.[0]).toEqual(expect.objectContaining({ unionidStatus: 'none' }))
    wrapper.unmount()
  })
})
