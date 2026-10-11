// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get ksuserSecurity => 'Ksuser安全';

  @override
  String get dataRefreshed => '数据刷新完成';

  @override
  String get signInPageRefreshed => '登录页数据已刷新';

  @override
  String get checkingTheTerms => '正在检查使用协议...';

  @override
  String get beforeYouBegin => '开始使用前请确认';

  @override
  String get beforeInstallingAndUsingThisAppFor =>
      '首次安装并使用本应用前，请阅读并同意以下协议和清单。未同意前将无法继续登录或使用桌面端功能。';

  @override
  String get termsOfService => '服务条款';

  @override
  String get privacyPolicy => '隐私政策';

  @override
  String get thirdPartyDataSharingDisclosure => '第三方信息共享清单';

  @override
  String get iHaveReadAndAgreeToThe => '我已阅读并同意以上服务条款、隐私政策和第三方信息共享清单';

  @override
  String get saving => '正在保存...';

  @override
  String get agreeAndContinue => '同意并继续';

  @override
  String get minimizeToSystemTray => '收起到托盘';

  @override
  String get minimizeToMenuBar => '收起到菜单栏';

  @override
  String get couldNotMinimizeTheWindow => '窗口收起失败';

  @override
  String get thisDeviceDoesNotSupportLocalAuthentication => '当前设备不支持系统本地认证';

  @override
  String get verifyYourIdentityBeforeDisplayingTheMobile =>
      '请先完成身份验证后再展示手机登录二维码';

  @override
  String get mobileQrSignIn => '手机扫码登录';

  @override
  String get generatingQrCode => '正在生成二维码...';

  @override
  String expiresInSeconds(String arg0) {
    return '剩余有效期：$arg0 秒';
  }

  @override
  String get thisQrCodeCanOnlyBeUsed => '二维码一次性有效，过期后请刷新。';

  @override
  String get close => '关闭';

  @override
  String get refreshQrCode => '刷新二维码';

  @override
  String get accountRecoveryAuthorization => '账号恢复授权';

  @override
  String get generatingRecoveryQrCode => '正在生成恢复二维码...';

  @override
  String authorizingDevice(String arg0) {
    return '背书设备：$arg0';
  }

  @override
  String get thisDesktopApp => '当前桌面端';

  @override
  String location(String arg0) {
    return '位置：$arg0';
  }

  @override
  String get unknownLocation => '未知位置';

  @override
  String get generatingRecoveryCode => '恢复码生成中...';

  @override
  String get openTheRecoveryPageOnAnotherDevice =>
      '请在另一台设备打开网页恢复页后扫码，或手动输入上面的恢复码。恢复成功后旧会话会自动失效。';

  @override
  String get recoveryCodeCopied => '恢复码已复制';

  @override
  String get copyRecoveryCode => '复制恢复码';

  @override
  String get openRecoveryPage => '打开网页恢复页';

  @override
  String get refreshing => '刷新中...';

  @override
  String get refreshAuthorization => '刷新授权';

  @override
  String get invalidQrSignInResultPleaseTry => '扫码登录结果无效，请重试';

  @override
  String get qrRequestRejectedRefreshTheQrCode => '扫码请求已被拒绝，请刷新二维码后重试';

  @override
  String get qrCodeExpiredPleaseRefreshIt => '二维码已过期，请刷新二维码';

  @override
  String get qrSignIn => '二维码登录';

  @override
  String get scanWithASignedInMobileApp => '请使用已登录的手机端扫描二维码，授权当前桌面端登录。';

  @override
  String get ifMfaIsEnabledCompleteTheRemaining =>
      '若账号开启了 MFA，扫码后还需在桌面端完成其他 MFA 验证。';

  @override
  String version(String arg0, String arg1) {
    return '版本 $arg0 ($arg1)';
  }

  @override
  String get settings => '设置';

  @override
  String get appearance => '外观';

  @override
  String get followSystem => '跟随系统';

  @override
  String get light => '浅色';

  @override
  String get dark => '深色';

  @override
  String get localPreferences => '本地调试';

  @override
  String get compactLayout => '紧凑布局';

  @override
  String get reduceSpacingToDisplayMoreContentOn => '压缩控件间距，便于桌面联调时查看更多内容';

  @override
  String get reduceMotion => '减少动画';

  @override
  String get reduceAnimationsWhenChangingThemesAndOpening => '切换主题和弹窗时尽量减少动画效果';

  @override
  String get about => '关于';

  @override
  String get privacyPolicy2 => '隐私协议';

  @override
  String get restoreDefaults => '恢复默认';

  @override
  String get done => '完成';

  @override
  String get desktopUnifiedAuthenticationClient => '桌面统一认证客户端';

  @override
  String theme(String arg0) {
    return '主题模式：$arg0';
  }

  @override
  String localPreferences2(String arg0, String arg1) {
    return '本地调试：$arg0$arg1';
  }

  @override
  String get compactLayout2 => '紧凑布局，';

  @override
  String get standardAnimations => '标准动画';

  @override
  String get system => '系统';

  @override
  String get verification => '验证';

  @override
  String useTheNativePasskeyServiceFor(String arg0, String arg1) {
    return '将直接调用 $arg0 系统原生 Passkey 完成$arg1。';
  }

  @override
  String nativePasskeysAreNotEnabledOnThis(String arg0) {
    return '当前设备未启用 $arg0 原生 Passkey。';
  }

  @override
  String get nativePasskeysAreNotSupportedOnThis => '当前平台暂不支持原生 Passkey';

  @override
  String get noPasskeyCredentialWasReceived => '未获取到 Passkey 凭证';

  @override
  String get passkeyVerificationFailed => 'Passkey 验证失败';

  @override
  String get noPasskeyCredentialWasCreated => '未创建 Passkey 凭证';

  @override
  String get couldNotCreatePasskey => 'Passkey 创建失败';

  @override
  String get signInWithAppleIsOnlyAvailable => 'Apple 登录仅在 macOS 上提供';

  @override
  String get noMacosSignInWithAppleConfiguration => '未获取到 macOS Apple 登录配置';

  @override
  String get couldNotReadTheSignInWith => '无法读取 Apple 登录配置';

  @override
  String get appleDidNotReturnCompleteSignIn => 'Apple 未返回完整的登录凭据';

  @override
  String get signInWithAppleCanceled => '已取消 Apple 登录';

  @override
  String get signInWithAppleFailedPleaseTry => 'Apple 登录失败，请重试';

  @override
  String get localAuthenticationIsNotSupportedOnThis => '当前平台暂不支持系统本地认证';

  @override
  String get localAuthenticationFailed => '本地认证未通过';

  @override
  String get systemAuthenticationCanceled => '用户取消了系统身份验证';

  @override
  String get windowsHelloPinOrAnotherSystemAuthentication =>
      '当前设备未启用 Windows Hello、PIN 或其他系统身份验证方式';

  @override
  String get windowsAuthenticationFailed => 'Windows 系统身份验证失败';

  @override
  String get localAuthenticationCanceled => '用户取消了本地认证';

  @override
  String get localAuthenticationFailed2 => '本地认证失败';

  @override
  String get processingAuthentication => '正在处理认证请求...';

  @override
  String get syncingDesktopData => '正在同步桌面数据...';

  @override
  String get processing => '正在处理中...';

  @override
  String get couldNotLoadAuthorizedAppsPleaseTry => '加载已授权应用失败，请稍后重试';

  @override
  String get signingIn => '正在登录...';

  @override
  String get sendingSignInCode => '正在发送登录验证码...';

  @override
  String get signingInWithApple => '正在使用 Apple 登录...';

  @override
  String get incompleteAppleSignInChallengePleaseTry => 'Apple 登录挑战信息不完整，请重试';

  @override
  String get linkYourAccountOnTheWebOr => '需前往网页端或移动端绑定账号之后才可以使用该第三方登录';

  @override
  String get verifyingAdditionalAuthentication => '正在验证二次认证...';

  @override
  String get waitingForPasskeyMfaVerification => '正在等待 Passkey 完成二次验证...';

  @override
  String get theBrowserDidNotReturnASign => '浏览器未返回登录结果';

  @override
  String get loadingPasswordRequirements => '正在加载密码规则...';

  @override
  String get sendingRegistrationCode => '正在发送注册验证码...';

  @override
  String get creatingAccount => '正在创建账户...';

  @override
  String get couldNotLoadDesktopData => '加载桌面数据失败';

  @override
  String get refreshingActivityLogs => '正在刷新敏感日志...';

  @override
  String get refreshingOnlineDevices => '正在刷新在线设备...';

  @override
  String get refreshingPasskeys => '正在刷新 Passkey 列表...';

  @override
  String get refreshingTotpStatus => '正在刷新 TOTP 状态...';

  @override
  String get refreshingContinuousAuthenticationStatus => '正在刷新连续认证状态...';

  @override
  String get savingSettings => '正在保存设置...';

  @override
  String get savingProfile => '正在保存资料...';

  @override
  String get uploadingAvatar => '正在上传头像...';

  @override
  String get checkingVerificationStatus => '正在检查验证状态...';

  @override
  String get checkingContinuousAuthenticationStatus => '正在检查连续认证状态...';

  @override
  String get sendingVerificationEmail => '正在发送验证邮件...';

  @override
  String get verifyingIdentity => '正在验证身份...';

  @override
  String get noUsableBrowserPasskeyBridgeUrlIs => '当前环境未配置可用的 Passkey 浏览器桥接地址';

  @override
  String get passkeysAreNotSupportedOnThisPlatform => '当前平台暂不支持 Passkey';

  @override
  String get waitingForPasskeySignIn => '正在等待 Passkey 登录...';

  @override
  String get waitingForPasskeyVerification => '正在等待 Passkey 验证...';

  @override
  String get theBrowserDidNotCompleteVerificationFor => '浏览器未完成敏感验证';

  @override
  String get waitingForPasskeyRegistration => '正在等待 Passkey 登记...';

  @override
  String get theBrowserDidNotCompletePasskeyRegistration => '浏览器未完成 Passkey 登记';

  @override
  String get thisEnvironmentPreventsALocalCallbackPort =>
      '当前环境禁止创建本地回调端口，无法使用浏览器 Passkey 桥接。';

  @override
  String get checkNetworkInboundPermissionsOrEnableNative =>
      '请检查系统网络入站权限，或在 macOS 上启用原生 Passkey。';

  @override
  String couldNotCreateTheLocalPasskeyCallback(String arg0) {
    return '无法创建本地 Passkey 回调服务：$arg0';
  }

  @override
  String get sendingEmailVerificationCode => '正在发送邮箱验证码...';

  @override
  String get updatingEmail => '正在更新邮箱...';

  @override
  String get updatingPassword => '正在更新密码...';

  @override
  String get revokingSession => '正在撤销会话...';

  @override
  String get signingOutAllDevices => '正在退出所有设备...';

  @override
  String get renamingPasskey => '正在重命名 Passkey...';

  @override
  String get deletingPasskey => '正在删除 Passkey...';

  @override
  String get gettingTotpConfiguration => '正在获取 TOTP 配置...';

  @override
  String get enablingTotp => '正在启用 TOTP...';

  @override
  String get loadingRecoveryCodes => '正在加载恢复码...';

  @override
  String get generatingNewRecoveryCodes => '正在生成新的恢复码...';

  @override
  String get disablingTotp => '正在关闭 TOTP...';

  @override
  String get signingOut => '正在退出登录...';

  @override
  String get generatingCrossDeviceSignInTicket => '正在生成跨端登录票据...';

  @override
  String get thisDesktopAppIsNotSignedIn => '当前桌面端尚未登录';

  @override
  String get generatingAccountRecoveryAuthorization => '正在生成账号恢复授权...';

  @override
  String get syncingWebSignInStatus => '正在同步网页登录状态...';

  @override
  String get crossDeviceSignInFailedNoAccess => '跨端登录失败，未获取到 accessToken';

  @override
  String networkConnectionFailed(String arg0) {
    return '网络连接失败：$arg0';
  }

  @override
  String avatarUploadFailed(String arg0) {
    return '头像上传失败：$arg0';
  }

  @override
  String couldNotConnectTo(String arg0, String arg1) {
    return '无法连接到 $arg0：$arg1';
  }

  @override
  String get tlsHandshakeFailedPleaseCheckTheServer => 'TLS 握手失败，请检查服务证书';

  @override
  String get requestTimedOutPleaseTryAgainLater => '请求超时，请稍后重试';

  @override
  String get theServerReturnedUnreadableData => '服务返回了无法解析的数据';

  @override
  String get authenticationExpiredPleaseSignInAgain => '认证已失效，请重新登录';

  @override
  String get couldNotRefreshTheToken => '刷新 Token 失败';

  @override
  String get enterYourEmailAddressFirst => '请先输入邮箱地址';

  @override
  String get enterYourPassword => '请输入密码';

  @override
  String get enterADigitVerificationCode => '请输入 6 位验证码';

  @override
  String get signedIn => '登录成功';

  @override
  String get firstVerificationCompletedContinueWithAdditionalVerification =>
      '第一步验证成功，请继续完成二次验证';

  @override
  String get verificationCodeSent => '验证码已发送';

  @override
  String get additionalVerificationCompleted => '二次验证通过';

  @override
  String get enterADigitAuthenticatorCode => '请输入 6 位动态码';

  @override
  String get enterARecoveryCode => '请输入恢复码';

  @override
  String get signedInWithPasskey => 'Passkey 登录成功';

  @override
  String get passkeyVerifiedContinueWithAdditionalVerification =>
      'Passkey 验证成功，请继续完成二次验证';

  @override
  String get signedInWithApple => 'Apple 登录成功';

  @override
  String get appleVerifiedContinueWithAdditionalVerification =>
      'Apple 验证成功，请继续完成二次验证';

  @override
  String get signedInWithQrCode => '扫码登录成功';

  @override
  String get qrCodeVerifiedContinueWithAdditionalVerification =>
      '扫码验证成功，请继续完成二次验证';

  @override
  String get signIn => '登录';

  @override
  String get signInToKsuserSecurityToManage => '登录 Ksuser 安全中心，继续管理你的账户与设备。';

  @override
  String get password => '密码';

  @override
  String get emailCode => '邮箱验证码';

  @override
  String get emailAddress => '邮箱地址';

  @override
  String get digitEmailCode => '6 位邮箱验证码';

  @override
  String get sendCode => '发送验证码';

  @override
  String get otherSignInMethods => '其他登录方式';

  @override
  String get signInWithApple => '使用 Apple 登录';

  @override
  String get qrSignIn2 => '扫码登录';

  @override
  String get newToKsuserRegisterOnTheWeb => '还没有账号？前往网页端注册';

  @override
  String get accountSecurityConsole => '账户安全控制台';

  @override
  String get workspace => '工作台';

  @override
  String get accountOverview => '账号总览';

  @override
  String get accountProfile => '账号资料';

  @override
  String get securitySettings => '安全设置';

  @override
  String get deviceManagement => '设备管理';

  @override
  String get activityLogs => '操作日志';

  @override
  String get accessAuthorization => '访问授权';

  @override
  String get crossDeviceAccess => '跨端联通';

  @override
  String get openTheWebAppAndSignIn => '打开网页端并自动登录';

  @override
  String get openingTheWebApp => '正在打开网页端...';

  @override
  String get webAppOpenedAndSignedInAutomatically => '已在浏览器打开网页端并自动登录';

  @override
  String get syncWebSignInToDesktop => '通过网页登录同步回桌面端';

  @override
  String get openingWebSignIn => '正在打开网页登录页...';

  @override
  String get webSignInOpenedYourSessionWill => '已打开网页登录页；网页登录成功后会自动同步回桌面端';

  @override
  String get openWebApp => '打开网页端';

  @override
  String get syncWebSignIn => '网页登录同步';

  @override
  String get accountActions => '账户操作';

  @override
  String get signOut => '退出登录';

  @override
  String get desktopDataRefreshed => '桌面数据已刷新';

  @override
  String get refresh => '刷新';

  @override
  String get profileSummary => '资料摘要';

  @override
  String get yourAccountAtAGlance => '账户资料一览';

  @override
  String get fullName => '真实姓名';

  @override
  String get notProvided => '未填写';

  @override
  String get region => '地区';

  @override
  String get gender => '性别';

  @override
  String get profileUpdated => '资料更新时间';

  @override
  String get signInMethods => '登录方式';

  @override
  String get availableAuthenticationMethods => '当前可用的认证方式';

  @override
  String get recentDeviceActivity => '近期设备活动';

  @override
  String get recentDevicesAndSessions => '最近登录的设备与会话';

  @override
  String get noDeviceActivityYet => '暂无设备活动';

  @override
  String get recentSensitiveActions => '近期敏感操作';

  @override
  String get recentAccountSecurityActivity => '账户安全相关的最近记录';

  @override
  String get noSensitiveActionsYet => '暂无敏感操作记录';

  @override
  String get accountIdentity => '账户身份';

  @override
  String get securityScore => '安全评分';

  @override
  String get totpProtectionEnabled => '已启用 TOTP 防护';

  @override
  String get enableTotpProtection => '建议开启 TOTP 防护';

  @override
  String get onlineDevices => '在线设备';

  @override
  String devicesOnline(int arg0) {
    return '$arg0 台当前在线';
  }

  @override
  String get activityLogs2 => '敏感日志';

  @override
  String get recentAccountSecurityActions => '近期账户安全操作';

  @override
  String get authorizedApps => '已授权应用';

  @override
  String get viewThirdPartyAppsThatCanAccess => '查看可访问您账号信息的第三方应用及授权范围。';

  @override
  String apps(int arg0) {
    return '$arg0 个应用';
  }

  @override
  String get authorizationInformationIsCurrentlyUnavailable => '暂时无法获取授权信息';

  @override
  String get retry => '重试';

  @override
  String get noAuthorizedAppsYet => '还没有授权应用';

  @override
  String get thirdPartyAppsYouAuthorizeWillAppear => '您确认授权的第三方应用会显示在这里。';

  @override
  String get aboutAuthorization => '授权说明';

  @override
  String get youCanManageThirdPartyAppsOn => '您可以随时在网页端访问授权页面管理第三方应用。';

  @override
  String get afterYouRevokeAccessTheAppCan => '撤销授权后，该应用将无法继续使用现有授权访问您的账户信息。';

  @override
  String revokeAccessFor(String arg0) {
    return '取消「$arg0」的授权？';
  }

  @override
  String get theAppWillNoLongerBeAble =>
      '取消后，该应用将无法继续访问您已授权的账号信息。若再次使用，需要重新确认授权。';

  @override
  String get keepAccess => '保留授权';

  @override
  String get revokeAccess => '确认取消';

  @override
  String accessRevokedFor(String arg0) {
    return '已取消「$arg0」的授权';
  }

  @override
  String get oneTimeAccess => '单次授权';

  @override
  String get noExpiration => '长期有效';

  @override
  String expires(String arg0) {
    return '有效期至 $arg0';
  }

  @override
  String developer(String arg0) {
    return '开发者：$arg0';
  }

  @override
  String contact(String arg0) {
    return '联系信息：$arg0';
  }

  @override
  String lastUsed(String arg0) {
    return '最近使用 $arg0';
  }

  @override
  String get revokeAccess2 => '取消授权';

  @override
  String get timeLimitedAccess => '限时授权';

  @override
  String get permanentAccess => '永久授权';

  @override
  String get accountIdentifier => '账号标识';

  @override
  String get basicProfile => '基本资料';

  @override
  String get email => '邮箱';

  @override
  String get phoneNumber => '手机号';

  @override
  String get images => '图片';

  @override
  String get chooseAvatar => '选择头像';

  @override
  String get imageMustBeNoLargerThanMb => '图片大小不能超过 3MB';

  @override
  String get onlyJpgPngWebpOrGifImages => '仅支持 JPG、PNG、WebP 或 GIF 图片';

  @override
  String get avatarUpdated => '头像已更新';

  @override
  String get personalInformation => '个人信息';

  @override
  String get keepYourBasicProfileAccurateAndComplete =>
      '维护您的基础资料，让账号信息保持准确、完整。';

  @override
  String get clickToChangeAvatar => '点击更换头像';

  @override
  String get clickToChangeAvatarJpgPngWebp =>
      '点击头像更换 · JPG、PNG、WebP 或 GIF · 最大 3MB';

  @override
  String get changeAvatar => '更换头像';

  @override
  String get username => '用户名';

  @override
  String get changeUsername => '修改用户名';

  @override
  String get extendedProfile => '扩展资料';

  @override
  String get viewAndEditYourPersonalInformation => '在这里查看与修改你的个人信息';

  @override
  String get changeFullName => '修改真实姓名';

  @override
  String get selectGender => '选择性别';

  @override
  String get male => '男';

  @override
  String get female => '女';

  @override
  String get preferNotToSay => '保密';

  @override
  String get dateOfBirth => '出生日期';

  @override
  String get changeDateOfBirth => '修改出生日期';

  @override
  String get changeRegion => '修改地区';

  @override
  String get bio => '简介';

  @override
  String get changeBio => '修改简介';

  @override
  String get securityOptions => '安全开关';

  @override
  String get manageYourAccountSecurityPreferences => '在这里管理你的账号安全偏好';

  @override
  String get enableMfa => '启用 MFA';

  @override
  String get addASecondVerificationStepWhenSigning => '登录时增加第二道验证。';

  @override
  String get mfaEnabled => '已开启 MFA';

  @override
  String get mfaDisabled => '已关闭 MFA';

  @override
  String get unusualLocationDetection => '异地登录检测';

  @override
  String get getAlertsWhenASignInComes => '发现异常位置登录时提醒。';

  @override
  String get unusualLocationDetectionEnabled => '已开启异地登录检测';

  @override
  String get unusualLocationDetectionDisabled => '已关闭异地登录检测';

  @override
  String get sensitiveActionEmails => '敏感操作邮件提醒';

  @override
  String get getNotificationsForPasswordEmailAndOther => '修改密码、邮箱等操作发送通知。';

  @override
  String get notificationsEnabled => '已开启提醒';

  @override
  String get notificationsDisabled => '已关闭提醒';

  @override
  String get subscribeToEmails => '订阅通知邮件';

  @override
  String get receiveProductUpdatesAndSystemAnnouncements => '接收产品更新和系统消息。';

  @override
  String get emailSubscriptionUpdated => '已更新邮件订阅';

  @override
  String get emailSubscriptionDisabled => '已关闭邮件订阅';

  @override
  String get signInMfaPreferencesUpdated => '登录 MFA 偏好已更新';

  @override
  String get signInMfaPreferences => '登录 MFA 偏好';

  @override
  String get sensitiveActionVerificationPreferencesUpdated => '敏感操作验证偏好已更新';

  @override
  String get sensitiveActionVerificationPreferences => '敏感操作验证偏好';

  @override
  String get authenticationMethods => '身份验证能力';

  @override
  String get configureVerificationMethods => '配置验证方式';

  @override
  String get passwordSignIn => '密码登录';

  @override
  String get enabled => '已启用';

  @override
  String get emailCodeSignIn => '邮箱验证码登录';

  @override
  String get notConfigured => '未配置';

  @override
  String itemCount(String arg0) {
    return '$arg0 个';
  }

  @override
  String get disabled => '未启用';

  @override
  String get changeEmail => '修改邮箱';

  @override
  String get changePassword => '修改密码';

  @override
  String get authorizeAccountRecovery => '发起账号恢复';

  @override
  String get generateAOneTimeRecoveryQrCode =>
      '为另一台设备生成 5 分钟有效的一次性恢复二维码和恢复码，恢复成功后旧会话会自动撤销。';

  @override
  String get adaptiveContinuousAuthentication => '自适应连续认证引擎';

  @override
  String get useSessionContextRiskSignalsAndRecent =>
      '根据当前会话环境、风险信号和最近验证状态动态判断是否需要 step-up。';

  @override
  String get totpManagement => 'TOTP 管理';

  @override
  String get manageYourAuthenticatorCodes => '在这里管理你的一次性验证码';

  @override
  String get passkeys => 'Passkey 列表';

  @override
  String get useNativePasskeysForSignInAnd => '使用系统原生 Passkey 完成登录与敏感验证';

  @override
  String get passkeyAdded => 'Passkey 已添加';

  @override
  String get addPasskey => '新增 Passkey';

  @override
  String get noPasskeysRegisteredYet => '当前没有已登记的 Passkey。';

  @override
  String get onlineSessions => '在线会话';

  @override
  String get manageYourSignInAndOnlineActivity => '管理您的登录与在线信息';

  @override
  String get allSessions => '全部会话';

  @override
  String get thisDevice => '当前设备';

  @override
  String get sessionsRefreshed => '会话已刷新';

  @override
  String get refreshList => '刷新列表';

  @override
  String get noOnlineSessions => '没有在线会话';

  @override
  String get sessionsWillAppearHereWhenYouSign => '登录其他设备后，会话信息会显示在这里。';

  @override
  String get dangerZone => '危险操作';

  @override
  String get signOutOfAllDevicesIncludingThe => '对应网页端的“退出所有设备”。';

  @override
  String get thisRevokesAllSessionsIncludingThisDevice =>
      '这会撤销所有会话，包括当前设备。AccessToken 失效后需要重新登录。';

  @override
  String get signOutAllDevices => '确认退出所有设备';

  @override
  String get thisDesktopSessionWillAlsoExpireImmediately => '当前桌面会话也会立即失效。';

  @override
  String get cancel => '取消';

  @override
  String get signOut2 => '确认退出';

  @override
  String get signedOutAllDevices => '已退出所有设备';

  @override
  String get signOutAllDevices2 => '退出所有设备';

  @override
  String get theSessionEnvironmentIsStableNoAdditional => '当前会话环境稳定，未发现额外风险信号';

  @override
  String get additionalVerificationIsRecommended => '建议立即补做验证';

  @override
  String get sessionTrustIsStable => '当前会话可信度稳定';

  @override
  String risk(String arg0, String arg1) {
    return '$arg0风险 $arg1';
  }

  @override
  String get policyFreezeSession => '策略：冻结会话';

  @override
  String get policyRequireVerification => '策略：强制补验';

  @override
  String get policyAllow => '策略：放行';

  @override
  String get continuousAuthenticationStatusUpdated => '连续认证状态已更新';

  @override
  String get verified => '已完成验证';

  @override
  String get verifyNow => '立即验证';

  @override
  String get riskAlert => '风险告警';

  @override
  String get riskSignalsDetectedPleaseTakeActionSoon => '检测到风险信号，请尽快处理';

  @override
  String alertExpiresInSeconds(String arg0) {
    return '告警剩余有效期：$arg0 秒';
  }

  @override
  String get sensitiveActionVerification => '敏感验证状态';

  @override
  String validForSeconds(String arg0) {
    return '有效 $arg0 秒';
  }

  @override
  String get notVerified => '未验证';

  @override
  String get sessionAge => '会话年龄';

  @override
  String get idleTime => '空闲时长';

  @override
  String get currentEnvironment => '当前环境';

  @override
  String get unknownDevice => '未知设备';

  @override
  String get riskSignals => '风险信号';

  @override
  String get viewSensitiveAccountActionsAndVerificationRecords =>
      '在这里查看账号敏感操作和验证记录';

  @override
  String get filterActivity => '筛选记录';

  @override
  String get reset => '重置';

  @override
  String get actionType => '操作类型';

  @override
  String get register => '注册';

  @override
  String get enableTotp => '启用 TOTP';

  @override
  String get disableTotp => '禁用 TOTP';

  @override
  String get result => '执行结果';

  @override
  String get success => '成功';

  @override
  String get failure => '失败';

  @override
  String get searching => '查询中';

  @override
  String get searchRecords => '查询记录';

  @override
  String get searchResults => '查询结果';

  @override
  String records(int arg0) {
    return '$arg0 条记录';
  }

  @override
  String get noMatchingRecords => '没有匹配的记录';

  @override
  String get changeTheActionTypeOrResultAnd => '调整操作类型或执行结果后重新查询。';

  @override
  String get additionalVerification => '二次验证';

  @override
  String currentChallenge(String arg0) {
    return '当前挑战：$arg0';
  }

  @override
  String get authenticatorCode => '动态码';

  @override
  String get recoveryCode => '恢复码';

  @override
  String get passkeyUnavailable => 'Passkey 不可用';

  @override
  String get digitAuthenticatorCode => '6 位动态码';

  @override
  String get useAPasskeyForAdditionalVerification => '使用 Passkey 完成二次验证';

  @override
  String get passkeyIsCurrentlyUnavailable => 'Passkey 当前不可用';

  @override
  String get verifyWithPasskey => '使用 Passkey 验证';

  @override
  String get completeMfaVerification => '完成 MFA 验证';

  @override
  String securityScoreSyncedDevicesAndLogs(
    String arg0,
    String arg1,
    String arg2,
  ) {
    return '当前安全评分 $arg0%，已同步 $arg1 台设备和 $arg2 条日志。';
  }

  @override
  String get edit => '编辑';

  @override
  String get totpEnabled => 'TOTP 已启用';

  @override
  String get totpIsNotEnabled => 'TOTP 尚未启用';

  @override
  String recoveryCodesRemaining(int arg0) {
    return '恢复码剩余 $arg0 个';
  }

  @override
  String get registerWithTheQrCodeOrSecret => '可通过二维码或密钥完成注册。';

  @override
  String get totpDisabled => 'TOTP 已关闭';

  @override
  String get disable => '禁用';

  @override
  String get enable => '启用';

  @override
  String get recoveryCodes => '恢复码列表';

  @override
  String get viewRecoveryCodes => '查看恢复码';

  @override
  String get newRecoveryCodes => '新恢复码';

  @override
  String get regenerateRecoveryCodes => '重新生成恢复码';

  @override
  String created(String arg0) {
    return '创建于 $arg0';
  }

  @override
  String get neverUsed => '从未使用';

  @override
  String get renamePasskey => '重命名 Passkey';

  @override
  String get rename => '重命名';

  @override
  String get deletePasskey => '删除 Passkey';

  @override
  String delete(String arg0) {
    return '确认删除 $arg0？';
  }

  @override
  String get delete2 => '删除';

  @override
  String get passkeyDeleted => 'Passkey 已删除';

  @override
  String get current => '当前';

  @override
  String get online => '在线';

  @override
  String get offline => '离线';

  @override
  String active(String arg0) {
    return '活跃 $arg0';
  }

  @override
  String get sessionRevoked => '会话已撤销';

  @override
  String get revoke => '撤销';

  @override
  String risk2(String arg0) {
    return '风险 $arg0';
  }

  @override
  String get verifyYourIdentityFirst => '需要先完成身份验证';

  @override
  String get invalidQrVerificationResultRefreshTheQr => '扫码验证结果无效，请刷新二维码后重试';

  @override
  String get verified2 => '验证成功';

  @override
  String get qrRequestRejectedPleaseRefreshTheQr => '扫码请求已被拒绝，请刷新二维码';

  @override
  String get enterYourSignInPassword => '请输入登录密码';

  @override
  String get enterADigitEmailCode => '请输入 6 位邮箱验证码';

  @override
  String get scanTheQrCodeWithYourPhone => '请使用手机扫码完成验证';

  @override
  String openingTheNativePasskeyService(String arg0) {
    return '即将调用 $arg0 系统 Passkey';
  }

  @override
  String get sensitiveActionVerification2 => '敏感操作验证';

  @override
  String get verifyYourIdentityToContinueWithThis => '请先验证身份后继续执行当前操作。';

  @override
  String get signInPassword => '登录密码';

  @override
  String get passkeyVerificationAvailable => 'Passkey 验证已接入';

  @override
  String get verifying => '验证中...';

  @override
  String get save => '保存';

  @override
  String get saved => '已保存';

  @override
  String get newEmail => '新邮箱';

  @override
  String get verificationCode => '验证码';

  @override
  String get emailUpdated => '邮箱已更新';

  @override
  String get saving2 => '保存中...';

  @override
  String get newPassword => '新密码';

  @override
  String get passwordUpdated => '密码已更新';

  @override
  String get scanTheQrCodeOrEnterThe => '扫描二维码或手动录入密钥，然后输入身份验证器生成的 6 位动态码。';

  @override
  String get initialRecoveryCodes => '初始恢复码';

  @override
  String get saveYourRecoveryCodes => '请保存恢复码';

  @override
  String get enabling => '启用中...';

  @override
  String get confirmAndEnable => '确认启用';

  @override
  String get couldNotGenerateQrCodePleaseRefresh => '二维码生成失败，请刷新';

  @override
  String get qrVerification => '扫码验证';

  @override
  String get enterYourCurrentSignInPasswordTo => '输入当前登录密码完成验证。';

  @override
  String get sendAOneTimeCodeToYour => '向当前绑定邮箱发送一次性验证码。';

  @override
  String get enterTheDigitCodeGeneratedByYour => '输入身份验证器生成的 6 位动态码。';

  @override
  String get scanWithYourSignedInMobileApp => '使用已登录手机端扫码确认当前敏感操作。';

  @override
  String get viewAccountStatusSignInMethodsAnd => '查看桌面端的账号状态、登录方式和近期活动。';

  @override
  String get manageYourAccountProfileAndExtendedInformation => '管理账户资料与扩展信息。';

  @override
  String get configureMfaTotpPasskeysAndSensitiveAction =>
      '配置 MFA、TOTP、Passkey 和敏感验证偏好。';

  @override
  String get viewOnlineDevicesSessionsAndTheirCurrent => '查看在线设备、登录会话和当前状态。';

  @override
  String get filterSensitiveActionsAndVerificationRecords => '筛选查看敏感操作与验证记录。';

  @override
  String get viewThirdPartyAppsAuthorizedToAccess => '查看已授权访问您账号信息的第三方应用。';

  @override
  String get desktopApp => '桌面端';

  @override
  String get mobileApp => '移动端';

  @override
  String get unknownBrowser => '未知浏览器';

  @override
  String get passwordMfa => '密码 + 二步验证';

  @override
  String get codeSignIn => '验证码登录';

  @override
  String get codeMfa => '验证码 + 二步验证';

  @override
  String get passkeySignIn => 'Passkey 登录';

  @override
  String get passkeyMfa => 'Passkey + 二步验证';

  @override
  String get qrCodeMfa => '扫码 + 二步验证';

  @override
  String get bridgeSignIn => '桥接登录';

  @override
  String get desktopBridge => '电脑端桥接';

  @override
  String get webBridge => '网页端桥接';

  @override
  String get mobileBridge => '桥接到手机端';

  @override
  String get accountRecovery => '账号恢复';

  @override
  String get generalAction => '通用操作';

  @override
  String get high => '高';

  @override
  String get medium => '中';

  @override
  String get low => '低';

  @override
  String seconds(int arg0) {
    return '$arg0 秒';
  }

  @override
  String minutes(int arg0) {
    return '$arg0 分钟';
  }

  @override
  String hours(int arg0) {
    return '$arg0 小时';
  }

  @override
  String days(int arg0) {
    return '$arg0 天';
  }

  @override
  String get justNow => '刚刚';

  @override
  String minutesAgo(int arg0) {
    return '$arg0 分钟前';
  }

  @override
  String hoursAgo(int arg0) {
    return '$arg0 小时前';
  }

  @override
  String daysAgo(int arg0) {
    return '$arg0 天前';
  }

  @override
  String get invalidDataFormat => '数据格式错误';

  @override
  String get unnamedApp => '未命名应用';

  @override
  String passwordMustBeBetweenAndCharactersLong(String arg0, String arg1) {
    return '密码长度必须在 $arg0-$arg1 个字符之间';
  }

  @override
  String get passwordMustContainAtLeastOneUppercase => '密码必须包含至少一个大写字母';

  @override
  String get passwordMustContainAtLeastOneLowercase => '密码必须包含至少一个小写字母';

  @override
  String get passwordMustContainAtLeastOneDigit => '密码必须包含至少一个数字';

  @override
  String get passwordMustContainAtLeastOneSpecial => '密码必须包含至少一个特殊字符';

  @override
  String get timedOutWaitingForTheBrowserResult => '等待浏览器回传结果超时';

  @override
  String get browserBridgeStateVerificationFailed => '浏览器桥接状态校验失败';

  @override
  String get theBrowserDidNotCompleteThePasskey => '浏览器未完成 Passkey 操作';

  @override
  String get theDesktopAppReceivedThePasskeyResult =>
      '桌面端已收到 Passkey 结果，可以关闭当前页面。';

  @override
  String hActionCompletedHPPP(String arg0) {
    return '<h2>操作已完成</h2><p>$arg0</p><p>现在可以回到桌面端。</p></body></html>';
  }

  @override
  String get theDesktopAppDidNotReceiveA => '桌面端未收到有效结果。';

  @override
  String hActionFailedHPPP(String arg0) {
    return '<h2>操作失败</h2><p>$arg0</p><p>请返回桌面端重试。</p></body></html>';
  }

  @override
  String get couldNotOpenTheExternalBrowser => '无法打开外部浏览器';

  @override
  String get language => '语言';

  @override
  String get languageSettingExplanation => '默认使用设备语言，也可以为此设备选择语言。';

  @override
  String get languageSaveFailed => '无法保存语言偏好，重启应用后可能恢复默认。';
}

