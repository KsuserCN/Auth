import { UserFacingError } from './Models';
import { common } from '@kit.AbilityKit';
import { AppEnvironment } from './Environment';
import { ApiClient, ApiError, MemorySessionStore, RequestCancelled } from './ApiClient';
import { NativeHttpTransport, HttpTransport } from './HttpTransport';
import { Repository } from './Repository';
import { SecureSessionStore, SessionStorageError } from '../native/SecureSessionStore';
import { NativeServices } from '../native/NativeServices';
import { parseQr, qrFlow } from './QrParser';
import { resultMessage } from './Presentation';
import { ActionResult, ResourceState, emptyResourceState, AvatarSource, AvatarCropRegion,
  AdaptiveAuthStatus, ApiEnvelope, AuthPayload, AvatarUpload, MfaChallenge, MobileAuthorizationContext,
  OfficialApp, PasskeyItem, PasswordRequirement, QrConfirmation, RecoveryTicket, SensitiveLog, SensitiveStatus,
  SessionItem, ThirdPartyApp, TotpSetup, TotpStatus, UserProfile, passwordError, validEmail, validUsername } from './Models';

type ResourceName = 'profile' | 'passkeys' | 'totp' | 'risk' | 'sessions' | 'logs' | 'apps' | 'passwordPolicy';

/** One account owns all visible state. Replacing snapshots keeps ArkUI's observed updates predictable. */
@Observed
export class AppModel {
  ready: boolean = false;
  authenticated: boolean = false;
  busy: boolean = false;
  offline: boolean = false;
  error: string = '';
  notice: string = '';
  codeSentType: string = '';
  codeSentEmail: string = '';
  codeSentAt: number = 0;
  storageIssue: string = '';
  profile: UserProfile | null = null;
  passwordPolicy: PasswordRequirement | null = null;
  profileState: ResourceState = emptyResourceState();
  passkeysState: ResourceState = emptyResourceState();
  totpState: ResourceState = emptyResourceState();
  riskState: ResourceState = emptyResourceState();
  sessionsState: ResourceState = emptyResourceState();
  logsState: ResourceState = emptyResourceState();
  appsState: ResourceState = emptyResourceState();
  passwordPolicyState: ResourceState = emptyResourceState();
  passkeys: PasskeyItem[] = [];
  totp: TotpStatus = { enabled: false, recoveryCodesCount: 0 };
  totpSetup: TotpSetup | null = null;
  recoveryCodes: string[] = [];
  sessions: SessionItem[] = [];
  logs: SensitiveLog[] = [];
  logsPage: number = 1;
  logsTotalPages: number = 1;
  logsTotal: number = 0;
  risk: AdaptiveAuthStatus | null = null;
  officialApps: OfficialApp[] = [];
  thirdPartyApps: ThirdPartyApp[] = [];
  recoveryTicket: RecoveryTicket | null = null;
  recoveryExpiresAt: number = 0;
  avatarSource: AvatarSource | null = null;
  avatarPreview: AvatarUpload | null = null;
  challengeVisible: boolean = false;
  challengeRevision: number = 0;
  challengeKind: string = '';
  mfa: MfaChallenge | null = null;
  sensitive: SensitiveStatus | null = null;
  sensitiveTitle: string = '';
  qr: QrConfirmation | null = null;
  consent: MobileAuthorizationContext | null = null;
  debug: boolean = false;
  passkeyOrigin: string = '';
  apiBaseURL: string = '';
  filingNumber: string = '';

  private environment: AppEnvironment = new AppEnvironment();
  private context: common.UIAbilityContext | null = null;
  private client: ApiClient | null = null;
  private repositoryValue: Repository | null = null;
  private native: NativeServices | null = null;
  private transport: HttpTransport = new NativeHttpTransport();
  private staged: Repository | null = null;
  private authGeneration: number = 0;
  private accountGeneration: number = 0;
  private pendingSensitive: (() => Promise<void>) | null = null;
  private pendingSensitiveResult: ((result: ActionResult) => void) | null = null;
  private pendingSensitiveGeneration: number = 0;
  consentExpiresAt: number = 0;
  private pendingRawQr: string = '';
  private logGeneration: number = 0;
  private idleWaiters: (() => void)[] = [];
  private initialized: boolean = false;
  private restored: boolean = false;

