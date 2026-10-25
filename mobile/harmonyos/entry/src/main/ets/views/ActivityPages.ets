import { AppIcon, DemoNote, IconTile, SectionHeader, StatusBadge } from '../components/AppComponents';

interface DemoSession {
  id: string;
  name: string;
  system: string;
  icon: string;
  location: string;
  lastActive: string;
  signedIn: string;
  ip: string;
  current: boolean;
  trusted: boolean;
}

const DEMO_SESSIONS: DemoSession[] = [
  { id: 'harmony', name: 'HUAWEI Mate 70', system: 'HarmonyOS · Ksuser 安全', icon: 'phone',
    location: '中国 · 上海', lastActive: '正在使用', signedIn: '2026-10-25 09:41',
    ip: '192.0.2.24', current: true, trusted: true },
  { id: 'mac', name: 'MacBook Pro', system: 'macOS · Chrome 141', icon: 'devices',
    location: '中国 · 上海', lastActive: '12 分钟前', signedIn: '2026-10-24 14:20',
    ip: '192.0.2.18', current: false, trusted: true },
  { id: 'safari', name: 'iPad Air', system: 'iPadOS · Safari', icon: 'globe',
    location: '中国 · 杭州', lastActive: '昨天 18:32', signedIn: '2026-10-24 18:32',
    ip: '198.51.100.12', current: false, trusted: false }
];

@Component
export struct SessionsPage {
  @State private sessions: DemoSession[] = DEMO_SESSIONS.slice();
  @State private showDetail: boolean = false;
  @State private selected: DemoSession = DEMO_SESSIONS[0];

