import { UserFacingError } from './Models';
import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import BuildProfile from 'BuildProfile';
import { parseUrl } from './Cookies';

interface EnvironmentConfig { apiBaseURL?: string; webOrigin?: string; passkeyRPID?: string; filingNumber?: string; }
export class AppEnvironment {
  apiBaseURL: string = 'https://api.ksuser.cn';
  webOrigin: string = 'https://auth.ksuser.cn';
  passkeyRPID: string = 'auth.ksuser.cn';
  userAgent: string = 'KsuserAuthMobile/' + BuildProfile.VERSION_NAME + ' (HarmonyOS)';
  debug: boolean = BuildProfile.DEBUG;
  filingNumber: string = '';

  static async load(context: common.UIAbilityContext): Promise<AppEnvironment> {
    const result = new AppEnvironment();
    try { result.filingNumber = context.resourceManager.getStringByNameSync('filing_number').trim(); } catch (_) {}
    if (BuildProfile.DEBUG) {
      let bytes: Uint8Array;
      try { bytes = await context.resourceManager.getRawFileContent('environment.local.json'); }
      catch (_) { return result; }
      const text = new util.TextDecoder().decodeToString(bytes);
      if (!text.trim()) { return result; }
      let config: EnvironmentConfig;
      try { config = JSON.parse(text) as EnvironmentConfig; }
      catch (_) { throw new UserFacingError('本地服务配置无法读取，请检查开发配置'); }
      result.apiBaseURL = (config.apiBaseURL ?? result.apiBaseURL).replace(/\/+$/, '');
      result.webOrigin = (config.webOrigin ?? result.webOrigin).replace(/\/+$/, '');
      result.passkeyRPID = config.passkeyRPID ?? result.passkeyRPID;
      result.filingNumber = config.filingNumber?.trim() ?? result.filingNumber;
    }
    if (!/^https?:\/\/[^/]+$/.test(result.apiBaseURL) || !/^https:\/\/[^/]+$/.test(result.webOrigin) ||
      !/^[A-Za-z0-9.-]+$/.test(result.passkeyRPID)) { throw new UserFacingError('服务地址配置无效'); }
    const api = parseUrl(result.apiBaseURL);
    const web = parseUrl(result.webOrigin);
    if (api.path !== '/' || web.path !== '/' || result.apiBaseURL.includes('?') || result.webOrigin.includes('?')) { throw new UserFacingError('服务地址必须为完整站点地址'); }
    return result;
  }
}
