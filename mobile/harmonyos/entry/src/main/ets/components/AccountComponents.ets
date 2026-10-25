import { AppModel } from '../core/AppModel';
import { AppIcon } from './AppComponents';
import { ResourceState, validEmail } from '../core/Models';

@Component
export struct FeedbackBanner {
  @ObjectLink model: AppModel;

  build() {
    if (this.model.error || this.model.notice || this.model.offline) {
      Row({ space: 10 }) {
        AppIcon({ name: this.model.error ? 'info' : 'check', iconSize: 18,
          color: this.model.error ? $r('app.color.danger') : $r('app.color.brand') })
        Text(this.model.error || this.model.notice || '服务暂时不可用，已保留当前登录状态')
          .fontSize(13).fontColor(this.model.error ? $r('app.color.danger') : $r('app.color.text_primary'))
          .lineHeight(20).layoutWeight(1)
        if (this.model.offline) {
          Button('重试').height(44).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
            .enabled(!this.model.busy).onClick(() => this.model.refreshAll())
        }
        Button({ type: ButtonType.Circle }) {
          AppIcon({ name: 'close', iconSize: 12, color: $r('app.color.text_secondary') })
        }.width(32).height(32).backgroundColor(Color.Transparent).onClick(() => this.model.clearFeedback())
      }.width('100%').padding(14).borderRadius(16)
        .backgroundColor(this.model.error ? $r('app.color.danger_soft') : $r('app.color.brand_soft'))
    }
  }
}

@Component
export struct InputField {
  @Prop title: string = '';
  @Prop text: string = '';
  @Prop placeholder: string = '';
  @Prop secure: boolean = false;
  @Prop numeric: boolean = false;
  @Prop email: boolean = false;
  @Prop multiline: boolean = false;
  @Prop maxLength: number = 128;
  onChange: (value: string) => void = () => {};

  build() {
    Column({ space: 9 }) {
      Text(this.title).fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_secondary'))
      if (this.multiline) {
        TextArea({ placeholder: this.placeholder, text: this.text }).height(140).maxLength(this.maxLength)
          .fontSize(16).fontColor($r('app.color.text_primary')).backgroundColor($r('app.color.input_background'))
          .borderRadius(16).padding(16).onChange((value: string) => this.onChange(value)).accessibilityText(this.title)
      } else {
        TextInput({ placeholder: this.placeholder, text: this.text }).height(54).maxLength(this.maxLength)
          .type(this.secure ? InputType.Password : this.email ? InputType.Email : this.numeric ? InputType.Number : InputType.Normal)
          .showPasswordIcon(this.secure)
          .fontSize(16).fontColor($r('app.color.text_primary')).backgroundColor($r('app.color.input_background'))
          .borderRadius(16).padding({ left: 16, right: 16 })
          .onChange((value: string) => this.onChange(value)).accessibilityText(this.title)
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}

@Component
export struct VerificationCodeField {
  @ObjectLink @Watch('sentChanged') model: AppModel;
  @Prop @Watch('emailChanged') email: string = '';
  @Prop @Watch('emailChanged') type: string = 'login';
  @Prop code: string = '';
  @Prop allowed: boolean = true;
  @State private seconds: number = 0;
  private timer: number = -1;
  private seenSentAt: number = 0;
  onChange: (value: string) => void = () => {};

  aboutToDisappear(): void {
    if (this.timer !== -1) { clearInterval(this.timer); this.timer = -1; }
  }

  aboutToAppear(): void { this.sentChanged(); }
  private emailChanged(): void {
    if (this.timer !== -1) { clearInterval(this.timer); this.timer = -1; }
    this.seconds = 0; this.seenSentAt = 0; this.sentChanged();
  }
  private sentChanged(): void {
    if (this.model.codeSentAt <= this.seenSentAt || this.model.codeSentType !== this.type ||
      this.model.codeSentEmail !== this.email.trim().toLowerCase()) { return; }
    this.seenSentAt = this.model.codeSentAt;
    this.seconds = Math.max(0, 60 - Math.floor((Date.now() - this.seenSentAt) / 1000));
    if (this.timer !== -1) { clearInterval(this.timer); }
    if (this.seconds > 0) {
      this.timer = setInterval(() => {
        this.seconds = Math.max(0, 60 - Math.floor((Date.now() - this.seenSentAt) / 1000));
        if (this.seconds === 0) { clearInterval(this.timer); this.timer = -1; }
      }, 1000);
    }
  }
  private async send(): Promise<void> { await this.model.sendCode(this.email, this.type); this.sentChanged(); }

  build() {
    Column({ space: 9 }) {
      Text('邮箱验证码').fontSize(13).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_secondary'))
      Row({ space: 10 }) {
        TextInput({ placeholder: '6 位验证码', text: this.code }).height(54).maxLength(6)
          .type(InputType.Number).layoutWeight(1).fontColor($r('app.color.text_primary'))
          .backgroundColor($r('app.color.input_background')).borderRadius(16)
          .onChange((value: string) => this.onChange(value)).accessibilityText('邮箱验证码')
        Button(this.seconds > 0 ? this.seconds + ' 秒' : '发送验证码')
          .height(44).fontSize(13).fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
          .enabled(this.allowed && !this.model.busy && this.seconds === 0 &&
            (this.type === 'sensitive-verification' || validEmail(this.email))).onClick(() => this.send())
      }.width('100%')
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}

@Component
export struct ResourceFeedback {
  @Prop state: ResourceState = { status: 'idle', error: '', hasData: false };
  @Prop label: string = '内容';
  onRetry: () => void = () => {};

  build() {
    if (this.state.status === 'loading' && !this.state.hasData) {
      Row({ space: 12 }) {
        LoadingProgress().width(24).height(24).color($r('app.color.brand'))
        Text('正在读取' + this.label + '…').fontSize(14).fontColor($r('app.color.text_secondary'))
      }.width('100%').padding(20).justifyContent(FlexAlign.Center)
    } else if (this.state.status === 'error') {
      Column({ space: 10 }) {
        Text(this.state.hasData ? '刷新失败，正在显示上次读取的内容' : this.label + '读取失败')
          .fontSize(14).fontWeight(FontWeight.Medium).fontColor($r('app.color.text_primary'))
        Text(this.state.error || '请稍后重试').fontSize(13).fontColor($r('app.color.text_secondary')).lineHeight(20)
        Button('重试').height(44).fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
          .onClick(() => this.onRetry())
      }.width('100%').padding(18).borderRadius(18).backgroundColor($r('app.color.card_background'))
    }
  }
}
