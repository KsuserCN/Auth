// Production AppModel state-flow tests. ArkUI observation and system adapters are substituted; core/repository code is real.
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { setImmediate as nextTurn } from 'node:timers/promises'

const require = createRequire(import.meta.url)
const ts = require(process.env.HARMONY_TYPESCRIPT_PATH ?? '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript')
const sourceRoot = fileURLToPath(new URL('../../main/ets/core/', import.meta.url))
class Environment {
  apiBaseURL = 'https://api.ksuser.cn'; webOrigin = 'https://auth.ksuser.cn'; passkeyRPID = 'auth.ksuser.cn'
  userAgent = 'KsuserAuthMobile/1.0 (HarmonyOS)'; debug = true
  static async load() { return new Environment() }
}
class SessionStorageError extends Error { constructor(kind) { super('storage ' + kind); this.kind = kind } }
class NativeHttpTransport { async send() { throw new Error('Unsubstituted native transport') } }
const replacements = new Map([
  ['./Environment', { AppEnvironment: Environment }], ['./HttpTransport', { NativeHttpTransport }],
  ['../native/SecureSessionStore', { SecureSessionStore: class {}, SessionStorageError }],
  ['../native/NativeServices', { NativeServices: class {} }]
])
const modules = new Map()
async function load(filename) {
  if (modules.has(filename)) return modules.get(filename)
  const code = ts.transpileModule(await fs.readFile(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true }
  }).outputText
  const dependencies = new Map()
  for (const match of code.matchAll(/require\("(\.[^"]+)"\)/g)) {
    dependencies.set(match[1], replacements.get(match[1]) ?? await load(path.resolve(path.dirname(filename), match[1] + '.ets')))
  }
  const module = { exports: {} }
  new Function('require', 'module', 'exports', 'Observed', code)(name => {
    if (dependencies.has(name)) return dependencies.get(name)
    throw new Error('Unexpected AppModel dependency: ' + name)
  }, module, module.exports, constructor => constructor)
  modules.set(filename, module.exports)
  return module.exports
}
const { AppModel } = await load(path.join(sourceRoot, 'AppModel.ets'))
const { ApiClient, RequestCancelled } = await load(path.join(sourceRoot, 'ApiClient.ets'))
const { Repository } = await load(path.join(sourceRoot, 'Repository.ets'))
const copy = value => value == null ? value : JSON.parse(JSON.stringify(value))
const oldProfile = { uuid: 'old-account', username: 'old-user', email: 'old@example.com', realName: '旧姓名',
  bio: '原简介', region: '上海', gender: 'secret', birthDate: '2000-01-02', avatarUrl: 'https://images.example/old.jpg' }
const newProfile = { uuid: 'new-account', username: 'new-user', email: 'new@example.com', realName: '新姓名',
  bio: '完整新简介', region: '杭州', gender: 'secret', birthDate: '2001-02-03', avatarUrl: 'https://images.example/new.jpg' }
