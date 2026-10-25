import { image } from '@kit.ImageKit';
import { AppModel } from '../core/AppModel';
import { displayName, profileCompleteness } from '../core/Models';
import { ActionButton, AppIcon, EmptyState, InfoLine, SectionHeader, SettingsRow, UserAvatar } from '../components/AppComponents';
import { FeedbackBanner, InputField } from '../components/AccountComponents';

@Component
export struct ProfilePage {
  @ObjectLink model: AppModel;
  @State private editorVisible: boolean = false;
  @State private field: string = '';
  @State private title: string = '';
  @State private value: string = '';
  @State private avatarImage: image.PixelMap | undefined = undefined;

  aboutToAppear(): void { this.model.loadProfile(); }

  private edit(field: string, title: string, value: string): void {
    this.field = field; this.title = title; this.value = value; this.editorVisible = true;
  }

  private async chooseAvatar(): Promise<void> {
    await this.model.prepareAvatar();
    if (this.model.avatarPreview === null) { return; }
    try {
      this.avatarImage = await image.createImageSource(this.model.avatarPreview.data).createPixelMap();
      this.field = 'avatar'; this.title = '确认头像'; this.editorVisible = true;
    } catch (_) { this.model.error = '无法预览这张图片，请重新选择'; this.model.cancelAvatar(); }
  }

  private async save(): Promise<void> {
    if (this.field === 'avatar') { await this.model.confirmAvatar(); }
    else { await this.model.saveProfile(this.field, this.value); }
    if (!this.model.error) { this.editorVisible = false; }
  }

  @Builder
  private editor() {
    Scroll() {
      Column({ space: 20 }) {
        SectionHeader({ title: this.title, subtitle: this.field === 'avatar' ? '已按正方形裁剪，确认后上传' : '保存后更新你的账号资料' })
        FeedbackBanner({ model: this.model })
        if (this.field === 'avatar') {
          if (this.avatarImage !== undefined) {
            Image(this.avatarImage).width(240).height(240).objectFit(ImageFit.Cover).borderRadius(28)
          }
          ActionButton({ title: '重新选择', secondary: true, busy: this.model.busy, onTap: () => this.chooseAvatar() })
        } else if (this.field === 'gender') {
          Select([{ value: '保密' }, { value: '男' }, { value: '女' }])
            .selected(this.value === 'male' ? 1 : this.value === 'female' ? 2 : 0)
            .value(this.value === 'male' ? '男' : this.value === 'female' ? '女' : '保密')
            .onSelect((index: number) => { this.value = ['secret', 'male', 'female'][index]; })
        } else if (this.field === 'birthDate') {
          DatePicker({ start: new Date(1900, 0, 1), end: new Date(), selected: this.value ? new Date(this.value) : new Date() })
            .onDateChange((value: Date) => {
              this.value = value.getFullYear() + '-' + (value.getMonth() + 1).toString().padStart(2, '0') + '-' +
                value.getDate().toString().padStart(2, '0');
            }).width('100%')
        } else {
          InputField({ title: this.title, text: this.value, placeholder: '请输入' + this.title,
            multiline: this.field === 'bio', maxLength: this.field === 'bio' ? 500 : this.field === 'username' ? 20 : 100,
            onChange: (value: string) => { this.value = value; } })
        }
        ActionButton({ title: this.field === 'avatar' ? '确认上传' : '保存', busy: this.model.busy, onTap: () => this.save() })
      }.width('100%').padding(24).constraintSize({ maxWidth: 680 })
    }.width('100%').height('100%').scrollBar(BarState.Off)
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        if (this.model.profile !== null) {
          Column({ space: 18 }) {
            Row({ space: 16 }) {
              UserAvatar({ url: this.model.profile?.avatarUrl || '', name: displayName(this.model.profile), avatarSize: 76 })
              Column({ space: 7 }) {
                Text(displayName(this.model.profile)).fontSize(25).fontWeight(FontWeight.Bold)
                  .fontColor($r('app.color.text_primary')).maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
                Text('@' + this.model.profile?.username).fontSize(14).fontColor($r('app.color.text_secondary'))
                Button('更换头像').fontSize(13).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
                  .enabled(!this.model.busy).onClick(() => this.chooseAvatar())
              }.alignItems(HorizontalAlign.Start).layoutWeight(1)
            }.width('100%')
            Row() {
              Text('资料完整度').fontSize(13).fontColor($r('app.color.text_secondary')).layoutWeight(1)
              Text(profileCompleteness(this.model.profile) + '%').fontSize(14).fontWeight(FontWeight.Bold).fontColor($r('app.color.brand'))
            }.width('100%')
            Progress({ value: profileCompleteness(this.model.profile), total: 100, type: ProgressType.Linear })
              .width('100%').color($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          Column({ space: 8 }) {
            SectionHeader({ title: '个人资料', subtitle: '轻点项目即可编辑' })
            SettingsRow({ title: '姓名', value: this.model.profile?.realName || '未填写',
              onTap: () => this.edit('realName', '姓名', this.model.profile?.realName || '') })
            SettingsRow({ title: '用户名', value: this.model.profile?.username || '',
              onTap: () => this.edit('username', '用户名', this.model.profile?.username || '') })
            SettingsRow({ title: '性别', value: this.model.profile?.gender === 'male' ? '男' : this.model.profile?.gender === 'female' ? '女' : '保密',
              onTap: () => this.edit('gender', '性别', this.model.profile?.gender || 'secret') })
            SettingsRow({ title: '生日', value: this.model.profile?.birthDate || '未填写',
              onTap: () => this.edit('birthDate', '生日', this.model.profile?.birthDate || '') })
            SettingsRow({ title: '地区', value: this.model.profile?.region || '未填写',
              onTap: () => this.edit('region', '地区', this.model.profile?.region || '') })
            SettingsRow({ title: '个人简介', value: this.model.profile?.bio || '未填写', last: true,
              onTap: () => this.edit('bio', '个人简介', this.model.profile?.bio || '') })
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          Column({ space: 8 }) {
            SectionHeader({ title: '账号信息' })
            InfoLine({ title: '邮箱', value: this.model.profile?.email || '' })
            InfoLine({ title: '账号 ID', value: this.model.profile?.uuid || '' })
            InfoLine({ title: '最近更新', value: this.model.profile?.updatedAt || '' })
          }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
          ActionButton({ title: '刷新资料', secondary: true, busy: this.model.busy, onTap: () => this.model.loadProfile() })
        } else {
          EmptyState({ title: '无法读取资料', busy: this.model.busy, onRetry: () => this.model.loadProfile() })
        }
      }.width('100%').constraintSize({ maxWidth: 680 }).padding({ left: 20, right: 20, top: 8, bottom: 24 })
    }.width('100%').height('100%').scrollBar(BarState.Off).backgroundColor($r('app.color.page_background'))
      .bindSheet($$this.editorVisible, this.editor(), {
        height: SheetSize.LARGE, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background'),
        onDisappear: () => { this.model.cancelAvatar(); this.avatarImage?.release(); this.avatarImage = undefined; }
      })
  }
}
