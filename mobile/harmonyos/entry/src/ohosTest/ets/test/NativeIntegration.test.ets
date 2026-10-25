import { common, UIAbility } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { abilityDelegatorRegistry } from '@kit.TestKit';
import { fileIo } from '@kit.CoreFileKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fido2 } from '@kit.OnlineAuthenticationKit';
import { http } from '@kit.NetworkKit';
import { image } from '@kit.ImageKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { describe, it, expect } from '@ohos/hypium';
import { SecureSessionStore, SessionStorageError } from '../../../main/ets/native/SecureSessionStore';
import { decodeBase64Url, encodeBase64Url, encodeUtf8 } from '../../../main/ets/native/NativeEncoding';
import { ApiClient, MemorySessionStore } from '../../../main/ets/core/ApiClient';
import { AppEnvironment } from '../../../main/ets/core/Environment';
import { NativeHttpTransport, HttpTransport, TransportRequest, TransportResponse } from '../../../main/ets/core/HttpTransport';
import { PasswordRequirement, SessionSnapshot, StoredCookie } from '../../../main/ets/core/Models';
import { NativeServices } from '../../../main/ets/native/NativeServices';

const DOMAIN = 0x0000;
const TAG = 'HarmonyNativeIntegration';
const FIXTURE_TOKEN = 'NOT_A_REAL_TOKEN_HARMONY_SYSTEM_KIT_TEST';
let sequence: number = 0;

interface SessionEnvelope { version: number; iv: string; ciphertext: string; }

class CountingNativeTransport implements HttpTransport {
  count: number = 0;
  private native: NativeHttpTransport = new NativeHttpTransport();
  async send(request: TransportRequest): Promise<TransportResponse> {
    this.count++;
    return this.native.send(request);
  }
}

async function foregroundContext(): Promise<common.UIAbilityContext> {
  // Use the actual foreground UIAbility. A generic application Context is never passed to FIDO2.
  const delegator = abilityDelegatorRegistry.getAbilityDelegator();
  try {
    const current = await delegator.getCurrentTopAbility();
    if (current?.context) { return current.context; }
  } catch (_) {
    // An ability started outside this test runner may not yet be tracked by the delegator.
  }
  let receiveAbility: (ability: UIAbility) => void = (_ability: UIAbility): void => {};
  let timeoutId: number = -1;
  const ready = new Promise<UIAbility | undefined>((resolve) => {
    receiveAbility = (ability: UIAbility): void => { if (ability?.context) { resolve(ability); } };
    // Resolve rather than reject so a slow native start cannot create an unhandled rejection.
    timeoutId = setTimeout(() => { resolve(undefined); }, 7000);
  });
  const monitor: abilityDelegatorRegistry.AbilityMonitor = {
    abilityName: 'EntryAbility', moduleName: 'entry',
    // Listen for both creation and foregrounding: an existing ability need not be recreated.
    onAbilityCreate: (ability: UIAbility): void => { receiveAbility(ability); },
    onAbilityForeground: (ability: UIAbility): void => { receiveAbility(ability); }
  };
  let registered: boolean = false;
  try {
    await delegator.addAbilityMonitor(monitor);
    registered = true;
    await delegator.startAbility({ bundleName: 'cn.ksuser.auth.hmos', moduleName: 'entry', abilityName: 'EntryAbility' });
    try {
      const current = await delegator.getCurrentTopAbility();
      if (current?.context) { receiveAbility(current); }
    } catch (_) {
      // The registered lifecycle monitor can still deliver the actual UIAbility.
    }
    const ability = await ready;
    if (!ability?.context) { throw new Error('EntryAbility did not become available to the native integration tests'); }
    return ability.context;
  } finally {
    clearTimeout(timeoutId);
    if (registered) { await delegator.removeAbilityMonitor(monitor); }
  }
}

function isolatedScope(label: string): string {
  sequence++;
  return 'HARMONY_SYSTEM_KIT_TEST_ONLY/' + label + '/' + Date.now() + '/' + sequence;
}

function fixture(): SessionSnapshot {
  return {
    accessToken: FIXTURE_TOKEN, apiBaseURL: 'https://fixture.example.invalid',
    cookies: [{ name: 'XSRF-TOKEN', value: 'FICTIONAL_TEST_CSRF', domain: 'fixture.example.invalid',
      path: '/', secure: true, hostOnly: true }],
    profile: { uuid: 'FICTIONAL_ACCOUNT_ONLY', username: 'system-kit-fixture', email: 'fixture@example.invalid',
      realName: '虚构测试资料', bio: '虚构资料，仅用于系统组件测试。'.repeat(256) }
  };
}

async function testCiphertextPath(context: common.UIAbilityContext, scope: string): Promise<string> {
  const digest = cryptoFramework.createMd('SHA256');
  await digest.update({ data: encodeUtf8(scope) });
  const suffix = encodeBase64Url((await digest.digest()).data);
  return context.filesDir + '/authenticated-session-' + suffix + '.enc';
}

