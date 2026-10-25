import { AppModel } from '../core/AppModel';
import { ApiResult } from '../core/Models';
import { formatDuration, resultMessage, riskTitle } from '../core/Presentation';
import { ActionButton, AppIcon, IconTile, InfoLine, SectionHeader, SettingsRow, StatusBadge, methodTitle } from '../components/AppComponents';

@Component
export struct SecurityPage {
  @ObjectLink model: AppModel;
  onOpen: (destination: string) => void = () => {};
  @State private refreshing: boolean = false;
  @State private details: boolean = false;

  aboutToAppear(): void { this.model.loadSecurity(); }

  private protectionEnabled(field: string): boolean {
    const settings = this.model.profile?.settings;
    switch (field) {
      case 'mfaEnabled': return settings?.mfaEnabled === true;
      case 'detectUnusualLogin': return settings?.detectUnusualLogin === true;
      case 'notifySensitiveActionEmail': return settings?.notifySensitiveActionEmail === true;
      default: return settings?.subscribeNewsEmail === true;
    }
  }
  private mfaMethods(): string[] {
    const methods: string[] = [];
    if (this.model.totpState.hasData && this.model.totp.enabled) { methods.push('totp'); }
    if (this.model.passkeysState.hasData && this.model.passkeys.length > 0) { methods.push('passkey'); }
    return methods;
  }
  private sensitiveMethods(): string[] {
    const methods: string[] = [];
    if (this.model.profile?.hasPassword !== false) { methods.push('password'); }
    if (this.model.profile?.email) { methods.push('email-code'); }
    if (this.model.totpState.hasData && this.model.totp.enabled) { methods.push('totp'); }
    if (this.model.passkeysState.hasData && this.model.passkeys.length > 0) { methods.push('passkey'); }
    return methods;
  }
  private passkeyStatus(): string {
    if (!this.model.passkeysState.hasData) { return this.model.passkeysState.status === 'loading' ? '正在加载…' : '点按重试'; }
    return this.model.passkeys.length + ' 个';
  }
  private totpStatus(): string {
    if (!this.model.totpState.hasData) { return this.model.totpState.status === 'loading' ? '正在加载…' : '点按重试'; }
    return this.model.totp.enabled ? '已启用' : '未启用';
  }
  private confirmLogout(all: boolean): void {
    this.getUIContext().showAlertDialog({
      title: all ? '退出所有设备？' : '退出当前账号？',
      message: all ? '所有设备的登录会话都将撤销，包括当前设备。' : '退出后需要重新验证身份才能使用账号。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '退出登录', fontColor: $r('app.color.danger'), action: () => this.model.logout(all) }
    });
  }
  private async refresh(): Promise<void> {
    try { await this.model.loadSecurity(); }
    finally { this.refreshing = false; }
  }

