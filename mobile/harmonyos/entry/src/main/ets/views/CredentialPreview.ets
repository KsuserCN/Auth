import { AppIcon, DemoNote, IconTile, SectionHeader, StatusBadge } from '../components/AppComponents';

@Component
export struct CredentialPreview {
  @Prop kind: string = 'passkeys';
  @State private authenticatorCode: string = '';
  @State private email: string = '';
  @State private emailCode: string = '';
  @State private emailSent: boolean = false;
  @State private currentPassword: string = '';
  @State private newPassword: string = '';
  @State private confirmPassword: string = '';
  @State private loginName: string = '';

  aboutToAppear(): void {
    this.loginName = AppStorage.get<string>('demoUsername') ?? 'linyuan';
  }
  @State private loginPassword: string = '';

  private toast(message: string): void {
    try {
      this.getUIContext().getPromptAction().showToast({ message: message });
    } catch (_) {
      // Toasts are optional feedback in this offline preview.
    }
  }

  private validEmail(): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim());
  }

  private sendEmailCode(): void {
    if (!this.validEmail()) {
      this.toast('请先输入有效的新邮箱地址');
      return;
    }
    this.emailSent = true;
    this.toast('演示发送完成，不会实际发送邮件；可输入任意 6 位数字继续');
  }

  private verifyEmail(): void {
    if (!this.validEmail()) {
      this.toast('请输入有效的新邮箱地址');
    } else if (!this.emailSent) {
      this.toast('请先点击「演示发送」');
    } else if (!/^\d{6}$/.test(this.emailCode)) {
      this.toast('请输入 6 位数字验证码');
    } else {
      this.toast('邮箱验证演示完成，账号邮箱保持原样');
      this.emailCode = '';
      this.emailSent = false;
    }
  }

  private changePassword(): void {
    if (this.currentPassword.length === 0) {
      this.toast('请填写当前密码');
    } else if (this.newPassword.length < 8 || !/[A-Za-z]/.test(this.newPassword) || !/\d/.test(this.newPassword)) {
      this.toast('新密码至少 8 位，需包含字母和数字');
    } else if (this.newPassword !== this.confirmPassword) {
      this.toast('两次输入的新密码不一致');
    } else if (this.currentPassword === this.newPassword) {
      this.toast('新密码应与当前密码不同');
    } else {
      this.toast('修改密码演示完成，未修改真实账号密码');
      this.currentPassword = '';
      this.newPassword = '';
      this.confirmPassword = '';
    }
  }

  @Builder
  private actionButton(title: string, action: () => void) {
    Button(title, { type: ButtonType.Capsule })
      .width('100%').height(50).fontSize(16).fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.on_brand')).backgroundColor($r('app.color.brand')).onClick(action)
  }

  private fieldValue(field: string): string {
    switch (field) {
      case 'email': return this.email;
      case 'current': return this.currentPassword;
      case 'new': return this.newPassword;
      case 'confirm': return this.confirmPassword;
      case 'loginName': return this.loginName;
      default: return this.loginPassword;
    }
  }

  @Builder
  private formField(title: string, placeholder: string, field: string, secure: boolean = false) {
    Column({ space: 9 }) {
      Text(title).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_secondary'))
      TextInput({ placeholder: placeholder, text: this.fieldValue(field) })
        .type(secure ? InputType.Password : field === 'email' ? InputType.Email : InputType.Normal)
        .height(54).maxLength(field === 'email' ? 120 : 64)
        .fontSize(16).fontColor($r('app.color.text_primary'))
        .backgroundColor($r('app.color.input_background')).borderRadius(16)
        .padding({ left: 16, right: 16 }).accessibilityText(title)
        .onChange((text: string) => {
          switch (field) {
            case 'email': this.email = text; this.emailSent = false; break;
            case 'current': this.currentPassword = text; break;
            case 'new': this.newPassword = text; break;
            case 'confirm': this.confirmPassword = text; break;
            case 'loginName': this.loginName = text; break;
            case 'loginPassword': this.loginPassword = text; break;
          }
        })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }

  @Builder
  private passkeyCard(name: string, subtitle: string, icon: string, current: boolean) {
    Column({ space: 16 }) {
      Row({ space: 14 }) {
        IconTile({ name: icon, tileSize: 50 })
        Column({ space: 6 }) {
          Text(name).fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text(subtitle).fontSize(12).fontColor($r('app.color.text_secondary')).lineHeight(18)
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
      }.width('100%')
      Row() {
        StatusBadge({ text: current ? '当前设备' : '已添加', positive: true })
        Blank()
        Text(current ? '今天 09:41 使用' : '昨天 18:26 使用')
          .fontSize(11).fontColor($r('app.color.text_tertiary'))
      }.width('100%')
    }.width('100%').padding(20).borderRadius(24).alignItems(HorizontalAlign.Start)
      .backgroundColor($r('app.color.card_background'))
  }

  build() {
    Column({ space: 16 }) {
      if (this.kind === 'totp') {
        Column({ space: 18 }) {
          Row({ space: 14 }) {
            IconTile({ name: 'security', tileSize: 54, color: $r('app.color.success'),
              tileBackground: $r('app.color.success_soft') })
            Column({ space: 7 }) {
              Text('多一层验证，多一份安心').fontSize(20).fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.text_primary'))
              StatusBadge({ text: '验证器已启用' })
            }.layoutWeight(1).alignItems(HorizontalAlign.Start)
          }.width('100%')
          Text('登录或进行重要操作时，输入验证器中的一次性验证码，确认是你本人。')
            .fontSize(14).fontColor($r('app.color.text_secondary')).lineHeight(23)
        }.width('100%').padding(20).borderRadius(24).alignItems(HorizontalAlign.Start)
          .backgroundColor($r('app.color.card_background'))
        Column({ space: 18 }) {
          SectionHeader({ title: '试试一次性验证码', subtitle: '输入任意 6 位数字，体验验证流程' })
          TextInput({ placeholder: '6 位数字验证码', text: this.authenticatorCode })
            .type(InputType.Number).maxLength(6).height(60).fontSize(23).letterSpacing(5)
            .textAlign(TextAlign.Center).fontColor($r('app.color.text_primary'))
            .backgroundColor($r('app.color.input_background')).borderRadius(16)
            .onChange((text: string) => { this.authenticatorCode = text; })
            .accessibilityText('一次性验证码')
          this.actionButton('演示验证', () => {
            if (/^\d{6}$/.test(this.authenticatorCode)) {
              this.toast('演示验证通过，仅检查了 6 位数字格式');
              this.authenticatorCode = '';
            } else {
              this.toast('请输入 6 位数字验证码');
            }
          })
        }.width('100%').padding(20).borderRadius(24)
          .backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'email') {
        Column({ space: 16 }) {
          IconTile({ name: 'mail', tileSize: 52 })
          SectionHeader({ title: '让重要提醒，及时送达', subtitle: '当前邮箱：linyuan@example.com' })
          Divider().color($r('app.color.divider'))
          this.formField('新邮箱', '输入新的邮箱地址', 'email')
          Column({ space: 9 }) {
            Text('邮箱验证码').fontSize(13).fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.text_secondary'))
            Row({ space: 10 }) {
              TextInput({ placeholder: '6 位验证码', text: this.emailCode })
                .type(InputType.Number).maxLength(6).height(54).layoutWeight(1)
                .fontSize(16).fontColor($r('app.color.text_primary'))
                .backgroundColor($r('app.color.input_background')).borderRadius(16)
                .onChange((text: string) => { this.emailCode = text; }).accessibilityText('邮箱验证码')
              Button(this.emailSent ? '重新演示' : '演示发送', { type: ButtonType.Capsule })
                .height(44).fontSize(13).fontColor($r('app.color.brand'))
                .backgroundColor($r('app.color.brand_soft')).onClick(() => this.sendEmailCode())
            }.width('100%')
          }.width('100%').alignItems(HorizontalAlign.Start)
          this.actionButton('演示验证邮箱', () => this.verifyEmail())
        }.width('100%').padding(20).borderRadius(24).alignItems(HorizontalAlign.Start)
          .backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'password') {
        Column({ space: 18 }) {
          IconTile({ name: 'lock', tileSize: 52 })
          SectionHeader({ title: '为账号换一把新钥匙', subtitle: '新密码至少 8 位，包含字母和数字' })
          this.formField('当前密码', '输入当前密码', 'current', true)
          this.formField('新密码', '设置一个新的密码', 'new', true)
          this.formField('确认新密码', '再次输入新密码', 'confirm', true)
          this.actionButton('演示修改密码', () => this.changePassword())
        }.width('100%').padding(20).borderRadius(24).alignItems(HorizontalAlign.Start)
          .backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'login') {
        Column({ space: 12 }) {
          Image($r('app.media.brand_logo')).width(72).height(72).borderRadius(22)
        .accessibilityText('Ksuser 标志')
          Text('登录 Ksuser').fontSize(28).fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
          Text('一个账号，连接你的数字生活。').fontSize(14).fontColor($r('app.color.text_secondary'))
        }.width('100%').padding({ top: 12, bottom: 12 })
        Column({ space: 18 }) {
          this.formField('用户名', '输入你的用户名', 'loginName')
          this.formField('密码', '输入你的密码', 'loginPassword', true)
          this.actionButton('演示登录', () => {
            if (this.loginName.trim().length === 0 || this.loginPassword.length === 0) {
              this.toast('请填写用户名和密码');
            } else {
              this.toast('登录界面演示完成，未发起真实登录');
              this.loginPassword = '';
            }
          })
        }.width('100%').padding(20).borderRadius(24)
          .backgroundColor($r('app.color.card_background'))
        Button({ type: ButtonType.Capsule }) {
          Row({ space: 10 }) {
            AppIcon({ name: 'key', iconSize: 21 })
            Text('使用通行密钥').fontSize(15).fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.brand'))
          }.justifyContent(FlexAlign.Center).width('100%')
        }.width('100%').height(50).backgroundColor($r('app.color.brand_soft'))
          .onClick(() => this.toast('通行密钥登录将在后续接入，当前为界面演示'))
      } else {
        Column({ space: 10 }) {
          SectionHeader({ title: '用你熟悉的方式，安全登录', subtitle: '使用面容、指纹或设备锁屏验证，无需记住密码' })
        }.width('100%').padding({ top: 4, bottom: 4 })
        this.passkeyCard('HUAWEI Mate 70', 'HarmonyOS · 设备通行密钥', 'phone', true)
        this.passkeyCard('MacBook Pro', 'macOS · iCloud 钥匙串', 'devices', false)
        Button({ type: ButtonType.Capsule }) {
          Row({ space: 8 }) {
            AppIcon({ name: 'plus', iconSize: 18, color: $r('app.color.on_brand') })
            Text('添加通行密钥').fontSize(16).fontWeight(FontWeight.Medium).fontColor($r('app.color.on_brand'))
          }.justifyContent(FlexAlign.Center).width('100%')
        }.width('100%').height(50).backgroundColor($r('app.color.brand'))
          .onClick(() => this.toast('添加通行密钥将在后续接入，当前使用示范列表'))
      }
      DemoNote({ text: '当前为界面演示，输入内容不会发送至服务器' })
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}
