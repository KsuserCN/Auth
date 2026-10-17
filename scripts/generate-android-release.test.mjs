import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { generateRelease, normalizeFingerprint, parseArgs, parseArtifactMetadata, releaseUrl, validateAssetLinks } from './generate-android-release.mjs';

const fingerprint = 'a'.repeat(64);
const verification = `Verifies\nSigner #1 certificate DN: CN=Ksuser Release\nSigner #1 certificate SHA-256 digest: ${fingerprint}\n`;
const badging = "package: name='cn.ksuser.auth' versionCode='13' versionName='1.0.0.13' platformBuildVersionName='16'\n";
const assetLinks = JSON.stringify([{ relation: ['delegate_permission/common.handle_all_urls'], target: {
  namespace: 'android_app', package_name: 'cn.ksuser.auth', sha256_cert_fingerprints: [fingerprint] } }]);

test('requires an APK and expected certificate, and validates URL and fingerprint', () => {
  assert.throws(() => parseArgs(['--apk', 'signed.apk']));
  assert.throws(() => parseArgs(['--unexpected', 'value']));
  assert.equal(normalizeFingerprint('AA:'.repeat(31) + 'AA'), fingerprint);
  assert.throws(() => normalizeFingerprint('bad'));
  assert.equal(releaseUrl('https://auth.ksuser.cn'), 'https://auth.ksuser.cn/downloads/latest/android.apk');
  for (const url of ['http://auth.ksuser.cn', 'https://user:password@auth.ksuser.cn', 'https://auth.ksuser.cn/path', 'https://auth.ksuser.cn?query']) {
    assert.throws(() => releaseUrl(url));
  }
});

test('derives package/version from the artifact and refuses unsigned, debug and unexpected certificates', () => {
  assert.deepEqual(parseArtifactMetadata(badging, verification, fingerprint), { packageName: 'cn.ksuser.auth', versionCode: 13, versionName: '1.0.0.13' });
  assert.throws(() => parseArtifactMetadata(badging, '', fingerprint));
  assert.throws(() => parseArtifactMetadata(`${badging}application-debuggable\n`, verification, fingerprint));
  assert.throws(() => parseArtifactMetadata(badging, verification.replace('Ksuser Release', 'Android Debug'), fingerprint));
  assert.throws(() => parseArtifactMetadata(badging, verification, 'b'.repeat(64)));
});

test('requires the expected release certificate for the correct package in website assetlinks', () => {
  validateAssetLinks(assetLinks, 'cn.ksuser.auth', fingerprint);
  assert.throws(() => validateAssetLinks(assetLinks, 'other.package', fingerprint), /assetlinks/);
  assert.throws(() => validateAssetLinks(assetLinks, 'cn.ksuser.auth', 'b'.repeat(64)), /assetlinks/);
  assert.throws(() => validateAssetLinks(assetLinks.replace('android_app', 'web'), 'cn.ksuser.auth', fingerprint), /assetlinks/);
  assert.throws(() => validateAssetLinks('{}', 'cn.ksuser.auth', fingerprint));
});

test('prepares deterministic copied APK and manifest, and does not replace outputs after verification failure', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'ksuser-android-release-'));
  try {
    const apk = join(directory, 'signed.apk');
    const apksigner = join(directory, 'apksigner');
    const aapt = join(directory, 'aapt');
    const output = join(directory, 'output');
    const assetLinksFile = join(directory, 'assetlinks.json');
    await writeFile(assetLinksFile, assetLinks);
    await writeFile(apk, 'final signed APK bytes');
    await writeFile(apksigner, `#!/usr/bin/env node\nimport fs from 'node:fs';\nif (fs.readFileSync(process.argv.at(-1), 'utf8') === 'unsigned') process.exit(1);\nconsole.log(${JSON.stringify(verification)});\n`, { mode: 0o755 });
    await writeFile(aapt, `#!/usr/bin/env node\nconsole.log(${JSON.stringify(badging)});\n`, { mode: 0o755 });
    const options = parseArgs(['--apk', apk, '--certificate-sha256', fingerprint, '--output', output, '--apksigner', apksigner, '--aapt', aapt, '--asset-links', assetLinksFile]);
    const first = await generateRelease(options);
    const content = await readFile(first.manifestPath, 'utf8');
    assert.equal(first.manifest.sha256, createHash('sha256').update(await readFile(apk)).digest('hex'));
    assert.deepEqual(await readFile(first.apkPath), await readFile(apk));
    await generateRelease(options);
    assert.equal(await readFile(first.manifestPath, 'utf8'), content);
    await assert.rejects(() => generateRelease({ ...options, certificateSha256: 'b'.repeat(64) }), /assetlinks/);
    assert.equal(await readFile(first.manifestPath, 'utf8'), content);
    await assert.rejects(() => generateRelease({ ...options, versionCode: '999' }), /versionCode/);
    await writeFile(apk, 'unsigned');
    await assert.rejects(() => generateRelease(options));
    assert.equal(await readFile(first.manifestPath, 'utf8'), content);
    assert.equal(await readFile(first.apkPath, 'utf8'), 'final signed APK bytes');
  } finally { await rm(directory, { recursive: true, force: true }); }
});
