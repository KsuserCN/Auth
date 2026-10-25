import { common } from '@kit.AbilityKit';
import BuildProfile from 'BuildProfile';
import { AppModel } from '../core/AppModel';
import { setAppearance } from '../core/DevicePreferences';
import { OfficialApp, ThirdPartyApp, dateText } from '../core/Models';
import { scopeTitle, scopeDescription, grantModeTitle, verificationTitle, websiteOrigin } from '../core/Presentation';
import { ActionButton, ApplicationIcon, AppIcon, EmptyState, InfoLine, SectionHeader, SettingsRow, StatusBadge } from '../components/AppComponents';
import { ResourceFeedback } from '../components/AccountComponents';
import { CredentialPage } from './CredentialPage';

export function accountTitle(destination: string): string {
  switch (destination) {
    case 'settings': return '关于与设置'; case 'apps': return '授权应用'; case 'passkeys': return '通行密钥';
    case 'totp': return '身份验证器'; case 'recovery-codes': return '恢复码'; case 'email': return '绑定邮箱';
    case 'password': return '登录密码'; case 'recovery': return '账号恢复授权'; case 'delete': return '删除账号';
    default: return 'Ksuser 安全';
  }
}
interface ApplicationView {
  id: string; name: string; logo: string; scopes: string[]; official: boolean;
  website: string; contact: string; first: string; last: string; expires: string;
  mode: string; creator: string; verification: string;
}

@Component
export struct AccountPage {
  @ObjectLink @Watch('accountChanged') model: AppModel;
  @Prop destination: string = '';
  onComplete: () => void = () => {};
  @StorageLink('appearance') private appearance: number = 0;
  @State private visible: boolean = false;
  @State private selected: ApplicationView | null = null;
  @State private refreshing: boolean = false;
  @State private changingAppearance: boolean = false;
  @State private revoking: boolean = false;
  private account: string = '';
  private pendingRevoke: ApplicationView | null = null;