  @Builder
  private protection(title: string, subtitle: string, icon: string, field: string) {
    Row({ space: 12 }) {
      AppIcon({ name: icon, iconSize: 21 })
      Column({ space: 5 }) {
        Text(title).fontSize(15).fontColor($r('app.color.text_primary'))
        Text(subtitle).fontSize(12).fontColor($r('app.color.text_secondary')).lineHeight(18)
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: this.protectionEnabled(field) }).selectedColor($r('app.color.brand'))
        .enabled(!this.model.busy && this.model.profile !== null).onChange((value: boolean) => this.model.updateSetting(field, value))
        .accessibilityText(title)
    }.width('100%').constraintSize({ minHeight: 74 }).padding({ top: 10, bottom: 10 })
  }

  @Builder
  private preference(title: string, field: string, sensitive: boolean) {
    Row({ space: 12 }) {
      Text(title).fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1)
      Select((sensitive ? this.sensitiveMethods() : this.mfaMethods()).map((method: string): SelectOption => ({ value: methodTitle(method) })))
        .value(methodTitle((sensitive ? this.model.profile?.settings?.preferredSensitiveMethod : this.model.profile?.settings?.preferredMfaMethod) || ''))
        .font({ size: 13 }).fontColor($r('app.color.text_secondary')).enabled(!this.model.busy)
        .backgroundColor($r('app.color.input_background'))
        .onSelect((index: number) => this.model.updateSetting(field, (sensitive ? this.sensitiveMethods() : this.mfaMethods())[index]))
    }.width('100%').constraintSize({ minHeight: 60 })
  }

  @Builder
  private riskCard() {
    Column({ space: 16 }) {
      if (this.model.risk !== null) {
        Row({ space: 14 }) {
          IconTile({ name: 'security', tileSize: 48,
            color: this.model.risk.sessionFrozen ? $r('app.color.danger') : $r('app.color.success'),
            tileBackground: this.model.risk.sessionFrozen ? $r('app.color.danger_soft') : $r('app.color.success_soft') })
          Column({ space: 6 }) {
            Text(this.model.risk.sessionFrozen ? '当前会话已冻结' : this.model.risk.requiresStepUp ? '需要再次验证身份' : '当前会话安全状态')
              .fontSize(20).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
            Text(this.model.risk.recommendedActionCode ? resultMessage(this.model.risk.recommendedActionCode, undefined, 400) : '持续检查登录环境与近期活动')
              .fontSize(13).fontColor($r('app.color.text_secondary')).lineHeight(21)
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        }.width('100%')
        Row({ space: 8 }) {
          StatusBadge({ text: '风险：' + riskTitle(this.model.risk.riskLevel).replace('风险', ''), positive: this.model.risk.riskLevel === 'low' })
          if (this.model.risk.sensitiveVerified) { StatusBadge({ text: '已验证' }) }
          Blank()
          Text(this.model.risk.riskScore + ' / 100').fontSize(12).fontColor($r('app.color.text_secondary'))
        }.width('100%')
        if (this.model.risk.alertMessageCode) {
          Column({ space: 6 }) {
            if (this.model.risk.alertTitleCode) {
              Text(resultMessage(this.model.risk.alertTitleCode, undefined, 400)).fontSize(14).fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.brand')).width('100%')
            }
            Text(resultMessage(this.model.risk.alertMessageCode, undefined, 400)).fontSize(13).lineHeight(21)
              .fontColor($r('app.color.text_secondary')).width('100%')
          }.width('100%').padding(14).borderRadius(14).backgroundColor($r('app.color.brand_soft'))
        }
        Button(this.details ? '收起会话详情与风险原因' : '会话详情与风险原因').height(44).width('100%')
          .backgroundColor($r('app.color.input_background')).fontColor($r('app.color.brand'))
          .onClick(() => { this.details = !this.details; }).accessibilityText(this.details ? '收起会话详情' : '展开会话详情')
        if (this.details) {
          Column() {
            InfoLine({ title: '当前位置', value: this.model.risk.currentLocation || '未知' })
            InfoLine({ title: '当前 IP', value: this.model.risk.currentIp || '未知' })
            InfoLine({ title: '设备', value: [this.model.risk.deviceType || '', this.model.risk.browser || ''].filter((value: string) => !!value).join(' · ') || '未知' })
            InfoLine({ title: '最近认证', value: typeof this.model.risk.authAgeSeconds !== 'number' ? '未知' : formatDuration(this.model.risk.authAgeSeconds) + '前' })
            InfoLine({ title: '空闲时间', value: this.model.risk.idleSeconds === undefined ? '未知' : formatDuration(this.model.risk.idleSeconds) })
            if (this.model.risk.sensitiveVerified) {
              InfoLine({ title: '敏感验证有效期', value: formatDuration(this.model.risk.sensitiveVerificationRemainingSeconds) })
            }
          }.width('100%')
          ForEach(this.model.risk.reasonCodes || [], (reason: ApiResult, index: number) => {
            Text('• ' + resultMessage(reason.errorCode, reason.params, 400)).fontSize(13).lineHeight(22)
              .fontColor($r('app.color.text_secondary')).width('100%')
          }, (reason: ApiResult, index: number) => (reason.errorCode || 'reason') + index)
        }
        if (this.model.risk.sessionFrozen) {
          ActionButton({ title: '重新登录', onTap: () => this.confirmLogout(false) })
        } else if (this.model.risk.requiresStepUp) {
          ActionButton({ title: '立即验证', busy: this.model.busy, onTap: () => this.model.verifyIdentity() })
        }
      } else {
        SectionHeader({ title: '当前会话安全状态', subtitle: this.model.riskState.status === 'loading' ? '正在检查登录环境…' : '安全状态暂时无法读取' })
        if (this.model.riskState.status === 'loading') { LoadingProgress().width(28).height(28).color($r('app.color.brand')) }
      }
      if (this.model.riskState.status === 'error') {
        Text(this.model.riskState.error || '安全状态读取失败，请重试').fontSize(13).lineHeight(21).fontColor($r('app.color.danger'))
        ActionButton({ title: '重试安全检查', secondary: true, onTap: () => this.model.loadSecurity() })
      }
    }.width('100%').alignItems(HorizontalAlign.Start).padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          this.riskCard()
          Column({ space: 8 }) {
            SectionHeader({ title: '验证方式' })
            SettingsRow({ title: '通行密钥', subtitle: '通过面容、指纹或锁屏验证身份', value: this.passkeyStatus(), icon: 'key',
              onTap: () => { if (this.model.passkeysState.hasData) { this.onOpen('passkeys'); } else { this.model.loadSecurity(); } } })
            SettingsRow({ title: '验证器', value: this.totpStatus(), icon: 'lock', last: !this.model.totpState.hasData || !this.model.totp.enabled,
              onTap: () => { if (this.model.totpState.hasData) { this.onOpen('totp'); } else { this.model.loadSecurity(); } } })
            if (this.model.totpState.hasData && this.model.totp.enabled) {
              SettingsRow({ title: 'TOTP 恢复码', value: '剩余 ' + this.model.totp.recoveryCodesCount + ' 个', icon: 'key', last: true,
                onTap: () => this.onOpen('recovery-codes') })
            }
            if (this.model.passkeysState.status === 'error' || this.model.totpState.status === 'error') {
              Text('部分验证方式读取失败，下拉刷新或点按对应项目重试。').fontSize(12).lineHeight(20).fontColor($r('app.color.danger'))
            }
          }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
          Column({ space: 8 }) {
            SectionHeader({ title: '登录保护' })
            this.protection('双重验证', '登录时增加一次身份确认', 'security', 'mfaEnabled')
            Divider().color($r('app.color.divider'))
            this.protection('异地登录检测', '留意不常用的登录位置', 'location', 'detectUnusualLogin')
            Divider().color($r('app.color.divider'))
            this.protection('敏感操作邮件提醒', '重要变更后通过邮件提醒', 'mail', 'notifySensitiveActionEmail')
            Divider().color($r('app.color.divider'))
            this.protection('订阅动态邮件', '接收产品和服务的最新动态', 'mail', 'subscribeNewsEmail')
            if (this.protectionEnabled('mfaEnabled') && this.mfaMethods().length > 0) {
              this.preference('首选双重验证', 'preferredMfaMethod', false)
            }
            if (this.sensitiveMethods().length > 0) { this.preference('首选敏感验证', 'preferredSensitiveMethod', true) }
          }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
          Column({ space: 8 }) {
            SectionHeader({ title: '账号与授权' })
            SettingsRow({ title: this.model.profile?.email ? '修改邮箱' : '绑定邮箱', value: this.model.profile?.email || '未绑定邮箱', icon: 'mail', onTap: () => this.onOpen('email') })
            SettingsRow({ title: this.model.profile?.hasPassword === false ? '设置密码' : '修改密码', icon: 'lock', onTap: () => this.onOpen('password') })
            SettingsRow({ title: '授权应用', subtitle: '查看与撤销应用的访问权限', icon: 'home', onTap: () => this.onOpen('apps') })
            SettingsRow({ title: '生成账号恢复授权', subtitle: '供其他设备找回账号', icon: 'scan', last: true, onTap: () => this.onOpen('recovery') })
          }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
          Column() {
            SettingsRow({ title: '退出当前账号', icon: 'profile', onTap: () => this.confirmLogout(false) })
            SettingsRow({ title: '退出所有设备', icon: 'devices', destructive: true, onTap: () => this.confirmLogout(true) })
            SettingsRow({ title: '删除账号', subtitle: '永久删除账号及相关数据', icon: 'close', destructive: true, last: true,
              onTap: () => this.onOpen('delete') })
          }.width('100%').padding({ left: 20, right: 20 }).borderRadius(22).backgroundColor($r('app.color.card_background'))
        }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
      }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
    }.onRefreshing(() => this.refresh()).width('100%').height('100%').backgroundColor($r('app.color.page_background'))
  }
}