async function cleanup(store: SecureSessionStore): Promise<void> {
  try { await store.clear(); }
  catch (error) {
    // This only concerns an independently scoped fixture, never an existing account session.
    const kind = error instanceof SessionStorageError ? error.kind : 'native-error';
    hilog.error(DOMAIN, TAG, 'FIXTURE_CLEANUP_FAILED kind=%{public}s', kind);
  }
}

export default function nativeIntegrationTest(): void {
  describe('HarmonyNativeIntegration', () => {
    it('realAssetAesSessionRoundTripAndClear', 0, async () => {
      const context = await foregroundContext();
      const scope = isolatedScope('round-trip');
      const store = new SecureSessionStore(context, scope);
      try {
        expect(await store.load()).assertEqual(null);
        const input = fixture();
        await store.save(input);
        // A new store instance forces a real ASSET lookup and decryption from disk.
        const reopened = new SecureSessionStore(context, scope);
        const loaded = await reopened.load();
        expect(loaded !== null).assertTrue();
        expect(loaded!.accessToken).assertEqual(FIXTURE_TOKEN);
        expect(loaded!.profile!.uuid).assertEqual(input.profile!.uuid);
        expect(loaded!.profile!.bio).assertEqual(input.profile!.bio);
        expect(loaded!.cookies[0].value).assertEqual('FICTIONAL_TEST_CSRF');
        const serialized = await fileIo.readText(await testCiphertextPath(context, scope));
        expect(serialized.includes(FIXTURE_TOKEN)).assertFalse();
        expect(serialized.includes('fixture@example.invalid')).assertFalse();
        await store.clear();
        expect(await reopened.load()).assertEqual(null);
        hilog.info(DOMAIN, TAG, 'ASSET_AES_ROUND_TRIP_OK fixture-only');
      } finally { await cleanup(store); }
    });

    it('realAesRejectsTamperedFixtureWithoutDeletingIt', 0, async () => {
      const context = await foregroundContext();
      const scope = isolatedScope('tamper');
      const store = new SecureSessionStore(context, scope);
      try {
        await store.save(fixture());
        const filename = await testCiphertextPath(context, scope);
        const envelope = JSON.parse(await fileIo.readText(filename)) as SessionEnvelope;
        const bytes = decodeBase64Url(envelope.ciphertext);
        bytes[0] = bytes[0] ^ 1;
        envelope.ciphertext = encodeBase64Url(bytes);
        const file = await fileIo.open(filename, fileIo.OpenMode.WRITE_ONLY | fileIo.OpenMode.TRUNC);
        try {
          await fileIo.write(file.fd, JSON.stringify(envelope));
          await fileIo.fsync(file.fd);
        } finally { await fileIo.close(file); }
        let rejected: boolean = false;
        try { await store.load(); }
        catch (error) {
          rejected = error instanceof SessionStorageError && error.kind === 'corrupt';
          if (!rejected) {
            const kind = error instanceof SessionStorageError ? error.kind : 'native-error';
            hilog.error(DOMAIN, TAG, 'TAMPER_RESULT kind=%{public}s', kind);
          }
        }
        expect(rejected).assertTrue();
        expect(await fileIo.access(filename)).assertTrue();
        hilog.info(DOMAIN, TAG, 'ASSET_AES_TAMPER_REJECTED fixture-only');
      } finally { await cleanup(store); }
    });

    it('realPublicPasswordPolicyThroughNativeHttp', 0, async () => {
      const client = new ApiClient(new AppEnvironment(), new MemorySessionStore(), new NativeHttpTransport());
      const policy = await client.request<PasswordRequirement>('/info/password-requirement', 'GET', undefined, false);
      expect(policy.minLength > 0).assertTrue();
      expect(policy.maxLength >= policy.minLength).assertTrue();
      expect(typeof policy.requireUppercase).assertEqual('boolean');
      expect(typeof policy.requireLowercase).assertEqual('boolean');
      expect(typeof policy.requireDigits).assertEqual('boolean');
      expect(client.snapshot().accessToken.length).assertEqual(0);
      hilog.info(DOMAIN, TAG, 'PUBLIC_PASSWORD_POLICY_OK no-account');
    });

    it('realCsrfCookieThroughNativeHttpWithoutLoggingValues', 0, async () => {
      const transport = new CountingNativeTransport();
      const client = new ApiClient(new AppEnvironment(), new MemorySessionStore(), transport);
      await Promise.all([client.ensureCsrf(), client.ensureCsrf()]);
      expect(transport.count).assertEqual(1);
      const present = client.snapshot().cookies.some((cookie: StoredCookie) =>
        cookie.name === 'XSRF-TOKEN' && cookie.value.length > 0);
      expect(present).assertTrue();
      expect(client.snapshot().accessToken.length).assertEqual(0);
      expect(client.snapshot().profile).assertEqual(null);
      await client.ensureCsrf();
      expect(transport.count).assertEqual(1);
      hilog.info(DOMAIN, TAG, 'PUBLIC_CSRF_COOKIE_OK values-not-logged');
    });

    it('rawCsrfResponseShapeWithoutLoggingValues', 0, async () => {
      const connection = http.createHttp();
      try {
        const response = await connection.request('https://api.ksuser.cn/auth/csrf-token', {
          method: http.RequestMethod.GET, expectDataType: http.HttpDataType.STRING,
          connectTimeout: 15000, readTimeout: 15000, usingCache: false, maxRedirects: 0
        });
        const cookies = response.cookies ?? '';
        // Only shape metadata leaves this test. Do not print headers, cookies, or response content.
        hilog.info(DOMAIN, TAG,
          'RAW_CSRF_SHAPE cookiesLength=%{public}d hasTab=%{public}s hasNewline=%{public}s headerHasSetCookie=%{public}s',
          cookies.length, String(cookies.includes('\t')), String(/[\r\n]/.test(cookies)),
          String(JSON.stringify(response.header).toLowerCase().includes('set-cookie')));
        expect(response.responseCode).assertEqual(200);
      } finally { connection.destroy(); }
    });

    it('realAvatarCropUsesSelectedRegionAndEncodes512Jpeg', 0, async () => {
      const colors = new Uint8Array(64 * 32 * 4);
      for (let y = 0; y < 32; y++) {
        for (let x = 0; x < 64; x++) {
          const offset = (y * 64 + x) * 4;
          colors[offset] = x < 32 ? 255 : 0; colors[offset + 1] = 0;
          colors[offset + 2] = x >= 32 ? 255 : 0; colors[offset + 3] = 255;
        }
      }
      const pixels = await image.createPixelMap(colors.buffer as ArrayBuffer, {
        size: { width: 64, height: 32 }, srcPixelFormat: image.PixelMapFormat.RGBA_8888,
        pixelFormat: image.PixelMapFormat.RGBA_8888, alphaType: image.AlphaType.OPAQUE });
      const packer = image.createImagePacker();
      let decoder: image.ImageSource | undefined;
      let output: image.PixelMap | undefined;
      try {
        const bytes = await packer.packToData(pixels, { format: 'image/jpeg', quality: 95 });
        const service = new NativeServices(await foregroundContext(), new AppEnvironment());
        // The right half is blue. The encoded output must follow the selected crop, not a center crop.
        const result = await service.cropAvatar({ data: bytes, width: 64, height: 32 }, { x: 32, y: 0, size: 32 });
        expect(result.mimeType).assertEqual('image/jpeg');
        decoder = image.createImageSource(result.data);
        const info = await decoder.getImageInfo();
        expect(info.size.width).assertEqual(512); expect(info.size.height).assertEqual(512);
        output = await decoder.createPixelMap({ desiredPixelFormat: image.PixelMapFormat.RGBA_8888 });
        const rendered = new ArrayBuffer(512 * 512 * 4);
        await output.readPixelsToBuffer(rendered);
        const sample = new Uint8Array(rendered);
        // A center crop or full-image scale would include red at the left sample.
        for (const x of [64, 256, 448]) {
          const offset = (256 * 512 + x) * 4;
          expect(sample[offset] < 40).assertTrue(); expect(sample[offset + 2] > 200).assertTrue();
        }
        hilog.info(DOMAIN, TAG, 'AVATAR_SELECTED_REGION_512_JPEG_OK fixture-only');
      } finally {
        try { await output?.release(); } catch (_) {}
        try { await decoder?.release(); } catch (_) {}
        await packer.release(); await pixels.release();
      }
    });

    it('fidoCapabilitiesNonInteractiveDiagnostic', 0, async () => {
      let context: common.UIAbilityContext;
      try { context = await foregroundContext(); }
      catch (_) {
        hilog.warn(DOMAIN, TAG, 'FIDO2_QUERY_NOT_RUN no-running-uiability');
        return;
      }
      try {
        const capabilities = await fido2.getClientCapabilities(context);
        const authenticators = await fido2.getPlatformAuthenticators(context);
        const availableCount = authenticators.filter((item: fido2.AuthenticatorMetadata) => item.isAvailable).length;
        hilog.info(DOMAIN, TAG,
          'FIDO2_QUERY_OK platform=%{public}s verifying=%{public}s hybrid=%{public}s authenticators=%{public}d available=%{public}d',
          String(capabilities.get(fido2.ClientCapability.PASSKEY_PLATFORM_AUTHENTICATOR) === true),
          String(capabilities.get(fido2.ClientCapability.USER_VERIFYING_PLATFORM_AUTHENTICATOR) === true),
          String(capabilities.get(fido2.ClientCapability.HYBRID_TRANSPORT) === true), authenticators.length, availableCount);
        expect(capabilities instanceof Map).assertTrue();
      } catch (error) {
        // Report the exact public error code; service failure does not imply unsupported hardware.
        const code = (error as BusinessError).code;
        hilog.warn(DOMAIN, TAG, 'FIDO2_QUERY_UNAVAILABLE code=%{public}s', String(code));
        expect(typeof code).assertEqual('number');
      }
    });
  });
}
