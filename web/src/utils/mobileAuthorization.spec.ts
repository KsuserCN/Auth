import { afterEach, describe, expect, it, vi } from 'vitest'
import { isIOSAuthorizationSupported, readMobileAuthorizationReturn } from './mobileAuthorization'
vi.mock('@/utils/request', () => ({ default: {} }))
afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
describe('iOS authorization handoff', () => {
  it('detects iPhone and desktop-mode iPad, leaving Android on the web', () => {
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('iPhone Safari')
    expect(isIOSAuthorizationSupported()).toBe(true)
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Macintosh Safari')
    vi.spyOn(navigator, 'platform', 'get').mockReturnValue('MacIntel')
    vi.stubGlobal('navigator', {
      userAgent: 'Macintosh Safari',
      platform: 'MacIntel',
      maxTouchPoints: 5,
    })
    expect(isIOSAuthorizationSupported()).toBe(true)
    vi.stubGlobal('navigator', {
      userAgent: 'Android Chrome',
      platform: 'Linux',
      maxTouchPoints: 5,
    })
    expect(isIOSAuthorizationSupported()).toBe(false)
  })
  it('requires both the ticket and browser secret on return', () => {
    const ticket = 'a'.repeat(32),
      secret = 'b'.repeat(32)
    expect(readMobileAuthorizationReturn(`#ticket=${ticket}&secret=${secret}`)).toEqual({
      ticket,
      secret,
    })
    expect(readMobileAuthorizationReturn(`#ticket=${ticket}`)).toBeNull()
    expect(readMobileAuthorizationReturn('#ticket=invalid&secret=invalid')).toBeNull()
  })
})
