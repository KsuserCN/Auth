import { AppModel } from '../core/AppModel';
import { SessionItem, SensitiveLog } from '../core/Models';
import { logTitle, logMethods, logReason } from '../core/Presentation';
import { ActionButton, AppIcon, EmptyState, IconTile, InfoLine, SectionHeader, StatusBadge, readableDate } from '../components/AppComponents';
import { InputField } from '../components/AccountComponents';

@Component
export struct SessionsPage {
  @ObjectLink model: AppModel;
  @State private visible: boolean = false;
  @State private selected: SessionItem | null = null;

  aboutToAppear(): void { this.model.loadSessions(); }
  private icon(device: SessionItem): string { return /ios|android|harmony/i.test(device.deviceType || '') ? 'phone' : 'devices'; }
  private revoke(): void {
    const id = this.selected?.id;
    if (id === undefined) { return; }
    this.getUIContext().showAlertDialog({ title: '撤销此登录会话？', message: '此设备将需要重新登录。',
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '撤销会话', fontColor: $r('app.color.danger'), action: async () => {
        this.visible = false; await this.model.revokeSession(id);
      } }
    });
  }

  @Builder
  private details() {
    Scroll() {
      Column({ space: 16 }) {
        IconTile({ name: this.selected ? this.icon(this.selected) : 'devices', tileSize: 64 })
        Text(this.selected?.deviceType || '登录设备').fontSize(24).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        Text(this.selected?.browser || '').fontSize(14).fontColor($r('app.color.text_secondary'))
        StatusBadge({ text: this.selected?.current ? '当前设备' : this.selected?.online ? '在线' : '离线', positive: this.selected?.online === true })
        Column() {
          InfoLine({ title: '登录位置', value: this.selected?.ipLocation || '' })
          InfoLine({ title: 'IP 地址', value: this.selected?.ipAddress || '' })
          InfoLine({ title: '登录时间', value: readableDate(this.selected?.createdAt) })
          InfoLine({ title: '最近活跃', value: readableDate(this.selected?.lastSeenAt) })
          InfoLine({ title: '到期时间', value: readableDate(this.selected?.expiresAt) })
          InfoLine({ title: '客户端', value: this.selected?.userAgent || '' })
        }.width('100%').padding(16).backgroundColor($r('app.color.input_background')).borderRadius(20)
        if (!this.selected?.current) {
          ActionButton({ title: '撤销此会话', destructive: true, busy: this.model.busy, onTap: () => this.revoke() })
        }
      }.width('100%').padding(24)
    }.width('100%').height('100%').scrollBar(BarState.Off)
  }

  @Builder
  private card(device: SessionItem) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 16 }) {
        Row({ space: 13 }) {
          IconTile({ name: this.icon(device), tileSize: 48 })
          Column({ space: 6 }) {
            Text(device.deviceType || '未知设备').fontSize(17).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
            Text(device.browser || '未知客户端').fontSize(12).fontColor($r('app.color.text_secondary'))
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          AppIcon({ name: 'next', iconSize: 13, color: $r('app.color.text_tertiary') })
        }.width('100%')
        Row() {
          Text(device.ipLocation || device.ipAddress).fontSize(12).fontColor($r('app.color.text_secondary')).layoutWeight(1)
          StatusBadge({ text: device.current ? '当前设备' : device.online ? '在线' : '离线', positive: device.online })
        }.width('100%')
        Text('最近活跃 · ' + readableDate(device.lastSeenAt)).fontSize(12).fontColor($r('app.color.text_secondary')).width('100%')
      }.width('100%').padding(20)
    }.width('100%').borderRadius(24).backgroundColor($r('app.color.card_background'))
      .onClick(() => { this.selected = device; this.visible = true; })
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        SectionHeader({ title: this.model.sessions.length + ' 个登录会话', subtitle: '留意不认识的设备，及时撤销登录' })
        if (this.model.sessions.length === 0) {
          EmptyState({ title: '暂无登录会话', busy: this.model.busy, onRetry: () => this.model.loadSessions() })
        }
        ForEach(this.model.sessions, (device: SessionItem) => { this.card(device) }, (device: SessionItem) => device.id.toString() + ":" + device.lastSeenAt + ":" + device.online)
        ActionButton({ title: '刷新设备', secondary: true, busy: this.model.busy, onTap: () => this.model.loadSessions() })
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.details(), { height: SheetSize.LARGE, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background') })
  }
}

