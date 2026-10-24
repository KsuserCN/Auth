#!/usr/bin/env node
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFile, mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { Writable } from 'node:stream';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseArgs } from 'node:util';
import { generateRelease, validateAssetLinks } from './generate-android-release.mjs';

export const releaseCertificate = 'd8febe29a88c6797fdeab6426fc2bca5efb076c201662cfc9283f070b4faa710';
const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const usage = `用法：npm run release:android -- [--release-notes-file notes.txt] [--force-update]
版本只改 mobile/android/app/build.gradle.kts 中的 versionName 和 versionCode。
默认密钥库：~/Documents/证书/Android/release.jks，别名：auth。
密码通过终端隐藏输入；也支持已有的 ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_PASSWORD 环境变量。
成功后生成 build/release.apk 和可部署的 build/web/，不会自动上传。`;

export function readAndroidVersion(source) {
  const code = source.match(/^\s*versionCode\s*=\s*(\d+)\s*$/m);
  const name = source.match(/^\s*versionName\s*=\s*"([^"\r\n]+)"\s*$/m);
  const versionCode = Number(code?.[1]);
  if (!code || !name || !Number.isSafeInteger(versionCode) || versionCode <= 0 || versionCode > 2100000000 || !name[1].trim()) {
    throw new Error('请在 mobile/android/app/build.gradle.kts 中填写有效的 versionCode 整数和 versionName 字符串。');
  }
  return { versionCode, versionName: name[1] };
}

async function exists(path) {
  return stat(path).then(value => value.isFile()).catch(() => false);
}

async function readSecret(label) {
  if (!process.stdin.isTTY || !process.stdout.isTTY) {
    throw new Error('需要在终端输入签名密码；非交互构建请设置 ANDROID_KEYSTORE_PASSWORD 和 ANDROID_KEY_PASSWORD。');
  }
  process.stdout.write(label);
  const muted = new Writable({ write(_chunk, _encoding, callback) { callback(); } });
  const input = createInterface({ input: process.stdin, output: muted, terminal: true });
  try {
    return await new Promise((accept, reject) => {
      input.once('SIGINT', () => reject(new Error('已取消签名密码输入。')));
      input.once('close', () => reject(new Error('签名密码输入已结束。')));
      input.question('', accept);
    });
  } finally {
    input.close();
    process.stdout.write('\n');
  }
}

async function runCommand(command, args, options) {
  await new Promise((accept, reject) => {
    const child = spawn(command, args, { ...options, stdio: 'inherit' });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) accept();
      else reject(new Error(`执行失败：${command}（${signal || code}）`));
    });
  });
}

async function sdkTools(sdk) {
  const directories = await readdir(join(sdk, 'build-tools')).catch(() => []);
  directories.sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
  for (const directory of directories) {
    const apksigner = join(sdk, 'build-tools', directory, 'apksigner');
    const aapt = join(sdk, 'build-tools', directory, 'aapt');
    if (await exists(apksigner) && await exists(aapt)) return { apksigner, aapt };
  }
  throw new Error('未找到 Android Build Tools；请安装 36.0.0，或设置 ANDROID_HOME 指向已有 SDK。');
}

