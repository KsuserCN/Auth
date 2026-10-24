import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { readAndroidVersion, releaseAndroid, releaseCertificate } from './release-android.mjs';

async function fixture(t, failure) {
  const root = await mkdtemp(join(tmpdir(), 'ksuser-release-workflow-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const file = async (relative, content, options) => {
    const path = join(root, relative);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, content, options);
    return path;
  };
  await file('mobile/android/app/build.gradle.kts', 'versionCode = 14\nversionName = "1.0.0.14"\n');
  const keystore = await file('home/Documents/证书/Android/release.jks', 'fixture key: Java preflight is mocked');
  await file('web/public/.well-known/assetlinks.json', JSON.stringify([{ relation: ['delegate_permission/common.handle_all_urls'],
    target: { namespace: 'android_app', package_name: 'cn.ksuser.auth', sha256_cert_fingerprints: [releaseCertificate] } }]));
  const verification = `Verifies\nSigner #1 certificate DN: CN=Ksuser\nSigner #1 certificate SHA-256 digest: ${releaseCertificate}\n`;
  await file('sdk/build-tools/36.0.0/apksigner', `#!/usr/bin/env node
import fs from 'node:fs';
if (fs.readFileSync(process.argv.at(-1), 'utf8') === 'invalid signature') process.exit(1);
console.log(${JSON.stringify(verification)});
`, { mode: 0o755 });
  await file('sdk/build-tools/36.0.0/aapt', `#!/usr/bin/env node
import fs from 'node:fs';
const apk = JSON.parse(fs.readFileSync(process.argv.at(-1), 'utf8'));
console.log("package: name='cn.ksuser.auth' versionCode='" + apk.versionCode + "' versionName='" + apk.versionName + "'");
`, { mode: 0o755 });
  const oldApk = 'previous verified APK';
  const oldManifest = '{"previous":true}\n';
  await file('web/public/downloads/latest/android.apk', oldApk);
  await file('web/public/downloads/latest/android.json', oldManifest);
  await file('build/release.apk', oldApk);
  const environment = { ANDROID_HOME: join(root, 'sdk'), ANDROID_KEYSTORE_PATH: keystore,
    ANDROID_KEYSTORE_PASSWORD: 'fixture-store-password', ANDROID_KEY_PASSWORD: 'fixture-key-password' };
  const calls = [];
  const logs = [];
  const newApk = JSON.stringify({ versionCode: failure === 'version' ? 13 : 14, versionName: '1.0.0.14' });
  const run = async (command, args, options) => {
    calls.push({ command, args, env: options.env });
    if (args[0]?.endsWith('VerifyAndroidSigning.java')) {
      if (failure === 'key') throw new Error('signing key rejected');
    } else if (args.includes(':app:assembleRelease')) {
      if (failure === 'gradle') throw new Error('Gradle failed');
      await file('mobile/android/app/build/outputs/apk/release/app-release.apk', failure === 'signature' ? 'invalid signature' : newApk);
    } else if (args.includes('build:web')) {
      if (failure === 'web') throw new Error('Web build failed');
      for (const name of ['android.apk', 'android.json']) {
        await mkdir(join(root, 'build/web/downloads/latest'), { recursive: true });
        await copyFile(join(root, 'web/public/downloads/latest', name), join(root, 'build/web/downloads/latest', name));
      }
      if (failure === 'web-apk') await file('build/web/downloads/latest/android.apk', 'wrong APK bytes');
    } else throw new Error('Unexpected build command');
  };
  return { root, file, environment, calls, logs, oldApk, oldManifest, newApk,
    options: { projectRoot: root, homeDirectory: join(root, 'home'), environment, run, log: value => logs.push(value),
      promptSecret: async () => { throw new Error('Unexpected password prompt'); } } };
}

test('reads versionName and versionCode from Android and rejects invalid build numbers', () => {
  assert.deepEqual(readAndroidVersion('    versionCode = 14\n    versionName = "1.0.1"\n'), { versionCode: 14, versionName: '1.0.1' });
  for (const code of ['0', '-1', '2100000001', 'missing']) {
    assert.throws(() => readAndroidVersion(`versionCode = ${code}\nversionName = "1.0.1"`));
  }
  assert.throws(() => readAndroidVersion('versionCode = 14\nversionName = " "'));
});

test('one command verifies signing first, tests Release, and packages matching APK and website', async t => {
  const f = await fixture(t);
  await f.file('notes.txt', 'Release notes\n');
  const result = await releaseAndroid({ ...f.options, releaseNotesFile: 'notes.txt', forceUpdate: true });
  assert.equal(await readFile(result.apkPath, 'utf8'), f.newApk);
  assert.equal(result.manifest.versionCode, 14);
  assert.equal(result.manifest.versionName, '1.0.0.14');
  assert.equal(result.manifest.releaseNotes, 'Release notes');
  assert.equal(result.manifest.forceUpdate, true);
  assert.equal(result.manifest.sha256, createHash('sha256').update(f.newApk).digest('hex'));
  assert.equal(result.manifest.apkUrl, 'https://auth.ksuser.cn/downloads/latest/android.apk');
  assert.deepEqual(JSON.parse(await readFile(join(f.root, 'build/web/downloads/latest/android.json'), 'utf8')), result.manifest);
  assert.ok(f.calls[0].args[0].endsWith('VerifyAndroidSigning.java'));
  assert.ok(f.calls[1].args.includes(':app:testReleaseUnitTest'));
  assert.ok(f.calls[1].args.includes(':app:assembleRelease'));
  assert.equal(f.calls[2].env.ANDROID_KEYSTORE_PASSWORD, undefined);
  assert.equal(f.calls[2].env.ANDROID_KEY_PASSWORD, undefined);
  assert.ok(!f.logs.join('\n').includes(f.environment.ANDROID_KEYSTORE_PASSWORD));
  assert.ok(!f.logs.join('\n').includes(f.environment.ANDROID_KEY_PASSWORD));
});

test('empty private key password stops before any build and preserves existing output', async t => {
  const f = await fixture(t);
  delete f.environment.ANDROID_KEY_PASSWORD;
  await assert.rejects(releaseAndroid({ ...f.options, promptSecret: async () => '' }), /密码不能为空/);
  assert.equal(f.calls.length, 0);
  assert.equal(await readFile(join(f.root, 'build/release.apk'), 'utf8'), f.oldApk);
});

for (const failure of ['key', 'gradle', 'signature', 'version', 'web', 'web-apk']) {
  test(`${failure} failure never replaces the final APK or leaves changed public downloads`, async t => {
    const f = await fixture(t, failure);
    await assert.rejects(releaseAndroid(f.options));
    assert.equal(await readFile(join(f.root, 'build/release.apk'), 'utf8'), f.oldApk);
    assert.equal(await readFile(join(f.root, 'web/public/downloads/latest/android.apk'), 'utf8'), f.oldApk);
    assert.equal(await readFile(join(f.root, 'web/public/downloads/latest/android.json'), 'utf8'), f.oldManifest);
    if (failure === 'key') assert.equal(f.calls.length, 1);
    if (failure === 'gradle') assert.equal(f.calls.length, 2);
  });
}
