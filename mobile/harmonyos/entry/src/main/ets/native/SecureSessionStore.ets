import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { asset } from '@kit.AssetStoreKit';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { fileIo } from '@kit.CoreFileKit';
import { SessionSnapshot, SessionStore } from '../core/Models';
import { decodeBase64Url, decodeUtf8, encodeBase64Url, encodeUtf8 } from './NativeEncoding';

export type SessionStorageFailure = 'locked' | 'unavailable' | 'corrupt';

export class SessionStorageError extends Error {
  readonly kind: SessionStorageFailure;

  constructor(kind: SessionStorageFailure) {
    super(kind === 'locked' ? '请解锁设备后重试读取登录凭据' : kind === 'corrupt' ?
      '本机登录凭据已损坏，请清除本机凭据后重新登录' : '暂时无法安全保存登录凭据，请稍后重试');
    this.kind = kind;
  }
}

interface EncryptedSession {
  version: number;
  iv: string;
  ciphertext: string;
}

function storeError(error: BusinessError): SessionStorageError {
  if ([24000004, 24000005].includes(error.code)) { return new SessionStorageError('locked'); }
  if (error.code === 24000007) { return new SessionStorageError('corrupt'); }
  return new SessionStorageError('unavailable');
}

/**
 * ASSET stores only a 32-byte encryption key. The larger authenticated session is an AES-GCM
 * ciphertext in the app sandbox. Writes and deletes are serialized; rename commits a full snapshot.
 */
export class SecureSessionStore implements SessionStore {
  private path: string;
  private temporary: string;
  private readonly directory: string;
  private readonly scope: string;
  private initialized: boolean = false;
  private initialization: Promise<void> | null = null;
  private alias: Uint8Array = encodeUtf8('cn.ksuser.auth.hmos.session.aes.v1');
  private aad: Uint8Array = encodeUtf8('KsuserAuth.HarmonyOS.Session.v1');
  private queue: Promise<void> = Promise.resolve();

  constructor(context: common.UIAbilityContext, scope: string = '') {
    this.directory = context.filesDir;
    this.scope = scope;
    this.path = context.filesDir + '/authenticated-session.enc';
    this.temporary = this.path + '.tmp';
  }

  private async initialize(): Promise<void> {
    if (this.initialized || !this.scope) { return; }
    if (this.initialization) { return this.initialization; }
    const operation = (async (): Promise<void> => {
      if (!canIUse('SystemCapability.Security.CryptoFramework.MessageDigest')) {
        throw new SessionStorageError('unavailable');
      }
      try {
        const digest = cryptoFramework.createMd('SHA256');
        await digest.update({ data: encodeUtf8(this.scope) });
        const suffix = encodeBase64Url((await digest.digest()).data);
        this.path = this.directory + '/authenticated-session-' + suffix + '.enc';
        this.temporary = this.path + '.tmp';
        this.alias = encodeUtf8('cn.ksuser.auth.hmos.session.aes.v1:' + suffix);
        this.aad = encodeUtf8('KsuserAuth.HarmonyOS.Session.v1:' + suffix);
        this.initialized = true;
      } catch (_) { throw new SessionStorageError('unavailable'); }
    })();
    this.initialization = operation;
    try { await operation; }
    finally { if (this.initialization === operation) { this.initialization = null; } }
  }

  private keyQuery(): asset.AssetMap {
    const query: asset.AssetMap = new Map();
    query.set(asset.Tag.ALIAS, this.alias);
    return query;
  }

  private async key(create: boolean): Promise<Uint8Array | null> {
    const query = this.keyQuery();
    query.set(asset.Tag.RETURN_TYPE, asset.ReturnType.ALL);
    let values: asset.AssetMap[];
    try {
      values = await asset.query(query);
    } catch (error) {
      const business = error as BusinessError;
      if (business.code !== 24000002) { throw storeError(business); }
      values = [];
    }
    if (values.length > 0) {
      const bytes = values[0].get(asset.Tag.SECRET) as Uint8Array;
      if (!(bytes instanceof Uint8Array) || bytes.length !== 32) { throw new SessionStorageError('corrupt'); }
      return bytes;
    }
    if (!create) { return null; }
    try {
      if (!canIUse('SystemCapability.Security.CryptoFramework.Rand')) { throw new SessionStorageError('unavailable'); }
      const random = await cryptoFramework.createRandom().generateRandom(32);
      const attributes = this.keyQuery();
      attributes.set(asset.Tag.SECRET, random.data);
      attributes.set(asset.Tag.ACCESSIBILITY, asset.Accessibility.DEVICE_FIRST_UNLOCKED);
      attributes.set(asset.Tag.AUTH_TYPE, asset.AuthType.NONE);
      attributes.set(asset.Tag.SYNC_TYPE, asset.SyncType.NEVER);
      // False is the default: no credentials survive app uninstall.
      await asset.add(attributes);
      return random.data;
    } catch (error) {
      const business = error as BusinessError;
      if (business.code === 24000003) { return await this.key(false); }
      throw storeError(business);
    }
  }