/// The translations for Chinese, using the Han script (`zh_Hans`).
class AppLocalizationsZhHans extends AppLocalizationsZh {
  AppLocalizationsZhHans() : super('zh_Hans');

  @override
  String get ksuserSecurity => 'Ksuser安全';

  @override
  String get dataRefreshed => '数据刷新完成';

  @override
  String get signInPageRefreshed => '登录页数据已刷新';

  @override
  String get checkingTheTerms => '正在检查使用协议...';

  @override
  String get beforeYouBegin => '开始使用前请确认';

  @override
  String get beforeInstallingAndUsingThisAppFor =>
      '首次安装并使用本应用前，请阅读并同意以下协议和清单。未同意前将无法继续登录或使用桌面端功能。';

  @override
  String get termsOfService => '服务条款';

  @override
  String get privacyPolicy => '隐私政策';

  @override
  String get thirdPartyDataSharingDisclosure => '第三方信息共享清单';

  @override
  String get iHaveReadAndAgreeToThe => '我已阅读并同意以上服务条款、隐私政策和第三方信息共享清单';

  @override
  String get saving => '正在保存...';

  @override
  String get agreeAndContinue => '同意并继续';

  @override
  String get minimizeToSystemTray => '收起到托盘';

  @override
  String get minimizeToMenuBar => '收起到菜单栏';

