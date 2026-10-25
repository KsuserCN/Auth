// Host contract tests execute the production ArkTS adapters with narrowly scoped system-kit substitutes.
// CryptoKit's documented AES-GCM byte framing is supplied by Node's crypto; real device tests remain required.
import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import crypto from 'node:crypto'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require(process.env.HARMONY_TYPESCRIPT_PATH ?? '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript')
const sourceRoot = fileURLToPath(new URL('../../main/ets/native/', import.meta.url))

function snapshot(token = 'token-one') {
  return { accessToken: token, cookies: [{ name: 'XSRF-TOKEN', value: 'csrf', domain: 'api.ksuser.cn', path: '/', secure: true, hostOnly: true }],
    profile: { uuid: 'account-one', username: 'testing', email: 'user@example.com', bio: '资料'.repeat(2000) } }
}

async function harness() {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'ksuser-harmony-native-'))
  const assets = new Map()
  const handles = new Map()
  const state = { assetError: 0, cryptoError: 0, finalError: 0, unsupportedCapabilities: new Set(), renameFailure: false, scanError: 0, scanValue: 'KSUSER-AUTH-QR:code', lastAuthentication: null, lastRegistration: null, clipboard: null }
  const Tag = { ALIAS: 1, SECRET: 2, ACCESSIBILITY: 3, AUTH_TYPE: 4, SYNC_TYPE: 5, RETURN_TYPE: 6 }
  const alias = query => Buffer.from(query.get(Tag.ALIAS)).toString('hex')
  const asset = {
    Tag, ReturnType: { ALL: 0 }, Accessibility: { DEVICE_FIRST_UNLOCKED: 1 }, AuthType: { NONE: 0 }, SyncType: { NEVER: 0 },
    async query(query) {
      if (state.assetError) throw { code: state.assetError }
      const existing = assets.get(alias(query))
      if (!existing) throw { code: 24000002 }
      return [new Map([...existing].map(([key, value]) => [key, value instanceof Uint8Array ? new Uint8Array(value) : value]))]
    },
    async add(attributes) {
      const name = alias(attributes)
      if (assets.has(name)) throw { code: 24000003 }
      assets.set(name, new Map([...attributes].map(([key, value]) => [key, value instanceof Uint8Array ? new Uint8Array(value) : value])))
    },
    async remove(query) {
      if (state.assetError) throw { code: state.assetError }
      if (!assets.delete(alias(query))) throw { code: 24000002 }
    }
  }
  const cryptoFramework = {
    CryptoMode: { ENCRYPT_MODE: 0, DECRYPT_MODE: 1 },
    createMd: () => {
      const digest = crypto.createHash('sha256')
      return { async update(value) { digest.update(value.data) }, async digest() { return { data: new Uint8Array(digest.digest()) } } }
    },
    createRandom: () => ({ generateRandom: async count => ({ data: new Uint8Array(crypto.randomBytes(count)) }) }),
    createSymKeyGenerator: () => ({ convertKey: async value => ({ key: Buffer.from(value.data) }) }),
    createCipher: transformation => {
      if (state.cryptoError) throw { code: state.cryptoError }
      assert.equal(transformation, 'AES256|GCM|NoPadding')
      let configuration
      return {
        async init(mode, key, params) { configuration = { mode, key: key.key, params } },
        async doFinal(input) {
          if (state.finalError) throw { code: state.finalError }
          const { mode, key, params } = configuration
          const engine = mode === 0 ? crypto.createCipheriv('aes-256-gcm', key, params.iv.data) : crypto.createDecipheriv('aes-256-gcm', key, params.iv.data)
          engine.setAAD(params.aad.data)
          if (mode === 1) engine.setAuthTag(params.authTag.data)
          try {
            const result = Buffer.concat([engine.update(input.data), engine.final(), ...(mode === 0 ? [engine.getAuthTag()] : [])])
            return { data: new Uint8Array(result) }
          } catch (error) {
            if (mode === 1) throw { code: 17630001 }
            throw error
          }
        }
      }
    }
  }
  const fileIo = {
    OpenMode: { CREATE: 1, READ_WRITE: 2, TRUNC: 4 },
    async access(filename) { try { await fs.access(filename); return true } catch (error) { if (error.code === 'ENOENT') return false; throw error } },
    stat: filename => fs.stat(filename), readText: filename => fs.readFile(filename, 'utf8'), unlink: filename => fs.unlink(filename),
    async open(filename) { const handle = await fs.open(filename, 'w+', 0o600); handles.set(handle.fd, handle); return { fd: handle.fd } },
    async write(fd, value) { return (await handles.get(fd).write(Buffer.from(value))).bytesWritten },
    async fsync(fd) { await handles.get(fd).sync() },
    async close(file) { const handle = handles.get(file.fd); handles.delete(file.fd); await handle.close() },
    async rename(from, to) { if (state.renameFailure) throw new Error('simulated rename failure'); await fs.rename(from, to) }
  }
  const util = {
    Type: { BASIC_URL_SAFE: 2 },
    Base64Helper: class {
      encodeToStringSync(value) { return Buffer.from(value).toString('base64url') }
      decodeSync(value) { return new Uint8Array(Buffer.from(value, 'base64url')) }
    },
    TextEncoder: { create: () => ({ encodeInto: value => new TextEncoder().encode(value) }) },
    TextDecoder: { create: () => ({ decodeToString: value => new TextDecoder('utf-8', { fatal: true }).decode(value) }) }
  }
  const bytes = new Uint8Array([1, 2, 250, 251, 252])
  const clientData = new TextEncoder().encode('{"origin":"harmony-native-origin","challenge":"untouched","type":"webauthn.get"}')
  const response = { clientDataJson: clientData, authenticatorData: bytes, signature: bytes, attestationObject: bytes, transports: ['internal'] }
  const fido2 = {
    UserVerificationRequirement: { REQUIRED: 'required', PREFERRED: 'preferred', DISCOURAGED: 'discouraged' },
    AuthenticatorTransport: { USB: 'usb', NFC: 'nfc', BLE: 'ble', HYBRID: 'hybrid', INTERNAL: 'internal', SMART_CARD: 'smart-card' },
    PublicKeyCredentialType: { PUBLIC_KEY: 'public-key' },
    AuthenticatorAttachment: { PLATFORM: 'platform', CROSS_PLATFORM: 'cross-platform' },
    AttestationConveyancePreference: { NONE: 'none', INDIRECT: 'indirect', DIRECT: 'direct', ENTERPRISE: 'enterprise' },
    async authenticate(context, options) { state.lastAuthentication = options; return { rawId: bytes, response } },
    async register(context, options) { state.lastRegistration = options; return { rawId: bytes, response } }
  }
  const mocks = new Map([
    ['@kit.BasicServicesKit', { pasteboard: {
      MIMETYPE_TEXT_PLAIN: 'text/plain', ShareOption: { LOCALDEVICE: 1 },
      createData(type, text) { let property = { shareOption: 2 }; return { type, text,
        getProperty() { return property }, setProperty(value) { property = value } } },
      getSystemPasteboard() { return { async setData(value) { state.clipboard = { text: value.text, shareOption: value.getProperty().shareOption } } } }
    } }], ['@kit.ArkTS', { util }], ['@kit.AssetStoreKit', { asset }], ['@kit.CryptoArchitectureKit', { cryptoFramework }],
    ['@kit.CoreFileKit', { fileIo }], ['@kit.OnlineAuthenticationKit', { fido2 }],
    ['@kit.ScanKit', { scanCore: { ScanType: { QR_CODE: 1 } }, scanBarcode: { async startScanForResult(context, options) {
      assert.deepEqual(options, { scanTypes: [1], enableMultiMode: false, enableAlbum: true })
      if (state.scanError) throw { code: state.scanError }
      return { originalValue: state.scanValue }
    } } }], ['@kit.MediaLibraryKit', { photoAccessHelper: {} }], ['@kit.ImageKit', { image: {} }]
  ])
  const loaded = new Map()
  async function load(filename) {
    if (loaded.has(filename)) return loaded.get(filename)
    const code = ts.transpileModule(await fs.readFile(filename, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
    const dependencies = new Map()
    for (const match of code.matchAll(/require\("(\.[^"]+)"\)/g)) {
      const dependency = path.resolve(path.dirname(filename), match[1] + '.ets')
      dependencies.set(match[1], await load(dependency))
    }
    const module = { exports: {} }
    const scopedRequire = name => {
      if (mocks.has(name)) return mocks.get(name)
      if (dependencies.has(name)) return dependencies.get(name)
      throw new Error('Unexpected adapter dependency: ' + name)
    }
    new Function('require', 'module', 'exports', 'canIUse', code)(scopedRequire, module, module.exports, capability => !state.unsupportedCapabilities.has(capability))
    loaded.set(filename, module.exports)
    return module.exports
  }
  return { directory, assets, state, Tag, load, clientData,
    async dispose() { for (const handle of handles.values()) await handle.close(); await fs.rm(directory, { recursive: true, force: true }) } }
}

test('encrypted session round trip exceeds ASSET size and keeps plaintext out of sandbox', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const store = new SecureSessionStore({ filesDir: h.directory })
    const value = snapshot()
    await store.save(value)
    const ciphertext = await fs.readFile(path.join(h.directory, 'authenticated-session.enc'), 'utf8')
    assert.equal(ciphertext.includes('user@example.com'), false)
    assert.equal(ciphertext.includes('token-one'), false)
    assert.deepEqual(await store.load(), value)
    const attributes = [...h.assets.values()][0]
    assert.equal(attributes.get(h.Tag.SECRET).length, 32)
    assert.equal(attributes.get(h.Tag.SYNC_TYPE), 0)
    assert.equal(attributes.get(h.Tag.ACCESSIBILITY), 1)
    const initialIV = JSON.parse(ciphertext).iv
    await store.save(value)
    assert.notEqual(JSON.parse(await fs.readFile(path.join(h.directory, 'authenticated-session.enc'), 'utf8')).iv, initialIV)
  } finally { await h.dispose() }
})

