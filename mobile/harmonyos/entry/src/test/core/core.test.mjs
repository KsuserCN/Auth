// These host tests transpile and execute the production ArkTS core; only transport and durable storage are substituted.
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
const modules = new Map()
async function load(filename) {
  if (modules.has(filename)) return modules.get(filename)
  const code = ts.transpileModule(await fs.readFile(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const dependencies = new Map()
  for (const match of code.matchAll(/require\("(\.[^"]+)"\)/g)) {
    dependencies.set(match[1], await load(path.resolve(path.dirname(filename), match[1] + '.ets')))
  }
  const module = { exports: {} }
  new Function('require', 'module', 'exports', code)(name => {
    if (dependencies.has(name)) return dependencies.get(name)
    throw new Error('Unexpected core dependency: ' + name)
  }, module, module.exports)
  modules.set(filename, module.exports)
  return module.exports
}
const { ApiClient, MemorySessionStore, ApiError, RequestCancelled } = await load(path.join(sourceRoot, 'ApiClient.ets'))
const { receiveCookies, splitSetCookie, cookieMatches } = await load(path.join(sourceRoot, 'Cookies.ets'))
const { resultMessage } = await load(path.join(sourceRoot, 'Presentation.ets'))
const { parseQr, qrFlow } = await load(path.join(sourceRoot, 'QrParser.ets'))
const { Repository } = await load(path.join(sourceRoot, 'Repository.ets'))
const environment = { apiBaseURL: 'https://api.ksuser.cn', webOrigin: 'https://auth.ksuser.cn', passkeyRPID: 'auth.ksuser.cn', userAgent: 'KsuserAuthMobile/1.0 (HarmonyOS)', debug: true }
const copy = value => value == null ? value : JSON.parse(JSON.stringify(value))
function snapshot(token = 'old-token') {
  return { accessToken: token, cookies: [
    { name: 'XSRF-TOKEN', value: 'csrf%2Bvalue', domain: 'api.ksuser.cn', path: '/', hostOnly: true, secure: true },
    { name: 'refreshToken', value: 'old-refresh', domain: 'api.ksuser.cn', path: '/auth', hostOnly: true, secure: true }
  ], profile: { uuid: 'account-one', username: 'testing', email: 'testing@example.com' } }
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
async function until(predicate) {
  for (let index = 0; index < 50; index++) {
    if (predicate()) return
    await nextTurn()
  }
  throw new Error('Expected asynchronous phase was never reached')
}
function reply(data = {}, { status = 200, code = status, errorCode = code >= 400 ? 'REQUEST_FAILED' : 'REQUEST_SUCCESS', cookies = [], params } = {}) {
  return { status, cookies, body: JSON.stringify({ code, errorCode, params, data }) }
}
async function clientFixture(handler, initial = snapshot()) {
  const state = { stored: copy(initial), saveError: null, beforeSave: null, saveCount: 0, clearCount: 0 }
  const store = {
    async load() { return copy(state.stored) },
    async save(value) {
      state.saveCount++
      if (state.beforeSave) await state.beforeSave(copy(value))
      if (state.saveError) throw state.saveError
      state.stored = copy(value)
    },
    async clear() { state.clearCount++; state.stored = null }
  }
  const calls = []
  const transport = { async send(request) {
    calls.push({ ...request, headers: { ...request.headers } })
    return handler(request, calls)
  } }
  const client = new ApiClient(environment, store, transport)
  await client.restore()
  return { client, state, store, calls, transport }
}
const endpoint = request => new URL(request.url).pathname

// Cookie boundaries matter because the transport has no ambient browser cookie jar.
test('Set-Cookie splitting preserves the Expires comma and multiple cookie headers', () => {
  const combined = 'refreshToken=one; Expires=Wed, 21 Oct 2037 07:28:00 GMT; Path=/auth; Secure, XSRF-TOKEN=two; Path=/; Secure'
  assert.equal(splitSetCookie(combined).length, 2)
  const values = receiveCookies([], [combined], environment.apiBaseURL + '/auth/login', Date.UTC(2026, 0, 1))
  assert.deepEqual(values.map(cookie => cookie.name), ['refreshToken', 'XSRF-TOKEN'])
  assert.equal(values[0].expiresAt, Date.parse('Wed, 21 Oct 2037 07:28:00 GMT'))
})

test('cookies enforce host/domain, secure transport, expiry and path segment boundaries', () => {
  const url = environment.apiBaseURL + '/auth/login'
  const cookies = receiveCookies([], ['host=one; Path=/auth; Secure', 'shared=two; Domain=.ksuser.cn; Path=/',
    'foreign=bad; Domain=attacker.example; Path=/'], url, 1000)
  assert.equal(cookies.length, 2)
  assert.equal(cookieMatches(cookies[0], environment.apiBaseURL + '/auth', 1000), true)
  assert.equal(cookieMatches(cookies[0], environment.apiBaseURL + '/auth/profile', 1000), true)
  assert.equal(cookieMatches(cookies[0], environment.apiBaseURL + '/authentication', 1000), false)
  assert.equal(cookieMatches(cookies[0], 'https://child.api.ksuser.cn/auth', 1000), false)
  assert.equal(cookieMatches(cookies[0], 'http://api.ksuser.cn/auth', 1000), false)
  assert.equal(cookieMatches(cookies[1], 'https://auth.ksuser.cn/', 1000), true)
  assert.equal(cookieMatches(cookies[1], 'https://evilksuser.cn/', 1000), false)
  assert.equal(cookieMatches({ ...cookies[0], expiresAt: 1000 }, url, 1000), false)
  assert.deepEqual(receiveCookies([], ['secret=unsafe; Secure'], 'http://api.ksuser.cn/auth', 1000), [])
})

test('Max-Age overrides Expires and deletion removes only the matching cookie identity', () => {
  const url = environment.apiBaseURL + '/auth/login'
  const initial = receiveCookies([], ['token=one; Path=/auth', 'token=root; Path=/'], url, 1000)
  const deleted = receiveCookies(initial, ['token=gone; Path=/auth; Max-Age=0; Expires=Wed, 21 Oct 2037 07:28:00 GMT'], url, 2000)
  assert.deepEqual(deleted.map(cookie => [cookie.path, cookie.value]), [['/', 'root']])
  const kept = receiveCookies([], ['token=alive; Max-Age=60; Expires=Thu, 01 Jan 1970 00:00:00 GMT'], url, 1000)
  assert.equal(kept.length, 1)
  assert.equal(kept[0].expiresAt, 61000)
  assert.deepEqual(receiveCookies(kept, [], url, 61000), [])
})

test('cookie default path follows the request directory without adding a trailing slash', () => {
  const [cookie] = receiveCookies([], ['default=value'], environment.apiBaseURL + '/auth/login', 1000)
  assert.equal(cookie.path, '/auth')
  assert.equal(cookieMatches(cookie, environment.apiBaseURL + '/auth', 1000), true)
  assert.equal(cookieMatches(cookie, environment.apiBaseURL + '/authentication', 1000), false)
})

test('concurrent writes share one CSRF bootstrap and send the decoded header with cookies', async () => {
  const gate = deferred()
  const h = await clientFixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') {
      await gate.promise
      return reply({}, { cookies: ['XSRF-TOKEN=csrf%2Bvalue; Path=/; Secure'] })
    }
    assert.equal(request.headers['X-XSRF-TOKEN'], 'csrf+value')
    assert.equal(request.headers.Cookie, 'XSRF-TOKEN=csrf%2Bvalue')
    assert.equal(request.headers.Authorization, undefined)
    return reply({ saved: true })
  }, null)
  const operations = Promise.all([h.client.request('/auth/login', 'POST', { email: 'first@example.com' }, false),
    h.client.request('/auth/register', 'POST', { email: 'second@example.com' }, false)])
  await until(() => h.calls.length === 1)
  assert.equal(endpoint(h.calls[0]), '/auth/csrf-token')
  gate.resolve()
  assert.deepEqual(await operations, [{ saved: true }, { saved: true }])
  assert.equal(h.calls.filter(request => endpoint(request) === '/auth/csrf-token').length, 1)
  assert.equal(h.state.stored.cookies[0].name, 'XSRF-TOKEN')
})

