import { ConfigurationConstant } from '@kit.AbilityKit';
import { AppIcon, DemoNote, IconTile, SectionHeader, SettingsRow, StatusBadge } from '../components/AppComponents';
import { CredentialPreview } from './CredentialPreview';

export function previewTitle(destination: string): string {
  switch (destination) {
    case 'settings': return '关于与设置';
    case 'apps': return '授权应用';
    case 'scan': return '扫码授权';
    case 'passkeys': return '通行密钥';
    case 'totp': return '验证器';
    case 'email': return '绑定邮箱';
    case 'password': return '修改密码';
    case 'login': return '登录界面预览';
    case 'privacy': return '隐私与数据';
    default: return 'Ksuser 安全';
  }
}

interface DemoApplication {
  id: string;
  name: string;
  icon: string;
  description: string;
  date: string;
}

@Component
export struct PreviewPage {
  @Prop destination: string = '';
  onOpen: (destination: string) => void = () => {};
  @State private scanned: boolean = false;
  @State private approved: boolean = false;
  @StorageLink('demoName') private displayName: string = '林予安';
  @StorageLink('demoAppearance') private appearance: number = 0;
  @State private showApplication: boolean = false;
  @State private application: DemoApplication = {
    id: 'campus', name: '工大聚合', icon: 'home', description: '校园信息与服务', date: '2026 年 10 月 20 日'
  };
  @StorageLink('demoApplications') private applications: DemoApplication[] = [
    { id: 'campus', name: '工大聚合', icon: 'home', description: '校园信息与服务', date: '2026 年 10 月 20 日' },
    { id: 'cloud', name: 'Ksuser 云空间', icon: 'document', description: '文件与资料同步', date: '2026 年 10 月 18 日' },
    { id: 'demo', name: 'OAuth 示例应用', icon: 'globe', description: '开放平台登录示例', date: '2026 年 10 月 16 日' }
  ];

  private toast(message: string): void {
    try {
      this.getUIContext().getPromptAction().showToast({ message: message });
    } catch (_) {
      // Toasts are optional feedback in this offline preview.
    }
  }

  private changeAppearance(index: number): void {
    const modes: ConfigurationConstant.ColorMode[] = [
      ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET,
      ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT,
      ConfigurationConstant.ColorMode.COLOR_MODE_DARK
    ];
    try {
      this.getUIContext().getHostContext()?.getApplicationContext().setColorMode(modes[index]);
      this.appearance = index;
    } catch (_) {
      this.toast('暂时无法切换外观');
    }
  }

  private revokeApplication(): void {
    this.getUIContext().showAlertDialog({
      title: '取消对此应用的授权？',
      message: '演示操作：将从当前示范列表移除「' + this.application.name + '」。',
      primaryButton: { value: '保留授权', action: () => {} },
      secondaryButton: { value: '取消授权', fontColor: $r('app.color.danger'), action: () => {
        this.applications = this.applications.filter((item: DemoApplication) => item.id !== this.application.id);
        this.showApplication = false;
      } }
    });
  }

  @Builder
  private settings() {
    Column({ space: 14 }) {
      Image($r('app.media.brand_logo')).width(76).height(76).borderRadius(22)
        .accessibilityText('Ksuser 标志')
      Text('Ksuser 安全').fontSize(25).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      Text('让数字生活，多一份安心').fontSize(13).fontColor($r('app.color.text_secondary'))
      StatusBadge({ text: 'HarmonyOS · 界面预览', positive: false })
      Text('版本 1.0.0').fontSize(12).fontColor($r('app.color.text_tertiary'))
    }.width('100%').padding({ top: 20, bottom: 26 })

    Column({ space: 10 }) {
      SectionHeader({ title: '偏好设置' })
      Row({ space: 12 }) {
        AppIcon({ name: 'settings', iconSize: 21 })
        Text('外观').fontSize(15).fontColor($r('app.color.text_primary')).layoutWeight(1)
        Select([{ value: '跟随系统' }, { value: '浅色' }, { value: '深色' }])
          .selected(this.appearance).value(['跟随系统', '浅色', '深色'][this.appearance])
          .font({ size: 14 }).fontColor($r('app.color.text_secondary'))
          .backgroundColor($r('app.color.input_background'))
          .onSelect((index: number) => this.changeAppearance(index))
      }.width('100%').constraintSize({ minHeight: 60 })
      Divider().color($r('app.color.divider'))
      Row({ space: 12 }) {
        AppIcon({ name: 'globe', iconSize: 21 })
        Text('语言').fontSize(15).fontColor($r('app.color.text_primary')).layoutWeight(1)
        Text('简体中文').fontSize(14).fontColor($r('app.color.text_secondary'))
      }.width('100%').constraintSize({ minHeight: 52 })
    }.width('100%').padding(20).backgroundColor($r('app.color.card_background')).borderRadius(24)

    Column() {
      SettingsRow({ title: '隐私与数据', icon: 'security', onTap: () => this.onOpen('privacy') })
      SettingsRow({ title: '登录界面预览', icon: 'profile', subtitle: '体验账号登录的界面设计',
        last: true, onTap: () => this.onOpen('login') })
    }.width('100%').padding({ left: 20, right: 20, top: 4, bottom: 4 })
      .backgroundColor($r('app.color.card_background')).borderRadius(24)
    DemoNote({ text: '此版本专注于界面与交互演示\n后续将逐步接入账号、授权与设备安全功能' })
  }