  @override
  String get couldNotMinimizeTheWindow => '窗口收起失败';

  @override
  String get thisDeviceDoesNotSupportLocalAuthentication => '当前设备不支持系统本地认证';

  @override
  String get verifyYourIdentityBeforeDisplayingTheMobile =>
      '请先完成身份验证后再展示手机登录二维码';

  @override
  String get mobileQrSignIn => '手机扫码登录';

  @override
  String get generatingQrCode => '正在生成二维码...';

  @override
  String expiresInSeconds(String arg0) {
    return '剩余有效期：$arg0 秒';
  }

  @override
  String get thisQrCodeCanOnlyBeUsed => '二维码一次性有效，过期后请刷新。';

  @override
  String get close => '关闭';

  @override
  String get refreshQrCode => '刷新二维码';

  @override
  String get accountRecoveryAuthorization => '账号恢复授权';

  @override
  String get generatingRecoveryQrCode => '正在生成恢复二维码...';

  @override
  String authorizingDevice(String arg0) {
    return '背书设备：$arg0';
  }

  @override
  String get thisDesktopApp => '当前桌面端';

  @override
  String location(String arg0) {
    return '位置：$arg0';
  }

  @override
  String get unknownLocation => '未知位置';

  @override
  String get generatingRecoveryCode => '恢复码生成中...';

