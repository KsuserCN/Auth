import { AppModel } from '../core/AppModel';
import { ActionResult, PasskeyItem, dateText, passwordError, passwordRequirementText, validEmail } from '../core/Models';
import { formatDuration } from '../core/Presentation';
import { ActionButton, IconTile, InfoLine, SectionHeader, SettingsRow } from '../components/AppComponents';
import { FeedbackBanner, InputField, ResourceFeedback, VerificationCodeField } from '../components/AccountComponents';

@Component
export struct CredentialPage {
  @ObjectLink model: AppModel;
  @Prop kind: string = '';
  onComplete: () => void = () => {};
  @State private name: string = '';
  @State private renameName: string = '';
  @State private code: string = '';
  @State private email: string = '';
  @State private password: string = '';
  @State private confirmation: string = '';
  @State private confirmText: string = '';
  @State private savedCodes: boolean = false;
  @State private selected: PasskeyItem | null = null;
  @State private rename: boolean = false;
  @State private action: string = '';
  @State private now: number = Date.now();
  private timer: number = -1;
  private active: boolean = false;

  aboutToAppear(): void {
    this.active = true;
    if (this.kind === 'passkeys' || this.kind === 'totp') { this.model.loadSecurity(); }
    if (this.kind === 'password') { this.model.loadPasswordRequirement(); }
    if (this.kind === 'recovery-codes') { this.perform('codes', () => this.model.loadRecoveryCodes()); }
    if (this.kind === 'recovery') {
      this.now = Date.now(); this.timer = setInterval(() => { this.now = Date.now(); }, 1000);
      if (!this.model.recoveryTicket) { this.perform('recovery', () => this.model.issueRecoveryTicket()); }
    }
  }
  aboutToDisappear(): void {
    this.active = false;
    if (this.action && this.model.challengeKind === 'sensitive') { this.model.cancelChallenge(); }
    if (this.timer !== -1) { clearInterval(this.timer); this.timer = -1; }
    this.password = ''; this.confirmation = ''; this.code = ''; this.confirmText = ''; this.name = '';
    this.model.totpSetup = null; this.model.recoveryCodes = [];
    if (this.kind === 'recovery') { this.model.recoveryTicket = null; this.model.recoveryExpiresAt = 0; }
  }
  private async perform(key: string, operation: () => Promise<ActionResult>, close: boolean = false): Promise<void> {
    if (this.action || this.model.busy) { return; }
    this.action = key;
    const account = this.model.profile?.uuid;
    try {
      const result = await operation();
      if (result.status === 'success' && this.active && account === this.model.profile?.uuid) {
        if (key === 'setup') { this.savedCodes = false; this.code = ''; }
        if (key === 'rename') { this.rename = false; this.selected = null; this.renameName = ''; }
        if (key === 'add-key') { this.name = ''; }
        if (key === 'delete-key') { this.selected = null; this.rename = false; }
        if (close) { this.onComplete(); }
      }
    } finally { this.action = ''; }
  }
  private remaining(): number { return Math.max(0, Math.ceil((this.model.recoveryExpiresAt - this.now) / 1000)); }
  private confirm(title: string, message: string, key: string, operation: () => Promise<ActionResult>, close: boolean = false): void {
    this.getUIContext().showAlertDialog({ title: title, message: message,
      primaryButton: { value: '取消', action: () => {} },
      secondaryButton: { value: '确认', fontColor: $r('app.color.danger'), action: () => this.perform(key, operation, close) } });
  }
  private transports(value: string | undefined): string {
    return (value || '').split(',').map((part: string) => {
      switch (part.trim()) {
        case 'internal': return '此设备'; case 'usb': return 'USB 安全密钥'; case 'nfc': return 'NFC';
        case 'ble': return '蓝牙'; case 'hybrid': return '跨设备'; default: return part.trim();
      }
    }).filter((part: string) => !!part).join('、') || '未知';
  }
  private async copy(value: string): Promise<void> { await this.model.copyText(value); }
  private validTicket(): boolean {
    this.now = Date.now();
    if (this.model.recoveryExpiresAt <= this.now) { this.model.error = '恢复授权已过期，请重新生成'; return false; }
    return true;
  }
  private copyTicket(link: boolean): void {
    if (this.validTicket()) { this.copy(link ? this.model.recoveryUrl() : this.model.recoveryTicket?.recoveryCode || ''); }
  }
  private openTicket(): void {
    if (this.validTicket()) { this.model.openRecovery(this.model.recoveryTicket?.recoveryCode || ''); }
  }