test('concurrent 401 responses share one refresh and atomically rotate token plus cookies before replay', async () => {
  const gate = deferred()
  const h = await clientFixture(async request => {
    if (endpoint(request) === '/auth/refresh') {
      await gate.promise
      assert.equal(request.headers.Authorization, undefined)
      assert.equal(request.headers['X-XSRF-TOKEN'], 'csrf+value')
      assert.match(request.headers.Cookie, /refreshToken=old-refresh/)
      return reply({ accessToken: 'new-token' }, { cookies: ['refreshToken=new-refresh; Path=/auth; Secure; HttpOnly',
        'XSRF-TOKEN=next%20csrf; Path=/; Secure'] })
    }
    if (request.headers.Authorization === 'Bearer old-token') return reply({}, { status: 401 })
    assert.equal(request.headers.Authorization, 'Bearer new-token')
    assert.match(request.headers.Cookie, /refreshToken=new-refresh/)
    assert.equal(request.headers['X-XSRF-TOKEN'], 'next csrf')
    return reply({ saved: true })
  })
  const operations = Promise.all([h.client.request('/auth/update/profile', 'POST', { key: 'bio', value: 'one' }),
    h.client.request('/auth/update/setting', 'POST', { field: 'mfaEnabled', value: true })])
  await until(() => h.calls.filter(request => endpoint(request) === '/auth/refresh').length === 1)
  gate.resolve()
  assert.deepEqual(await operations, [{ saved: true }, { saved: true }])
  assert.equal(h.calls.filter(request => endpoint(request) === '/auth/refresh').length, 1)
  assert.equal(h.calls.length, 5)
  assert.equal(h.state.stored.accessToken, 'new-token')
  assert.equal(h.state.stored.cookies.find(cookie => cookie.name === 'refreshToken').value, 'new-refresh')
  assert.equal(h.state.stored.profile.uuid, 'account-one')
})

