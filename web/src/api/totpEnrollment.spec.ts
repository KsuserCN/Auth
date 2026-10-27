import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getTotpEnrollmentPasskeyOptions,
  getTotpRegistrationOptions,
  verifyTotpEnrollment,
  verifyTotpEnrollmentPasskey,
  verifyTotpRegistration,
} from './auth'

const mocks = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: mocks }))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.post.mockResolvedValue({ data: { proofId: 'enrollment-proof', expiresInSeconds: 300 } })
})

describe('TOTP enrollment API contract', () => {
  it('verifies existing credentials with the enrollment purpose', async () => {
    expect((await verifyTotpEnrollment({ method: 'password', password: 'current-password' })).proofId)
      .toBe('enrollment-proof')
    expect(mocks.post).toHaveBeenCalledWith('/auth/totp/enrollment/verify', {
      method: 'password', password: 'current-password',
    })
    await verifyTotpEnrollment({ method: 'email-code', code: '123456' })
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/totp/enrollment/verify', {
      method: 'email-code', code: '123456',
    })
  })

  it('uses the enrollment-specific Passkey challenge and verification endpoints', async () => {
    const credential = { credentialRawId: 'id', clientDataJSON: 'client', authenticatorData: 'auth', signature: 'signature' }
    await getTotpEnrollmentPasskeyOptions()
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/totp/enrollment/passkey/options', {})
    await verifyTotpEnrollmentPasskey('passkey-challenge', credential)
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/totp/enrollment/passkey/verify', {
      passkeyChallengeId: 'passkey-challenge', credential,
    })
  })

  it('requires a proof to initialize and confirms only the server enrollment ID and OTP', async () => {
    await getTotpRegistrationOptions('enrollment-proof')
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/totp/registration-options', { proofId: 'enrollment-proof' })
    await verifyTotpRegistration({ enrollmentId: 'enrollment-one', code: '123456' })
    expect(mocks.post).toHaveBeenLastCalledWith('/auth/totp/registration-verify', {
      enrollmentId: 'enrollment-one', code: '123456',
    })
    expect(mocks.post.mock.lastCall?.[1]).not.toHaveProperty('recoveryCodes')
  })
})