  @Builder
  private codes(codes: string[]) {
    Column({ space: 12 }) {
      SectionHeader({ title: '恢复码', subtitle: '每个恢复码只能使用一次。请保存在安全的地方，不要提供给他人。' })
      ForEach(codes, (value: string, index: number) => {
        Row({ space: 12 }) {
          Text((index + 1).toString().padStart(2, '0')).fontSize(12).fontColor($r('app.color.text_tertiary'))
          Text(value).fontFamily('monospace').fontSize(16).fontColor($r('app.color.text_primary')).layoutWeight(1)
          Button('复制').height(44).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent).onClick(() => this.copy(value))
        }.width('100%')
      }, (value: string) => value)
      ActionButton({ title: '复制全部恢复码', secondary: true, disabled: !codes.length || this.model.busy,
        onTap: () => this.copy(codes.join('\n')) })
    }.width('100%').padding(20).borderRadius(20).backgroundColor($r('app.color.card_background'))
  }

  @Builder
  private passkeys() {
    ResourceFeedback({ state: this.model.passkeysState, label: '通行密钥', onRetry: () => this.model.loadSecurity() })
    SectionHeader({ title: '使用通行密钥', subtitle: '通过设备解锁安全登录，无需输入密码。' })
    if (this.selected) {
      Column({ space: 8 }) {
        SectionHeader({ title: this.selected.name })
        InfoLine({ title: '连接方式', value: this.transports(this.selected.transports) })
        InfoLine({ title: '创建时间', value: dateText(this.selected.createdAt) })
        InfoLine({ title: '最近使用', value: dateText(this.selected.lastUsedAt) })
        if (this.rename) {
          InputField({ title: '密钥名称', text: this.renameName, maxLength: 50, onChange: (value: string) => { this.renameName = value; } })
          ActionButton({ title: '保存名称', busy: this.action === 'rename', disabled: !this.renameName.trim() || !!this.action || this.model.busy,
            onTap: () => { const key = this.selected; if (key) { this.perform('rename', () => this.model.renamePasskey(key.id, this.renameName)); } } })
        }
        Button('关闭详情').height(44).backgroundColor(Color.Transparent).fontColor($r('app.color.brand'))
          .onClick(() => { this.selected = null; this.rename = false; })
      }.width('100%').padding(20).borderRadius(20).backgroundColor($r('app.color.card_background'))
    }
    if (this.model.passkeysState.hasData && !this.model.passkeys.length) {
      Text('还没有通行密钥，添加后可使用设备解锁登录。').fontSize(14).lineHeight(22).fontColor($r('app.color.text_secondary'))
    }
    ForEach(this.model.passkeys, (key: PasskeyItem) => {
      Row({ space: 12 }) {
        IconTile({ name: 'key' })
        Column({ space: 5 }) {
          Text(key.name).fontSize(16).fontColor($r('app.color.text_primary'))
          Text('最近使用：' + dateText(key.lastUsedAt)).fontSize(12).fontColor($r('app.color.text_secondary'))
        }.layoutWeight(1).alignItems(HorizontalAlign.Start)
        Button('管理').height(44).fontColor($r('app.color.brand')).backgroundColor(Color.Transparent)
          .enabled(!this.action && !this.model.busy).bindMenu([
            { value: '查看详情', action: () => { this.selected = key; this.rename = false; } },
            { value: '重命名', action: () => { this.selected = key; this.renameName = key.name; this.rename = true; } },
            { value: '删除通行密钥', action: () => this.confirm('删除通行密钥？', '请确保还有其他可用的登录方式。', 'delete-key', () => this.model.deletePasskey(key.id)) }
          ])
      }.width('100%').padding(16).borderRadius(18).backgroundColor($r('app.color.card_background'))
    }, (key: PasskeyItem) => key.id.toString() + key.name)
    InputField({ title: '新密钥名称', text: this.name, placeholder: '例如：我的鸿蒙手机', maxLength: 50, onChange: (value: string) => { this.name = value; } })
    ActionButton({ title: '添加通行密钥', busy: this.action === 'add-key', disabled: !this.name.trim() || !!this.action || this.model.busy,
      onTap: () => this.perform('add-key', () => this.model.addPasskey(this.name)) })
  }

  @Builder
  private totp() {
    ResourceFeedback({ state: this.model.totpState, label: '验证器状态', onRetry: () => this.model.loadSecurity() })
    if (this.model.totpState.hasData && this.model.totp.enabled) {
      SectionHeader({ title: '验证器已启用', subtitle: '使用验证器生成的动态验证码完成身份验证。' })
      InfoLine({ title: '剩余恢复码', value: this.model.totp.recoveryCodesCount + ' 个' })
      ActionButton({ title: '停用身份验证器', destructive: true, busy: this.action === 'disable-totp', disabled: !!this.action || this.model.busy,
        onTap: () => this.confirm('停用身份验证器？', '停用后将无法再使用当前动态验证码和恢复码。', 'disable-totp', () => this.model.disableTotp(), true) })
      if (this.model.recoveryCodes.length) { this.codes(this.model.recoveryCodes) }
    } else if (this.model.totpSetup) {
      SectionHeader({ title: '添加到你的验证器', subtitle: '使用另一台设备扫描二维码，或复制密钥手动添加。' })
      QRCode(this.model.totpSetup.qrCodeUrl).width(220).height(220).color(Color.Black).backgroundColor(Color.White)
      Text(this.model.totpSetup.secret).fontFamily('monospace').fontSize(16).fontColor($r('app.color.text_primary'))
      ActionButton({ title: '复制绑定密钥', secondary: true, onTap: () => this.copy(this.model.totpSetup?.secret || '') })
      this.codes(this.model.totpSetup.recoveryCodes)
      Row({ space: 10 }) {
        Checkbox().select(this.savedCodes).selectedColor($r('app.color.brand')).onChange((value: boolean) => { this.savedCodes = value; })
          .accessibilityText('我已安全保存恢复码')
        Text('我已安全保存恢复码').fontSize(14).fontColor($r('app.color.text_primary')).layoutWeight(1)
      }.width('100%').constraintSize({ minHeight: 48 })
      InputField({ title: '验证器中的 6 位验证码', text: this.code, numeric: true, maxLength: 6, onChange: (value: string) => { this.code = value; } })
      ActionButton({ title: '确认启用', busy: this.action === 'enable-totp', disabled: !this.savedCodes || !/^\d{6}$/.test(this.code) || !!this.action || this.model.busy,
        onTap: () => this.perform('enable-totp', () => this.model.enableTotp(this.code)) })
    } else if (this.model.totpState.hasData) {
      SectionHeader({ title: '启用身份验证器', subtitle: '为账号增加一层独立的动态验证码保护。' })
      ActionButton({ title: '开始设置', busy: this.action === 'setup', disabled: !!this.action || this.model.busy,
        onTap: () => this.perform('setup', () => this.model.setupTotp()) })
    }
  }

  @Builder
  private recovery() {
    SectionHeader({ title: '账号恢复授权', subtitle: '供其他设备通过官方网页找回账号。仅向你信任的人或设备展示。' })
    if (this.model.recoveryTicket) {
      Column({ space: 12 }) {
        if (this.remaining() > 0) {
          QRCode(this.model.recoveryUrl()).width(220).height(220).color(Color.Black).backgroundColor(Color.White)
          Text(this.model.recoveryTicket.recoveryCode || '').fontFamily('monospace').fontSize(17).fontColor($r('app.color.text_primary'))
        }
        Text(this.remaining() > 0 ? '剩余 ' + formatDuration(this.remaining()) : '恢复授权已过期，请重新生成')
          .fontSize(14).fontColor(this.remaining() > 0 ? $r('app.color.brand') : $r('app.color.danger'))
        InfoLine({ title: '账号', value: this.model.recoveryTicket.username || '' })
        InfoLine({ title: '邮箱', value: this.model.recoveryTicket.maskedEmail || '' })
        InfoLine({ title: '背书设备', value: [this.model.recoveryTicket.sponsorClientName || '', this.model.recoveryTicket.sponsorSystem || '', this.model.recoveryTicket.sponsorBrowser || ''].filter((value: string) => !!value).join(' · ') })
        InfoLine({ title: '位置', value: this.model.recoveryTicket.sponsorIpLocation || '' })
        ActionButton({ title: '复制恢复码', secondary: true, disabled: this.remaining() <= 0 || this.model.busy, onTap: () => this.copyTicket(false) })
        ActionButton({ title: '复制恢复链接', secondary: true, disabled: this.remaining() <= 0 || this.model.busy, onTap: () => this.copyTicket(true) })
        ActionButton({ title: '打开官方恢复网页', secondary: true, disabled: this.remaining() <= 0 || this.model.busy,
          onTap: () => this.openTicket() })
      }.width('100%').padding(20).borderRadius(22).backgroundColor($r('app.color.card_background'))
    }
    ActionButton({ title: this.model.recoveryTicket ? '重新生成恢复授权' : '生成恢复授权', busy: this.action === 'recovery', disabled: !!this.action || this.model.busy,
      onTap: () => this.perform('recovery', () => this.model.issueRecoveryTicket()) })
  }

  build() {
    Column({ space: 18 }) {
      if (this.kind === 'passkeys') { this.passkeys() }
      else if (this.kind === 'totp') { this.totp() }
      else if (this.kind === 'recovery-codes') {
        this.codes(this.model.recoveryCodes)
        ActionButton({ title: '重新生成恢复码', destructive: true, busy: this.action === 'regenerate', disabled: !!this.action || this.model.busy,
          onTap: () => this.confirm('重新生成恢复码？', '旧恢复码将全部失效，请重新安全保存。', 'regenerate', () => this.model.loadRecoveryCodes(true)) })
      } else if (this.kind === 'email') {
        SectionHeader({ title: this.model.profile?.email ? '修改绑定邮箱' : '绑定邮箱', subtitle: '请先验证新邮箱，保存后用于登录与账号通知。' })
        InputField({ title: '新邮箱', text: this.email, email: true, onChange: (value: string) => { this.email = value; this.code = ''; } })
        VerificationCodeField({ model: this.model, email: this.email, type: 'change-email', code: this.code, onChange: (value: string) => { this.code = value; } })
        ActionButton({ title: '保存邮箱', busy: this.action === 'email', disabled: !validEmail(this.email) || !/^\d{6}$/.test(this.code) || !!this.action || this.model.busy,
          onTap: () => this.perform('email', () => this.model.changeEmail(this.email.trim(), this.code), true) })
      } else if (this.kind === 'password') {
        SectionHeader({ title: this.model.profile?.hasPassword === false ? '设置登录密码' : '修改登录密码' })
        ResourceFeedback({ state: this.model.passwordPolicyState, label: '密码规则', onRetry: () => this.model.loadPasswordRequirement() })
        Text(passwordRequirementText(this.model.passwordPolicy)).fontSize(13).lineHeight(22).fontColor($r('app.color.text_secondary'))
        InputField({ title: '新密码', text: this.password, secure: true, onChange: (value: string) => { this.password = value; } })
        if (this.password && passwordError(this.password, this.model.passwordPolicy)) {
          Text(passwordError(this.password, this.model.passwordPolicy)).fontSize(13).fontColor($r('app.color.danger'))
        }
        InputField({ title: '确认新密码', text: this.confirmation, secure: true, onChange: (value: string) => { this.confirmation = value; } })
        if (this.confirmation && this.password !== this.confirmation) { Text('两次输入的密码不一致').fontSize(13).fontColor($r('app.color.danger')) }
        ActionButton({ title: '保存密码', busy: this.action === 'password', disabled: !this.password || !!passwordError(this.password, this.model.passwordPolicy) || this.confirmation !== this.password || !!this.action || this.model.busy,
          onTap: () => this.perform('password', () => this.model.changePassword(this.password), true) })
      } else if (this.kind === 'recovery') { this.recovery() }
      else if (this.kind === 'delete') {
        SectionHeader({ title: '永久删除账号', subtitle: '此操作不可撤销。账号资料、会话和相关授权将被删除，请确认已保存需要的信息。' })
        Text('请输入：我真的不想要我的号辣').fontSize(14).fontColor($r('app.color.danger'))
        InputField({ title: '确认文字', text: this.confirmText, onChange: (value: string) => { this.confirmText = value; } })
        ActionButton({ title: '永久删除账号', destructive: true, busy: this.action === 'delete-account', disabled: this.confirmText !== '我真的不想要我的号辣' || !!this.action || this.model.busy,
          onTap: () => this.confirm('永久删除账号？', '账号及相关数据将永久删除，无法恢复。', 'delete-account', () => this.model.deleteAccount(this.confirmText), true) })
      }
    }.width('100%').alignItems(HorizontalAlign.Start)
  }
}