  @override
  String get openTheRecoveryPageOnAnotherDevice =>
      '请在另一台设备打开网页恢复页后扫码，或手动输入上面的恢复码。恢复成功后旧会话会自动失效。';

  @override
  String get recoveryCodeCopied => '恢复码已复制';

  @override
  String get copyRecoveryCode => '复制恢复码';

  @override
  String get openRecoveryPage => '打开网页恢复页';

  @override
  String get refreshing => '刷新中...';

  @override
  String get refreshAuthorization => '刷新授权';

  @override
  String get invalidQrSignInResultPleaseTry => '扫码登录结果无效，请重试';

  @override
  String get qrRequestRejectedRefreshTheQrCode => '扫码请求已被拒绝，请刷新二维码后重试';

  @override
  String get qrCodeExpiredPleaseRefreshIt => '二维码已过期，请刷新二维码';

  @override
  String get qrSignIn => '二维码登录';

  @override
  String get scanWithASignedInMobileApp => '请使用已登录的手机端扫描二维码，授权当前桌面端登录。';

  @override
  String get ifMfaIsEnabledCompleteTheRemaining =>
      '若账号开启了 MFA，扫码后还需在桌面端完成其他 MFA 验证。';

  @override
  String version(String arg0, String arg1) {
    return '版本 $arg0 ($arg1)';
  }

