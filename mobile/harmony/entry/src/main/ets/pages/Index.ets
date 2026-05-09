import { BOTTOM_BAR_DESTINATIONS, BottomBarDestination, KsuserBottomBar, MainDestination } from '../components/KsuserBottomBar';
import { KsuserLoginPage } from '../components/KsuserLoginPage';

@Entry
@Component
struct Index {
  @State isAuthenticated: boolean = false;
  @State selectedRoute: MainDestination = MainDestination.HOME;

  build() {
    if (this.isAuthenticated) {
      Column() {
        this.MainContent()

        KsuserBottomBar({
          selectedRoute: this.selectedRoute,
          onDestinationSelected: (route: MainDestination) => {
            this.selectedRoute = route;
          },
        })
      }
      .height('100%')
      .width('100%')
      .backgroundColor('#FFF6F5F2')
    } else {
      KsuserLoginPage({
        loginSuccessHandler: () => {
          this.isAuthenticated = true;
          this.selectedRoute = MainDestination.HOME;
        },
      })
    }
  }

  @Builder
  MainContent() {
    Column({ space: 12 }) {
      Text(this.currentTitle())
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FF1F1B13')

      Text(this.currentDescription())
        .fontSize(15)
        .lineHeight(22)
        .fontColor('#FF5B5344')

      Row() {
        Text('当前底部栏已按 Android 端的入口顺序与文案实现，后续可把这里替换为对应业务页面。')
          .fontSize(13)
          .lineHeight(20)
          .fontColor('#FF5B5344')
      }
      .padding(14)
      .borderRadius(18)
      .backgroundColor('#FFFFF6E6')
    }
    .layoutWeight(1)
    .height('100%')
    .width('100%')
    .padding({ left: 24, right: 24, top: 42 })
    .justifyContent(FlexAlign.Start)
  }

  private currentTitle(): string {
    const destination = this.currentDestination();
    return destination.label;
  }

  private currentDescription(): string {
    switch (this.selectedRoute) {
      case MainDestination.HOME:
        return '查看账号安全概览、资料完整度与快捷入口。';
      case MainDestination.PROFILE:
        return '查看和编辑头像、昵称、地区、简介等账号资料。';
      case MainDestination.SECURITY:
        return '管理双重验证、Passkey、敏感操作保护和账号安全策略。';
      case MainDestination.SESSIONS:
        return '查看当前登录设备、会话状态，并处理异常登录。';
      case MainDestination.LOGS:
        return '查看安全相关操作记录与风险分数。';
      default:
        return '';
    }
  }

  private currentDestination(): BottomBarDestination {
    for (let index = 0; index < BOTTOM_BAR_DESTINATIONS.length; index++) {
      const destination = BOTTOM_BAR_DESTINATIONS[index];
      if (destination.route === this.selectedRoute) {
        return destination;
      }
    }
    return BOTTOM_BAR_DESTINATIONS[0];
  }
}
