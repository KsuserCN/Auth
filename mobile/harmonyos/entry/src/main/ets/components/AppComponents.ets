// Shared native ArkUI primitives. Semantic resources follow the system color mode.
export function iconResource(name: string): Resource {
  switch (name) {
    case 'home': return $r('sys.symbol.square_grid_2x2');
    case 'profile': return $r('sys.symbol.person_crop_circle_fill');
    case 'security': return $r('sys.symbol.checkmark_shield');
    case 'devices': return $r('sys.symbol.matebook');
    case 'logs': return $r('sys.symbol.clock');
    case 'scan': return $r('sys.symbol.qrcode');
    case 'settings': return $r('sys.symbol.gearshape');
    case 'key': return $r('sys.symbol.key');
    case 'lock': return $r('sys.symbol.lock');
    case 'mail': return $r('sys.symbol.envelope');
    case 'bell': return $r('sys.symbol.bell');
    case 'location': return $r('sys.symbol.location_up');
    case 'phone': return $r('sys.symbol.phone_1');
    case 'check': return $r('sys.symbol.checkmark');
    case 'close': return $r('sys.symbol.xmark');
    case 'plus': return $r('sys.symbol.plus');
    case 'edit': return $r('sys.symbol.square_and_pencil');
    case 'info': return $r('sys.symbol.info_circle');
    case 'arrow': return $r('sys.symbol.arrow_up_right');
    case 'back': return $r('sys.symbol.chevron_left');
    case 'search': return $r('sys.symbol.magnifyingglass');
    case 'globe': return $r('sys.symbol.worldclock');
    case 'document': return $r('sys.symbol.doc_text');
    default: return $r('sys.symbol.chevron_right');
  }
}

@Component
export struct AppIcon {
  @Prop name: string = 'info';
  @Prop iconSize: number = 22;
  @Prop color: ResourceColor = $r('app.color.brand');

  build() {
    SymbolGlyph(iconResource(this.name))
      .fontSize(this.iconSize)
      .fontColor([this.color])
      .accessibilityLevel('no')
  }
}

@Component
export struct IconTile {
  @Prop name: string = 'security';
  @Prop tileSize: number = 44;
  @Prop color: ResourceColor = $r('app.color.brand');
  @Prop tileBackground: ResourceColor = $r('app.color.brand_soft');

  build() {
    Row() {
      AppIcon({ name: this.name, iconSize: this.tileSize * 0.48, color: this.color })
    }
    .justifyContent(FlexAlign.Center)
    .width(this.tileSize)
    .height(this.tileSize)
    .borderRadius(this.tileSize * 0.32)
    .backgroundColor(this.tileBackground)
  }
}

@Component
export struct SectionHeader {
  @Prop title: string = '';
  @Prop subtitle: string = '';

  build() {
    Column({ space: 5 }) {
      Text(this.title).fontSize(18).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
      if (this.subtitle.length > 0) {
        Text(this.subtitle).fontSize(13).fontColor($r('app.color.text_secondary')).lineHeight(20)
      }
    }.alignItems(HorizontalAlign.Start).width('100%')
  }
}

@Component
export struct StatusBadge {
  @Prop text: string = '';
  @Prop positive: boolean = true;

  build() {
    Row({ space: 4 }) {
      if (this.positive) {
        AppIcon({ name: 'check', iconSize: 10, color: $r('app.color.success') })
      }
      Text(this.text).fontSize(11).fontWeight(FontWeight.Medium)
        .fontColor(this.positive ? $r('app.color.success') : $r('app.color.brand'))
    }
    .padding({ left: 9, right: 9, top: 5, bottom: 5 })
    .borderRadius(20)
    .backgroundColor(this.positive ? $r('app.color.success_soft') : $r('app.color.brand_soft'))
  }
}

@Component
export struct SettingsRow {
  @Prop title: string = '';
  @Prop subtitle: string = '';
  @Prop value: string = '';
  @Prop icon: string = '';
  @Prop last: boolean = false;
  @Prop destructive: boolean = false;
  onTap: () => void = () => {};

  build() {
    Column() {
      Button({ type: ButtonType.Normal }) {
        Row({ space: 12 }) {
          if (this.icon.length > 0) {
            AppIcon({ name: this.icon, iconSize: 21,
              color: this.destructive ? $r('app.color.danger') : $r('app.color.brand') })
          }
          Column({ space: 4 }) {
            Text(this.title).fontSize(15)
              .fontColor(this.destructive ? $r('app.color.danger') : $r('app.color.text_primary'))
            if (this.subtitle.length > 0) {
              Text(this.subtitle).fontSize(12).fontColor($r('app.color.text_secondary')).lineHeight(18)
            }
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          if (this.value.length > 0) {
            Text(this.value).fontSize(13).fontColor($r('app.color.text_secondary'))
              .constraintSize({ maxWidth: '45%' }).maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          AppIcon({ name: 'next', iconSize: 13, color: $r('app.color.text_tertiary') })
        }.width('100%').constraintSize({ minHeight: 58 }).padding({ top: 9, bottom: 9 })
      }
      .backgroundColor(Color.Transparent).borderRadius(12).width('100%')
      .onClick(() => this.onTap())
      .accessibilityText(this.title + (this.value ? '，' + this.value : ''))
      if (!this.last) {
        Divider().color($r('app.color.divider'))
      }
    }.width('100%')
  }
}

@Component
export struct DemoNote {
  @Prop text: string = '当前为界面预览，所有内容均为示范数据';

  build() {
    Text(this.text).fontSize(11).fontColor($r('app.color.text_tertiary'))
      .textAlign(TextAlign.Center).lineHeight(18).width('100%').padding({ top: 6, bottom: 16 })
  }
}
