import { ApiClient, ApiError } from './ApiClient';
import { AdaptiveAuthStatus, ApiBody, ApiEnvelope, AuthPayload, AuthorizationReturn, BooleanResult,
  MobileAuthorizationContext, OfficialApp, PaginatedLogs, PasskeyAssertion, PasskeyAuthenticationOptions,
  PasskeyItem, PasskeyList, PasskeyRegistration, PasskeyRegistrationOptions, PasswordRequirement, QueryParam,
  QrPreview, RecoveryTicket, SensitiveLog, SensitiveStatus, SessionItem, ThirdPartyApp, TotpSetup, TotpStatus,
  UserProfile, UserSettings } from './Models';

export class Repository {
  readonly client: ApiClient;
  constructor(client: ApiClient) { this.client = client; }
  private post<T>(path: string, body: ApiBody = {}, auth: boolean = true): Promise<T> {
    return this.client.request<T>(path, 'POST', body, auth);
  }
  passwordRequirement(): Promise<PasswordRequirement> { return this.client.request<PasswordRequirement>('/info/password-requirement', 'GET', undefined, false); }
  validatePassword(password: string): Promise<Object> { return this.post<Object>('/info/validate-password', { password: password }, false); }
  checkUsername(username: string): Promise<BooleanResult> { return this.client.request<BooleanResult>('/auth/check-username', 'GET', undefined, false, [{ name: 'username', value: username.trim() }]); }
  sendCode(email: string, type: string): Promise<Object> { return this.post<Object>('/auth/send-code', { email: email.trim(), type: type }, !['login', 'register'].includes(type)); }
  login(email: string, password: string, codeMode: boolean = false): Promise<ApiEnvelope<AuthPayload>> {
    const body: ApiBody = { email: email.trim() };
    if (codeMode) { body.code = password.trim(); } else { body.password = password; }
    return this.client.envelope<AuthPayload>(codeMode ? '/auth/login-with-code' : '/auth/login', 'POST', body, false);
  }
  register(username: string, email: string, password: string, code: string): Promise<ApiEnvelope<AuthPayload>> {
    return this.client.envelope<AuthPayload>('/auth/register', 'POST', { username: username.trim(), email: email.trim(), password: password, code: code.trim() } as ApiBody, false);
  }
  currentUser(): Promise<UserProfile> { return this.client.request<UserProfile>('/auth/info', 'GET', undefined, true, [{ name: 'type', value: 'details' }]); }
  logout(all: boolean = false): Promise<Object> { return this.post<Object>(all ? '/auth/logout/all' : '/auth/logout'); }
  async updateProfile(key: string, value: string): Promise<UserProfile> {
    await this.post<Object>('/auth/update/profile', { key: key, value: key === 'username' ? value.trim() : value });
    return this.currentUser();
  }
  async uploadAvatar(data: ArrayBuffer, mimeType: string): Promise<UserProfile> {
    await this.client.upload<Object>('/auth/upload/avatar', data, mimeType); return this.currentUser();
  }
  updateSetting(field: string, value: boolean | string): Promise<UserSettings> {
    const body: ApiBody = { field: field };
    if (typeof value === 'boolean') { body.value = value; } else { body.stringValue = value; }
    return this.post<UserSettings>('/auth/update/setting', body);
  }
  sensitiveStatus(): Promise<SensitiveStatus> { return this.client.request<SensitiveStatus>('/auth/check-sensitive-verification'); }
  adaptiveStatus(): Promise<AdaptiveAuthStatus> { return this.client.request<AdaptiveAuthStatus>('/auth/adaptive-auth/status'); }
  verifySensitive(method: string, value: string, recovery: boolean): Promise<Object> {
    const body: ApiBody = { method: method };
    if (method === 'password') { body.password = value; }
    else if (recovery) { body.recoveryCode = value.trim().toUpperCase(); }
    else { body.code = value.trim(); }
    return this.post<Object>('/auth/verify-sensitive', body);
  }
  passkeyAuthenticationOptions(sensitive: boolean = false): Promise<PasskeyAuthenticationOptions> {
    return this.post<PasskeyAuthenticationOptions>(sensitive ? '/auth/passkey/sensitive-verification-options' : '/auth/passkey/authentication-options', {}, sensitive);
  }
  private assertionBody(payload: PasskeyAssertion): ApiBody {
    return { credentialRawId: payload.credentialRawId, clientDataJSON: payload.clientDataJSON,
      authenticatorData: payload.authenticatorData, signature: payload.signature };
  }
  loginPasskey(options: PasskeyAuthenticationOptions, payload: PasskeyAssertion): Promise<ApiEnvelope<AuthPayload>> {
    return this.client.envelope<AuthPayload>('/auth/passkey/authentication-verify', 'POST', this.assertionBody(payload), false,
      [{ name: 'challengeId', value: options.challengeId }]);
  }
  verifyMfa(challenge: string, value: string, recovery: boolean): Promise<AuthPayload> {
    const body: ApiBody = { challengeId: challenge };
    if (recovery) { body.recoveryCode = value.trim().toUpperCase(); } else { body.code = value.trim(); }
    return this.post<AuthPayload>('/auth/totp/mfa-verify', body, false);
  }
  verifyMfaPasskey(mfaChallengeId: string, options: PasskeyAuthenticationOptions, payload: PasskeyAssertion): Promise<AuthPayload> {
    const body = this.assertionBody(payload); body.mfaChallengeId = mfaChallengeId; body.passkeyChallengeId = options.challengeId;
    return this.post<AuthPayload>('/auth/passkey/mfa-verify', body, false);
  }
  verifySensitivePasskey(options: PasskeyAuthenticationOptions, payload: PasskeyAssertion): Promise<Object> {
    return this.client.request<Object>('/auth/passkey/sensitive-verification-verify', 'POST', this.assertionBody(payload), true,
      [{ name: 'challengeId', value: options.challengeId }]);
  }
  async passkeys(): Promise<PasskeyItem[]> { const value = await this.client.request<PasskeyList>('/auth/passkey/list'); return value.passkeys; }
  registrationOptions(name: string): Promise<PasskeyRegistrationOptions> { return this.post<PasskeyRegistrationOptions>('/auth/passkey/registration-options', { passkeyName: name.trim(), authenticatorType: 'auto' }); }
  registerPasskey(name: string, payload: PasskeyRegistration): Promise<Object> {
    return this.post<Object>('/auth/passkey/registration-verify', { passkeyName: name.trim(), credentialRawId: payload.credentialRawId,
      clientDataJSON: payload.clientDataJSON, attestationObject: payload.attestationObject, transports: payload.transports });
  }
  renamePasskey(id: number, name: string): Promise<Object> { return this.client.request<Object>('/auth/passkey/' + id + '/rename', 'PUT', { newName: name.trim() } as ApiBody); }
  deletePasskey(id: number): Promise<Object> { return this.client.request<Object>('/auth/passkey/' + id, 'DELETE'); }
  totpStatus(): Promise<TotpStatus> { return this.client.request<TotpStatus>('/auth/totp/status'); }
  setupTotp(): Promise<TotpSetup> { return this.post<TotpSetup>('/auth/totp/registration-options'); }
  enableTotp(code: string, recoveryCodes: string[]): Promise<Object> { return this.post<Object>('/auth/totp/registration-verify', { code: code.trim(), recoveryCodes: recoveryCodes }); }
  disableTotp(): Promise<Object> { return this.post<Object>('/auth/totp/disable'); }
  recoveryCodes(regenerate: boolean): Promise<string[]> {
    const empty: ApiBody = {};
    return this.client.request<string[]>(regenerate ? '/auth/totp/recovery-codes/regenerate' : '/auth/totp/recovery-codes', regenerate ? 'POST' : 'GET', regenerate ? empty : undefined);
  }
  async changeEmail(email: string, code: string): Promise<UserProfile> {
    await this.post<Object>('/auth/update/email', { newEmail: email.trim(), code: code.trim() }); return this.currentUser();
  }
  changePassword(password: string): Promise<Object> { return this.post<Object>('/auth/update/password', { newPassword: password }); }
  deleteAccount(confirmText: string): Promise<Object> { return this.post<Object>('/auth/delete', { confirmText: confirmText }); }
  sessions(): Promise<SessionItem[]> { return this.client.request<SessionItem[]>('/auth/sessions'); }
  revokeSession(id: number): Promise<Object> { return this.post<Object>('/auth/sessions/' + id + '/revoke'); }
  logs(page: number, result: string, operation: string, startDate: string, endDate: string): Promise<PaginatedLogs> {
    const query: QueryParam[] = [{ name: 'page', value: String(page) }, { name: 'pageSize', value: '20' }];
    if (result) { query.push({ name: 'result', value: result }); }
    if (operation) { query.push({ name: 'operationType', value: operation }); }
    if (startDate) { query.push({ name: 'startDate', value: startDate }); }
    if (endDate) { query.push({ name: 'endDate', value: endDate }); }
    return this.client.request<PaginatedLogs>('/auth/sensitive-logs', 'GET', undefined, true, query);
  }
  log(id: number): Promise<SensitiveLog> { return this.client.request<SensitiveLog>('/auth/sensitive-logs/' + id); }
  officialApps(): Promise<OfficialApp[]> { return this.client.request<OfficialApp[]>('/official/authorizations'); }
  thirdPartyApps(): Promise<ThirdPartyApp[]> { return this.client.request<ThirdPartyApp[]>('/oauth2/authorizations'); }
  revokeApplication(id: string, official: boolean): Promise<Object> {
    return this.client.request<Object>((official ? '/official/authorizations/' : '/oauth2/authorizations/') + encodeURIComponent(id), 'DELETE');
  }
  issueRecovery(): Promise<RecoveryTicket> { return this.post<RecoveryTicket>('/auth/account-recovery/issue'); }
  previewQr(code: string, transfer: boolean): Promise<QrPreview> {
    return this.client.request<QrPreview>('/auth/qr/preview', 'GET', undefined, false,
      [{ name: transfer ? 'transferCode' : 'approveCode', value: code }]);
  }
  exchangeTransfer(code: string): Promise<AuthPayload> { return this.post<AuthPayload>('/auth/session-transfer/exchange', { transferCode: code, target: 'mobile' }, false); }
  approveQr(code: string, official: boolean = false, approve: boolean = true): Promise<Object> {
    return this.post<Object>(official ? '/auth/qr/official/' + (approve ? 'approve' : 'reject') : '/auth/qr/approve', { approveCode: code });
  }
  consentContext(ticket: string): Promise<MobileAuthorizationContext> {
    return this.client.request<MobileAuthorizationContext>('/auth/mobile-authorization/context', 'GET', undefined, true, [{ name: 'ticket', value: ticket }]);
  }
  decideConsent(ticket: string, approve: boolean, mode: string, ttl: number, loginWeb: boolean): Promise<AuthorizationReturn> {
    const body: ApiBody = { ticket: ticket, approve: approve, grantMode: mode, loginWeb: approve && loginWeb };
    if (mode === 'TIME_LIMITED') { body.grantTtlSeconds = ttl; }
    return this.post<AuthorizationReturn>('/auth/mobile-authorization/decide', body);
  }
}
