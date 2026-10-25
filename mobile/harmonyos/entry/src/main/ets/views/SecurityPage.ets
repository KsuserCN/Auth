import { AppModel } from '../core/AppModel';
import { ActionButton, AppIcon, IconTile, InfoLine, SectionHeader, SettingsRow, StatusBadge, methodTitle } from '../components/AppComponents';

@Component
export struct SecurityPage {
  @ObjectLink model: AppModel;
  onOpen: (destination: string) => void = () => {};

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
    if (this.model.totp.enabled) { methods.push('totp'); }
    if (this.model.passkeys.length > 0) { methods.push('passkey'); }
    return methods;
  }
  private sensitiveMethods(): string[] {
    const methods: string[] = [];
    if (this.model.profile?.hasPassword) { methods.push('password'); }
    if (this.model.profile?.email) { methods.push('email-code'); }
    if (this.model.totp.enabled) { methods.push('totp'); }
    if (this.model.passkeys.length > 0) { methods.push('passkey'); }
    return methods;
  }
  private confirmLogout(all: boolean): void {
    this.getUIContext().showAlertDialog({
      title: all ? '退出所有设备？' : '退出当前账号？',
      message: all ? '所有设备的登录会话都将撤销，包括当前设备。' : '退出后需要重新验证身份才能使用账号。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '退出登录', fontColor: $r('app.color.danger'), action: () => this.model.logout(all) }
    });
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
        .enabled(!this.model.busy).onChange((value: boolean) => this.model.updateSetting(field, value))
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

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 18 }) {
          Row({ space: 14 }) {
            IconTile({ name: 'security', tileSize: 58, color: $r('app.color.success'), tileBackground: $r('app.color.success_soft') })
            Column({ space: 6 }) {
              Text(this.model.risk?.sessionFrozen ? '当前会话需要验证' : this.protectionEnabled('mfaEnabled') ? '安全，始终在你身边' : '再多一层安心')
                .fontSize(21).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
              Text(this.model.risk ? '风险评分 ' + this.model.risk.riskScore : '查看验证方式和账号保护设置')
                .fontSize(13).fontColor($r('app.color.text_secondary')).lineHeight(20)
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          }.width('100%')
          StatusBadge({ text: this.protectionEnabled('mfaEnabled') ? '双重验证已开启' : '建议开启双重验证', positive: this.protectionEnabled('mfaEnabled') })
          if (this.model.risk !== null) {
            InfoLine({ title: '当前登录位置', value: this.model.risk.currentLocation || '' })
            if (this.model.risk.requiresStepUp || this.model.risk.sessionFrozen) {
              Text('当前操作需要再次验证身份。').fontSize(13).lineHeight(21).fontColor($r('app.color.brand'))
              ActionButton({ title: '验证当前身份', busy: this.model.busy, onTap: () => this.model.verifyIdentity() })
            }
          }
        }.width('100%').alignItems(HorizontalAlign.Start).padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
        Column({ space: 8 }) {
          SectionHeader({ title: '验证方式', subtitle: '选择适合你的方式，安全又轻松' })
          SettingsRow({ title: '通行密钥', subtitle: '通过面容、指纹或锁屏验证身份', value: this.model.passkeys.length + ' 个',
            icon: 'key', onTap: () => this.onOpen('passkeys') })
          SettingsRow({ title: '验证器', value: this.model.totp.enabled ? '已启用' : '未启用', icon: 'lock',
            onTap: () => this.onOpen('totp') })
          if (this.model.totp.enabled) {
            SettingsRow({ title: 'TOTP 恢复码', value: '剩余 ' + this.model.totp.recoveryCodesCount + ' 个', icon: 'key',
              last: true, onTap: () => this.onOpen('recovery-codes') })
          }
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
        Column({ space: 8 }) {
          SectionHeader({ title: '登录保护' })
          this.protection('双重验证', '登录时增加一次身份确认', 'security', 'mfaEnabled')
          Divider().color($r('app.color.divider'))
          this.protection('异地登录检测', '留意不常用的登录位置', 'location', 'detectUnusualLogin')
          Divider().color($r('app.color.divider'))
          this.protection('敏感操作提醒', '重要变更后通过邮件提醒', 'mail', 'notifySensitiveActionEmail')
          Divider().color($r('app.color.divider'))
          this.protection('订阅动态邮件', '接收产品和服务的最新动态', 'mail', 'subscribeNewsEmail')
          if (this.protectionEnabled('mfaEnabled') && this.mfaMethods().length > 0) {
            this.preference('首选双重验证', 'preferredMfaMethod', false)
          }
          if (this.sensitiveMethods().length > 0) {
            this.preference('首选敏感验证', 'preferredSensitiveMethod', true)
          }
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
        Column({ space: 8 }) {
          SectionHeader({ title: '账号安全' })
          SettingsRow({ title: '修改邮箱', value: this.model.profile?.email || '', icon: 'mail', onTap: () => this.onOpen('email') })
          SettingsRow({ title: this.model.profile?.hasPassword ? '修改密码' : '设置密码', icon: 'lock', onTap: () => this.onOpen('password') })
          SettingsRow({ title: '授权应用', subtitle: '查看与撤销应用的访问权限', icon: 'home', onTap: () => this.onOpen('apps') })
          SettingsRow({ title: '生成账号恢复授权', subtitle: '协助其他设备恢复当前账号', icon: 'scan', last: true, onTap: () => this.onOpen('recovery') })
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
        Column() {
          SettingsRow({ title: '退出当前账号', icon: 'profile', onTap: () => this.confirmLogout(false) })
          SettingsRow({ title: '退出所有设备', icon: 'devices', destructive: true, onTap: () => this.confirmLogout(true) })
          SettingsRow({ title: '删除账号', subtitle: '永久删除账号及相关数据', icon: 'close', destructive: true, last: true,
            onTap: () => this.onOpen('delete') })
        }.width('100%').padding({ left: 20, right: 20 }).borderRadius(24).backgroundColor($r('app.color.card_background'))
        ActionButton({ title: '刷新安全状态', secondary: true, busy: this.model.busy, onTap: () => this.model.loadSecurity() })
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
  }
}
