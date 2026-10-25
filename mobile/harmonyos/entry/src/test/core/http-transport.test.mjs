// Execute the production transport with a controllable native HTTP request; no real credentials or network are used.
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import { setTimeout as delay } from 'node:timers/promises'

const require = createRequire(import.meta.url)
const ts = require(process.env.HARMONY_TYPESCRIPT_PATH ?? '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript')
const sourceRoot = fileURLToPath(new URL('../../main/ets/core/', import.meta.url))
const request = { url: 'https://api.example.test/auth/login', method: 'POST',
  headers: { Accept: 'application/json', 'Accept-Language': 'zh-Hans', 'User-Agent': 'Harmony transport test' }, body: '{}' }
const response = { responseCode: 200, result: '{"code":200,"errorCode":"REQUEST_SUCCESS","data":{}}', header: {}, cookies: '' }

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

async function harness(nativeRequest) {
  const state = { calls: [], destroys: 0 }
  const http = { HttpDataType: { STRING: 0 }, createHttp: () => ({
    request(url, options) { state.calls.push({ url, options }); return nativeRequest() },
    destroy() { state.destroys++ }
  }) }
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
      if (name === '@kit.NetworkKit') return { http }
      if (dependencies.has(name)) return dependencies.get(name)
      throw new Error('Unexpected transport dependency: ' + name)
    }, module, module.exports)
    modules.set(filename, module.exports)
    return module.exports
  }
  return { state, load }
}

test('an unresolved native request reaches the overall deadline and is destroyed without replay', async () => {
  const h = await harness(() => new Promise(() => {}))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const { resultMessage } = await h.load(path.join(sourceRoot, 'Presentation.ets'))
  await assert.rejects(new NativeHttpTransport(20).send(request), error => error.message === resultMessage('REQUEST_TIMEOUT'))
  assert.equal(h.state.calls.length, 1)
  assert.equal(h.state.calls[0].options.method, 'POST')
  assert.equal(h.state.calls[0].options.connectTimeout, 30000)
  assert.equal(h.state.calls[0].options.readTimeout, 30000)
  assert.equal(h.state.destroys, 1)
})

test('a response arriving after the deadline cannot install cookies in the API client', async () => {
  const gate = deferred()
  const h = await harness(() => gate.promise)
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const { ApiClient, MemorySessionStore } = await h.load(path.join(sourceRoot, 'ApiClient.ets'))
  const client = new ApiClient({ apiBaseURL: 'https://api.example.test', userAgent: 'Harmony transport test' },
    new MemorySessionStore(), new NativeHttpTransport(20))
  await assert.rejects(client.request('/info/password-requirement', 'GET', undefined, false))
  gate.resolve({ ...response, cookies: 'XSRF-TOKEN=late-cookie; Path=/; Secure' })
  await delay(30)
  assert.equal(client.snapshot().cookies.length, 0)
  assert.equal(client.snapshot().accessToken, '')
  assert.equal(h.state.calls.length, 1)
  assert.equal(h.state.destroys, 1)
})

test('a timely native response preserves data and clears its deadline timer', async () => {
  const h = await harness(async () => ({ ...response, cookies: 'XSRF-TOKEN=example; Path=/; Secure' }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const result = await new NativeHttpTransport(20).send(request)
  assert.deepEqual(result, { status: 200, body: response.result, cookies: ['XSRF-TOKEN=example; Path=/; Secure'] })
  await delay(40)
  assert.equal(h.state.destroys, 1)
  assert.equal(h.state.calls.length, 1)
})

test('Set-Cookie headers take priority over the native cookie metadata', async () => {
  const lines = ['XSRF-TOKEN=from-header; Domain=.example.test; Path=/; Secure', 'refreshToken=from-header; Path=/; Secure; HttpOnly']
  const h = await harness(async () => ({ ...response, header: { 'set-cookie': lines },
    cookies: '.example.test\tTRUE\t/\tTRUE\t0\tXSRF-TOKEN\tmetadata-value' }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const result = await new NativeHttpTransport(200).send(request)
  assert.deepEqual(result.cookies, lines)
})

test('a conventional Set-Cookie header bootstraps the real API-client CSRF state', async () => {
  const h = await harness(async () => ({ ...response, header: { 'Set-Cookie': 'XSRF-TOKEN=from-header; Domain=.example.test; Path=/; Secure' }, cookies: '' }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const { ApiClient, MemorySessionStore } = await h.load(path.join(sourceRoot, 'ApiClient.ets'))
  const client = new ApiClient({ apiBaseURL: 'https://api.example.test', userAgent: 'Harmony transport test' },
    new MemorySessionStore(), new NativeHttpTransport(200))
  await client.ensureCsrf()
  assert.equal(client.snapshot().cookies[0].name, 'XSRF-TOKEN')
  assert.equal(client.snapshot().cookies[0].value, 'from-header')
})

test('Netscape cookie metadata is converted without widening host, path, secure or expiry scope', async () => {
  const raw = '# Netscape HTTP Cookie File\n' +
    '#HttpOnly_.example.test\tTRUE\t/\tTRUE\t2000000000\trefreshToken\tsynthetic-refresh\n' +
    'api.example.test\tFALSE\t/auth\tFALSE\t0\tXSRF-TOKEN\tsynthetic-csrf\n'
  const h = await harness(async () => ({ ...response, cookies: raw }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  const { receiveCookies } = await h.load(path.join(sourceRoot, 'Cookies.ets'))
  const result = await new NativeHttpTransport(200).send(request)
  const cookies = receiveCookies([], result.cookies, request.url, 1000)
  assert.deepEqual(cookies.map(cookie => ({ name: cookie.name, domain: cookie.domain, hostOnly: cookie.hostOnly,
    path: cookie.path, secure: cookie.secure, expiresAt: cookie.expiresAt })), [
    { name: 'refreshToken', domain: 'example.test', hostOnly: false, path: '/', secure: true, expiresAt: 2000000000000 },
    { name: 'XSRF-TOKEN', domain: 'api.example.test', hostOnly: true, path: '/auth', secure: false, expiresAt: undefined }
  ])
})

test('malformed or foreign Netscape cookie metadata is ignored', async () => {
  const raw = 'attacker.test\tTRUE\t/\tTRUE\t0\tforeign\tvalue\n' +
    'other.example.test\tFALSE\t/\tFALSE\t0\thostOnly\tvalue\n' +
    'api.example.test\tFALSE\t/\tMAYBE\t0\tbadFlag\tvalue\n' +
    'api.example.test\tFALSE\t/; Domain=attacker.test\tFALSE\t0\tbadPath\tvalue\n' +
    'api.example.test\tFALSE\t/\tFALSE\tbad-expiry\tbadExpiry\tvalue\n'
  const h = await harness(async () => ({ ...response, cookies: raw }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  assert.deepEqual((await new NativeHttpTransport(200).send(request)).cookies, [])
})

test('Set-Cookie header names are matched without case sensitivity', async () => {
  const h = await harness(async () => ({ ...response, header: { 'SET-COOKIE': 'XSRF-TOKEN=uppercase; Path=/; Secure' } }))
  const { NativeHttpTransport } = await h.load(path.join(sourceRoot, 'HttpTransport.ets'))
  assert.deepEqual((await new NativeHttpTransport(200).send(request)).cookies, ['XSRF-TOKEN=uppercase; Path=/; Secure'])
})