  @override
  String get settings => '设置';

  @override
  String get appearance => '外观';

  @override
  String get followSystem => '跟随系统';

  @override
  String get light => '浅色';

  @override
  String get dark => '深色';

  @override
  String get localPreferences => '本地调试';

  @override
  String get compactLayout => '紧凑布局';

  @override
  String get reduceSpacingToDisplayMoreContentOn => '压缩控件间距，便于桌面联调时查看更多内容';

  @override
  String get reduceMotion => '减少动画';

  @override
  String get reduceAnimationsWhenChangingThemesAndOpening => '切换主题和弹窗时尽量减少动画效果';

  @override
  String get about => '关于';

  @override
  String get privacyPolicy2 => '隐私协议';

  @override
  String get restoreDefaults => '恢复默认';

  @override
  String get done => '完成';

  @override
  String get desktopUnifiedAuthenticationClient => '桌面统一认证客户端';

  @override
  String theme(String arg0) {
    return '主题模式：$arg0';
  }

  @override
  String localPreferences2(String arg0, String arg1) {
    return '本地调试：$arg0$arg1';
  }

  @override
  String get compactLayout2 => '紧凑布局，';

  @override
  String get standardAnimations => '标准动画';

  @override
  String get system => '系统';

  @override
  String get verification => '验证';

  @override
  String useTheNativePasskeyServiceFor(String arg0, String arg1) {
    return '将直接调用 $arg0 系统原生 Passkey 完成$arg1。';
  }

  @override
  String nativePasskeysAreNotEnabledOnThis(String arg0) {
    return '当前设备未启用 $arg0 原生 Passkey。';
  }

  @override
  String get nativePasskeysAreNotSupportedOnThis => '当前平台暂不支持原生 Passkey';

  @override
  String get noPasskeyCredentialWasReceived => '未获取到 Passkey 凭证';

  @override
  String get passkeyVerificationFailed => 'Passkey 验证失败';

  @override
  String get noPasskeyCredentialWasCreated => '未创建 Passkey 凭证';

  @override
  String get couldNotCreatePasskey => 'Passkey 创建失败';

  @override
  String get signInWithAppleIsOnlyAvailable => 'Apple 登录仅在 macOS 上提供';

  @override
  String get noMacosSignInWithAppleConfiguration => '未获取到 macOS Apple 登录配置';

  @override
  String get couldNotReadTheSignInWith => '无法读取 Apple 登录配置';

  @override
  String get appleDidNotReturnCompleteSignIn => 'Apple 未返回完整的登录凭据';

  @override
  String get signInWithAppleCanceled => '已取消 Apple 登录';

  @override
  String get signInWithAppleFailedPleaseTry => 'Apple 登录失败，请重试';

  @override
  String get localAuthenticationIsNotSupportedOnThis => '当前平台暂不支持系统本地认证';

  @override
  String get localAuthenticationFailed => '本地认证未通过';

  @override
  String get systemAuthenticationCanceled => '用户取消了系统身份验证';

  @override
  String get windowsHelloPinOrAnotherSystemAuthentication =>
      '当前设备未启用 Windows Hello、PIN 或其他系统身份验证方式';

  @override
  String get windowsAuthenticationFailed => 'Windows 系统身份验证失败';

  @override
  String get localAuthenticationCanceled => '用户取消了本地认证';

  @override
  String get localAuthenticationFailed2 => '本地认证失败';

  @override
  String get processingAuthentication => '正在处理认证请求...';

  @override
  String get syncingDesktopData => '正在同步桌面数据...';

  @override
  String get processing => '正在处理中...';

  @override
  String get couldNotLoadAuthorizedAppsPleaseTry => '加载已授权应用失败，请稍后重试';

  @override
  String get signingIn => '正在登录...';

  @override
  String get sendingSignInCode => '正在发送登录验证码...';

  @override
  String get signingInWithApple => '正在使用 Apple 登录...';

  @override
  String get incompleteAppleSignInChallengePleaseTry => 'Apple 登录挑战信息不完整，请重试';

  @override
  String get linkYourAccountOnTheWebOr => '需前往网页端或移动端绑定账号之后才可以使用该第三方登录';

  @override
  String get verifyingAdditionalAuthentication => '正在验证二次认证...';

  @override
  String get waitingForPasskeyMfaVerification => '正在等待 Passkey 完成二次验证...';

  @override
  String get theBrowserDidNotReturnASign => '浏览器未返回登录结果';

  @override
  String get loadingPasswordRequirements => '正在加载密码规则...';

  @override
  String get sendingRegistrationCode => '正在发送注册验证码...';

  @override
  String get creatingAccount => '正在创建账户...';

  @override
  String get couldNotLoadDesktopData => '加载桌面数据失败';

  @override
  String get refreshingActivityLogs => '正在刷新敏感日志...';

  @override
  String get refreshingOnlineDevices => '正在刷新在线设备...';

  @override
  String get refreshingPasskeys => '正在刷新 Passkey 列表...';

  @override
  String get refreshingTotpStatus => '正在刷新 TOTP 状态...';

  @override
  String get refreshingContinuousAuthenticationStatus => '正在刷新连续认证状态...';

  @override
  String get savingSettings => '正在保存设置...';

  @override
  String get savingProfile => '正在保存资料...';

  @override
  String get uploadingAvatar => '正在上传头像...';

  @override
  String get checkingVerificationStatus => '正在检查验证状态...';

  @override
  String get checkingContinuousAuthenticationStatus => '正在检查连续认证状态...';

  @override
  String get sendingVerificationEmail => '正在发送验证邮件...';

  @override
  String get verifyingIdentity => '正在验证身份...';

  @override
  String get noUsableBrowserPasskeyBridgeUrlIs => '当前环境未配置可用的 Passkey 浏览器桥接地址';

