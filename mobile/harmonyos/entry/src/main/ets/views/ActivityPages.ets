import { AppModel } from '../core/AppModel';
import { SessionItem, SensitiveLog, dateText, dateGroup } from '../core/Models';
import { LogFilters } from '../core/LogFilters';
import { LOG_OPERATIONS, operationTitle, logTitle, logMethods, logReason, logAction, logResult, riskTitle } from '../core/Presentation';
import { ActionButton, AppIcon, EmptyState, IconTile, InfoLine, SectionHeader, SettingsRow, StatusBadge } from '../components/AppComponents';
import { ResourceFeedback } from '../components/AccountComponents';

@Component
export struct SessionsPage {
  @ObjectLink @Watch('accountChanged') model: AppModel;
  @State private visible: boolean = false;
  @State private selected: SessionItem | null = null;
  @State private refreshing: boolean = false;
  @State private revoking: boolean = false;
  private account: string = '';
  private pendingRevoke: SessionItem | null = null;

  aboutToAppear(): void { this.account = this.model.profile?.uuid || ''; this.model.loadSessions(); }
  private accountChanged(): void {
    const account = this.model.profile?.uuid || '';
    if (account !== this.account) { this.account = account; this.visible = false; this.selected = null; this.pendingRevoke = null; }
  }
  aboutToDisappear(): void { this.pendingRevoke = null; }
  private icon(device: SessionItem): string { return /ios|android|harmony/i.test(device.deviceType || '') ? 'phone' : 'devices'; }
  private async refresh(): Promise<void> { try { await this.model.loadSessions(); } finally { this.refreshing = false; } }
  private revoke(): void {
    const selected = this.selected;
    if (!selected) { return; }
    this.getUIContext().showAlertDialog({ title: '撤销此登录会话？', message: selected.current ? '当前设备将退出登录。' : '此设备将需要重新登录。',
      primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '撤销会话', fontColor: $r('app.color.danger'), action: () => {
        // Finish dismissing the details sheet before a sensitive-verification sheet can open.
        this.pendingRevoke = selected; this.visible = false;
      } } });
  }
  private async detailsDismissed(): Promise<void> {
    const selected = this.pendingRevoke; this.pendingRevoke = null;
    if (!selected || !this.model.authenticated || this.revoking) { return; }
    const account = this.account; this.revoking = true;
    try {
      const result = await this.model.revokeSession(selected.id);
      if (result.status === 'success' && account === this.model.profile?.uuid) { this.selected = null; }
    } finally { this.revoking = false; }
  }
  private logoutAll(): void {
    this.getUIContext().showAlertDialog({ title: '退出所有设备？', message: '所有登录会话都将被撤销，包括当前设备。',
      primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '退出所有设备', fontColor: $r('app.color.danger'), action: () => this.model.logout(true) } });
  }
  @Builder
  private badges(device: SessionItem) {
    Row({ space: 8 }) {
      if (device.current) { StatusBadge({ text: '当前设备' }) }
      StatusBadge({ text: device.online ? '在线' : '离线', positive: device.online })
    }.width('100%')
  }
  @Builder
  private details() {
    Scroll() {
      Column({ space: 16 }) {
        if (this.selected) {
          IconTile({ name: this.icon(this.selected), tileSize: 60 })
          SectionHeader({ title: this.selected.deviceType || '未知设备', subtitle: this.selected.browser || '未知客户端' })
          this.badges(this.selected)
          Column() {
            InfoLine({ title: '位置', value: this.selected.ipLocation || '未知' })
            InfoLine({ title: 'IP 地址', value: this.selected.ipAddress })
            InfoLine({ title: '登录时间', value: dateText(this.selected.createdAt) })
            InfoLine({ title: '最近活跃', value: dateText(this.selected.lastSeenAt) })
            InfoLine({ title: '到期时间', value: dateText(this.selected.expiresAt) })
            InfoLine({ title: '客户端', value: this.selected.userAgent || '' })
          }.width('100%').padding(16).borderRadius(18).backgroundColor($r('app.color.input_background'))
          ActionButton({ title: this.selected.current ? '退出当前设备' : '撤销此会话', destructive: true, busy: this.revoking,
            disabled: this.model.busy, onTap: () => this.revoke() })
        }
      }.width('100%').padding(24)
    }.width('100%').height('100%').align(Alignment.TopStart)
  }
  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          SectionHeader({ title: '登录设备', subtitle: this.model.sessionsState.hasData ? this.model.sessions.length + ' 个会话，留意不认识的设备' : '查看账号正在使用的设备' })
          ResourceFeedback({ state: this.model.sessionsState, label: '登录设备', onRetry: () => this.model.loadSessions() })
          if (this.model.sessionsState.hasData && !this.model.sessions.length) {
            EmptyState({ title: '暂无登录会话', onRetry: () => this.model.loadSessions() })
          }
          ForEach(this.model.sessions, (device: SessionItem) => {
            Button({ type: ButtonType.Normal }) {
              Column({ space: 13 }) {
                Row({ space: 12 }) {
                  IconTile({ name: this.icon(device), tileSize: 46 })
                  Column({ space: 6 }) {
                    Text(device.deviceType || '未知设备').fontSize(17).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
                    Text(device.browser || '未知客户端').fontSize(12).fontColor($r('app.color.text_secondary'))
                  }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                  AppIcon({ name: 'next', iconSize: 13, color: $r('app.color.text_tertiary') })
                }.width('100%')
                this.badges(device)
                Text(device.ipLocation || device.ipAddress).fontSize(13).fontColor($r('app.color.text_secondary')).width('100%')
                Text('最近活跃 · ' + dateText(device.lastSeenAt)).fontSize(12).fontColor($r('app.color.text_tertiary')).width('100%')
              }.width('100%').padding(20)
            }.width('100%').borderRadius(22).backgroundColor($r('app.color.card_background'))
              .onClick(() => { this.selected = device; this.visible = true; })
          }, (device: SessionItem) => device.id.toString() + device.lastSeenAt)
          if (this.model.sessionsState.hasData) {
            ActionButton({ title: '退出所有设备', destructive: true, disabled: this.model.busy, onTap: () => this.logoutAll() })
          }
        }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
      }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
    }.width('100%').height('100%').onRefreshing(() => this.refresh()).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.details(), { height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background'), onDisappear: () => this.detailsDismissed() })
  }
}