test('a rejected replay is not refreshed repeatedly and clears the invalid session', async () => {
  const h = await clientFixture(async request => endpoint(request) === '/auth/refresh'
    ? reply({ accessToken: 'new-token' }) : reply({}, { status: 401 }))
  let unauthorized = 0
  h.client.onUnauthorized = () => unauthorized++
  await assert.rejects(h.client.request('/auth/info'), error => error instanceof ApiError && error.status === 401)
  assert.equal(h.calls.filter(request => endpoint(request) === '/auth/info').length, 2)
  assert.equal(h.calls.filter(request => endpoint(request) === '/auth/refresh').length, 1)
  assert.equal(h.state.clearCount, 1)
  assert.equal(unauthorized, 1)
  assert.equal(h.client.snapshot().accessToken, '')
})

test('network errors never replay a write or clear its valid session', async () => {
  const networkError = new Error('offline transport fixture')
  const h = await clientFixture(async () => { throw networkError })
  await assert.rejects(h.client.request('/auth/update/profile', 'POST', { key: 'bio', value: 'unchanged' }), error => error === networkError)
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].method, 'POST')
  assert.deepEqual(h.client.snapshot(), snapshot())
  assert.equal(h.state.clearCount, 0)
})

test('refresh 401 clears durable credentials and notifies unauthorized once', async () => {
  const h = await clientFixture(async () => reply({}, { status: 401 }))
  let unauthorized = 0
  h.client.onUnauthorized = () => unauthorized++
  await assert.rejects(h.client.request('/auth/info'), error => error instanceof ApiError && error.status === 401)
  assert.equal(h.calls.length, 2)
  assert.equal(h.state.stored, null)
  assert.equal(h.client.snapshot().accessToken, '')
  assert.equal(unauthorized, 1)
})

test('refresh connectivity failure retains the current credentials and does not replay', async () => {
  const networkError = new Error('refresh offline')
  const h = await clientFixture(async request => {
    if (endpoint(request) === '/auth/refresh') throw networkError
    return reply({}, { status: 401 })
  })
  await assert.rejects(h.client.request('/auth/info'), error => error === networkError)
  assert.equal(h.calls.length, 2)
  assert.deepEqual(h.client.snapshot(), snapshot())
  assert.deepEqual(h.state.stored, snapshot())
  assert.equal(h.state.clearCount, 0)
})

for (const action of ['invalidate', 'clear']) {
  test(action + ' discards late response data and Set-Cookie without starting a refresh', async () => {
    const gate = deferred()
    const h = await clientFixture(async () => { await gate.promise; return reply({ obsolete: true }, {
      status: 401, cookies: ['refreshToken=late; Path=/auth; Secure'] }) })
    const request = h.client.request('/auth/info')
    const rejected = assert.rejects(request, error => error instanceof RequestCancelled)
    await until(() => h.calls.length === 1)
    await h.client[action]()
    gate.resolve()
    await rejected
    assert.equal(h.calls.length, 1)
    assert.deepEqual(h.client.snapshot(), action === 'clear' ? { accessToken: '', cookies: [], profile: null } : snapshot())
    assert.equal(h.state.stored?.cookies.find(cookie => cookie.name === 'refreshToken').value, action === 'clear' ? undefined : 'old-refresh')
  })
}

