import { UserFacingError } from './Models';
import { StoredCookie } from './Models';

export interface UrlParts { scheme: string; host: string; port: string; path: string; origin: string; }
export function parseUrl(value: string): UrlParts {
  const match = value.match(/^(https?):\/\/([^/?#]+)(\/[^?#]*)?(?:\?[^#]*)?$/i);
  if (!match || match[2].includes('@')) { throw new UserFacingError('服务地址无效'); }
  const authority = match[2].toLowerCase();
  const hostMatch = authority.match(/^(\[[0-9a-f:]+\]|[a-z0-9.-]+)(?::(\d+))?$/);
  if (!hostMatch) { throw new UserFacingError('服务地址无效'); }
  const scheme = match[1].toLowerCase();
  const port = hostMatch[2] ?? (scheme === 'https' ? '443' : '80');
  if (Number(port) < 1 || Number(port) > 65535) { throw new UserFacingError('服务端口无效'); }
  return { scheme: scheme, host: hostMatch[1], port: port, path: match[3] ?? '/', origin: scheme + '://' + authority };
}
export function splitSetCookie(value: string): string[] {
  return value.split(/\r?\n|,(?=\s*[!#$%&'*+\-.^_`|~0-9A-Za-z]+=)/).map((line: string) => line.trim()).filter((line: string) => line.length > 0);
}
export function cookieMatches(cookie: StoredCookie, value: string, now: number = Date.now()): boolean {
  const url = parseUrl(value);
  if (cookie.expiresAt !== undefined && cookie.expiresAt <= now) { return false; }
  if (cookie.secure && url.scheme !== 'https') { return false; }
  if (cookie.hostOnly ? url.host !== cookie.domain : url.host !== cookie.domain && !url.host.endsWith('.' + cookie.domain)) { return false; }
  return url.path === cookie.path || (url.path.startsWith(cookie.path) && (cookie.path.endsWith('/') || url.path.charAt(cookie.path.length) === '/'));
}
export function receiveCookies(current: StoredCookie[], lines: string[], value: string, now: number = Date.now()): StoredCookie[] {
  const url = parseUrl(value);
  let result = current.filter((cookie: StoredCookie) => cookie.expiresAt === undefined || cookie.expiresAt > now);
  for (const header of lines) {
    for (const line of splitSetCookie(header)) {
      const parts = line.split(';').map((part: string) => part.trim());
      const separator = parts[0].indexOf('=');
      if (separator <= 0) { continue; }
      const name = parts[0].slice(0, separator);
      const cookieValue = parts[0].slice(separator + 1);
      if (!/^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/.test(name) || /[\r\n;]/.test(cookieValue)) { continue; }
      let domain = url.host;
      let hostOnly = true;
      let path = url.path.slice(0, url.path.lastIndexOf('/')) || '/';
      let secure = false;
      let expiresAt: number | undefined = undefined;
      let maxAge: number | undefined = undefined;
      for (const part of parts.slice(1)) {
        const eq = part.indexOf('=');
        const key = (eq < 0 ? part : part.slice(0, eq)).toLowerCase();
        const attribute = eq < 0 ? '' : part.slice(eq + 1);
        if (key === 'domain') { domain = attribute.toLowerCase().replace(/^\./, ''); hostOnly = false; }
        else if (key === 'path' && attribute.startsWith('/')) { path = attribute; }
        else if (key === 'secure') { secure = true; }
        else if (key === 'expires') { const parsed = Date.parse(attribute); if (Number.isFinite(parsed)) { expiresAt = parsed; } }
        else if (key === 'max-age' && /^-?\d+$/.test(attribute)) { maxAge = Number(attribute); }
      }
      if (!domain || (url.host !== domain && !url.host.endsWith('.' + domain))) { continue; }
      if (secure && url.scheme !== 'https') { continue; }
      if (maxAge !== undefined) { expiresAt = now + maxAge * 1000; }
      result = result.filter((cookie: StoredCookie) => !(cookie.name === name && cookie.domain === domain && cookie.path === path));
      if (expiresAt !== undefined && expiresAt <= now) { continue; }
      result.push({ name: name, value: cookieValue, domain: domain, hostOnly: hostOnly, path: path, secure: secure, expiresAt: expiresAt });
    }
  }
  return result;
}
