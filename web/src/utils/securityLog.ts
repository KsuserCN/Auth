import type { SensitiveLogItem } from '@/api/auth'
import { effectiveLocale } from '@/i18n'
import { securityLogCatalog } from '@/generated/securityLogCatalog'
import { resolveAuditReason } from './apiResult'

export const securityLogText = (key: string): string =>
  securityLogCatalog[effectiveLocale.value]?.[key] ?? key
const normalize = (value: string) =>
  value.trim().replace(/["']/g, '').toUpperCase().replace(/[- ]/g, '_')

/** Both historical bracket/comma strings and normalized API arrays remain readable. */
export const parseLogMethods = (raw?: string | null, methods?: string[]): string[] => {
  const values = methods?.length ? methods : [raw ?? '']
  const tokens = values
    .flatMap((value) =>
      value
        .replace(/^\s*\[/, '')
        .replace(/\]\s*$/, '')
        .split(','),
    )
    .map(normalize)
    .filter((value) => value && value !== 'NULL')
    .flatMap((value) => (value.endsWith('_MFA') ? [value.slice(0, -4), 'MFA'] : [value]))
    .map((value) => (value === 'EMAIL' ? 'EMAIL_CODE' : value === 'WEIXIN' ? 'WECHAT' : value))
  return [...new Set(tokens)]
}

export const logOperationLabel = (operation: string): string =>
  securityLogCatalog[effectiveLocale.value]?.[`operation.${normalize(operation)}`] ??
  securityLogText('ui.unknownOperation')

export const logTitle = (log: SensitiveLogItem): string =>
  normalize(log.operationType) === 'ADAPTIVE_POLICY'
    ? logActionLabel(log.actionTaken)
    : logOperationLabel(log.operationType)

export const logActionLabel = (action?: string | null): string =>
  securityLogCatalog[effectiveLocale.value]?.[`action.${normalize(action ?? '')}`] ??
  securityLogText('ui.unknownAction')

export const logMethodLabels = (log: SensitiveLogItem): string[] => {
  if (
    !['LOGIN', 'SENSITIVE_VERIFY', 'BIND_OAUTH', 'UNBIND_OAUTH'].includes(
      normalize(log.operationType),
    )
  )
    return []
  return [
    ...new Set(
      parseLogMethods(log.loginMethod, log.loginMethods).map(
        (value) =>
          securityLogCatalog[effectiveLocale.value]?.[`method.${value}`] ??
          securityLogText('ui.unknownMethod'),
      ),
    ),
  ]
}

export const logReason = (raw: string | null): string | null => {
  if (!raw) return null
  const resolved = resolveAuditReason(raw)
  if (resolved !== raw) return resolved
  const historical = raw.replace(/^risk=\d+;?\s*/i, '').trim()
  if (!historical) return null
  const key = normalize(historical).toLowerCase()
  return securityLogCatalog[effectiveLocale.value]?.[`reason.${key}`] ?? historical
}

export const logRiskLabel = (score: number): string =>
  securityLogText(score >= 70 ? 'ui.high' : score >= 40 ? 'ui.medium' : 'ui.low')