test('rotation save failure keeps new credentials in memory and flush repairs durable storage', async () => {
  const storageError = new Error('temporarily locked store')
  const h = await clientFixture(async request => {
    if (endpoint(request) === '/auth/refresh') return reply({ accessToken: 'new-token' }, {
      cookies: ['refreshToken=new-refresh; Path=/auth; Secure'] })
    return request.headers.Authorization === 'Bearer old-token' ? reply({}, { status: 401 }) : reply({ account: 'one' })
  })
  h.state.saveError = storageError
  await assert.rejects(h.client.request('/auth/info'), error => error === storageError)
  assert.equal(h.client.snapshot().accessToken, 'new-token')
  assert.equal(h.client.snapshot().cookies.find(cookie => cookie.name === 'refreshToken').value, 'new-refresh')
  assert.equal(h.state.stored.accessToken, 'old-token')
  h.state.saveError = null
  await h.client.flush()
  assert.equal(h.state.stored.accessToken, 'new-token')
  assert.equal(h.state.stored.cookies.find(cookie => cookie.name === 'refreshToken').value, 'new-refresh')
  assert.deepEqual(await h.client.request('/auth/info'), { account: 'one' })
  assert.equal(h.calls.filter(request => endpoint(request) === '/auth/refresh').length, 1)
})

test('temporary MemorySessionStore changes cannot mutate a live session before explicit adoption', async () => {
  const h = await clientFixture(async () => reply({}))
  const temporaryStore = new MemorySessionStore()
  const staged = new ApiClient(environment, temporaryStore, h.transport)
  await staged.setAccessToken('pending-token')
  await staged.setProfile({ uuid: 'account-two', username: 'other', email: 'other@example.com' })
  const firstRead = await temporaryStore.load(); firstRead.profile.username = 'changed copy'
  assert.equal((await temporaryStore.load()).profile.username, 'other')
  assert.equal(h.client.snapshot().accessToken, 'old-token')
  assert.equal(h.state.stored.profile.uuid, 'account-one')
  await h.client.adopt(staged.snapshot())
  assert.equal(h.client.snapshot().profile.uuid, 'account-two')
  assert.equal(h.state.stored.accessToken, 'pending-token')
})

test('failed adoption retains the existing authenticated snapshot', async () => {
  const h = await clientFixture(async () => reply({}))
  h.state.saveError = new Error('store locked')
  await assert.rejects(h.client.adopt({ ...snapshot('pending-token'), profile: { uuid: 'account-two', username: 'other', email: 'other@example.com' } }), /store locked/)
  assert.equal(h.client.snapshot().accessToken, 'old-token')
  assert.deepEqual(h.state.stored, snapshot())
})

test('clear during pending adoption cannot restore a logged-out account', async () => {
  const gate = deferred()
  const h = await clientFixture(async () => reply({}))
  h.state.beforeSave = async () => gate.promise
  const adopting = h.client.adopt({ ...snapshot('pending-token'), profile: { uuid: 'account-two', username: 'other', email: 'other@example.com' } })
  await until(() => h.state.saveCount === 1)
  const clearing = h.client.clear()
  gate.resolve()
  await Promise.allSettled([adopting, clearing])
  assert.equal(h.state.stored, null)
  assert.deepEqual(h.client.snapshot(), { accessToken: '', cookies: [], profile: null })
})

test('result messages interpolate local codes and fall back locally for missing parameters or unknown codes', async () => {
  assert.equal(resultMessage('AUTH_020', { min: 8, max: 64 }, 400), '密码长度必须在8-64个字符之间')
  const generic = resultMessage(undefined, undefined, 400)
  assert.equal(resultMessage('AUTH_020', { min: 8 }, 400), generic)
  assert.equal(resultMessage('UNPUBLISHED_CODE', { msg: 'server detail' }, 400), generic)
  const h = await clientFixture(async () => ({ status: 400, cookies: [],
    body: JSON.stringify({ code: 400, errorCode: 'UNPUBLISHED_CODE', msg: 'server detail', error_description: 'upstream secret' }) }))
  await assert.rejects(h.client.request('/auth/info'), error => error.message === generic && !error.message.includes('server detail') && !error.message.includes('upstream secret'))
})

