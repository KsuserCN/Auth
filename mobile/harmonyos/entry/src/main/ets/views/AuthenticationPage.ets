import { AppModel } from '../core/AppModel';
import { ActionButton, AppIcon, SectionHeader } from '../components/AppComponents';
import { FeedbackBanner, InputField, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct AuthenticationPage {
  @ObjectLink model: AppModel;
  onSettings: () => void = () => {};
  @State private mode: string = 'login';
  @State private useCode: boolean = false;
  @State private email: string = '';
  @State private password: string = '';
  @State private code: string = '';
  @State private username: string = '';
  @State private confirmPassword: string = '';
  @State private recoveryCode: string = '';
  @State private acceptedTerms: boolean = false;

  private async submit(): Promise<void> {
    if (this.mode === 'register') {
      if (this.password !== this.confirmPassword) { this.model.error = '两次输入的密码不一致'; return; }
      if (!this.acceptedTerms) { this.model.error = '请先阅读并同意用户协议与隐私政策'; return; }
      await this.model.register(this.username, this.email, this.password, this.code);
    } else if (this.useCode) {
      await this.model.loginWithCode(this.email, this.code);
    } else {
      await this.model.login(this.email, this.password);
    }
    this.password = ''; this.confirmPassword = '';
  }

  private resetMode(mode: string): void {
    this.mode = mode; this.password = ''; this.code = ''; this.model.clearFeedback();
  }

  build() {
    Scroll() {
      Column({ space: 20 }) {
        Row() {
          Button({ type: ButtonType.Circle }) { AppIcon({ name: 'scan', iconSize: 22 }) }
            .width(44).height(44).backgroundColor($r('app.color.card_background'))
            .accessibilityText('扫码登录').onClick(() => this.model.scan())
          Blank()
          Button({ type: ButtonType.Circle }) { AppIcon({ name: 'settings', iconSize: 22 }) }
            .width(44).height(44).backgroundColor($r('app.color.card_background'))
            .accessibilityText('关于与设置').onClick(() => this.onSettings())
        }.width('100%')
        Column({ space: 12 }) {
          Image($r('app.media.brand_logo')).width(76).height(76).borderRadius(22)
          Text(this.mode === 'register' ? '创建 Ksuser 账号' : this.mode === 'recovery' ? '恢复你的账号' : '登录 Ksuser')
            .fontSize(28).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text('一个账号，连接你的数字生活').fontSize(14).fontColor($r('app.color.text_secondary'))
        }.width('100%').padding({ top: 12, bottom: 16 })
        FeedbackBanner({ model: this.model })
        if (this.mode === 'recovery') {
          Column({ space: 18 }) {
            SectionHeader({ title: '通过官方网页找回', subtitle: '在受信任的 Ksuser 网页中完成身份核验与密码重置。' })
            InputField({ title: '账号恢复码（可选）', text: this.recoveryCode, placeholder: '已有恢复授权时填写',
              onChange: (value: string) => { this.recoveryCode = value; } })
            ActionButton({ title: '打开账号恢复', busy: this.model.busy, onTap: () => this.model.openRecovery(this.recoveryCode) })
          }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
        } else {
          Column({ space: 18 }) {
            if (this.mode === 'login') {
              Row({ space: 8 }) {
                Button('密码登录').layoutWeight(1).fontColor(this.useCode ? $r('app.color.text_secondary') : $r('app.color.brand'))
                  .backgroundColor(this.useCode ? Color.Transparent : $r('app.color.brand_soft')).onClick(() => { this.useCode = false; })
                Button('验证码登录').layoutWeight(1).fontColor(this.useCode ? $r('app.color.brand') : $r('app.color.text_secondary'))
                  .backgroundColor(this.useCode ? $r('app.color.brand_soft') : Color.Transparent).onClick(() => { this.useCode = true; })
              }.width('100%')
            }
            if (this.mode === 'register') {
              InputField({ title: '用户名', text: this.username, placeholder: '设置你的用户名', maxLength: 20,
                onChange: (value: string) => { this.username = value; } })
            }
            InputField({ title: '邮箱', text: this.email, placeholder: '输入邮箱地址',
              onChange: (value: string) => { this.email = value; } })
            if (this.mode === 'register' || this.useCode) {
              VerificationCodeField({ model: this.model, email: this.email, type: this.mode === 'register' ? 'register' : 'login',
                code: this.code, onChange: (value: string) => { this.code = value; } })
            }
            if (this.mode === 'register' || !this.useCode) {
              InputField({ title: '密码', text: this.password, placeholder: '输入密码', secure: true,
                onChange: (value: string) => { this.password = value; } })
            }
            if (this.mode === 'register') {
              if (this.model.passwordPolicy !== null) {
                Text('密码长度 ' + this.model.passwordPolicy!.minLength + '–' + this.model.passwordPolicy!.maxLength + ' 位；' +
                  (this.model.passwordPolicy!.requireUppercase ? '包含大写字母；' : '') +
                  (this.model.passwordPolicy!.requireLowercase ? '包含小写字母；' : '') +
                  (this.model.passwordPolicy!.requireDigits ? '包含数字；' : '') +
                  (this.model.passwordPolicy!.requireSpecialChars ? '包含特殊字符' : ''))
                  .fontSize(12).lineHeight(20).fontColor($r('app.color.text_secondary'))
              }
              InputField({ title: '确认密码', text: this.confirmPassword, placeholder: '再次输入密码', secure: true,
                onChange: (value: string) => { this.confirmPassword = value; } })
              Row({ space: 8 }) {
                Checkbox().select(this.acceptedTerms).selectedColor($r('app.color.brand'))
                  .onChange((value: boolean) => { this.acceptedTerms = value; }).accessibilityText('同意用户协议与隐私政策')
                Text('我已阅读并同意').fontSize(12).fontColor($r('app.color.text_secondary'))
                Button('用户协议').fontSize(12).padding(0).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
                  .onClick(() => this.model.openLegal('terms'))
                Button('隐私政策').fontSize(12).padding(0).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
                  .onClick(() => this.model.openLegal('privacy'))
              }.width('100%')
            }
            ActionButton({ title: this.mode === 'register' ? '注册并登录' : '登录', busy: this.model.busy,
              disabled: !this.email.trim() || (this.mode === 'register' && !this.acceptedTerms), onTap: () => this.submit() })
          }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
          if (this.mode === 'login') {
            ActionButton({ title: '使用通行密钥', secondary: true, busy: this.model.busy, onTap: () => this.model.loginPasskey() })
          }
        }
        Row({ space: 18 }) {
          Button(this.mode === 'login' ? '注册账号' : '返回登录').fontSize(14).fontColor($r('app.color.brand'))
            .backgroundColor(Color.Transparent).onClick(() => this.resetMode(this.mode === 'login' ? 'register' : 'login'))
          if (this.mode === 'login') {
            Button('无法登录？恢复账号').fontSize(14).fontColor($r('app.color.text_secondary'))
              .backgroundColor(Color.Transparent).onClick(() => this.resetMode('recovery'))
          }
        }.width('100%').justifyContent(FlexAlign.Center)
      }.width('100%').constraintSize({ maxWidth: 620 }).padding({ left: 24, right: 24, top: 16, bottom: 30 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
  }
}