@Component
export struct LogsPage {
  @ObjectLink model: AppModel;
  @State private query: string = '';
  @State private result: string = '';
  @State private operation: string = '';
  @State private startDate: string = '';
  @State private endDate: string = '';
  @State private visible: boolean = false;
  @State private sheetKind: string = 'filter';
  @State private selected: SensitiveLog | null = null;
  private operations: string[] = ['', 'LOGIN', 'REGISTER', 'SENSITIVE_VERIFY', 'CHANGE_PASSWORD', 'CHANGE_EMAIL', 'UPDATE_PROFILE', 'ADD_PASSKEY', 'DELETE_PASSKEY', 'ENABLE_TOTP', 'DISABLE_TOTP'];
  private operationNames: SelectOption[] = [{ value: '全部操作' }, { value: '登录' }, { value: '注册' }, { value: '身份验证' },
    { value: '修改密码' }, { value: '修改邮箱' }, { value: '更新资料' }, { value: '添加通行密钥' }, { value: '删除通行密钥' },
    { value: '启用验证器' }, { value: '停用验证器' }];

  aboutToAppear(): void { this.reload(1); }
  private reload(page: number): void { this.model.loadLogs(page, this.result, this.operation, this.startDate, this.endDate); }
  private rows(): SensitiveLog[] {
    const q = this.query.trim().toLowerCase();
    return this.model.logs.filter((log: SensitiveLog) => !q || (logTitle(log) + log.ipLocation + log.ipAddress + log.deviceType + log.browser).toLowerCase().includes(q));
  }

  @Builder
  private resultButton(title: string, result: string) {
    Button(title).layoutWeight(1).height(38).fontSize(14)
      .fontColor(this.result === result ? $r('app.color.brand') : $r('app.color.text_secondary'))
      .backgroundColor(this.result === result ? $r('app.color.brand_soft') : $r('app.color.card_background'))
      .onClick(() => { this.result = result; this.reload(1); })
  }