export async function releaseAndroid({
  projectRoot = repository, environment = process.env, homeDirectory = homedir(),
  promptSecret = readSecret, run = runCommand, log = console.log,
  releaseNotesFile, forceUpdate = false,
} = {}) {
  const android = join(projectRoot, 'mobile/android');
  const version = readAndroidVersion(await readFile(join(android, 'app/build.gradle.kts'), 'utf8'));
  const env = { ...environment };
  env.ANDROID_KEYSTORE_PATH = resolve(env.ANDROID_KEYSTORE_PATH || join(homeDirectory, 'Documents/证书/Android/release.jks'));
  env.ANDROID_KEY_ALIAS ||= 'auth';
  if (!await exists(env.ANDROID_KEYSTORE_PATH)) {
    throw new Error(`未找到密钥库：${env.ANDROID_KEYSTORE_PATH}。可通过 ANDROID_KEYSTORE_PATH 指定其他路径。`);
  }
  const localProperties = await readFile(join(android, 'local.properties'), 'utf8').catch(() => '');
  const localSdk = localProperties.match(/^sdk\.dir\s*=\s*(.+)$/m)?.[1].trim().replaceAll('\\:', ':').replaceAll('\\\\', '\\');
  env.ANDROID_HOME = env.ANDROID_SDK_ROOT || env.ANDROID_HOME || localSdk || join(homeDirectory, 'Library/Android/sdk');
  const tools = await sdkTools(env.ANDROID_HOME);
  const localJava = join(homeDirectory, '.jenv/versions/21');
  if (!env.JAVA_HOME && await exists(join(localJava, 'bin/java'))) env.JAVA_HOME = localJava;
  const java = env.JAVA_HOME ? join(env.JAVA_HOME, 'bin/java') : 'java';
  const assetLinks = join(projectRoot, 'web/public/.well-known/assetlinks.json');
  validateAssetLinks(await readFile(assetLinks, 'utf8'), 'cn.ksuser.auth', releaseCertificate);
  for (const [key, label] of [
    ['ANDROID_KEYSTORE_PASSWORD', '密钥库密码（输入时不显示）：'],
    ['ANDROID_KEY_PASSWORD', `${env.ANDROID_KEY_ALIAS} 私钥密码（输入时不显示）：`],
  ]) {
    if (!env[key]) env[key] = await promptSecret(label);
    if (!env[key]) throw new Error('签名密码不能为空；已停止，未构建 unsigned 包。');
  }

  log(`准备正式版本 ${version.versionName}（${version.versionCode}），密钥库：${env.ANDROID_KEYSTORE_PATH}`);
  await run(java, [join(repository, 'scripts/VerifyAndroidSigning.java'), releaseCertificate], { cwd: projectRoot, env });
  await run(join(android, 'gradlew'), [':app:testReleaseUnitTest', ':app:assembleRelease', '--no-daemon'], { cwd: android, env });

  // Verification finishes before public files are replaced; restore them if Web packaging fails.
  const publicRoot = join(projectRoot, 'web/public');
  const publicFiles = ['android.apk', 'android.json'].map(name => join(publicRoot, 'downloads/latest', name));
  const originals = await Promise.all(publicFiles.map(path => readFile(path).catch(error => {
    if (error.code === 'ENOENT') return null;
    throw error;
  })));
  try {
    const release = await generateRelease({
      apk: join(android, 'app/build/outputs/apk/release/app-release.apk'),
      output: publicRoot, baseUrl: 'https://auth.ksuser.cn', package: 'cn.ksuser.auth',
      certificateSha256: releaseCertificate, assetLinks, ...tools,
      versionCode: String(version.versionCode), versionName: version.versionName,
      releaseNotesFile: releaseNotesFile ? resolve(projectRoot, releaseNotesFile) : undefined, forceUpdate,
    });
    // Only the Android build needs the signing secrets; do not pass them to Web tooling.
    const webEnv = { ...env };
    delete webEnv.ANDROID_KEYSTORE_PASSWORD;
    delete webEnv.ANDROID_KEY_PASSWORD;
    const npmCli = environment.npm_execpath;
    await run(npmCli ? process.execPath : 'npm', npmCli ? [npmCli, 'run', 'build:web'] : ['run', 'build:web'], { cwd: projectRoot, env: webEnv });
    const webDownload = join(projectRoot, 'build/web/downloads/latest');
    const webManifest = JSON.parse(await readFile(join(webDownload, 'android.json'), 'utf8'));
    const webHash = createHash('sha256').update(await readFile(join(webDownload, 'android.apk'))).digest('hex');
    if (JSON.stringify(webManifest) !== JSON.stringify(release.manifest) || webHash !== release.manifest.sha256) {
      throw new Error('Web 构建中的版本清单或 APK 与本次签名产物不一致。');
    }
    const output = join(projectRoot, 'build/release.apk');
    await mkdir(dirname(output), { recursive: true });
    const stagedOutput = join(projectRoot, 'build/.release.apk.tmp');
    await copyFile(release.apkPath, stagedOutput);
    if (createHash('sha256').update(await readFile(stagedOutput)).digest('hex') !== release.manifest.sha256) {
      throw new Error('复制到 build 的最终 APK 散列不匹配。');
    }
    await rename(stagedOutput, output);
    log(`完成：${version.versionName}（${version.versionCode}）\n正式 APK：${output}\n上传网站目录：${join(projectRoot, 'build/web')}\n版本清单：https://auth.ksuser.cn/downloads/latest/android.json`);
    return { apkPath: output, manifest: release.manifest };
  } catch (error) {
    for (let i = 0; i < publicFiles.length; i++) {
      if (originals[i] === null) await rm(publicFiles[i], { force: true });
      else await writeFile(publicFiles[i], originals[i]);
    }
    throw error;
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    const { values } = parseArgs({ options: {
      help: { type: 'boolean' }, 'force-update': { type: 'boolean' }, 'release-notes-file': { type: 'string' },
    } });
    if (values.help) console.log(usage);
    else await releaseAndroid({ releaseNotesFile: values['release-notes-file'], forceUpdate: values['force-update'] });
  } catch (error) {
    console.error(`正式发布构建失败：${error.message}\n本次未完成，请勿将 build 中旧的 APK 当作本次产物。`);
    process.exitCode = 1;
  }
}
