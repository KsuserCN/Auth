import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildMobileBridgeReturnUrl,
  createMobileBridgeLogin,
  isAndroidMobileBridgeSupported,
  isIOSMobileBridgeSupported,
  launchMobileBridgeApp,
} from './mobileBridge'

const mocks = vi.hoisted(() => ({ create: vi.fn(), assign: vi.fn() }))
vi.mock('@/api/auth', () => ({
  createMobileBridgeChallenge: mocks.create,
  getMobileBridgeStatus: vi.fn(),
}))
vi.mock('@/utils/request', () => ({ default: {} }))
const challengeId = 'a'.repeat(32)
const appLink = `https://auth.ksuser.cn/app/bridge-login?challengeId=${challengeId}&returnUrl=https%3A%2F%2Fauth.ksuser.cn%2Flogin`

const device = (userAgent: string, platform = 'iPhone', maxTouchPoints = 5) => {
  vi.stubGlobal('navigator', { userAgent, platform, maxTouchPoints })
}

beforeEach(() => {
  vi.clearAllMocks()
  device('iPhone Safari')
  vi.stubGlobal('window', {
    location: {
      href: 'https://auth.ksuser.cn/login?redirect=%2Fhome%2Fsecurity&mobileBridgeFallback=1&transferCode=old',
      assign: mocks.assign,
    },
    crypto: { randomUUID: () => 'browser-nonce' },
  })
})
afterEach(() => vi.unstubAllGlobals())

describe('mobile app login handoff', () => {
  it('supports iPhone and desktop-mode iPad without enabling desktop Macs', () => {
    expect(isIOSMobileBridgeSupported()).toBe(true)
    device('Macintosh Safari', 'MacIntel')
    expect(isIOSMobileBridgeSupported()).toBe(true)
    device('Macintosh Safari', 'MacIntel', 0)
    expect(isIOSMobileBridgeSupported()).toBe(false)
    device('Android Chrome', 'Linux')
    expect(isIOSMobileBridgeSupported()).toBe(false)
    expect(isAndroidMobileBridgeSupported()).toBe(true)
  })

  it('opens the registered iOS scheme with only the server challenge', () => {
    launchMobileBridgeApp(appLink)
    expect(mocks.assign).toHaveBeenCalledWith(
      `ksuserauth://bridge-login?challengeId=${challengeId}`,
    )
  })

  it('remembers Chrome for the return to the requesting browser', () => {
    device('iPhone CriOS/140.0')
    launchMobileBridgeApp(appLink)
    expect(mocks.assign).toHaveBeenCalledWith(
      `ksuserauth://bridge-login?challengeId=${challengeId}&returnBrowser=chrome`,
    )
  })

  it('rejects missing or malformed challenges before opening the app', () => {
    for (const link of [
      'https://auth.ksuser.cn/app/bridge-login',
      appLink.replace(challengeId, 'invalid'),
    ]) {
      expect(() => launchMobileBridgeApp(link)).toThrow('App 登录请求无效')
    }
    expect(mocks.assign).not.toHaveBeenCalled()
  })

  it('keeps the Android intent and HTTPS fallback', () => {
    device('Android Chrome', 'Linux')
    launchMobileBridgeApp(appLink)
    expect(mocks.assign).toHaveBeenCalledWith(
      expect.stringContaining(';package=cn.ksuser.auth;S.browser_fallback_url='),
    )
  })

  it('preserves the post-login destination while clearing stale handoff parameters', async () => {
    const returnURL = new URL(buildMobileBridgeReturnUrl(challengeId))
    expect(returnURL.searchParams.get('redirect')).toBe('/home/security')
    expect(returnURL.searchParams.get('mobileBridgeChallengeId')).toBe(challengeId)
    expect(returnURL.searchParams.has('mobileBridgeFallback')).toBe(false)
    expect(returnURL.searchParams.has('transferCode')).toBe(false)
    mocks.create.mockResolvedValue({ challengeId, appLink })
    await createMobileBridgeLogin()
    expect(mocks.create).toHaveBeenCalledWith(
      expect.stringContaining('mobileBridgeChallengeId=pending'),
      'browser-nonce',
    )
  })
})
