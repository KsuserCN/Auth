import { UserFacingError } from './Models';
import { ParsedQr } from './Models';
import { parseUrl, UrlParts } from './Cookies';

export function parseQr(content: string, webOrigin: string): ParsedQr {
  const value = content.trim();
  const prefixes: string[] = ['KSUSER-AUTH-XFER:v1:', 'KSUSER-AUTH-QR:'];
  for (let i = 0; i < prefixes.length; i++) {
    if (value.toUpperCase().startsWith(prefixes[i].toUpperCase())) {
      const code = value.slice(prefixes[i].length).trim();
      if (!code || code.length > 4096) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
      return { code: code, transfer: i === 0 };
    }
  }
  let actual: UrlParts;
  try { actual = parseUrl(value); } catch (_) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
  const expected = parseUrl(webOrigin);
  if (actual.scheme !== 'https' || actual.host !== expected.host || actual.port !== expected.port) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
  const queryIndex = value.indexOf('?');
  if (queryIndex < 0) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
  const values = new Map<string, string[]>();
  for (const part of value.slice(queryIndex + 1).split('&')) {
    const eq = part.indexOf('=');
    const name = decodeURIComponent(eq < 0 ? part : part.slice(0, eq));
    const parameter = decodeURIComponent(eq < 0 ? '' : part.slice(eq + 1));
    const list = values.get(name) ?? []; list.push(parameter); values.set(name, list);
  }
  const supported = ['transferCode', 'approveCode', 'code'].filter((name: string) => values.has(name));
  if (supported.length !== 1) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
  const candidates = values.get(supported[0]) ?? [];
  if (candidates.length !== 1 || !candidates[0] || candidates[0].length > 4096) { throw new UserFacingError('这不是有效的 Ksuser 授权二维码'); }
  return { code: candidates[0], transfer: supported[0] === 'transferCode' };
}
export function qrFlow(codeType: string, transfer: boolean): string {
  if (transfer) {
    if (codeType !== 'transfer') { throw new UserFacingError('此二维码不支持登录到手机'); }
    return 'transfer';
  }
  if (['approve_login', 'approve_mfa', 'approve_sensitive', 'approve_recovery', 'approve_console_login'].includes(codeType)) { return 'approval'; }
  if (codeType === 'approve_official') { return 'official'; }
  if (codeType === 'approve_application') { return 'consent'; }
  throw new UserFacingError('此二维码不属于受支持的网页授权请求');
}