  private removeSession(): void {
    this.getUIContext().showAlertDialog({
      title: '移除此设备？',
      message: '演示操作：此设备将从当前示范列表移除。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: {
        value: '移除设备', fontColor: $r('app.color.danger'),
        action: () => {
          this.sessions = this.sessions.filter((item: DemoSession) => item.id !== this.selected.id);
          this.showDetail = false;
        }
      }
    });
  }

  private removeOthers(): void {
    this.getUIContext().showAlertDialog({
      title: '退出其他设备？',
      message: '演示操作：保留当前设备，移除列表中的其他会话。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: {
        value: '退出其他设备', fontColor: $r('app.color.danger'),
        action: () => { this.sessions = this.sessions.filter((item: DemoSession) => item.current); }
      }
    });
  }

  @Builder
  private detailLine(title: string, value: string) {
    Row({ space: 12 }) {
      Text(title).fontSize(14).fontColor($r('app.color.text_secondary'))
      Text(value).fontSize(14).fontColor($r('app.color.text_primary'))
        .layoutWeight(1).textAlign(TextAlign.End)
    }.width('100%').padding({ top: 14, bottom: 14 })
  }

  @Builder
  private detailSheet() {
    Scroll() {
      Column({ space: 16 }) {
        IconTile({ name: this.selected.icon, tileSize: 64 })
        Text(this.selected.name).fontSize(24).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text(this.selected.system).fontSize(14).fontColor($r('app.color.text_secondary'))
        StatusBadge({ text: this.selected.current ? '当前设备' : (this.selected.trusted ? '受信任设备' : '普通会话'),
          positive: this.selected.current || this.selected.trusted })
        Column() {
          this.detailLine('登录位置', this.selected.location)
          Divider().color($r('app.color.divider'))
          this.detailLine('IP 地址', this.selected.ip)
          Divider().color($r('app.color.divider'))
          this.detailLine('登录时间', this.selected.signedIn)
          Divider().color($r('app.color.divider'))
          this.detailLine('最近活跃', this.selected.lastActive)
        }.width('100%').padding({ left: 16, right: 16 })
          .backgroundColor($r('app.color.input_background')).borderRadius(20)
        if (!this.selected.current) {
          Button('移除此设备', { type: ButtonType.Capsule })
            .width('100%').height(48).fontSize(15).fontColor($r('app.color.danger'))
            .backgroundColor($r('app.color.danger_soft')).onClick(() => this.removeSession())
        }
        DemoNote({ text: '设备信息及移除操作仅用于界面演示' })
      }.width('100%').padding(24).constraintSize({ maxWidth: 680 })
    }.width('100%').scrollBar(BarState.Off)
  }

  @Builder
  private deviceCard(device: DemoSession) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 18 }) {
        Row({ space: 13 }) {
          IconTile({ name: device.icon, tileSize: 48 })
          Column({ space: 5 }) {
            Text(device.name).fontSize(17).fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.text_primary'))
            Text(device.system).fontSize(12).fontColor($r('app.color.text_secondary'))
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          AppIcon({ name: 'next', iconSize: 14, color: $r('app.color.text_tertiary') })
        }.width('100%')
        Row({ space: 8 }) {
          AppIcon({ name: 'location', iconSize: 13, color: $r('app.color.text_secondary') })
          Text(device.location).fontSize(12).fontColor($r('app.color.text_secondary'))
          Blank()
          Text(device.lastActive).fontSize(12)
            .fontColor(device.current ? $r('app.color.success') : $r('app.color.text_secondary'))
        }.width('100%')
        Row() {
          StatusBadge({ text: device.current ? '当前设备' : (device.trusted ? '受信任设备' : '普通会话'),
            positive: device.current || device.trusted })
          Blank()
          Text('查看详情').fontSize(12).fontColor($r('app.color.brand'))
        }.width('100%')
      }.width('100%').padding(20)
    }.width('100%').borderRadius(24).backgroundColor($r('app.color.card_background'))
      .onClick(() => { this.selected = device; this.showDetail = true; })
      .accessibilityText(device.name + '，' + device.location + '，' + (device.current ? '当前设备' : '查看会话详情'))
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Row({ space: 14 }) {
          IconTile({ name: 'devices', tileSize: 48,
            color: $r('app.color.success'), tileBackground: $r('app.color.success_soft') })
          Column({ space: 5 }) {
            Text(this.sessions.length + ' 台设备已登录').fontSize(19).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text('每一次连接，都由你掌握').fontSize(13).fontColor($r('app.color.text_secondary'))
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
        }.width('100%').padding(20).backgroundColor($r('app.color.card_background')).borderRadius(24)
        SectionHeader({ title: '当前设备', subtitle: '你正在使用的登录会话' })
        ForEach(this.sessions.filter((item: DemoSession) => item.current), (device: DemoSession) => {
          this.deviceCard(device)
        }, (device: DemoSession) => device.id)
        SectionHeader({ title: '其他设备', subtitle: '如有不认识的设备，可随时移除' })
        if (this.sessions.length > 1) {
          ForEach(this.sessions.filter((item: DemoSession) => !item.current), (device: DemoSession) => {
            this.deviceCard(device)
          }, (device: DemoSession) => device.id)
          Button('退出其他设备', { type: ButtonType.Capsule })
            .height(48).width('100%').fontSize(15).fontColor($r('app.color.danger'))
            .backgroundColor($r('app.color.danger_soft')).onClick(() => this.removeOthers())
        } else {
          Column({ space: 12 }) {
            AppIcon({ name: 'check', iconSize: 30, color: $r('app.color.success') })
            Text('没有其他登录设备').fontSize(15).fontColor($r('app.color.text_primary'))
            Text('你的账号仅在当前设备上登录').fontSize(12).fontColor($r('app.color.text_secondary'))
          }.width('100%').padding(28).backgroundColor($r('app.color.card_background')).borderRadius(24)
        }
        DemoNote()
      }.width('100%').constraintSize({ maxWidth: 680 })
        .padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off)
      .bindSheet($$this.showDetail, this.detailSheet(), {
        height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background')
      })
  }
}

interface DemoLog {
  id: string;
  title: string;
  description: string;
  day: string;
  time: string;
  date: string;
  icon: string;
  success: boolean;
  device: string;
  location: string;
  ip: string;
}

