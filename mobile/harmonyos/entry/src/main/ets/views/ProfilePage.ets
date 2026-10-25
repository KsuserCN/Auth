import { image } from '@kit.ImageKit';
import { AppModel } from '../core/AppModel';
import { displayName, profileRealName, profileCompleteness, dateText, validUsername } from '../core/Models';
import { avatarCropRegion } from '../core/AvatarGeometry';
import { ActionButton, InfoLine, SectionHeader, SettingsRow, UserAvatar } from '../components/AppComponents';
import { FeedbackBanner, InputField, ResourceFeedback } from '../components/AccountComponents';

@Component
export struct ProfilePage {
  @ObjectLink @Watch('accountChanged') model: AppModel;
  @State private editorVisible: boolean = false;
  @State private field: string = '';
  @State private title: string = '';
  @State private value: string = '';
  @State private refreshing: boolean = false;
  @State private avatarBusy: boolean = false;
  @State private avatarImage: image.PixelMap | undefined = undefined;
  @State private viewport: number = 280;
  @State private zoom: number = 1;
  @State private offsetX: number = 0;
  @State private offsetY: number = 0;
  private startZoom: number = 1;
  private startX: number = 0;
  private startY: number = 0;
  private account: string = '';
  private active: boolean = false;
  private cropMask: CanvasRenderingContext2D = new CanvasRenderingContext2D(new RenderingContextSettings(true));

  aboutToAppear(): void { this.active = true; this.account = this.model.profile?.uuid || ''; this.model.loadProfile(); }
  aboutToDisappear(): void { this.active = false; this.releaseAvatar(); }
  private accountChanged(): void {
    const account = this.model.profile?.uuid || '';
    if (account !== this.account) { this.account = account; this.editorVisible = false; this.value = ''; this.releaseAvatar(); }
  }
  private releaseAvatar(): void {
    this.model.cancelAvatar();
    try { this.avatarImage?.release().catch(() => {}); } catch (_) {}
    this.avatarImage = undefined;
  }
  private today(): string {
    const date = new Date();
    return date.getFullYear() + '-' + (date.getMonth() + 1).toString().padStart(2, '0') + '-' + date.getDate().toString().padStart(2, '0');
  }
  private edit(field: string, title: string, value: string): void {
    this.model.clearFeedback(); this.field = field; this.title = title;
    this.value = field === 'birthDate' && !value ? this.today() : value;
    this.editorVisible = true;
  }
  private async chooseAvatar(): Promise<void> {
    if (this.avatarBusy || this.model.busy) { return; }
    this.avatarBusy = true;
    const account = this.model.profile?.uuid;
    const previous = this.model.avatarSource;
    const previousPreview = this.model.avatarPreview;
    let decoder: image.ImageSource | undefined;
    try {
      const result = await this.model.prepareAvatar();
      if (result.status !== 'success' || !this.model.avatarSource) { return; }
      const selected = this.model.avatarSource;
      decoder = image.createImageSource(selected.data);
      const pixels = await decoder.createPixelMap();
      if (!this.active || account !== this.model.profile?.uuid || selected !== this.model.avatarSource) {
        await pixels.release(); if (selected === this.model.avatarSource) { this.model.cancelAvatar(); } return;
      }
      try { this.avatarImage?.release().catch(() => {}); } catch (_) {}
      this.avatarImage = pixels; this.zoom = 1; this.offsetX = 0; this.offsetY = 0;
      this.field = 'avatar'; this.title = '裁剪头像'; this.editorVisible = true;
    } catch (_) {
      if (account === this.model.profile?.uuid) {
        this.model.error = '无法预览这张图片，请重新选择'; this.model.avatarSource = previous; this.model.avatarPreview = previousPreview;
      }
    } finally { try { await decoder?.release(); } catch (_) {} this.avatarBusy = false; }
  }
  private imageScale(): number {
    const source = this.model.avatarSource;
    return source ? Math.max(this.viewport / source.width, this.viewport / source.height) * this.zoom : 1;
  }
  private constrain(): void {
    const source = this.model.avatarSource;
    if (!source) { return; }
    const x = Math.max(0, (source.width * this.imageScale() - this.viewport) / 2);
    const y = Math.max(0, (source.height * this.imageScale() - this.viewport) / 2);
    this.offsetX = Math.max(-x, Math.min(x, this.offsetX)); this.offsetY = Math.max(-y, Math.min(y, this.offsetY));
  }
  private drawMask(): void {
    const size = this.viewport;
    this.cropMask.clearRect(0, 0, size, size);
    this.cropMask.globalCompositeOperation = 'source-over'; this.cropMask.fillStyle = 'rgba(0,0,0,0.4)';
    this.cropMask.fillRect(0, 0, size, size); this.cropMask.globalCompositeOperation = 'destination-out';
    this.cropMask.beginPath(); this.cropMask.arc(size / 2, size / 2, size / 2 - 2, 0, Math.PI * 2); this.cropMask.fill();
    this.cropMask.globalCompositeOperation = 'source-over'; this.cropMask.strokeStyle = '#ffffff'; this.cropMask.lineWidth = 2;
    this.cropMask.stroke();
  }
  private valid(): boolean {
    return this.field !== 'username' || validUsername(this.value);
  }
  private async save(): Promise<void> {
    if (!this.valid() || this.avatarBusy || this.model.busy) { return; }
    if (this.field === 'avatar') {
      const source = this.model.avatarSource;
      if (!source) { return; }
      const crop = avatarCropRegion(source.width, source.height, this.viewport, this.zoom, this.offsetX, this.offsetY);
      if ((await this.model.cropAvatar(crop)).status !== 'success') { return; }
      if ((await this.model.confirmAvatar()).status === 'success') { this.editorVisible = false; }
    } else {
      const result = await this.model.saveProfile(this.field, this.value);
      if (result.status === 'success') { this.editorVisible = false; }
    }
  }
  private async refresh(): Promise<void> {
    try { await this.model.loadProfile(); } finally { this.refreshing = false; }
  }

