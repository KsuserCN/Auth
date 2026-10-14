import { LocalizedError } from '@/i18n'
import { getUserDetailsInfo, type UserDetails } from '@/api/auth'
import { getOAuth2Authorizations, type OAuth2AuthorizedApp } from '@/api/oauth2'
import { getOfficialAuthorizations, type OfficialAuthorization } from '@/api/official'

export interface PrivacyExportMeta {
  exportedAt: string
  version: string
  schemaVersion: number
  timezone: string
}

export interface PrivacyExportAuthorizations {
  oauth2: OAuth2AuthorizedApp[]
  official: OfficialAuthorization[]
}

export interface PrivacyExportPayload {
  meta: PrivacyExportMeta
  profile: UserDetails
  authorizations: PrivacyExportAuthorizations
}

const PRIVACY_EXPORT_VERSION = '2.0.0'
const PRIVACY_EXPORT_SCHEMA_VERSION = 2

export const fetchPrivacyExportData = async (): Promise<PrivacyExportPayload> => {
  try {
    const [profile, oauth2, official] = await Promise.all([
      getUserDetailsInfo(),
      getOAuth2Authorizations(),
      getOfficialAuthorizations(),
    ])

    return {
      meta: {
        exportedAt: new Date().toISOString(),
        version: PRIVACY_EXPORT_VERSION,
        schemaVersion: PRIVACY_EXPORT_SCHEMA_VERSION,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      },
      profile,
      authorizations: {
        oauth2,
        official,
      },
    }
  } catch (error) {
    if (error instanceof Error && error.message) {
      throw error
    }
    throw new LocalizedError('ui.privacyExport.message001')
  }
}

export const buildPrivacyExportFilename = (date = new Date()): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `ksuser-data-export-${year}-${month}-${day}.json`
}

export const downloadPrivacyExportFile = (payload: PrivacyExportPayload): void => {
  const serialized = JSON.stringify(payload, null, 2)
  const blob = new Blob([serialized], { type: 'application/json;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = buildPrivacyExportFilename()
  link.click()

  window.URL.revokeObjectURL(url)
}
