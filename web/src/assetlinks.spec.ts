import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const repoRoot = resolve(process.cwd(), '..')
const androidBuildGradle = readFileSync(
  resolve(repoRoot, 'mobile/android/app/build.gradle.kts'),
  'utf8',
)
const assetLinks = JSON.parse(
  readFileSync(resolve(repoRoot, 'web/public/.well-known/assetlinks.json'), 'utf8'),
)
const passkeyTarget = assetLinks.find(
  (statement: any) =>
    statement.target?.namespace === 'android_app' &&
    statement.relation?.includes('delegate_permission/common.get_login_creds'),
)?.target

describe('Android Digital Asset Links', () => {
  it('matches the Android release package name used by Passkey', () => {
    const applicationId = androidBuildGradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1]

    expect(applicationId).toBe('cn.ksuser.auth')
    expect(passkeyTarget).toEqual(
      expect.objectContaining({
        namespace: 'android_app',
        package_name: applicationId,
      }),
    )
  })

  it('contains the Android apk-key-hash certificates used by current builds', () => {
    expect(passkeyTarget.sha256_cert_fingerprints).toContain(
      'D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C2:01:66:2C:FC:92:83:F0:70:B4:FA:A7:10',
    )
    expect(passkeyTarget.sha256_cert_fingerprints).toContain(
      'D8:FE:BE:29:A8:8C:67:97:FD:EA:B6:42:6F:C2:BC:A5:EF:B0:76:C9:41:66:2C:FC:92:83:F0:70:B4:FA:A7:10',
    )
  })
})