function snapshot() { return { accessToken: 'old-token', cookies: [
  { name: 'XSRF-TOKEN', value: 'old-csrf', domain: 'api.ksuser.cn', path: '/', hostOnly: true, secure: true },
  { name: 'refreshToken', value: 'old-refresh', domain: 'api.ksuser.cn', path: '/auth', hostOnly: true, secure: true }
], profile: copy(oldProfile) } }
function reply(data = {}, status = 200, code = status, cookies = []) {
  return { status, cookies, body: JSON.stringify({ code, errorCode: status >= 400 ? 'REQUEST_FAILED' : 'REQUEST_SUCCESS', data }) }
}
function deferred() {
  let resolve
  const promise = new Promise(yes => { resolve = yes })
  return { promise, resolve }
}
async function until(predicate) {
  for (let index = 0; index < 50; index++) { if (predicate()) return; await nextTurn() }
  throw new Error('Expected asynchronous phase was never reached')
}
const endpoint = request => new URL(request.url).pathname
async function fixture(handler = async () => reply(), authenticated = true) {
  const state = { stored: authenticated ? snapshot() : null, scanRaw: 'KSUSER-AUTH-QR:approve-code', openedURLs: [] }
  const store = { async load() { return copy(state.stored) }, async save(value) { state.stored = copy(value) }, async clear() { state.stored = null } }
  const calls = []
  const transport = { async send(request) {
    calls.push({ ...request, headers: { ...request.headers } })
    return handler(request, calls)
  } }
  const client = new ApiClient(new Environment(), store, transport); await client.restore()
  const model = new AppModel()
  model.ready = true; model.initialized = true; model.client = client; model.repositoryValue = new Repository(client)
  model.transport = transport; model.authenticated = authenticated; model.profile = authenticated ? copy(oldProfile) : null
  model.context = { async startAbility(want) { state.openedURLs.push(want.uri) } }
  model.native = { async scan() { return state.scanRaw }, readLastOrigin() { return '' }, async authenticate() { throw new Error('操作已取消') } }
  client.onUnauthorized = () => { model.authGeneration++; model.clearAccountData() }
  return { model, client, state, calls }
}
function consent(overrides = {}) {
  return { ticket: 'website-ticket', appName: '测试网页应用', redirectUri: 'https://service.example/callback', requestedScopes: ['openid', 'profile', 'email'],
    alreadyAuthorized: false, existingGrantMode: 'PERSISTENT', expiresInSeconds: 60, qr: true, nativeAuthorization: false, ...overrides }
}
function applicationPreview(overrides = {}) {
  return { codeType: 'approve_application', expiresInSeconds: 60, authorizationTicket: 'website-ticket', ...overrides }
}
function stubConsentRepository(model, context = consent(), preview = applicationPreview()) {
  const calls = []
  model.repositoryValue = {
    async previewQr(...args) { calls.push(['preview', ...args]); return preview },
    async consentContext(...args) { calls.push(['context', ...args]); return context },
    async decideConsent(...args) { calls.push(['decide', ...args]); return { returnUrl: 'https://third-party.example/callback?code=do-not-open' } }
  }
  return calls
}

// The live account must survive every incomplete authentication attempt.
test('failed staged login does not replace the live profile, cookies or durable session', async () => {
  const h = await fixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (endpoint(request) === '/auth/login') {
      assert.equal(request.headers.Authorization, undefined)
      assert.equal(request.headers.Cookie, 'XSRF-TOKEN=staged-csrf')
      return reply({ accessToken: 'pending-token' }, 200, 200, ['refreshToken=pending-refresh; Path=/auth; Secure'])
    }
    assert.equal(endpoint(request), '/auth/info')
    assert.equal(request.headers.Authorization, 'Bearer pending-token')
    return reply({}, 503)
  })
  await h.model.login('new@example.com', 'password')
  assert.equal(h.model.authenticated, true)
  assert.deepEqual(h.model.profile, oldProfile)
  assert.deepEqual(h.client.snapshot(), snapshot())
  assert.deepEqual(h.state.stored, snapshot())
  assert.ok(h.model.error)
})

test('MFA stages only supported methods and cancellation leaves the current account untouched', async () => {
  const h = await fixture(async request => endpoint(request) === '/auth/csrf-token'
    ? reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    : reply({ challengeId: 'mfa-one', method: 'apple', methods: ['apple', 'totp', 'passkey', 'qq'] }, 200, 201))
  await h.model.login('new@example.com', 'password')
  assert.equal(h.model.challengeKind, 'mfa')
  assert.deepEqual(h.model.mfa, { challengeId: 'mfa-one', method: 'totp', methods: ['totp', 'passkey'] })
  assert.deepEqual(h.client.snapshot(), snapshot())
  h.model.cancelChallenge()
  assert.equal(h.model.challengeVisible, false)
  assert.equal(h.model.mfa, null)
  assert.equal(h.model.staged, null)
  assert.deepEqual(h.model.profile, oldProfile)
  assert.deepEqual(h.state.stored, snapshot())
})

