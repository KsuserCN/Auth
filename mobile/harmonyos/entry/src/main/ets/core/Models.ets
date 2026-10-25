export class UserFacingError extends Error { constructor(message: string) { super(message); } }
export interface ActionResult { status: 'success' | 'cancelled' | 'failure'; error?: string; }
export interface ResourceState { status: 'idle' | 'loading' | 'loaded' | 'error'; error: string; hasData: boolean; }
export function emptyResourceState(): ResourceState { return { status: 'idle', error: '', hasData: false }; }
export interface ApiEnvelope<T> { code: number; errorCode?: string; params?: Object; data?: T; }
export interface QueryParam { name: string; value: string; }
export interface StoredCookie { name: string; value: string; domain: string; path: string; secure: boolean; hostOnly: boolean; expiresAt?: number; }
export interface SessionSnapshot { apiBaseURL?: string; accessToken: string; cookies: StoredCookie[]; profile: UserProfile | null; }
export interface SessionStore { load(): Promise<SessionSnapshot | null>; save(value: SessionSnapshot): Promise<void>; clear(): Promise<void>; }
export interface UserSettings {
  mfaEnabled: boolean; detectUnusualLogin: boolean; notifySensitiveActionEmail: boolean; subscribeNewsEmail: boolean;
  preferredMfaMethod?: string; preferredSensitiveMethod?: string;
}
export interface UserProfile {
  uuid: string; username: string; email: string; avatarUrl?: string; realName?: string; gender?: string;
  birthDate?: string; region?: string; bio?: string; verificationType?: string; updatedAt?: string;
  settings?: UserSettings; hasPassword?: boolean;
}
export interface PasswordRequirement {
  minLength: number; maxLength: number; requireUppercase: boolean; requireLowercase: boolean;
  requireDigits: boolean; requireSpecialChars: boolean; rejectCommonWeakPasswords?: boolean;
  requirementCode?: string; requirementParams?: Object;
}
export interface AuthPayload { accessToken?: string; challengeId?: string; method?: string; methods?: string[]; }
export interface MfaChallenge { challengeId: string; method: string; methods: string[]; }
export interface PasskeyAuthenticationOptions {
  challenge: string; challengeId: string; rpId: string; timeout?: number | string;
  allowCredentials?: string | CredentialDescriptor[]; userVerification?: string;
}
export interface CredentialDescriptor { id: string; type: string; transports?: string[]; }
export interface CredentialParameter { type: string; alg: number; }
export interface RpEntity { id: string; name: string; }
export interface UserEntity { id: string; name: string; displayName: string; }
export interface AuthenticatorSelection { authenticatorAttachment?: string; residentKey?: string; requireResidentKey?: boolean; userVerification?: string; }
export interface PasskeyRegistrationOptions {
  challenge: string; rp: string | RpEntity; user: string | UserEntity; pubKeyCredParams: string | CredentialParameter[];
  timeout?: number | string; attestation?: string; authenticatorSelection?: string | AuthenticatorSelection;
}
export interface PasskeyAssertion { credentialRawId: string; clientDataJSON: string; authenticatorData: string; signature: string; }
export interface PasskeyRegistration { credentialRawId: string; clientDataJSON: string; attestationObject: string; transports: string; }
export interface PasskeyItem { id: number; name: string; transports?: string; lastUsedAt?: string; createdAt: string; }
export interface PasskeyList { passkeys: PasskeyItem[]; }
export interface TotpStatus { enabled: boolean; recoveryCodesCount: number; }
export interface TotpSetup { secret: string; qrCodeUrl: string; recoveryCodes: string[]; }
export interface SensitiveStatus { verified: boolean; remainingSeconds: number; preferredMethod?: string; methods?: string[]; }
export interface AdaptiveAuthStatus {
  sessionId?: number; riskScore: number; riskLevel: string; policyDecision?: string; trusted: boolean;
  requiresStepUp: boolean; sessionFrozen: boolean; sensitiveVerified: boolean; sensitiveVerificationRemainingSeconds: number;
  currentIp?: string; currentLocation?: string; sessionLocation?: string; multiEndpointAlert: boolean;
  authAgeSeconds?: number; idleSeconds?: number; browser?: string; deviceType?: string;
  sessionIp?: string; alertLevel?: string; alertRemainingSeconds?: number; policyVersion?: string;
  alertTitleCode?: string; alertMessageCode?: string; recommendedActionCode?: string; reasonCodes?: ApiResult[];
}
export interface ApiResult { errorCode?: string; params?: Object; }
export interface SessionItem {
  id: number; ipAddress: string; ipLocation?: string; userAgent?: string; browser?: string; deviceType?: string;
  createdAt: string; lastSeenAt: string; expiresAt: string; revokedAt?: string; online: boolean; current: boolean;
}
export interface SensitiveLog {
  id: number; operationType: string; loginMethod?: string; loginMethods?: string[]; ipAddress: string;
  ipLocation?: string; browser?: string; deviceType?: string; result: string; failureReason?: string;
  riskScore: number; actionTaken?: string; durationMs?: number; createdAt: string;
  triggeredMultiErrorLock?: boolean; triggeredRateLimitLock?: boolean;
}
export interface PaginatedLogs { data: SensitiveLog[]; page: number; pageSize: number; total: number; totalPages: number; }
export interface OfficialApp { clientId: string; clientName: string; logoUrl?: string; scopes: string[]; authorizedAt?: string; lastAuthorizedAt?: string; }
export interface ThirdPartyApp {
  appId: string; appName: string; logoUrl?: string; creatorName?: string; contactInfo?: string; redirectUri?: string;
  creatorVerificationType?: string;
  scopes: string[]; authorizedAt?: string; lastAuthorizedAt?: string; grantMode: string; expiresAt?: string;
}
export interface QrPreview {
  codeType: string; clientName?: string; browser?: string; system?: string; ipAddress?: string; ipLocation?: string;
  expiresInSeconds: number; targetName?: string; targetOrigin?: string; targetLogoUrl?: string;
  requestedScopes?: string[]; consoleApplication?: boolean; authorizationTicket?: string;
}
export interface ParsedQr { code: string; transfer: boolean; }
export interface QrConfirmation { code: string; transfer: boolean; preview: QrPreview; expiresAt: number; }
export interface MobileAuthorizationContext {
  ticket: string; appName: string; logoUrl?: string; appLogoUrl?: string; contactInfo?: string; redirectUri: string;
  requestedScopes: string[]; alreadyAuthorized: boolean; existingGrantMode: string; expiresInSeconds: number;
  nativeAuthorization?: boolean; qr?: boolean; officialApplication?: boolean; consoleApplication?: boolean;
  webOrigin?: string; subjectName?: string; subjectVerificationType?: string;
}
export interface AuthorizationReturn { returnUrl?: string; completed?: boolean; }
export interface RecoveryTicket {
  recoveryCode?: string; expiresInSeconds: number; username?: string; maskedEmail?: string;
  sponsorClientName?: string; sponsorBrowser?: string; sponsorSystem?: string; sponsorIpLocation?: string;
}
export interface AvatarUpload { data: ArrayBuffer; mimeType: string; }
export interface AvatarSource { data: ArrayBuffer; width: number; height: number; }
export interface AvatarCropRegion { x: number; y: number; size: number; }
export interface BooleanResult { success?: boolean; exists?: boolean; }
export interface ApiBody {
  email?: string; password?: string; newPassword?: string; newEmail?: string; username?: string; code?: string;
  recoveryCode?: string; challengeId?: string; mfaChallengeId?: string; passkeyChallengeId?: string;
  passkeyName?: string; authenticatorType?: string; credentialRawId?: string; clientDataJSON?: string;
  authenticatorData?: string; signature?: string; attestationObject?: string; transports?: string;
  newName?: string; method?: string; type?: string; key?: string; value?: string | boolean;
  field?: string; stringValue?: string; confirmText?: string; approveCode?: string; transferCode?: string;
  target?: string; recoveryCodes?: string[]; ticket?: string; approve?: boolean; grantMode?: string;
  grantTtlSeconds?: number; loginWeb?: boolean;
}
export function emptySettings(): UserSettings {
  return { mfaEnabled: false, detectUnusualLogin: true, notifySensitiveActionEmail: true, subscribeNewsEmail: false };
}
export function profileRealName(user: UserProfile | null): string {
  if (!user) { return ''; }
  const name = (user.realName ?? '').trim();
  return name && !['无', '未设置', '暂无', 'null', '-'].includes(name.toLowerCase()) ? name : '';
}
export function displayName(user: UserProfile | null): string {
  return user ? profileRealName(user) || user.username : '';
}
export function profileCompleteness(user: UserProfile | null): number {
  if (!user) { return 0; }
  return [user.username, user.avatarUrl ?? '', profileRealName(user), user.region ?? '', user.bio ?? '']
    .filter((value: string) => value.trim().length > 0).length * 20;
}
function pad(value: number): string { return value.toString().padStart(2, '0'); }
export function dateGroup(value: string | undefined): string {
  if (!value) { return '未知日期'; }
  if (/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) {
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) { return '未知日期'; }
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  }
  return /^\d{4}-\d{2}-\d{2}(?:$|[T ])/.test(value) ? value.slice(0, 10) : '未知日期';
}
export function dateText(value: string | undefined): string {
  if (!value) { return '—'; }
  if (/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)) {
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) { return '—'; }
    return dateGroup(value) + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
  }
  return /^\d{4}-\d{2}-\d{2}(?:$|[T ])/.test(value) ? value.replace('T', ' ').slice(0, 19) : '—';
}
export function validEmail(value: string): boolean { return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value.trim()); }
export function validUsername(value: string): boolean { return /^[A-Za-z0-9_\-\u4E00-\u9FFF]{3,20}$/.test(value.trim()); }
export function passwordRequirementText(policy: PasswordRequirement | null): string {
  if (!policy) { return '正在获取密码要求…'; }
  const rules: string[] = ['长度 ' + policy.minLength + '–' + policy.maxLength + ' 位'];
  if (policy.requireUppercase) { rules.push('大写字母'); }
  if (policy.requireLowercase) { rules.push('小写字母'); }
  if (policy.requireDigits) { rules.push('数字'); }
  if (policy.requireSpecialChars) { rules.push('特殊字符 @$!%*?&'); }
  if (policy.rejectCommonWeakPasswords) { rules.push('避免常见弱密码'); }
  return rules.join('；');
}
export function passwordError(value: string, policy: PasswordRequirement | null): string {
  if (!policy) { return '密码规则尚未加载，请重试'; }
  if (value.length < policy.minLength || value.length > policy.maxLength) { return '密码需要 ' + policy.minLength + '–' + policy.maxLength + ' 个字符'; }
  if (/[\r\n]/.test(value)) { return '密码不能包含换行符'; }
  if (policy.requireUppercase && !/[A-Z]/.test(value)) { return '密码需包含大写字母'; }
  if (policy.requireLowercase && !/[a-z]/.test(value)) { return '密码需包含小写字母'; }
  if (policy.requireDigits && !/[0-9]/.test(value)) { return '密码需包含数字'; }
  if (policy.requireSpecialChars && !/[@$!%*?&]/.test(value)) { return '密码需包含特殊字符 @$!%*?&'; }
  return '';
}
