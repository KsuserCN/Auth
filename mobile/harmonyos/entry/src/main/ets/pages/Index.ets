import { common } from '@kit.AbilityKit';
import { AppModel } from '../core/AppModel';
import { AppIcon, ActionButton } from '../components/AppComponents';
import { FeedbackBanner } from '../components/AccountComponents';
import { HomePage } from '../views/HomePage';
import { ProfilePage } from '../views/ProfilePage';
import { SecurityPage } from '../views/SecurityPage';
import { SessionsPage, LogsPage } from '../views/ActivityPages';
import { AuthenticationPage } from '../views/AuthenticationPage';
import { AccountPage, accountTitle } from '../views/AccountPage';
import { ChallengeSheet } from '../views/ChallengeSheet';

@Entry
@Component
struct Index {
  @State @Watch('accountChanged') model: AppModel = new AppModel();
  @State selectedTab: number = 0;
  private paths: NavPathStack = new NavPathStack();
  private loginPaths: NavPathStack = new NavPathStack();
  private tabsController: TabsController = new TabsController();
  private account: string = '';
  private titles: string[] = ['概览', '资料', '安全', '会话', '日志'];
  private subtitles: string[] = ['与你有关的安全，都在这里', '每一个细节，都是你', '为账号多一份安心', '随时了解账号的登录情况', '每一次操作，都有迹可循'];

  aboutToAppear(): void { this.initialize(); }
  private initialize(): void {
    const context = this.getUIContext().getHostContext() as common.UIAbilityContext;
    this.model.initialize(context);
  }
  private switchTab(index: number): void { this.selectedTab = index; this.tabsController.changeIndex(index); }
  private accountChanged(): void {
    const account = this.model.profile?.uuid || '';
    if (account !== this.account) {
      this.account = account; this.paths.clear(); this.loginPaths.clear(); this.selectedTab = 0; this.tabsController.changeIndex(0);
    }
  }
  private open(destination: string): void { this.openRoute(destination); }
  private async openRoute(destination: string): Promise<void> {
    const account = this.model.profile?.uuid;
    if (['email', 'password', 'totp', 'recovery', 'recovery-codes'].includes(destination)) {
      const result = await this.model.requireSensitive('确认身份：' + accountTitle(destination));
      if (result.status !== 'success' || account !== this.model.profile?.uuid) { return; }
    }
    this.paths.pushPath({ name: destination });
  }

  @Builder
  private destination(name: string) {
    NavDestination() {
      Column() {
        if (this.model.authenticated || name === 'settings') {
          Column() { FeedbackBanner({ model: this.model }) }.padding({ left: 20, right: 20 })
          AccountPage({ model: this.model, destination: name, onComplete: () => { this.paths.pop(); } }).layoutWeight(1)
        }
      }.width('100%').height('100%')
    }.title(accountTitle(name)).backgroundColor($r('app.color.page_background'))
  }

  @Builder
  private loginDestination(name: string) {
    NavDestination() { AccountPage({ model: this.model, destination: 'settings' }) }
      .title('关于与设置').backgroundColor($r('app.color.page_background'))
  }

  @Builder
  private tabItem(index: number, icon: string) {
    Column({ space: 5 }) {
      AppIcon({ name: icon, iconSize: 23, color: this.selectedTab === index ? $r('app.color.brand') : $r('app.color.text_tertiary') })
      Text(this.titles[index]).fontSize(10).fontWeight(this.selectedTab === index ? FontWeight.Bold : FontWeight.Regular)
        .fontColor(this.selectedTab === index ? $r('app.color.brand') : $r('app.color.text_secondary'))
    }.height(60).width('100%').justifyContent(FlexAlign.Center)
      .accessibilityText(this.titles[index]).accessibilitySelected(this.selectedTab === index)
  }