test('cancelling MFA while verification is in flight discards a late successful token', async () => {
  const gate = deferred()
  const h = await fixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (endpoint(request) === '/auth/login') return reply({ challengeId: 'mfa-one', method: 'totp', methods: ['totp'] }, 200, 201)
    if (endpoint(request) === '/auth/totp/mfa-verify') { await gate.promise; return reply({ accessToken: 'late-token' }) }
    throw new Error('Late MFA must not fetch or adopt this profile')
  })
  await h.model.login('new@example.com', 'password')
  const verifying = h.model.verifyMfa('123456')
  await until(() => h.calls.some(request => endpoint(request) === '/auth/totp/mfa-verify'))
  h.model.cancelChallenge(); gate.resolve(); await verifying
  assert.deepEqual(h.model.profile, oldProfile)
  assert.deepEqual(h.state.stored, snapshot())
  assert.equal(h.model.error, '')
  assert.equal(h.model.busy, false)
})

test('successful login adopts only after the complete profile is read', async () => {
  const profileGate = deferred()
  const h = await fixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (endpoint(request) === '/auth/login') return reply({ accessToken: 'new-token' }, 200, 200, ['refreshToken=new-refresh; Path=/auth; Secure'])
    if (endpoint(request) === '/auth/info') { await profileGate.promise; return reply(newProfile) }
    throw new Error('Unexpected request')
  })
  const loggingIn = h.model.login('new@example.com', 'password')
  await until(() => h.calls.some(request => endpoint(request) === '/auth/info'))
  assert.deepEqual(h.model.profile, oldProfile)
  assert.equal(h.client.snapshot().accessToken, 'old-token')
  profileGate.resolve(); await loggingIn
  assert.deepEqual(h.model.profile, newProfile)
  assert.equal(h.client.snapshot().accessToken, 'new-token')
  assert.equal(h.state.stored.cookies.find(cookie => cookie.name === 'refreshToken').value, 'new-refresh')
  assert.equal(h.model.notice, '登录成功')
})

test('malformed authenticated profile produces a local error without silently completing login', async () => {
  const h = await fixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (endpoint(request) === '/auth/login') return reply({ accessToken: 'pending-token' })
    return reply({ username: 'missing-identity' })
  })
  await h.model.login('new@example.com', 'password')
  assert.deepEqual(h.model.profile, oldProfile)
  assert.equal(h.client.snapshot().accessToken, 'old-token')
  assert.ok(h.model.error, 'Malformed profile must not be reported as an ordinary user cancellation')
})

test('logout clears all account-owned state and late profile fetch cannot restore it', async () => {
  const gate = deferred()
  const h = await fixture()
  h.model.repositoryValue = { async currentUser() { await gate.promise; return newProfile }, async logout() {} }
  h.model.sessions = [{ id: 1 }]; h.model.logs = [{ id: 2 }]; h.model.passkeys = [{ id: 3 }]
  h.model.officialApps = [{ clientId: 'app' }]; h.model.thirdPartyApps = [{ appId: 'third' }]
  h.model.recoveryCodes = ['one']; h.model.avatarPreview = { data: new ArrayBuffer(1), mimeType: 'image/jpeg' }
  const fetching = h.model.fetchProfile()
  const rejection = assert.rejects(fetching, error => error instanceof RequestCancelled)
  await h.model.logout(); gate.resolve(); await rejection
  assert.equal(h.model.authenticated, false); assert.equal(h.model.profile, null)
  assert.deepEqual([h.model.sessions, h.model.logs, h.model.passkeys, h.model.officialApps, h.model.thirdPartyApps, h.model.recoveryCodes], [[], [], [], [], [], []])
  assert.equal(h.model.avatarPreview, null); assert.equal(h.state.stored, null)
})

test('profile update ignores compact mutation data and replaces state with the subsequent complete profile', async () => {
  const complete = { ...oldProfile, bio: '新版完整资料', gender: 'female', region: '北京' }
  const h = await fixture(async request => endpoint(request) === '/auth/update/profile'
    ? reply({ username: 'compact-mutation-only' }) : reply(complete))
  await h.model.saveProfile('bio', '新版完整资料')
  assert.deepEqual(h.calls.map(endpoint), ['/auth/update/profile', '/auth/info'])
  assert.deepEqual(h.model.profile, complete)
  assert.deepEqual(h.state.stored.profile, complete)
  assert.equal(h.model.profile.birthDate, oldProfile.birthDate)
})

