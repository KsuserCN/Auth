import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { componentStubs } from '@/test/componentStubs'
import UnionidApplicationsView from './UnionidApplicationsView.vue'
const mocks = vi.hoisted(() => ({ list: vi.fn(), review: vi.fn(), verify: vi.fn() }))
vi.mock('@/api/console', () => ({ consoleApi: { unionidApplications: mocks.list, reviewUnionidApplication: mocks.review } }))
vi.mock('@/utils/consoleVerification', () => ({ withConsoleVerification: mocks.verify, VerificationCancelledError: class extends Error {} }))
const application = { id: 7, appId: 'mobile-app', appName: '校园 App', applicationType: 'mobile', ownerUserId: 12, ownerUsername: '开发者', status: 'pending', reason: '网站和 App 共享账号', createdAt: '2026-10-19T12:00:00' }
const render = () => mount(UnionidApplicationsView, { global: { stubs: componentStubs, directives: { loading: {} } } })
beforeEach(() => { vi.clearAllMocks(); mocks.list.mockResolvedValue({ items: [application], total: 1 }); mocks.review.mockResolvedValue({ ...application, status: 'approved' }); mocks.verify.mockImplementation((operation: () => Promise<unknown>) => operation()) })
describe('UnionID reviews', () => {
  it('loads pending requests and requires review feedback before approving with fresh verification', async () => {
    const wrapper = render()
    await flushPromises()
    expect(mocks.list).toHaveBeenCalledWith({ page: 0, size: 20, status: 'pending' })
    expect(wrapper.text()).toContain('移动应用')
    await wrapper.findAll('button').find((button) => button.text() === '审核')!.trigger('click')
    await wrapper.findAll('button').find((button) => button.text() === '批准')!.trigger('click')
    expect(mocks.review).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请填写审核意见')
    await wrapper.get('input[placeholder="填写批准、驳回或撤销的原因"]').setValue('同一主体账号互通，批准')
    await wrapper.findAll('button').find((button) => button.text() === '批准')!.trigger('click')
    await flushPromises()
    expect(mocks.verify).toHaveBeenCalledOnce()
    expect(mocks.review).toHaveBeenCalledWith(7, { decision: 'approve', reason: '同一主体账号互通，批准' })
    wrapper.unmount()
  })
  it('shows conflicts returned by a concurrent reviewer', async () => {
    mocks.review.mockRejectedValue(new Error('申请状态已改变'))
    const wrapper = render()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '审核')!.trigger('click')
    await wrapper.get('input[placeholder="填写批准、驳回或撤销的原因"]').setValue('无法确认用途')
    await wrapper.findAll('button').find((button) => button.text() === '驳回')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('申请状态已改变')
    wrapper.unmount()
  })
  it('distinguishes load failures from an empty queue', async () => {
    mocks.list.mockRejectedValue(new Error('网络错误'))
    const wrapper = render()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('网络错误')
    wrapper.unmount()
  })
})
