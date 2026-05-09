export enum MainDestination {
  HOME = 'home',
  PROFILE = 'profile',
  SECURITY = 'security',
  SESSIONS = 'sessions',
  LOGS = 'logs',
}

export interface BottomBarDestination {
  route: MainDestination;
  label: string;
}

export const BOTTOM_BAR_DESTINATIONS: BottomBarDestination[] = [
  { route: MainDestination.HOME, label: '概览' },
  { route: MainDestination.PROFILE, label: '资料' },
  { route: MainDestination.SECURITY, label: '安全' },
  { route: MainDestination.SESSIONS, label: '会话' },
  { route: MainDestination.LOGS, label: '日志' },
];

const BRAND_PRIMARY: ResourceColor = '#FFB90F';
const BRAND_PRIMARY_CONTAINER: ResourceColor = '#FFFFE6A6';
const TEXT_SECONDARY: ResourceColor = '#FF5B5344';
const BAR_BACKGROUND: ResourceColor = '#FFFFFFFF';
const BAR_BORDER: ResourceColor = '#1FD2C7AF';
const SELECTION_ANIMATION: AnimateParam = {
  duration: 220,
  curve: Curve.Friction,
};

@Component
export struct KsuserBottomBar {
  @Prop selectedRoute: MainDestination = MainDestination.HOME;
  onDestinationSelected: (route: MainDestination) => void = () => {
  };

  build() {
    Row() {
      ForEach(BOTTOM_BAR_DESTINATIONS, (destination: BottomBarDestination) => {
        BottomBarItem({
          destination: destination,
          selected: this.selectedRoute === destination.route,
          onItemClick: () => {
            this.onDestinationSelected(destination.route);
          },
        })
      }, (destination: BottomBarDestination) => destination.route)
    }
    .width('100%')
    .height(72)
    .padding({ left: 6, right: 6, top: 6, bottom: 8 })
    .justifyContent(FlexAlign.SpaceBetween)
    .backgroundColor(BAR_BACKGROUND)
    .border({
      width: { top: 1 },
      color: BAR_BORDER,
    })
  }
}

@Component
struct BottomBarItem {
  destination: BottomBarDestination = { route: MainDestination.HOME, label: '概览' };
  @Prop selected: boolean = false;
  onItemClick: () => void = () => {
  };

  build() {
    Column({ space: 4 }) {
      Stack() {
        Row()
          .width(this.selected ? 50 : 28)
          .height(28)
          .borderRadius(16)
          .opacity(this.selected ? 1 : 0)
          .backgroundColor(BRAND_PRIMARY_CONTAINER)
          .scale({ x: this.selected ? 1 : 0.72, y: this.selected ? 1 : 0.72 })
          .animation(SELECTION_ANIMATION)

        this.DestinationIcon()
      }
      .width(56)
      .height(30)
      .alignContent(Alignment.Center)
      .scale({ x: this.selected ? 1.08 : 1, y: this.selected ? 1.08 : 1 })
      .animation(SELECTION_ANIMATION)

      Text(this.destination.label)
        .fontSize(11)
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
        .fontColor(this.selected ? BRAND_PRIMARY : TEXT_SECONDARY)
        .maxLines(1)
        .animation(SELECTION_ANIMATION)
    }
    .layoutWeight(1)
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .borderRadius(18)
    .backgroundColor(this.selected ? '#08FFB90F' : '#00FFFFFF')
    .scale({ x: this.selected ? 1.04 : 1, y: this.selected ? 1.04 : 1 })
    .animation(SELECTION_ANIMATION)
    .onClick(() => {
      this.onItemClick();
    })
  }

  @Builder
  DestinationIcon() {
    if (this.destination.route === MainDestination.HOME) {
      SymbolGlyph($r('sys.symbol.house'))
        .fontSize(23)
        .fontColor([this.selected ? BRAND_PRIMARY : TEXT_SECONDARY])
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
    } else if (this.destination.route === MainDestination.PROFILE) {
      SymbolGlyph($r('sys.symbol.doc_plaintext'))
        .fontSize(23)
        .fontColor([this.selected ? BRAND_PRIMARY : TEXT_SECONDARY])
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
    } else if (this.destination.route === MainDestination.SECURITY) {
      SymbolGlyph($r('sys.symbol.lock'))
        .fontSize(23)
        .fontColor([this.selected ? BRAND_PRIMARY : TEXT_SECONDARY])
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
    } else if (this.destination.route === MainDestination.SESSIONS) {
      SymbolGlyph($r('sys.symbol.matebook'))
        .fontSize(23)
        .fontColor([this.selected ? BRAND_PRIMARY : TEXT_SECONDARY])
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
    } else {
      SymbolGlyph($r('sys.symbol.eye'))
        .fontSize(23)
        .fontColor([this.selected ? BRAND_PRIMARY : TEXT_SECONDARY])
        .fontWeight(this.selected ? FontWeight.Medium : FontWeight.Regular)
    }
  }
}
