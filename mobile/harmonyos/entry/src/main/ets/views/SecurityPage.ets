import { AppIcon, DemoNote, IconTile, SectionHeader, SettingsRow, StatusBadge } from '../components/AppComponents';

@Component
export struct SecurityPage {
  @StorageLink('demoMfaEnabled') private mfaEnabled: boolean = true;
  @StorageLink('demoUnusualLogin') private unusualLoginEnabled: boolean = true;
  @StorageLink('demoSensitiveMail') private sensitiveMailEnabled: boolean = true;
  @State private pushEnabled: boolean = false;
  onOpen: (destination: string) => void = () => {};

  private protectionEnabled(kind: number): boolean {
    if (kind === 0) { return this.mfaEnabled; }
    if (kind === 1) { return this.unusualLoginEnabled; }
    if (kind === 2) { return this.sensitiveMailEnabled; }
    return this.pushEnabled;
  }

  @Builder
  private protectionRow(title: string, subtitle: string, icon: string, kind: number) {
    Row({ space: 12 }) {
      AppIcon({ name: icon, iconSize: 21 })
      Column({ space: 5 }) {
        Text(title).fontSize(15).fontColor($r('app.color.text_primary'))
        Text(subtitle).fontSize(12).fontColor($r('app.color.text_secondary')).lineHeight(18)
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Toggle({ type: ToggleType.Switch, isOn: this.protectionEnabled(kind) })
        .selectedColor($r('app.color.brand'))
        .onChange((value: boolean) => {
          if (kind === 0) {
            this.mfaEnabled = value;
          } else if (kind === 1) {
            this.unusualLoginEnabled = value;
          } else if (kind === 2) {
            this.sensitiveMailEnabled = value;
          } else {
            this.pushEnabled = value;
          }
        })
        .accessibilityText(title)
        .id('securityToggle-' + kind)
    }
    .width('100%')
    .constraintSize({ minHeight: 74 })
    .padding({ top: 10, bottom: 10 })
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 18 }) {
          Row({ space: 14 }) {
            IconTile({ name: 'security', tileSize: 58, color: $r('app.color.success'),
              tileBackground: $r('app.color.success_soft') })
            Column({ space: 6 }) {
              Text(this.mfaEnabled ? '安全，始终在你身边' : '再多一层安心')
                .fontSize(21).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
              Text(this.mfaEnabled ? '你的账号已开启多重保护' : '开启双重验证，让登录更安全')
                .fontSize(13).fontColor($r('app.color.text_secondary')).lineHeight(20)
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          }.width('100%')
          Row() {
            StatusBadge({ text: this.mfaEnabled ? '安全状态良好' : '建议开启双重验证', positive: this.mfaEnabled })
            Blank()
            Text('刚刚检查').fontSize(11).fontColor($r('app.color.text_tertiary'))
          }.width('100%')
        }
        .alignItems(HorizontalAlign.Start)
        .padding(22).width('100%').borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 8 }) {
          SectionHeader({ title: '验证方式', subtitle: '选择适合你的方式，安全又轻松' })
          Column() {
            SettingsRow({ title: '通行密钥', subtitle: '通过面容或指纹，轻松验证身份',
              value: '2 个', icon: 'key', onTap: () => this.onOpen('passkeys') })
            SettingsRow({ title: '验证器', subtitle: '使用一次性动态验证码',
              value: '已启用', icon: 'lock', last: true, onTap: () => this.onOpen('totp') })
          }.width('100%')
        }
        .alignItems(HorizontalAlign.Start)
        .padding(20).width('100%').borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 8 }) {
          SectionHeader({ title: '登录保护', subtitle: '为每一次登录多添一份安心' })
          Column() {
            this.protectionRow('双重验证', '登录时增加一次身份确认', 'security', 0)
            Divider().color($r('app.color.divider'))
            this.protectionRow('异地登录检测', '发现不常用的登录地点时提醒你', 'location', 1)
            Divider().color($r('app.color.divider'))
            this.protectionRow('敏感操作提醒', '账号重要信息变更后发送邮件', 'mail', 2)
          }.width('100%')
        }
        .alignItems(HorizontalAlign.Start)
        .padding(20).width('100%').borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 8 }) {
          SectionHeader({ title: '账号安全' })
          Column() {
            SettingsRow({ title: '绑定邮箱', value: 'linyuan@example.com', icon: 'mail',
              onTap: () => this.onOpen('email') })
            SettingsRow({ title: '登录密码', value: '已设置', icon: 'lock',
              onTap: () => this.onOpen('password') })
            SettingsRow({ title: '授权应用', subtitle: '查看和管理应用的访问权限', icon: 'home', last: true, onTap: () => this.onOpen('apps') })
          }.width('100%')
        }
        .alignItems(HorizontalAlign.Start)
        .padding(20).width('100%').borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 8 }) {
          SectionHeader({ title: '消息提醒' })
          this.protectionRow('安全消息推送', '及时了解账号的安全动态', 'bell', 3)
          Text('开关仅在本次演示中生效，不会申请系统通知权限。')
            .fontSize(11).fontColor($r('app.color.text_tertiary')).lineHeight(18)
        }
        .alignItems(HorizontalAlign.Start)
        .padding(20).width('100%').borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        DemoNote()
      }
      .width('100%')
      .constraintSize({ maxWidth: 680 })
      .padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }
    .width('100%').height('100%')
    .align(Alignment.Top)
    .scrollBar(BarState.Off)
    .backgroundColor($r('app.color.page_background'))
  }
}