  @override
  String get passkeysAreNotSupportedOnThisPlatform => '当前平台暂不支持 Passkey';

  @override
  String get waitingForPasskeySignIn => '正在等待 Passkey 登录...';

  @override
  String get waitingForPasskeyVerification => '正在等待 Passkey 验证...';

  @override
  String get theBrowserDidNotCompleteVerificationFor => '浏览器未完成敏感验证';

  @override
  String get waitingForPasskeyRegistration => '正在等待 Passkey 登记...';

  @override
  String get theBrowserDidNotCompletePasskeyRegistration => '浏览器未完成 Passkey 登记';

  @override
  String get thisEnvironmentPreventsALocalCallbackPort =>
      '当前环境禁止创建本地回调端口，无法使用浏览器 Passkey 桥接。';

  @override
  String get checkNetworkInboundPermissionsOrEnableNative =>
      '请检查系统网络入站权限，或在 macOS 上启用原生 Passkey。';

  @override
  String couldNotCreateTheLocalPasskeyCallback(String arg0) {
    return '无法创建本地 Passkey 回调服务：$arg0';
  }

  @override
  String get sendingEmailVerificationCode => '正在发送邮箱验证码...';

  @override
  String get updatingEmail => '正在更新邮箱...';

  @override
  String get updatingPassword => '正在更新密码...';

  @override
  String get revokingSession => '正在撤销会话...';

  @override
  String get signingOutAllDevices => '正在退出所有设备...';

  @override
  String get renamingPasskey => '正在重命名 Passkey...';

  @override
  String get deletingPasskey => '正在删除 Passkey...';

  @override
  String get gettingTotpConfiguration => '正在获取 TOTP 配置...';

  @override
  String get enablingTotp => '正在启用 TOTP...';

  @override
  String get loadingRecoveryCodes => '正在加载恢复码...';

  @override
  String get generatingNewRecoveryCodes => '正在生成新的恢复码...';

  @override
  String get disablingTotp => '正在关闭 TOTP...';

  @override
  String get signingOut => '正在退出登录...';

  @override
  String get generatingCrossDeviceSignInTicket => '正在生成跨端登录票据...';

  @override
  String get thisDesktopAppIsNotSignedIn => '当前桌面端尚未登录';

  @override
  String get generatingAccountRecoveryAuthorization => '正在生成账号恢复授权...';

  @override
  String get syncingWebSignInStatus => '正在同步网页登录状态...';

  @override
  String get crossDeviceSignInFailedNoAccess => '跨端登录失败，未获取到 accessToken';

  @override
  String networkConnectionFailed(String arg0) {
    return '网络连接失败：$arg0';
  }

  @override
  String avatarUploadFailed(String arg0) {
    return '头像上传失败：$arg0';
  }

  @override
  String couldNotConnectTo(String arg0, String arg1) {
    return '无法连接到 $arg0：$arg1';
  }

  @override
  String get tlsHandshakeFailedPleaseCheckTheServer => 'TLS 握手失败，请检查服务证书';

  @override
  String get requestTimedOutPleaseTryAgainLater => '请求超时，请稍后重试';

  @override
  String get theServerReturnedUnreadableData => '服务返回了无法解析的数据';

  @override
  String get authenticationExpiredPleaseSignInAgain => '认证已失效，请重新登录';

  @override
  String get couldNotRefreshTheToken => '刷新 Token 失败';

  @override
  String get enterYourEmailAddressFirst => '请先输入邮箱地址';

  @override
  String get enterYourPassword => '请输入密码';

  @override
  String get enterADigitVerificationCode => '请输入 6 位验证码';

  @override
  String get signedIn => '登录成功';

  @override
  String get firstVerificationCompletedContinueWithAdditionalVerification =>
      '第一步验证成功，请继续完成二次验证';

  @override
  String get verificationCodeSent => '验证码已发送';

  @override
  String get additionalVerificationCompleted => '二次验证通过';

  @override
  String get enterADigitAuthenticatorCode => '请输入 6 位动态码';

  @override
  String get enterARecoveryCode => '请输入恢复码';

  @override
  String get signedInWithPasskey => 'Passkey 登录成功';

  @override
  String get passkeyVerifiedContinueWithAdditionalVerification =>
      'Passkey 验证成功，请继续完成二次验证';

  @override
  String get signedInWithApple => 'Apple 登录成功';

  @override
  String get appleVerifiedContinueWithAdditionalVerification =>
      'Apple 验证成功，请继续完成二次验证';

  @override
  String get signedInWithQrCode => '扫码登录成功';

  @override
  String get qrCodeVerifiedContinueWithAdditionalVerification =>
      '扫码验证成功，请继续完成二次验证';

  @override
  String get signIn => '登录';

  @override
  String get signInToKsuserSecurityToManage => '登录 Ksuser 安全中心，继续管理你的账户与设备。';

  @override
  String get password => '密码';

  @override
  String get emailCode => '邮箱验证码';

  @override
  String get emailAddress => '邮箱地址';

  @override
  String get digitEmailCode => '6 位邮箱验证码';

  @override
  String get sendCode => '发送验证码';

  @override
  String get otherSignInMethods => '其他登录方式';

  @override
  String get signInWithApple => '使用 Apple 登录';

  @override
  String get qrSignIn2 => '扫码登录';

  @override
  String get newToKsuserRegisterOnTheWeb => '还没有账号？前往网页端注册';

  @override
  String get accountSecurityConsole => '账户安全控制台';

  @override
  String get workspace => '工作台';

  @override
  String get accountOverview => '账号总览';

  @override
  String get accountProfile => '账号资料';

  @override
  String get securitySettings => '安全设置';

  @override
  String get deviceManagement => '设备管理';

  @override
  String get activityLogs => '操作日志';

  @override
  String get accessAuthorization => '访问授权';

  @override
  String get crossDeviceAccess => '跨端联通';

  @override
  String get openTheWebAppAndSignIn => '打开网页端并自动登录';

  @override
  String get openingTheWebApp => '正在打开网页端...';

  @override
  String get webAppOpenedAndSignedInAutomatically => '已在浏览器打开网页端并自动登录';

  @override
  String get syncWebSignInToDesktop => '通过网页登录同步回桌面端';

  @override
  String get openingWebSignIn => '正在打开网页登录页...';

  @override
  String get webSignInOpenedYourSessionWill => '已打开网页登录页；网页登录成功后会自动同步回桌面端';

  @override
  String get openWebApp => '打开网页端';

  @override
  String get syncWebSignIn => '网页登录同步';

  @override
  String get accountActions => '账户操作';

  @override
  String get signOut => '退出登录';

  @override
  String get desktopDataRefreshed => '桌面数据已刷新';

  @override
  String get refresh => '刷新';

  @override
  String get profileSummary => '资料摘要';

  @override
  String get yourAccountAtAGlance => '账户资料一览';

  @override
  String get fullName => '真实姓名';

  @override
  String get notProvided => '未填写';

  @override
  String get region => '地区';

  @override
  String get gender => '性别';

  @override
  String get profileUpdated => '资料更新时间';

  @override
  String get signInMethods => '登录方式';

  @override
  String get availableAuthenticationMethods => '当前可用的认证方式';

  @override
  String get recentDeviceActivity => '近期设备活动';

  @override
  String get recentDevicesAndSessions => '最近登录的设备与会话';

  @override
  String get noDeviceActivityYet => '暂无设备活动';

  @override
  String get recentSensitiveActions => '近期敏感操作';

  @override
  String get recentAccountSecurityActivity => '账户安全相关的最近记录';

  @override
  String get noSensitiveActionsYet => '暂无敏感操作记录';

  @override
  String get accountIdentity => '账户身份';

  @override
  String get securityScore => '安全评分';

  @override
  String get totpProtectionEnabled => '已启用 TOTP 防护';

  @override
  String get enableTotpProtection => '建议开启 TOTP 防护';

  @override
  String get onlineDevices => '在线设备';

  @override
  String devicesOnline(int arg0) {
    return '$arg0 台当前在线';
  }

  @override
  String get activityLogs2 => '敏感日志';

  @override
  String get recentAccountSecurityActions => '近期账户安全操作';

  @override
  String get authorizedApps => '已授权应用';

  @override
  String get viewThirdPartyAppsThatCanAccess => '查看可访问您账号信息的第三方应用及授权范围。';

  @override
  String apps(int arg0) {
    return '$arg0 个应用';
  }

  @override
  String get authorizationInformationIsCurrentlyUnavailable => '暂时无法获取授权信息';

  @override
  String get retry => '重试';

  @override
  String get noAuthorizedAppsYet => '还没有授权应用';

  @override
  String get thirdPartyAppsYouAuthorizeWillAppear => '您确认授权的第三方应用会显示在这里。';

  @override
  String get aboutAuthorization => '授权说明';

  @override
  String get youCanManageThirdPartyAppsOn => '您可以随时在网页端访问授权页面管理第三方应用。';

  @override
  String get afterYouRevokeAccessTheAppCan => '撤销授权后，该应用将无法继续使用现有授权访问您的账户信息。';

  @override
  String revokeAccessFor(String arg0) {
    return '取消「$arg0」的授权？';
  }

  @override
  String get theAppWillNoLongerBeAble =>
      '取消后，该应用将无法继续访问您已授权的账号信息。若再次使用，需要重新确认授权。';

  @override
  String get keepAccess => '保留授权';

  @override
  String get revokeAccess => '确认取消';

