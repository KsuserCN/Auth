export const mobileDownloadPageUrl = 'https://auth.ksuser.cn/downloads'
export const androidManifestPath = '/downloads/latest/android.json'

export interface AndroidDownloadRelease {
  versionCode: number
  versionName: string
  apkUrl: string
  sha256: string
  releaseNotes: string
}

export function parseAndroidRelease(value: unknown): AndroidDownloadRelease {
  if (!value || typeof value !== 'object') throw new Error('Invalid Android release')
  const release = value as Record<string, unknown>
  if (
    !Number.isSafeInteger(release.versionCode) ||
    Number(release.versionCode) <= 0 ||
    typeof release.versionName !== 'string' ||
    !release.versionName.trim() ||
    typeof release.apkUrl !== 'string' ||
    typeof release.sha256 !== 'string' ||
    !/^[a-f\d]{64}$/i.test(release.sha256)
  )
    throw new Error('Invalid Android release')
  const url = new URL(release.apkUrl)
  if (url.protocol !== 'https:' || url.username || url.password || url.hash) {
    throw new Error('Invalid Android download URL')
  }
  return {
    versionCode: Number(release.versionCode),
    versionName: release.versionName.trim(),
    apkUrl: url.href,
    sha256: release.sha256.toLowerCase(),
    releaseNotes: typeof release.releaseNotes === 'string' ? release.releaseNotes.trim() : '',
  }
}

export async function fetchAndroidRelease(signal: AbortSignal): Promise<AndroidDownloadRelease> {
  const response = await fetch(androidManifestPath, {
    headers: { Accept: 'application/json' },
    credentials: 'omit',
    cache: 'no-store',
    signal,
  })
  if (
    !response.ok ||
    !response.headers.get('content-type')?.toLowerCase().includes('application/json')
  ) {
    throw new Error('Android release is unavailable')
  }
  return parseAndroidRelease(await response.json())
}
