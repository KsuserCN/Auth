import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getOfficialAuthorizeContext,
  approveOfficialAuthorize,
  getOfficialAuthorizations,
  revokeOfficialAuthorization,
} from './official'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: mocks }))
const params = {
  clientId: 'ksuser_console',
  redirectUri: 'https://console.ksuser.cn/auth/callback',
  responseType: 'code',
  scope: 'openid profile email',
  state: 'state',
  nonce: 'nonce',
  codeChallenge: 'a'.repeat(43),
  codeChallengeMethod: 'S256' as const,
}
beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ data: {} })
  mocks.post.mockResolvedValue({ data: { redirectUrl: params.redirectUri } })
})
describe('official authorization API', () => {
  it('preserves the full PKCE and OIDC request in context and approval', async () => {
    await getOfficialAuthorizeContext(params)
    await approveOfficialAuthorize({ ...params, approved: true })
    expect(mocks.get).toHaveBeenCalledWith('/official/authorize/context', {
      params: {
        client_id: params.clientId,
        redirect_uri: params.redirectUri,
        response_type: 'code',
        scope: params.scope,
        state: 'state',
        nonce: 'nonce',
        code_challenge: params.codeChallenge,
        code_challenge_method: 'S256',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/official/authorize/approve', {
      ...params,
      approved: true,
    })
  })
  it('sends denial to the server so the validated callback is used', async () => {
    await approveOfficialAuthorize({ ...params, approved: false })
    expect(mocks.post).toHaveBeenCalledWith('/official/authorize/approve', {
      ...params,
      approved: false,
    })
  })
  it('reads and revokes grants from the official application namespace', async () => {
    await getOfficialAuthorizations()
    await revokeOfficialAuthorization('client/with-id')
    expect(mocks.get).toHaveBeenCalledWith('/official/authorizations')
    expect(mocks.delete).toHaveBeenCalledWith('/official/authorizations/client%2Fwith-id')
  })
})
