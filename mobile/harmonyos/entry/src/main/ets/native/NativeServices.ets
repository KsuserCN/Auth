import { UserFacingError } from '../core/Models';
import { common } from '@kit.AbilityKit';
import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
import { scanBarcode, scanCore } from '@kit.ScanKit';
import { fido2 } from '@kit.OnlineAuthenticationKit';
import { photoAccessHelper } from '@kit.MediaLibraryKit';
import { image } from '@kit.ImageKit';
import { fileIo } from '@kit.CoreFileKit';
import { AppEnvironment } from '../core/Environment';
import { AvatarUpload, AvatarSource, AvatarCropRegion, PasskeyAssertion, PasskeyAuthenticationOptions, PasskeyRegistration,
  PasskeyRegistrationOptions, RpEntity, UserEntity, CredentialDescriptor, CredentialParameter, AuthenticatorSelection } from '../core/Models';
import { decodeBase64Url, decodeUtf8, encodeBase64Url } from './NativeEncoding';
import { clampCrop } from '../core/AvatarGeometry';

interface ClientDataOrigin { origin: string; }

export function nativeError(error: BusinessError, operation: string): Error {
  if ([1000500002, 1021300004, 1021300010].includes(error.code)) {
    return new UserFacingError('操作已取消');
  }
  if (error.code === 1021300005) { return new UserFacingError('通行密钥操作已超时，请重试'); }
  if (error.code === 1021310006) { return new UserFacingError('未找到适用于此账号的通行密钥，请使用其他方式登录'); }
  if ([801, 1021300001].includes(error.code)) { return new UserFacingError('当前环境暂不支持此通行密钥操作，请使用其他验证方式'); }
  return new UserFacingError(typeof error.code === 'number' ? operation + '失败，请稍后重试（' + error.code + '）' :
    operation + '失败，请稍后重试');
}

function verification(value?: string): fido2.UserVerificationRequirement {
  switch (value) {
    case 'required': return fido2.UserVerificationRequirement.REQUIRED;
    case 'discouraged': return fido2.UserVerificationRequirement.DISCOURAGED;
    case 'preferred':
    case undefined: return fido2.UserVerificationRequirement.PREFERRED;
    default: throw new UserFacingError('通行密钥验证要求无效');
  }
}

function transport(value: string): fido2.AuthenticatorTransport | undefined {
  switch (value) {
    case 'usb': return fido2.AuthenticatorTransport.USB;
    case 'nfc': return fido2.AuthenticatorTransport.NFC;
    case 'ble': return fido2.AuthenticatorTransport.BLE;
    case 'hybrid': return fido2.AuthenticatorTransport.HYBRID;
    case 'internal': return fido2.AuthenticatorTransport.INTERNAL;
    case 'smart-card': return fido2.AuthenticatorTransport.SMART_CARD;
    default: return undefined;
  }
}

function descriptors(inputs: CredentialDescriptor[]): fido2.PublicKeyCredentialDescriptor[] {
  return inputs.map((input: CredentialDescriptor): fido2.PublicKeyCredentialDescriptor => {
    if (input.type !== 'public-key') { throw new UserFacingError('通行密钥凭据类型无效'); }
    const transports: fido2.AuthenticatorTransport[] = [];
    (input.transports ?? []).forEach((value: string) => {
      const result = transport(value);
      if (result !== undefined) { transports.push(result); }
    });
    return { id: decodeBase64Url(input.id), type: fido2.PublicKeyCredentialType.PUBLIC_KEY, transports: transports };
  });
}

function timeout(value?: number | string): number {
  const result = typeof value === 'string' ? Number(value) : value;
  if (result === undefined) { return 300000; }
  if (!Number.isFinite(result) || result <= 0 || result > 600000) {
    throw new UserFacingError('通行密钥请求超时参数无效');
  }
  return result;
}

/** Only native platform results are returned; signed client data is never rewritten. */
export class NativeServices {
  private context: common.UIAbilityContext;
  private environment: AppEnvironment;
  private lastOrigin: string = '';

  constructor(context: common.UIAbilityContext, environment: AppEnvironment) {
    this.context = context;
    this.environment = environment;
  }

  readLastOrigin(): string { return this.lastOrigin; }

  async scan(): Promise<string> {
    try {
      const result = await scanBarcode.startScanForResult(this.context, {
        scanTypes: [scanCore.ScanType.QR_CODE], enableMultiMode: false, enableAlbum: true
      });
      if (!result.originalValue?.trim()) { throw new UserFacingError('未识别到有效二维码，请重新扫描'); }
      return result.originalValue.trim();
    } catch (error) {
      const business = error as BusinessError;
      if (business.code !== undefined) { throw nativeError(business, '扫码'); }
      throw error as Error;
    }
  }

