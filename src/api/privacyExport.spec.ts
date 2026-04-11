import { describe, expect, it, vi, beforeEach } from 'vitest'
import {
  buildPrivacyExportFilename,
  fetchPrivacyExportData,
  type PrivacyExportPayload,
} from '@/api/privacyExport'

vi.mock('@/api/auth', () => ({
  getUserDetailsInfo: vi.fn(),
}))

vi.mock('@/api/oauth2', () => ({
  getOAuth2Authorizations: vi.fn(),
}))

vi.mock('@/api/sso', () => ({
  getSSOAuthorizations: vi.fn(),
}))

import { getUserDetailsInfo } from '@/api/auth'
import { getOAuth2Authorizations } from '@/api/oauth2'
import { getSSOAuthorizations } from '@/api/sso'

describe('privacy export', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('builds filename with yyyy-mm-dd format', () => {
    const date = new Date('2026-04-11T10:20:30.000Z')
    expect(buildPrivacyExportFilename(date)).toBe('ksuser-data-export-2026-04-11.json')
  })

  it('fetches and assembles payload with empty authorizations', async () => {
    vi.mocked(getUserDetailsInfo).mockResolvedValue({
      uuid: 'u-1',
      username: 'test',
      email: 'test@example.com',
      settings: {
        mfaEnabled: false,
        detectUnusualLogin: true,
        notifySensitiveActionEmail: true,
        subscribeNewsEmail: false,
      },
    })
    vi.mocked(getOAuth2Authorizations).mockResolvedValue([])
    vi.mocked(getSSOAuthorizations).mockResolvedValue([])

    const payload = await fetchPrivacyExportData()

    expect(payload.profile.uuid).toBe('u-1')
    expect(payload.authorizations.oauth2).toEqual([])
    expect(payload.authorizations.sso).toEqual([])
    expect(payload.meta.version).toBe('1.0.0')
    expect(payload.meta.schemaVersion).toBe(1)
    expect(payload.meta.exportedAt).toMatch(/T/)
    expect(payload.meta.timezone.length).toBeGreaterThan(0)
  })

  it('throws readable message when fetch fails', async () => {
    vi.mocked(getUserDetailsInfo).mockRejectedValue(new Error('boom'))
    vi.mocked(getOAuth2Authorizations).mockResolvedValue([])
    vi.mocked(getSSOAuthorizations).mockResolvedValue([])

    await expect(fetchPrivacyExportData()).rejects.toThrow('boom')
  })

  it('matches payload type shape', () => {
    const payload: PrivacyExportPayload = {
      meta: {
        exportedAt: '2026-04-11T00:00:00.000Z',
        version: '1.0.0',
        schemaVersion: 1,
        timezone: 'Asia/Shanghai',
      },
      profile: {
        uuid: 'u-1',
        username: 'tester',
        email: 'tester@example.com',
      },
      authorizations: {
        oauth2: [],
        sso: [],
      },
    }

    expect(payload.authorizations.oauth2.length).toBe(0)
  })
})
