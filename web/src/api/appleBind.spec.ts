import { beforeEach, describe, expect, it, vi } from 'vitest'
import { bindPendingApple } from '@/api/auth'
import request from '@/utils/request'

vi.mock('@/utils/request', () => ({
  default: { post: vi.fn() },
}))

describe('Apple pending binding', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('completes only when the API confirms the Apple account was bound', async () => {
    vi.mocked(request.post).mockResolvedValue({
      code: 200, msg: 'Apple 绑定成功', data: { bound: true },
    })

    await expect(bindPendingApple('apple-ticket')).resolves.toBeUndefined()
    expect(request.post).toHaveBeenCalledWith('/oauth/apple/bind-pending', {
      oauthBindToken: 'apple-ticket',
    })
  })

  it('reports expired sensitive verification instead of treating HTTP 202 as binding success', async () => {
    vi.mocked(request.post).mockResolvedValue({
      code: 202, msg: '需要完成敏感操作验证', data: { needVerification: true },
    })

    await expect(bindPendingApple('apple-ticket')).rejects.toThrow('需要完成敏感操作验证')
  })

  it('rejects an incomplete success response', async () => {
    vi.mocked(request.post).mockResolvedValue({
      code: 200, msg: '获取成功', data: { bound: false },
    })

    await expect(bindPendingApple('apple-ticket')).rejects.toThrow('Apple 绑定未完成，请重新授权')
  })

  it('preserves authentication failures from the request layer', async () => {
    vi.mocked(request.post).mockRejectedValue(new Error('未登录或Token已过期'))

    await expect(bindPendingApple('apple-ticket')).rejects.toThrow('未登录或Token已过期')
  })
})
