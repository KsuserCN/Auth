import { ApiEnvelope, AuthPayload, QueryParam, SessionSnapshot, SessionStore, UserProfile } from './Models';
import { AppEnvironment } from './Environment';
import { HttpTransport, RequestHeaders, TransportRequest } from './HttpTransport';
import { cookieMatches, receiveCookies } from './Cookies';
import { resultMessage } from './Presentation';

export class ApiError extends Error {
  status: number;
  errorCode: string;
  constructor(status: number, code: string = '', params?: Object) {
    super(resultMessage(code, params, status)); this.status = status; this.errorCode = code;
  }
}
export class RequestCancelled extends Error { constructor() { super('操作已取消'); } }
export function emptySnapshot(): SessionSnapshot { return { accessToken: '', cookies: [], profile: null }; }
function copySnapshot(value: SessionSnapshot): SessionSnapshot { return JSON.parse(JSON.stringify(value)) as SessionSnapshot; }
export class MemorySessionStore implements SessionStore {
  private value: SessionSnapshot | null = null;
  async load(): Promise<SessionSnapshot | null> { return this.value ? copySnapshot(this.value) : null; }
  async save(value: SessionSnapshot): Promise<void> { this.value = copySnapshot(value); }
  async clear(): Promise<void> { this.value = null; }
}

export class ApiClient {
  private value: SessionSnapshot = emptySnapshot();
  private generation: number = 0;
  private refreshTask: Promise<string> | null = null;
  private csrfTask: Promise<void> | null = null;
  private writes: Promise<void> = Promise.resolve();
  private dirty: boolean = false;
  private lastPersistenceError: Error | null = null;
  onUnauthorized: () => void = () => {};
  onServiceState: (unavailable: boolean) => void = () => {};

