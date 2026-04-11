import { getUserDetailsInfo, type UserDetails } from '@/api/auth'
import { getOAuth2Authorizations, type OAuth2AuthorizedApp } from '@/api/oauth2'
import { getSSOAuthorizations, type SSOAuthorizedClient } from '@/api/sso'

export interface PrivacyExportMeta {
  exportedAt: string
  version: string
  schemaVersion: number
  timezone: string
}

export interface PrivacyExportAuthorizations {
  oauth2: OAuth2AuthorizedApp[]
  sso: SSOAuthorizedClient[]
}

export interface PrivacyExportPayload {
  meta: PrivacyExportMeta
  profile: UserDetails
  authorizations: PrivacyExportAuthorizations
}

const PRIVACY_EXPORT_VERSION = '1.0.0'
const PRIVACY_EXPORT_SCHEMA_VERSION = 1

export const fetchPrivacyExportData = async (): Promise<PrivacyExportPayload> => {
  try {
    const [profile, oauth2, sso] = await Promise.all([
      getUserDetailsInfo(),
      getOAuth2Authorizations(),
      getSSOAuthorizations(),
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
        sso,
      },
    }
  } catch (error) {
    const fallbackMessage = '拉取导出数据失败，请稍后重试'
    if (error instanceof Error && error.message) {
      throw new Error(error.message)
    }
    throw new Error(fallbackMessage)
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
