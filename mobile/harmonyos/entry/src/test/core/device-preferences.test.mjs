import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const ts = require(process.env.HARMONY_TYPESCRIPT_PATH ?? '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript')
const source = await fs.readFile(new URL('../../main/ets/core/DevicePreferences.ets', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
function harness(initial = {}) {
  const values = new Map(Object.entries(initial)); const storage = new Map(); const colors = []
  const state = { fail: false, writes: [] }
  const preferences = { async getPreferences() { return {
    async get(name, fallback) { return values.has(name) ? values.get(name) : fallback },
    async put(name, value) { state.writes.push(name); values.set(name, value) },
    async flush() { if (state.fail) throw new Error('fixture failure') }
  } } }
  const module = { exports: {} }
  new Function('require', 'module', 'exports', 'AppStorage', code)(name => {
    if (name === '@kit.ArkData') return { preferences }
    if (name === '@kit.AbilityKit') return { ConfigurationConstant: { ColorMode: {
      COLOR_MODE_NOT_SET: 0, COLOR_MODE_LIGHT: 1, COLOR_MODE_DARK: 2 } } }
    throw new Error('Unexpected preference dependency ' + name)
  }, module, module.exports, { setOrCreate(name, value) { storage.set(name, value) } })
  return { api: module.exports, values, storage, colors, state,
    context: { getApplicationContext() { return { setColorMode(value) { colors.push(value) } } } } }
}
test('appearance and agreement are restored before the first page is loaded', async () => {
  const h = harness({ appearance: 2, agreementAccepted: true })
  await h.api.initializeDevicePreferences(h.context)
  assert.equal(h.storage.get('appearance'), 2); assert.equal(h.storage.get('agreementAccepted'), true)
  assert.deepEqual(h.colors, [2])
  await h.api.setAppearance(h.context, 1); await h.api.setAgreementAccepted(false)
  await h.api.initializeDevicePreferences(h.context)
  assert.equal(h.storage.get('appearance'), 1); assert.equal(h.storage.get('agreementAccepted'), false)
  assert.deepEqual(h.state.writes, ['appearance', 'agreementAccepted'])
})
test('a failed preference write does not change the visible theme or break later writes', async () => {
  const h = harness({ appearance: 0 }); await h.api.initializeDevicePreferences(h.context)
  h.state.fail = true
  await assert.rejects(h.api.setAppearance(h.context, 2))
  assert.equal(h.storage.get('appearance'), 0); assert.deepEqual(h.colors, [0])
  h.state.fail = false; await h.api.setAppearance(h.context, 1)
  assert.equal(h.storage.get('appearance'), 1); assert.equal(h.colors.at(-1), 1)
})
test('malformed preference values fall back to safe device defaults', async () => {
  const h = harness({ appearance: 999, agreementAccepted: 'true' })
  await h.api.initializeDevicePreferences(h.context)
  assert.equal(h.storage.get('appearance'), 0); assert.equal(h.storage.get('agreementAccepted'), false)
})
test('explicit agreement changes remain effective for this run if persistence is temporarily unavailable', async () => {
  const h = harness({ agreementAccepted: true }); await h.api.initializeDevicePreferences(h.context)
  h.state.fail = true
  await assert.rejects(h.api.setAgreementAccepted(false))
  assert.equal(h.storage.get('agreementAccepted'), false)
  await assert.rejects(h.api.setAgreementAccepted(true))
  assert.equal(h.storage.get('agreementAccepted'), true)
})
