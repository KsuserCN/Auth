import { AppIcon } from '../components/AppComponents';
import { HomePage } from '../views/HomePage';
import { ProfilePage } from '../views/ProfilePage';
import { SecurityPage } from '../views/SecurityPage';
import { SessionsPage, LogsPage } from '../views/ActivityPages';
import { PreviewPage, previewTitle } from '../views/PreviewPage';

@Entry
@Component
struct Index {
  @State selectedTab: number = 0;
  private paths: NavPathStack = new NavPathStack();
  private tabsController: TabsController = new TabsController();
  private titles: string[] = ['概览', '资料', '安全', '会话', '日志'];
  private subtitles: string[] = ['与你有关的安全，都在这里', '每一个细节，都是你', '为账号多一份安心', '随时了解账号的登录情况', '每一次操作，都有迹可循'];

  private switchTab(index: number): void {
    this.selectedTab = index;
    this.tabsController.changeIndex(index);
  }

  private open(destination: string): void {
    this.paths.pushPath({ name: destination });
  }

  @Builder
  destination(name: string) {
    NavDestination() {
      PreviewPage({ destination: name, onOpen: (route: string) => this.open(route) })
    }.title(previewTitle(name)).backgroundColor($r('app.color.page_background'))
  }

  @Builder
  tabItem(index: number, icon: string) {
    Column({ space: 5 }) {
      AppIcon({ name: icon, iconSize: 23,
        color: this.selectedTab === index ? $r('app.color.brand') : $r('app.color.text_tertiary') })
      Text(this.titles[index]).fontSize(10).fontWeight(this.selectedTab === index ? FontWeight.Bold : FontWeight.Regular)
        .fontColor(this.selectedTab === index ? $r('app.color.brand') : $r('app.color.text_secondary'))
    }.height(60).width('100%').justifyContent(FlexAlign.Center)
      .accessibilityText(this.titles[index]).accessibilitySelected(this.selectedTab === index)
  }

  @Builder
  header() {
    Row({ space: 12 }) {
      Column({ space: 6 }) {
        Text('KSUSER 安全').fontSize(11).fontWeight(FontWeight.Medium).fontColor($r('app.color.brand')).letterSpacing(1.2)
        Text(this.titles[this.selectedTab]).fontSize(30).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
        Text(this.subtitles[this.selectedTab]).fontSize(12).fontColor($r('app.color.text_secondary'))
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Button({ type: ButtonType.Circle }) {
        AppIcon({ name: 'scan', iconSize: 22, color: $r('app.color.text_primary') })
      }.width(44).height(44).backgroundColor($r('app.color.card_background'))
        .onClick(() => this.open('scan')).accessibilityText('扫码授权')
      Button({ type: ButtonType.Circle }) {
        AppIcon({ name: 'settings', iconSize: 22, color: $r('app.color.text_primary') })
      }.width(44).height(44).backgroundColor($r('app.color.card_background'))
        .onClick(() => this.open('settings')).accessibilityText('关于与设置')
    }.padding({ left: 24, right: 20, top: 16, bottom: 22 }).width('100%').constraintSize({ maxWidth: 680 })
  }

  build() {
    Navigation(this.paths) {
      Column() {
        this.header()
        Tabs({ barPosition: BarPosition.End, controller: this.tabsController, index: this.selectedTab }) {
          TabContent() {
            HomePage({ onNavigate: (index: number) => this.switchTab(index), onOpen: (route: string) => this.open(route) })
          }.tabBar(this.tabItem(0, 'home'))
          TabContent() {
            ProfilePage()
          }.tabBar(this.tabItem(1, 'profile'))
          TabContent() {
            SecurityPage({ onOpen: (route: string) => this.open(route) })
          }.tabBar(this.tabItem(2, 'security'))
          TabContent() {
            SessionsPage()
          }.tabBar(this.tabItem(3, 'devices'))
          TabContent() {
            LogsPage()
          }.tabBar(this.tabItem(4, 'logs'))
        }.layoutWeight(1).width('100%').barHeight(68).barMode(BarMode.Fixed)
          .barBackgroundColor($r('app.color.tab_background')).divider({ strokeWidth: 0.5, color: $r('app.color.divider') })
          .animationDuration(220).onChange((index: number) => { this.selectedTab = index; })
      }.width('100%').height('100%').backgroundColor($r('app.color.page_background'))
    }.hideTitleBar(true).navDestination(this.destination).mode(NavigationMode.Stack)
      .width('100%').height('100%').backgroundColor($r('app.color.page_background'))
  }
}