  @override
  String accessRevokedFor(String arg0) {
    return '已取消「$arg0」的授权';
  }

  @override
  String get oneTimeAccess => '单次授权';

  @override
  String get noExpiration => '长期有效';

  @override
  String expires(String arg0) {
    return '有效期至 $arg0';
  }

  @override
  String developer(String arg0) {
    return '开发者：$arg0';
  }

  @override
  String contact(String arg0) {
    return '联系信息：$arg0';
  }

  @override
  String lastUsed(String arg0) {
    return '最近使用 $arg0';
  }

  @override
  String get revokeAccess2 => '取消授权';

  @override
  String get timeLimitedAccess => '限时授权';

  @override
  String get permanentAccess => '永久授权';

  @override
  String get accountIdentifier => '账号标识';

  @override
  String get basicProfile => '基本资料';

  @override
  String get email => '邮箱';

  @override
  String get phoneNumber => '手机号';

  @override
  String get images => '图片';

  @override
  String get chooseAvatar => '选择头像';

  @override
  String get imageMustBeNoLargerThanMb => '图片大小不能超过 3MB';

  @override
  String get onlyJpgPngWebpOrGifImages => '仅支持 JPG、PNG、WebP 或 GIF 图片';

  @override
  String get avatarUpdated => '头像已更新';

  @override
  String get personalInformation => '个人信息';

  @override
  String get keepYourBasicProfileAccurateAndComplete =>
      '维护您的基础资料，让账号信息保持准确、完整。';

  @override
  String get clickToChangeAvatar => '点击更换头像';

  @override
  String get clickToChangeAvatarJpgPngWebp =>
      '点击头像更换 · JPG、PNG、WebP 或 GIF · 最大 3MB';

  @override
  String get changeAvatar => '更换头像';

  @override
  String get username => '用户名';

  @override
  String get changeUsername => '修改用户名';

  @override
  String get extendedProfile => '扩展资料';

  @override
  String get viewAndEditYourPersonalInformation => '在这里查看与修改你的个人信息';

  @override
  String get changeFullName => '修改真实姓名';

  @override
  String get selectGender => '选择性别';

  @override
  String get male => '男';

  @override
  String get female => '女';

  @override
  String get preferNotToSay => '保密';

  @override
  String get dateOfBirth => '出生日期';

  @override
  String get changeDateOfBirth => '修改出生日期';

  @override
  String get changeRegion => '修改地区';

  @override
  String get bio => '简介';

  @override
  String get changeBio => '修改简介';

  @override
  String get securityOptions => '安全开关';

  @override
  String get manageYourAccountSecurityPreferences => '在这里管理你的账号安全偏好';

  @override
  String get enableMfa => '启用 MFA';

  @override
  String get addASecondVerificationStepWhenSigning => '登录时增加第二道验证。';

  @override
  String get mfaEnabled => '已开启 MFA';

  @override
  String get mfaDisabled => '已关闭 MFA';

  @override
  String get unusualLocationDetection => '异地登录检测';

  @override
  String get getAlertsWhenASignInComes => '发现异常位置登录时提醒。';

  @override
  String get unusualLocationDetectionEnabled => '已开启异地登录检测';

  @override
  String get unusualLocationDetectionDisabled => '已关闭异地登录检测';

  @override
  String get sensitiveActionEmails => '敏感操作邮件提醒';

  @override
  String get getNotificationsForPasswordEmailAndOther => '修改密码、邮箱等操作发送通知。';

  @override
  String get notificationsEnabled => '已开启提醒';

  @override
  String get notificationsDisabled => '已关闭提醒';

  @override
  String get subscribeToEmails => '订阅通知邮件';

  @override
  String get receiveProductUpdatesAndSystemAnnouncements => '接收产品更新和系统消息。';

  @override
  String get emailSubscriptionUpdated => '已更新邮件订阅';

  @override
  String get emailSubscriptionDisabled => '已关闭邮件订阅';

  @override
  String get signInMfaPreferencesUpdated => '登录 MFA 偏好已更新';

  @override
  String get signInMfaPreferences => '登录 MFA 偏好';

  @override
  String get sensitiveActionVerificationPreferencesUpdated => '敏感操作验证偏好已更新';

  @override
  String get sensitiveActionVerificationPreferences => '敏感操作验证偏好';

  @override
  String get authenticationMethods => '身份验证能力';

  @override
  String get configureVerificationMethods => '配置验证方式';

  @override
  String get passwordSignIn => '密码登录';

  @override
  String get enabled => '已启用';

  @override
  String get emailCodeSignIn => '邮箱验证码登录';

  @override
  String get notConfigured => '未配置';

  @override
  String itemCount(String arg0) {
    return '$arg0 个';
  }

  @override
  String get disabled => '未启用';

  @override
  String get changeEmail => '修改邮箱';

  @override
  String get changePassword => '修改密码';

  @override
  String get authorizeAccountRecovery => '发起账号恢复';

  @override
  String get generateAOneTimeRecoveryQrCode =>
      '为另一台设备生成 5 分钟有效的一次性恢复二维码和恢复码，恢复成功后旧会话会自动撤销。';

  @override
  String get adaptiveContinuousAuthentication => '自适应连续认证引擎';

  @override
  String get useSessionContextRiskSignalsAndRecent =>
      '根据当前会话环境、风险信号和最近验证状态动态判断是否需要 step-up。';

  @override
  String get totpManagement => 'TOTP 管理';

  @override
  String get manageYourAuthenticatorCodes => '在这里管理你的一次性验证码';

  @override
  String get passkeys => 'Passkey 列表';

  @override
  String get useNativePasskeysForSignInAnd => '使用系统原生 Passkey 完成登录与敏感验证';

  @override
  String get passkeyAdded => 'Passkey 已添加';

  @override
  String get addPasskey => '新增 Passkey';

  @override
  String get noPasskeysRegisteredYet => '当前没有已登记的 Passkey。';

  @override
  String get onlineSessions => '在线会话';

  @override
  String get manageYourSignInAndOnlineActivity => '管理您的登录与在线信息';

  @override
  String get allSessions => '全部会话';

  @override
  String get thisDevice => '当前设备';

  @override
  String get sessionsRefreshed => '会话已刷新';

  @override
  String get refreshList => '刷新列表';

  @override
  String get noOnlineSessions => '没有在线会话';

  @override
  String get sessionsWillAppearHereWhenYouSign => '登录其他设备后，会话信息会显示在这里。';

  @override
  String get dangerZone => '危险操作';

  @override
  String get signOutOfAllDevicesIncludingThe => '对应网页端的“退出所有设备”。';

  @override
  String get thisRevokesAllSessionsIncludingThisDevice =>
      '这会撤销所有会话，包括当前设备。AccessToken 失效后需要重新登录。';

  @override
  String get signOutAllDevices => '确认退出所有设备';

  @override
  String get thisDesktopSessionWillAlsoExpireImmediately => '当前桌面会话也会立即失效。';

  @override
  String get cancel => '取消';

  @override
  String get signOut2 => '确认退出';

  @override
  String get signedOutAllDevices => '已退出所有设备';

  @override
  String get signOutAllDevices2 => '退出所有设备';

  @override
  String get theSessionEnvironmentIsStableNoAdditional => '当前会话环境稳定，未发现额外风险信号';

  @override
  String get additionalVerificationIsRecommended => '建议立即补做验证';

  @override
  String get sessionTrustIsStable => '当前会话可信度稳定';

  @override
  String risk(String arg0, String arg1) {
    return '$arg0风险 $arg1';
  }

  @override
  String get policyFreezeSession => '策略：冻结会话';

  @override
  String get policyRequireVerification => '策略：强制补验';

  @override
  String get policyAllow => '策略：放行';

  @override
  String get continuousAuthenticationStatusUpdated => '连续认证状态已更新';

  @override
  String get verified => '已完成验证';

  @override
  String get verifyNow => '立即验证';

  @override
  String get riskAlert => '风险告警';

  @override
  String get riskSignalsDetectedPleaseTakeActionSoon => '检测到风险信号，请尽快处理';

  @override
  String alertExpiresInSeconds(String arg0) {
    return '告警剩余有效期：$arg0 秒';
  }

  @override
  String get sensitiveActionVerification => '敏感验证状态';

  @override
  String validForSeconds(String arg0) {
    return '有效 $arg0 秒';
  }

  @override
  String get notVerified => '未验证';

  @override
  String get sessionAge => '会话年龄';

  @override
  String get idleTime => '空闲时长';

  @override
  String get currentEnvironment => '当前环境';

  @override
  String get unknownDevice => '未知设备';

  @override
  String get riskSignals => '风险信号';

  @override
  String get viewSensitiveAccountActionsAndVerificationRecords =>
      '在这里查看账号敏感操作和验证记录';

  @override
  String get filterActivity => '筛选记录';

  @override
  String get reset => '重置';

  @override
  String get actionType => '操作类型';

  @override
  String get register => '注册';

  @override
  String get enableTotp => '启用 TOTP';

  @override
  String get disableTotp => '禁用 TOTP';

  @override
  String get result => '执行结果';

  @override
  String get success => '成功';

  @override
  String get failure => '失败';

  @override
  String get searching => '查询中';

  @override
  String get searchRecords => '查询记录';

  @override
  String get searchResults => '查询结果';

  @override
  String records(int arg0) {
    return '$arg0 条记录';
  }

  @override
  String get noMatchingRecords => '没有匹配的记录';

  @override
  String get changeTheActionTypeOrResultAnd => '调整操作类型或执行结果后重新查询。';

