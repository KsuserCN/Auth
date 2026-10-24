import { afterEach, describe, expect, it, vi } from 'vitest'
import { androidManifestPath, fetchAndroidRelease, parseAndroidRelease } from './appDownloads'

const release = {
  versionCode: 14,
  versionName: '1.0.0.14',
  apkUrl: 'https://auth.ksuser.cn/downloads/latest/android.apk',
  sha256: 'a'.repeat(64),
  releaseNotes: 'Updated app',
}
afterEach(() => vi.unstubAllGlobals())

describe('public Android downloads', () => {
  it('loads version data without browser credentials and bypasses cached manifests', async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify(release), { headers: { 'Content-Type': 'application/json' } }),
      )
    vi.stubGlobal('fetch', fetcher)
    const signal = new AbortController().signal
    expect(await fetchAndroidRelease(signal)).toEqual(release)
    expect(fetcher).toHaveBeenCalledWith(androidManifestPath, {
      credentials: 'omit',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal,
    })
  })

  it.each([
    [200, 'text/html', '<html>SPA fallback</html>'],
    [404, 'application/json', '{}'],
  ])(
    'rejects HTTP %s with %s instead of presenting a broken APK link',
    async (status, contentType, body) => {
      vi.stubGlobal(
        'fetch',
        vi
          .fn()
          .mockResolvedValue(
            new Response(body, { status, headers: { 'Content-Type': contentType } }),
          ),
      )
      await expect(fetchAndroidRelease(new AbortController().signal)).rejects.toThrow()
    },
  )

  it.each([
    { versionCode: 0 },
    { versionCode: '14' },
    { versionName: '' },
    { sha256: 'bad-hash' },
    { apkUrl: 'javascript:alert(1)' },
    { apkUrl: 'http://example.com/app.apk' },
    { apkUrl: 'https://user:password@example.com/app.apk' },
    { apkUrl: 'https://example.com/app.apk#fragment' },
  ])('rejects invalid release metadata %j', (change) => {
    expect(() => parseAndroidRelease({ ...release, ...change })).toThrow()
  })
})
