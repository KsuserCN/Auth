import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppleChallenge, submitAppleCredential, verifyAppleSensitive } from '@/api/auth'
import { signInWithApple, verifySensitiveWithApple } from './appleSignIn'

vi.mock('@/api/auth', () => ({
  createAppleChallenge: vi.fn(),
  submitAppleCredential: vi.fn(),
  verifyAppleSensitive: vi.fn(),
}))

describe('Apple web sign in', () => {
  const init = vi.fn()
  const signIn = vi.fn()

  beforeEach(() => {
    vi.stubEnv('VITE_APPLE_CLIENT_ID', 'com.example.web')
    vi.stubEnv('VITE_APPLE_REDIRECT_URI', 'https://example.com/oauth/apple/callback')
    vi.stubGlobal('window', { AppleID: { auth: { init, signIn } } })
    vi.mocked(createAppleChallenge).mockResolvedValue({
      challengeId: 'challenge', nonce: 'server-nonce', state: 'server-state', expiresInSeconds: 300,
    })
    vi.mocked(submitAppleCredential).mockResolvedValue({ accessToken: 'local-token' })
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
    vi.unstubAllEnvs()
  })

  it('passes the server nonce and state to Apple and submits the returned credentials', async () => {
    signIn.mockResolvedValue({
      authorization: { code: 'one-time-code', id_token: 'apple-jwt', state: 'server-state' },
      user: { name: { firstName: 'Ada', lastName: 'Lovelace' } },
    })

    await signInWithApple('login')

    expect(init).toHaveBeenCalledWith(expect.objectContaining({
      clientId: 'com.example.web', nonce: 'server-nonce', state: 'server-state', usePopup: true,
    }))
    expect(submitAppleCredential).toHaveBeenCalledWith({
      challengeId: 'challenge', authorizationCode: 'one-time-code', identityToken: 'apple-jwt',
      state: 'server-state', givenName: 'Ada', familyName: 'Lovelace',
    })
  })

  it('rejects a mismatched Apple state before sending credentials to the API', async () => {
    signIn.mockResolvedValue({
      authorization: { code: 'one-time-code', id_token: 'apple-jwt', state: 'wrong-state' },
    })

    await expect(signInWithApple('bind')).rejects.toThrow('状态校验失败')
    expect(submitAppleCredential).not.toHaveBeenCalled()
  })

  it('uses a sensitive challenge and the sensitive verification endpoint', async () => {
    signIn.mockResolvedValue({
      authorization: { code: 'one-time-code', id_token: 'apple-jwt', state: 'server-state' },
    })

    await verifySensitiveWithApple()

    expect(createAppleChallenge).toHaveBeenCalledWith('sensitive')
    expect(verifyAppleSensitive).toHaveBeenCalledWith(expect.objectContaining({
      challengeId: 'challenge', authorizationCode: 'one-time-code', identityToken: 'apple-jwt',
    }))
    expect(submitAppleCredential).not.toHaveBeenCalled()
  })
})
