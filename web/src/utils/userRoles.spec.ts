import { describe, expect, it } from 'vitest'
import { canCreateApplications, isSystemAdmin, verificationDisplayType } from './userRoles'

describe('system roles and developer verification', () => {
  it('uses the current system role instead of the legacy administrator badge', () => {
    const user = { systemRole: 'USER' as const, verificationType: 'admin' as const, developerVerificationType: 'none' as const }
    expect(isSystemAdmin(user)).toBe(false)
    expect(canCreateApplications(user)).toBe(false)
    expect(verificationDisplayType(user)).toBe('none')
  })

  it('keeps a developer certification after administrator privileges are revoked', () => {
    const user = { systemRole: 'ADMIN' as const, developerVerificationType: 'enterprise' as const }
    expect(isSystemAdmin(user)).toBe(true)
    expect(verificationDisplayType(user)).toBe('admin')
    expect(canCreateApplications({ ...user, systemRole: 'USER' })).toBe(true)
    expect(verificationDisplayType({ ...user, systemRole: 'USER' })).toBe('enterprise')
  })
})
