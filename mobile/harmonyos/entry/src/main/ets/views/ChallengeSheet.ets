import { AppModel } from '../core/AppModel';
import { displayName } from '../core/Models';
import { ActionButton, IconTile, InfoLine, SectionHeader, UserAvatar, methodTitle, scopeTitle } from '../components/AppComponents';
import { FeedbackBanner, InputField, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct ChallengeSheet {
  @ObjectLink model: AppModel;
  @State private method: string = '';
  @State private value: string = '';
  @State private recovery: boolean = false;
  @State private grantMode: string = 'PERSISTENT';
  @State private ttl: number = 3600;
  @State private loginWeb: boolean = false;
  @State private remaining: number = 0;
  private timer: number = -1;

  aboutToAppear(): void {
    this.method = this.model.challengeKind === 'mfa' ? this.model.mfa?.method || '' : this.model.sensitive?.preferredMethod || '';
    this.value = ''; this.recovery = false; this.loginWeb = false; this.grantMode = 'PERSISTENT'; this.ttl = 3600;
    this.updateRemaining();
    this.timer = setInterval(() => this.updateRemaining(), 1000);
  }
  aboutToDisappear(): void { if (this.timer !== -1) { clearInterval(this.timer); this.timer = -1; } }
  private updateRemaining(): void {
    const end = this.model.challengeKind === 'consent' ? this.model.consentExpiresAt : this.model.qr?.expiresAt || 0;
    this.remaining = Math.max(0, Math.ceil((end - Date.now()) / 1000));
  }
  private methods(): string[] {
    const values = this.model.challengeKind === 'mfa' ? this.model.mfa?.methods || [] : this.model.sensitive?.methods || [];
    return values.filter((value: string) => ['password', 'email-code', 'totp', 'passkey'].includes(value));
  }
  private selectedMethod(): string {
    return this.methods().includes(this.method) ? this.method : this.methods()[0] || '';
  }
  private submitVerification(): void {
    if (this.model.challengeKind === 'mfa') {
      if (this.selectedMethod() === 'passkey') { this.model.verifyMfaPasskey(); }
      else { this.model.verifyMfa(this.value, this.recovery); }
    } else {
      if (this.selectedMethod() === 'passkey') { this.model.verifySensitivePasskey(); }
      else { this.model.verifySensitive(this.selectedMethod(), this.value, this.recovery); }
    }
  }

  @Builder
  private verification() {
    IconTile({ name: 'security', tileSize: 60 })
    SectionHeader({ title: this.model.challengeKind === 'mfa' ? '完成双重验证' : this.model.sensitiveTitle || '确认是你本人',
      subtitle: this.model.challengeKind === 'mfa' ? '为保护账号，请完成一次身份确认' : '验证通过后继续刚才的操作' })
    if (this.methods().length === 0) {
      Text('当前账号没有本应用支持的验证方式，请通过官方网页补充密码、验证器或通行密钥后重试。')
        .fontSize(14).lineHeight(24).fontColor($r('app.color.text_secondary'))
    } else {
      Select(this.methods().map((value: string): SelectOption => ({ value: methodTitle(value) })))
        .selected(Math.max(0, this.methods().indexOf(this.selectedMethod()))).value(methodTitle(this.selectedMethod()))
        .width('100%').onSelect((index: number) => { this.method = this.methods()[index]; this.value = ''; this.recovery = false; })
      if (this.selectedMethod() === 'passkey') {
        Text('使用设备的面容、指纹或锁屏验证完成身份确认。').fontSize(14).lineHeight(23).fontColor($r('app.color.text_secondary'))
      } else if (this.selectedMethod() === 'email-code') {
        VerificationCodeField({ model: this.model, email: this.model.profile?.email || '', type: 'sensitive-verification',
          code: this.value, onChange: (value: string) => { this.value = value; } })
      } else {
        if (this.selectedMethod() === 'totp') {
          Row() {
            Text('使用恢复码').fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1)
            Toggle({ type: ToggleType.Switch, isOn: this.recovery }).selectedColor($r('app.color.brand'))
              .onChange((value: boolean) => { this.recovery = value; this.value = ''; })
          }.width('100%')
        }
        InputField({ title: this.selectedMethod() === 'password' ? '账号密码' : this.recovery ? '恢复码' : '动态验证码',
          placeholder: this.selectedMethod() === 'password' ? '输入账号密码' : this.recovery ? '输入一个未使用的恢复码' : '6 位数字',
          text: this.value, secure: this.selectedMethod() === 'password', numeric: this.selectedMethod() === 'totp' && !this.recovery,
          maxLength: this.selectedMethod() === 'totp' && !this.recovery ? 6 : 128,
          onChange: (value: string) => { this.value = value; } })
      }
      ActionButton({ title: this.selectedMethod() === 'passkey' ? '使用通行密钥验证' : '完成验证', busy: this.model.busy,
        disabled: this.selectedMethod() !== 'passkey' && !this.value.trim(), onTap: () => this.submitVerification() })
    }
    ActionButton({ title: '取消', secondary: true, disabled: this.model.busy, onTap: () => this.model.cancelChallenge() })
  }

  @Builder
  private qr() {
    IconTile({ name: this.model.qr?.transfer ? 'profile' : 'scan', tileSize: 66 })
    SectionHeader({ title: this.model.qr?.transfer ? '在此手机登录账号？' : '确认网页请求',
      subtitle: this.model.qr?.transfer ? '将使用二维码对应的账号建立本机独立会话' : '请确认这是你本人发起的操作' })
    if (this.model.qr?.transfer && this.model.authenticated) {
      Text('继续后会切换当前登录的账号，请核对原网页中的账号信息。').fontSize(14).lineHeight(23).fontColor($r('app.color.brand'))
    }
    Column() {
      InfoLine({ title: '客户端', value: this.model.qr?.preview.clientName || this.model.qr?.preview.browser || '' })
      InfoLine({ title: '系统', value: this.model.qr?.preview.system || '' })
      InfoLine({ title: '位置', value: this.model.qr?.preview.ipLocation || '' })
      if (this.model.qr?.preview.targetName) { InfoLine({ title: '应用', value: this.model.qr.preview.targetName || '' }) }
      if (this.model.qr?.preview.targetOrigin) { InfoLine({ title: '网站', value: this.model.qr.preview.targetOrigin || '' }) }
      InfoLine({ title: '剩余有效期', value: this.remaining > 0 ? this.remaining + ' 秒' : '已过期，请重新扫码' })
    }.width('100%').padding(18).borderRadius(20).backgroundColor($r('app.color.input_background'))
    if ((this.model.qr?.preview.requestedScopes || []).length > 0) {
      SectionHeader({ title: '请求的资料范围' })
      ForEach(this.model.qr?.preview.requestedScopes || [], (scope: string) => {
        InfoLine({ title: scopeTitle(scope), value: scope })
      }, (scope: string) => scope)
    }
    if (!this.model.qr?.transfer && this.model.profile) {
      InfoLine({ title: '使用账号', value: displayName(this.model.profile) + ' · ' + this.model.profile.email })
    }
    if (!this.model.qr?.transfer && !this.model.authenticated) {
      Text('请先登录账号，再扫描此网页二维码。').fontSize(14).fontColor($r('app.color.brand'))
    }
    ActionButton({ title: this.model.qr?.transfer ? '确认登录此手机' : '确认批准', busy: this.model.busy,
      disabled: this.remaining <= 0 || (!this.model.qr?.transfer && !this.model.authenticated), onTap: () => this.model.confirmQr() })
    ActionButton({ title: this.model.qr?.preview.codeType === 'approve_official' ? '拒绝授权' : '取消', secondary: true,
      disabled: this.model.busy, onTap: () => {
        if (this.model.qr?.preview.codeType === 'approve_official') { this.model.rejectQr(); }
        else { this.model.cancelChallenge(); }
      } })
  }

  @Builder
  private consent() {
    IconTile({ name: 'key', tileSize: 66 })
    SectionHeader({ title: this.model.consent?.appName || '网页应用授权',
      subtitle: this.model.consent?.officialApplication ? '官方应用申请使用你的账号继续' : '第三方应用申请使用你的账号继续' })
    Column() {
      InfoLine({ title: '返回网站', value: this.model.consent?.redirectUri || '' })
      if (this.model.consent?.contactInfo) { InfoLine({ title: '联系方式', value: this.model.consent.contactInfo || '' }) }
      if (this.model.consent?.webOrigin) { InfoLine({ title: '发起网站', value: this.model.consent.webOrigin || '' }) }
      InfoLine({ title: '剩余有效期', value: this.remaining > 0 ? this.remaining + ' 秒' : '已过期，请重新扫码' })
    }.width('100%').padding(18).backgroundColor($r('app.color.input_background')).borderRadius(20)
    SectionHeader({ title: '授权范围', subtitle: '你的密码不会提供给该应用' })
    ForEach(this.model.consent?.requestedScopes || [], (scope: string) => {
      InfoLine({ title: scopeTitle(scope), value: scope })
    }, (scope: string) => scope)
    Row({ space: 12 }) {
      UserAvatar({ url: this.model.profile?.avatarUrl || '', name: displayName(this.model.profile), avatarSize: 44 })
      Column({ space: 5 }) {
        Text(displayName(this.model.profile)).fontSize(16).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(this.model.profile?.email || '').fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start)
    }.width('100%')
    if (!this.model.consent?.officialApplication) {
      SectionHeader({ title: '授权有效期' })
      Select([{ value: '长期' }, { value: '仅本次' }, { value: '限时' }])
        .value(this.grantMode === 'ONE_TIME' ? '仅本次' : this.grantMode === 'TIME_LIMITED' ? '限时' : '长期')
        .onSelect((index: number) => { this.grantMode = ['PERSISTENT', 'ONE_TIME', 'TIME_LIMITED'][index]; }).width('100%')
      if (this.grantMode === 'TIME_LIMITED') {
        Select([{ value: '5 分钟' }, { value: '1 小时' }, { value: '24 小时' }, { value: '7 天' }])
          .selected([300, 3600, 86400, 604800].indexOf(this.ttl))
          .value(['5 分钟', '1 小时', '24 小时', '7 天'][[300, 3600, 86400, 604800].indexOf(this.ttl)])
          .onSelect((index: number) => { this.ttl = [300, 3600, 86400, 604800][index]; }).width('100%')
      }
    }
    Row() {
      Column({ space: 5 }) {
        Text('同时登录通行证网页端').fontSize(14).fontColor($r('app.color.text_primary'))
        Text('默认仅授权当前应用').fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: this.loginWeb }).selectedColor($r('app.color.brand'))
        .onChange((value: boolean) => { this.loginWeb = value; })
    }.width('100%')
    if (this.model.consent?.consoleApplication) {
      Text('管理中心仅允许有权限的管理员登录。').fontSize(13).fontColor($r('app.color.brand'))
    }
    ActionButton({ title: '同意并继续', busy: this.model.busy, disabled: this.remaining <= 0,
      onTap: () => this.model.decideConsent(true, this.grantMode, this.ttl, this.loginWeb) })
    ActionButton({ title: '拒绝授权', secondary: true, busy: this.model.busy,
      onTap: () => this.model.decideConsent(false, this.grantMode, this.ttl, false) })
  }

  build() {
    Scroll() {
      Column({ space: 18 }) {
        FeedbackBanner({ model: this.model })
        if (this.model.challengeKind === 'mfa' || this.model.challengeKind === 'sensitive') { this.verification() }
        else if (this.model.challengeKind === 'qr') { this.qr() }
        else if (this.model.challengeKind === 'consent') { this.consent() }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding(24).alignItems(HorizontalAlign.Start)
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.card_background'))
  }
}