  @override
  String get additionalVerification => '二次验证';

  @override
  String currentChallenge(String arg0) {
    return '当前挑战：$arg0';
  }

  @override
  String get authenticatorCode => '动态码';

  @override
  String get recoveryCode => '恢复码';

  @override
  String get passkeyUnavailable => 'Passkey 不可用';

  @override
  String get digitAuthenticatorCode => '6 位动态码';

  @override
  String get useAPasskeyForAdditionalVerification => '使用 Passkey 完成二次验证';

  @override
  String get passkeyIsCurrentlyUnavailable => 'Passkey 当前不可用';

  @override
  String get verifyWithPasskey => '使用 Passkey 验证';

  @override
  String get completeMfaVerification => '完成 MFA 验证';

  @override
  String securityScoreSyncedDevicesAndLogs(
    String arg0,
    String arg1,
    String arg2,
  ) {
    return '当前安全评分 $arg0%，已同步 $arg1 台设备和 $arg2 条日志。';
  }

  @override
  String get edit => '编辑';

  @override
  String get totpEnabled => 'TOTP 已启用';

  @override
  String get totpIsNotEnabled => 'TOTP 尚未启用';

  @override
  String recoveryCodesRemaining(int arg0) {
    return '恢复码剩余 $arg0 个';
  }

  @override
  String get registerWithTheQrCodeOrSecret => '可通过二维码或密钥完成注册。';

  @override
  String get totpDisabled => 'TOTP 已关闭';

  @override
  String get disable => '禁用';

  @override
  String get enable => '启用';

  @override
  String get recoveryCodes => '恢复码列表';

  @override
  String get viewRecoveryCodes => '查看恢复码';

  @override
  String get newRecoveryCodes => '新恢复码';

  @override
  String get regenerateRecoveryCodes => '重新生成恢复码';

  @override
  String created(String arg0) {
    return '创建于 $arg0';
  }

  @override
  String get neverUsed => '从未使用';

  @override
  String get renamePasskey => '重命名 Passkey';

  @override
  String get rename => '重命名';

  @override
  String get deletePasskey => '删除 Passkey';

  @override
  String delete(String arg0) {
    return '确认删除 $arg0？';
  }

  @override
  String get delete2 => '删除';

  @override
  String get passkeyDeleted => 'Passkey 已删除';

  @override
  String get current => '当前';

  @override
  String get online => '在线';

  @override
  String get offline => '离线';

  @override
  String active(String arg0) {
    return '活跃 $arg0';
  }

  @override
  String get sessionRevoked => '会话已撤销';

  @override
  String get revoke => '撤销';

  @override
  String risk2(String arg0) {
    return '风险 $arg0';
  }

  @override
  String get verifyYourIdentityFirst => '需要先完成身份验证';

  @override
  String get invalidQrVerificationResultRefreshTheQr => '扫码验证结果无效，请刷新二维码后重试';

  @override
  String get verified2 => '验证成功';

  @override
  String get qrRequestRejectedPleaseRefreshTheQr => '扫码请求已被拒绝，请刷新二维码';

  @override
  String get enterYourSignInPassword => '请输入登录密码';

  @override
  String get enterADigitEmailCode => '请输入 6 位邮箱验证码';

  @override
  String get scanTheQrCodeWithYourPhone => '请使用手机扫码完成验证';

  @override
  String openingTheNativePasskeyService(String arg0) {
    return '即将调用 $arg0 系统 Passkey';
  }

  @override
  String get sensitiveActionVerification2 => '敏感操作验证';

  @override
  String get verifyYourIdentityToContinueWithThis => '请先验证身份后继续执行当前操作。';

  @override
  String get signInPassword => '登录密码';

  @override
  String get passkeyVerificationAvailable => 'Passkey 验证已接入';

  @override
  String get verifying => '验证中...';

  @override
  String get save => '保存';

  @override
  String get saved => '已保存';

  @override
  String get newEmail => '新邮箱';

  @override
  String get verificationCode => '验证码';

  @override
  String get emailUpdated => '邮箱已更新';

  @override
  String get saving2 => '保存中...';

  @override
  String get newPassword => '新密码';

  @override
  String get passwordUpdated => '密码已更新';

  @override
  String get scanTheQrCodeOrEnterThe => '扫描二维码或手动录入密钥，然后输入身份验证器生成的 6 位动态码。';

  @override
  String get initialRecoveryCodes => '初始恢复码';

  @override
  String get saveYourRecoveryCodes => '请保存恢复码';

  @override
  String get enabling => '启用中...';

  @override
  String get confirmAndEnable => '确认启用';

  @override
  String get couldNotGenerateQrCodePleaseRefresh => '二维码生成失败，请刷新';

  @override
  String get qrVerification => '扫码验证';

  @override
  String get enterYourCurrentSignInPasswordTo => '输入当前登录密码完成验证。';

  @override
  String get sendAOneTimeCodeToYour => '向当前绑定邮箱发送一次性验证码。';

  @override
  String get enterTheDigitCodeGeneratedByYour => '输入身份验证器生成的 6 位动态码。';

  @override
  String get scanWithYourSignedInMobileApp => '使用已登录手机端扫码确认当前敏感操作。';

  @override
  String get viewAccountStatusSignInMethodsAnd => '查看桌面端的账号状态、登录方式和近期活动。';

  @override
  String get manageYourAccountProfileAndExtendedInformation => '管理账户资料与扩展信息。';

  @override
  String get configureMfaTotpPasskeysAndSensitiveAction =>
      '配置 MFA、TOTP、Passkey 和敏感验证偏好。';

  @override
  String get viewOnlineDevicesSessionsAndTheirCurrent => '查看在线设备、登录会话和当前状态。';

  @override
  String get filterSensitiveActionsAndVerificationRecords => '筛选查看敏感操作与验证记录。';

  @override
  String get viewThirdPartyAppsAuthorizedToAccess => '查看已授权访问您账号信息的第三方应用。';

  @override
  String get desktopApp => '桌面端';

  @override
  String get mobileApp => '移动端';

  @override
  String get unknownBrowser => '未知浏览器';

  @override
  String get passwordMfa => '密码 + 二步验证';

  @override
  String get codeSignIn => '验证码登录';

  @override
  String get codeMfa => '验证码 + 二步验证';

  @override
  String get passkeySignIn => 'Passkey 登录';

  @override
  String get passkeyMfa => 'Passkey + 二步验证';

  @override
  String get qrCodeMfa => '扫码 + 二步验证';

  @override
  String get bridgeSignIn => '桥接登录';

  @override
  String get desktopBridge => '电脑端桥接';

  @override
  String get webBridge => '网页端桥接';

  @override
  String get mobileBridge => '桥接到手机端';

  @override
  String get accountRecovery => '账号恢复';

  @override
  String get generalAction => '通用操作';

  @override
  String get high => '高';

  @override
  String get medium => '中';

  @override
  String get low => '低';

  @override
  String seconds(int arg0) {
    return '$arg0 秒';
  }

  @override
  String minutes(int arg0) {
    return '$arg0 分钟';
  }

  @override
  String hours(int arg0) {
    return '$arg0 小时';
  }

  @override
  String days(int arg0) {
    return '$arg0 天';
  }

  @override
  String get justNow => '刚刚';

  @override
  String minutesAgo(int arg0) {
    return '$arg0 分钟前';
  }

  @override
  String hoursAgo(int arg0) {
    return '$arg0 小时前';
  }

  @override
  String daysAgo(int arg0) {
    return '$arg0 天前';
  }

  @override
  String get invalidDataFormat => '数据格式错误';

  @override
  String get unnamedApp => '未命名应用';

  @override
  String passwordMustBeBetweenAndCharactersLong(String arg0, String arg1) {
    return '密码长度必须在 $arg0-$arg1 个字符之间';
  }

  @override
  String get passwordMustContainAtLeastOneUppercase => '密码必须包含至少一个大写字母';

  @override
  String get passwordMustContainAtLeastOneLowercase => '密码必须包含至少一个小写字母';

  @override
  String get passwordMustContainAtLeastOneDigit => '密码必须包含至少一个数字';

  @override
  String get passwordMustContainAtLeastOneSpecial => '密码必须包含至少一个特殊字符';

  @override
  String get timedOutWaitingForTheBrowserResult => '等待浏览器回传结果超时';

  @override
  String get browserBridgeStateVerificationFailed => '浏览器桥接状态校验失败';

  @override
  String get theBrowserDidNotCompleteThePasskey => '浏览器未完成 Passkey 操作';

  @override
  String get theDesktopAppReceivedThePasskeyResult =>
      '桌面端已收到 Passkey 结果，可以关闭当前页面。';

  @override
  String hActionCompletedHPPP(String arg0) {
    return '<h2>操作已完成</h2><p>$arg0</p><p>现在可以回到桌面端。</p></body></html>';
  }

  @override
  String get theDesktopAppDidNotReceiveA => '桌面端未收到有效结果。';

  @override
  String hActionFailedHPPP(String arg0) {
    return '<h2>操作失败</h2><p>$arg0</p><p>请返回桌面端重试。</p></body></html>';
  }

  @override
  String get couldNotOpenTheExternalBrowser => '无法打开外部浏览器';

  @override
  String get language => '语言';

  @override
  String get languageSettingExplanation => '默认使用设备语言，也可以为此设备选择语言。';

  @override
  String get languageSaveFailed => '无法保存语言偏好，重启应用后可能恢复默认。';
}