  async load(): Promise<SessionSnapshot | null> {
    await this.queue;
    await this.initialize();
    let serialized: string;
    try {
      if (!await fileIo.access(this.path)) { return null; }
      const stat = await fileIo.stat(this.path);
      if (stat.size < 1 || stat.size > 4 * 1024 * 1024) { throw new SessionStorageError('corrupt'); }
      serialized = await fileIo.readText(this.path);
    } catch (error) {
      if (error instanceof SessionStorageError) { throw error; }
      throw new SessionStorageError('unavailable');
    }
    let envelope: EncryptedSession;
    let iv: Uint8Array;
    let sealed: Uint8Array;
    try {
      envelope = JSON.parse(serialized) as EncryptedSession;
      if (envelope.version !== 1 || typeof envelope.iv !== 'string' || typeof envelope.ciphertext !== 'string') {
        throw new SessionStorageError('corrupt');
      }
      iv = decodeBase64Url(envelope.iv);
      sealed = decodeBase64Url(envelope.ciphertext);
      if (iv.length !== 12 || sealed.length < 16) { throw new SessionStorageError('corrupt'); }
    } catch (_) {
      throw new SessionStorageError('corrupt');
    }
    const key = await this.key(false);
    if (!key) { throw new SessionStorageError('corrupt'); }
    try {
      if (!canIUse('SystemCapability.Security.CryptoFramework.Cipher') ||
        !canIUse('SystemCapability.Security.CryptoFramework.Key.SymKey')) { throw new SessionStorageError('unavailable'); }
      const cipher = cryptoFramework.createCipher('AES256|GCM|NoPadding');
      const secret = await cryptoFramework.createSymKeyGenerator('AES256').convertKey({ data: key });
      const params: cryptoFramework.GcmParamsSpec = {
        algName: 'GcmParamsSpec', iv: { data: iv }, aad: { data: this.aad },
        authTag: { data: sealed.slice(sealed.length - 16) }
      };
      await cipher.init(cryptoFramework.CryptoMode.DECRYPT_MODE, secret, params);
      let plaintext: cryptoFramework.DataBlob;
      try {
        plaintext = await cipher.doFinal({ data: sealed.slice(0, sealed.length - 16) });
      } catch (error) {
        // Separate decryption failure from transient capability and resource errors.
        const business = error as BusinessError;
        throw new SessionStorageError(business.code === 17630001 ? 'corrupt' : 'unavailable');
      }
      try {
        const snapshot = JSON.parse(decodeUtf8(plaintext.data)) as SessionSnapshot;
        if (typeof snapshot.accessToken !== 'string' || !Array.isArray(snapshot.cookies)) {
          throw new SessionStorageError('corrupt');
        }
        return snapshot;
      } catch (_) {
        throw new SessionStorageError('corrupt');
      }
    } catch (error) {
      if (error instanceof SessionStorageError) { throw error; }
      throw new SessionStorageError('unavailable');
    } finally {
      key.fill(0);
    }
  }

  save(snapshot: SessionSnapshot): Promise<void> {
    // Capture the caller's snapshot immediately, before another request can mutate it.
    const plaintext = JSON.stringify(snapshot);
    const operation = this.queue.then(() => this.write(plaintext));
    this.queue = operation.catch(() => {});
    return operation;
  }

  private async write(plaintext: string): Promise<void> {
    await this.initialize();
    const key = await this.key(true);
    if (!key) { throw new SessionStorageError('unavailable'); }
    try {
      if (!canIUse('SystemCapability.Security.CryptoFramework.Rand') ||
        !canIUse('SystemCapability.Security.CryptoFramework.Cipher') ||
        !canIUse('SystemCapability.Security.CryptoFramework.Key.SymKey')) { throw new SessionStorageError('unavailable'); }
      const secret = await cryptoFramework.createSymKeyGenerator('AES256').convertKey({ data: key });
      const iv = (await cryptoFramework.createRandom().generateRandom(12)).data;
      const params: cryptoFramework.GcmParamsSpec = {
        algName: 'GcmParamsSpec', iv: { data: iv }, aad: { data: this.aad }, authTag: { data: new Uint8Array(16) }
      };
      const cipher = cryptoFramework.createCipher('AES256|GCM|NoPadding');
      await cipher.init(cryptoFramework.CryptoMode.ENCRYPT_MODE, secret, params);
      const ciphertext = await cipher.doFinal({ data: encodeUtf8(plaintext) });
      const envelope: EncryptedSession = { version: 1, iv: encodeBase64Url(iv), ciphertext: encodeBase64Url(ciphertext.data) };
      const bytes = encodeUtf8(JSON.stringify(envelope));
      const file = await fileIo.open(this.temporary, fileIo.OpenMode.CREATE | fileIo.OpenMode.READ_WRITE | fileIo.OpenMode.TRUNC);
      try {
        let written = 0;
        while (written < bytes.length) {
          const count = await fileIo.write(file.fd, bytes.slice(written).buffer as ArrayBuffer);
          if (count <= 0) { throw new SessionStorageError('unavailable'); }
          written += count;
        }
        await fileIo.fsync(file.fd);
      } finally {
        await fileIo.close(file);
      }
      await fileIo.rename(this.temporary, this.path);
    } catch (error) {
      try { if (await fileIo.access(this.temporary)) { await fileIo.unlink(this.temporary); } } catch (_) {}
      if (error instanceof SessionStorageError) { throw error; }
      throw new SessionStorageError('unavailable');
    } finally {
      key.fill(0);
    }
  }

  clear(): Promise<void> {
    const operation = this.queue.then(async () => {
      await this.initialize();
      try {
        if (await fileIo.access(this.path)) { await fileIo.unlink(this.path); }
        if (await fileIo.access(this.temporary)) { await fileIo.unlink(this.temporary); }
      } catch (_) {
        throw new SessionStorageError('unavailable');
      }
      try {
        await asset.remove(this.keyQuery());
      } catch (error) {
        const business = error as BusinessError;
        if (business.code !== 24000002) { throw storeError(business); }
      }
    });
    this.queue = operation.catch(() => {});
    return operation;
  }
}
