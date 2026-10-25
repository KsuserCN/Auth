import { ConfigurationConstant } from '@kit.AbilityKit';
import BuildProfile from 'BuildProfile';
import { AppModel } from '../core/AppModel';
import { OfficialApp, ThirdPartyApp } from '../core/Models';
import { ActionButton, EmptyState, IconTile, InfoLine, SectionHeader, SettingsRow, readableDate, scopeTitle } from '../components/AppComponents';
import { CredentialPage } from './CredentialPage';

export function accountTitle(destination: string): string {
  switch (destination) {
    case 'settings': return '关于与设置';
    case 'apps': return '授权应用';
    case 'passkeys': return '通行密钥';
    case 'totp': return '身份验证器';
    case 'recovery-codes': return '恢复码';
    case 'email': return '修改邮箱';
    case 'password': return '登录密码';
    case 'recovery': return '账号恢复授权';
    case 'delete': return '删除账号';
    default: return 'Ksuser 安全';
  }
}

interface ApplicationView {
  id: string; name: string; scopes: string[]; official: boolean;
  website: string; contact: string; date: string; expires: string;
}

@Component
export struct AccountPage {
  @ObjectLink model: AppModel;
  @Prop destination: string = '';
  @StorageLink('appearance') private appearance: number = 0;
  @State private visible: boolean = false;
  @State private selected: ApplicationView | null = null;

  aboutToAppear(): void { if (this.destination === 'apps') { this.model.loadApps(); } }

  private changeAppearance(index: number): void {
    const modes: ConfigurationConstant.ColorMode[] = [ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET,
      ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT, ConfigurationConstant.ColorMode.COLOR_MODE_DARK];
    try {
      this.getUIContext().getHostContext()?.getApplicationContext().setColorMode(modes[index]); this.appearance = index;
    } catch (_) { this.model.error = '暂时无法切换外观'; }
  }

  private revoke(): void {
    const selected = this.selected;
    if (!selected) { return; }
    this.getUIContext().showAlertDialog({ title: '取消对此应用的授权？', message: '「' + selected.name + '」将无法继续使用当前授权。',
      primaryButton: { value: '保留授权', action: () => {} },
      secondaryButton: { value: '取消授权', fontColor: $r('app.color.danger'), action: async () => {
        this.visible = false; await this.model.revokeApplication(selected.id, selected.official);
      } }
    });
  }

  @Builder
  private applicationDetails() {
    Scroll() {
      Column({ space: 18 }) {
        IconTile({ name: 'key', tileSize: 64 })
        SectionHeader({ title: this.selected?.name || '', subtitle: this.selected?.official ? '官方应用' : '第三方应用' })
        SectionHeader({ title: '已授权的资料范围' })
        ForEach(this.selected?.scopes || [], (scope: string) => {
          InfoLine({ title: scopeTitle(scope), value: scope })
        }, (scope: string) => scope)
        InfoLine({ title: '授权时间', value: readableDate(this.selected?.date) })
        if (this.selected?.website) { InfoLine({ title: '返回网站', value: this.selected.website }) }
        if (this.selected?.contact) { InfoLine({ title: '联系方式', value: this.selected.contact }) }
        if (this.selected?.expires) { InfoLine({ title: '到期时间', value: readableDate(this.selected.expires) }) }
        ActionButton({ title: '取消授权', destructive: true, busy: this.model.busy, onTap: () => this.revoke() })
      }.width('100%').padding(24)
    }.width('100%').height('100%').scrollBar(BarState.Off)
  }

