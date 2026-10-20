import { describe, expect, it } from 'vitest'
import { buildPlatforms, platformDraft } from './mobilePlatforms'

describe('mobile platform registration', () => {
  it('requires a platform and complete identity fields', () => {
    expect(() => buildPlatforms(platformDraft())).toThrow()
    expect(() => buildPlatforms({ ...platformDraft(), iosEnabled: true })).toThrow()
  })

  it.each(['http://example.test/callback', 'https://example.test/', 'https://example.test/callback?code=x', 'https://example.test/callback#fragment', 'https://user@example.test/callback'])('rejects callback %s', (universalLink) => {
    expect(() => buildPlatforms({ ...platformDraft(), iosEnabled: true, bundleId: 'com.example.app', appIdPrefix: 'TEAMID1234', universalLink })).toThrow()
  })

  it('registers both platforms and normalizes certificate fingerprints without copying verification', () => {
    const draft = platformDraft({ ios: { bundleId: 'com.example.app', appIdPrefix: 'TEAMID1234', universalLink: 'https://example.test/ios', verificationStatus: 'verified' } })
    draft.androidEnabled = true
    draft.packageName = 'com.example.app'
    draft.appLink = 'https://example.test/android'
    draft.fingerprints = `${'ab:'.repeat(31)}ab\n${'AB'.repeat(32)}`
    const platforms = buildPlatforms(draft)
    expect(platforms.ios?.verificationStatus).toBeUndefined()
    expect(platforms.android?.sha256CertFingerprints).toEqual(['AB'.repeat(32)])
  })

  it('rejects MD5 and malformed signatures', () => {
    expect(() => buildPlatforms({ ...platformDraft(), androidEnabled: true, packageName: 'com.example.app', appLink: 'https://example.test/android', fingerprints: 'AB'.repeat(16) })).toThrow()
  })
})
