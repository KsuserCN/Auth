import { API_RESULT_MESSAGES } from '../generated/GeneratedAPIResultCodes';
import { SECURITY_LOG_MESSAGES } from '../generated/GeneratedSecurityLogCatalog';
import { SensitiveLog } from './Models';
import { parseUrl } from './Cookies';
export { dateText, dateGroup } from './Models';

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
    case 'real_name': return '真实姓名';
    case 'gender': return '性别';
    case 'birth_date': return '生日';
    case 'region': return '地区';
    case 'bio': return '个人简介';
    default: return scope;
  }
}
export function scopeDescription(scope: string): string {
  switch (scope) {
    case 'openid': return '使用唯一账号标识识别你的身份';
    case 'profile': return '读取用户名、头像等基本个人资料';
    case 'email': return '读取你绑定的邮箱地址';
    case 'offline_access': return '在你不使用应用时，仍可在授权期限内访问已同意的资料';
    case 'real_name': return '读取你填写的真实姓名';
    case 'gender': return '读取你填写的性别';
    case 'birth_date': return '读取你填写的出生日期';
    case 'region': return '读取你填写的所在地区';
    case 'bio': return '读取你的个人简介';
    default: return '该应用请求的其他权限：' + scope;
  }
}
export function riskTitle(level: string | number | undefined): string {
  if (level === undefined) { return '未知'; }
  const value = typeof level === 'number' ? level >= 70 ? 'high' : level >= 40 ? 'medium' : 'low' : level.toLowerCase();
  return value === 'low' ? '低风险' : value === 'medium' ? '中风险' : value === 'high' ? '高风险' : '未知';
}
export function logAction(value: string | undefined): string {
  return value ? SECURITY_LOG_MESSAGES.get('action.' + normalize(value)) ?? '其他安全处理' : '—';
}
export function formatDuration(seconds: number | undefined): string {
  if (seconds === undefined || !Number.isFinite(seconds)) { return '未知'; }
  const value = Math.max(0, Math.floor(seconds));
  if (value < 60) { return value + ' 秒'; }
  if (value < 3600) { return Math.floor(value / 60) + ' 分 ' + (value % 60) + ' 秒'; }
  if (value < 86400) { return Math.floor(value / 3600) + ' 小时 ' + Math.floor(value % 3600 / 60) + ' 分'; }
  return Math.floor(value / 86400) + ' 天 ' + Math.floor(value % 86400 / 3600) + ' 小时';
}
export function grantModeTitle(mode: string | undefined): string {
  switch (mode) {
    case 'PERSISTENT': return '长期授权';
    case 'ONE_TIME': return '仅本次';
    case 'TIME_LIMITED': return '限时授权';
    default: return '授权方式未知';
  }
}
export const LOG_OPERATIONS: string[] = ['', 'LOGIN', 'REGISTER', 'SENSITIVE_VERIFY', 'ADAPTIVE_POLICY',
  'CHANGE_PASSWORD', 'CHANGE_EMAIL', 'ADD_PASSKEY', 'DELETE_PASSKEY', 'ENABLE_TOTP', 'DISABLE_TOTP',
  'BIND_OAUTH', 'UNBIND_OAUTH', 'UPDATE_PROFILE', 'DELETE_ACCOUNT'];
export function operationTitle(value: string): string {
  return value ? SECURITY_LOG_MESSAGES.get('operation.' + value) ?? '其他操作' : '全部操作';
}
export function logResult(log: SensitiveLog): string {
  return log.result.toUpperCase() === 'SUCCESS' ? log.operationType === 'ADAPTIVE_POLICY' ? '已执行' : '成功' : '失败';
}
export function verificationTitle(value: string | undefined): string {
  switch (value) {
    case 'personal': return '个人认证';
    case 'enterprise': return '企业认证';
    case 'admin': return '管理员';
    default: return '未认证';
  }
}
export function websiteOrigin(value: string | undefined): string {
  if (!value) { return '—'; }
  try { return parseUrl(value).origin; } catch (_) { return '未知网站'; }
}