  @Builder
  private settings() {
    Column({ space: 14 }) {
      Image($r('app.media.brand_logo')).width(76).height(76).borderRadius(22)
      Text('Ksuser 安全').fontSize(25).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      Text('让数字生活，多一份安心').fontSize(13).fontColor($r('app.color.text_secondary'))
      Text('HarmonyOS · 版本 ' + BuildProfile.VERSION_NAME).fontSize(12).fontColor($r('app.color.text_tertiary'))
    }.width('100%').padding({ top: 20, bottom: 26 })
    Column({ space: 10 }) {
      SectionHeader({ title: '偏好设置' })
      Row({ space: 12 }) {
        Text('外观').fontSize(15).fontColor($r('app.color.text_primary')).layoutWeight(1)
        Select([{ value: '跟随系统' }, { value: '浅色' }, { value: '深色' }]).selected(this.appearance)
          .value(['跟随系统', '浅色', '深色'][this.appearance]).font({ size: 14 })
          .fontColor($r('app.color.text_secondary')).backgroundColor($r('app.color.input_background'))
          .onSelect((index: number) => this.changeAppearance(index))
      }.width('100%').constraintSize({ minHeight: 60 })
      InfoLine({ title: '语言', value: '简体中文' })
    }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
    Column() {
      SettingsRow({ title: '隐私政策', icon: 'security', onTap: () => this.model.openLegal('privacy') })
      SettingsRow({ title: '用户协议', icon: 'document', last: true, onTap: () => this.model.openLegal('terms') })
    }.width('100%').padding({ left: 20, right: 20 }).borderRadius(24).backgroundColor($r('app.color.card_background'))
    if (this.model.debug) {
      Column({ space: 8 }) {
        SectionHeader({ title: '开发配置' })
        InfoLine({ title: 'API 地址', value: this.model.apiBaseURL })
        if (this.model.passkeyOrigin) { InfoLine({ title: '原生 Passkey Origin', value: this.model.passkeyOrigin }) }
      }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
    }
    Text('登录凭据通过系统安全能力加密保存在本机。').fontSize(12).lineHeight(20)
      .fontColor($r('app.color.text_tertiary')).textAlign(TextAlign.Center).width('100%').padding(12)
  }

  @Builder
  private apps() {
    SectionHeader({ title: '连接你的数字生活', subtitle: '随时查看资料访问权限，也可以取消授权' })
    if (this.model.officialApps.length + this.model.thirdPartyApps.length === 0) {
      EmptyState({ title: '还没有授权的应用', busy: this.model.busy, onRetry: () => this.model.loadApps() })
    }
    if (this.model.officialApps.length > 0) {
      Column({ space: 8 }) {
        SectionHeader({ title: '官方应用' })
        ForEach(this.model.officialApps, (app: OfficialApp, index: number) => {
          SettingsRow({ title: app.clientName, subtitle: app.scopes.map((scope: string) => scopeTitle(scope)).join('、'), icon: 'key',
            last: index === this.model.officialApps.length - 1, onTap: () => {
              this.selected = { id: app.clientId, name: app.clientName, scopes: app.scopes, official: true,
                website: '', contact: '', date: app.lastAuthorizedAt || app.authorizedAt || '', expires: '' }; this.visible = true;
            } })
        }, (app: OfficialApp) => app.clientId + ":" + app.scopes.join(",") + ":" + (app.lastAuthorizedAt || ""))
      }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
    }
    if (this.model.thirdPartyApps.length > 0) {
      Column({ space: 8 }) {
        SectionHeader({ title: '第三方应用' })
        ForEach(this.model.thirdPartyApps, (app: ThirdPartyApp, index: number) => {
          SettingsRow({ title: app.appName, subtitle: app.scopes.map((scope: string) => scopeTitle(scope)).join('、'), icon: 'home',
            last: index === this.model.thirdPartyApps.length - 1, onTap: () => {
              this.selected = { id: app.appId, name: app.appName, scopes: app.scopes, official: false,
                website: app.redirectUri || '', contact: app.contactInfo || '', date: app.lastAuthorizedAt || app.authorizedAt || '', expires: app.expiresAt || '' };
              this.visible = true;
            } })
        }, (app: ThirdPartyApp) => app.appId + ":" + app.scopes.join(",") + ":" + (app.lastAuthorizedAt || ""))
      }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
    }
    ActionButton({ title: '刷新授权应用', secondary: true, busy: this.model.busy, onTap: () => this.model.loadApps() })
  }

  build() {
    Scroll() {
      Column({ space: 18 }) {
        if (this.destination === 'settings') { this.settings() }
        else if (this.destination === 'apps') { this.apps() }
        else { CredentialPage({ model: this.model, kind: this.destination }) }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 12, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.applicationDetails(), { height: SheetSize.LARGE, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background') })
  }
}
