import { beforeEach, describe, expect, it, vi } from 'vitest'
import { applyApplicationUnionid, closeApplicationUnionid, verifyApplicationPlatform, approveOAuth2Authorize, createOAuth2App, getOAuth2AuthorizeContext } from './oauth2'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: mocks }))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.get.mockResolvedValue({ data: {} })
  mocks.post.mockResolvedValue({ data: {} })
  mocks.delete.mockResolvedValue({ data: {} })
})

describe('public OAuth clients', () => {
  it('preserves S256 PKCE in both context lookup and approval', async () => {
    const params = { clientId: 'native-client', redirectUri: 'https://native.example/callback', responseType: 'code' as const, scope: 'profile', state: 'original-state', codeChallenge: 'a'.repeat(43), codeChallengeMethod: 'S256' as const }
    await getOAuth2AuthorizeContext(params)
    await approveOAuth2Authorize({ ...params, state: 'original-state' })
    expect(mocks.get).toHaveBeenCalledWith('/oauth2/authorize/context', { params: { client_id: params.clientId, redirect_uri: params.redirectUri, response_type: 'code', scope: 'profile', state: 'original-state', code_challenge: params.codeChallenge, code_challenge_method: 'S256' } })
    expect(mocks.post).toHaveBeenCalledWith('/oauth2/authorize/approve', { ...params, state: 'original-state' })
  })

  it('creates a public client without expecting an application secret', async () => {
    const input = { applicationType: 'mobile' as const, appName: 'Native app', platforms: { ios: { bundleId: 'com.example.app', appIdPrefix: 'TEAMID1234', universalLink: 'https://native.example/callback' } }, contactInfo: 'support@example.com', scopes: ['profile' as const] }
    mocks.post.mockResolvedValue({ data: { ...input, appId: 'native', appSecret: null } })
    expect((await createOAuth2App(input)).appSecret).toBeNull()
    expect(mocks.post).toHaveBeenCalledWith('/oauth2/apps', input)
  })

  it('encodes application IDs for platform verification and the UnionID lifecycle', async () => {
    await verifyApplicationPlatform('app/123', 'ios')
    expect(mocks.post).toHaveBeenCalledWith('/oauth2/apps/app%2F123/platforms/ios/verify')
    await applyApplicationUnionid('app/123', 'Share account identities')
    expect(mocks.post).toHaveBeenCalledWith('/oauth2/apps/app%2F123/unionid/apply', { reason: 'Share account identities' })
    await closeApplicationUnionid('app/123')
    expect(mocks.delete).toHaveBeenCalledWith('/oauth2/apps/app%2F123/unionid')
  })
})
