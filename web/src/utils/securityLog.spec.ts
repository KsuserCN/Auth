import { describe, it, expect, afterEach } from 'vitest'
import { languagePreference } from '@/i18n'
import type { SensitiveLogItem } from '@/api/auth'
import {
  parseLogMethods,
  logMethodLabels,
  logTitle,
  logReason,
  logActionLabel,
} from './securityLog'

const log = (values: Partial<SensitiveLogItem>) =>
  ({ operationType: 'LOGIN', ...values }) as SensitiveLogItem
afterEach(() => {
  languagePreference.value = 'system'
})
describe('security activity presentation', () => {
  it('reads old and new formats with aliases and deduplication', () => {
    expect(parseLogMethods('["email_mfa", weixin, MFA, null]')).toEqual([
      'EMAIL_CODE',
      'MFA',
      'WECHAT',
    ])
    expect(parseLogMethods('PASSWORD_MFA')).toEqual(['PASSWORD', 'MFA'])
    expect(parseLogMethods('PASSWORD', ['PASSKEY_MFA', 'PASSKEY'])).toEqual(['PASSKEY', 'MFA'])
    expect(parseLogMethods('null')).toEqual([])
  })
  it('translates retained entries when the selected app language changes', () => {
    const entry = log({ loginMethod: 'PASSWORD_MFA', loginMethods: [] })
    languagePreference.value = 'zh-Hans'
    expect(logMethodLabels(entry)).toEqual(['密码', '二步验证'])
    expect(logActionLabel('FREEZE')).toBe('会话已冻结')
    expect(logTitle(log({ operationType: 'CHANGE_EMAIL' }))).toBe('修改邮箱')
    expect(logReason('Invalid password')).toBe('密码不正确')
    languagePreference.value = 'en'
    expect(logMethodLabels(entry)).toEqual(['Password', 'Two-step verification'])
    expect(logReason('invalid_password')).toBe('Incorrect password')
  })
  it('describes system actions and never calls request sources login methods', () => {
    languagePreference.value = 'zh-Hans'
    const event = log({
      operationType: 'ADAPTIVE_POLICY',
      actionTaken: 'STEP_UP',
      loginMethod: 'adaptive-auth-status',
    })
    expect(logTitle(event)).toBe('需要再次验证身份')
    expect(logMethodLabels(event)).toEqual([])
    expect(logMethodLabels(log({ loginMethod: 'FUTURE_PROVIDER' }))).toEqual(['其他验证方式'])
    expect(logReason('risk=45; 网络环境已变化')).toBe('网络环境已变化')
    expect(logMethodLabels(log({ operationType: 'BIND_OAUTH', loginMethod: 'APPLE' }))).toEqual([
      'Apple',
    ])
  })
})