for (const [description, overrides] of [
  ['qr=false', { qr: false }], ['missing qr flag', { qr: undefined }], ['nativeAuthorization=true', { nativeAuthorization: true }],
  ['ticket mismatch', { ticket: 'different-ticket' }], ['expired lifetime', { expiresInSeconds: 0 }], ['negative lifetime', { expiresInSeconds: -1 }],
  ['missing lifetime', { expiresInSeconds: undefined }]
]) {
  test('website consent context rejects ' + description, async () => {
    const h = await fixture(); const calls = stubConsentRepository(h.model, consent(overrides))
    await h.model.scan()
    assert.equal(h.model.consent, null)
    assert.equal(h.model.challengeVisible, false)
    assert.ok(h.model.error)
    assert.equal(calls.some(call => call[0] === 'decide'), false)
  })
}

test('expired preview is rejected before requesting an application context', async () => {
  const h = await fixture(); const calls = stubConsentRepository(h.model, consent(), applicationPreview({ expiresInSeconds: 0 }))
  await h.model.scan()
  assert.equal(h.model.consent, null)
  assert.equal(h.model.qr, null)
  assert.deepEqual(calls.map(call => call[0]), ['preview'])
  assert.match(h.model.error, /过期/)
})

test('website consent defaults loginWeb to false and leaves callback navigation to the original browser', async () => {
  const h = await fixture(); const calls = stubConsentRepository(h.model)
  await h.model.scan()
  assert.equal(h.model.challengeKind, 'consent')
  await h.model.decideConsent(true)
  assert.deepEqual(calls.find(call => call[0] === 'decide'), ['decide', 'website-ticket', true, 'PERSISTENT', 3600, false])
  assert.equal(h.model.consent, null); assert.equal(h.model.challengeVisible, false)
  assert.deepEqual(h.state.openedURLs, [])
})

test('a consent that expires while the user reviews it cannot be approved', async () => {
  const h = await fixture(); const calls = stubConsentRepository(h.model)
  await h.model.scan(); h.model.consentExpiresAt = Date.now() - 1
  await h.model.decideConsent(true)
  assert.equal(calls.some(call => call[0] === 'decide'), false)
  assert.equal(h.model.consent, null); assert.equal(h.model.challengeVisible, false)
  assert.match(h.model.error, /过期/)
})

test('unsigned native app contexts cannot be submitted even if inserted after preview', async () => {
  const h = await fixture(); const calls = stubConsentRepository(h.model)
  h.model.consent = consent({ nativeAuthorization: true }); h.model.consentExpiresAt = Date.now() + 60000
  await h.model.decideConsent(true)
  assert.equal(calls.some(call => call[0] === 'decide'), false)
  assert.match(h.model.error, /网页扫码/)
})

