enum AuthMode {
  LOGIN,
  REGISTER,
}

enum LoginMethod {
  PASSWORD,
  CODE,
}

enum RegisterStep {
  USERNAME,
  PASSWORD,
  PASSWORD_CONFIRM,
  EMAIL,
  CODE,
}

enum PendingAuthAction {
  NONE,
  PASSWORD_LOGIN,
  CODE_LOGIN,
  SEND_LOGIN_CODE,
  QQ_LOGIN,
  PASSKEY_LOGIN,
  QR_SCAN,
  SEND_REGISTER_CODE,
  REGISTER,
}

const BRAND_PRIMARY: ResourceColor = '#FFB90F';
const BRAND_PRIMARY_DISABLED: ResourceColor = '#99FFB90F';
const BRAND_PRIMARY_CONTAINER: ResourceColor = '#FFFFE6A6';
const BRAND_SURFACE: ResourceColor = '#FFFFFFFF';
const BRAND_SURFACE_LOW: ResourceColor = '#FFFCF6EA';
const BRAND_BACKGROUND: ResourceColor = '#FFF6F5F2';
const TEXT_PRIMARY: ResourceColor = '#FF1F1B13';
const TEXT_SECONDARY: ResourceColor = '#FF5B5344';
const TEXT_MUTED: ResourceColor = '#FF968A6F';
const BORDER_SOFT: ResourceColor = '#66D2C7AF';
const ERROR_COLOR: ResourceColor = '#FFB3261E';
const AUTH_ANIMATION: AnimateParam = {
  duration: 220,
  curve: Curve.Friction,
};

@Component
export struct KsuserLoginPage {
  loginSuccessHandler: () => void = () => {
  };

  @State authMode: AuthMode = AuthMode.LOGIN;
  @State loginMethod: LoginMethod = LoginMethod.PASSWORD;
  @State email: string = '';
  @State password: string = '';
  @State code: string = '';
  @State registerUsername: string = '';
  @State registerEmail: string = '';
  @State registerPassword: string = '';
  @State registerPasswordConfirm: string = '';
  @State registerCode: string = '';
  @State registerStep: RegisterStep = RegisterStep.USERNAME;
  @State agreementAccepted: boolean = false;
  @State showAgreementConfirm: boolean = false;
  @State pendingAction: PendingAuthAction = PendingAuthAction.NONE;
  @State statusMessage: string = '';

