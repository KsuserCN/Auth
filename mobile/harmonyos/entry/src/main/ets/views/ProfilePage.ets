import { AppIcon, DemoNote, SectionHeader, SettingsRow, StatusBadge } from '../components/AppComponents';

@Component
export struct ProfilePage {
  @StorageLink('demoName') displayName: string = '林予安';
  @StorageLink('demoUsername') username: string = 'linyuan';
  @State private region: string = '上海 · 浦东新区';
  @State private bio: string = '保持好奇，认真生活。';
  @State private editorVisible: boolean = false;
  @State private editorField: string = '';
  @State private editorTitle: string = '';
  @State private editorValue: string = '';

  private toast(message: string): void {
    try {
      this.getUIContext().getPromptAction().showToast({ message: message });
    } catch (_) {
      // A transient toast must not interrupt local editing.
    }
  }

  private openEditor(field: string, title: string, value: string): void {
    this.editorField = field;
    this.editorTitle = title;
    this.editorValue = value;
    this.editorVisible = true;
  }

  private saveProfile(): void {
    const value = this.editorValue.trim();
    if (value.length === 0 && this.editorField !== 'bio') {
      this.toast('请填写' + this.editorTitle);
      return;
    }
    if (this.editorField === 'username' && !/^[\u4e00-\u9fa5a-zA-Z0-9_-]{3,20}$/.test(value)) {
      this.toast('用户名需为 3–20 个汉字、字母、数字、下划线或连字符');
      return;
    }
    switch (this.editorField) {
      case 'name': this.displayName = value; break;
      case 'username': this.username = value; break;
      case 'region': this.region = value; break;
      case 'bio': this.bio = value; break;
    }
    this.editorVisible = false;
    this.toast('已更新本次预览中的资料');
  }