  @Builder
  private header() {
    Row({ space: 12 }) {
      Column({ space: 6 }) {
        Text('KSUSER 安全').fontSize(11).fontWeight(FontWeight.Medium).fontColor($r('app.color.brand')).letterSpacing(1.2)
        Text(this.titles[this.selectedTab]).fontSize(30).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        Text(this.subtitles[this.selectedTab]).fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Button({ type: ButtonType.Circle }) { AppIcon({ name: 'scan', iconSize: 22, color: $r('app.color.text_primary') }) }
        .width(44).height(44).backgroundColor($r('app.color.card_background')).enabled(!this.model.busy)
        .onClick(() => this.model.scan()).accessibilityText('扫码授权')
      Button({ type: ButtonType.Circle }) { AppIcon({ name: 'settings', iconSize: 22, color: $r('app.color.text_primary') }) }
        .width(44).height(44).backgroundColor($r('app.color.card_background'))
        .onClick(() => this.open('settings')).accessibilityText('关于与设置')
    }.padding({ left: 24, right: 20, top: 16, bottom: 22 }).width('100%').constraintSize({ maxWidth: 680 })
  }

  @Builder
  private shell() {
    Navigation(this.paths) {
      Column() {
        this.header()
        Column() { FeedbackBanner({ model: this.model }) }.padding({ left: 20, right: 20, bottom: 8 })
        Tabs({ barPosition: BarPosition.End, controller: this.tabsController, index: this.selectedTab }) {
          TabContent() { HomePage({ model: this.model, onNavigate: (index: number) => this.switchTab(index), onOpen: (route: string) => this.open(route) }) }.tabBar(this.tabItem(0, 'home'))
          TabContent() { ProfilePage({ model: this.model }) }.tabBar(this.tabItem(1, 'profile'))
          TabContent() { SecurityPage({ model: this.model, onOpen: (route: string) => this.open(route) }) }.tabBar(this.tabItem(2, 'security'))
          TabContent() { SessionsPage({ model: this.model }) }.tabBar(this.tabItem(3, 'devices'))
          TabContent() { LogsPage({ model: this.model }) }.tabBar(this.tabItem(4, 'logs'))
        }.layoutWeight(1).width('100%').barHeight(68).barMode(BarMode.Fixed).barBackgroundColor($r('app.color.tab_background'))
          .divider({ strokeWidth: 0.5, color: $r('app.color.divider') }).animationDuration(220)
          .onChange((index: number) => { this.selectedTab = index; })
      }.width('100%').height('100%')
    }.hideTitleBar(true).navDestination(this.destination).mode(NavigationMode.Stack)
      .width('100%').height('100%').backgroundColor($r('app.color.page_background'))
      .onAppear(() => { this.paths.clear(); this.selectedTab = 0; })
  }

  @Builder
  private authentication() {
    Navigation(this.loginPaths) {
      Column() {
        if (this.model.storageIssue) {
          Column({ space: 12 }) {
            FeedbackBanner({ model: this.model })
            ActionButton({ title: '重新读取本机登录状态', secondary: true, busy: this.model.busy, onTap: () => this.initialize() })
            if (this.model.storageIssue === 'corrupt') {
              ActionButton({ title: '清除损坏的本机登录数据', destructive: true, onTap: () => {
                this.getUIContext().showAlertDialog({ title: '清除本机登录数据？', message: '清除后可重新登录，不会删除服务器账号。',
                  primaryButton: { value: '取消', action: () => {} }, secondaryButton: { value: '清除并重新登录', action: () => this.model.resetLocalSession() } });
              } })
            }
          }.padding(20).width('100%')
        }
        AuthenticationPage({ model: this.model, onSettings: () => this.loginPaths.pushPath({ name: 'settings' }) }).layoutWeight(1)
      }.width('100%').height('100%')
    }.hideTitleBar(true).navDestination(this.loginDestination).mode(NavigationMode.Stack)
      .width('100%').height('100%').backgroundColor($r('app.color.page_background'))
      .onAppear(() => this.loginPaths.clear())
  }

  @Builder
  private challenge() { ChallengeSheet({ model: this.model }) }

  build() {
    Column() {
      if (!this.model.ready) {
        Column({ space: 18 }) {
          LoadingProgress().width(44).height(44).color($r('app.color.brand'))
          Text('正在恢复登录状态…').fontSize(15).fontColor($r('app.color.text_secondary'))
        }.width('100%').height('100%').justifyContent(FlexAlign.Center)
      } else if (this.model.authenticated) { this.shell() }
      else { this.authentication() }
    }.width('100%').height('100%').backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.model.challengeVisible, this.challenge(), {
        height: SheetSize.LARGE, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background'),
        onWillDismiss: (action: DismissSheetAction) => {
          if (!this.model.busy) { if (this.model.challengeKind) { this.model.cancelChallenge(); } action.dismiss(); }
        }
      })
  }
}