const DEMO_LOGS: DemoLog[] = [
  { id: '1', title: '通行密钥登录', description: '使用通行密钥安全登录账号', day: '今天', time: '09:41', date: '2026-10-25',
    icon: 'key', success: true, device: 'HUAWEI Mate 70 · HarmonyOS', location: '中国 · 上海', ip: '192.0.2.24' },
  { id: '2', title: '扫码授权', description: '批准 MacBook Pro 的登录请求', day: '今天', time: '09:29', date: '2026-10-25',
    icon: 'scan', success: true, device: 'MacBook Pro · Chrome', location: '中国 · 上海', ip: '192.0.2.18' },
  { id: '3', title: '更新个人资料', description: '修改了账号的个人简介', day: '今天', time: '08:56', date: '2026-10-25',
    icon: 'profile', success: true, device: 'HUAWEI Mate 70 · HarmonyOS', location: '中国 · 上海', ip: '192.0.2.24' },
  { id: '4', title: '密码登录失败', description: '密码输入有误，登录未完成', day: '昨天', time: '20:18', date: '2026-10-24',
    icon: 'lock', success: false, device: 'Windows · Edge', location: '中国 · 上海', ip: '203.0.113.8' },
  { id: '5', title: '新设备登录', description: '已通过验证器完成双重验证', day: '昨天', time: '18:32', date: '2026-10-24',
    icon: 'devices', success: true, device: 'iPad Air · Safari', location: '中国 · 杭州', ip: '198.51.100.12' },
  { id: '6', title: '开启双重验证', description: '为账号增加一层安全保护', day: '昨天', time: '14:20', date: '2026-10-24',
    icon: 'security', success: true, device: 'MacBook Pro · Chrome', location: '中国 · 上海', ip: '192.0.2.18' }
];

@Component
export struct LogsPage {
  @State private query: string = '';
  @State private result: string = '全部';
  @State private showDetail: boolean = false;
  @State private selected: DemoLog = DEMO_LOGS[0];

  private filteredLogs(day: string = ''): DemoLog[] {
    const keyword: string = this.query.trim().toLowerCase();
    return DEMO_LOGS.filter((log: DemoLog) => {
      return (day.length === 0 || log.day === day) &&
        (this.result === '全部' || (this.result === '成功' ? log.success : !log.success)) &&
        (keyword.length === 0 || (log.title + log.description + log.device + log.location).toLowerCase().includes(keyword));
    });
  }

  @Builder
  private resultButton(title: string) {
    Button(title, { type: ButtonType.Capsule })
      .layoutWeight(1).height(38).fontSize(14)
      .fontWeight(this.result === title ? FontWeight.Medium : FontWeight.Normal)
      .fontColor(this.result === title ? $r('app.color.brand') : $r('app.color.text_secondary'))
      .backgroundColor(this.result === title ? $r('app.color.brand_soft') : $r('app.color.card_background'))
      .onClick(() => { this.result = title; })
      .accessibilityText(title + (this.result === title ? '，已选中' : ''))
  }

  @Builder
  private detailLine(title: string, value: string) {
    Column({ space: 6 }) {
      Text(title).fontSize(12).fontColor($r('app.color.text_secondary'))
      Text(value).fontSize(15).fontColor($r('app.color.text_primary')).lineHeight(23)
    }.width('100%').alignItems(HorizontalAlign.Start).padding({ top: 12, bottom: 12 })
  }

  @Builder
  private detailSheet() {
    Scroll() {
      Column({ space: 16 }) {
        IconTile({ name: this.selected.icon, tileSize: 60,
          color: this.selected.success ? $r('app.color.success') : $r('app.color.danger'),
          tileBackground: this.selected.success ? $r('app.color.success_soft') : $r('app.color.danger_soft') })
        Text(this.selected.title).fontSize(24).fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text(this.selected.description).fontSize(14).fontColor($r('app.color.text_secondary'))
          .textAlign(TextAlign.Center).lineHeight(22)
        Text(this.selected.success ? '操作成功' : '操作失败').fontSize(13).fontWeight(FontWeight.Medium)
          .fontColor(this.selected.success ? $r('app.color.success') : $r('app.color.danger'))
          .padding({ left: 12, right: 12, top: 6, bottom: 6 }).borderRadius(20)
          .backgroundColor(this.selected.success ? $r('app.color.success_soft') : $r('app.color.danger_soft'))
        Column() {
          this.detailLine('操作时间', this.selected.date + ' ' + this.selected.time)
          Divider().color($r('app.color.divider'))
          this.detailLine('设备与客户端', this.selected.device)
          Divider().color($r('app.color.divider'))
          this.detailLine('登录位置', this.selected.location)
          Divider().color($r('app.color.divider'))
          this.detailLine('IP 地址', this.selected.ip)
        }.width('100%').padding({ left: 20, right: 20 })
          .backgroundColor($r('app.color.input_background')).borderRadius(20)
        DemoNote({ text: '此记录为示范数据，并非真实账号活动' })
      }.width('100%').padding(24).constraintSize({ maxWidth: 680 })
    }.width('100%').scrollBar(BarState.Off)
  }

