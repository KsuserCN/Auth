import { AppIcon, DemoNote, IconTile, SectionHeader, StatusBadge } from '../components/AppComponents';

@Component
export struct HomePage {
  @StorageLink('demoName') private name: string = '林予安';
  @StorageLink('demoUsername') private username: string = 'linyuan';
  @StorageLink('demoMfaEnabled') private mfaEnabled: boolean = true;
  @StorageLink('demoUnusualLogin') private unusualLoginEnabled: boolean = true;
  @StorageLink('demoSensitiveMail') private sensitiveMailEnabled: boolean = true;
  onNavigate: (index: number) => void = () => {};
  onOpen: (destination: string) => void = () => {};

  private protectionEnabled(kind: number): boolean {
    return kind === 0 ? this.mfaEnabled : kind === 1 ? this.unusualLoginEnabled : this.sensitiveMailEnabled;
  }

  @Builder
  private protectionLine(title: string, kind: number) {
    Row({ space: 8 }) {
      Text(title).fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1)
      AppIcon({ name: this.protectionEnabled(kind) ? 'check' : 'info', iconSize: 12,
        color: this.protectionEnabled(kind) ? $r('app.color.success') : $r('app.color.brand') })
      Text(this.protectionEnabled(kind) ? '已开启' : '待开启').fontSize(12)
        .fontColor(this.protectionEnabled(kind) ? $r('app.color.success') : $r('app.color.brand'))
    }.width('100%').padding({ top: 5, bottom: 5 })
  }

  @Builder
  private shortcut(title: string, icon: string, destination: number) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 10 }) {
        IconTile({ name: icon, tileSize: 46 })
        Text(title).fontSize(12).fontColor($r('app.color.text_primary'))
      }.width('100%').padding({ top: 10, bottom: 10 })
    }
    .layoutWeight(1).backgroundColor(Color.Transparent).borderRadius(16)
    .onClick(() => {
      if (destination === 0) {
        this.onOpen('scan');
      } else if (destination === 1) {
        this.onNavigate(3);
      } else if (destination === 2) {
        this.onOpen('apps');
      } else {
        this.onNavigate(4);
      }
    })
    .accessibilityText(title)
  }

  @Builder
  private activityLine(title: string, subtitle: string, time: string, icon: string) {
    Button({ type: ButtonType.Normal }) {
      Row({ space: 12 }) {
        IconTile({ name: icon, tileSize: 38, color: $r('app.color.success'),
          tileBackground: $r('app.color.success_soft') })
        Column({ space: 5 }) {
          Text(title).fontSize(14).fontColor($r('app.color.text_primary'))
          Text(subtitle).fontSize(11).fontColor($r('app.color.text_secondary'))
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        Text(time).fontSize(11).fontColor($r('app.color.text_tertiary'))
      }.width('100%').padding({ top: 13, bottom: 13 })
    }.width('100%').backgroundColor(Color.Transparent).borderRadius(12)
    .onClick(() => this.onNavigate(4))
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 20 }) {
          Row({ space: 16 }) {
            Text(this.name.substring(0, 1)).fontSize(28).fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.brand')).width(68).height(68).textAlign(TextAlign.Center)
              .backgroundColor($r('app.color.card_background')).borderRadius(34)
            Column({ space: 7 }) {
              Text('欢迎回来').fontSize(13).fontColor($r('app.color.text_secondary'))
              Text(this.name).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis }).fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
              Text('@' + this.username).fontSize(12).fontColor($r('app.color.text_secondary'))
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          }.width('100%')
          Row() {
            StatusBadge({ text: this.mfaEnabled ? '双重验证已开启' : '建议开启双重验证', positive: this.mfaEnabled })
            Blank()
            Button({ type: ButtonType.Normal }) {
              Row({ space: 5 }) {
                Text('我的资料').fontSize(12).fontWeight(FontWeight.Medium).fontColor($r('app.color.brand'))
                AppIcon({ name: 'next', iconSize: 10 })
              }.height(40)
            }.backgroundColor(Color.Transparent).borderRadius(12).onClick(() => this.onNavigate(1))
          }.width('100%')
        }.alignItems(HorizontalAlign.Start).width('100%').padding(22).borderRadius(26)
        .backgroundColor($r('app.color.hero_background'))

        Column({ space: 13 }) {
          Row({ space: 12 }) {
            IconTile({ name: 'security', tileSize: 42 })
            Column({ space: 5 }) {
              Text('每一次登录，都安心').fontSize(18).fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.text_primary'))
              Text('你的账号，正在被妥善保护').fontSize(12).fontColor($r('app.color.text_secondary'))
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          }.width('100%')
          Column({ space: 5 }) {
            this.protectionLine('双重验证', 0)
            this.protectionLine('异地登录检测', 1)
            this.protectionLine('敏感操作提醒', 2)
          }.width('100%')
          Button('管理账号安全', { type: ButtonType.Capsule })
            .width('100%').height(44).fontSize(14).fontWeight(FontWeight.Medium)
            .backgroundColor($r('app.color.brand')).fontColor($r('app.color.on_brand'))
            .onClick(() => this.onNavigate(2))
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))

        Column({ space: 10 }) {
          SectionHeader({ title: '常用服务' })
          Row({ space: 4 }) {
            this.shortcut('扫码授权', 'scan', 0)
            this.shortcut('登录设备', 'devices', 1)
            this.shortcut('授权应用', 'home', 2)
            this.shortcut('安全日志', 'logs', 3)
          }.width('100%')
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))

        Button({ type: ButtonType.Normal }) {
          Column({ space: 14 }) {
            Row() {
              Column({ space: 6 }) {
                Text('让大家更好地认识你').fontSize(17).fontWeight(FontWeight.Bold)
                  .fontColor($r('app.color.text_primary'))
                Text('再补充一点，资料就更完整了').fontSize(12).fontColor($r('app.color.text_secondary'))
              }.alignItems(HorizontalAlign.Start).layoutWeight(1)
              Text('86%').fontSize(24).fontWeight(FontWeight.Bold).fontColor($r('app.color.brand'))
            }.width('100%')
            Progress({ value: 86, total: 100, type: ProgressType.Linear }).width('100%')
              .color($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
              .style({ strokeWidth: 5 })
            Row({ space: 5 }) {
              Text('完善个人资料').fontSize(12).fontWeight(FontWeight.Medium).fontColor($r('app.color.brand'))
              AppIcon({ name: 'arrow', iconSize: 12 })
            }.width('100%')
          }.width('100%').padding(20)
        }.width('100%').borderRadius(24).backgroundColor($r('app.color.card_background'))
        .onClick(() => this.onNavigate(1))

        Column({ space: 8 }) {
          SectionHeader({ title: '最近活动', subtitle: '一眼了解账号的安全动态' })
          Column() {
            this.activityLine('通行密钥登录', '上海 · 本机设备', '09:41', 'key')
            Divider().color($r('app.color.divider'))
            this.activityLine('更新个人资料', '上海 · 本机设备', '08:56', 'profile')
          }.width('100%')
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
        DemoNote()
      }.width('100%').constraintSize({ maxWidth: 680 })
      .padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').align(Alignment.Top).scrollBar(BarState.Off)
    .backgroundColor($r('app.color.page_background'))
  }
}
