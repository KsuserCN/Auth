import { AppModel } from '../core/AppModel';
import { PasskeyItem, passwordError } from '../core/Models';
import { ActionButton, EmptyState, IconTile, InfoLine, SectionHeader, SettingsRow, StatusBadge, readableDate } from '../components/AppComponents';
import { FeedbackBanner, InputField, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct CredentialPage {
  @ObjectLink model: AppModel;
  @Prop kind: string = 'passkeys';
  @State private name: string = '';
  @State private renameId: number = 0;
  @State private renameVisible: boolean = false;
  @State private code: string = '';
  @State private email: string = '';
  @State private password: string = '';
  @State private confirmPassword: string = '';
  @State private confirmText: string = '';

  aboutToAppear(): void {
    if (this.kind === 'passkeys' || this.kind === 'totp') { this.model.loadSecurity(); }
    if (this.kind === 'recovery-codes') { this.model.loadRecoveryCodes(); }
  }

  aboutToDisappear(): void {
    this.model.totpSetup = null; this.model.recoveryCodes = []; this.model.recoveryTicket = null;
    this.password = ''; this.confirmPassword = ''; this.code = '';
  }

  private confirm(title: string, message: string, action: () => void): void {
    this.getUIContext().showAlertDialog({ title: title, message: message,
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '确认', fontColor: $r('app.color.danger'), action: action }
    });
  }

  private async savePassword(): Promise<void> {
    const error = passwordError(this.password, this.model.passwordPolicy);
    if (error) { this.model.error = error; return; }
    if (this.password !== this.confirmPassword) { this.model.error = '两次输入的密码不一致'; return; }
    await this.model.changePassword(this.password); this.password = ''; this.confirmPassword = '';
  }

  @Builder
  private renameSheet() {
    Column({ space: 20 }) {
      SectionHeader({ title: '重命名通行密钥' })
      FeedbackBanner({ model: this.model })
      InputField({ title: '名称', text: this.name, placeholder: '如：我的鸿蒙手机', maxLength: 100,
        onChange: (value: string) => { this.name = value; } })
      ActionButton({ title: '保存', busy: this.model.busy, onTap: async () => {
        await this.model.renamePasskey(this.renameId, this.name); if (!this.model.error) { this.renameVisible = false; }
      } })
    }.width('100%').padding(24)
  }

  @Builder
  private passkey(item: PasskeyItem) {
    Column({ space: 12 }) {
      Row({ space: 14 }) {
        IconTile({ name: 'key', tileSize: 48 })
        Column({ space: 6 }) {
          Text(item.name).fontSize(17).fontWeight(FontWeight.Bold).fontColor($r('app.color.text_primary'))
          Text('最近使用 · ' + readableDate(item.lastUsedAt)).fontSize(12).fontColor($r('app.color.text_secondary'))
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      }.width('100%')
      InfoLine({ title: '创建时间', value: readableDate(item.createdAt) })
      Row({ space: 12 }) {
        Button('重命名').layoutWeight(1).fontColor($r('app.color.brand')).backgroundColor($r('app.color.brand_soft'))
          .onClick(() => { this.name = item.name; this.renameId = item.id; this.renameVisible = true; })
        Button('删除').layoutWeight(1).fontColor($r('app.color.danger')).backgroundColor($r('app.color.danger_soft'))
          .enabled(!this.model.busy).onClick(() => this.confirm('删除此通行密钥？', '此密钥将无法继续用于登录和身份验证。', () => this.model.deletePasskey(item.id)))
      }.width('100%')
    }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
  }

  build() {
    Column({ space: 18 }) {
      if (this.kind === 'passkeys') {
        SectionHeader({ title: '用熟悉的方式，安全登录', subtitle: '使用面容、指纹或设备锁屏验证，无需记住密码' })
        if (this.model.passkeys.length === 0) {
          EmptyState({ title: '还没有通行密钥', busy: this.model.busy, onRetry: () => this.model.loadSecurity() })
        }
        ForEach(this.model.passkeys, (item: PasskeyItem) => { this.passkey(item) }, (item: PasskeyItem) => item.id.toString() + ":" + item.name + ":" + (item.lastUsedAt || ""))
        Column({ space: 16 }) {
          InputField({ title: '新密钥名称', text: this.name, placeholder: '为这台设备取个名称', maxLength: 100,
            onChange: (value: string) => { this.name = value; } })
          ActionButton({ title: '添加通行密钥', busy: this.model.busy, disabled: !this.name.trim(), onTap: () => this.model.addPasskey(this.name) })
        }.width('100%').padding(20).borderRadius(24).backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'totp') {
        Column({ space: 18 }) {
          IconTile({ name: 'security', tileSize: 60 })
          SectionHeader({ title: '身份验证器', subtitle: '动态验证码，为重要操作增加一层保护' })
          StatusBadge({ text: this.model.totp.enabled ? '已启用' : '未启用', positive: this.model.totp.enabled })
          if (this.model.totp.enabled) {
            Text('请妥善保管验证器及恢复码，避免失去账号访问权限。').fontSize(14).lineHeight(23).fontColor($r('app.color.text_secondary'))
            ActionButton({ title: '停用验证器', destructive: true, busy: this.model.busy,
              onTap: () => this.confirm('停用身份验证器？', '请确保账号仍有其他可用的验证方式。', () => this.model.disableTotp()) })
          } else if (this.model.totpSetup === null) {
            ActionButton({ title: '设置验证器', busy: this.model.busy, onTap: () => this.model.setupTotp() })
          }
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
        if (this.model.totpSetup !== null) {
          Column({ space: 18 }) {
            SectionHeader({ title: '添加到验证器', subtitle: '扫描二维码或手动输入密钥，然后填写动态验证码' })
            QRCode(this.model.totpSetup.qrCodeUrl).width(216).height(216).color(Color.Black).backgroundColor(Color.White)
            Text(this.model.totpSetup.secret).fontSize(16).fontColor($r('app.color.text_primary')).copyOption(CopyOptions.LocalDevice)
            InputField({ title: '动态验证码', text: this.code, placeholder: '6 位数字', numeric: true, maxLength: 6,
              onChange: (value: string) => { this.code = value; } })
            SectionHeader({ title: '请保存恢复码', subtitle: '恢复码可在无法使用验证器时登录，每个仅可使用一次' })
            ForEach(this.model.totpSetup.recoveryCodes, (value: string) => {
              Text(value).fontSize(15).fontColor($r('app.color.text_primary')).copyOption(CopyOptions.LocalDevice)
            }, (value: string) => value)
            ActionButton({ title: '完成设置', busy: this.model.busy, disabled: !/^\d{6}$/.test(this.code), onTap: () => this.model.enableTotp(this.code) })
          }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
        }
      } else if (this.kind === 'recovery-codes') {
        Column({ space: 18 }) {
          SectionHeader({ title: '验证器恢复码', subtitle: '每个恢复码仅可使用一次，请保存在安全的位置' })
          if (this.model.recoveryCodes.length === 0) {
            EmptyState({ title: '暂无可显示的恢复码', busy: this.model.busy, onRetry: () => this.model.loadRecoveryCodes() })
          }
          ForEach(this.model.recoveryCodes, (value: string) => {
            Text(value).fontSize(17).fontColor($r('app.color.text_primary')).copyOption(CopyOptions.LocalDevice)
          }, (value: string) => value)
          ActionButton({ title: '重新生成恢复码', secondary: true, busy: this.model.busy,
            onTap: () => this.confirm('重新生成恢复码？', '旧的恢复码将立即失效。', () => this.model.loadRecoveryCodes(true)) })
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'email') {
        Column({ space: 18 }) {
          IconTile({ name: 'mail', tileSize: 52 })
          SectionHeader({ title: '让重要提醒，及时送达', subtitle: '当前邮箱：' + (this.model.profile?.email || '') })
          InputField({ title: '新邮箱', text: this.email, placeholder: '输入新的邮箱地址',
            onChange: (value: string) => { this.email = value; } })
          VerificationCodeField({ model: this.model, email: this.email, type: 'change-email', code: this.code,
            onChange: (value: string) => { this.code = value; } })
          ActionButton({ title: '修改邮箱', busy: this.model.busy, onTap: () => this.model.changeEmail(this.email, this.code) })
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'password') {
        Column({ space: 18 }) {
          IconTile({ name: 'lock', tileSize: 52 })
          SectionHeader({ title: '为账号换一把新钥匙', subtitle: '提交前将按需验证当前身份' })
          InputField({ title: '新密码', text: this.password, placeholder: '设置新的密码', secure: true,
            onChange: (value: string) => { this.password = value; } })
          InputField({ title: '确认新密码', text: this.confirmPassword, placeholder: '再次输入新密码', secure: true,
            onChange: (value: string) => { this.confirmPassword = value; } })
          ActionButton({ title: '保存新密码', busy: this.model.busy, onTap: () => this.savePassword() })
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'recovery') {
        Column({ space: 18 }) {
          IconTile({ name: 'scan', tileSize: 60 })
          SectionHeader({ title: '账号恢复授权', subtitle: '仅在确认是你本人的恢复操作时使用，切勿转发给他人' })
          if (this.model.recoveryTicket?.recoveryCode) {
            Text(this.model.recoveryTicket?.recoveryCode || '').fontSize(21).fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_primary')).copyOption(CopyOptions.LocalDevice)
            Text('有效期 ' + (this.model.recoveryTicket?.expiresInSeconds || 0) + ' 秒，请尽快使用。')
              .fontSize(13).fontColor($r('app.color.text_secondary'))
            InfoLine({ title: '账号', value: this.model.recoveryTicket?.username || '' })
          }
          ActionButton({ title: '生成恢复授权', busy: this.model.busy, onTap: () => this.model.issueRecoveryTicket() })
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
      } else if (this.kind === 'delete') {
        Column({ space: 18 }) {
          IconTile({ name: 'close', tileSize: 60, color: $r('app.color.danger'), tileBackground: $r('app.color.danger_soft') })
          SectionHeader({ title: '永久删除账号', subtitle: '此操作会删除账号及相关数据，完成后无法撤销。请谨慎确认。' })
          Text('请在下方完整输入：我真的不想要我的号辣').fontSize(14).lineHeight(23).fontColor($r('app.color.danger'))
          InputField({ title: '确认文字', text: this.confirmText, placeholder: '输入上述确认文字',
            onChange: (value: string) => { this.confirmText = value; } })
          ActionButton({ title: '永久删除账号', destructive: true, busy: this.model.busy,
            disabled: this.confirmText !== '我真的不想要我的号辣', onTap: () => this.confirm('确认永久删除？', '删除后无法恢复账号数据。', () => this.model.deleteAccount(this.confirmText)) })
        }.width('100%').padding(22).borderRadius(24).backgroundColor($r('app.color.card_background'))
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
      .bindSheet($$this.renameVisible, this.renameSheet(), { height: SheetSize.MEDIUM, showClose: true, dragBar: true, backgroundColor: $r('app.color.card_background') })
  }
}
