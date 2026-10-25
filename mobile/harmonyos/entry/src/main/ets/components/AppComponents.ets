// Shared native ArkUI primitives. Semantic resources follow the system color mode.
import { dateText } from '../core/Models';
import { scopeTitle as presentedScopeTitle } from '../core/Presentation';
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
    case 'sun': return $r('sys.symbol.sun_max_fill');
    case 'moon': return $r('sys.symbol.moon_fill');
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
export struct ActionButton {
  @Prop title: string = '';
  @Prop busy: boolean = false;
  @Prop disabled: boolean = false;
  @Prop secondary: boolean = false;
  @Prop destructive: boolean = false;
  onTap: () => void = () => {};

  build() {
    Button({ type: ButtonType.Capsule }) {
      Row({ space: 10 }) {
        if (this.busy) {
          LoadingProgress().width(20).height(20)
            .color(this.secondary ? $r('app.color.brand') : $r('app.color.on_brand'))
        }
        Text(this.title).fontSize(15).fontWeight(FontWeight.Medium).layoutWeight(1).textAlign(TextAlign.Center)
          .fontColor(this.destructive ? $r('app.color.danger') :
            this.secondary ? $r('app.color.brand') : $r('app.color.on_brand'))
      }.width('100%').justifyContent(FlexAlign.Center)
    }.width('100%').constraintSize({ minHeight: 48 }).padding({ top: 12, bottom: 12 })
      .backgroundColor(this.destructive ? $r('app.color.danger_soft') :
        this.secondary ? $r('app.color.brand_soft') : $r('app.color.brand'))
      .enabled(!this.busy && !this.disabled).onClick(() => this.onTap())
  }
}

@Component
export struct EmptyState {
  @Prop title: string = '暂无内容';
  @Prop subtitle: string = '';
  @Prop icon: string = 'info';
  @Prop busy: boolean = false;
  onRetry: () => void = () => {};

  build() {
    Column({ space: 14 }) {
      if (this.busy) { LoadingProgress().width(36).height(36).color($r('app.color.brand')) }
      else { IconTile({ name: this.icon, tileSize: 56 }) }
      Text(this.busy ? '正在读取…' : this.title).fontSize(16).fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
      if (this.subtitle.length > 0) {
        Text(this.subtitle).fontSize(13).fontColor($r('app.color.text_secondary'))
          .lineHeight(21).textAlign(TextAlign.Center)
      }
      Button('刷新').fontSize(13).fontColor($r('app.color.brand'))
        .backgroundColor($r('app.color.brand_soft')).enabled(!this.busy).onClick(() => this.onRetry())
    }.padding(28).width('100%').borderRadius(24).backgroundColor($r('app.color.card_background'))
  }
}

@Component
export struct InfoLine {
  @Prop title: string = '';
  @Prop value: string = '';

  build() {
    Row({ space: 16 }) {
      Text(this.title).fontSize(13).fontColor($r('app.color.text_secondary'))
      Text(this.value || '—').fontSize(14).fontColor($r('app.color.text_primary'))
        .lineHeight(22).layoutWeight(1).textAlign(TextAlign.End).copyOption(CopyOptions.LocalDevice)
    }.width('100%').padding({ top: 10, bottom: 10 }).alignItems(VerticalAlign.Top)
  }
}

@Component
export struct UserAvatar {
  @Prop @Watch('resetImage') url: string = '';
  @Prop name: string = '';
  @Prop avatarSize: number = 68;
  @State private failed: boolean = false;
  private resetImage(): void { this.failed = false; }

  build() {
    if (this.url.length > 0 && !this.failed) {
      Image(this.url).width(this.avatarSize).height(this.avatarSize).objectFit(ImageFit.Cover)
        .borderRadius(this.avatarSize / 2).alt($r('app.media.brand_logo')).onError(() => { this.failed = true; })
    } else {
      Text((this.name || 'K').substring(0, 1)).fontSize(this.avatarSize * 0.42).fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.brand')).width(this.avatarSize).height(this.avatarSize)
        .textAlign(TextAlign.Center).borderRadius(this.avatarSize / 2).backgroundColor($r('app.color.brand_soft'))
    }
  }
}

@Component
export struct ApplicationIcon {
  @Prop @Watch('resetImage') url: string = '';
  @Prop name: string = '';
  @Prop iconSize: number = 48;
  @State private failed: boolean = false;
  private resetImage(): void { this.failed = false; }
  build() {
    if (this.url && !this.failed) {
      Image(this.url).width(this.iconSize).height(this.iconSize).objectFit(ImageFit.Cover).borderRadius(12)
        .onError(() => { this.failed = true; }).accessibilityText(this.name + '图标')
    } else {
      Text((this.name || 'K').slice(0, 1)).width(this.iconSize).height(this.iconSize).fontSize(this.iconSize * 0.42)
        .fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft')).borderRadius(12).textAlign(TextAlign.Center)
    }
  }
}

export function readableDate(value: string | undefined): string {
  return dateText(value);
}

export function methodTitle(value: string): string {
  switch (value) {
    case 'password': return '密码';
    case 'email-code': return '邮箱验证码';
    case 'totp': return '验证器';
    case 'passkey': return '通行密钥';
    default: return value;
  }
}

export function scopeTitle(value: string): string {
  return presentedScopeTitle(value);
}