  build() {
    Stack() {
      Column() {
        Scroll() {
          Column({ space: 16 }) {
            this.Header()
            this.StatusBanner()
            this.AccountEntryCard()

            if (this.authMode === AuthMode.LOGIN) {
              this.LoginCard()
              this.QuickLoginCard()
            } else {
              this.RegisterCard()
            }
          }
          .width('100%')
          .padding({ left: 16, right: 16, top: 28, bottom: 20 })
        }
        .layoutWeight(1)
        .scrollable(ScrollDirection.Vertical)
        .edgeEffect(EdgeEffect.Spring)
      }
      .width('100%')
      .height('100%')
      .backgroundColor(BRAND_BACKGROUND)

      if (this.showAgreementConfirm) {
        this.AgreementConfirmPanel()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(BRAND_BACKGROUND)
  }

  @Builder
  Header() {
    Column({ space: 8 }) {
      Row() {
        Row({ space: 12 }) {
          Image($r('app.media.ksuser_launcher_icon'))
            .width(34)
            .height(34)
            .borderRadius(8)
            .padding(2)
            .backgroundColor(BRAND_SURFACE)
            .objectFit(ImageFit.Contain)

          Text('登录')
            .fontSize(28)
            .fontWeight(FontWeight.Bold)
            .fontColor(TEXT_PRIMARY)
        }
        .alignItems(VerticalAlign.Center)

        Button({ type: ButtonType.Normal, stateEffect: true }) {
          SymbolGlyph($r('sys.symbol.qrcode'))
            .fontSize(20)
            .fontColor([TEXT_PRIMARY])
        }
        .width(54)
        .height(42)
        .borderRadius(12)
        .backgroundColor(BRAND_SURFACE)
        .border({ width: 1, color: BORDER_SOFT })
        .onClick(() => {
          this.runWithAgreement(PendingAuthAction.QR_SCAN);
        })
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      .alignItems(VerticalAlign.Center)

      Text(this.subtitleText())
        .fontSize(14)
        .lineHeight(21)
        .fontColor(TEXT_SECONDARY)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  StatusBanner() {
    if (this.statusMessage.length > 0) {
      Row() {
        Text(this.statusMessage)
          .fontSize(13)
          .lineHeight(19)
          .fontColor(TEXT_SECONDARY)
      }
      .width('100%')
      .padding(12)
      .borderRadius(14)
      .backgroundColor('#FFFFF6E6')
      .border({ width: 1, color: '#44D2C7AF' })
      .animation(AUTH_ANIMATION)
    }
  }

  @Builder
  AccountEntryCard() {
    Column({ space: 10 }) {
      Text('账号入口')
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor(TEXT_PRIMARY)

      Row({ space: 8 }) {
        AuthChip({
          titleText: '登录',
          isActive: this.authMode === AuthMode.LOGIN,
          tapHandler: () => {
            this.authMode = AuthMode.LOGIN;
            this.statusMessage = '';
          },
        })
        AuthChip({
          titleText: '注册',
          isActive: this.authMode === AuthMode.REGISTER,
          tapHandler: () => {
            this.authMode = AuthMode.REGISTER;
            this.statusMessage = '';
          },
        })
      }
      .justifyContent(FlexAlign.Start)
    }
    .width('100%')
    .padding(12)
    .borderRadius(16)
    .backgroundColor(BRAND_SURFACE)
    .border({ width: 1, color: BORDER_SOFT })
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 3 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  LoginCard() {
    Column({ space: 12 }) {
      Row({ space: 8 }) {
        AuthChip({
          titleText: '密码登录',
          isActive: this.loginMethod === LoginMethod.PASSWORD,
          tapHandler: () => {
            this.loginMethod = LoginMethod.PASSWORD;
          },
        })
        AuthChip({
          titleText: '验证码登录',
          isActive: this.loginMethod === LoginMethod.CODE,
          tapHandler: () => {
            this.loginMethod = LoginMethod.CODE;
          },
        })
      }
      .justifyContent(FlexAlign.Start)

      AuthLineInput({ textValue: $email, labelText: '邮箱' })

      if (this.loginMethod === LoginMethod.PASSWORD) {
        AuthLineInput({ textValue: $password, labelText: '密码', isSecret: true })
        AuthPrimaryButton({
          titleText: '继续',
          buttonEnabled: this.canPasswordLogin(),
          tapHandler: () => {
            this.runWithAgreement(PendingAuthAction.PASSWORD_LOGIN);
          },
        })
      } else {
        Row({ space: 8 }) {
          AuthLineInput({ textValue: $code, labelText: '验证码' })
            .layoutWeight(1)
          AuthOutlineButton({
            titleText: '发送',
            buttonEnabled: this.email.trim().length > 0,
            tapHandler: () => {
              this.runWithAgreement(PendingAuthAction.SEND_LOGIN_CODE);
            },
          })
            .width(88)
            .height(56)
        }
        .alignItems(VerticalAlign.Bottom)

        AuthPrimaryButton({
          titleText: '继续',
          buttonEnabled: this.canCodeLogin(),
          tapHandler: () => {
            this.runWithAgreement(PendingAuthAction.CODE_LOGIN);
          },
        })
      }

      this.AgreementNotice()
    }
    .width('100%')
    .padding(12)
    .borderRadius(16)
    .backgroundColor(BRAND_SURFACE)
    .border({ width: 1, color: BORDER_SOFT })
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 3 })
    .animation(AUTH_ANIMATION)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  QuickLoginCard() {
    Column({ space: 10 }) {
      Text('快捷登录')
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor(TEXT_PRIMARY)

      AuthOutlineButton({
        titleText: '使用 QQ 登录',
        buttonEnabled: true,
        leadingSymbol: '',
        tapHandler: () => {
          this.runWithAgreement(PendingAuthAction.QQ_LOGIN);
        },
      })

      Text('使用通行密钥(Passkey)登录')
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor(TEXT_PRIMARY)
        .margin({ top: 2 })

      AuthOutlineButton({
        titleText: '使用通行密钥(Passkey)登录',
        buttonEnabled: true,
        leadingSymbol: 'key',
        tapHandler: () => {
          this.runWithAgreement(PendingAuthAction.PASSKEY_LOGIN);
        },
      })
    }
    .width('100%')
    .padding(12)
    .borderRadius(16)
    .backgroundColor(BRAND_SURFACE)
    .border({ width: 1, color: BORDER_SOFT })
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 3 })
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  RegisterCard() {
    Column({ space: 12 }) {
      RegisterStepHeader({ stepValue: this.registerStep })

      if (this.registerStep === RegisterStep.USERNAME) {
        Text('3-20 个字符，支持中文、字母、数字、下划线和连字符')
          .fontSize(12)
          .lineHeight(18)
          .fontColor(TEXT_SECONDARY)
        AuthLineInput({ textValue: $registerUsername, labelText: '用户名' })
        AuthPrimaryButton({
          titleText: '下一步',
          buttonEnabled: this.isRegisterUsernameValid(),
          tapHandler: () => {
            this.registerStep = RegisterStep.PASSWORD;
          },
        })
      } else if (this.registerStep === RegisterStep.PASSWORD) {
        Text('请设置至少 8 位密码，建议包含大小写字母、数字和符号。')
          .fontSize(12)
          .lineHeight(18)
          .fontColor(TEXT_SECONDARY)
        AuthLineInput({ textValue: $registerPassword, labelText: '密码', isSecret: true })
        Row({ space: 8 }) {
          AuthOutlineButton({
            titleText: '返回',
            buttonEnabled: true,
            tapHandler: () => {
              this.registerStep = RegisterStep.USERNAME;
            },
          })
            .layoutWeight(1)
          AuthPrimaryButton({
            titleText: '下一步',
            buttonEnabled: this.registerPassword.length >= 8,
            tapHandler: () => {
              this.registerStep = RegisterStep.PASSWORD_CONFIRM;
            },
          })
            .layoutWeight(1)
        }
      } else if (this.registerStep === RegisterStep.PASSWORD_CONFIRM) {
        Text('请再次输入密码以确认')
          .fontSize(12)
          .lineHeight(18)
          .fontColor(TEXT_SECONDARY)
        AuthLineInput({ textValue: $registerPasswordConfirm, labelText: '确认密码', isSecret: true })
        if (this.registerPasswordConfirm.length > 0 && this.registerPassword !== this.registerPasswordConfirm) {
          Text('两次输入的密码不一致')
            .fontSize(12)
            .fontColor(ERROR_COLOR)
        }
        Row({ space: 8 }) {
          AuthOutlineButton({
            titleText: '返回',
            buttonEnabled: true,
            tapHandler: () => {
              this.registerStep = RegisterStep.PASSWORD;
            },
          })
            .layoutWeight(1)
          AuthPrimaryButton({
            titleText: '下一步',
            buttonEnabled: this.canConfirmPassword(),
            tapHandler: () => {
              this.registerStep = RegisterStep.EMAIL;
            },
          })
            .layoutWeight(1)
        }
      } else if (this.registerStep === RegisterStep.EMAIL) {
        Text('用于接收验证码和系统通知')
          .fontSize(12)
          .lineHeight(18)
          .fontColor(TEXT_SECONDARY)
        AuthLineInput({ textValue: $registerEmail, labelText: '邮箱' })
        Row({ space: 8 }) {
          AuthOutlineButton({
            titleText: '返回',
            buttonEnabled: true,
            tapHandler: () => {
              this.registerStep = RegisterStep.PASSWORD_CONFIRM;
            },
          })
            .layoutWeight(1)
          AuthPrimaryButton({
            titleText: '发送验证码',
            buttonEnabled: this.isValidEmail(this.registerEmail),
            tapHandler: () => {
              this.runWithAgreement(PendingAuthAction.SEND_REGISTER_CODE);
            },
          })
            .layoutWeight(1)
        }
      } else {
        Text(`验证码已发送至 ${this.registerEmail.length > 0 ? this.registerEmail : '你的邮箱'}`)
          .fontSize(12)
          .lineHeight(18)
          .fontColor(TEXT_SECONDARY)
        AuthLineInput({ textValue: $registerCode, labelText: '验证码' })
        Row({ space: 8 }) {
          AuthOutlineButton({
            titleText: '返回',
            buttonEnabled: true,
            tapHandler: () => {
              this.registerStep = RegisterStep.EMAIL;
            },
          })
            .layoutWeight(1)
          AuthPrimaryButton({
            titleText: '创建账号',
            buttonEnabled: this.canRegister(),
            tapHandler: () => {
              this.runWithAgreement(PendingAuthAction.REGISTER);
            },
          })
            .layoutWeight(1)
        }
      }

      Text('已有账号，返回登录')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(BRAND_PRIMARY)
        .width('100%')
        .textAlign(TextAlign.Center)
        .padding({ top: 4, bottom: 4 })
        .onClick(() => {
          this.authMode = AuthMode.LOGIN;
          this.statusMessage = '';
        })

      this.AgreementNotice()
    }
    .width('100%')
    .padding(12)
    .borderRadius(16)
    .backgroundColor(BRAND_SURFACE)
    .border({ width: 1, color: BORDER_SOFT })
    .shadow({ radius: 10, color: '#14000000', offsetX: 0, offsetY: 3 })
    .animation(AUTH_ANIMATION)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  AgreementNotice() {
    Text('使用登录、注册或扫码等功能即代表您同意服务协议与隐私政策\n使用第三方登录，即代表您已阅读并同意第三方信息共享清单')
      .fontSize(12)
      .lineHeight(18)
      .fontColor(TEXT_SECONDARY)
  }

  @Builder
  AgreementConfirmPanel() {
    Column() {
      Column({ space: 12 }) {
        Text('请先阅读并同意')
          .fontSize(19)
          .fontWeight(FontWeight.Bold)
          .fontColor(TEXT_PRIMARY)

        Text('登录或注册即代表您同意服务协议与隐私政策')
          .fontSize(14)
          .lineHeight(20)
          .fontColor(TEXT_SECONDARY)

        Text('使用第三方登录，即代表您已阅读并同意第三方信息共享清单')
          .fontSize(14)
          .lineHeight(20)
          .fontColor(TEXT_SECONDARY)

        Row({ space: 10 }) {
          AuthOutlineButton({
            titleText: '取消',
            buttonEnabled: true,
            tapHandler: () => {
              this.showAgreementConfirm = false;
              this.pendingAction = PendingAuthAction.NONE;
            },
          })
            .layoutWeight(1)
          AuthPrimaryButton({
            titleText: '同意并继续',
            buttonEnabled: true,
            tapHandler: () => {
              const action = this.pendingAction;
              this.agreementAccepted = true;
              this.showAgreementConfirm = false;
              this.pendingAction = PendingAuthAction.NONE;
              this.performAction(action);
            },
          })
            .layoutWeight(1)
        }
        .margin({ top: 4 })
      }
      .width('88%')
      .padding(18)
      .borderRadius(20)
      .backgroundColor(BRAND_SURFACE)
      .shadow({ radius: 24, color: '#33000000', offsetX: 0, offsetY: 10 })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor('#66000000')
  }

  private subtitleText(): string {
    if (this.authMode === AuthMode.REGISTER) {
      return '创建 Ksuser 账号，完成邮箱验证后即可登录。';
    }
    return '使用你的 Ksuser 账号继续，或创建一个新账号。';
  }

  private runWithAgreement(action: PendingAuthAction): void {
    if (this.agreementAccepted) {
      this.performAction(action);
      return;
    }
    this.pendingAction = action;
    this.showAgreementConfirm = true;
  }

  private performAction(action: PendingAuthAction): void {
    switch (action) {
      case PendingAuthAction.PASSWORD_LOGIN:
        if (!this.canPasswordLogin()) {
          this.statusMessage = '请先填写邮箱和密码。';
          return;
        }
        this.loginSuccessHandler();
        return;
      case PendingAuthAction.CODE_LOGIN:
        if (!this.canCodeLogin()) {
          this.statusMessage = '请先填写邮箱和验证码。';
          return;
        }
        this.loginSuccessHandler();
        return;
      case PendingAuthAction.SEND_LOGIN_CODE:
        this.statusMessage = '验证码登录入口已就绪，后续可接入发送验证码接口。';
        return;
      case PendingAuthAction.QQ_LOGIN:
        this.statusMessage = 'QQ 登录入口已就绪，后续可接入 Android 同款 OAuth 流程。';
        return;
      case PendingAuthAction.PASSKEY_LOGIN:
        this.statusMessage = 'Passkey 登录入口已就绪，后续可接入系统通行密钥能力。';
        return;
      case PendingAuthAction.QR_SCAN:
        this.statusMessage = '扫码登录/授权入口已就绪，后续可接入相机权限与二维码识别。';
        return;
      case PendingAuthAction.SEND_REGISTER_CODE:
        if (!this.isValidEmail(this.registerEmail)) {
          this.statusMessage = '请先填写有效邮箱。';
          return;
        }
        this.registerStep = RegisterStep.CODE;
        this.statusMessage = '注册验证码入口已就绪，后续可接入发送验证码接口。';
        return;
      case PendingAuthAction.REGISTER:
        if (!this.canRegister()) {
          this.statusMessage = '请完整填写注册信息。';
          return;
        }
        this.loginSuccessHandler();
        return;
      default:
        return;
    }
  }

  private canPasswordLogin(): boolean {
    return this.email.trim().length > 0 && this.password.length > 0;
  }

  private canCodeLogin(): boolean {
    return this.email.trim().length > 0 && this.code.trim().length > 0;
  }

  private isRegisterUsernameValid(): boolean {
    const username = this.registerUsername.trim();
    return username.length >= 3 && username.length <= 20;
  }

  private canConfirmPassword(): boolean {
    return this.registerPasswordConfirm.length > 0 && this.registerPassword === this.registerPasswordConfirm;
  }

  private canRegister(): boolean {
    return this.isRegisterUsernameValid() &&
      this.isValidEmail(this.registerEmail) &&
      this.registerPassword.length >= 8 &&
      this.registerPassword === this.registerPasswordConfirm &&
      this.registerCode.trim().length > 0;
  }

  private isValidEmail(value: string): boolean {
    const email = value.trim();
    const atIndex = email.indexOf('@');
    const dotIndex = email.lastIndexOf('.');
    return email.length > 5 && atIndex > 0 && dotIndex > atIndex + 1 && dotIndex < email.length - 1;
  }
}

@Component
struct AuthChip {
  titleText: string = '';
  @Prop isActive: boolean = false;
  tapHandler: () => void = () => {
  };

  build() {
    Row() {
      Text(this.titleText)
        .fontSize(13)
        .fontWeight(this.isActive ? FontWeight.Medium : FontWeight.Regular)
        .fontColor(this.isActive ? TEXT_PRIMARY : TEXT_SECONDARY)
    }
    .height(34)
    .padding({ left: 14, right: 14 })
    .borderRadius(17)
    .backgroundColor(this.isActive ? BRAND_PRIMARY_CONTAINER : BRAND_SURFACE_LOW)
    .border({ width: 1, color: this.isActive ? BRAND_PRIMARY : BORDER_SOFT })
    .justifyContent(FlexAlign.Center)
    .alignItems(VerticalAlign.Center)
    .scale({ x: this.isActive ? 1.03 : 1, y: this.isActive ? 1.03 : 1 })
    .animation(AUTH_ANIMATION)
    .onClick(() => {
      this.tapHandler();
    })
  }
}

@Component
struct AuthLineInput {
  @Link textValue: string;
  labelText: string = '';
  @Prop isSecret: boolean = false;

  build() {
    Column({ space: 4 }) {
      TextInput({ placeholder: this.labelText, text: this.textValue })
        .height(48)
        .width('100%')
        .fontSize(16)
        .fontColor(TEXT_PRIMARY)
        .placeholderColor(TEXT_MUTED)
        .backgroundColor('#00FFFFFF')
        .borderRadius(0)
        .padding({ left: 0, right: 0, top: 0, bottom: 0 })
        .border({ width: { bottom: 1 }, color: BORDER_SOFT })
        .textAlign(TextAlign.Start)
        .type(this.isSecret ? InputType.Password : InputType.Normal)
        .onChange((value: string) => {
          this.textValue = value;
        })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

@Component
struct AuthPrimaryButton {
  titleText: string = '';
  @Prop buttonEnabled: boolean = true;
  tapHandler: () => void = () => {
  };

  build() {
    Button({ type: ButtonType.Normal, stateEffect: true }) {
      Text(this.titleText)
        .fontSize(15)
        .fontWeight(FontWeight.Medium)
        .fontColor(TEXT_PRIMARY)
    }
    .width('100%')
    .height(46)
    .enabled(this.buttonEnabled)
    .borderRadius(12)
    .backgroundColor(this.buttonEnabled ? BRAND_PRIMARY : BRAND_PRIMARY_DISABLED)
    .onClick(() => {
      if (this.buttonEnabled) {
        this.tapHandler();
      }
    })
  }
}

@Component
struct AuthOutlineButton {
  titleText: string = '';
  leadingSymbol: string = '';
  @Prop buttonEnabled: boolean = true;
  tapHandler: () => void = () => {
  };

  build() {
    Button({ type: ButtonType.Normal, stateEffect: true }) {
      Row({ space: 8 }) {
        if (this.leadingSymbol === 'key') {
          SymbolGlyph($r('sys.symbol.key'))
            .fontSize(18)
            .fontColor([this.buttonEnabled ? TEXT_PRIMARY : TEXT_MUTED])
        }

        Text(this.titleText)
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.buttonEnabled ? TEXT_PRIMARY : TEXT_MUTED)
      }
      .width('100%')
      .justifyContent(FlexAlign.Center)
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .height(46)
    .enabled(this.buttonEnabled)
    .borderRadius(12)
    .backgroundColor(BRAND_SURFACE)
    .border({ width: 1, color: this.buttonEnabled ? BORDER_SOFT : '#33D2C7AF' })
    .onClick(() => {
      if (this.buttonEnabled) {
        this.tapHandler();
      }
    })
  }
}

@Component
struct RegisterStepHeader {
  @Prop stepValue: RegisterStep = RegisterStep.USERNAME;

  build() {
    Column({ space: 3 }) {
      Text(this.stepTitle())
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor(TEXT_PRIMARY)

      Text(`${this.stepIndex()} / 5`)
        .fontSize(12)
        .fontColor(TEXT_SECONDARY)
    }
    .width('100%')
  }

  private stepTitle(): string {
    switch (this.stepValue) {
      case RegisterStep.USERNAME:
        return '选择用户名';
      case RegisterStep.PASSWORD:
        return '设置密码';
      case RegisterStep.PASSWORD_CONFIRM:
        return '确认密码';
      case RegisterStep.EMAIL:
        return '输入邮箱';
      case RegisterStep.CODE:
        return '输入验证码';
      default:
        return '选择用户名';
    }
  }

  private stepIndex(): number {
    switch (this.stepValue) {
      case RegisterStep.USERNAME:
        return 1;
      case RegisterStep.PASSWORD:
        return 2;
      case RegisterStep.PASSWORD_CONFIRM:
        return 3;
      case RegisterStep.EMAIL:
        return 4;
      case RegisterStep.CODE:
        return 5;
      default:
        return 1;
    }
  }
}