test('an unsigned-in website scan is retained and resumed after a successful login', async () => {
  const h = await fixture(async request => {
    const pathname = endpoint(request)
    if (pathname === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (pathname === '/auth/login') return reply({ accessToken: 'new-token' }, 200, 200, ['refreshToken=new-refresh; Path=/auth; Secure'])
    if (pathname === '/auth/info') return reply(newProfile)
    if (pathname === '/auth/qr/preview') return reply(applicationPreview())
    if (pathname === '/auth/mobile-authorization/context') {
      assert.equal(request.headers.Authorization, 'Bearer new-token')
      return reply(consent())
    }
    throw new Error('Unexpected request: ' + pathname)
  }, false)
  await h.model.scan()
  assert.equal(h.calls.length, 0)
  assert.equal(h.model.pendingRawQr, h.state.scanRaw)
  assert.match(h.model.notice, /先登录/)
  await h.model.login('new@example.com', 'password')
  assert.equal(h.model.authenticated, true)
  assert.equal(h.model.profile.uuid, 'new-account')
  assert.equal(h.model.challengeKind, 'consent')
  assert.equal(h.model.consent.ticket, 'website-ticket')
  assert.equal(h.model.pendingRawQr, '')
})

test('transfer QR previews before switching and exchanges into an isolated mobile session', async () => {
  const h = await fixture(async request => {
    const pathname = endpoint(request)
    if (pathname === '/auth/qr/preview') return reply({ codeType: 'transfer', expiresInSeconds: 60, clientName: '网页账号' })
    if (pathname === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged-csrf; Path=/; Secure'])
    if (pathname === '/auth/session-transfer/exchange') {
      assert.deepEqual(JSON.parse(request.body), { transferCode: 'transfer-code', target: 'mobile' })
      assert.equal(request.headers.Authorization, undefined)
      assert.doesNotMatch(request.headers.Cookie, /old-refresh/)
      return reply({ accessToken: 'new-token' }, 200, 200, ['refreshToken=new-refresh; Path=/auth; Secure'])
    }
    if (pathname === '/auth/info') return reply(newProfile)
    throw new Error('Unexpected request: ' + pathname)
  })
  h.state.scanRaw = 'KSUSER-AUTH-XFER:v1:transfer-code'
  await h.model.scan()
  assert.equal(h.model.challengeKind, 'qr')
  assert.deepEqual(h.model.profile, oldProfile)
  assert.equal(h.calls.some(request => endpoint(request) === '/auth/session-transfer/exchange'), false)
  await h.model.confirmQr()
  assert.deepEqual(h.model.profile, newProfile)
  assert.equal(h.client.snapshot().accessToken, 'new-token')
})

test('protected email code countdown state changes only after the deferred send succeeds', async () => {
  const h = await fixture()
  let sent = 0
  h.model.repositoryValue = {
    async sensitiveStatus() { return { verified: false, remainingSeconds: 0, methods: ['password'], preferredMethod: 'password' } },
    async verifySensitive() {},
    async sendCode(email, type) { assert.equal(email, ' New@Example.com '); assert.equal(type, 'change-email'); sent++ }
  }
  const sending = h.model.sendCode(' New@Example.com ', 'change-email')
  await until(() => h.model.challengeVisible)
  assert.equal(h.model.challengeKind, 'sensitive')
  assert.equal(h.model.codeSentAt, 0); assert.equal(sent, 0)
  await h.model.verifySensitive('password', 'secret')
  assert.equal((await sending).status, 'success')
  assert.equal(sent, 1)
  assert.equal(h.model.codeSentType, 'change-email')
  assert.equal(h.model.codeSentEmail, 'new@example.com')
  assert.ok(h.model.codeSentAt > 0)
})

test('page loads wait for the active operation instead of being silently discarded', async () => {
  const gate = deferred(); let sessionReads = 0
  const h = await fixture()
  h.model.repositoryValue = {
    async currentUser() { await gate.promise; return oldProfile },
    async sessions() { sessionReads++; return [{ id: 41, current: true }] }
  }
  const profileRead = h.model.loadProfile()
  await until(() => h.model.busy)
  const sessionsRead = h.model.loadSessions()
  assert.equal(sessionReads, 0)
  gate.resolve(); await profileRead; await sessionsRead
  assert.equal(sessionReads, 1)
  assert.equal(h.model.sessions[0].id, 41)
})

test('an account without email can authenticate through an existing credential', async () => {
  const profile = { ...newProfile, email: '' }
  const h = await fixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') return reply({}, 200, 200, ['XSRF-TOKEN=staged; Path=/; Secure'])
    if (endpoint(request) === '/auth/login') return reply({ accessToken: 'new-token' })
    return reply(profile)
  })
  await h.model.login('login@example.com', 'password')
  assert.equal(h.model.profile.uuid, 'new-account')
  assert.equal(h.model.profile.email, '')
  assert.equal(h.model.error, '')
})

test('unexpected runtime failures use local feedback instead of exposing exception prose', async () => {
  const h = await fixture()
  h.model.repositoryValue = { async sessions() { throw new SyntaxError('Unexpected JSON text with internal payload') } }
  await h.model.loadSessions()
  assert.ok(h.model.error)
  assert.doesNotMatch(h.model.error, /Unexpected|internal|payload/)
})

