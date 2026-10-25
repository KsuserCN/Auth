import { UserFacingError } from './Models';
import { http } from '@kit.NetworkKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { resultMessage } from './Presentation';
import { parseUrl } from './Cookies';

export interface RequestHeaders {
  Accept: string; 'Accept-Language': string; 'User-Agent': string; 'Content-Type'?: string;
  Authorization?: string; Cookie?: string; 'X-XSRF-TOKEN'?: string;
}
export interface TransportRequest { url: string; method: string; headers: RequestHeaders; body?: string | ArrayBuffer; }
export interface TransportResponse { status: number; body: string; cookies: string[]; }
export interface HttpTransport { send(request: TransportRequest): Promise<TransportResponse>; }
type ResponseHeaders = Record<string, string | string[]>;

function nativeCookieLines(raw: string, url: string): string[] {
  if (!raw) { return []; }
  if (!raw.includes('\t')) { return [raw]; }
  const host = parseUrl(url).host;
  const lines: string[] = [];
  // libcurl's Netscape cookie-list fallback has seven TAB-separated fields.
  // https://curl.se/docs/http-cookies.html
  for (let line of raw.split(/\r?\n/)) {
    const httpOnly = line.startsWith('#HttpOnly_');
    if (httpOnly) { line = line.slice('#HttpOnly_'.length); }
    else if (line.startsWith('#')) { continue; }
    const fields = line.split('\t');
    if (fields.length !== 7 || !['TRUE', 'FALSE'].includes(fields[1]) || !['TRUE', 'FALSE'].includes(fields[3]) ||
      !/^\d+$/.test(fields[4]) || !/^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/.test(fields[5]) ||
      !fields[2].startsWith('/') || /[;\r\n]/.test(fields[0] + fields[2] + fields[6])) { continue; }
    const domain = fields[0].toLowerCase().replace(/^\./, '');
    const includeSubdomains = fields[1] === 'TRUE';
    if (domain !== host && (!includeSubdomains || !host.endsWith('.' + domain))) { continue; }
    let cookie = fields[5] + '=' + fields[6] + '; Path=' + fields[2];
    if (includeSubdomains) { cookie += '; Domain=' + domain; }
    if (fields[3] === 'TRUE') { cookie += '; Secure'; }
    if (httpOnly) { cookie += '; HttpOnly'; }
    const expiry = Number(fields[4]);
    if (expiry > 0) {
      const date = new Date(expiry * 1000);
      if (!Number.isFinite(date.getTime())) { continue; }
      cookie += '; Expires=' + date.toUTCString();
    }
    lines.push(cookie);
  }
  return lines;
}

function responseCookies(response: http.HttpResponse, url: string): string[] {
  const headers = response.header as ResponseHeaders;
  const cookies: string[] = [];
  if (headers) {
    for (const name of Object.keys(headers)) {
      if (name.toLowerCase() !== 'set-cookie') { continue; }
      const declared = headers[name];
      if (typeof declared === 'string' && declared.length > 0) { cookies.push(declared); }
      else if (Array.isArray(declared)) {
        cookies.push(...declared.filter((line: string) => typeof line === 'string' && line.length > 0));
      }
    }
  }
  return cookies.length > 0 ? cookies : nativeCookieLines(response.cookies, url);
}
export class NativeHttpTransport implements HttpTransport {
  private readonly deadlineMs: number;

  constructor(deadlineMs: number = 35000) { this.deadlineMs = deadlineMs; }

  async send(request: TransportRequest): Promise<TransportResponse> {
    const connection = http.createHttp();
    let timer: number = -1;
    let timedOut: boolean = false;
    let destroyed: boolean = false;
    const release = (): void => {
      if (destroyed) { return; }
      destroyed = true;
      try { connection.destroy(); } catch (_) {}
    };
    try {
      const deadline = new Promise<http.HttpResponse>((_resolve, reject) => {
        timer = setTimeout(() => {
          timedOut = true;
          release();
          reject(new UserFacingError(resultMessage('REQUEST_TIMEOUT')));
        }, this.deadlineMs);
      });
      const pending = connection.request(request.url, {
        method: request.method as http.RequestMethod,
        header: request.headers,
        extraData: request.body,
        connectTimeout: 30000,
        readTimeout: 30000,
        expectDataType: http.HttpDataType.STRING,
        usingCache: false,
        maxRedirects: 0
      });
      // Only the winning result reaches the API client; a late native result cannot update its session.
      const response = await Promise.race<http.HttpResponse>([pending, deadline]);
      const cookies = responseCookies(response, request.url);
      if (typeof response.result !== 'string') { throw new UserFacingError('服务返回了无法识别的数据'); }
      return { status: response.responseCode, body: response.result, cookies: cookies };
    } catch (error) {
      const business = error as BusinessError;
      throw new UserFacingError(resultMessage(timedOut || business.code === 2300028 ? 'REQUEST_TIMEOUT' : 'NETWORK_ERROR'));
    } finally {
      if (timer !== -1) { clearTimeout(timer); }
      release();
    }
  }
}