  @Builder
  private logRow(log: DemoLog, last: boolean) {
    Column() {
      Button({ type: ButtonType.Normal }) {
        Row({ space: 12 }) {
          IconTile({ name: log.icon, tileSize: 42,
            color: log.success ? $r('app.color.brand') : $r('app.color.danger'),
            tileBackground: log.success ? $r('app.color.brand_soft') : $r('app.color.danger_soft') })
          Column({ space: 6 }) {
            Text(log.title).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
            Text(log.location + ' · ' + log.time).fontSize(12).fontColor($r('app.color.text_secondary'))
          }.layoutWeight(1).alignItems(HorizontalAlign.Start)
          Column({ space: 7 }) {
            Text(log.success ? '成功' : '失败').fontSize(11)
              .fontColor(log.success ? $r('app.color.success') : $r('app.color.danger'))
            AppIcon({ name: 'next', iconSize: 12, color: $r('app.color.text_tertiary') })
          }
        }.width('100%').padding({ top: 17, bottom: 17 })
      }.width('100%').backgroundColor(Color.Transparent).borderRadius(12)
        .onClick(() => { this.selected = log; this.showDetail = true; })
        .accessibilityText(log.title + '，' + (log.success ? '成功' : '失败') + '，' + log.day + log.time)
      if (!last) {
        Divider().color($r('app.color.divider')).margin({ left: 54 })
      }
    }
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        SectionHeader({ title: '安全活动', subtitle: '留意每一次登录与账号操作' })
        Search({ value: this.query, placeholder: '搜索操作、设备或位置' })
          .height(48).fontColor($r('app.color.text_primary'))
          .placeholderColor($r('app.color.text_tertiary'))
          .backgroundColor($r('app.color.card_background'))
          .onChange((value: string) => { this.query = value; })
        Row({ space: 10 }) {
          this.resultButton('全部')
          this.resultButton('成功')
          this.resultButton('失败')
        }.width('100%')
        Text('共 ' + this.filteredLogs().length + ' 条记录').fontSize(12)
          .fontColor($r('app.color.text_secondary')).width('100%')
        if (this.filteredLogs().length === 0) {
          Column({ space: 14 }) {
            AppIcon({ name: 'search', iconSize: 36, color: $r('app.color.text_tertiary') })
            Text('暂无符合条件的记录').fontSize(16).fontColor($r('app.color.text_primary'))
            Text('试试其他关键词，或调整筛选条件').fontSize(12).fontColor($r('app.color.text_secondary'))
            Button('清除筛选').fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
              .onClick(() => { this.query = ''; this.result = '全部'; })
          }.width('100%').padding({ top: 40, bottom: 40 }).borderRadius(24)
            .backgroundColor($r('app.color.card_background'))
        }
        ForEach(['今天', '昨天'], (day: string) => {
          if (this.filteredLogs(day).length > 0) {
            Column({ space: 10 }) {
              Text(day).fontSize(13).fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_secondary')).width('100%')
              Column() {
                ForEach(this.filteredLogs(day), (log: DemoLog, index: number) => {
                  this.logRow(log, index === this.filteredLogs(day).length - 1)
                }, (log: DemoLog) => log.id)
              }.width('100%').padding({ left: 18, right: 18 })
                .backgroundColor($r('app.color.card_background')).borderRadius(24)
            }.width('100%').alignItems(HorizontalAlign.Start)
          }
        }, (day: string) => day)
        DemoNote()
      }.width('100%').constraintSize({ maxWidth: 680 })
        .padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off)
      .bindSheet($$this.showDetail, this.detailSheet(), {
        height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background')
      })
  }
}