  @Builder
  private cropper() {
    Stack({ alignContent: Alignment.Center }) {
      if (this.avatarImage) {
        Image(this.avatarImage).width((this.model.avatarSource?.width || 1) * this.imageScale())
          .height((this.model.avatarSource?.height || 1) * this.imageScale()).objectFit(ImageFit.Fill)
          .translate({ x: this.offsetX, y: this.offsetY })
      }
      Canvas(this.cropMask).width('100%').height('100%').hitTestBehavior(HitTestMode.None).onReady(() => this.drawMask())
    }.width('100%').aspectRatio(1).clip(true).backgroundColor(Color.Black).borderRadius(18)
      .onAreaChange((_old: Area, area: Area) => { this.viewport = Number(area.width); this.constrain(); this.drawMask(); })
      .gesture(GestureGroup(GestureMode.Parallel,
        PanGesture().onActionStart(() => { this.startX = this.offsetX; this.startY = this.offsetY; })
          .onActionUpdate((event: GestureEvent) => { this.offsetX = this.startX + event.offsetX; this.offsetY = this.startY + event.offsetY; this.constrain(); }),
        PinchGesture().onActionStart(() => { this.startZoom = this.zoom; })
          .onActionUpdate((event: GestureEvent) => { this.zoom = Math.max(1, Math.min(6, this.startZoom * event.scale)); this.constrain(); })
      )).accessibilityText('可拖动和缩放的头像预览，圆圈内为头像内容')
    Text('拖动调整位置，双指缩放。圆圈内是头像预览。').fontSize(13).lineHeight(21).fontColor($r('app.color.text_secondary'))
    ActionButton({ title: '重新选择', secondary: true, disabled: this.model.busy || this.avatarBusy, onTap: () => this.chooseAvatar() })
  }

