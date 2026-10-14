import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  initApplicationQr,
  pollApplicationQr,
  exchangeApplicationQr,
  cancelApplicationQr,
  finishApplicationQrWebLogin,
  applicationQrScopeLabel,
} from './applicationQr'
import { setLanguagePreference } from '@/i18n'
import { ksuserIosAppStoreUrl } from './appStore'

const mocks = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn(), exchange: vi.fn(), login: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { post: mocks.post, get: mocks.get } }))
vi.mock('@/api/auth', () => ({ exchangeSessionTransfer: mocks.exchange }))
vi.mock('@/utils/desktopBridge', () => ({ finalizeWebLogin: mocks.login }))
const authorization = {
  clientId: 'app',
  redirectUri: 'https://app.example/callback',
  responseType: 'code',
  scope: 'openid email',
  state: 'state',
  nonce: 'nonce',
  codeChallenge: 'original-PKCE',
  codeChallengeMethod: 'S256' as const,
}
beforeEach(() => {
  vi.clearAllMocks()
  setLanguagePreference('zh-Hans')
})
describe('shared application QR receipt', () => {
  it.each(['oauth', 'official'] as const)(
    'sends the immutable %s request and keeps the browser secret separate from QR text',
    async (mode) => {
      mocks.post.mockResolvedValue({
        data: {
          ticket: 'ticket',
          secret: 'private-secret',
          qrText: 'KSUSER-AUTH-QR:phone',
          expiresInSeconds: 120,
          application: {
            appName: 'Calendar',
            requestedScopes: ['openid', 'email'],
            consoleApplication: false,
            redirectUri: authorization.redirectUri,
          },
        },
      })
      const result = await initApplicationQr(mode, authorization)
      expect(mocks.post).toHaveBeenCalledWith('/auth/mobile-authorization/qr/create', {
        mode,
        returnOrigin: window.location.origin,
        authorization,
      })
      expect(result.challenge.pollToken).toBe('private-secret')
      expect(result.challenge.qrText).not.toContain('private-secret')
      expect(result.application.clientName).toBe('Calendar')
    },
  )
  it('polls, consumes and cancels only with the original browser receipt', async () => {
    mocks.get.mockResolvedValue({ data: { status: 'pending' } })
    mocks.post.mockResolvedValue({
      data: { status: 'completed', redirectUrl: authorization.redirectUri },
    })
    const receipt = { challengeId: 'ticket', pollToken: 'private-secret' }
    await pollApplicationQr(receipt)
    await exchangeApplicationQr(receipt)
    await cancelApplicationQr(receipt)
    expect(mocks.get).toHaveBeenCalledWith('/auth/mobile-authorization/qr/status', {
      params: { ticket: 'ticket', secret: 'private-secret' },
    })
    expect(mocks.post).toHaveBeenCalledWith('/auth/mobile-authorization/consume', {
      ticket: 'ticket',
      secret: 'private-secret',
    })
    expect(mocks.post).toHaveBeenCalledWith('/auth/mobile-authorization/cancel', {
      ticket: 'ticket',
      secret: 'private-secret',
    })
  })
  it('does not alter Passport by default and logs in only with a phone-issued capability', async () => {
    await finishApplicationQrWebLogin({
      status: 'completed',
      redirectUrl: authorization.redirectUri,
    })
    expect(mocks.exchange).not.toHaveBeenCalled()
    expect(mocks.login).not.toHaveBeenCalled()
    mocks.exchange.mockResolvedValue({
      accessToken: 'web-token',
      user: { uuid: 'current-phone-user' },
    })
    await finishApplicationQrWebLogin({
      status: 'completed',
      webLoginTransferCode: 'explicit-phone-choice',
    })
    expect(mocks.exchange).toHaveBeenCalledWith('explicit-phone-choice', 'web')
    expect(mocks.login).toHaveBeenCalledWith({
      accessToken: 'web-token',
      user: { uuid: 'current-phone-user' },
      syncDesktop: false,
    })
  })
  it('selects the App Store region from the active language and labels third-party identity accurately', () => {
    expect(ksuserIosAppStoreUrl.value).toContain('apps.apple.com/cn/')
    expect(applicationQrScopeLabel('oauth', 'openid')).toBe('确认账号身份')
    setLanguagePreference('en')
    expect(ksuserIosAppStoreUrl.value).toContain('apps.apple.com/us/')
    expect(applicationQrScopeLabel('oauth', 'email')).toBe('Email address')
    setLanguagePreference('zh-Hans')
  })
})