  @Builder
  private sheet() {
    Scroll() {
      Column({ space: 18 }) {
        if (this.sheetKind === 'filter') {
          SectionHeader({ title: '筛选安全活动', subtitle: '筛选会应用于全部服务端记录' })
          Select(this.operationNames).selected(Math.max(0, this.operations.indexOf(this.operation)))
            .value(this.operationNames[Math.max(0, this.operations.indexOf(this.operation))].value)
            .onSelect((index: number) => { this.operation = this.operations[index]; }).width('100%')
          InputField({ title: '开始日期', placeholder: 'YYYY-MM-DD', text: this.startDate,
            onChange: (value: string) => { this.startDate = value; } })
          InputField({ title: '结束日期', placeholder: 'YYYY-MM-DD', text: this.endDate,
            onChange: (value: string) => { this.endDate = value; } })
          ActionButton({ title: '应用筛选', busy: this.model.busy, onTap: () => { this.reload(1); this.visible = false; } })
          ActionButton({ title: '清除筛选', secondary: true, onTap: () => {
            this.operation = ''; this.startDate = ''; this.endDate = ''; this.result = ''; this.query = ''; this.reload(1); this.visible = false;
          } })
        } else if (this.selected !== null) {
          IconTile({ name: 'logs', tileSize: 60 })
          SectionHeader({ title: logTitle(this.selected), subtitle: this.selected.result.toUpperCase() === 'SUCCESS' ? '操作成功' : '操作失败' })
          Column() {
            InfoLine({ title: '时间', value: readableDate(this.selected.createdAt) })
            InfoLine({ title: '验证方式', value: logMethods(this.selected) })
            InfoLine({ title: '客户端', value: this.selected.browser || '' })
            InfoLine({ title: '设备', value: this.selected.deviceType || '' })
            InfoLine({ title: '位置', value: this.selected.ipLocation || '' })
            InfoLine({ title: 'IP 地址', value: this.selected.ipAddress })
            InfoLine({ title: '风险评分', value: this.selected.riskScore.toString() })
            if (this.selected.failureReason) { InfoLine({ title: '失败原因', value: logReason(this.selected.failureReason) }) }
          }.width('100%').padding(16).borderRadius(20).backgroundColor($r('app.color.input_background'))
        }
      }.width('100%').padding(24)
    }.width('100%').height('100%').scrollBar(BarState.Off)
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Row() {
          SectionHeader({ title: '安全活动', subtitle: '共 ' + this.model.logsTotal + ' 条记录' }).layoutWeight(1)
          Button('筛选').fontColor($r('app.color.brand')).backgroundColor($r('app.color.card_background'))
            .onClick(() => { this.sheetKind = 'filter'; this.visible = true; })
        }.width('100%')
        Search({ value: this.query, placeholder: '搜索本页操作、设备或位置' }).height(48)
          .fontColor($r('app.color.text_primary')).backgroundColor($r('app.color.card_background'))
          .onChange((value: string) => { this.query = value; })
        Row({ space: 10 }) { this.resultButton('全部', ''); this.resultButton('成功', 'SUCCESS'); this.resultButton('失败', 'FAILURE'); }.width('100%')
        if (this.rows().length === 0) {
          EmptyState({ title: '暂无符合条件的记录', subtitle: '调整筛选条件或刷新试试', busy: this.model.busy, onRetry: () => this.reload(1) })
        }
        Column() {
          ForEach(this.rows(), (log: SensitiveLog) => {
            Button({ type: ButtonType.Normal }) {
              Row({ space: 12 }) {
                IconTile({ name: 'logs', tileSize: 42 })
                Column({ space: 6 }) {
                  Text(logTitle(log)).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
                  Text(readableDate(log.createdAt)).fontSize(12).fontColor($r('app.color.text_secondary'))
                  Text(log.ipLocation || log.ipAddress).fontSize(12).fontColor($r('app.color.text_secondary'))
                }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                Text(log.result.toUpperCase() === 'SUCCESS' ? '成功' : '失败').fontSize(11)
                  .fontColor(log.result.toUpperCase() === 'SUCCESS' ? $r('app.color.success') : $r('app.color.danger'))
              }.width('100%').padding({ top: 17, bottom: 17 })
            }.width('100%').backgroundColor(Color.Transparent).borderRadius(12)
              .onClick(() => { this.selected = log; this.sheetKind = 'detail'; this.visible = true; })
            Divider().color($r('app.color.divider'))
          }, (log: SensitiveLog) => log.id.toString())
        }.width('100%').padding({ left: 18, right: 18 }).backgroundColor($r('app.color.card_background')).borderRadius(24)
        Row() {
          Button('上一页').enabled(!this.model.busy && this.model.logsPage > 1).fontColor($r('app.color.brand'))
            .backgroundColor(Color.Transparent).onClick(() => this.reload(this.model.logsPage - 1))
          Text(this.model.logsPage + ' / ' + Math.max(1, this.model.logsTotalPages)).fontSize(13).fontColor($r('app.color.text_secondary')).layoutWeight(1).textAlign(TextAlign.Center)
          Button('下一页').enabled(!this.model.busy && this.model.logsPage < this.model.logsTotalPages).fontColor($r('app.color.brand'))
            .backgroundColor(Color.Transparent).onClick(() => this.reload(this.model.logsPage + 1))
        }.width('100%')
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.sheet(), { height: SheetSize.LARGE, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background') })
  }
}
