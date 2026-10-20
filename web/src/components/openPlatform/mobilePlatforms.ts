import type { ApplicationPlatforms } from '@/api/oauth2'
import { LocalizedError } from '@/i18n'

export interface MobilePlatformDraft {
  iosEnabled: boolean
  androidEnabled: boolean
  bundleId: string
  appIdPrefix: string
  universalLink: string
  urlScheme: string
  appStoreId: string
  packageName: string
  fingerprints: string
  appLink: string
  downloadUrl: string
}

export function platformDraft(platforms?: ApplicationPlatforms): MobilePlatformDraft {
  return {
    iosEnabled: !!platforms?.ios, androidEnabled: !!platforms?.android,
    bundleId: platforms?.ios?.bundleId ?? '', appIdPrefix: platforms?.ios?.appIdPrefix ?? '',
    universalLink: platforms?.ios?.universalLink ?? '', urlScheme: platforms?.ios?.urlScheme ?? '',
    appStoreId: platforms?.ios?.appStoreId ?? '', packageName: platforms?.android?.packageName ?? '',
    fingerprints: platforms?.android?.sha256CertFingerprints.join('\n') ?? '',
    appLink: platforms?.android?.appLink ?? '', downloadUrl: platforms?.android?.downloadUrl ?? '',
  }
}

function callback(value: string): string {
  try {
    const url = new URL(value.trim())
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname === '/' || (url.port && url.port !== '443')) throw new Error()
    return value.trim()
  } catch {
    throw new LocalizedError('ui.openPlatform.invalidHttpsCallback')
  }
}

export function buildPlatforms(draft: MobilePlatformDraft): ApplicationPlatforms {
  if (!draft.iosEnabled && !draft.androidEnabled) throw new LocalizedError('ui.openPlatform.platformRequired')
  const platforms: ApplicationPlatforms = {}
  if (draft.iosEnabled) {
    if (!draft.bundleId.trim() || !draft.appIdPrefix.trim() || !draft.universalLink.trim()) throw new LocalizedError('ui.openPlatform.platformFieldsRequired')
    platforms.ios = { bundleId: draft.bundleId.trim(), appIdPrefix: draft.appIdPrefix.trim(), universalLink: callback(draft.universalLink), urlScheme: draft.urlScheme.trim(), appStoreId: draft.appStoreId.trim() }
  }
  if (draft.androidEnabled) {
    if (!draft.packageName.trim() || !draft.fingerprints.trim() || !draft.appLink.trim()) throw new LocalizedError('ui.openPlatform.platformFieldsRequired')
    const fingerprints = draft.fingerprints.split(/[\n,;]/).map((value) => value.trim().replace(/:/g, '').toUpperCase()).filter(Boolean)
    if (!fingerprints.length || fingerprints.some((value) => !/^[A-F0-9]{64}$/.test(value))) throw new LocalizedError('ui.openPlatform.invalidFingerprint')
    platforms.android = { packageName: draft.packageName.trim(), sha256CertFingerprints: [...new Set(fingerprints)], appLink: callback(draft.appLink), downloadUrl: draft.downloadUrl.trim() }
  }
  return platforms
}