  @Builder
  private applicationSheet() {
    Column({ space: 20 }) {
      IconTile({ name: this.application.icon, tileSize: 64 })
      Text(this.application.name).fontSize(24).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      Text(this.application.description).fontSize(14).fontColor($r('app.color.text_secondary'))
      Column({ space: 16 }) {
        SectionHeader({ title: '此应用可以访问' })
        Row({ space: 10 }) {
          AppIcon({ name: 'check', iconSize: 15, color: $r('app.color.success') })
          Text('基本资料：头像、昵称和账号标识').fontSize(14).fontColor($r('app.color.text_primary'))
        }.width('100%')
        Row({ space: 10 }) {
          AppIcon({ name: 'check', iconSize: 15, color: $r('app.color.success') })
          Text('已验证的邮箱地址').fontSize(14).fontColor($r('app.color.text_primary'))
        }.width('100%')
        Text('授权时间 · ' + this.application.date).fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start).width('100%').padding(20)
        .backgroundColor($r('app.color.input_background')).borderRadius(20)
      Button('取消授权').width('100%').height(48).fontColor($r('app.color.danger'))
        .backgroundColor($r('app.color.danger_soft')).onClick(() => this.revokeApplication())
      DemoNote({ text: '授权信息及取消操作仅用于本地演示' })
    }.width('100%').padding(24)
  }

  @Builder
  private apps() {
    SectionHeader({ title: '连接你的数字生活', subtitle: '你可以随时查看应用的权限，也可以取消授权。' })
    if (this.applications.length > 0) {
      Column() {
        ForEach(this.applications, (application: DemoApplication, index: number) => {
          SettingsRow({ title: application.name, subtitle: application.description, icon: application.icon,
            last: index === this.applications.length - 1,
            onTap: () => { this.application = application; this.showApplication = true; } })
        }, (application: DemoApplication) => application.id)
      }.width('100%').padding({ left: 20, right: 20, top: 8, bottom: 8 })
        .backgroundColor($r('app.color.card_background')).borderRadius(24)
    } else {
      Column({ space: 14 }) {
        IconTile({ name: 'key', tileSize: 56 })
        Text('还没有授权的应用').fontSize(17).fontColor($r('app.color.text_primary'))
        Text('使用 Ksuser 登录应用后，将显示在这里').fontSize(13).fontColor($r('app.color.text_secondary'))
      }.width('100%').padding(28).backgroundColor($r('app.color.card_background')).borderRadius(24)
    }
    DemoNote()
  }

