import { API_RESULT_MESSAGES } from '../generated/GeneratedAPIResultCodes';
import { SECURITY_LOG_MESSAGES } from '../generated/GeneratedSecurityLogCatalog';
import { SensitiveLog } from './Models';

function fallback(status: number): string {
  let code = 'REQUEST_FAILED';
  if (status >= 200 && status < 300) { code = 'REQUEST_SUCCESS'; }
  else if (status === 401) { code = 'UNAUTHENTICATED'; }
  else if (status === 403) { code = 'FORBIDDEN'; }
  else if (status === 404) { code = 'NOT_FOUND'; }
  else if (status === 408) { code = 'REQUEST_TIMEOUT'; }
  else if (status === 409) { code = 'REQUEST_CONFLICT'; }
  else if (status === 413) { code = 'REQUEST_UPLOAD_TOO_LARGE'; }
  else if (status === 429) { code = 'RATE_LIMITED'; }
  else if (status >= 500) { code = 'SERVER_ERROR'; }
  else if (status >= 400) { code = 'BAD_REQUEST'; }
  return API_RESULT_MESSAGES.get(code) ?? '请求失败，请重试';
}
export function resultMessage(code: string | undefined, params?: Object, status: number = 500): string {
  const template = code ? API_RESULT_MESSAGES.get(code) : undefined;
  if (!template) { return fallback(status); }
  const keys = template.match(/\{[A-Za-z][A-Za-z0-9_]*\}/g) ?? [];
  let result = template;
  const encoded = params ? JSON.stringify(params) : '{}';
  for (const placeholder of keys) {
    const key = placeholder.slice(1, -1);
    const expression = new RegExp('"' + key + '"\\s*:\\s*("(?:[^"\\\\]|\\\\.)*"|-?[0-9]+(?:\\.[0-9]+)?|true|false)(?=\\s*[,}])');
    const match = encoded.match(expression);
    if (!match) { return fallback(status); }
    const value = JSON.parse(match[1]) as string | number | boolean;
    result = result.split(placeholder).join(String(value));
  }
  return result;
}
function normalize(value: string): string { return value.trim().replace(/["']/g, '').toUpperCase().replace(/[- ]/g, '_'); }
export function logTitle(log: SensitiveLog): string {
  const key = normalize(log.operationType) === 'ADAPTIVE_POLICY' ? 'action.' + normalize(log.actionTaken ?? '') : 'operation.' + normalize(log.operationType);
  return SECURITY_LOG_MESSAGES.get(key) ?? '账号安全活动';
}
export function logMethods(log: SensitiveLog): string {
  const values = log.loginMethods?.length ? log.loginMethods : [log.loginMethod ?? ''];
  const labels: string[] = [];
  values.forEach((value: string) => {
    normalize(value.replace(/[\[\]]/g, '')).split(',').forEach((segment: string) => {
      const parts = segment.endsWith('_MFA') ? [segment.slice(0, -4), 'MFA'] : [segment];
      parts.forEach((part: string) => {
        const method = part.trim() === 'EMAIL' ? 'EMAIL_CODE' : part.trim();
        if (method && method !== 'NULL') {
          const label = SECURITY_LOG_MESSAGES.get('method.' + method) ?? '其他验证方式';
          if (!labels.includes(label)) { labels.push(label); }
        }
      });
    });
  });
  return labels.join(' · ');
}
export function logReason(value: string | undefined): string {
  if (!value) { return ''; }
  if (API_RESULT_MESSAGES.has(value)) { return resultMessage(value, undefined, 400); }
  const historical = value.replace(/^risk=\d+;?\s*/i, '').trim();
  return SECURITY_LOG_MESSAGES.get('reason.' + normalize(historical).toLowerCase()) ?? historical;
}
export function scopeTitle(scope: string): string {
  switch (scope) {
    case 'openid': return '账号标识';
    case 'profile': return '基本个人资料';
    case 'email': return '邮箱地址';
    case 'offline_access': return '在授权期限内保持访问';
    default: return scope;
  }
}