test('AES-GCM rejects tampered ciphertext, preserving explicit corrupt state', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const store = new SecureSessionStore({ filesDir: h.directory })
    await store.save(snapshot())
    const filename = path.join(h.directory, 'authenticated-session.enc')
    const envelope = JSON.parse(await fs.readFile(filename, 'utf8'))
    const tampered = Buffer.from(envelope.ciphertext, 'base64url'); tampered[0] ^= 1
    envelope.ciphertext = tampered.toString('base64url')
    await fs.writeFile(filename, JSON.stringify(envelope))
    await assert.rejects(store.load(), error => error.kind === 'corrupt')
    assert.equal(await fs.readFile(filename, 'utf8'), JSON.stringify(envelope))
  } finally { await h.dispose() }
})

test('concurrent snapshots serialize, failed rename preserves previous committed session', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const store = new SecureSessionStore({ filesDir: h.directory })
    const first = snapshot('first'), second = snapshot('second')
    const firstSave = store.save(first); first.profile.username = 'mutated after save'
    await Promise.all([firstSave, store.save(second)])
    assert.deepEqual(await store.load(), second)
    h.state.renameFailure = true
    await assert.rejects(store.save(snapshot('failed')), error => error.kind === 'unavailable')
    h.state.renameFailure = false
    assert.deepEqual(await store.load(), second)
    await assert.rejects(fs.access(path.join(h.directory, 'authenticated-session.enc.tmp')))
  } finally { await h.dispose() }
})