  async isPasskeySupported(): Promise<boolean> {
    try {
      const capabilities = await fido2.getClientCapabilities(this.context);
      return capabilities.get(fido2.ClientCapability.PASSKEY_PLATFORM_AUTHENTICATOR) === true ||
        capabilities.get(fido2.ClientCapability.USER_VERIFYING_PLATFORM_AUTHENTICATOR) === true ||
        capabilities.get(fido2.ClientCapability.HYBRID_TRANSPORT) === true;
    } catch (_) {
      return false;
    }
  }

  private rememberOrigin(clientData: Uint8Array): void {
    // The only diagnostic retained is the public Origin, never challenges or signatures.
    try {
      const data = JSON.parse(decodeUtf8(clientData)) as ClientDataOrigin;
      this.lastOrigin = typeof data.origin === 'string' ? data.origin : '';
    } catch (_) {
      this.lastOrigin = '';
    }
  }

  async authenticate(options: PasskeyAuthenticationOptions): Promise<PasskeyAssertion> {
    if (options.rpId !== this.environment.passkeyRPID) { throw new UserFacingError('通行密钥的服务域名与应用配置不一致'); }
    const inputs: CredentialDescriptor[] = typeof options.allowCredentials === 'string' ?
      JSON.parse(options.allowCredentials) as CredentialDescriptor[] : options.allowCredentials ?? [];
    const request: fido2.CredentialRequestOptions = {
      publicKey: {
        challenge: decodeBase64Url(options.challenge), rpId: options.rpId,
        timeout: timeout(options.timeout), allowCredentials: descriptors(inputs),
        userVerification: verification(options.userVerification)
      }
    };
    try {
      const credential = await fido2.authenticate(this.context, request);
      this.rememberOrigin(credential.response.clientDataJson);
      return {
        credentialRawId: encodeBase64Url(credential.rawId),
        clientDataJSON: encodeBase64Url(credential.response.clientDataJson),
        authenticatorData: encodeBase64Url(credential.response.authenticatorData),
        signature: encodeBase64Url(credential.response.signature)
      };
    } catch (error) {
      throw nativeError(error as BusinessError, '通行密钥认证');
    }
  }

  private attestation(value?: string): fido2.AttestationConveyancePreference {
    switch (value) {
      case 'direct': return fido2.AttestationConveyancePreference.DIRECT;
      case 'indirect': return fido2.AttestationConveyancePreference.INDIRECT;
      case 'enterprise': return fido2.AttestationConveyancePreference.ENTERPRISE;
      case 'none':
      case undefined: return fido2.AttestationConveyancePreference.NONE;
      default: throw new UserFacingError('通行密钥证明要求无效');
    }
  }

  async register(options: PasskeyRegistrationOptions): Promise<PasskeyRegistration> {
    const rp: RpEntity = typeof options.rp === 'string' ? JSON.parse(options.rp) as RpEntity : options.rp;
    const user: UserEntity = typeof options.user === 'string' ? JSON.parse(options.user) as UserEntity : options.user;
    const parameters: CredentialParameter[] = typeof options.pubKeyCredParams === 'string' ?
      JSON.parse(options.pubKeyCredParams) as CredentialParameter[] : options.pubKeyCredParams;
    const selection: AuthenticatorSelection = typeof options.authenticatorSelection === 'string' ?
      JSON.parse(options.authenticatorSelection) as AuthenticatorSelection : options.authenticatorSelection ?? {};
    if (rp.id !== this.environment.passkeyRPID) { throw new UserFacingError('通行密钥的服务域名与应用配置不一致'); }
    const algorithms: fido2.PublicKeyCredentialParameters[] = parameters
      .filter((item: CredentialParameter) => item.type === 'public-key' && [-7, -35, -36, -257, -258, -259, -37, -38, -39].includes(item.alg))
      .map((item: CredentialParameter): fido2.PublicKeyCredentialParameters => {
        return { type: fido2.PublicKeyCredentialType.PUBLIC_KEY, alg: item.alg as fido2.Algorithm };
      });
    if (algorithms.length === 0) { throw new UserFacingError('服务器未提供设备支持的通行密钥算法'); }
    const nativeSelection: fido2.AuthenticatorSelectionCriteria = {
      residentKey: selection.residentKey, requireResidentKey: selection.requireResidentKey,
      userVerification: verification(selection.userVerification)
    };
    if (selection.authenticatorAttachment === 'platform') {
      nativeSelection.authenticatorAttachment = fido2.AuthenticatorAttachment.PLATFORM;
    } else if (selection.authenticatorAttachment === 'cross-platform') {
      nativeSelection.authenticatorAttachment = fido2.AuthenticatorAttachment.CROSS_PLATFORM;
    }
    const request: fido2.CredentialCreationOptions = {
      publicKey: {
        rp: { id: rp.id, name: rp.name }, user: { id: decodeBase64Url(user.id), name: user.name, displayName: user.displayName },
        challenge: decodeBase64Url(options.challenge), pubKeyCredParams: algorithms,
        timeout: timeout(options.timeout), authenticatorSelection: nativeSelection,
        attestation: this.attestation(options.attestation)
      }
    };
    try {
      const credential = await fido2.register(this.context, request);
      this.rememberOrigin(credential.response.clientDataJson);
      return {
        credentialRawId: encodeBase64Url(credential.rawId),
        clientDataJSON: encodeBase64Url(credential.response.clientDataJson),
        attestationObject: encodeBase64Url(credential.response.attestationObject),
        transports: credential.response.transports.join(',')
      };
    } catch (error) {
      throw nativeError(error as BusinessError, '创建通行密钥');
    }
  }

