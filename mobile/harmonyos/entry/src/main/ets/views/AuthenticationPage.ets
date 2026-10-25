import { AppModel } from '../core/AppModel';
import { passwordError, passwordRequirementText, validEmail, validUsername } from '../core/Models';
import { setAgreementAccepted } from '../core/DevicePreferences';
import { ActionButton, AppIcon, SectionHeader } from '../components/AppComponents';
import { FeedbackBanner, InputField, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct AuthenticationPage {
  @ObjectLink model: AppModel;
  onSettings: () => void = () => {};
  @StorageLink('agreementAccepted') private acceptedTerms: boolean = false;
  @State private mode: string = 'login';
  @State private useCode: boolean = false;
  @State private email: string = '';
  @State private password: string = '';
  @State private code: string = '';
  @State private username: string = '';
  @State private confirmPassword: string = '';
  @State private recoveryCode: string = '';
  @State private step: number = 0;
  @State private submitting: boolean = false;
  @State private passkeyBusy: boolean = false;
  private stepTitles: string[] = ['选择用户名', '设置密码', '确认密码', '输入邮箱', '输入验证码'];

  aboutToDisappear(): void { this.clearSecrets(); }

  private clearSecrets(): void { this.password = ''; this.confirmPassword = ''; this.code = ''; this.recoveryCode = ''; }
  private resetMode(mode: string): void {
    this.mode = mode; this.step = 0; this.clearSecrets(); this.model.clearFeedback();
    if (mode === 'register') { this.model.loadPasswordRequirement(); }
  }
  private async acceptTerms(value: boolean): Promise<void> {
    try { await setAgreementAccepted(value); }
    catch (_) { this.model.error = '暂时无法保存协议选择，请重试'; }
  }
  private canLogin(): boolean {
    return this.acceptedTerms && validEmail(this.email) && (this.useCode ? /^\d{6}$/.test(this.code) : this.password.length > 0);
  }
  private canContinue(): boolean {
    switch (this.step) {
      case 0: return validUsername(this.username);
      case 1: return this.model.passwordPolicy !== null && !passwordError(this.password, this.model.passwordPolicy) && this.password.length > 0;
      case 2: return this.confirmPassword.length > 0 && this.confirmPassword === this.password;
      case 3: return validEmail(this.email) && this.acceptedTerms;
      default: return /^\d{6}$/.test(this.code) && this.acceptedTerms;
    }
  }
  private async login(): Promise<void> {
    if (!this.canLogin() || this.submitting || this.model.busy) { return; }
    this.submitting = true;
    try {
      const result = this.useCode ? await this.model.loginWithCode(this.email.trim(), this.code) :
        await this.model.login(this.email.trim(), this.password);
      if (result.status === 'success') { this.clearSecrets(); }
    } finally { this.submitting = false; }
  }
  private async passkey(): Promise<void> {
    if (!this.acceptedTerms || this.model.busy || this.passkeyBusy) { return; }
    this.passkeyBusy = true;
    try {
      const result = await this.model.loginPasskey();
      if (result.status === 'success') { this.clearSecrets(); }
    } finally { this.passkeyBusy = false; }
  }
  private async continueRegistration(): Promise<void> {
    if (!this.canContinue() || this.submitting || this.model.busy) { return; }
    this.submitting = true;
    try {
      switch (this.step) {
        case 0:
          const username = this.username.trim();
          if (await this.model.checkUsernameAvailable(username) && this.username.trim() === username) { this.step = 1; }
          break;
        case 1:
          const candidate = this.password;
          if (await this.model.validateRegistrationPassword(candidate) && this.password === candidate) { this.step = 2; }
          break;
        case 3:
          const email = this.email.trim();
          const sent = await this.model.sendCode(email, 'register');
          if (sent.status === 'success' && this.email.trim().toLowerCase() === email.toLowerCase()) { this.step = 4; }
          break;
        case 4:
          const registered = await this.model.register(this.username.trim(), this.email.trim(), this.password, this.code);
          if (registered.status === 'success') { this.clearSecrets(); }
          break;
        default: this.step++;
      }
    } finally { this.submitting = false; }
  }

  @Builder
  private agreement() {
    Column({ space: 6 }) {
      Row({ space: 10 }) {
        Checkbox().select(this.acceptedTerms).selectedColor($r('app.color.brand'))
          .onChange((value: boolean) => this.acceptTerms(value)).accessibilityText('同意服务协议与隐私政策')
        Text('我已阅读并同意以下条款').fontSize(13).fontColor($r('app.color.text_secondary'))
          .layoutWeight(1).lineHeight(20)
      }.width('100%').constraintSize({ minHeight: 44 })
      Row({ space: 16 }) {
        Button('服务协议').fontSize(13).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
          .onClick(() => this.model.openLegal('terms'))
        Button('隐私政策').fontSize(13).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
          .onClick(() => this.model.openLegal('privacy'))
      }.width('100%')
      Button('第三方信息共享清单').fontSize(13).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
        .onClick(() => this.model.openLegal('sharing')).alignSelf(ItemAlign.Start)
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private policy() {
    if (this.model.passwordPolicy !== null) {
      Text(passwordRequirementText(this.model.passwordPolicy)).fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
      if (this.password.length > 0 && passwordError(this.password, this.model.passwordPolicy)) {
        Text(passwordError(this.password, this.model.passwordPolicy)).fontSize(13).lineHeight(21).fontColor($r('app.color.danger'))
      }
    } else {
      Text(this.model.passwordPolicyState.status === 'loading' ? '正在获取密码要求…' : '密码要求暂时无法读取')
        .fontSize(13).fontColor($r('app.color.text_secondary'))
      ActionButton({ title: '重试获取密码要求', secondary: true, busy: this.model.passwordPolicyState.status === 'loading',
        onTap: () => this.model.loadPasswordRequirement() })
    }
  }

  @Builder
  private registration() {
    Row() {
      Text(this.stepTitles[this.step]).fontSize(18).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary')).layoutWeight(1)
      Text((this.step + 1) + ' / 5').fontSize(13).fontColor($r('app.color.text_secondary'))
    }.width('100%')
    Progress({ value: this.step + 1, total: 5, type: ProgressType.Linear }).width('100%')
      .color($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
    if (this.step === 0) {
      InputField({ title: '用户名', text: this.username, placeholder: '设置你的用户名', maxLength: 20,
        onChange: (value: string) => { this.username = value; } })
      Text('3–20 个字符，支持汉字、字母、数字、下划线和连字符。').fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
    } else if (this.step === 1) {
      InputField({ title: '设置密码', text: this.password, placeholder: '输入新的密码', secure: true,
        onChange: (value: string) => { this.password = value; } })
      this.policy()
    } else if (this.step === 2) {
      InputField({ title: '确认密码', text: this.confirmPassword, placeholder: '再次输入密码', secure: true,
        onChange: (value: string) => { this.confirmPassword = value; } })
      if (this.confirmPassword && this.confirmPassword !== this.password) {
        Text('两次输入的密码不一致').fontSize(13).fontColor($r('app.color.danger'))
      }
    } else if (this.step === 3) {
      InputField({ title: '注册邮箱', text: this.email, placeholder: '输入邮箱地址', email: true,
        onChange: (value: string) => { this.email = value; this.code = ''; } })
      Text('我们会向这个邮箱发送验证码。').fontSize(13).fontColor($r('app.color.text_secondary'))
    } else {
      Text('验证码已发送至 ' + this.email.trim()).fontSize(14).lineHeight(22).fontColor($r('app.color.text_secondary'))
      VerificationCodeField({ model: this.model, email: this.email, type: 'register', code: this.code, allowed: this.acceptedTerms,
        onChange: (value: string) => { this.code = value; } })
    }
    ActionButton({ title: this.step === 4 ? '注册并登录' : this.step === 3 ? '发送验证码并继续' : '继续',
      busy: this.submitting, disabled: !this.canContinue() || this.model.busy, onTap: () => this.continueRegistration() })
    if (this.step > 0) {
      Button('上一步').height(44).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
        .enabled(!this.submitting && !this.model.busy).onClick(() => { this.step--; this.model.clearFeedback(); })
    }
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Row() {
          Button({ type: ButtonType.Circle }) { AppIcon({ name: 'scan', iconSize: 22 }) }
            .width(44).height(44).backgroundColor($r('app.color.card_background')).enabled(!this.model.busy)
            .accessibilityText('扫码登录').onClick(() => this.model.scan())
          Blank()
          Button({ type: ButtonType.Circle }) { AppIcon({ name: 'settings', iconSize: 22 }) }
            .width(44).height(44).backgroundColor($r('app.color.card_background'))
            .accessibilityText('关于与设置').onClick(() => this.onSettings())
        }.width('100%')
        Column({ space: 10 }) {
          Row({ space: 12 }) {
            Image($r('app.media.brand_logo')).width(48).height(48).borderRadius(14)
            Text(this.mode === 'register' ? '创建 Ksuser 账号' : this.mode === 'recovery' ? '恢复你的账号' : '登录 Ksuser')
              .fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          }.width('100%').justifyContent(FlexAlign.Center)
          Text('一个账号，连接你的数字生活').fontSize(13).fontColor($r('app.color.text_secondary'))
        }.width('100%').padding({ top: 6, bottom: 10 })
        FeedbackBanner({ model: this.model })
        if (this.mode === 'recovery') {
          Column({ space: 18 }) {
            SectionHeader({ title: '通过官方网页找回', subtitle: '在受信任的 Ksuser 网页中完成身份核验与密码重置。' })
            InputField({ title: '账号恢复码（可选）', text: this.recoveryCode, placeholder: '已有恢复授权时填写',
              onChange: (value: string) => { this.recoveryCode = value; } })
            ActionButton({ title: '打开账号恢复', busy: this.model.busy, onTap: () => this.model.openRecovery(this.recoveryCode) })
          }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
        } else {
          Column({ space: 18 }) {
            if (this.mode === 'register') { this.registration() }
            else {
              Row({ space: 8 }) {
                Button('密码登录').height(44).layoutWeight(1).fontColor(this.useCode ? $r('app.color.text_secondary') : $r('app.color.brand'))
                  .backgroundColor(this.useCode ? Color.Transparent : $r('app.color.brand_soft'))
                  .enabled(!this.model.busy).onClick(() => { this.useCode = false; this.code = ''; })
                Button('验证码登录').height(44).layoutWeight(1).fontColor(this.useCode ? $r('app.color.brand') : $r('app.color.text_secondary'))
                  .backgroundColor(this.useCode ? $r('app.color.brand_soft') : Color.Transparent)
                  .enabled(!this.model.busy).onClick(() => { this.useCode = true; this.password = ''; })
              }.width('100%')
              InputField({ title: '邮箱', text: this.email, placeholder: '输入邮箱地址', email: true,
                onChange: (value: string) => { this.email = value; this.code = ''; } })
              if (this.useCode) {
                VerificationCodeField({ model: this.model, email: this.email, type: 'login', code: this.code, allowed: this.acceptedTerms,
                  onChange: (value: string) => { this.code = value; } })
              } else {
                InputField({ title: '密码', text: this.password, placeholder: '输入账号密码', secure: true,
                  onChange: (value: string) => { this.password = value; } })
              }
              ActionButton({ title: '登录', busy: this.submitting, disabled: !this.canLogin() || this.model.busy, onTap: () => this.login() })
            }
          }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
          if (this.mode === 'login') {
            ActionButton({ title: '使用通行密钥', secondary: true, busy: this.passkeyBusy,
              disabled: !this.acceptedTerms || this.model.busy, onTap: () => this.passkey() })
          }
          this.agreement()
        }
        Row({ space: 12 }) {
          Button(this.mode === 'login' ? '注册账号' : '返回登录').height(44).fontSize(14).fontColor($r('app.color.brand'))
            .backgroundColor(Color.Transparent).enabled(!this.model.busy)
            .onClick(() => this.resetMode(this.mode === 'login' ? 'register' : 'login'))
          if (this.mode === 'login') {
            Button('无法登录？恢复账号').height(44).fontSize(14).fontColor($r('app.color.text_secondary'))
              .backgroundColor(Color.Transparent).enabled(!this.model.busy).onClick(() => this.resetMode('recovery'))
          }
        }.width('100%').justifyContent(FlexAlign.Center)
      }.width('100%').constraintSize({ maxWidth: 620 }).padding({ left: 24, right: 24, top: 16, bottom: 30 })
    }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart).backgroundColor($r('app.color.page_background'))
  }
}
