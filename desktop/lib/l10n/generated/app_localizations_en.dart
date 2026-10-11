// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get ksuserSecurity => 'Ksuser Authentication Center';

  @override
  String get dataRefreshed => 'Data refreshed';

  @override
  String get signInPageRefreshed => 'Sign-in page refreshed';

  @override
  String get checkingTheTerms => 'Checking the terms...';

  @override
  String get beforeYouBegin => 'Before you begin';

  @override
  String get beforeInstallingAndUsingThisAppFor =>
      'Before installing and using this app for the first time, please read and accept the following agreements and disclosures. You must accept them to sign in or use the desktop app.';

  @override
  String get termsOfService => 'Terms of Service';

  @override
  String get privacyPolicy => 'Privacy Policy';

  @override
  String get thirdPartyDataSharingDisclosure =>
      'Third-party Data Sharing Disclosure';

  @override
  String get iHaveReadAndAgreeToThe =>
      'I have read and agree to the Terms of Service, Privacy Policy, and Third-party Data Sharing Disclosure';

  @override
  String get saving => 'Saving...';

  @override
  String get agreeAndContinue => 'Agree and continue';

  @override
  String get minimizeToSystemTray => 'Minimize to system tray';

  @override
  String get minimizeToMenuBar => 'Minimize to menu bar';

  @override
  String get couldNotMinimizeTheWindow => 'Could not minimize the window';

  @override
  String get thisDeviceDoesNotSupportLocalAuthentication =>
      'This device does not support local authentication';

  @override
  String get verifyYourIdentityBeforeDisplayingTheMobile =>
      'Verify your identity before displaying the mobile sign-in QR code';

  @override
  String get mobileQrSignIn => 'Mobile QR sign-in';

  @override
  String get generatingQrCode => 'Generating QR code...';

  @override
  String expiresInSeconds(String arg0) {
    return 'Expires in $arg0 seconds';
  }

  @override
  String get thisQrCodeCanOnlyBeUsed =>
      'This QR code can only be used once. Refresh it after it expires.';

  @override
  String get close => 'Close';

  @override
  String get refreshQrCode => 'Refresh QR code';

  @override
  String get accountRecoveryAuthorization => 'Account recovery authorization';

  @override
  String get generatingRecoveryQrCode => 'Generating recovery QR code...';

  @override
  String authorizingDevice(String arg0) {
    return 'Authorizing device: $arg0';
  }

  @override
  String get thisDesktopApp => 'This desktop app';

  @override
  String location(String arg0) {
    return 'Location: $arg0';
  }

  @override
  String get unknownLocation => 'Unknown location';

  @override
  String get generatingRecoveryCode => 'Generating recovery code...';

  @override
  String get openTheRecoveryPageOnAnotherDevice =>
      'Open the recovery page on another device and scan the QR code or enter the recovery code above. Existing sessions will expire after successful recovery.';

  @override
  String get recoveryCodeCopied => 'Recovery code copied';

  @override
  String get copyRecoveryCode => 'Copy recovery code';

  @override
  String get openRecoveryPage => 'Open recovery page';

  @override
  String get refreshing => 'Refreshing...';

  @override
  String get refreshAuthorization => 'Refresh authorization';

  @override
  String get invalidQrSignInResultPleaseTry =>
      'Invalid QR sign-in result. Please try again';

  @override
  String get qrRequestRejectedRefreshTheQrCode =>
      'QR request rejected. Refresh the QR code and try again';

  @override
  String get qrCodeExpiredPleaseRefreshIt =>
      'QR code expired. Please refresh it';

  @override
  String get qrSignIn => 'QR sign-in';

  @override
  String get scanWithASignedInMobileApp =>
      'Scan with a signed-in mobile app to authorize sign-in on this desktop app.';

  @override
  String get ifMfaIsEnabledCompleteTheRemaining =>
      'If MFA is enabled, complete the remaining MFA verification on this desktop app after scanning.';

  @override
  String version(String arg0, String arg1) {
    return 'Version $arg0 ($arg1)';
  }

  @override
  String get settings => 'Settings';

  @override
  String get appearance => 'Appearance';

  @override
  String get followSystem => 'Follow system';

  @override
  String get light => 'Light';

  @override
  String get dark => 'Dark';

  @override
  String get localPreferences => 'Local preferences';

  @override
  String get compactLayout => 'Compact layout';

  @override
  String get reduceSpacingToDisplayMoreContentOn =>
      'Reduce spacing to display more content on desktop';

  @override
  String get reduceMotion => 'Reduce motion';

  @override
  String get reduceAnimationsWhenChangingThemesAndOpening =>
      'Reduce animations when changing themes and opening dialogs';

  @override
  String get about => 'About';

  @override
  String get privacyPolicy2 => 'Privacy Policy';

  @override
  String get restoreDefaults => 'Restore defaults';

  @override
  String get done => 'Done';

  @override
  String get desktopUnifiedAuthenticationClient =>
      'Desktop unified authentication client';

  @override
  String theme(String arg0) {
    return 'Theme: $arg0';
  }

  @override
  String localPreferences2(String arg0, String arg1) {
    return 'Local preferences: $arg0$arg1';
  }

  @override
  String get compactLayout2 => 'Compact layout, ';

  @override
  String get standardAnimations => 'Standard animations';

  @override
  String get system => 'System';

  @override
  String get verification => 'verification';

  @override
  String useTheNativePasskeyServiceFor(String arg0, String arg1) {
    return 'Use the native $arg0 passkey service for $arg1.';
  }

  @override
  String nativePasskeysAreNotEnabledOnThis(String arg0) {
    return 'Native $arg0 passkeys are not enabled on this device.';
  }

  @override
  String get nativePasskeysAreNotSupportedOnThis =>
      'Native passkeys are not supported on this platform';

  @override
  String get noPasskeyCredentialWasReceived =>
      'No passkey credential was received';

  @override
  String get passkeyVerificationFailed => 'Passkey verification failed';

  @override
  String get noPasskeyCredentialWasCreated =>
      'No passkey credential was created';

  @override
  String get couldNotCreatePasskey => 'Could not create passkey';

  @override
  String get signInWithAppleIsOnlyAvailable =>
      'Sign in with Apple is only available on macOS';

  @override
  String get noMacosSignInWithAppleConfiguration =>
      'No macOS Sign in with Apple configuration was received';

  @override
  String get couldNotReadTheSignInWith =>
      'Could not read the Sign in with Apple configuration';

  @override
  String get appleDidNotReturnCompleteSignIn =>
      'Apple did not return complete sign-in credentials';

  @override
  String get signInWithAppleCanceled => 'Sign in with Apple canceled';

  @override
  String get signInWithAppleFailedPleaseTry =>
      'Sign in with Apple failed. Please try again';

  @override
  String get localAuthenticationIsNotSupportedOnThis =>
      'Local authentication is not supported on this platform';

  @override
  String get localAuthenticationFailed => 'Local authentication failed';

  @override
  String get systemAuthenticationCanceled => 'System authentication canceled';

  @override
  String get windowsHelloPinOrAnotherSystemAuthentication =>
      'Windows Hello, PIN, or another system authentication method is not enabled on this device';

  @override
  String get windowsAuthenticationFailed => 'Windows authentication failed';

  @override
  String get localAuthenticationCanceled => 'Local authentication canceled';

  @override
  String get localAuthenticationFailed2 => 'Local authentication failed';

  @override
  String get processingAuthentication => 'Processing authentication...';

  @override
  String get syncingDesktopData => 'Syncing desktop data...';

  @override
  String get processing => 'Processing...';

  @override
  String get couldNotLoadAuthorizedAppsPleaseTry =>
      'Could not load authorized apps. Please try again later';

  @override
  String get signingIn => 'Signing in...';

  @override
  String get sendingSignInCode => 'Sending sign-in code...';

  @override
  String get signingInWithApple => 'Signing in with Apple...';

  @override
  String get incompleteAppleSignInChallengePleaseTry =>
      'Incomplete Apple sign-in challenge. Please try again';

  @override
  String get linkYourAccountOnTheWebOr =>
      'Link your account on the web or mobile app before using this sign-in provider';

  @override
  String get verifyingAdditionalAuthentication =>
      'Verifying additional authentication...';

  @override
  String get waitingForPasskeyMfaVerification =>
      'Waiting for passkey MFA verification...';

  @override
  String get theBrowserDidNotReturnASign =>
      'The browser did not return a sign-in result';

  @override
  String get loadingPasswordRequirements => 'Loading password requirements...';

  @override
  String get sendingRegistrationCode => 'Sending registration code...';

  @override
  String get creatingAccount => 'Creating account...';

  @override
  String get couldNotLoadDesktopData => 'Could not load desktop data';

  @override
  String get refreshingActivityLogs => 'Refreshing activity logs...';

  @override
  String get refreshingOnlineDevices => 'Refreshing online devices...';

  @override
  String get refreshingPasskeys => 'Refreshing passkeys...';

  @override
  String get refreshingTotpStatus => 'Refreshing TOTP status...';

  @override
  String get refreshingContinuousAuthenticationStatus =>
      'Refreshing continuous authentication status...';

  @override
  String get savingSettings => 'Saving settings...';

  @override
  String get savingProfile => 'Saving profile...';

  @override
  String get uploadingAvatar => 'Uploading avatar...';

  @override
  String get checkingVerificationStatus => 'Checking verification status...';

  @override
  String get checkingContinuousAuthenticationStatus =>
      'Checking continuous authentication status...';

  @override
  String get sendingVerificationEmail => 'Sending verification email...';

  @override
  String get verifyingIdentity => 'Verifying identity...';

  @override
  String get noUsableBrowserPasskeyBridgeUrlIs =>
      'No usable browser passkey bridge URL is configured for this environment';

  @override
  String get passkeysAreNotSupportedOnThisPlatform =>
      'Passkeys are not supported on this platform';

  @override
  String get waitingForPasskeySignIn => 'Waiting for passkey sign-in...';

  @override
  String get waitingForPasskeyVerification =>
      'Waiting for passkey verification...';

  @override
  String get theBrowserDidNotCompleteVerificationFor =>
      'The browser did not complete verification for the sensitive action';

  @override
  String get waitingForPasskeyRegistration =>
      'Waiting for passkey registration...';

  @override
  String get theBrowserDidNotCompletePasskeyRegistration =>
      'The browser did not complete passkey registration';

  @override
  String get thisEnvironmentPreventsALocalCallbackPort =>
      'This environment prevents a local callback port from being created. The browser passkey bridge is unavailable.';

  @override
  String get checkNetworkInboundPermissionsOrEnableNative =>
      'Check network inbound permissions or enable native passkeys on macOS.';

  @override
  String couldNotCreateTheLocalPasskeyCallback(String arg0) {
    return 'Could not create the local passkey callback server: $arg0';
  }

  @override
  String get sendingEmailVerificationCode =>
      'Sending email verification code...';

  @override
  String get updatingEmail => 'Updating email...';

  @override
  String get updatingPassword => 'Updating password...';

  @override
  String get revokingSession => 'Revoking session...';

  @override
  String get signingOutAllDevices => 'Signing out all devices...';

  @override
  String get renamingPasskey => 'Renaming passkey...';

  @override
  String get deletingPasskey => 'Deleting passkey...';

  @override
  String get gettingTotpConfiguration => 'Getting TOTP configuration...';

  @override
  String get enablingTotp => 'Enabling TOTP...';

  @override
  String get loadingRecoveryCodes => 'Loading recovery codes...';

  @override
  String get generatingNewRecoveryCodes => 'Generating new recovery codes...';

  @override
  String get disablingTotp => 'Disabling TOTP...';

  @override
  String get signingOut => 'Signing out...';

  @override
  String get generatingCrossDeviceSignInTicket =>
      'Generating cross-device sign-in ticket...';

  @override
  String get thisDesktopAppIsNotSignedIn => 'This desktop app is not signed in';

  @override
  String get generatingAccountRecoveryAuthorization =>
      'Generating account recovery authorization...';

  @override
  String get syncingWebSignInStatus => 'Syncing web sign-in status...';

  @override
  String get crossDeviceSignInFailedNoAccess =>
      'Cross-device sign-in failed. No access token was received';

  @override
  String networkConnectionFailed(String arg0) {
    return 'Network connection failed: $arg0';
  }

  @override
  String avatarUploadFailed(String arg0) {
    return 'Avatar upload failed: $arg0';
  }

  @override
  String couldNotConnectTo(String arg0, String arg1) {
    return 'Could not connect to $arg0: $arg1';
  }

  @override
  String get tlsHandshakeFailedPleaseCheckTheServer =>
      'TLS handshake failed. Please check the server certificate';

  @override
  String get requestTimedOutPleaseTryAgainLater =>
      'Request timed out. Please try again later';

  @override
  String get theServerReturnedUnreadableData =>
      'The server returned unreadable data';

  @override
  String get authenticationExpiredPleaseSignInAgain =>
      'Authentication expired. Please sign in again';

  @override
  String get couldNotRefreshTheToken => 'Could not refresh the token';

  @override
  String get enterYourEmailAddressFirst => 'Enter your email address first';

  @override
  String get enterYourPassword => 'Enter your password';

  @override
  String get enterADigitVerificationCode => 'Enter a 6-digit verification code';

  @override
  String get signedIn => 'Signed in';

  @override
  String get firstVerificationCompletedContinueWithAdditionalVerification =>
      'First verification completed. Continue with additional verification';

  @override
  String get verificationCodeSent => 'Verification code sent';

  @override
  String get additionalVerificationCompleted =>
      'Additional verification completed';

  @override
  String get enterADigitAuthenticatorCode =>
      'Enter a 6-digit authenticator code';

  @override
  String get enterARecoveryCode => 'Enter a recovery code';

  @override
  String get signedInWithPasskey => 'Signed in with passkey';

  @override
  String get passkeyVerifiedContinueWithAdditionalVerification =>
      'Passkey verified. Continue with additional verification';

  @override
  String get signedInWithApple => 'Signed in with Apple';

  @override
  String get appleVerifiedContinueWithAdditionalVerification =>
      'Apple verified. Continue with additional verification';

  @override
  String get signedInWithQrCode => 'Signed in with QR code';

  @override
  String get qrCodeVerifiedContinueWithAdditionalVerification =>
      'QR code verified. Continue with additional verification';

  @override
  String get signIn => 'Sign in';

  @override
  String get signInToKsuserSecurityToManage =>
      'Sign in to Ksuser Authentication Center to manage your account and devices.';

  @override
  String get password => 'Password';

  @override
  String get emailCode => 'Email code';

  @override
  String get emailAddress => 'Email address';

  @override
  String get digitEmailCode => '6-digit email code';

  @override
  String get sendCode => 'Send code';

  @override
  String get otherSignInMethods => 'Other sign-in methods';

  @override
  String get signInWithApple => 'Sign in with Apple';

  @override
  String get qrSignIn2 => 'QR sign-in';

  @override
  String get newToKsuserRegisterOnTheWeb =>
      'New to Ksuser? Register on the web';

  @override
  String get accountSecurityConsole => 'Account security console';

  @override
  String get workspace => 'Workspace';

  @override
  String get accountOverview => 'Account overview';

  @override
  String get accountProfile => 'Account profile';

  @override
  String get securitySettings => 'Security settings';

  @override
  String get deviceManagement => 'Device management';

  @override
  String get activityLogs => 'Activity logs';

  @override
  String get accessAuthorization => 'Access authorization';

  @override
  String get crossDeviceAccess => 'Cross-device access';

  @override
  String get openTheWebAppAndSignIn =>
      'Open the web app and sign in automatically';

  @override
  String get openingTheWebApp => 'Opening the web app...';

  @override
  String get webAppOpenedAndSignedInAutomatically =>
      'Web app opened and signed in automatically';

  @override
  String get syncWebSignInToDesktop => 'Sync web sign-in to desktop';

  @override
  String get openingWebSignIn => 'Opening web sign-in...';

  @override
  String get webSignInOpenedYourSessionWill =>
      'Web sign-in opened. Your session will sync to desktop after you sign in';

  @override
  String get openWebApp => 'Open web app';

  @override
  String get syncWebSignIn => 'Sync web sign-in';

  @override
  String get accountActions => 'Account actions';

  @override
  String get signOut => 'Sign out';

  @override
  String get desktopDataRefreshed => 'Desktop data refreshed';

  @override
  String get refresh => 'Refresh';

  @override
  String get profileSummary => 'Profile summary';

  @override
  String get yourAccountAtAGlance => 'Your account at a glance';

  @override
  String get fullName => 'Full name';

  @override
  String get notProvided => 'Not provided';

  @override
  String get region => 'Region';

  @override
  String get gender => 'Gender';

  @override
  String get profileUpdated => 'Profile updated';

  @override
  String get signInMethods => 'Sign-in methods';

  @override
  String get availableAuthenticationMethods =>
      'Available authentication methods';

  @override
  String get recentDeviceActivity => 'Recent device activity';

  @override
  String get recentDevicesAndSessions => 'Recent devices and sessions';

  @override
  String get noDeviceActivityYet => 'No device activity yet';

  @override
  String get recentSensitiveActions => 'Recent sensitive actions';

  @override
  String get recentAccountSecurityActivity =>
      'Recent account security activity';

  @override
  String get noSensitiveActionsYet => 'No sensitive actions yet';

  @override
  String get accountIdentity => 'Account identity';

  @override
  String get securityScore => 'Security score';

  @override
  String get totpProtectionEnabled => 'TOTP protection enabled';

  @override
  String get enableTotpProtection => 'Enable TOTP protection';

  @override
  String get onlineDevices => 'Online devices';

  @override
  String devicesOnline(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 devices online',
      one: '1 device online',
    );
    return '$_temp0';
  }

  @override
  String get activityLogs2 => 'Activity logs';

  @override
  String get recentAccountSecurityActions => 'Recent account security actions';

  @override
  String get authorizedApps => 'Authorized apps';

  @override
  String get viewThirdPartyAppsThatCanAccess =>
      'View third-party apps that can access your account and their permissions.';

  @override
  String apps(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 apps',
      one: '1 app',
    );
    return '$_temp0';
  }

  @override
  String get authorizationInformationIsCurrentlyUnavailable =>
      'Authorization information is currently unavailable';

  @override
  String get retry => 'Retry';

  @override
  String get noAuthorizedAppsYet => 'No authorized apps yet';

  @override
  String get thirdPartyAppsYouAuthorizeWillAppear =>
      'Third-party apps you authorize will appear here.';

  @override
  String get aboutAuthorization => 'About authorization';

  @override
  String get youCanManageThirdPartyAppsOn =>
      'You can manage third-party apps on the web authorization page at any time.';

  @override
  String get afterYouRevokeAccessTheAppCan =>
      'After you revoke access, the app can no longer use its existing authorization to access your account.';

  @override
  String revokeAccessFor(String arg0) {
    return 'Revoke access for $arg0?';
  }

  @override
  String get theAppWillNoLongerBeAble =>
      'The app will no longer be able to access the account information you authorized. Using it again requires a new authorization.';

  @override
  String get keepAccess => 'Keep access';

  @override
  String get revokeAccess => 'Revoke access';

  @override
  String accessRevokedFor(String arg0) {
    return 'Access revoked for $arg0';
  }

  @override
  String get oneTimeAccess => 'One-time access';

  @override
  String get noExpiration => 'No expiration';

  @override
  String expires(String arg0) {
    return 'Expires $arg0';
  }

  @override
  String developer(String arg0) {
    return 'Developer: $arg0';
  }

  @override
  String contact(String arg0) {
    return 'Contact: $arg0';
  }

  @override
  String lastUsed(String arg0) {
    return 'Last used $arg0';
  }

  @override
  String get revokeAccess2 => 'Revoke access';

  @override
  String get timeLimitedAccess => 'Time-limited access';

  @override
  String get permanentAccess => 'Permanent access';

  @override
  String get accountIdentifier => 'Account identifier';

  @override
  String get basicProfile => 'Basic profile';

  @override
  String get email => 'Email';

  @override
  String get phoneNumber => 'Phone number';

  @override
  String get images => 'Images';

  @override
  String get chooseAvatar => 'Choose avatar';

  @override
  String get imageMustBeNoLargerThanMb => 'Image must be no larger than 3 MB';

  @override
  String get onlyJpgPngWebpOrGifImages =>
      'Only JPG, PNG, WebP, or GIF images are supported';

  @override
  String get avatarUpdated => 'Avatar updated';

  @override
  String get personalInformation => 'Personal information';

  @override
  String get keepYourBasicProfileAccurateAndComplete =>
      'Keep your basic profile accurate and complete.';

  @override
  String get clickToChangeAvatar => 'Click to change avatar';

  @override
  String get clickToChangeAvatarJpgPngWebp =>
      'Click to change avatar · JPG, PNG, WebP, or GIF · Up to 3 MB';

  @override
  String get changeAvatar => 'Change avatar';

  @override
  String get username => 'Username';

  @override
  String get changeUsername => 'Change username';

  @override
  String get extendedProfile => 'Extended profile';

  @override
  String get viewAndEditYourPersonalInformation =>
      'View and edit your personal information';

  @override
  String get changeFullName => 'Change full name';

  @override
  String get selectGender => 'Select gender';

  @override
  String get male => 'Male';

  @override
  String get female => 'Female';

  @override
  String get preferNotToSay => 'Prefer not to say';

  @override
  String get dateOfBirth => 'Date of birth';

  @override
  String get changeDateOfBirth => 'Change date of birth';

  @override
  String get changeRegion => 'Change region';

  @override
  String get bio => 'Bio';

  @override
  String get changeBio => 'Change bio';

  @override
  String get securityOptions => 'Security options';

  @override
  String get manageYourAccountSecurityPreferences =>
      'Manage your account security preferences';

  @override
  String get enableMfa => 'Enable MFA';

  @override
  String get addASecondVerificationStepWhenSigning =>
      'Add a second verification step when signing in.';

  @override
  String get mfaEnabled => 'MFA enabled';

  @override
  String get mfaDisabled => 'MFA disabled';

  @override
  String get unusualLocationDetection => 'Unusual location detection';

  @override
  String get getAlertsWhenASignInComes =>
      'Get alerts when a sign-in comes from an unusual location.';

  @override
  String get unusualLocationDetectionEnabled =>
      'Unusual location detection enabled';

  @override
  String get unusualLocationDetectionDisabled =>
      'Unusual location detection disabled';

  @override
  String get sensitiveActionEmails => 'Sensitive action emails';

  @override
  String get getNotificationsForPasswordEmailAndOther =>
      'Get notifications for password, email, and other sensitive changes.';

  @override
  String get notificationsEnabled => 'Notifications enabled';

  @override
  String get notificationsDisabled => 'Notifications disabled';

  @override
  String get subscribeToEmails => 'Subscribe to emails';

  @override
  String get receiveProductUpdatesAndSystemAnnouncements =>
      'Receive product updates and system announcements.';

  @override
  String get emailSubscriptionUpdated => 'Email subscription updated';

  @override
  String get emailSubscriptionDisabled => 'Email subscription disabled';

  @override
  String get signInMfaPreferencesUpdated => 'Sign-in MFA preferences updated';

  @override
  String get signInMfaPreferences => 'Sign-in MFA preferences';

  @override
  String get sensitiveActionVerificationPreferencesUpdated =>
      'Sensitive action verification preferences updated';

  @override
  String get sensitiveActionVerificationPreferences =>
      'Sensitive action verification preferences';

  @override
  String get authenticationMethods => 'Authentication methods';

  @override
  String get configureVerificationMethods => 'Configure verification methods';

  @override
  String get passwordSignIn => 'Password sign-in';

  @override
  String get enabled => 'Enabled';

  @override
  String get emailCodeSignIn => 'Email code sign-in';

  @override
  String get notConfigured => 'Not configured';

  @override
  String itemCount(String arg0) {
    return '$arg0';
  }

  @override
  String get disabled => 'Disabled';

  @override
  String get changeEmail => 'Change email';

  @override
  String get changePassword => 'Change password';

  @override
  String get authorizeAccountRecovery => 'Authorize account recovery';

  @override
  String get generateAOneTimeRecoveryQrCode =>
      'Generate a one-time recovery QR code and recovery code for another device, valid for 5 minutes. Existing sessions will be revoked after successful recovery.';

  @override
  String get adaptiveContinuousAuthentication =>
      'Adaptive continuous authentication';

  @override
  String get useSessionContextRiskSignalsAndRecent =>
      'Use session context, risk signals, and recent verification to determine whether additional verification is needed.';

  @override
  String get totpManagement => 'TOTP management';

  @override
  String get manageYourAuthenticatorCodes => 'Manage your authenticator codes';

  @override
  String get passkeys => 'Passkeys';

  @override
  String get useNativePasskeysForSignInAnd =>
      'Use native passkeys for sign-in and sensitive action verification';

  @override
  String get passkeyAdded => 'Passkey added';

  @override
  String get addPasskey => 'Add passkey';

  @override
  String get noPasskeysRegisteredYet => 'No passkeys registered yet.';

  @override
  String get onlineSessions => 'Online sessions';

  @override
  String get manageYourSignInAndOnlineActivity =>
      'Manage your sign-in and online activity';

  @override
  String get allSessions => 'All sessions';

  @override
  String get thisDevice => 'This device';

  @override
  String get sessionsRefreshed => 'Sessions refreshed';

  @override
  String get refreshList => 'Refresh list';

  @override
  String get noOnlineSessions => 'No online sessions';

  @override
  String get sessionsWillAppearHereWhenYouSign =>
      'Sessions will appear here when you sign in on other devices.';

  @override
  String get dangerZone => 'Danger zone';

  @override
  String get signOutOfAllDevicesIncludingThe =>
      'Sign out of all devices, including the web app.';

  @override
  String get thisRevokesAllSessionsIncludingThisDevice =>
      'This revokes all sessions, including this device. You will need to sign in again after the access token expires.';

  @override
  String get signOutAllDevices => 'Sign out all devices?';

  @override
  String get thisDesktopSessionWillAlsoExpireImmediately =>
      'This desktop session will also expire immediately.';

  @override
  String get cancel => 'Cancel';

  @override
  String get signOut2 => 'Sign out';

  @override
  String get signedOutAllDevices => 'Signed out all devices';

  @override
  String get signOutAllDevices2 => 'Sign out all devices';

  @override
  String get theSessionEnvironmentIsStableNoAdditional =>
      'The session environment is stable. No additional risk signals were detected';

  @override
  String get additionalVerificationIsRecommended =>
      'Additional verification is recommended';

  @override
  String get sessionTrustIsStable => 'Session trust is stable';

  @override
  String risk(String arg0, String arg1) {
    return '$arg0 risk $arg1';
  }

  @override
  String get policyFreezeSession => 'Policy: freeze session';

  @override
  String get policyRequireVerification => 'Policy: require verification';

  @override
  String get policyAllow => 'Policy: allow';

  @override
  String get continuousAuthenticationStatusUpdated =>
      'Continuous authentication status updated';

  @override
  String get verified => 'Verified';

  @override
  String get verifyNow => 'Verify now';

  @override
  String get riskAlert => 'Risk alert';

  @override
  String get riskSignalsDetectedPleaseTakeActionSoon =>
      'Risk signals detected. Please take action soon';

  @override
  String alertExpiresInSeconds(String arg0) {
    return 'Alert expires in $arg0 seconds';
  }

  @override
  String get sensitiveActionVerification => 'Sensitive action verification';

  @override
  String validForSeconds(String arg0) {
    return 'Valid for $arg0 seconds';
  }

  @override
  String get notVerified => 'Not verified';

  @override
  String get sessionAge => 'Session age';

  @override
  String get idleTime => 'Idle time';

  @override
  String get currentEnvironment => 'Current environment';

  @override
  String get unknownDevice => 'Unknown device';

  @override
  String get riskSignals => 'Risk signals';

  @override
  String get viewSensitiveAccountActionsAndVerificationRecords =>
      'View sensitive account actions and verification records';

  @override
  String get filterActivity => 'Filter activity';

  @override
  String get reset => 'Reset';

  @override
  String get actionType => 'Action type';

  @override
  String get register => 'Register';

  @override
  String get enableTotp => 'Enable TOTP';

  @override
  String get disableTotp => 'Disable TOTP';

  @override
  String get result => 'Result';

  @override
  String get success => 'Success';

  @override
  String get failure => 'Failure';

  @override
  String get searching => 'Searching';

  @override
  String get searchRecords => 'Search records';

  @override
  String get searchResults => 'Search results';

  @override
  String records(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 records',
      one: '1 record',
    );
    return '$_temp0';
  }

  @override
  String get noMatchingRecords => 'No matching records';

  @override
  String get changeTheActionTypeOrResultAnd =>
      'Change the action type or result and search again.';

  @override
  String get additionalVerification => 'Additional verification';

  @override
  String currentChallenge(String arg0) {
    return 'Current challenge: $arg0';
  }

  @override
  String get authenticatorCode => 'Authenticator code';

  @override
  String get recoveryCode => 'Recovery code';

  @override
  String get passkeyUnavailable => 'Passkey unavailable';

  @override
  String get digitAuthenticatorCode => '6-digit authenticator code';

  @override
  String get useAPasskeyForAdditionalVerification =>
      'Use a passkey for additional verification';

  @override
  String get passkeyIsCurrentlyUnavailable =>
      'Passkey is currently unavailable';

  @override
  String get verifyWithPasskey => 'Verify with passkey';

  @override
  String get completeMfaVerification => 'Complete MFA verification';

  @override
  String securityScoreSyncedDevicesAndLogs(
    String arg0,
    String arg1,
    String arg2,
  ) {
    return 'Security score: $arg0%. Synced $arg1 devices and $arg2 logs.';
  }

  @override
  String get edit => 'Edit';

  @override
  String get totpEnabled => 'TOTP enabled';

  @override
  String get totpIsNotEnabled => 'TOTP is not enabled';

  @override
  String recoveryCodesRemaining(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 recovery codes remaining',
      one: '1 recovery code remaining',
    );
    return '$_temp0';
  }

  @override
  String get registerWithTheQrCodeOrSecret =>
      'Register with the QR code or secret key.';

  @override
  String get totpDisabled => 'TOTP disabled';

  @override
  String get disable => 'Disable';

  @override
  String get enable => 'Enable';

  @override
  String get recoveryCodes => 'Recovery codes';

  @override
  String get viewRecoveryCodes => 'View recovery codes';

  @override
  String get newRecoveryCodes => 'New recovery codes';

  @override
  String get regenerateRecoveryCodes => 'Regenerate recovery codes';

  @override
  String created(String arg0) {
    return 'Created $arg0';
  }

  @override
  String get neverUsed => 'Never used';

  @override
  String get renamePasskey => 'Rename passkey';

  @override
  String get rename => 'Rename';

  @override
  String get deletePasskey => 'Delete passkey';

  @override
  String delete(String arg0) {
    return 'Delete $arg0?';
  }

  @override
  String get delete2 => 'Delete';

  @override
  String get passkeyDeleted => 'Passkey deleted';

  @override
  String get current => 'Current';

  @override
  String get online => 'Online';

  @override
  String get offline => 'Offline';

  @override
  String active(String arg0) {
    return 'Active $arg0';
  }

  @override
  String get sessionRevoked => 'Session revoked';

  @override
  String get revoke => 'Revoke';

  @override
  String risk2(String arg0) {
    return 'Risk $arg0';
  }

  @override
  String get verifyYourIdentityFirst => 'Verify your identity first';

  @override
  String get invalidQrVerificationResultRefreshTheQr =>
      'Invalid QR verification result. Refresh the QR code and try again';

  @override
  String get verified2 => 'Verified';

  @override
  String get qrRequestRejectedPleaseRefreshTheQr =>
      'QR request rejected. Please refresh the QR code';

  @override
  String get enterYourSignInPassword => 'Enter your sign-in password';

  @override
  String get enterADigitEmailCode => 'Enter a 6-digit email code';

  @override
  String get scanTheQrCodeWithYourPhone =>
      'Scan the QR code with your phone to verify';

  @override
  String openingTheNativePasskeyService(String arg0) {
    return 'Opening the native $arg0 passkey service';
  }

  @override
  String get sensitiveActionVerification2 => 'Sensitive action verification';

  @override
  String get verifyYourIdentityToContinueWithThis =>
      'Verify your identity to continue with this action.';

  @override
  String get signInPassword => 'Sign-in password';

  @override
  String get passkeyVerificationAvailable => 'Passkey verification available';

  @override
  String get verifying => 'Verifying...';

  @override
  String get save => 'Save';

  @override
  String get saved => 'Saved';

  @override
  String get newEmail => 'New email';

  @override
  String get verificationCode => 'Verification code';

  @override
  String get emailUpdated => 'Email updated';

  @override
  String get saving2 => 'Saving...';

  @override
  String get newPassword => 'New password';

  @override
  String get passwordUpdated => 'Password updated';

  @override
  String get scanTheQrCodeOrEnterThe =>
      'Scan the QR code or enter the secret key manually, then enter the 6-digit code generated by your authenticator.';

  @override
  String get initialRecoveryCodes => 'Initial recovery codes';

  @override
  String get saveYourRecoveryCodes => 'Save your recovery codes';

  @override
  String get enabling => 'Enabling...';

  @override
  String get confirmAndEnable => 'Confirm and enable';

  @override
  String get couldNotGenerateQrCodePleaseRefresh =>
      'Could not generate QR code. Please refresh';

  @override
  String get qrVerification => 'QR verification';

  @override
  String get enterYourCurrentSignInPasswordTo =>
      'Enter your current sign-in password to verify.';

  @override
  String get sendAOneTimeCodeToYour =>
      'Send a one-time code to your linked email address.';

  @override
  String get enterTheDigitCodeGeneratedByYour =>
      'Enter the 6-digit code generated by your authenticator.';

  @override
  String get scanWithYourSignedInMobileApp =>
      'Scan with your signed-in mobile app to confirm this sensitive action.';

  @override
  String get viewAccountStatusSignInMethodsAnd =>
      'View account status, sign-in methods, and recent activity on desktop.';

  @override
  String get manageYourAccountProfileAndExtendedInformation =>
      'Manage your account profile and extended information.';

  @override
  String get configureMfaTotpPasskeysAndSensitiveAction =>
      'Configure MFA, TOTP, passkeys, and sensitive action verification preferences.';

  @override
  String get viewOnlineDevicesSessionsAndTheirCurrent =>
      'View online devices, sessions, and their current status.';

  @override
  String get filterSensitiveActionsAndVerificationRecords =>
      'Filter sensitive actions and verification records.';

  @override
  String get viewThirdPartyAppsAuthorizedToAccess =>
      'View third-party apps authorized to access your account.';

  @override
  String get desktopApp => 'Desktop app';

  @override
  String get mobileApp => 'Mobile app';

  @override
  String get unknownBrowser => 'Unknown browser';

  @override
  String get passwordMfa => 'Password + MFA';

  @override
  String get codeSignIn => 'Code sign-in';

  @override
  String get codeMfa => 'Code + MFA';

  @override
  String get passkeySignIn => 'Passkey sign-in';

  @override
  String get passkeyMfa => 'Passkey + MFA';

  @override
  String get qrCodeMfa => 'QR code + MFA';

  @override
  String get bridgeSignIn => 'Bridge sign-in';

  @override
  String get desktopBridge => 'Desktop bridge';

  @override
  String get webBridge => 'Web bridge';

  @override
  String get mobileBridge => 'Mobile bridge';

  @override
  String get accountRecovery => 'Account recovery';

  @override
  String get generalAction => 'General action';

  @override
  String get high => 'High';

  @override
  String get medium => 'Medium';

  @override
  String get low => 'Low';

  @override
  String seconds(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 seconds',
      one: '1 second',
    );
    return '$_temp0';
  }

  @override
  String minutes(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 minutes',
      one: '1 minute',
    );
    return '$_temp0';
  }

  @override
  String hours(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 hours',
      one: '1 hour',
    );
    return '$_temp0';
  }

  @override
  String days(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 days',
      one: '1 day',
    );
    return '$_temp0';
  }

  @override
  String get justNow => 'Just now';

  @override
  String minutesAgo(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 minutes ago',
      one: '1 minute ago',
    );
    return '$_temp0';
  }

  @override
  String hoursAgo(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 hours ago',
      one: '1 hour ago',
    );
    return '$_temp0';
  }

  @override
  String daysAgo(int arg0) {
    String _temp0 = intl.Intl.pluralLogic(
      arg0,
      locale: localeName,
      other: '$arg0 days ago',
      one: '1 day ago',
    );
    return '$_temp0';
  }

  @override
  String get invalidDataFormat => 'Invalid data format';

  @override
  String get unnamedApp => 'Unnamed app';

  @override
  String passwordMustBeBetweenAndCharactersLong(String arg0, String arg1) {
    return 'Password must be between $arg0 and $arg1 characters long';
  }

  @override
  String get passwordMustContainAtLeastOneUppercase =>
      'Password must contain at least one uppercase letter';

  @override
  String get passwordMustContainAtLeastOneLowercase =>
      'Password must contain at least one lowercase letter';

  @override
  String get passwordMustContainAtLeastOneDigit =>
      'Password must contain at least one digit';

  @override
  String get passwordMustContainAtLeastOneSpecial =>
      'Password must contain at least one special character';

  @override
  String get timedOutWaitingForTheBrowserResult =>
      'Timed out waiting for the browser result';

  @override
  String get browserBridgeStateVerificationFailed =>
      'Browser bridge state verification failed';

  @override
  String get theBrowserDidNotCompleteThePasskey =>
      'The browser did not complete the passkey operation';

  @override
  String get theDesktopAppReceivedThePasskeyResult =>
      'The desktop app received the passkey result. You can close this page.';

  @override
  String hActionCompletedHPPP(String arg0) {
    return '<h2>Action completed</h2><p>$arg0</p><p>You can return to the desktop app.</p></body></html>';
  }

  @override
  String get theDesktopAppDidNotReceiveA =>
      'The desktop app did not receive a valid result.';

  @override
  String hActionFailedHPPP(String arg0) {
    return '<h2>Action failed</h2><p>$arg0</p><p>Return to the desktop app and try again.</p></body></html>';
  }

  @override
  String get couldNotOpenTheExternalBrowser =>
      'Could not open the external browser';

  @override
  String get language => 'Language';

  @override
  String get languageSettingExplanation =>
      'Follow your device language or choose a language for this device.';

  @override
  String get languageSaveFailed =>
      'Could not save your language preference. It may reset after restarting the app.';
}