test('temporarily unavailable crypto preserves the session and permits a later retry', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const store = new SecureSessionStore({ filesDir: h.directory })
    const value = snapshot()
    await store.save(value)
    const filename = path.join(h.directory, 'authenticated-session.enc')
    const original = await fs.readFile(filename, 'utf8')
    h.state.unsupportedCapabilities.add('SystemCapability.Security.CryptoFramework.Cipher')
    await assert.rejects(store.load(), error => error.kind === 'unavailable')
    h.state.unsupportedCapabilities.clear()
    for (const code of [401, 801, 17620001, 17620002, 17620003, 17630001, -1]) {
      h.state.cryptoError = code
      await assert.rejects(store.load(), error => error.kind === 'unavailable')
    }
    h.state.cryptoError = 0
    for (const code of [401, 801, 17620001, 17620002, 17620003, -1]) {
      h.state.finalError = code
      await assert.rejects(store.load(), error => error.kind === 'unavailable')
    }
    assert.equal(await fs.readFile(filename, 'utf8'), original)
    h.state.finalError = 0
    assert.deepEqual(await store.load(), value)
  } finally { await h.dispose() }
})

test('locked ASSET never clears data; blank token with CSRF cookies is a valid snapshot', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const store = new SecureSessionStore({ filesDir: h.directory })
    const value = snapshot(''); value.profile = null
    await store.save(value)
    h.state.assetError = 24000005
    await assert.rejects(store.load(), error => error.kind === 'locked')
    h.state.assetError = 0
    assert.deepEqual(await store.load(), value)
    await store.clear()
    assert.equal(await store.load(), null)
    assert.equal(h.assets.size, 0)
  } finally { await h.dispose() }
})