  aboutToAppear(): void { this.account = this.model.profile?.uuid || ''; if (this.destination === 'apps') { this.model.loadApps(); } }
  private accountChanged(): void {
    const account = this.model.profile?.uuid || '';
    if (account !== this.account) { this.account = account; this.visible = false; this.selected = null; this.pendingRevoke = null; }
  }
  aboutToDisappear(): void { this.pendingRevoke = null; }
  private async changeAppearance(index: number): Promise<void> {
    if (this.changingAppearance) { return; }
    this.changingAppearance = true;
    try { await setAppearance(this.getUIContext().getHostContext() as common.UIAbilityContext, index); }
    catch (_) { this.model.error = '暂时无法保存外观选择，请重试'; }
    finally { this.changingAppearance = false; }
  }
  private async refresh(): Promise<void> { try { await this.model.loadApps(); } finally { this.refreshing = false; } }
  private logout(): void {
    this.getUIContext().showAlertDialog({ title: '退出当前账号？', message: '退出后需要重新登录才能继续使用。',
      primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '退出登录', fontColor: $r('app.color.danger'), action: () => this.model.logout() } });
  }
  private revoke(): void {
    const selected = this.selected;
    if (!selected || this.revoking) { return; }
    this.getUIContext().showAlertDialog({ title: '取消对此应用的授权？', message: '「' + selected.name + '」将无法继续使用当前授权。',
      primaryButton: { value: '保留授权', action: () => {} }, secondaryButton: { value: '取消授权', fontColor: $r('app.color.danger'), action: () => {
        this.pendingRevoke = selected; this.visible = false;
      } } });
  }
  private async detailsDismissed(): Promise<void> {
    const selected = this.pendingRevoke; this.pendingRevoke = null;
    if (!selected || !this.model.authenticated || this.revoking) { return; }
    const account = this.account; this.revoking = true;
    try {
      const result = await this.model.revokeApplication(selected.id, selected.official);
      if (result.status === 'success' && account === this.model.profile?.uuid) { this.selected = null; }
    } finally { this.revoking = false; }
  }
  private showOfficial(app: OfficialApp): void {
    this.selected = { id: app.clientId, name: app.clientName, logo: app.logoUrl || '', scopes: app.scopes,
      official: true, website: '', contact: '', first: app.authorizedAt || '', last: app.lastAuthorizedAt || '',
      expires: '', mode: 'OFFICIAL', creator: '', verification: '' }; this.visible = true;
  }
  private showThirdParty(app: ThirdPartyApp): void {
    this.selected = { id: app.appId, name: app.appName, logo: app.logoUrl || '', scopes: app.scopes,
      official: false, website: app.redirectUri || '', contact: app.contactInfo || '', first: app.authorizedAt || '',
      last: app.lastAuthorizedAt || '', expires: app.expiresAt || '', mode: app.grantMode,
      creator: app.creatorName || '', verification: app.creatorVerificationType || '' }; this.visible = true;
  }

  @Builder
  private applicationDetails() {
    Column() {
      Scroll() {
        Column({ space: 16 }) {
          if (this.selected) {
            ApplicationIcon({ url: this.selected.logo, name: this.selected.name, iconSize: 68 })
            SectionHeader({ title: this.selected.name, subtitle: this.selected.official ? '官方应用' : '第三方应用' })
            if (this.selected.official) { StatusBadge({ text: 'Ksuser 官方应用' }) }
            else {
              InfoLine({ title: '开发者', value: this.selected.creator || '未提供' })
              StatusBadge({ text: verificationTitle(this.selected.verification), positive: ['personal', 'enterprise', 'admin'].includes(this.selected.verification) })
              InfoLine({ title: '返回网站', value: websiteOrigin(this.selected.website) })
              if (this.selected.contact) { InfoLine({ title: '联系方式', value: this.selected.contact }) }
            }
            SectionHeader({ title: '已授权的资料范围' })
            ForEach(this.selected.scopes, (scope: string) => {
              Column({ space: 5 }) {
                Text(scopeTitle(scope)).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
                Text(scopeDescription(scope)).fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
              }.width('100%').alignItems(HorizontalAlign.Start).padding({ top: 5, bottom: 5 })
            }, (scope: string) => scope)
            InfoLine({ title: '首次授权', value: dateText(this.selected.first) })
            InfoLine({ title: '最近授权', value: dateText(this.selected.last || this.selected.first) })
            InfoLine({ title: '授权方式', value: this.selected.official ? '官方应用授权' : grantModeTitle(this.selected.mode) })
            if (this.selected.expires) { InfoLine({ title: '到期时间', value: dateText(this.selected.expires) }) }
          }
        }.width('100%').padding(24)
      }.layoutWeight(1).width('100%').align(Alignment.TopStart)
      Column() {
        ActionButton({ title: '取消授权', destructive: true, busy: this.revoking, disabled: this.model.busy, onTap: () => this.revoke() })
      }.width('100%').padding({ left: 24, right: 24, top: 12, bottom: 24 }).backgroundColor($r('app.color.card_background'))
    }.width('100%').height('100%')
  }

  @Builder
  private theme(index: number, title: string, icon: string) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 8 }) {
        AppIcon({ name: icon, iconSize: 24, color: this.appearance === index ? $r('app.color.brand') : $r('app.color.text_secondary') })
        Text(title).fontSize(13).fontColor(this.appearance === index ? $r('app.color.brand') : $r('app.color.text_primary'))
        if (this.appearance === index) { Text('已选择').fontSize(10).fontColor($r('app.color.brand')) }
      }.width('100%').constraintSize({ minHeight: 88 }).justifyContent(FlexAlign.Center)
    }.layoutWeight(1).borderRadius(16).enabled(!this.changingAppearance)
      .backgroundColor(this.appearance === index ? $r('app.color.brand_soft') : $r('app.color.input_background'))
      .onClick(() => this.changeAppearance(index)).accessibilityText(title + (this.appearance === index ? '，已选择' : ''))
  }
  @Builder
  private settings() {
    Column({ space: 12 }) {
      Image($r('app.media.brand_logo')).width(78).height(78).borderRadius(22)
      Text('Ksuser 安全').fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      Text('安全连接，安心使用。').fontSize(14).fontColor($r('app.color.text_secondary'))
      Text('版本 ' + BuildProfile.VERSION_NAME + ' (' + BuildProfile.BUILD_VERSION + ') · HarmonyOS')
        .fontSize(12).fontColor($r('app.color.text_tertiary'))
    }.width('100%').padding({ top: 20, bottom: 18 })
    Column({ space: 14 }) {
      SectionHeader({ title: '外观' })
      Row({ space: 8 }) { this.theme(0, '跟随系统', 'globe'); this.theme(1, '浅色', 'sun'); this.theme(2, '深色', 'moon'); }.width('100%')
      InfoLine({ title: '显示语言', value: '简体中文' })
    }.width('100%').padding(18).borderRadius(22).backgroundColor($r('app.color.card_background'))
    Column() {
      SectionHeader({ title: '协议与隐私' }).padding({ top: 16, bottom: 6 })
      SettingsRow({ title: '服务协议', subtitle: '使用条款', icon: 'document', onTap: () => this.model.openLegal('terms') })
      SettingsRow({ title: '隐私政策', subtitle: '个人信息保护', icon: 'security', onTap: () => this.model.openLegal('privacy') })
      SettingsRow({ title: '第三方信息共享清单', subtitle: '第三方服务说明', icon: 'document', last: true, onTap: () => this.model.openLegal('third-party') })
    }.width('100%').padding({ left: 18, right: 18 }).borderRadius(22).backgroundColor($r('app.color.card_background'))
    Column() {
      SettingsRow({ title: '问题反馈', subtitle: '提交问题或建议', icon: 'mail', last: true, onTap: () => this.model.openFeedback() })
    }.width('100%').padding({ left: 18, right: 18 }).borderRadius(22).backgroundColor($r('app.color.card_background'))
    if (this.model.debug) {
      Column() {
        SectionHeader({ title: '开发配置' })
        InfoLine({ title: 'API 地址', value: this.model.apiBaseURL })
        if (this.model.passkeyOrigin) { InfoLine({ title: '原生 Passkey Origin', value: this.model.passkeyOrigin }) }
      }.width('100%').padding(18).borderRadius(22).backgroundColor($r('app.color.card_background'))
    }
    if (this.model.authenticated) { ActionButton({ title: '退出当前账号', destructive: true, disabled: this.model.busy, onTap: () => this.logout() }) }
    Text(new Date().getFullYear() + ' Ksuser | KsuserKqy').fontSize(12).fontColor($r('app.color.text_tertiary')).width('100%').textAlign(TextAlign.Center)
    if (this.model.filingNumber) {
      Button(this.model.filingNumber).height(44).fontSize(12).fontColor($r('app.color.text_secondary'))
        .backgroundColor(Color.Transparent).onClick(() => this.model.openFiling())
    }
  }

  @Builder
  private applicationRow(name: string, logo: string, mode: string, last: string) {
    Row({ space: 12 }) {
      ApplicationIcon({ name: name, url: logo })
      Column({ space: 5 }) {
        Text(name).fontSize(16).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(mode).fontSize(12).fontColor($r('app.color.text_secondary'))
        if (last) { Text('最近授权 ' + dateText(last)).fontSize(12).fontColor($r('app.color.text_tertiary')) }
      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      AppIcon({ name: 'next', iconSize: 13, color: $r('app.color.text_tertiary') })
    }.width('100%').padding({ top: 14, bottom: 14 })
  }
  @Builder
  private apps() {
    SectionHeader({ title: '连接你的数字生活', subtitle: '查看资料访问权限，随时取消授权' })
    ResourceFeedback({ state: this.model.appsState, label: '授权应用', onRetry: () => this.model.loadApps() })
    if (this.model.appsState.hasData && this.model.officialApps.length + this.model.thirdPartyApps.length === 0) {
      EmptyState({ title: '还没有授权的应用', onRetry: () => this.model.loadApps() })
    }
    if (this.model.thirdPartyApps.length) {
      Column() {
        SectionHeader({ title: '第三方应用 · ' + this.model.thirdPartyApps.length }).padding({ top: 16, bottom: 8 })
        ForEach(this.model.thirdPartyApps, (app: ThirdPartyApp) => {
          Button({ type: ButtonType.Normal }) { this.applicationRow(app.appName, app.logoUrl || '', grantModeTitle(app.grantMode), app.lastAuthorizedAt || app.authorizedAt || '') }
            .width('100%').backgroundColor(Color.Transparent).onClick(() => this.showThirdParty(app))
          Divider().color($r('app.color.divider'))
        }, (app: ThirdPartyApp) => app.appId + ':' + (app.lastAuthorizedAt || '') + app.scopes.join(','))
      }.width('100%').padding({ left: 18, right: 18 }).borderRadius(22).backgroundColor($r('app.color.card_background'))
    }
    if (this.model.officialApps.length) {
      Column() {
        SectionHeader({ title: '官方应用 · ' + this.model.officialApps.length }).padding({ top: 16, bottom: 8 })
        ForEach(this.model.officialApps, (app: OfficialApp) => {
          Button({ type: ButtonType.Normal }) { this.applicationRow(app.clientName, app.logoUrl || '', 'Ksuser 官方应用', app.lastAuthorizedAt || app.authorizedAt || '') }
            .width('100%').backgroundColor(Color.Transparent).onClick(() => this.showOfficial(app))
          Divider().color($r('app.color.divider'))
        }, (app: OfficialApp) => app.clientId + ':' + (app.lastAuthorizedAt || '') + app.scopes.join(','))
      }.width('100%').padding({ left: 18, right: 18 }).borderRadius(22).backgroundColor($r('app.color.card_background'))
    }
  }
  @Builder
  private page() {
    Scroll() {
      Column({ space: 18 }) {
        if (this.destination === 'settings') { this.settings() }
        else if (this.destination === 'apps') { this.apps() }
        else { CredentialPage({ model: this.model, kind: this.destination, onComplete: () => this.onComplete() }) }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 12, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
  }
  build() {
    Column() {
      if (this.destination === 'apps') {
        Refresh({ refreshing: $$this.refreshing }) { this.page() }.onRefreshing(() => this.refresh()).width('100%').height('100%')
      } else { this.page() }
    }.width('100%').height('100%').backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.applicationDetails(), { height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background'), onDisappear: () => this.detailsDismissed() })
  }
}
