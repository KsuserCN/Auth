import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
const require = createRequire(import.meta.url)
const ts = require(process.env.HARMONY_TYPESCRIPT_PATH ?? '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript')
const root = fileURLToPath(new URL('../../main/ets/core/', import.meta.url))
const loaded = new Map()
async function load(file) {
  if (loaded.has(file)) return loaded.get(file)
  const code = ts.transpileModule(await fs.readFile(file, 'utf8'), { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, experimentalDecorators: true } }).outputText
  const dependencies = new Map()
  for (const match of code.matchAll(/require\("(\.[^"]+)"\)/g)) dependencies.set(match[1], await load(path.resolve(path.dirname(file), match[1] + '.ets')))
  const module = { exports: {} }
  new Function('require', 'module', 'exports', 'Observed', code)(name => dependencies.get(name), module, module.exports, constructor => constructor)
  loaded.set(file, module.exports); return module.exports
}
const models = await load(path.join(root, 'Models.ets'))
const presentation = await load(path.join(root, 'Presentation.ets'))
const { LogFilters } = await load(path.join(root, 'LogFilters.ets'))
const { avatarCropRegion, clampCrop } = await load(path.join(root, 'AvatarGeometry.ets'))

test('filter drafts cancel without changing applied requests and apply as complete snapshots', () => {
  const filters = new LogFilters()
  filters.begin(); filters.update('operation', 'DELETE_ACCOUNT'); filters.update('startDate', '2026-01-01')
  filters.cancel(); assert.equal(filters.applied.operation, ''); assert.equal(filters.draft.operation, '')
  filters.begin(); filters.update('operation', 'ADAPTIVE_POLICY'); filters.update('result', 'FAILURE'); filters.apply()
  const previous = filters.applied
  filters.begin(); filters.update('operation', 'LOGIN')
  assert.equal(previous.operation, 'ADAPTIVE_POLICY'); assert.equal(filters.applied.result, 'FAILURE')
  filters.cancel(); assert.equal(filters.draft.operation, 'ADAPTIVE_POLICY')
})
test('invalid or reversed calendar ranges cannot be applied', () => {
  const filters = new LogFilters(); filters.update('startDate', '2026-02-30')
  assert.throws(() => filters.apply(), /有效日期/)
  filters.update('startDate', '2026-03-01'); filters.update('endDate', '2026-02-28')
  assert.throws(() => filters.apply(), /不能晚于/)
  assert.equal(filters.applied.startDate, '')
})
test('local date/time values retain their wall date while offsets convert to device time', () => {
  process.env.TZ = 'Asia/Shanghai'
  assert.equal(models.dateText('2026-01-01'), '2026-01-01')
  assert.equal(models.dateText('2026-01-01T01:02:03'), '2026-01-01 01:02:03')
  assert.equal(models.dateText('2026-01-01T23:02:03Z'), '2026-01-02 07:02:03')
  assert.equal(models.dateGroup('2026-01-01T23:02:03Z'), '2026-01-02')
  assert.equal(models.dateText('not-a-date'), '—')
})
test('scope explanations and log labels cover the complete supported interface', () => {
  for (const scope of ['openid', 'profile', 'email', 'offline_access', 'real_name', 'gender', 'birth_date', 'region', 'bio']) {
    assert.notEqual(presentation.scopeTitle(scope), scope); assert.ok(presentation.scopeDescription(scope))
  }
  assert.ok(presentation.LOG_OPERATIONS.includes('ADAPTIVE_POLICY')); assert.ok(presentation.LOG_OPERATIONS.includes('DELETE_ACCOUNT'))
  assert.equal(presentation.logResult({ result: 'SUCCESS', operationType: 'ADAPTIVE_POLICY' }), '已执行')
  assert.equal(presentation.websiteOrigin('https://app.example/callback?code=not-displayed'), 'https://app.example')
})
test('placeholder names do not inflate completeness or disagree with display names', () => {
  const profile = { username: 'fixture', realName: '未设置', avatarUrl: 'a', region: 'b', bio: 'c' }
  assert.equal(models.displayName(profile), 'fixture'); assert.equal(models.profileCompleteness(profile), 80)
  profile.realName = 'fixture'; assert.equal(models.profileCompleteness(profile), 100)
})
test('avatar framing produces bounded square crops for wide/tall images and extreme gestures', () => {
  assert.deepEqual(avatarCropRegion(200, 100, 300, 1, 0, 0), { x: 50, y: 0, size: 100 })
  assert.deepEqual(avatarCropRegion(100, 200, 300, 1, 0, 0), { x: 0, y: 50, size: 100 })
  assert.deepEqual(avatarCropRegion(200, 100, 300, 100, 10000, -10000), { x: 0, y: 84, size: 16 })
  assert.throws(() => clampCrop(100, 100, { x: 0, y: 0, size: NaN }), /无效/)
  assert.deepEqual(clampCrop(100, 50, { x: 90, y: -5, size: 80 }), { x: 50, y: 0, size: 50 })
})