test('legal links point to the official documentation agreements', async () => {
  const h = await fixture()
  await h.model.openLegal('privacy'); await h.model.openLegal('terms')
  assert.deepEqual(h.state.openedURLs, ['https://docs.ksuser.cn/agreement/privacy', 'https://docs.ksuser.cn/agreement/user'])
})

test('a protected write resolves only after actual completion while verification stays usable', async () => {
  const h = await fixture(); const write = deferred(); let writes = 0; let settled = false
  h.model.repositoryValue = {
    async sensitiveStatus() { return { verified: false, methods: ['password'], remainingSeconds: 0 } },
    async verifySensitive() {}, async updateSetting() { writes++; await write.promise },
    async currentUser() { return { ...oldProfile, settings: { mfaEnabled: true } } }
  }
  const saving = h.model.updateSetting('mfaEnabled', true).then(result => { settled = true; return result })
  await until(() => h.model.challengeVisible && !h.model.busy)
  assert.equal(settled, false); assert.equal(writes, 0)
  const verifying = h.model.verifySensitive('password', 'fixture-secret')
  await until(() => writes === 1)
  assert.equal(settled, false)
  write.resolve(); await verifying
  assert.equal((await saving).status, 'success'); assert.equal(h.model.profile.settings.mfaEnabled, true)
})

test('cancelling verification settles the originating form without modifying or clearing its data', async () => {
  const h = await fixture(); let writes = 0
  h.model.recoveryCodes = ['FICTIONAL_OLD_CODE']
  h.model.repositoryValue = {
    async sensitiveStatus() { return { verified: false, methods: ['password'], remainingSeconds: 0 } },
    async changePassword() { writes++ }
  }
  const saving = h.model.changePassword('fixture-password')
  await until(() => h.model.challengeVisible && !h.model.busy)
  h.model.cancelChallenge()
  assert.equal((await saving).status, 'cancelled'); assert.equal(writes, 0)
  assert.deepEqual(h.model.recoveryCodes, ['FICTIONAL_OLD_CODE'])
})

test('a failed protected mutation reports failure to its form after successful identity verification', async () => {
  const h = await fixture()
  h.model.repositoryValue = {
    async sensitiveStatus() { return { verified: false, methods: ['password'], remainingSeconds: 0 } },
    async verifySensitive() {}, async revokeApplication() { throw new Error('private backend exception') }
  }
  const revoke = h.model.revokeApplication('fixture', false)
  await until(() => h.model.challengeVisible && !h.model.busy)
  await h.model.verifySensitive('password', 'fixture-secret')
  const result = await revoke
  assert.equal(result.status, 'failure'); assert.ok(result.error); assert.doesNotMatch(result.error, /private backend/)
  assert.equal(h.model.challengeVisible, false)
})

test('account changes cancel an awaiting sensitive operation and clear all page states', async () => {
  const h = await fixture()
  h.model.repositoryValue = { async sensitiveStatus() { return { verified: false, methods: ['password'], remainingSeconds: 0 } } }
  h.model.appsState = { status: 'loaded', error: '', hasData: true }
  const saving = h.model.requireSensitive('fixture')
  await until(() => h.model.challengeVisible && !h.model.busy)
  h.model.clearAccountData()
  assert.equal((await saving).status, 'cancelled')
  assert.deepEqual(h.model.appsState, { status: 'idle', error: '', hasData: false })
})

test('independent credential reads do not present a failed TOTP lookup as disabled', async () => {
  const h = await fixture()
  h.model.repositoryValue = {
    async passkeys() { return [] }, async totpStatus() { throw new Error('fixture failure') },
    async adaptiveStatus() { return { riskLevel: 'low', riskScore: 0 } }
  }
  await h.model.loadSecurity()
  assert.equal(h.model.passkeysState.status, 'loaded'); assert.equal(h.model.passkeysState.hasData, true)
  assert.equal(h.model.totpState.status, 'error'); assert.equal(h.model.totpState.hasData, false)
  assert.equal(h.model.riskState.status, 'loaded'); assert.equal(h.model.busy, false)
})