  @Builder
  private editor() {
    Column() {
      Scroll() {
        Column({ space: 18 }) {
          SectionHeader({ title: this.title, subtitle: this.field === 'avatar' ? '确认后将上传新的头像' : '保存后更新账号资料' })
          FeedbackBanner({ model: this.model })
          if (this.field === 'avatar') { this.cropper() }
          else if (this.field === 'gender') {
            Select([{ value: '保密' }, { value: '男' }, { value: '女' }]).selected(this.value === 'male' ? 1 : this.value === 'female' ? 2 : 0)
              .value(this.value === 'male' ? '男' : this.value === 'female' ? '女' : '保密')
              .onSelect((index: number) => { this.value = ['secret', 'male', 'female'][index]; })
          } else if (this.field === 'birthDate') {
            DatePicker({ start: new Date(1900, 0, 1), end: new Date(), selected: new Date(this.value + 'T12:00:00') })
              .onDateChange((date: Date) => { this.value = date.getFullYear() + '-' + (date.getMonth() + 1).toString().padStart(2, '0') + '-' + date.getDate().toString().padStart(2, '0'); })
          } else {
            InputField({ title: this.title, text: this.value, placeholder: '请输入' + this.title, multiline: this.field === 'bio',
              maxLength: this.field === 'bio' ? 500 : this.field === 'username' ? 20 : 100, onChange: (value: string) => { this.value = value; } })
            if (this.field === 'username') {
              Text('3–20 个字符，支持汉字、字母、数字、下划线和连字符。').fontSize(12).fontColor($r('app.color.text_secondary'))
            }
          }
        }.width('100%').padding(24)
      }.layoutWeight(1).width('100%').align(Alignment.TopStart)
      Column() {
        ActionButton({ title: this.field === 'avatar' ? '使用头像' : '保存', busy: this.model.busy,
          disabled: !this.valid() || this.avatarBusy, onTap: () => this.save() })
      }.width('100%').padding({ left: 24, right: 24, top: 12, bottom: 24 }).backgroundColor($r('app.color.card_background'))
    }.width('100%').height('100%')
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          ResourceFeedback({ state: this.model.profileState, label: '资料', onRetry: () => this.model.loadProfile() })
          if (this.model.profile) {
            Column({ space: 18 }) {
              Row({ space: 16 }) {
                UserAvatar({ url: this.model.profile.avatarUrl || '', name: displayName(this.model.profile), avatarSize: 76 })
                Column({ space: 7 }) {
                  Text(displayName(this.model.profile)).fontSize(25).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
                  Text('@' + this.model.profile.username).fontSize(14).fontColor($r('app.color.text_secondary'))
                  Button('更换头像').height(44).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
                    .enabled(!this.model.busy && !this.avatarBusy).onClick(() => this.chooseAvatar())
                }.layoutWeight(1).alignItems(HorizontalAlign.Start)
              }.width('100%')
              Row() {
                Text('资料完整度').fontSize(13).fontColor($r('app.color.text_secondary')).layoutWeight(1)
                Text(profileCompleteness(this.model.profile) + '%').fontColor($r('app.color.brand'))
              }.width('100%')
              Progress({ value: profileCompleteness(this.model.profile), total: 100, type: ProgressType.Linear }).width('100%').color($r('app.color.brand'))
            }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
            Column({ space: 6 }) {
              SectionHeader({ title: '个人资料', subtitle: '轻点项目即可编辑' })
              SettingsRow({ title: '姓名', value: profileRealName(this.model.profile) || '未填写', onTap: () => this.edit('realName', '姓名', profileRealName(this.model.profile)) })
              SettingsRow({ title: '用户名', value: this.model.profile.username, onTap: () => this.edit('username', '用户名', this.model.profile?.username || '') })
              SettingsRow({ title: '性别', value: this.model.profile.gender === 'male' ? '男' : this.model.profile.gender === 'female' ? '女' : '保密', onTap: () => this.edit('gender', '性别', this.model.profile?.gender || 'secret') })
              SettingsRow({ title: '生日', value: this.model.profile.birthDate || '未填写', onTap: () => this.edit('birthDate', '生日', this.model.profile?.birthDate || '') })
              SettingsRow({ title: '地区', value: this.model.profile.region || '未填写', onTap: () => this.edit('region', '地区', this.model.profile?.region || '') })
              SettingsRow({ title: '个人简介', value: this.model.profile.bio || '未填写', last: true, onTap: () => this.edit('bio', '个人简介', this.model.profile?.bio || '') })
            }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
            Column() {
              SectionHeader({ title: '账号信息' })
              InfoLine({ title: '邮箱', value: this.model.profile.email || '未绑定邮箱' })
              InfoLine({ title: '账号 ID', value: this.model.profile.uuid })
              InfoLine({ title: '最近更新', value: dateText(this.model.profile.updatedAt) })
            }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
          }
        }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
      }.width('100%').height('100%').scrollBar(BarState.Off).align(Alignment.TopStart)
    }.onRefreshing(() => this.refresh()).width('100%').height('100%').backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.editorVisible, this.editor(), { height: SheetSize.LARGE, showClose: true, dragBar: true,
        backgroundColor: $r('app.color.card_background'), onWillDismiss: (action: DismissSheetAction) => { if (!this.model.busy) { action.dismiss(); } },
        onDisappear: () => { this.value = ''; this.releaseAvatar(); } })
  }
}
