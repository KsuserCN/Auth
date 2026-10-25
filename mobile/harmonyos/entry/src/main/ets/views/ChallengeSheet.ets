import { AppModel } from '../core/AppModel';
import { displayName } from '../core/Models';
import { ActionButton, ApplicationIcon, IconTile, InfoLine, SectionHeader, StatusBadge, UserAvatar, methodTitle, scopeTitle } from '../components/AppComponents';
import { scopeDescription, verificationTitle, websiteOrigin } from '../core/Presentation';
import { FeedbackBanner, InputField, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct ChallengeSheet {
  @ObjectLink @Watch('syncChallenge') model: AppModel;
  @State private method: string = '';
  @State private value: string = '';
  @State private recovery: boolean = false;
  @State private grantMode: string = 'PERSISTENT';
  @State private ttl: number = 3600;
  @State private loginWeb: boolean = false;
  @State private remaining: number = 0;
  private timer: number = -1;
  private identity: string = '';

  aboutToAppear(): void {
    this.syncChallenge();
    this.timer = setInterval(() => this.updateRemaining(), 1000);
  }
  private syncChallenge(): void {
    const identity = this.model.challengeRevision + ':' + this.model.challengeKind + ':' + (this.model.mfa?.challengeId || this.model.qr?.code || this.model.consent?.ticket || this.model.sensitiveTitle);
    if (identity === this.identity) { return; }
    this.identity = identity;
    this.method = this.model.challengeKind === 'mfa' ? this.model.mfa?.method || '' : this.model.sensitive?.preferredMethod || '';
    this.value = ''; this.recovery = false; this.loginWeb = false; this.grantMode = 'PERSISTENT'; this.ttl = 3600;
    this.updateRemaining();
  }
  aboutToDisappear(): void {
    if (this.timer !== -1) { clearInterval(this.timer); this.timer = -1; }
    this.value = ''; this.method = ''; this.recovery = false; this.identity = '';
  }
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
  private canVerify(): boolean {
    if (this.selectedMethod() === 'passkey') { return true; }
    if (this.selectedMethod() === 'password') { return this.value.length > 0; }
    return this.recovery ? !!this.value.trim() : /^\d{6}$/.test(this.value);
  }
  private qrTitle(): string {
    if (this.model.qr?.transfer) { return '在此手机登录账号'; }
    switch (this.model.qr?.preview.codeType) {
      case 'approve_login': return '确认网页登录';
      case 'approve_mfa': return '批准网页登录的双重验证';
      case 'approve_sensitive': return '批准网页敏感操作';
      case 'approve_console_login': return '确认登录管理中心';
      case 'approve_official': return this.model.qr?.preview.consoleApplication ? '确认登录管理中心' : '确认官方应用授权';
      default: return '确认网页请求';
    }
  }
  private confirmQr(): void {
    if (this.model.qr?.transfer && this.model.authenticated) {
      this.getUIContext().showAlertDialog({ title: '切换当前账号？', message: '将使用二维码对应的账号登录本机。请核对原网页中的账号信息。',
        primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '确认切换', action: () => this.model.confirmQr() } });
    } else { this.model.confirmQr(); }
  }
  @Builder
  private permission(scope: string) {
    Column({ space: 5 }) {
      Text(scopeTitle(scope)).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
      Text(scopeDescription(scope)).fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
    }.width('100%').alignItems(HorizontalAlign.Start).padding({ top: 5, bottom: 5 })
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
        disabled: !this.canVerify(), onTap: () => this.submitVerification() })
    }
    ActionButton({ title: '取消', secondary: true, disabled: this.model.busy, onTap: () => this.model.cancelChallenge() })
  }

  @Builder
  private qr() {
    if (this.model.qr?.preview.targetLogoUrl) {
      ApplicationIcon({ url: this.model.qr.preview.targetLogoUrl || '', name: this.model.qr.preview.targetName || '应用', iconSize: 66 })
    } else { IconTile({ name: this.model.qr?.transfer ? 'profile' : 'scan', tileSize: 66 }) }
    SectionHeader({ title: this.qrTitle(),
      subtitle: this.model.qr?.transfer ? '将使用二维码对应的账号建立本机独立会话' : '请确认这是你本人发起的操作' })
    if (this.model.qr?.transfer && this.model.authenticated) {
      Text('继续后会切换当前登录的账号，请核对原网页中的账号信息。').fontSize(14).lineHeight(23).fontColor($r('app.color.brand'))
    }
    Column() {
      InfoLine({ title: '客户端', value: this.model.qr?.preview.clientName || this.model.qr?.preview.browser || '' })
      InfoLine({ title: '系统', value: this.model.qr?.preview.system || '' })
      InfoLine({ title: '位置', value: this.model.qr?.preview.ipLocation || '' })
      InfoLine({ title: 'IP 地址', value: this.model.qr?.preview.ipAddress || '' })
      if (this.model.qr?.preview.targetName) { InfoLine({ title: '应用', value: this.model.qr.preview.targetName || '' }) }
      if (this.model.qr?.preview.targetOrigin) { InfoLine({ title: '网站', value: this.model.qr.preview.targetOrigin || '' }) }
      InfoLine({ title: '剩余有效期', value: this.remaining > 0 ? this.remaining + ' 秒' : '已过期，请重新扫码' })
    }.width('100%').padding(18).borderRadius(20).backgroundColor($r('app.color.input_background'))
    if ((this.model.qr?.preview.requestedScopes || []).length > 0) {
      SectionHeader({ title: '请求的资料范围' })
      ForEach(this.model.qr?.preview.requestedScopes || [], (scope: string) => {
        this.permission(scope)
      }, (scope: string) => scope)
    }
    if (!this.model.qr?.transfer && this.model.profile) {
      InfoLine({ title: '使用账号', value: displayName(this.model.profile) + ' · ' + this.model.profile.email })
    }
    if (!this.model.qr?.transfer && !this.model.authenticated) {
      Text('请先登录账号，再扫描此网页二维码。').fontSize(14).fontColor($r('app.color.brand'))
    }
    Text('仅确认由你本人发起的请求。如果不认识来源设备或网站，请取消本次请求。')
      .fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
  }

  @Builder
  private consent() {
    ApplicationIcon({ url: this.model.consent?.appLogoUrl || this.model.consent?.logoUrl || '', name: this.model.consent?.appName || '网页应用', iconSize: 66 })
    SectionHeader({ title: this.model.consent?.appName || '网页应用授权',
      subtitle: this.model.consent?.officialApplication ? '官方应用申请使用你的账号继续' : '第三方应用申请使用你的账号继续' })
    Column() {
      InfoLine({ title: '返回网站', value: websiteOrigin(this.model.consent?.redirectUri) })
      if (this.model.consent?.contactInfo) { InfoLine({ title: '联系方式', value: this.model.consent.contactInfo || '' }) }
      if (this.model.consent?.webOrigin) { InfoLine({ title: '发起网站', value: this.model.consent.webOrigin || '' }) }
      InfoLine({ title: '剩余有效期', value: this.remaining > 0 ? this.remaining + ' 秒' : '已过期，请重新扫码' })
    }.width('100%').padding(18).backgroundColor($r('app.color.input_background')).borderRadius(20)
    if (this.model.consent?.officialApplication) { StatusBadge({ text: 'Ksuser 官方应用' }) }
    if (this.model.consent?.subjectName) {
      InfoLine({ title: '应用主体', value: this.model.consent.subjectName || '' })
      StatusBadge({ text: verificationTitle(this.model.consent.subjectVerificationType), positive: ['personal', 'enterprise', 'admin'].includes(this.model.consent.subjectVerificationType || '') })
    }
    SectionHeader({ title: '授权范围', subtitle: '你的密码不会提供给该应用' })
    ForEach(this.model.consent?.requestedScopes || [], (scope: string) => {
      this.permission(scope)
    }, (scope: string) => scope)
    Row({ space: 12 }) {
      UserAvatar({ url: this.model.profile?.avatarUrl || '', name: displayName(this.model.profile), avatarSize: 44 })
      Column({ space: 5 }) {
        Text(displayName(this.model.profile)).fontSize(16).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(this.model.profile?.email || '未绑定邮箱').fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start)
    }.width('100%')
    if (!this.model.consent?.officialApplication) {
      SectionHeader({ title: '授权有效期' })
      Select([{ value: '长期' }, { value: '仅本次' }, { value: '限时' }])
        .selected(['PERSISTENT', 'ONE_TIME', 'TIME_LIMITED'].indexOf(this.grantMode))
        .value(this.grantMode === 'ONE_TIME' ? '仅本次' : this.grantMode === 'TIME_LIMITED' ? '限时' : '长期')
        .onSelect((index: number) => { this.grantMode = ['PERSISTENT', 'ONE_TIME', 'TIME_LIMITED'][index]; }).width('100%')
      if (this.grantMode === 'TIME_LIMITED') {
        Select([{ value: '5 分钟' }, { value: '1 小时' }, { value: '24 小时' }, { value: '7 天' }])
          .selected([300, 3600, 86400, 604800].indexOf(this.ttl))
          .value(['5 分钟', '1 小时', '24 小时', '7 天'][[300, 3600, 86400, 604800].indexOf(this.ttl)])
          .onSelect((index: number) => { this.ttl = [300, 3600, 86400, 604800][index]; }).width('100%')
      }
      Text(this.grantMode === 'ONE_TIME' ? '仅允许本次请求使用这些资料。' : this.grantMode === 'TIME_LIMITED' ?
        '在选择的期限内允许访问，到期后需要重新授权。' : '持续允许访问已确认的资料，可随时在授权应用中撤销。')
        .fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
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
  }

  @Builder
  private decisions() {
    Column({ space: 10 }) {
      if (this.model.challengeKind === 'consent') {
        ActionButton({ title: '同意并继续', busy: this.model.busy, disabled: this.remaining <= 0,
          onTap: () => this.model.decideConsent(true, this.grantMode, this.ttl, this.loginWeb) })
        ActionButton({ title: this.remaining <= 0 ? '关闭已过期请求' : '拒绝授权', secondary: true, disabled: this.model.busy,
          onTap: () => { if (this.remaining <= 0) { this.model.cancelChallenge(); } else { this.model.decideConsent(false, this.grantMode, this.ttl, false); } } })
      } else {
        ActionButton({ title: this.model.qr?.transfer ? '确认登录此手机' : this.qrTitle(), busy: this.model.busy,
          disabled: this.remaining <= 0 || (!this.model.qr?.transfer && !this.model.authenticated), onTap: () => this.confirmQr() })
        ActionButton({ title: this.model.qr?.preview.codeType === 'approve_official' && this.remaining > 0 ? '拒绝授权' : '取消', secondary: true,
          disabled: this.model.busy, onTap: () => {
            if (this.model.qr?.preview.codeType === 'approve_official' && this.remaining > 0) { this.model.rejectQr(); }
            else { this.model.cancelChallenge(); }
          } })
      }
    }.width('100%').padding({ left: 24, right: 24, top: 12, bottom: 24 }).backgroundColor($r('app.color.card_background'))
  }

  build() {
    Column() {
    Scroll() {
      Column({ space: 18 }) {
        FeedbackBanner({ model: this.model })
        if (this.model.challengeKind === 'mfa' || this.model.challengeKind === 'sensitive') { this.verification() }
        else if (this.model.challengeKind === 'qr') { this.qr() }
        else if (this.model.challengeKind === 'consent') { this.consent() }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding(24).alignItems(HorizontalAlign.Start)
    }.width('100%').layoutWeight(1).scrollBar(BarState.Off).align(Alignment.TopStart)
    if (this.model.challengeKind === 'qr' || this.model.challengeKind === 'consent') { this.decisions() }
    }.width('100%').height('100%').backgroundColor($r('app.color.card_background'))
  }
}