test('an application refresh failure preserves its previous complete snapshot', async () => {
  const h = await fixture()
  h.model.officialApps = [{ clientId: 'old', clientName: 'old', scopes: [] }]
  h.model.thirdPartyApps = [{ appId: 'old-third-party', appName: 'old', scopes: [] }]
  h.model.appsState = { status: 'loaded', error: '', hasData: true }
  h.model.repositoryValue = {
    async officialApps() { return [{ clientId: 'new', clientName: 'new', scopes: [] }] },
    async thirdPartyApps() { throw new Error('fixture unavailable') }
  }
  await h.model.loadApps()
  assert.equal(h.model.appsState.status, 'error'); assert.equal(h.model.appsState.hasData, true)
  assert.equal(h.model.officialApps[0].clientId, 'old'); assert.equal(h.model.thirdPartyApps[0].appId, 'old-third-party')
})

test('issued recovery URLs encode the ticket and disappear exactly at expiry', async () => {
  const h = await fixture()
  h.model.repositoryValue = {
    async sensitiveStatus() { return { verified: true } },
    async issueRecovery() { return { recoveryCode: 'FICTIONAL code+/=', expiresInSeconds: 60 } }
  }
  assert.equal((await h.model.issueRecoveryTicket()).status, 'success')
  const url = new URL(h.model.recoveryUrl())
  assert.equal(url.origin, 'https://auth.ksuser.cn'); assert.equal(url.searchParams.get('recoveryCode'), 'FICTIONAL code+/=')
  h.model.recoveryExpiresAt = Date.now()
  assert.equal(h.model.recoveryUrl(), '')
})

test('registration uses server password validation before sending the registration request', async () => {
  const h = await fixture(); let registerCalls = 0
  h.model.passwordPolicy = { minLength: 6, maxLength: 66, requireDigits: false }
  h.model.newStaged = () => ({ async checkUsername() { return { exists: false } },
    async validatePassword() { throw new Error('weak fixture password') }, async register() { registerCalls++ } })
  const result = await h.model.register('username', 'fixture@example.invalid', 'password', '123456')
  assert.equal(result.status, 'failure'); assert.equal(registerCalls, 0); assert.deepEqual(h.model.profile, oldProfile)
})

for (const codeType of ['approve_sensitive', 'approve_recovery']) {
  test(codeType + ' always verifies an independent credential before sending QR approval', async () => {
    const h = await fixture(async request => {
      const pathname = endpoint(request)
      if (pathname === '/auth/qr/preview') return reply({ codeType, expiresInSeconds: 60 })
      if (pathname === '/auth/check-sensitive-verification') return reply({ verified: true, methods: ['password', 'qr'], preferredMethod: 'password', remainingSeconds: 300 })
      if (pathname === '/auth/qr/approval/verify') {
        assert.deepEqual(JSON.parse(request.body), { approveCode: 'approve-code', method: 'password', password: 'fixture-password' })
        return reply({ proofId: 'independent-proof', expiresInSeconds: 60 })
      }
      if (pathname === '/auth/qr/approve') {
        assert.deepEqual(JSON.parse(request.body), { approveCode: 'approve-code', proofId: 'independent-proof' })
        return reply({ verified: true })
      }
      throw new Error('Unexpected request: ' + pathname)
    })
    await h.model.scan()
    await h.model.confirmQr()
    assert.equal(h.model.challengeKind, 'sensitive')
    assert.equal(h.model.sensitive.verified, false)
    assert.equal(h.calls.some(request => endpoint(request) === '/auth/qr/approve'), false)
    await h.model.verifySensitive('password', 'fixture-password')
    assert.equal(h.model.qr, null)
    const mutationPaths = h.calls.filter(request => request.method === 'POST').map(endpoint)
    assert.deepEqual(mutationPaths, ['/auth/qr/approval/verify', '/auth/qr/approve'])
    assert.equal(h.calls.some(request => endpoint(request) === '/auth/verify-sensitive'), false)
  })
}