  async copyText(text: string): Promise<void> {
    try {
      const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, text);
      const property = data.getProperty(); property.shareOption = pasteboard.ShareOption.LOCALDEVICE;
      data.setProperty(property);
      await pasteboard.getSystemPasteboard().setData(data);
    } catch (_) { throw new UserFacingError('暂时无法复制到剪贴板，请重试'); }
  }

  /** Normalize metadata before preview so gesture coordinates and the encoded crop share one image. */
  async selectAvatar(): Promise<AvatarSource> {
    let file: fileIo.File | undefined;
    let source: image.ImageSource | undefined;
    let pixels: image.PixelMap | undefined;
    let packer: image.ImagePacker | undefined;
    try {
      const picker = new photoAccessHelper.PhotoViewPicker();
      const picked = await picker.select({ MIMEType: photoAccessHelper.PhotoViewMIMETypes.IMAGE_TYPE,
        maxSelectNumber: 1 });
      if (picked.photoUris.length === 0) { throw new UserFacingError('操作已取消'); }
      file = await fileIo.open(picked.photoUris[0], fileIo.OpenMode.READ_ONLY);
      source = image.createImageSource(file.fd);
      const information = await source.getImageInfo();
      const width = information.size.width; const height = information.size.height;
      if (width < 1 || height < 1) { throw new UserFacingError('无法读取所选图片'); }
      const ratio = Math.min(1, 2048 / Math.max(width, height));
      pixels = await source.createPixelMap({ editable: true,
        desiredSize: { width: Math.max(1, Math.round(width * ratio)), height: Math.max(1, Math.round(height * ratio)) } });
      let orientation = '';
      try { orientation = (await source.getImageProperty(image.PropertyKey.ORIENTATION)).trim().toLowerCase(); } catch (_) {}
      if (['2', 'top-right'].includes(orientation)) { await pixels.flip(true, false); }
      else if (['3', 'bottom-right'].includes(orientation)) { await pixels.rotate(180); }
      else if (['4', 'bottom-left'].includes(orientation)) { await pixels.flip(false, true); }
      else if (['5', 'left-top'].includes(orientation)) { await pixels.flip(true, false); await pixels.rotate(270); }
      else if (['6', 'right-top'].includes(orientation)) { await pixels.rotate(90); }
      else if (['7', 'right-bottom'].includes(orientation)) { await pixels.flip(false, true); await pixels.rotate(90); }
      else if (['8', 'left-bottom'].includes(orientation)) { await pixels.rotate(270); }
      const normalized = await pixels.getImageInfo();
      packer = image.createImagePacker();
      const bytes = await packer.packToData(pixels, { format: 'image/jpeg', quality: 95 });
      return { data: bytes, width: normalized.size.width, height: normalized.size.height };
    } catch (error) {
      if ((error as Error).message === '操作已取消') { throw new UserFacingError('操作已取消'); }
      throw new UserFacingError('无法读取所选图片，请尝试其他图片');
    } finally {
      // Releasing native resources must not replace a successful encoding or its original failure.
      try { if (packer) { await packer.release(); } } catch (_) {}
      try { if (pixels) { await pixels.release(); } } catch (_) {}
      try { if (source) { await source.release(); } } catch (_) {}
      try { if (file) { await fileIo.close(file); } } catch (_) {}
    }
  }

  async cropAvatar(source: AvatarSource, region: AvatarCropRegion): Promise<AvatarUpload> {
    const crop = clampCrop(source.width, source.height, region);
    const decoder = image.createImageSource(source.data);
    let pixels: image.PixelMap | undefined;
    let packer: image.ImagePacker | undefined;
    try {
      // Crop in the normalized source coordinates used by the preview, then scale the selected square.
      pixels = await decoder.createPixelMap({ editable: true });
      await pixels.crop({ x: crop.x, y: crop.y, size: { width: crop.size, height: crop.size } });
      await pixels.scale(512 / crop.size, 512 / crop.size);
      packer = image.createImagePacker();
      return { data: await packer.packToData(pixels, { format: 'image/jpeg', quality: 88 }), mimeType: 'image/jpeg' };
    } finally {
      try { await packer?.release(); } catch (_) {}
      try { await pixels?.release(); } catch (_) {}
      try { await decoder.release(); } catch (_) {}
    }
  }

  async pickAvatar(): Promise<AvatarUpload> {
    const source = await this.selectAvatar();
    const side = Math.min(source.width, source.height);
    return this.cropAvatar(source, { x: (source.width - side) / 2, y: (source.height - side) / 2, size: side });
  }
}