test('native Passkey requests enforce RP and preserve original signed binary responses', async () => {
  const h = await harness()
  try {
    const { NativeServices } = await h.load(path.join(sourceRoot, 'NativeServices.ets'))
    const native = new NativeServices({}, { passkeyRPID: 'auth.ksuser.cn' })
    const input = { challenge: Buffer.alloc(32, 7).toString('base64url'), challengeId: 'challenge', rpId: 'auth.ksuser.cn',
      userVerification: 'required', allowCredentials: JSON.stringify([{ type: 'public-key', id: 'AQID', transports: ['internal', 'unknown'] }]) }
    const result = await native.authenticate(input)
    assert.deepEqual(Buffer.from(result.clientDataJSON, 'base64url'), Buffer.from(h.clientData))
    assert.equal(h.state.lastAuthentication.publicKey.userVerification, 'required')
    assert.deepEqual(h.state.lastAuthentication.publicKey.allowCredentials[0].transports, ['internal'])
    assert.equal(native.readLastOrigin(), 'harmony-native-origin')
    const registered = await native.register({ challenge: input.challenge, rp: JSON.stringify({ id: input.rpId, name: 'Ksuser' }),
      user: JSON.stringify({ id: 'AQID', name: 'user@example.com', displayName: 'User' }),
      pubKeyCredParams: JSON.stringify([{ type: 'public-key', alg: -7 }]),
      authenticatorSelection: JSON.stringify({ residentKey: 'required', userVerification: 'required' }), attestation: 'none' })
    assert.deepEqual(Buffer.from(registered.clientDataJSON, 'base64url'), Buffer.from(h.clientData))
    assert.equal(registered.transports, 'internal')
    assert.equal(h.state.lastRegistration.publicKey.authenticatorSelection.residentKey, 'required')
    assert.equal(h.state.lastRegistration.publicKey.authenticatorSelection.userVerification, 'required')
    await assert.rejects(native.authenticate({ ...input, rpId: 'attacker.example' }), /域名/)
    assert.equal(await native.scan(), h.state.scanValue)
    h.state.scanError = 1000500002
    await assert.rejects(native.scan(), /操作已取消/)
  } finally { await h.dispose() }
})


test('API-origin scopes keep session files and ASSET keys isolated', async () => {
  const h = await harness()
  try {
    const { SecureSessionStore } = await h.load(path.join(sourceRoot, 'SecureSessionStore.ets'))
    const production = new SecureSessionStore({ filesDir: h.directory }, 'https://api.ksuser.cn')
    const development = new SecureSessionStore({ filesDir: h.directory }, 'http://192.0.2.1:8000')
    await production.save(snapshot('production'))
    assert.equal(await development.load(), null)
    await development.save(snapshot('development'))
    assert.equal((await production.load()).accessToken, 'production')
    assert.equal((await development.load()).accessToken, 'development')
    assert.equal(h.assets.size, 2)
    await development.clear()
    assert.equal((await production.load()).accessToken, 'production')
    assert.equal(h.assets.size, 1)
  } finally { await h.dispose() }
})

test('explicit secret copying uses the local-device clipboard scope', async () => {
  const h = await harness()
  try {
    const { NativeServices } = await h.load(path.join(sourceRoot, 'NativeServices.ets'))
    const native = new NativeServices({}, { passkeyRPID: 'auth.ksuser.cn' })
    await native.copyText('FICTIONAL_RECOVERY_CODE')
    assert.deepEqual(h.state.clipboard, { text: 'FICTIONAL_RECOVERY_CODE', shareOption: 1 })
  } finally { await h.dispose() }
})