@Component
export struct LogsPage {
  @ObjectLink @Watch('accountChanged') model: AppModel;
  @State private filters: LogFilters = new LogFilters();
  @State private query: string = '';
  @State private visible: boolean = false;
  @State private sheetKind: string = '';
  @State private selected: SensitiveLog | null = null;
  @State private refreshing: boolean = false;
  @State private picking: string = '';
  @State private filterError: string = '';
  private account: string = '';

  aboutToAppear(): void { this.account = this.model.profile?.uuid || ''; this.reload(1); }
  private accountChanged(): void {
    const account = this.model.profile?.uuid || '';
    if (account !== this.account) { this.account = account; this.filters.clear(); this.query = ''; this.visible = false; this.selected = null; }
  }
  private async reload(page: number): Promise<void> {
    const filter = this.filters.applied;
    await this.model.loadLogs(page, filter.result, filter.operation, filter.startDate, filter.endDate);
  }
  private async refresh(): Promise<void> { try { await this.reload(this.model.logsPage); } finally { this.refreshing = false; } }
  private rows(): SensitiveLog[] {
    const query = this.query.trim().toLowerCase();
    return this.model.logs.filter((log: SensitiveLog) => !query || [logTitle(log), log.deviceType || '', log.browser || '', log.ipLocation || '', log.ipAddress].join(' ').toLowerCase().includes(query));
  }
  private groups(): string[] {
    const groups: string[] = [];
    this.rows().forEach((log: SensitiveLog) => { const day = dateGroup(log.createdAt); if (!groups.includes(day)) { groups.push(day); } });
    return groups;
  }
  private groupRows(day: string): SensitiveLog[] { return this.rows().filter((log: SensitiveLog) => dateGroup(log.createdAt) === day); }
  private openFilters(): void { this.filters.begin(); this.filterError = ''; this.picking = ''; this.sheetKind = 'filter'; this.visible = true; }
  private apply(): void {
    try { this.filters.apply(); this.visible = false; this.reload(1); }
    catch (error) { this.filterError = (error as Error).message; }
  }
  private hasFilters(): boolean {
    const filter = this.filters.applied; return !!(filter.operation || filter.result || filter.startDate || filter.endDate);
  }
  private chooseDate(field: string): void {
    this.picking = field;
    if (!(field === 'startDate' ? this.filters.draft.startDate : this.filters.draft.endDate)) {
      const date = new Date();
      this.filters.update(field, date.getFullYear() + '-' + (date.getMonth() + 1).toString().padStart(2, '0') + '-' + date.getDate().toString().padStart(2, '0'));
    }
  }
  private selectedDate(): Date { return new Date((this.picking === 'startDate' ? this.filters.draft.startDate : this.filters.draft.endDate) + 'T12:00:00'); }
  @Builder
  private sheet() {
    Column() {
      Scroll() {
        Column({ space: 16 }) {
          if (this.sheetKind === 'filter') {
            SectionHeader({ title: '筛选安全活动', subtitle: '条件会应用于全部服务端记录' })
            Text('操作类型').fontSize(13).fontColor($r('app.color.text_secondary')).width('100%')
            Select(LOG_OPERATIONS.map((value: string): SelectOption => ({ value: operationTitle(value) })))
              .selected(Math.max(0, LOG_OPERATIONS.indexOf(this.filters.draft.operation))).value(operationTitle(this.filters.draft.operation))
              .onSelect((index: number) => this.filters.update('operation', LOG_OPERATIONS[index])).width('100%')
            Text('操作结果').fontSize(13).fontColor($r('app.color.text_secondary')).width('100%')
            Select([{ value: '全部结果' }, { value: '成功' }, { value: '失败' }]).selected(['', 'SUCCESS', 'FAILURE'].indexOf(this.filters.draft.result))
              .value(this.filters.draft.result === 'SUCCESS' ? '成功' : this.filters.draft.result === 'FAILURE' ? '失败' : '全部结果')
              .onSelect((index: number) => this.filters.update('result', ['', 'SUCCESS', 'FAILURE'][index])).width('100%')
            SettingsRow({ title: '开始日期', value: this.filters.draft.startDate || '不限', onTap: () => this.chooseDate('startDate') })
            SettingsRow({ title: '结束日期', value: this.filters.draft.endDate || '不限', last: true, onTap: () => this.chooseDate('endDate') })
            if (this.picking) {
              DatePicker({ start: new Date(2000, 0, 1), end: new Date(), selected: this.selectedDate() })
                .onDateChange((date: Date) => this.filters.update(this.picking, date.getFullYear() + '-' + (date.getMonth() + 1).toString().padStart(2, '0') + '-' + date.getDate().toString().padStart(2, '0'))).width('100%')
            }
            if (this.filterError) { Text(this.filterError).fontSize(13).fontColor($r('app.color.danger')) }
            Button('重置条件').height(44).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
              .onClick(() => { this.filters.resetDraft(); this.picking = ''; this.filterError = ''; })
          } else if (this.selected) {
            SectionHeader({ title: logTitle(this.selected), subtitle: logResult(this.selected) })
            InfoLine({ title: '时间', value: dateText(this.selected.createdAt) })
            InfoLine({ title: '验证方式', value: logMethods(this.selected) })
            InfoLine({ title: '浏览器', value: this.selected.browser || '未知' })
            InfoLine({ title: '设备', value: this.selected.deviceType || '未知' })
            InfoLine({ title: '位置', value: this.selected.ipLocation || '未知' })
            InfoLine({ title: 'IP 地址', value: this.selected.ipAddress })
            InfoLine({ title: '风险评分', value: riskTitle(this.selected.riskScore) + ' · ' + this.selected.riskScore })
            InfoLine({ title: '响应耗时', value: typeof this.selected.durationMs !== 'number' ? '暂无记录' : this.selected.durationMs + ' 毫秒' })
            if (this.selected.actionTaken) { InfoLine({ title: '安全措施', value: logAction(this.selected.actionTaken) }) }
            if (this.selected.failureReason) { InfoLine({ title: this.selected.result.toUpperCase() === 'SUCCESS' ? '说明' : '失败原因', value: logReason(this.selected.failureReason) }) }
            if (this.selected.triggeredMultiErrorLock) { Text('已触发连续错误保护').fontColor($r('app.color.brand')) }
            if (this.selected.triggeredRateLimitLock) { Text('已触发请求频率保护').fontColor($r('app.color.brand')) }
          }
        }.width('100%').padding(24)
      }.layoutWeight(1).width('100%').align(Alignment.TopStart)
      if (this.sheetKind === 'filter') {
        Row({ space: 12 }) {
          Button('取消').height(48).layoutWeight(1).fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
            .onClick(() => { this.filters.cancel(); this.visible = false; })
          Button('查看记录').height(48).layoutWeight(1).fontColor($r('app.color.on_brand')).backgroundColor($r('app.color.brand'))
            .onClick(() => this.apply())
        }.padding({ left: 24, right: 24, top: 12, bottom: 24 }).width('100%').backgroundColor($r('app.color.card_background'))
      }
    }.width('100%').height('100%')
  }
  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          Row() {
            SectionHeader({ title: '安全活动', subtitle: this.model.logsState.hasData ? '共 ' + this.model.logsTotal + ' 条记录' : '了解登录与账号变更记录' }).layoutWeight(1)
            Button('筛选').height(44).fontColor($r('app.color.brand')).backgroundColor($r('app.color.card_background')).onClick(() => this.openFilters())
          }.width('100%')
          Search({ value: this.query, placeholder: '搜索本页操作、设备或位置' }).height(48).fontColor($r('app.color.text_primary'))
            .backgroundColor($r('app.color.card_background')).onChange((value: string) => { this.query = value; })
          if (this.hasFilters()) {
            Column({ space: 6 }) {
              Text([this.filters.applied.operation ? operationTitle(this.filters.applied.operation) : '', this.filters.applied.result === 'SUCCESS' ? '成功' : this.filters.applied.result === 'FAILURE' ? '失败' : '',
                this.filters.applied.startDate ? '从 ' + this.filters.applied.startDate : '', this.filters.applied.endDate ? '至 ' + this.filters.applied.endDate : ''].filter((value: string) => !!value).join(' · '))
                .fontSize(12).fontColor($r('app.color.brand')).lineHeight(20)
              Button('清除筛选').height(44).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
                .onClick(() => { this.filters.clear(); this.reload(1); })
            }.width('100%').padding(12).borderRadius(16).backgroundColor($r('app.color.brand_soft'))
          }
          ResourceFeedback({ state: this.model.logsState, label: '安全日志', onRetry: () => this.reload(this.model.logsPage) })
          if (this.model.logsState.hasData && !this.rows().length) { EmptyState({ title: '暂无符合条件的记录', subtitle: '可调整筛选条件或搜索内容', onRetry: () => this.reload(1) }) }
          ForEach(this.groups(), (day: string) => {
            Column({ space: 8 }) {
              Text(day).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_secondary')).width('100%')
              Column() {
                ForEach(this.groupRows(day), (log: SensitiveLog) => {
                  Button({ type: ButtonType.Normal }) {
                    Row({ space: 12 }) {
                      IconTile({ name: 'logs', tileSize: 40 })
                      Column({ space: 6 }) {
                        Text(logTitle(log)).fontSize(15).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
                        Text(dateText(log.createdAt)).fontSize(12).fontColor($r('app.color.text_secondary'))
                        Text(log.ipLocation || log.ipAddress).fontSize(12).fontColor($r('app.color.text_secondary'))
                      }.layoutWeight(1).alignItems(HorizontalAlign.Start)
                      StatusBadge({ text: logResult(log), positive: log.result.toUpperCase() === 'SUCCESS' })
                    }.width('100%').padding({ top: 16, bottom: 16 })
                  }.width('100%').backgroundColor(Color.Transparent).onClick(() => { this.selected = log; this.sheetKind = 'detail'; this.visible = true; })
                  Divider().color($r('app.color.divider'))
                }, (log: SensitiveLog) => log.id.toString())
              }.width('100%').padding({ left: 16, right: 16 }).borderRadius(20).backgroundColor($r('app.color.card_background'))
            }.width('100%')
          }, (day: string) => day)
          if (this.model.logsState.hasData && this.model.logsTotalPages > 1) {
            Row() {
              Button('上一页').height(44).enabled(this.model.logsState.status !== 'loading' && this.model.logsPage > 1)
                .fontColor($r('app.color.brand')).backgroundColor(Color.Transparent).onClick(() => this.reload(this.model.logsPage - 1))
              Text(this.model.logsPage + ' / ' + this.model.logsTotalPages).fontSize(13).textAlign(TextAlign.Center).layoutWeight(1).fontColor($r('app.color.text_secondary'))
              Button('下一页').height(44).enabled(this.model.logsState.status !== 'loading' && this.model.logsPage < this.model.logsTotalPages)
                .fontColor($r('app.color.brand')).backgroundColor(Color.Transparent).onClick(() => this.reload(this.model.logsPage + 1))
            }.width('100%')
          }
        }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
      }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
    }.width('100%').height('100%').onRefreshing(() => this.refresh()).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.visible, this.sheet(), { height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background'), onDisappear: () => { if (this.sheetKind === 'filter') { this.filters.cancel(); } } })
  }
}
