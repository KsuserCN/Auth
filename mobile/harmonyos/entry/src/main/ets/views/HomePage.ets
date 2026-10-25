import { AppModel } from '../core/AppModel';
import { displayName, profileCompleteness } from '../core/Models';
import { ResourceFeedback } from '../components/AccountComponents';
import { ActionButton, AppIcon, EmptyState, IconTile, SectionHeader, SettingsRow, StatusBadge, UserAvatar } from '../components/AppComponents';

@Component
export struct HomePage {
  @ObjectLink model: AppModel;
  onNavigate: (index: number) => void = () => {};
  onOpen: (destination: string) => void = () => {};

  @State private refreshing: boolean = false;
  private async refresh(): Promise<void> {
    try { await this.model.refreshAll(); } finally { this.refreshing = false; }
  }

  private protectionEnabled(kind: number): boolean {
    const settings = this.model.profile?.settings;
    return kind === 0 ? settings?.mfaEnabled === true : kind === 1 ? settings?.detectUnusualLogin === true : settings?.notifySensitiveActionEmail === true;
  }

  @Builder
  private checkLine(title: string, kind: number) {
    Row({ space: 8 }) {
      Text(title).fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1)
      AppIcon({ name: this.protectionEnabled(kind) ? 'check' : 'info', iconSize: 12,
        color: this.protectionEnabled(kind) ? $r('app.color.success') : $r('app.color.text_tertiary') })
      Text(this.protectionEnabled(kind) ? '已开启' : '未开启').fontSize(12)
        .fontColor(this.protectionEnabled(kind) ? $r('app.color.success') : $r('app.color.text_secondary'))
    }.width('100%').padding({ top: 5, bottom: 5 })
  }

  @Builder
  private shortcut(title: string, icon: string, target: number) {
    Button({ type: ButtonType.Normal }) {
      Column({ space: 10 }) {
        IconTile({ name: icon, tileSize: 46 })
        Text(title).fontSize(12).fontColor($r('app.color.text_primary'))
      }.width('100%').padding({ top: 10, bottom: 10 })
    }.layoutWeight(1).backgroundColor(Color.Transparent).borderRadius(16)
      .onClick(() => {
        if (target === 0) { this.model.scan(); }
        else if (target === 1) { this.onNavigate(3); }
        else if (target === 2) { this.onOpen('apps'); }
        else { this.onNavigate(4); }
      })
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
    Scroll() {
      Column({ space: 16 }) {
        ResourceFeedback({ state: this.model.profileState, label: '概览资料', onRetry: () => this.model.refreshAll() })
        if (this.model.profile !== null) {
          Column({ space: 20 }) {
            Row({ space: 16 }) {
              UserAvatar({ url: this.model.profile?.avatarUrl || '', name: displayName(this.model.profile) })
              Column({ space: 7 }) {
                Text('欢迎回来').fontSize(13).fontColor($r('app.color.text_secondary'))
                Text(displayName(this.model.profile)).fontSize(26).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
                  .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                Text(this.model.profile?.email || '未绑定邮箱').fontSize(12).fontColor($r('app.color.text_secondary'))
              }.alignItems(HorizontalAlign.Start).layoutWeight(1)
            }.width('100%')
            Row() {
              StatusBadge({ text: this.protectionEnabled(0) ? '双重验证已开启' : '建议开启双重验证', positive: this.protectionEnabled(0) })
              Blank()
              Button('我的资料').fontSize(12).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
                .onClick(() => this.onNavigate(1))
            }.width('100%')
          }.width('100%').padding(22).borderRadius(26).backgroundColor($r('app.color.hero_background'))
          Column({ space: 14 }) {
            Row({ space: 12 }) {
              IconTile({ name: 'security', tileSize: 42 })
              SectionHeader({ title: '每一次登录，都安心', subtitle: '查看账号的真实保护状态' }).layoutWeight(1)
            }.width('100%')
            this.checkLine('双重验证', 0)
            this.checkLine('异地登录检测', 1)
            this.checkLine('敏感操作提醒', 2)
            ActionButton({ title: '管理账号安全', onTap: () => this.onNavigate(2) })
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          Column({ space: 10 }) {
            SectionHeader({ title: '常用服务' })
            Row({ space: 4 }) {
              this.shortcut('扫码授权', 'scan', 0)
              this.shortcut('登录设备', 'devices', 1)
              this.shortcut('授权应用', 'key', 2)
              this.shortcut('安全日志', 'logs', 3)
            }.width('100%')
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          Column({ space: 14 }) {
            Row() {
              SectionHeader({ title: '让大家更好地认识你', subtitle: '补充关于你的个人资料' }).layoutWeight(1)
              Text(profileCompleteness(this.model.profile) + '%').fontSize(24).fontWeight(FontWeight.Bold).fontColor($r('app.color.brand'))
            }.width('100%')
            Progress({ value: profileCompleteness(this.model.profile), total: 100, type: ProgressType.Linear }).width('100%')
              .color($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
            SettingsRow({ title: '完善个人资料', icon: 'edit', last: true, onTap: () => this.onNavigate(1) })
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          SettingsRow({ title: '查看最近安全活动', subtitle: '了解登录和账号变更记录', icon: 'logs', last: true,
            onTap: () => this.onNavigate(4) }).padding(20).backgroundColor($r('app.color.card_background')).borderRadius(24)
        } else {
          EmptyState({ title: '暂时无法读取资料', busy: this.model.busy, onRetry: () => this.model.refreshAll() })
        }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
    }.onRefreshing(() => this.refresh()).width('100%').height('100%').backgroundColor($r('app.color.page_background'))
  }
}
