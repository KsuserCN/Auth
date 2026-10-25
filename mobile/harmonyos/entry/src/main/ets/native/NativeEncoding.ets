import { util } from '@kit.ArkTS';

const base64 = new util.Base64Helper();

export function encodeBase64Url(bytes: Uint8Array): string {
  return base64.encodeToStringSync(bytes, util.Type.BASIC_URL_SAFE).replace(/=+$/, '');
}

export function decodeBase64Url(value: string): Uint8Array {
  if (!value || !/^[A-Za-z0-9_-]+={0,2}$/.test(value) || value.length % 4 === 1) {
    throw new Error('通行密钥数据格式无效');
  }
  return base64.decodeSync(value.replace(/=+$/, ''), util.Type.BASIC_URL_SAFE);
}

export function encodeUtf8(value: string): Uint8Array {
  return value.length === 0 ? new Uint8Array() : util.TextEncoder.create('utf-8').encodeInto(value);
}

export function decodeUtf8(value: Uint8Array): string {
  return util.TextDecoder.create('utf-8', { fatal: true }).decodeToString(value);
}