  @Builder
  private scan() {
    if (!this.scanned) {
      Column({ space: 20 }) {
        Text('轻轻一扫，安心登录').fontSize(25).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        Text('扫描 Ksuser 登录页上的二维码\n在手机上确认，即可安全登录')
          .fontSize(14).fontColor($r('app.color.text_secondary')).textAlign(TextAlign.Center).lineHeight(24)
        Stack() {
          Column({ space: 22 }) {
            AppIcon({ name: 'scan', iconSize: 92 })
            Text('将二维码放入框内').fontSize(13).fontColor($r('app.color.text_secondary'))
          }.width('100%').height('100%').justifyContent(FlexAlign.Center)
          Divider().width('70%').color($r('app.color.brand')).strokeWidth(2).translate({ y: -10 })
        }.width(248).height(248).borderRadius(32)
          .backgroundColor($r('app.color.hero_background')).margin({ top: 18, bottom: 18 })
        Text('扫码界面示意').fontSize(12).fontColor($r('app.color.text_tertiary'))
        Button('体验扫码结果').height(50).width('100%').fontColor($r('app.color.on_brand'))
          .backgroundColor($r('app.color.brand')).onClick(() => { this.scanned = true; })
      }.width('100%').padding({ left: 12, right: 12, top: 26, bottom: 20 })
      DemoNote({ text: '当前为扫码流程预览，不会启用摄像头' })
    } else {
      Column({ space: 20 }) {
        IconTile({ name: this.approved ? 'check' : 'devices', tileSize: 78,
          color: this.approved ? $r('app.color.success') : $r('app.color.brand'),
          tileBackground: this.approved ? $r('app.color.success_soft') : $r('app.color.brand_soft') })
        Text(this.approved ? '已确认登录' : '确认在 Mac 上登录？').fontSize(24).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text(this.approved ? '演示流程已完成' : '请确认这是你本人发起的登录请求')
          .fontSize(14).fontColor($r('app.color.text_secondary'))
        Column({ space: 15 }) {
          this.infoLine('登录账号', this.displayName)
          this.infoLine('登录设备', 'MacBook Pro · Chrome')
          this.infoLine('登录位置', '中国 · 上海')
          this.infoLine('请求时间', '今天 09:41')
        }.width('100%').padding(20).backgroundColor($r('app.color.input_background')).borderRadius(20)
        if (!this.approved) {
          Button('确认登录').height(50).width('100%').fontColor($r('app.color.on_brand'))
            .backgroundColor($r('app.color.brand')).onClick(() => { this.approved = true; })
          Button('取消').height(48).width('100%').fontColor($r('app.color.text_secondary'))
            .backgroundColor($r('app.color.input_background')).onClick(() => { this.scanned = false; })
        } else {
          Button('再次体验').height(48).width('100%').fontColor($r('app.color.brand'))
            .backgroundColor($r('app.color.brand_soft')).onClick(() => { this.scanned = false; this.approved = false; })
        }
      }.width('100%').padding(24).backgroundColor($r('app.color.card_background')).borderRadius(28)
      DemoNote({ text: '此请求为硬编码示例，不会产生真实的登录授权' })
    }
  }

  @Builder
  private infoLine(title: string, value: string) {
    Row({ space: 12 }) {
      Text(title).fontSize(13).fontColor($r('app.color.text_secondary'))
      Text(value).fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1).textAlign(TextAlign.End)
    }.width('100%')
  }

  @Builder
  private privacy() {
    Column({ space: 20 }) {
      IconTile({ name: 'security', tileSize: 60 })
      SectionHeader({ title: '你的信息，由你掌握', subtitle: '当前版本使用本地示范资料，方便预览界面和交互。' })
      Text('资料编辑、安全开关、设备管理与应用授权均仅改变本次运行中的示范状态。关闭并重新启动应用后，示范数据将重置。')
        .fontSize(15).fontColor($r('app.color.text_secondary')).lineHeight(26)
      Divider().color($r('app.color.divider'))
      Text('登录、摄像头扫描、生物识别、验证码、推送和服务器数据同步将在后续版本接入。')
        .fontSize(15).fontColor($r('app.color.text_secondary')).lineHeight(26)
    }.width('100%').alignItems(HorizontalAlign.Start).padding(24)
      .backgroundColor($r('app.color.card_background')).borderRadius(24)
  }

  build() {
    Scroll() {
      Column({ space: 18 }) {
        if (this.destination === 'settings') {
          this.settings()
        } else if (this.destination === 'apps') {
          this.apps()
        } else if (this.destination === 'scan') {
          this.scan()
        } else if (this.destination === 'privacy') {
          this.privacy()
        } else {
          CredentialPreview({ kind: this.destination })
        }
      }.width('100%').constraintSize({ maxWidth: 680 })
        .padding({ left: 20, right: 20, top: 12, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.Top)
      .backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.showApplication, this.applicationSheet(), {
        height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background')
      })
  }
}