  private repository(): Repository {
    if (!this.repositoryValue) { throw new UserFacingError('应用尚未初始化，请重试'); }
    return this.repositoryValue;
  }
  private services(): NativeServices {
    if (!this.native) { throw new UserFacingError('设备能力尚未初始化，请重试'); }
    return this.native;
  }
  private checkAccount(version: number): void { if (version !== this.accountGeneration) { throw new RequestCancelled(); } }
  private trace(phase: string): void {
    // Static phase names only: never include account input, tokens, cookies or signed data.
    if (this.environment.debug) { console.info('[KsuserAccountFlow] ' + phase); }
  }
  clearFeedback(): void { this.error = ''; this.notice = ''; }
  private failure(error: Error): void {
    if (error instanceof RequestCancelled || error instanceof UserFacingError && error.message === '操作已取消') { return; }
    if (error instanceof SessionStorageError) { this.storageIssue = error.kind === 'corrupt' ? 'corrupt' : 'unavailable'; }
    this.error = error instanceof ApiError || error instanceof SessionStorageError || error instanceof UserFacingError ?
      error.message : resultMessage('REQUEST_FAILED');
  }
  private async run(operation: () => Promise<void>): Promise<ActionResult> {
    if (this.busy) { return { status: 'cancelled' }; }
    this.busy = true; this.clearFeedback();
    try { await operation(); return { status: 'success' }; }
    catch (error) {
      this.failure(error as Error);
      return { status: error instanceof RequestCancelled || error instanceof UserFacingError &&
        error.message === '操作已取消' ? 'cancelled' : 'failure', error: this.error };
    }
    finally { this.finishBusy(); }
  }
  private resourceState(name: ResourceName): ResourceState {
    switch (name) {
      case 'profile': return this.profileState;
      case 'passkeys': return this.passkeysState;
      case 'totp': return this.totpState;
      case 'risk': return this.riskState;
      case 'sessions': return this.sessionsState;
      case 'logs': return this.logsState;
      case 'apps': return this.appsState;
      default: return this.passwordPolicyState;
    }
  }
  private setResourceState(name: ResourceName, state: ResourceState): void {
    switch (name) {
      case 'profile': this.profileState = state; break;
      case 'passkeys': this.passkeysState = state; break;
      case 'totp': this.totpState = state; break;
      case 'risk': this.riskState = state; break;
      case 'sessions': this.sessionsState = state; break;
      case 'logs': this.logsState = state; break;
      case 'apps': this.appsState = state; break;
      default: this.passwordPolicyState = state;
    }
  }
  private async resource(name: ResourceName, operation: () => Promise<void>): Promise<void> {
    const version = this.accountGeneration;
    const previous = this.resourceState(name);
    this.setResourceState(name, { status: 'loading', error: '', hasData: previous.hasData });
    try {
      await operation(); this.checkAccount(version);
      this.setResourceState(name, { status: 'loaded', error: '', hasData: true });
    } catch (error) {
      if (version === this.accountGeneration && !(error instanceof RequestCancelled)) {
        this.failure(error as Error);
        this.setResourceState(name, { status: 'error', error: this.error, hasData: previous.hasData });
      }
      throw error as Error;
    }
  }
  private finishBusy(): void {
    this.busy = false;
    const waiting = this.idleWaiters; this.idleWaiters = [];
    waiting.forEach((resolve: () => void) => resolve());
  }
  private async read(operation: () => Promise<void>): Promise<void> {
    const version = this.accountGeneration;
    while (this.busy) { await new Promise<void>((resolve) => { this.idleWaiters.push(resolve); }); }
    if (version !== this.accountGeneration) { return; }
    await this.run(operation);
  }
  private showChallenge(kind: string): void { this.challengeKind = kind; this.challengeRevision++; this.challengeVisible = true; }
  private closeChallenge(): void { this.challengeVisible = false; this.challengeKind = ''; }
  private clearAccountData(): void {
    this.accountGeneration++; this.logGeneration++;
    this.authenticated = false; this.profile = null; this.passkeys = []; this.sessions = []; this.logs = [];
    this.logsPage = 1; this.logsTotal = 0; this.logsTotalPages = 1; this.risk = null;
    this.totp = { enabled: false, recoveryCodesCount: 0 }; this.totpSetup = null; this.recoveryCodes = [];
    this.officialApps = []; this.thirdPartyApps = []; this.recoveryTicket = null; this.recoveryExpiresAt = 0;
    this.avatarPreview = null; this.avatarSource = null;
    this.profileState = emptyResourceState(); this.passkeysState = emptyResourceState();
    this.totpState = emptyResourceState(); this.riskState = emptyResourceState();
    this.sessionsState = emptyResourceState(); this.logsState = emptyResourceState(); this.appsState = emptyResourceState();
    this.mfa = null; this.sensitive = null; this.qr = null; this.consent = null;
    this.codeSentAt = 0; this.codeSentType = ''; this.codeSentEmail = '';
    this.cancelPendingSensitive(); this.staged?.client.invalidate(); this.staged = null; this.pendingRawQr = '';
    this.closeChallenge();
  }
  async initialize(context: common.UIAbilityContext): Promise<void> {
    if (this.busy) { return; }
    this.busy = true; this.clearFeedback(); this.storageIssue = '';
    try {
      if (!this.initialized) {
        this.context = context;
        this.environment = await AppEnvironment.load(context);
        this.apiBaseURL = this.environment.apiBaseURL; this.debug = this.environment.debug;
        this.filingNumber = this.environment.filingNumber ?? '';
        this.native = new NativeServices(context, this.environment);
        this.client = new ApiClient(this.environment, new SecureSessionStore(context, this.environment.apiBaseURL), this.transport);
        this.repositoryValue = new Repository(this.client);
        this.client.onUnauthorized = () => { this.authGeneration++; this.clearAccountData(); };
        this.client.onServiceState = (unavailable: boolean) => { this.offline = unavailable; };
        this.initialized = true;
      }
      this.trace('startup: flush pending session');
      await this.client!.flush();
      if (!this.restored) {
        this.trace('startup: restore encrypted session');
        await this.client!.restore(); this.restored = true;
        this.trace('startup: session restored');
      }
      const snapshot = this.client!.snapshot();
      if (snapshot.accessToken) {
        this.profile = snapshot.profile; this.authenticated = snapshot.profile !== null;
        this.profileState = { status: 'idle', error: '', hasData: snapshot.profile !== null };
        await this.fetchProfile();
      }
      try {
        this.trace('startup: load password policy');
        await this.fetchPasswordRequirement();
        this.trace('startup: password policy loaded');
      }
      catch (error) { if (!this.authenticated) { this.failure(error as Error); } }
      this.ready = true;
    } catch (error) { this.ready = true; this.failure(error as Error); }
    finally { this.trace('startup: finished'); this.finishBusy(); }
  }
  async resetLocalSession(): Promise<ActionResult> {
    return await this.run(async () => {
      this.authGeneration++; await this.client?.clear(); this.clearAccountData(); this.storageIssue = ''; this.ready = true;
      this.notice = '本机登录数据已清除，请重新登录';
    });
  }
  private newStaged(): Repository { return new Repository(new ApiClient(this.environment, new MemorySessionStore(), this.transport)); }
  private async authenticate(operation: (repository: Repository) => Promise<ApiEnvelope<AuthPayload>>): Promise<ActionResult> {
    return await this.run(async () => {
      const attempt = ++this.authGeneration;
      this.staged?.client.invalidate();
      const staged = this.newStaged(); this.staged = staged;
      this.trace('login: authenticate temporary session');
      const result = await operation(staged);
      this.trace('login: authentication response received');
      if (attempt !== this.authGeneration) { throw new RequestCancelled(); }
      if (result.code === 201 && result.data?.challengeId) {
        const supported = (result.data.methods ?? [result.data.method ?? 'totp']).filter((method: string) => ['totp', 'passkey'].includes(method));
        if (!supported.length) { throw new UserFacingError('当前账号需要其他验证方式，请在网页版完成账号设置'); }
        this.mfa = { challengeId: result.data.challengeId, method: supported.includes(result.data.method ?? '') ? result.data.method! : supported[0], methods: supported };
        this.showChallenge('mfa'); return;
      }
      if (result.code !== 200 || !result.data?.accessToken) { throw new ApiError(500, 'INVALID_RESPONSE'); }
      await this.finishAuthentication(result.data.accessToken, staged, attempt);
    });
  }
  private async finishAuthentication(token: string, staged: Repository, attempt: number): Promise<void> {
    if (!token) { throw new ApiError(500, 'INVALID_RESPONSE'); }
    await staged.client.setAccessToken(token);
    this.trace('login: read full profile');
    const profile = await staged.currentUser();
    this.trace('login: full profile received');
    if (attempt !== this.authGeneration) { throw new RequestCancelled(); }
    if (!profile?.uuid || typeof profile.username !== 'string' || typeof profile.email !== 'string') { throw new ApiError(500, 'INVALID_RESPONSE'); }
    await staged.client.setProfile(profile);
    const snapshot = staged.client.snapshot(); snapshot.apiBaseURL = this.environment.apiBaseURL;
    this.trace('login: persist encrypted session');
    await this.client!.adopt(snapshot);
    this.trace('login: encrypted session committed');
    if (attempt !== this.authGeneration) { throw new RequestCancelled(); }
    const pending = this.pendingRawQr;
    this.clearAccountData(); this.pendingRawQr = pending;
    this.profile = profile; this.authenticated = true; this.storageIssue = ''; this.offline = false;
    this.profileState = { status: 'loaded', error: '', hasData: true };
    this.notice = '登录成功';
    if (pending) { this.pendingRawQr = ''; await this.previewRawQr(pending); }
  }
  async login(email: string, password: string): Promise<ActionResult> { return await this.authenticate((repo: Repository) => repo.login(email, password)); }
  async loginWithCode(email: string, code: string): Promise<ActionResult> { return await this.authenticate((repo: Repository) => repo.login(email, code, true)); }
  async register(username: string, email: string, password: string, code: string): Promise<ActionResult> {
    return await this.authenticate(async (repo: Repository) => {
      if (!this.passwordPolicy) { this.passwordPolicy = await repo.passwordRequirement(); }
      const invalid = passwordError(password, this.passwordPolicy); if (invalid) { throw new UserFacingError(invalid); }
      const availability = await repo.checkUsername(username);
      if (availability.exists) { throw new UserFacingError('这个用户名已被使用，请换一个'); }
      await repo.validatePassword(password);
      return repo.register(username, email, password, code);
    });
  }
  async sendCode(email: string, type: string): Promise<ActionResult> {
    const operation = async (): Promise<void> => {
      if (type !== 'sensitive-verification' && !validEmail(email)) { throw new UserFacingError('请输入有效的邮箱地址'); }
      await this.repository().sendCode(email, type);
      this.codeSentType = type; this.codeSentEmail = email.trim().toLowerCase(); this.codeSentAt = Date.now();
      this.notice = '验证码已发送，请检查邮箱';
    };
    if (type === 'change-email') { return await this.protect('修改绑定邮箱', operation); }
    return await this.run(operation);
  }
  async loginPasskey(): Promise<ActionResult> {
    return await this.authenticate(async (repo: Repository) => {
      const options = await repo.passkeyAuthenticationOptions();
      try { return await repo.loginPasskey(options, await this.services().authenticate(options)); }
      finally { this.passkeyOrigin = this.services().readLastOrigin(); }
    });
  }
  async verifyMfa(value: string, recovery: boolean = false): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.mfa || !this.staged) { throw new UserFacingError('登录验证已结束，请重新登录'); }
      const attempt = this.authGeneration; const staged = this.staged;
      const token = await staged.verifyMfa(this.mfa.challengeId, value, recovery);
      await this.finishAuthentication(token.accessToken ?? '', staged, attempt);
    });
  }
  async verifyMfaPasskey(): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.mfa || !this.staged || !this.mfa.methods.includes('passkey')) { throw new UserFacingError('当前登录不支持此验证方式'); }
      const attempt = this.authGeneration; const staged = this.staged;
      const options = await staged.passkeyAuthenticationOptions();
      try {
        const token = await staged.verifyMfaPasskey(this.mfa.challengeId, options, await this.services().authenticate(options));
        await this.finishAuthentication(token.accessToken ?? '', staged, attempt);
      } finally { this.passkeyOrigin = this.services().readLastOrigin(); }
    });
  }
  cancelChallenge(): void {
    this.clearFeedback();
    if (this.challengeKind === 'mfa') {
      this.authGeneration++; this.staged?.client.invalidate(); this.staged = null; this.client?.invalidate(); this.mfa = null; this.pendingRawQr = '';
    }
    this.cancelPendingSensitive(); this.sensitive = null; this.qr = null; this.consent = null;
    this.closeChallenge();
  }
  private cancelPendingSensitive(): void {
    const resolve = this.pendingSensitiveResult;
    this.pendingSensitive = null; this.pendingSensitiveResult = null;
    if (resolve) { resolve({ status: 'cancelled' }); }
  }
  private async protect(title: string, operation: () => Promise<void>): Promise<ActionResult> {
    if (this.pendingSensitive) { return { status: 'cancelled' }; }
    let completion: Promise<ActionResult> | null = null;
    const immediate = await this.run(async () => {
      if (!this.authenticated) { throw new ApiError(401, 'UNAUTHENTICATED'); }
      const version = this.accountGeneration;
      const status = await this.repository().sensitiveStatus(); this.checkAccount(version);
      if (status.verified) { await operation(); return; }
      const methods = (status.methods ?? ['password', 'email-code', 'totp', 'passkey']).filter((method: string) => ['password', 'email-code', 'totp', 'passkey'].includes(method));
      if (!methods.length) { throw new UserFacingError('当前账号的验证方式暂不受支持，请先在网页版设置其他验证方式'); }
      this.sensitive = { verified: false, remainingSeconds: status.remainingSeconds, methods: methods,
        preferredMethod: methods.includes(status.preferredMethod ?? '') ? status.preferredMethod : methods[0] };
      this.sensitiveTitle = title; this.pendingSensitive = operation; this.pendingSensitiveGeneration = version;
      completion = new Promise<ActionResult>((resolve) => { this.pendingSensitiveResult = resolve; });
      if (this.profile) { this.profile = JSON.parse(JSON.stringify(this.profile)) as UserProfile; }
      this.showChallenge('sensitive');
    });
    // Do not hold busy while waiting for the user: the verification action must be able to run.
    return completion !== null ? await completion : immediate;
  }
  private async completeSensitive(): Promise<void> {
    this.checkAccount(this.pendingSensitiveGeneration);
    const operation = this.pendingSensitive; const resolve = this.pendingSensitiveResult;
    this.pendingSensitive = null; this.pendingSensitiveResult = null; this.sensitive = null; this.closeChallenge();
    try {
      if (operation) { await operation(); }
      if (resolve) { resolve({ status: 'success' }); }
    } catch (error) {
      this.failure(error as Error);
      if (resolve) { resolve({ status: error instanceof RequestCancelled ? 'cancelled' : 'failure', error: this.error }); }
      throw error as Error;
    }
  }
  async requireSensitive(title: string): Promise<ActionResult> { return this.protect(title, async () => {}); }
  async verifySensitive(method: string, value: string, recovery: boolean = false): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.sensitive?.methods?.includes(method)) { throw new UserFacingError('当前操作不支持此验证方式'); }
      await this.repository().verifySensitive(method, value, recovery); await this.completeSensitive();
    });
  }
  async verifySensitivePasskey(): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.sensitive?.methods?.includes('passkey')) { throw new UserFacingError('当前操作不支持通行密钥'); }
      const options = await this.repository().passkeyAuthenticationOptions(true);
      try {
        await this.repository().verifySensitivePasskey(options, await this.services().authenticate(options));
        await this.completeSensitive();
      } finally { this.passkeyOrigin = this.services().readLastOrigin(); }
    });
  }
  private async fetchProfile(): Promise<void> {
    await this.resource('profile', async () => {
      const version = this.accountGeneration; const user = await this.repository().currentUser(); this.checkAccount(version);
      if (!user?.uuid) { throw new ApiError(500, 'INVALID_RESPONSE'); }
      await this.client!.setProfile(user); this.checkAccount(version); this.profile = user; this.authenticated = true;
    });
  }
  async loadProfile(): Promise<void> { await this.read(async () => this.fetchProfile()); }
  private async fetchSecurity(): Promise<void> {
    const version = this.accountGeneration;
    const tasks: Promise<void>[] = [
      this.resource('passkeys', async () => { const values = await this.repository().passkeys(); this.checkAccount(version); this.passkeys = values ?? []; }),
      this.resource('totp', async () => { const value = await this.repository().totpStatus(); this.checkAccount(version); this.totp = value; }),
      this.resource('risk', async () => { const value = await this.repository().adaptiveStatus(); this.checkAccount(version); this.risk = value; })
    ];
    // A failed credential query must not hide the independently available risk/TOTP information.
    await Promise.allSettled(tasks);
  }
  async loadSecurity(): Promise<void> { await this.read(async () => this.fetchSecurity()); }
  private async fetchPasswordRequirement(): Promise<void> {
    await this.resource('passwordPolicy', async () => { this.passwordPolicy = await this.repository().passwordRequirement(); });
  }
  async loadPasswordRequirement(): Promise<void> { await this.read(async () => this.fetchPasswordRequirement()); }
  async checkUsernameAvailable(username: string): Promise<boolean> {
    let available = false;
    await this.run(async () => {
      if (!validUsername(username)) { throw new UserFacingError('用户名需为 3–20 个汉字、字母、数字、下划线或连字符'); }
      const value = await this.repository().checkUsername(username);
      if (value.exists) { throw new UserFacingError('这个用户名已被使用，请换一个'); }
      available = true;
    });
    return available;
  }
  async validateRegistrationPassword(password: string): Promise<boolean> {
    let valid = false;
    await this.run(async () => {
      const invalid = passwordError(password, this.passwordPolicy);
      if (invalid) { throw new UserFacingError(invalid); }
      await this.repository().validatePassword(password); valid = true;
    });
    return valid;
  }
  async refreshAll(): Promise<void> {
    await this.read(async () => {
      if (!this.authenticated) {
        if (this.client?.snapshot().accessToken) { await this.fetchProfile(); }
        else { await this.fetchPasswordRequirement(); return; }
      }
      const tasks: Promise<void>[] = [this.fetchProfile(), this.fetchSecurity(), this.fetchSessions()];
      const results = await Promise.allSettled(tasks);
      for (const result of results) { if (result.status === 'rejected') { throw result.reason as Error; } }
    });
  }
  async saveProfile(field: string, value: string): Promise<ActionResult> {
    return await this.run(async () => {
      if (field === 'username' && !validUsername(value)) { throw new UserFacingError('用户名需为 3–20 个汉字、字母、数字、下划线或连字符'); }
      const version = this.accountGeneration;
      if (field === 'username' && (await this.repository().checkUsername(value)).exists && value.trim() !== this.profile?.username) { throw new UserFacingError('这个用户名已被使用，请换一个'); }
      const user = await this.repository().updateProfile(field, value); this.checkAccount(version);
      await this.client!.setProfile(user); this.checkAccount(version); this.profile = user; this.profileState = { status: 'loaded', error: '', hasData: true }; this.notice = '个人资料已更新';
    });
  }
  async prepareAvatar(): Promise<ActionResult> {
    return await this.run(async () => {
      const version = this.accountGeneration; const avatar = await this.services().selectAvatar(); this.checkAccount(version);
      this.avatarSource = avatar; this.avatarPreview = null;
    });
  }
  async cropAvatar(region: AvatarCropRegion): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.avatarSource) { throw new UserFacingError('请先选择头像'); }
      const version = this.accountGeneration;
      const avatar = await this.services().cropAvatar(this.avatarSource, region); this.checkAccount(version); this.avatarPreview = avatar;
    });
  }
  cancelAvatar(): void { this.avatarPreview = null; this.avatarSource = null; }
  async confirmAvatar(): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.avatarPreview) { return; }
      const version = this.accountGeneration;
      const user = await this.repository().uploadAvatar(this.avatarPreview.data, this.avatarPreview.mimeType); this.checkAccount(version);
      await this.client!.setProfile(user); this.checkAccount(version); this.profile = user; this.profileState = { status: 'loaded', error: '', hasData: true }; this.cancelAvatar(); this.notice = '头像已更新';
    });
  }
  async updateSetting(field: string, value: boolean | string): Promise<ActionResult> {
    const result = await this.protect('修改账号安全设置', async () => {
      const version = this.accountGeneration; await this.repository().updateSetting(field, value); this.checkAccount(version);
      await this.fetchProfile(); this.notice = '安全设置已更新';
    });
    // Native controls must reflect committed values after a pending verification or failed write.
    if (this.profile) { this.profile = JSON.parse(JSON.stringify(this.profile)) as UserProfile; }
    return result;
  }
  private async fetchSessions(): Promise<void> {
    await this.resource('sessions', async () => {
      const version = this.accountGeneration; const items = await this.repository().sessions(); this.checkAccount(version); this.sessions = items ?? [];
    });
  }
  async loadSessions(): Promise<void> { await this.read(async () => this.fetchSessions()); }
  async revokeSession(id: number): Promise<ActionResult> {
    return await this.protect('撤销登录会话', async () => {
      const version = this.accountGeneration; const current = this.sessions.find((item: SessionItem) => item.id === id)?.current;
      await this.repository().revokeSession(id); this.checkAccount(version);
      if (current) { this.authGeneration++; await this.client!.clear(); this.clearAccountData(); }
      else { await this.fetchSessions(); }
      this.notice = '登录会话已撤销';
    });
  }
  async logout(all: boolean = false): Promise<ActionResult> {
    const operation = async (): Promise<void> => {
      this.authGeneration++; this.client?.invalidate();
      let remoteError: Error | null = null;
      try { await this.repository().logout(all); } catch (error) { remoteError = error as Error; }
      if (all && remoteError) { throw remoteError; }
      await this.client!.clear(); this.clearAccountData(); this.storageIssue = '';
      this.notice = remoteError ? '本机已退出；服务端退出未完成，可在其他设备撤销此会话' : '已退出登录';
    };
    if (all) { return await this.protect('退出所有设备', operation); }
    return await this.run(operation);
  }
  async loadLogs(page: number = 1, result: string = '', operation: string = '', startDate: string = '', endDate: string = ''): Promise<void> {
    // Logs have their own sequence so filter changes cannot be overwritten by a late page.
    const request = ++this.logGeneration;
    await this.read(async () => {
      await this.resource('logs', async () => {
        const version = this.accountGeneration;
        const response = await this.repository().logs(page, result, operation, startDate, endDate); this.checkAccount(version);
        if (request !== this.logGeneration) { throw new RequestCancelled(); }
        this.logs = response.data ?? []; this.logsPage = response.page; this.logsTotal = response.total; this.logsTotalPages = Math.max(1, response.totalPages);
      });
    });
  }
  async loadApps(): Promise<void> {
    await this.read(async () => {
      await this.resource('apps', async () => {
        const version = this.accountGeneration;
        const official = await this.repository().officialApps(); this.checkAccount(version);
        const thirdParty = await this.repository().thirdPartyApps(); this.checkAccount(version);
        this.officialApps = official ?? []; this.thirdPartyApps = thirdParty ?? [];
      });
    });
  }
  async revokeApplication(id: string, official: boolean): Promise<ActionResult> {
    return await this.protect('撤销应用授权', async () => {
      const version = this.accountGeneration; await this.repository().revokeApplication(id, official); this.checkAccount(version);
      if (official) { const apps = await this.repository().officialApps(); this.checkAccount(version); this.officialApps = apps ?? []; }
      else { const apps = await this.repository().thirdPartyApps(); this.checkAccount(version); this.thirdPartyApps = apps ?? []; }
      this.notice = '应用授权已撤销';
    });
  }
  async addPasskey(name: string): Promise<ActionResult> {
    return await this.protect('添加通行密钥', async () => {
      if (!name.trim()) { throw new UserFacingError('请输入通行密钥名称'); }
      const version = this.accountGeneration; const options = await this.repository().registrationOptions(name); this.checkAccount(version);
      try { const result = await this.services().register(options); this.checkAccount(version); await this.repository().registerPasskey(name, result); }
      finally { this.passkeyOrigin = this.services().readLastOrigin(); }
      this.checkAccount(version); await this.fetchSecurity(); this.notice = '通行密钥已添加';
    });
  }
  async renamePasskey(id: number, name: string): Promise<ActionResult> {
    return await this.protect('重命名通行密钥', async () => {
      if (!name.trim()) { throw new UserFacingError('请输入通行密钥名称'); }
      const version = this.accountGeneration; await this.repository().renamePasskey(id, name); this.checkAccount(version);
      await this.fetchSecurity(); this.notice = '通行密钥名称已更新';
    });
  }
  async deletePasskey(id: number): Promise<ActionResult> {
    return await this.protect('删除通行密钥', async () => {
      const version = this.accountGeneration; await this.repository().deletePasskey(id); this.checkAccount(version);
      await this.fetchSecurity(); this.notice = '通行密钥已删除';
    });
  }
  async setupTotp(): Promise<ActionResult> {
    return await this.protect('启用身份验证器', async () => {
      const version = this.accountGeneration; const setup = await this.repository().setupTotp(); this.checkAccount(version); this.totpSetup = setup;
    });
  }
  async enableTotp(code: string): Promise<ActionResult> {
    return await this.protect('启用身份验证器', async () => {
      if (!this.totpSetup) { throw new UserFacingError('请先获取验证器绑定信息'); }
      const version = this.accountGeneration; const recoveryCodes = this.totpSetup.recoveryCodes;
      await this.repository().enableTotp(code, recoveryCodes); this.checkAccount(version);
      this.totpSetup = null; this.recoveryCodes = recoveryCodes; await this.fetchSecurity(); this.notice = '验证器已启用，请妥善保存恢复码';
    });
  }
  async disableTotp(): Promise<ActionResult> {
    return await this.protect('停用身份验证器', async () => {
      const version = this.accountGeneration; await this.repository().disableTotp(); this.checkAccount(version);
      this.recoveryCodes = []; this.totpSetup = null; await this.fetchSecurity(); this.notice = '验证器已停用';
    });
  }
  async loadRecoveryCodes(regenerate: boolean = false): Promise<ActionResult> {
    return await this.protect(regenerate ? '重新生成恢复码' : '查看恢复码', async () => {
      const version = this.accountGeneration; const codes = await this.repository().recoveryCodes(regenerate); this.checkAccount(version);
      this.recoveryCodes = codes; if (regenerate) { this.notice = '恢复码已更新，旧恢复码已失效'; }
    });
  }
  async changeEmail(email: string, code: string): Promise<ActionResult> {
    return await this.protect('修改绑定邮箱', async () => {
      if (!validEmail(email) || !/^\d{6}$/.test(code)) { throw new UserFacingError('请输入有效邮箱和 6 位验证码'); }
      const version = this.accountGeneration; const user = await this.repository().changeEmail(email, code); this.checkAccount(version);
      await this.client!.setProfile(user); this.checkAccount(version); this.profile = user; this.profileState = { status: 'loaded', error: '', hasData: true }; this.notice = '绑定邮箱已更新';
    });
  }
  async changePassword(password: string): Promise<ActionResult> {
    return await this.protect('修改登录密码', async () => {
      if (!this.passwordPolicy) { await this.fetchPasswordRequirement(); }
      const invalid = passwordError(password, this.passwordPolicy); if (invalid) { throw new UserFacingError(invalid); }
      const version = this.accountGeneration; await this.repository().changePassword(password); this.checkAccount(version);
      await this.fetchProfile(); this.notice = '密码已更新';
    });
  }
  async deleteAccount(confirmText: string): Promise<ActionResult> {
    return await this.protect('删除账号', async () => {
      const version = this.accountGeneration; await this.repository().deleteAccount(confirmText); this.checkAccount(version);
      this.authGeneration++; await this.client!.clear(); this.clearAccountData(); this.notice = '账号已删除';
    });
  }
  async issueRecoveryTicket(): Promise<ActionResult> {
    return await this.protect('生成账号恢复授权', async () => {
      const version = this.accountGeneration; const ticket = await this.repository().issueRecovery(); this.checkAccount(version);
      if (!ticket.recoveryCode || !Number.isFinite(ticket.expiresInSeconds) || ticket.expiresInSeconds <= 0) { throw new ApiError(500, 'INVALID_RESPONSE'); }
      this.recoveryTicket = ticket; this.recoveryExpiresAt = Date.now() + ticket.expiresInSeconds * 1000;
    });
  }
  async scan(): Promise<ActionResult> { return await this.run(async () => { await this.previewRawQr(await this.services().scan()); }); }
  private async previewRawQr(content: string): Promise<void> {
    const parsed = parseQr(content, this.environment.webOrigin);
    if (!parsed.transfer && !this.authenticated) {
      this.pendingRawQr = content; this.notice = '请先登录，登录后将继续确认扫码请求'; return;
    }
    const version = this.accountGeneration;
    const preview = await this.repository().previewQr(parsed.code, parsed.transfer); this.checkAccount(version);
    if (!preview || !Number.isFinite(preview.expiresInSeconds) || preview.expiresInSeconds <= 0) { throw new UserFacingError('二维码已过期，请重新扫描'); }
    const previewExpiresAt = Date.now() + preview.expiresInSeconds * 1000;
    const flow = qrFlow(preview.codeType, parsed.transfer);
    if (flow === 'consent') {
      if (!preview.authorizationTicket) { throw new ApiError(500, 'INVALID_RESPONSE'); }
      const context = await this.repository().consentContext(preview.authorizationTicket); this.checkAccount(version);
      if (context.ticket !== preview.authorizationTicket || context.qr !== true || context.nativeAuthorization === true || !Number.isFinite(context.expiresInSeconds) || context.expiresInSeconds <= 0) { throw new UserFacingError('此请求不是有效的网页扫码授权'); }
      this.consent = context; this.consentExpiresAt = Math.min(previewExpiresAt, Date.now() + context.expiresInSeconds * 1000);
      this.qr = null; this.showChallenge('consent'); return;
    }
    this.qr = { code: parsed.code, transfer: parsed.transfer, preview: preview, expiresAt: previewExpiresAt };
    this.showChallenge('qr');
  }
  async confirmQr(): Promise<ActionResult> {
    return await this.run(async () => {
      const qr = this.qr;
      if (!qr || qr.expiresAt <= Date.now()) { this.cancelChallenge(); throw new UserFacingError('二维码已过期，请重新扫描'); }
      if (qr.transfer) {
        const staged = this.newStaged(); const attempt = ++this.authGeneration;
        this.staged = staged;
        const token = await staged.exchangeTransfer(qr.code); await this.finishAuthentication(token.accessToken ?? '', staged, attempt);
        this.notice = '此设备已登录账号';
      } else {
        const version = this.accountGeneration;
        await this.repository().approveQr(qr.code, qr.preview.codeType === 'approve_official'); this.checkAccount(version);
        this.qr = null; this.closeChallenge(); this.notice = '网页请求已批准，请返回原浏览器继续';
      }
    });
  }
  async rejectQr(): Promise<ActionResult> {
    return await this.run(async () => {
      const qr = this.qr;
      if (qr && qr.preview.codeType === 'approve_official' && qr.expiresAt > Date.now()) {
        const version = this.accountGeneration; await this.repository().approveQr(qr.code, true, false); this.checkAccount(version);
      }
      this.qr = null; this.closeChallenge(); this.notice = '已取消扫码请求';
    });
  }
  async decideConsent(approve: boolean, mode: string = 'PERSISTENT', ttl: number = 3600, loginWeb: boolean = false): Promise<ActionResult> {
    return await this.run(async () => {
      const context = this.consent;
      if (!context || context.qr !== true || context.nativeAuthorization === true) { throw new UserFacingError('此请求不是网页扫码授权'); }
      if (this.consentExpiresAt <= Date.now()) { this.cancelChallenge(); throw new UserFacingError('授权请求已过期，请重新扫码'); }
      if (!['PERSISTENT', 'ONE_TIME', 'TIME_LIMITED'].includes(mode) || mode === 'TIME_LIMITED' && ![300, 3600, 86400, 604800].includes(ttl)) { throw new UserFacingError('授权期限无效'); }
      const version = this.accountGeneration;
      await this.repository().decideConsent(context.ticket, approve, context.officialApplication ? 'PERSISTENT' : mode, ttl, loginWeb); this.checkAccount(version);
      this.consent = null; this.closeChallenge(); this.notice = approve ? '授权已完成，请返回原浏览器继续' : '已拒绝本次网页授权';
    });
  }
  async openRecovery(code: string = ''): Promise<ActionResult> {
    return await this.openWeb('/forgot-password' + (code.trim() ? '?recoveryCode=' + encodeURIComponent(code.trim()) + '&source=mobile' : ''));
  }
  recoveryUrl(): string {
    if (!this.recoveryTicket?.recoveryCode || this.recoveryExpiresAt <= Date.now()) { return ''; }
    return this.environment.webOrigin + '/forgot-password?recoveryCode=' +
      encodeURIComponent(this.recoveryTicket.recoveryCode) + '&source=mobile';
  }
  async copyText(text: string): Promise<ActionResult> {
    return await this.run(async () => {
      if (!text) { throw new UserFacingError('当前内容不可复制'); }
      await this.services().copyText(text); this.notice = '已复制到本机剪贴板';
    });
  }
  async openFeedback(): Promise<ActionResult> {
    return await this.openExternal('https://github.com/KsuserCN/Auth/issues');
  }
  async openFiling(): Promise<ActionResult> { return await this.openExternal('https://beian.miit.gov.cn/'); }
  async openLegal(kind: string): Promise<ActionResult> {
    return await this.openExternal('https://docs.ksuser.cn/agreement/' + (kind === 'privacy' ? 'privacy' :
      kind === 'third-party' || kind === 'sharing' ? 'third-party-information-sharing' : 'user'));
  }
  async verifyIdentity(): Promise<ActionResult> {
    return await this.protect('验证当前身份', async () => { await this.fetchSecurity(); this.notice = '身份验证已完成'; });
  }
  private async openWeb(path: string): Promise<ActionResult> {
    return await this.openExternal(this.environment.webOrigin + path);
  }
  private async openExternal(uri: string): Promise<ActionResult> {
    return await this.run(async () => {
      if (!this.context) { throw new UserFacingError('应用尚未初始化'); }
      await this.context.startAbility({ action: 'ohos.want.action.viewData', uri: uri });
    });
  }
}
