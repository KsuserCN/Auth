#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { copyFile, mkdir, readFile, readdir, rename, stat, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const usage = `Generate local Android release artifacts from a FINAL SIGNED release APK.
node scripts/generate-android-release.mjs --apk <signed.apk> --certificate-sha256 <release fingerprint>
  [--output build/android-release] [--base-url https://auth.ksuser.cn]
  [--package cn.ksuser.auth] [--version-code expected] [--version-name expected]
  [--asset-links web/public/.well-known/assetlinks.json]
  [--release-notes-file notes.txt] [--force-update]
  [--apksigner /sdk/build-tools/36.0.0/apksigner] [--aapt /sdk/build-tools/36.0.0/aapt]
Writes downloads/latest/android.apk and android.json below --output; does not publish.`;

export function parseArgs(args) {
  const options = { output: 'build/android-release', baseUrl: 'https://auth.ksuser.cn', package: 'cn.ksuser.auth',
    assetLinks: 'web/public/.well-known/assetlinks.json', forceUpdate: false };
  const keys = new Map([['--apk', 'apk'], ['--certificate-sha256', 'certificateSha256'], ['--output', 'output'],
    ['--base-url', 'baseUrl'], ['--package', 'package'], ['--version-code', 'versionCode'], ['--version-name', 'versionName'],
    ['--release-notes-file', 'releaseNotesFile'], ['--apksigner', 'apksigner'], ['--aapt', 'aapt'], ['--asset-links', 'assetLinks']]);
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--help') return { help: true };
    if (args[i] === '--force-update') { options.forceUpdate = true; continue; }
    const key = keys.get(args[i]);
    if (!key || !args[i + 1] || args[i + 1].startsWith('--')) throw new Error(`Unknown option or missing value: ${args[i]}`);
    options[key] = args[++i];
  }
  if (!options.apk || !options.certificateSha256) throw new Error('--apk and --certificate-sha256 are required');
  return options;
}

export function normalizeFingerprint(value) {
  const normalized = value.replaceAll(':', '').toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(normalized)) throw new Error('Expected a SHA-256 signing certificate fingerprint');
  return normalized;
}

export function parseArtifactMetadata(badging, verification, expectedCertificate) {
  if (!/^Verifies\s*$/m.test(verification)) throw new Error('apksigner did not confirm a valid APK signature');
  const match = badging.match(/^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'/m);
  if (!match) throw new Error('aapt did not return valid APK package/version metadata');
  if (/^application-debuggable\b/m.test(badging) || /certificate DN:.*CN\s*=\s*Android Debug(?:,|$)/mi.test(verification)) {
    throw new Error('Debug APKs and debug signing certificates cannot be published as releases');
  }
  const certificates = [...verification.matchAll(/^Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$/gmi)]
    .map(match => normalizeFingerprint(match[1]));
  if (certificates.length !== 1 || certificates[0] !== normalizeFingerprint(expectedCertificate)) {
    throw new Error('APK signer does not match the expected release certificate');
  }
  const versionCode = Number(match[2]);
  if (!Number.isSafeInteger(versionCode) || versionCode <= 0) throw new Error('APK versionCode is invalid');
  return { packageName: match[1], versionCode, versionName: match[3] };
}

export function validateAssetLinks(content, packageName, expectedCertificate) {
  const entries = JSON.parse(content);
  if (!Array.isArray(entries)) throw new Error('assetlinks.json must contain an array');
  const expected = normalizeFingerprint(expectedCertificate);
  const matches = entries.some(entry => entry?.target?.namespace === 'android_app' &&
    entry.target.package_name === packageName && Array.isArray(entry.relation) &&
    entry.relation.includes('delegate_permission/common.handle_all_urls') &&
    Array.isArray(entry.target.sha256_cert_fingerprints) &&
    entry.target.sha256_cert_fingerprints.some(value => typeof value === 'string' && normalizeFingerprint(value) === expected));
  if (!matches) throw new Error('Expected release certificate is not registered for this package in assetlinks.json');
}

export function releaseUrl(base) {
  const url = new URL(base);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('--base-url must be an HTTPS origin without credentials, query, or path');
  }
  return `${url.origin}/downloads/latest/android.apk`;
}

async function sdkTool(name, override) {
  if (override) return resolve(override);
  const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
  if (sdk) {
    const versions = await readdir(join(sdk, 'build-tools'));
    versions.sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    for (const version of versions) {
      const candidate = join(sdk, 'build-tools', version, process.platform === 'win32' && name === 'apksigner' ? `${name}.bat` : name);
      if (await stat(candidate).then(value => value.isFile()).catch(() => false)) return candidate;
    }
  }
  return name;
}

export async function generateRelease(options) {
  const apk = resolve(options.apk);
  if (!(await stat(apk)).isFile()) throw new Error('--apk must point to a finalized signed APK file');
  const apkUrl = releaseUrl(options.baseUrl);
  validateAssetLinks(await readFile(resolve(options.assetLinks || 'web/public/.well-known/assetlinks.json'), 'utf8'),
    options.package, options.certificateSha256);
  const apksigner = await sdkTool('apksigner', options.apksigner);
  const aapt = await sdkTool('aapt', options.aapt);
  const run = (tool, args) => execFileSync(tool, args, { encoding: 'utf8', maxBuffer: 4 * 1024 * 1024 });
  // Non-zero verification status aborts before any output files are changed.
  const verification = run(apksigner, ['verify', '--min-sdk-version', '24', '--verbose', '--print-certs', apk]);
  const metadata = parseArtifactMetadata(run(aapt, ['dump', 'badging', apk]), verification, options.certificateSha256);
  if (metadata.packageName !== options.package) throw new Error('APK package does not match --package');
  if (options.versionCode && String(metadata.versionCode) !== options.versionCode) throw new Error('APK versionCode differs from --version-code');
  if (options.versionName && metadata.versionName !== options.versionName) throw new Error('APK versionName differs from --version-name');
  const releaseNotes = options.releaseNotesFile ? (await readFile(resolve(options.releaseNotesFile), 'utf8')).trim() : '';
  const destination = resolve(options.output, 'downloads/latest');
  await mkdir(destination, { recursive: true });
  const stagedApk = join(destination, '.android.apk.tmp');
  const stagedManifest = join(destination, '.android.json.tmp');
  await copyFile(apk, stagedApk);
  const sha256 = createHash('sha256').update(await readFile(stagedApk)).digest('hex');
  // Verify the exact copied bytes as well, detecting modifications during artifact preparation.
  const copied = parseArtifactMetadata(run(aapt, ['dump', 'badging', stagedApk]),
    run(apksigner, ['verify', '--min-sdk-version', '24', '--verbose', '--print-certs', stagedApk]), options.certificateSha256);
  if (JSON.stringify(copied) !== JSON.stringify(metadata)) throw new Error('APK changed while generating release artifacts');
  const manifest = { versionCode: metadata.versionCode, versionName: metadata.versionName, apkUrl, sha256, releaseNotes, forceUpdate: options.forceUpdate };
  await writeFile(stagedManifest, `${JSON.stringify(manifest, null, 2)}\n`);
  await rename(stagedApk, join(destination, 'android.apk'));
  await rename(stagedManifest, join(destination, 'android.json'));
  return { manifest, manifestPath: join(destination, 'android.json'), apkPath: join(destination, 'android.apk') };
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    const options = parseArgs(process.argv.slice(2));
    if (options.help) console.log(usage);
    else {
      const result = await generateRelease(options);
      console.log(`Prepared ${result.manifestPath}\nPrepared ${result.apkPath}\nSHA-256 ${result.manifest.sha256}`);
    }
  } catch (error) {
    console.error(`Android release generation failed: ${error.message}`);
    process.exitCode = 1;
  }
}