test('QR parsing accepts existing prefixes and the configured HTTPS site while rejecting ambiguous or foreign tickets', () => {
  assert.deepEqual(parseQr(' KSUSER-AUTH-XFER:v1:transfer-code ', environment.webOrigin), { code: 'transfer-code', transfer: true })
  assert.deepEqual(parseQr('ksuser-auth-qr:approve-code', environment.webOrigin), { code: 'approve-code', transfer: false })
  assert.deepEqual(parseQr('https://auth.ksuser.cn/scan?approveCode=one%2Btwo', environment.webOrigin), { code: 'one+two', transfer: false })
  for (const invalid of ['https://attacker.example/?approveCode=code', 'http://auth.ksuser.cn/?approveCode=code',
    'https://auth.ksuser.cn:444/?approveCode=code', 'https://auth.ksuser.cn@attacker.example/?approveCode=code',
    'https://auth.ksuser.cn/?approveCode=one&approveCode=two', 'https://auth.ksuser.cn/?approveCode=one&transferCode=two',
    'https://auth.ksuser.cn/?ticket=app-ticket', 'https://auth.ksuser.cn/?approveCode=code#fragment',
    'KSUSER-AUTH-QR:', 'KSUSER-AUTH-XFER:v1:' + 'x'.repeat(4097)]) {
    assert.throws(() => parseQr(invalid, environment.webOrigin))
  }
})

test('QR flow dispatch keeps transfer, ordinary approval and website consent protocols separate', () => {
  assert.equal(qrFlow('transfer', true), 'transfer')
  for (const type of ['approve_login', 'approve_mfa', 'approve_sensitive', 'approve_console_login']) assert.equal(qrFlow(type, false), 'approval')
  assert.equal(qrFlow('approve_official', false), 'official')
  assert.equal(qrFlow('approve_application', false), 'consent')
  assert.throws(() => qrFlow('approve_application', true))
  assert.throws(() => qrFlow('native_application', false))
})

test('website consent decisions use only QR protocol endpoints and normalize loginWeb plus optional lifetime', async () => {
  const calls = []
  const client = { async request(...args) { calls.push(args); return {} } }
  const repository = new Repository(client)
  await repository.decideConsent('ticket', false, 'TIME_LIMITED', 3600, true)
  assert.equal(calls[0][0], '/auth/mobile-authorization/decide')
  assert.deepEqual(calls[0][2], { ticket: 'ticket', approve: false, grantMode: 'TIME_LIMITED', loginWeb: false, grantTtlSeconds: 3600 })
  await repository.decideConsent('ticket', true, 'ONE_TIME', 3600, false)
  assert.deepEqual(calls[1][2], { ticket: 'ticket', approve: true, grantMode: 'ONE_TIME', loginWeb: false })
})

test('requests waiting for an account adoption use only the committed account credentials', async () => {
  const gate = deferred()
  const h = await clientFixture(async request => {
    assert.equal(request.headers.Authorization, 'Bearer account-two-token')
    return reply({ ok: true })
  })
  h.state.beforeSave = async () => gate.promise
  const adopting = h.client.adopt({ ...snapshot('account-two-token'), profile: { uuid: 'account-two', username: 'other', email: '' } })
  await until(() => h.state.saveCount === 1)
  const reading = h.client.request('/auth/info')
  await nextTurn()
  assert.equal(h.calls.length, 0, 'No old-account HTTP request may run while adoption is pending')
  gate.resolve(); await adopting; await reading
  assert.equal(h.calls.length, 1)
})

test('an operation begun before adoption is cancelled before it can send as the next account', async () => {
  const h = await clientFixture(async () => reply())
  const reading = h.client.request('/auth/info')
  const rejected = assert.rejects(reading, error => error instanceof RequestCancelled)
  await h.client.adopt({ ...snapshot('new-account-token'), profile: { uuid: 'next', username: 'next', email: '' } })
  await rejected
  assert.equal(h.calls.length, 0)
})

test('a bootstrap cancelled by logout cannot continue to send a protected write', async () => {
  const gate = deferred()
  const h = await clientFixture(async request => {
    if (endpoint(request) === '/auth/csrf-token') { await gate.promise; return reply({}, { cookies: ['XSRF-TOKEN=late; Path=/; Secure'] }) }
    throw new Error('Cancelled write must never be sent')
  }, { accessToken: 'token', cookies: [], profile: null })
  const posting = h.client.request('/auth/update/profile', 'POST', { key: 'bio', value: 'draft' })
  const rejected = assert.rejects(posting, error => error instanceof RequestCancelled)
  await until(() => h.calls.length === 1)
  await h.client.clear(); gate.resolve(); await rejected
  assert.equal(h.calls.length, 1)
})