  readonly environment: AppEnvironment;
  private store: SessionStore;
  private transport: HttpTransport;
  constructor(environment: AppEnvironment, store: SessionStore, transport: HttpTransport) {
    this.environment = environment; this.store = store; this.transport = transport;
  }
  snapshot(): SessionSnapshot { return copySnapshot(this.value); }
  async restore(): Promise<void> {
    const version = this.generation;
    const restored = await this.store.load();
    if (version !== this.generation) { throw new RequestCancelled(); }
    if (restored?.apiBaseURL && restored.apiBaseURL !== this.environment.apiBaseURL) { throw new Error('登录凭据与当前服务地址不匹配'); }
    this.value = restored ?? emptySnapshot();
  }
  invalidate(): void { this.generation++; this.refreshTask = null; this.csrfTask = null; }
  private async serialize(work: () => Promise<void>): Promise<void> {
    const task = this.writes.then(work);
    this.writes = task.catch(() => {});
    return task;
  }
  async adopt(value: SessionSnapshot): Promise<void> {
    this.invalidate();
    const version = this.generation;
    await this.serialize(async () => {
      if (version !== this.generation) { throw new RequestCancelled(); }
      await this.store.save(value);
      if (version !== this.generation) { await this.store.save(this.value); throw new RequestCancelled(); }
      this.value = copySnapshot(value); this.dirty = false; this.lastPersistenceError = null;
    });
  }
  async setProfile(profile: UserProfile): Promise<void> { await this.mutate((value: SessionSnapshot) => { value.profile = profile; }); }
  async setAccessToken(token: string): Promise<void> { await this.mutate((value: SessionSnapshot) => { value.accessToken = token; }); }
  async clear(): Promise<void> {
    this.invalidate();
    this.value = emptySnapshot(); this.dirty = false; this.lastPersistenceError = null;
    await this.serialize(async () => { await this.store.clear(); });
  }
  private async mutate(change: (value: SessionSnapshot) => void, version: number = this.generation): Promise<void> {
    await this.serialize(async () => {
      if (version !== this.generation) { throw new RequestCancelled(); }
      const next = this.snapshot(); change(next); next.apiBaseURL = this.environment.apiBaseURL;
      // Keep rotated credentials in memory if durable storage is temporarily locked.
      this.value = next; this.dirty = true;
      try { await this.store.save(next); this.dirty = false; this.lastPersistenceError = null; }
      catch (error) { this.lastPersistenceError = error as Error; throw error as Error; }
    });
  }
  async flush(): Promise<void> {
    await this.writes;
    if (!this.dirty) { return; }
    await this.serialize(async () => {
      if (!this.dirty) { return; }
      try { await this.store.save(this.value); this.dirty = false; this.lastPersistenceError = null; }
      catch (error) { this.lastPersistenceError = error as Error; throw error as Error; }
    });
  }
  private cookie(name: string): string | undefined {
    return this.value.cookies.find((item) => item.name === name && cookieMatches(item, this.environment.apiBaseURL + '/auth/'))?.value;
  }
  async ensureCsrf(): Promise<void> {
    if (this.cookie('XSRF-TOKEN')) { return; }
    if (this.csrfTask) { return this.csrfTask; }
    const task = this.perform<Object>('/auth/csrf-token', 'GET', undefined, false, false).then(() => {
      if (!this.cookie('XSRF-TOKEN')) { throw new ApiError(500, 'INVALID_RESPONSE'); }
    });
    this.csrfTask = task;
    try { await task; } finally { if (this.csrfTask === task) { this.csrfTask = null; } }
  }
  async request<T>(path: string, method: string = 'GET', body?: Object, authenticated: boolean = true, query: QueryParam[] = []): Promise<T> {
    const envelope = await this.envelope<T>(path, method, body, authenticated, query);
    return envelope.data as T;
  }
  async envelope<T>(path: string, method: string = 'GET', body?: Object, authenticated: boolean = true, query: QueryParam[] = []): Promise<ApiEnvelope<T>> {
    const version = this.generation;
    await this.flush();
    if (version !== this.generation) { throw new RequestCancelled(); }
    if (method !== 'GET' && method !== 'HEAD') { await this.ensureCsrf(); }
    if (version !== this.generation) { throw new RequestCancelled(); }
    return this.perform<T>(path, method, body === undefined ? undefined : JSON.stringify(body), authenticated, true, query);
  }
  private async refresh(failedToken: string): Promise<string> {
    if (this.value.accessToken && this.value.accessToken !== failedToken) { return this.value.accessToken; }
    if (this.refreshTask) { return this.refreshTask; }
    const version = this.generation;
    const task = (async (): Promise<string> => {
      try {
        await this.ensureCsrf();
        if (version !== this.generation) { throw new RequestCancelled(); }
        const result = await this.perform<AuthPayload>('/auth/refresh', 'POST', '{}', false, false);
        if (version !== this.generation) { throw new RequestCancelled(); }
        if (!result.data?.accessToken) { throw new ApiError(500, 'INVALID_RESPONSE'); }
        return result.data.accessToken;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401 && version === this.generation) {
          try { await this.clear(); } finally { this.onUnauthorized(); }
        }
        throw error as Error;
      }
    })();
    this.refreshTask = task;
    try { return await task; } finally { if (this.refreshTask === task) { this.refreshTask = null; } }
  }
  async upload<T>(path: string, data: ArrayBuffer, mimeType: string): Promise<T> {
    const version = this.generation;
    await this.flush();
    if (version !== this.generation) { throw new RequestCancelled(); }
    await this.ensureCsrf();
    if (version !== this.generation) { throw new RequestCancelled(); }
    const boundary = 'KsuserHarmonyUploadBoundary';
    const prefix = '--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="avatar.jpg"\r\nContent-Type: ' + mimeType + '\r\n\r\n';
    const suffix = '\r\n--' + boundary + '--\r\n';
    const first = new Uint8Array(prefix.length); const last = new Uint8Array(suffix.length);
    for (let i = 0; i < prefix.length; i++) { first[i] = prefix.charCodeAt(i); }
    for (let i = 0; i < suffix.length; i++) { last[i] = suffix.charCodeAt(i); }
    const bytes = new Uint8Array(first.length + data.byteLength + last.length);
    bytes.set(first); bytes.set(new Uint8Array(data), first.length); bytes.set(last, first.length + data.byteLength);
    const result = await this.perform<T>(path, 'POST', bytes.buffer as ArrayBuffer, true, true, [], 'multipart/form-data; boundary=' + boundary);
    return result.data as T;
  }
  private async perform<T>(path: string, method: string, body: string | ArrayBuffer | undefined, authenticated: boolean,
    canReplay: boolean, query: QueryParam[] = [], contentType: string = 'application/json'): Promise<ApiEnvelope<T>> {
    if (!path.startsWith('/') || path.startsWith('//')) { throw new Error('请求路径无效'); }
    const url = this.environment.apiBaseURL + path + (query.length ? '?' + query.map((part: QueryParam) => encodeURIComponent(part.name) + '=' + encodeURIComponent(part.value)).join('&') : '');
    const token = authenticated ? this.value.accessToken : '';
    const cookies = this.value.cookies.filter((cookie) => cookieMatches(cookie, url));
    const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
    const headers: RequestHeaders = {
      Accept: 'application/json', 'Accept-Language': 'zh-Hans', 'User-Agent': this.environment.userAgent,
      'Content-Type': body !== undefined ? contentType : undefined,
      Authorization: token ? 'Bearer ' + token : undefined,
      Cookie: cookies.length ? cookies.map((cookie) => cookie.name + '=' + cookie.value).join('; ') : undefined,
      'X-XSRF-TOKEN': method !== 'GET' && method !== 'HEAD' && csrf ? decodeURIComponent(csrf.value) : undefined
    };
    const version = this.generation;
    const request: TransportRequest = { url: url, method: method, headers: headers, body: body };
    if (this.environment.debug) { console.info('[KsuserAccountFlow] HTTP request started'); }
    const response = await this.transport.send(request);
    if (this.environment.debug) { console.info('[KsuserAccountFlow] HTTP response ' + response.status); }
    if (version !== this.generation) { throw new RequestCancelled(); }
    let envelope: ApiEnvelope<T>;
    try { envelope = JSON.parse(response.body) as ApiEnvelope<T>; }
    catch (_) { this.onServiceState(true); throw new ApiError(response.status >= 400 ? response.status : 500, 'INVALID_RESPONSE'); }
    if (!envelope || typeof envelope.code !== 'number' || typeof envelope.errorCode !== 'string') {
      this.onServiceState(true); throw new ApiError(response.status >= 400 ? response.status : 500, 'INVALID_RESPONSE');
    }
    this.onServiceState(response.status >= 500 || envelope.code >= 500);
    const refreshToken = path === '/auth/refresh' && response.status < 300 && envelope.code === 200 ? (envelope.data as AuthPayload)?.accessToken : undefined;
    if (response.cookies.length || refreshToken) {
      await this.mutate((value: SessionSnapshot) => {
        value.cookies = receiveCookies(value.cookies, response.cookies, url);
        if (refreshToken) { value.accessToken = refreshToken; }
      }, version);
    }
    if (response.status === 401 || envelope.code === 401) {
      if (authenticated && canReplay && token) {
        await this.refresh(token);
        if (version !== this.generation) { throw new RequestCancelled(); }
        return this.perform<T>(path, method, body, true, false, query, contentType);
      }
      if (authenticated && !canReplay) { try { await this.clear(); } finally { this.onUnauthorized(); } }
      throw new ApiError(401, envelope.errorCode, envelope.params);
    }
    if (response.status < 200 || response.status >= 300 || envelope.code >= 400) {
      throw new ApiError(response.status >= 400 ? response.status : envelope.code, envelope.errorCode, envelope.params);
    }
    return envelope;
  }
}