  @Builder
  private profileEditor() {
    Scroll() {
      Column({ space: 20 }) {
        Row() {
          Column({ space: 6 }) {
            Text('编辑' + this.editorTitle).fontSize(24).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary'))
            Text('让你的资料，更像你。').fontSize(14).fontColor($r('app.color.text_secondary'))
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          AppIcon({ name: 'edit', iconSize: 24 })
        }.width('100%').padding({ top: 12 })

        Column({ space: 10 }) {
          Text(this.editorTitle).fontSize(13).fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_secondary'))
          if (this.editorField === 'bio') {
            TextArea({ placeholder: '写几句话介绍自己', text: this.editorValue })
              .height(140).maxLength(160).showCounter(true)
              .fontSize(16).fontColor($r('app.color.text_primary'))
              .backgroundColor($r('app.color.input_background')).borderRadius(16)
              .padding(16).onChange((value: string) => { this.editorValue = value; })
              .accessibilityText('个人简介')
          } else {
            TextInput({ placeholder: '请输入' + this.editorTitle, text: this.editorValue })
              .height(56).maxLength(this.editorField === 'username' ? 20 : 40)
              .fontSize(16).fontColor($r('app.color.text_primary'))
              .backgroundColor($r('app.color.input_background')).borderRadius(16)
              .padding({ left: 16, right: 16 })
              .onChange((value: string) => { this.editorValue = value; })
              .accessibilityText(this.editorTitle)
          }
          if (this.editorField === 'username') {
            Text('3–20 个字符，支持汉字、字母、数字、下划线和连字符。')
              .fontSize(12).fontColor($r('app.color.text_secondary')).lineHeight(20)
          }
        }.width('100%').alignItems(HorizontalAlign.Start)

        Button('保存', { type: ButtonType.Capsule })
          .width('100%').height(50).fontSize(16).fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.on_brand')).backgroundColor($r('app.color.brand'))
          .onClick(() => this.saveProfile())
        DemoNote({ text: '仅更新当前演示资料，不会提交至服务器' })
      }
      .width('100%').padding({ left: 24, right: 24, bottom: 24 })
      .alignItems(HorizontalAlign.Start)
      .backgroundColor($r('app.color.card_background'))
    }.width('100%').height('100%').scrollBar(BarState.Off)
  }

  @Builder
  private accountInfo(title: string, value: string, last: boolean = false) {
    Column() {
      Row({ space: 16 }) {
        Text(title).fontSize(14).fontColor($r('app.color.text_secondary'))
        Text(value).fontSize(14).fontColor($r('app.color.text_primary'))
          .layoutWeight(1).textAlign(TextAlign.End).lineHeight(22)
      }.width('100%').padding({ top: 16, bottom: 16 })
      if (!last) {
        Divider().color($r('app.color.divider'))
      }
    }.width('100%')
  }

  build() {
    Scroll() {
      Column({ space: 16 }) {
        Column({ space: 20 }) {
          Row({ space: 16 }) {
            Button({ type: ButtonType.Circle }) {
              Text(this.displayName.substring(0, 1)).fontSize(32).fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.brand'))
            }
            .width(76).height(76).backgroundColor($r('app.color.brand_soft'))
            .accessibilityText('头像，' + this.displayName)
            .onClick(() => {
              this.toast('头像上传将在后续接入，当前使用示范头像');
            })
            Column({ space: 7 }) {
              Text(this.displayName).fontSize(25).fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.text_primary')).maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              Text('@' + this.username).fontSize(14).fontColor($r('app.color.text_secondary'))
              StatusBadge({ text: '已验证账号' })
            }.alignItems(HorizontalAlign.Start).layoutWeight(1)
            Button({ type: ButtonType.Circle }) {
              AppIcon({ name: 'edit', iconSize: 19 })
            }.width(40).height(40).backgroundColor($r('app.color.input_background'))
              .accessibilityText('编辑姓名')
              .onClick(() => this.openEditor('name', '姓名', this.displayName))
          }.width('100%')
          Divider().color($r('app.color.divider'))
          Column({ space: 10 }) {
            Row() {
              Text('资料完整度').fontSize(13).fontColor($r('app.color.text_secondary')).layoutWeight(1)
              Text('86%').fontSize(14).fontWeight(FontWeight.Bold).fontColor($r('app.color.brand'))
            }.width('100%')
            Progress({ value: 86, total: 100, type: ProgressType.Linear })
              .width('100%').color($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
              .style({ strokeWidth: 5 })
          }.width('100%')
        }
        .width('100%').padding(20).borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 10 }) {
          SectionHeader({ title: '个人资料', subtitle: '轻点项目，记录关于你的点滴' })
          Column() {
            SettingsRow({ title: '姓名', value: this.displayName,
              onTap: () => this.openEditor('name', '姓名', this.displayName) })
            SettingsRow({ title: '用户名', value: this.username,
              onTap: () => this.openEditor('username', '用户名', this.username) })
            SettingsRow({ title: '地区', value: this.region,
              onTap: () => this.openEditor('region', '地区', this.region) })
            SettingsRow({ title: '个人简介', value: this.bio.length > 0 ? this.bio : '介绍一下自己', last: true,
              onTap: () => this.openEditor('bio', '个人简介', this.bio) })
          }.width('100%')
        }
        .width('100%').padding(20).borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        Column({ space: 10 }) {
          SectionHeader({ title: '账号信息', subtitle: '你的数字身份，安心保管' })
          Column() {
            this.accountInfo('邮箱', 'linyuan@example.com')
            this.accountInfo('账号 ID', 'KS · 100268')
            this.accountInfo('加入时间', '2026 年 8 月 16 日', true)
          }.width('100%')
          Row({ space: 8 }) {
            AppIcon({ name: 'lock', iconSize: 14, color: $r('app.color.text_tertiary') })
            Text('邮箱等安全信息可在「安全」中管理')
              .fontSize(12).fontColor($r('app.color.text_tertiary')).lineHeight(20)
          }.width('100%').padding({ top: 4 })
        }
        .width('100%').padding(20).borderRadius(24)
        .backgroundColor($r('app.color.card_background'))

        DemoNote()
      }
      .width('100%').constraintSize({ maxWidth: 680 })
      .padding({ left: 20, right: 20, top: 8, bottom: 24 })
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%').height('100%').scrollBar(BarState.Off)
    .backgroundColor($r('app.color.page_background'))
    .bindSheet($$this.editorVisible, this.profileEditor(), {
      height: SheetSize.LARGE,
      showClose: true,
      dragBar: true,
      backgroundColor: $r('app.color.card_background')
    })
  }
}
