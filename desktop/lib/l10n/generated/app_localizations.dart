import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
    Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hans'),
  ];

  /// No description provided for @ksuserSecurity.
  ///
  /// In en, this message translates to:
  /// **'Ksuser Authentication Center'**
  String get ksuserSecurity;

  /// No description provided for @dataRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Data refreshed'**
  String get dataRefreshed;

  /// No description provided for @signInPageRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Sign-in page refreshed'**
  String get signInPageRefreshed;

  /// No description provided for @checkingTheTerms.
  ///
  /// In en, this message translates to:
  /// **'Checking the terms...'**
  String get checkingTheTerms;

  /// No description provided for @beforeYouBegin.
  ///
  /// In en, this message translates to:
  /// **'Before you begin'**
  String get beforeYouBegin;

  /// No description provided for @beforeInstallingAndUsingThisAppFor.
  ///
  /// In en, this message translates to:
  /// **'Before installing and using this app for the first time, please read and accept the following agreements and disclosures. You must accept them to sign in or use the desktop app.'**
  String get beforeInstallingAndUsingThisAppFor;

  /// No description provided for @termsOfService.
  ///
  /// In en, this message translates to:
  /// **'Terms of Service'**
  String get termsOfService;

  /// No description provided for @privacyPolicy.
  ///
  /// In en, this message translates to:
  /// **'Privacy Policy'**
  String get privacyPolicy;

  /// No description provided for @thirdPartyDataSharingDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Third-party Data Sharing Disclosure'**
  String get thirdPartyDataSharingDisclosure;

  /// No description provided for @iHaveReadAndAgreeToThe.
  ///
  /// In en, this message translates to:
  /// **'I have read and agree to the Terms of Service, Privacy Policy, and Third-party Data Sharing Disclosure'**
  String get iHaveReadAndAgreeToThe;

  /// No description provided for @saving.
  ///
  /// In en, this message translates to:
  /// **'Saving...'**
  String get saving;

  /// No description provided for @agreeAndContinue.
  ///
  /// In en, this message translates to:
  /// **'Agree and continue'**
  String get agreeAndContinue;

  /// No description provided for @minimizeToSystemTray.
  ///
  /// In en, this message translates to:
  /// **'Minimize to system tray'**
  String get minimizeToSystemTray;

  /// No description provided for @minimizeToMenuBar.
  ///
  /// In en, this message translates to:
  /// **'Minimize to menu bar'**
  String get minimizeToMenuBar;

  /// No description provided for @couldNotMinimizeTheWindow.
  ///
  /// In en, this message translates to:
  /// **'Could not minimize the window'**
  String get couldNotMinimizeTheWindow;

  /// No description provided for @thisDeviceDoesNotSupportLocalAuthentication.
  ///
  /// In en, this message translates to:
  /// **'This device does not support local authentication'**
  String get thisDeviceDoesNotSupportLocalAuthentication;

  /// No description provided for @verifyYourIdentityBeforeDisplayingTheMobile.
  ///
  /// In en, this message translates to:
  /// **'Verify your identity before displaying the mobile sign-in QR code'**
  String get verifyYourIdentityBeforeDisplayingTheMobile;

  /// No description provided for @mobileQrSignIn.
  ///
  /// In en, this message translates to:
  /// **'Mobile QR sign-in'**
  String get mobileQrSignIn;

  /// No description provided for @generatingQrCode.
  ///
  /// In en, this message translates to:
  /// **'Generating QR code...'**
  String get generatingQrCode;

  /// No description provided for @expiresInSeconds.
  ///
  /// In en, this message translates to:
  /// **'Expires in {arg0} seconds'**
  String expiresInSeconds(String arg0);

  /// No description provided for @thisQrCodeCanOnlyBeUsed.
  ///
  /// In en, this message translates to:
  /// **'This QR code can only be used once. Refresh it after it expires.'**
  String get thisQrCodeCanOnlyBeUsed;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @refreshQrCode.
  ///
  /// In en, this message translates to:
  /// **'Refresh QR code'**
  String get refreshQrCode;

  /// No description provided for @accountRecoveryAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Account recovery authorization'**
  String get accountRecoveryAuthorization;

  /// No description provided for @generatingRecoveryQrCode.
  ///
  /// In en, this message translates to:
  /// **'Generating recovery QR code...'**
  String get generatingRecoveryQrCode;

  /// No description provided for @authorizingDevice.
  ///
  /// In en, this message translates to:
  /// **'Authorizing device: {arg0}'**
  String authorizingDevice(String arg0);

  /// No description provided for @thisDesktopApp.
  ///
  /// In en, this message translates to:
  /// **'This desktop app'**
  String get thisDesktopApp;

  /// No description provided for @location.
  ///
  /// In en, this message translates to:
  /// **'Location: {arg0}'**
  String location(String arg0);

  /// No description provided for @unknownLocation.
  ///
  /// In en, this message translates to:
  /// **'Unknown location'**
  String get unknownLocation;

  /// No description provided for @generatingRecoveryCode.
  ///
  /// In en, this message translates to:
  /// **'Generating recovery code...'**
  String get generatingRecoveryCode;

  /// No description provided for @openTheRecoveryPageOnAnotherDevice.
  ///
  /// In en, this message translates to:
  /// **'Open the recovery page on another device and scan the QR code or enter the recovery code above. Existing sessions will expire after successful recovery.'**
  String get openTheRecoveryPageOnAnotherDevice;

  /// No description provided for @recoveryCodeCopied.
  ///
  /// In en, this message translates to:
  /// **'Recovery code copied'**
  String get recoveryCodeCopied;

  /// No description provided for @copyRecoveryCode.
  ///
  /// In en, this message translates to:
  /// **'Copy recovery code'**
  String get copyRecoveryCode;

  /// No description provided for @openRecoveryPage.
  ///
  /// In en, this message translates to:
  /// **'Open recovery page'**
  String get openRecoveryPage;

  /// No description provided for @refreshing.
  ///
  /// In en, this message translates to:
  /// **'Refreshing...'**
  String get refreshing;

  /// No description provided for @refreshAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Refresh authorization'**
  String get refreshAuthorization;

  /// No description provided for @invalidQrSignInResultPleaseTry.
  ///
  /// In en, this message translates to:
  /// **'Invalid QR sign-in result. Please try again'**
  String get invalidQrSignInResultPleaseTry;

  /// No description provided for @qrRequestRejectedRefreshTheQrCode.
  ///
  /// In en, this message translates to:
  /// **'QR request rejected. Refresh the QR code and try again'**
  String get qrRequestRejectedRefreshTheQrCode;

  /// No description provided for @qrCodeExpiredPleaseRefreshIt.
  ///
  /// In en, this message translates to:
  /// **'QR code expired. Please refresh it'**
  String get qrCodeExpiredPleaseRefreshIt;

  /// No description provided for @qrSignIn.
  ///
  /// In en, this message translates to:
  /// **'QR sign-in'**
  String get qrSignIn;

  /// No description provided for @scanWithASignedInMobileApp.
  ///
  /// In en, this message translates to:
  /// **'Scan with a signed-in mobile app to authorize sign-in on this desktop app.'**
  String get scanWithASignedInMobileApp;

  /// No description provided for @ifMfaIsEnabledCompleteTheRemaining.
  ///
  /// In en, this message translates to:
  /// **'If MFA is enabled, complete the remaining MFA verification on this desktop app after scanning.'**
  String get ifMfaIsEnabledCompleteTheRemaining;

  /// No description provided for @version.
  ///
  /// In en, this message translates to:
  /// **'Version {arg0} ({arg1})'**
  String version(String arg0, String arg1);

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @appearance.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearance;

  /// No description provided for @followSystem.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get followSystem;

  /// No description provided for @light.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get light;

  /// No description provided for @dark.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get dark;

  /// No description provided for @localPreferences.
  ///
  /// In en, this message translates to:
  /// **'Local preferences'**
  String get localPreferences;

  /// No description provided for @compactLayout.
  ///
  /// In en, this message translates to:
  /// **'Compact layout'**
  String get compactLayout;

  /// No description provided for @reduceSpacingToDisplayMoreContentOn.
  ///
  /// In en, this message translates to:
  /// **'Reduce spacing to display more content on desktop'**
  String get reduceSpacingToDisplayMoreContentOn;

  /// No description provided for @reduceMotion.
  ///
  /// In en, this message translates to:
  /// **'Reduce motion'**
  String get reduceMotion;

  /// No description provided for @reduceAnimationsWhenChangingThemesAndOpening.
  ///
  /// In en, this message translates to:
  /// **'Reduce animations when changing themes and opening dialogs'**
  String get reduceAnimationsWhenChangingThemesAndOpening;

  /// No description provided for @about.
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get about;

  /// No description provided for @privacyPolicy2.
  ///
  /// In en, this message translates to:
  /// **'Privacy Policy'**
  String get privacyPolicy2;

  /// No description provided for @restoreDefaults.
  ///
  /// In en, this message translates to:
  /// **'Restore defaults'**
  String get restoreDefaults;

  /// No description provided for @done.
  ///
  /// In en, this message translates to:
  /// **'Done'**
  String get done;

  /// No description provided for @desktopUnifiedAuthenticationClient.
  ///
  /// In en, this message translates to:
  /// **'Desktop unified authentication client'**
  String get desktopUnifiedAuthenticationClient;

  /// No description provided for @theme.
  ///
  /// In en, this message translates to:
  /// **'Theme: {arg0}'**
  String theme(String arg0);

  /// No description provided for @localPreferences2.
  ///
  /// In en, this message translates to:
  /// **'Local preferences: {arg0}{arg1}'**
  String localPreferences2(String arg0, String arg1);

  /// No description provided for @compactLayout2.
  ///
  /// In en, this message translates to:
  /// **'Compact layout, '**
  String get compactLayout2;

  /// No description provided for @standardAnimations.
  ///
  /// In en, this message translates to:
  /// **'Standard animations'**
  String get standardAnimations;

  /// No description provided for @system.
  ///
  /// In en, this message translates to:
  /// **'System'**
  String get system;

  /// No description provided for @verification.
  ///
  /// In en, this message translates to:
  /// **'verification'**
  String get verification;

  /// No description provided for @useTheNativePasskeyServiceFor.
  ///
  /// In en, this message translates to:
  /// **'Use the native {arg0} passkey service for {arg1}.'**
  String useTheNativePasskeyServiceFor(String arg0, String arg1);

  /// No description provided for @nativePasskeysAreNotEnabledOnThis.
  ///
  /// In en, this message translates to:
  /// **'Native {arg0} passkeys are not enabled on this device.'**
  String nativePasskeysAreNotEnabledOnThis(String arg0);

  /// No description provided for @nativePasskeysAreNotSupportedOnThis.
  ///
  /// In en, this message translates to:
  /// **'Native passkeys are not supported on this platform'**
  String get nativePasskeysAreNotSupportedOnThis;

  /// No description provided for @noPasskeyCredentialWasReceived.
  ///
  /// In en, this message translates to:
  /// **'No passkey credential was received'**
  String get noPasskeyCredentialWasReceived;

  /// No description provided for @passkeyVerificationFailed.
  ///
  /// In en, this message translates to:
  /// **'Passkey verification failed'**
  String get passkeyVerificationFailed;

  /// No description provided for @noPasskeyCredentialWasCreated.
  ///
  /// In en, this message translates to:
  /// **'No passkey credential was created'**
  String get noPasskeyCredentialWasCreated;

  /// No description provided for @couldNotCreatePasskey.
  ///
  /// In en, this message translates to:
  /// **'Could not create passkey'**
  String get couldNotCreatePasskey;

  /// No description provided for @signInWithAppleIsOnlyAvailable.
  ///
  /// In en, this message translates to:
  /// **'Sign in with Apple is only available on macOS'**
  String get signInWithAppleIsOnlyAvailable;

  /// No description provided for @noMacosSignInWithAppleConfiguration.
  ///
  /// In en, this message translates to:
  /// **'No macOS Sign in with Apple configuration was received'**
  String get noMacosSignInWithAppleConfiguration;

  /// No description provided for @couldNotReadTheSignInWith.
  ///
  /// In en, this message translates to:
  /// **'Could not read the Sign in with Apple configuration'**
  String get couldNotReadTheSignInWith;

  /// No description provided for @appleDidNotReturnCompleteSignIn.
  ///
  /// In en, this message translates to:
  /// **'Apple did not return complete sign-in credentials'**
  String get appleDidNotReturnCompleteSignIn;

  /// No description provided for @signInWithAppleCanceled.
  ///
  /// In en, this message translates to:
  /// **'Sign in with Apple canceled'**
  String get signInWithAppleCanceled;

  /// No description provided for @signInWithAppleFailedPleaseTry.
  ///
  /// In en, this message translates to:
  /// **'Sign in with Apple failed. Please try again'**
  String get signInWithAppleFailedPleaseTry;

  /// No description provided for @localAuthenticationIsNotSupportedOnThis.
  ///
  /// In en, this message translates to:
  /// **'Local authentication is not supported on this platform'**
  String get localAuthenticationIsNotSupportedOnThis;

  /// No description provided for @localAuthenticationFailed.
  ///
  /// In en, this message translates to:
  /// **'Local authentication failed'**
  String get localAuthenticationFailed;

  /// No description provided for @systemAuthenticationCanceled.
  ///
  /// In en, this message translates to:
  /// **'System authentication canceled'**
  String get systemAuthenticationCanceled;

  /// No description provided for @windowsHelloPinOrAnotherSystemAuthentication.
  ///
  /// In en, this message translates to:
  /// **'Windows Hello, PIN, or another system authentication method is not enabled on this device'**
  String get windowsHelloPinOrAnotherSystemAuthentication;

  /// No description provided for @windowsAuthenticationFailed.
  ///
  /// In en, this message translates to:
  /// **'Windows authentication failed'**
  String get windowsAuthenticationFailed;

  /// No description provided for @localAuthenticationCanceled.
  ///
  /// In en, this message translates to:
  /// **'Local authentication canceled'**
  String get localAuthenticationCanceled;

  /// No description provided for @localAuthenticationFailed2.
  ///
  /// In en, this message translates to:
  /// **'Local authentication failed'**
  String get localAuthenticationFailed2;

  /// No description provided for @processingAuthentication.
  ///
  /// In en, this message translates to:
  /// **'Processing authentication...'**
  String get processingAuthentication;

  /// No description provided for @syncingDesktopData.
  ///
  /// In en, this message translates to:
  /// **'Syncing desktop data...'**
  String get syncingDesktopData;

  /// No description provided for @processing.
  ///
  /// In en, this message translates to:
  /// **'Processing...'**
  String get processing;

  /// No description provided for @couldNotLoadAuthorizedAppsPleaseTry.
  ///
  /// In en, this message translates to:
  /// **'Could not load authorized apps. Please try again later'**
  String get couldNotLoadAuthorizedAppsPleaseTry;

  /// No description provided for @signingIn.
  ///
  /// In en, this message translates to:
  /// **'Signing in...'**
  String get signingIn;

  /// No description provided for @sendingSignInCode.
  ///
  /// In en, this message translates to:
  /// **'Sending sign-in code...'**
  String get sendingSignInCode;

  /// No description provided for @signingInWithApple.
  ///
  /// In en, this message translates to:
  /// **'Signing in with Apple...'**
  String get signingInWithApple;

  /// No description provided for @incompleteAppleSignInChallengePleaseTry.
  ///
  /// In en, this message translates to:
  /// **'Incomplete Apple sign-in challenge. Please try again'**
  String get incompleteAppleSignInChallengePleaseTry;

  /// No description provided for @linkYourAccountOnTheWebOr.
  ///
  /// In en, this message translates to:
  /// **'Link your account on the web or mobile app before using this sign-in provider'**
  String get linkYourAccountOnTheWebOr;

  /// No description provided for @verifyingAdditionalAuthentication.
  ///
  /// In en, this message translates to:
  /// **'Verifying additional authentication...'**
  String get verifyingAdditionalAuthentication;

  /// No description provided for @waitingForPasskeyMfaVerification.
  ///
  /// In en, this message translates to:
  /// **'Waiting for passkey MFA verification...'**
  String get waitingForPasskeyMfaVerification;

  /// No description provided for @theBrowserDidNotReturnASign.
  ///
  /// In en, this message translates to:
  /// **'The browser did not return a sign-in result'**
  String get theBrowserDidNotReturnASign;

  /// No description provided for @loadingPasswordRequirements.
  ///
  /// In en, this message translates to:
  /// **'Loading password requirements...'**
  String get loadingPasswordRequirements;

  /// No description provided for @sendingRegistrationCode.
  ///
  /// In en, this message translates to:
  /// **'Sending registration code...'**
  String get sendingRegistrationCode;

  /// No description provided for @creatingAccount.
  ///
  /// In en, this message translates to:
  /// **'Creating account...'**
  String get creatingAccount;

  /// No description provided for @couldNotLoadDesktopData.
  ///
  /// In en, this message translates to:
  /// **'Could not load desktop data'**
  String get couldNotLoadDesktopData;

  /// No description provided for @refreshingActivityLogs.
  ///
  /// In en, this message translates to:
  /// **'Refreshing activity logs...'**
  String get refreshingActivityLogs;

  /// No description provided for @refreshingOnlineDevices.
  ///
  /// In en, this message translates to:
  /// **'Refreshing online devices...'**
  String get refreshingOnlineDevices;

  /// No description provided for @refreshingPasskeys.
  ///
  /// In en, this message translates to:
  /// **'Refreshing passkeys...'**
  String get refreshingPasskeys;

  /// No description provided for @refreshingTotpStatus.
  ///
  /// In en, this message translates to:
  /// **'Refreshing TOTP status...'**
  String get refreshingTotpStatus;

  /// No description provided for @refreshingContinuousAuthenticationStatus.
  ///
  /// In en, this message translates to:
  /// **'Refreshing continuous authentication status...'**
  String get refreshingContinuousAuthenticationStatus;

  /// No description provided for @savingSettings.
  ///
  /// In en, this message translates to:
  /// **'Saving settings...'**
  String get savingSettings;

  /// No description provided for @savingProfile.
  ///
  /// In en, this message translates to:
  /// **'Saving profile...'**
  String get savingProfile;

  /// No description provided for @uploadingAvatar.
  ///
  /// In en, this message translates to:
  /// **'Uploading avatar...'**
  String get uploadingAvatar;

  /// No description provided for @checkingVerificationStatus.
  ///
  /// In en, this message translates to:
  /// **'Checking verification status...'**
  String get checkingVerificationStatus;

  /// No description provided for @checkingContinuousAuthenticationStatus.
  ///
  /// In en, this message translates to:
  /// **'Checking continuous authentication status...'**
  String get checkingContinuousAuthenticationStatus;

  /// No description provided for @sendingVerificationEmail.
  ///
  /// In en, this message translates to:
  /// **'Sending verification email...'**
  String get sendingVerificationEmail;

  /// No description provided for @verifyingIdentity.
  ///
  /// In en, this message translates to:
  /// **'Verifying identity...'**
  String get verifyingIdentity;

  /// No description provided for @noUsableBrowserPasskeyBridgeUrlIs.
  ///
  /// In en, this message translates to:
  /// **'No usable browser passkey bridge URL is configured for this environment'**
  String get noUsableBrowserPasskeyBridgeUrlIs;

  /// No description provided for @passkeysAreNotSupportedOnThisPlatform.
  ///
  /// In en, this message translates to:
  /// **'Passkeys are not supported on this platform'**
  String get passkeysAreNotSupportedOnThisPlatform;

  /// No description provided for @waitingForPasskeySignIn.
  ///
  /// In en, this message translates to:
  /// **'Waiting for passkey sign-in...'**
  String get waitingForPasskeySignIn;

  /// No description provided for @waitingForPasskeyVerification.
  ///
  /// In en, this message translates to:
  /// **'Waiting for passkey verification...'**
  String get waitingForPasskeyVerification;

  /// No description provided for @theBrowserDidNotCompleteVerificationFor.
  ///
  /// In en, this message translates to:
  /// **'The browser did not complete verification for the sensitive action'**
  String get theBrowserDidNotCompleteVerificationFor;

  /// No description provided for @waitingForPasskeyRegistration.
  ///
  /// In en, this message translates to:
  /// **'Waiting for passkey registration...'**
  String get waitingForPasskeyRegistration;

  /// No description provided for @theBrowserDidNotCompletePasskeyRegistration.
  ///
  /// In en, this message translates to:
  /// **'The browser did not complete passkey registration'**
  String get theBrowserDidNotCompletePasskeyRegistration;

  /// No description provided for @thisEnvironmentPreventsALocalCallbackPort.
  ///
  /// In en, this message translates to:
  /// **'This environment prevents a local callback port from being created. The browser passkey bridge is unavailable.'**
  String get thisEnvironmentPreventsALocalCallbackPort;

  /// No description provided for @checkNetworkInboundPermissionsOrEnableNative.
  ///
  /// In en, this message translates to:
  /// **'Check network inbound permissions or enable native passkeys on macOS.'**
  String get checkNetworkInboundPermissionsOrEnableNative;

  /// No description provided for @couldNotCreateTheLocalPasskeyCallback.
  ///
  /// In en, this message translates to:
  /// **'Could not create the local passkey callback server: {arg0}'**
  String couldNotCreateTheLocalPasskeyCallback(String arg0);

  /// No description provided for @sendingEmailVerificationCode.
  ///
  /// In en, this message translates to:
  /// **'Sending email verification code...'**
  String get sendingEmailVerificationCode;

  /// No description provided for @updatingEmail.
  ///
  /// In en, this message translates to:
  /// **'Updating email...'**
  String get updatingEmail;

  /// No description provided for @updatingPassword.
  ///
  /// In en, this message translates to:
  /// **'Updating password...'**
  String get updatingPassword;

  /// No description provided for @revokingSession.
  ///
  /// In en, this message translates to:
  /// **'Revoking session...'**
  String get revokingSession;

  /// No description provided for @signingOutAllDevices.
  ///
  /// In en, this message translates to:
  /// **'Signing out all devices...'**
  String get signingOutAllDevices;

  /// No description provided for @renamingPasskey.
  ///
  /// In en, this message translates to:
  /// **'Renaming passkey...'**
  String get renamingPasskey;

  /// No description provided for @deletingPasskey.
  ///
  /// In en, this message translates to:
  /// **'Deleting passkey...'**
  String get deletingPasskey;

  /// No description provided for @gettingTotpConfiguration.
  ///
  /// In en, this message translates to:
  /// **'Getting TOTP configuration...'**
  String get gettingTotpConfiguration;

  /// No description provided for @enablingTotp.
  ///
  /// In en, this message translates to:
  /// **'Enabling TOTP...'**
  String get enablingTotp;

  /// No description provided for @loadingRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Loading recovery codes...'**
  String get loadingRecoveryCodes;

  /// No description provided for @generatingNewRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Generating new recovery codes...'**
  String get generatingNewRecoveryCodes;

  /// No description provided for @disablingTotp.
  ///
  /// In en, this message translates to:
  /// **'Disabling TOTP...'**
  String get disablingTotp;

  /// No description provided for @signingOut.
  ///
  /// In en, this message translates to:
  /// **'Signing out...'**
  String get signingOut;

  /// No description provided for @generatingCrossDeviceSignInTicket.
  ///
  /// In en, this message translates to:
  /// **'Generating cross-device sign-in ticket...'**
  String get generatingCrossDeviceSignInTicket;

  /// No description provided for @thisDesktopAppIsNotSignedIn.
  ///
  /// In en, this message translates to:
  /// **'This desktop app is not signed in'**
  String get thisDesktopAppIsNotSignedIn;

  /// No description provided for @generatingAccountRecoveryAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Generating account recovery authorization...'**
  String get generatingAccountRecoveryAuthorization;

  /// No description provided for @syncingWebSignInStatus.
  ///
  /// In en, this message translates to:
  /// **'Syncing web sign-in status...'**
  String get syncingWebSignInStatus;

  /// No description provided for @crossDeviceSignInFailedNoAccess.
  ///
  /// In en, this message translates to:
  /// **'Cross-device sign-in failed. No access token was received'**
  String get crossDeviceSignInFailedNoAccess;

  /// No description provided for @networkConnectionFailed.
  ///
  /// In en, this message translates to:
  /// **'Network connection failed: {arg0}'**
  String networkConnectionFailed(String arg0);

  /// No description provided for @avatarUploadFailed.
  ///
  /// In en, this message translates to:
  /// **'Avatar upload failed: {arg0}'**
  String avatarUploadFailed(String arg0);

  /// No description provided for @couldNotConnectTo.
  ///
  /// In en, this message translates to:
  /// **'Could not connect to {arg0}: {arg1}'**
  String couldNotConnectTo(String arg0, String arg1);

  /// No description provided for @tlsHandshakeFailedPleaseCheckTheServer.
  ///
  /// In en, this message translates to:
  /// **'TLS handshake failed. Please check the server certificate'**
  String get tlsHandshakeFailedPleaseCheckTheServer;

  /// No description provided for @requestTimedOutPleaseTryAgainLater.
  ///
  /// In en, this message translates to:
  /// **'Request timed out. Please try again later'**
  String get requestTimedOutPleaseTryAgainLater;

  /// No description provided for @theServerReturnedUnreadableData.
  ///
  /// In en, this message translates to:
  /// **'The server returned unreadable data'**
  String get theServerReturnedUnreadableData;

  /// No description provided for @authenticationExpiredPleaseSignInAgain.
  ///
  /// In en, this message translates to:
  /// **'Authentication expired. Please sign in again'**
  String get authenticationExpiredPleaseSignInAgain;

  /// No description provided for @couldNotRefreshTheToken.
  ///
  /// In en, this message translates to:
  /// **'Could not refresh the token'**
  String get couldNotRefreshTheToken;

  /// No description provided for @enterYourEmailAddressFirst.
  ///
  /// In en, this message translates to:
  /// **'Enter your email address first'**
  String get enterYourEmailAddressFirst;

  /// No description provided for @enterYourPassword.
  ///
  /// In en, this message translates to:
  /// **'Enter your password'**
  String get enterYourPassword;

  /// No description provided for @enterADigitVerificationCode.
  ///
  /// In en, this message translates to:
  /// **'Enter a 6-digit verification code'**
  String get enterADigitVerificationCode;

  /// No description provided for @signedIn.
  ///
  /// In en, this message translates to:
  /// **'Signed in'**
  String get signedIn;

  /// No description provided for @firstVerificationCompletedContinueWithAdditionalVerification.
  ///
  /// In en, this message translates to:
  /// **'First verification completed. Continue with additional verification'**
  String get firstVerificationCompletedContinueWithAdditionalVerification;

  /// No description provided for @verificationCodeSent.
  ///
  /// In en, this message translates to:
  /// **'Verification code sent'**
  String get verificationCodeSent;

  /// No description provided for @additionalVerificationCompleted.
  ///
  /// In en, this message translates to:
  /// **'Additional verification completed'**
  String get additionalVerificationCompleted;

  /// No description provided for @enterADigitAuthenticatorCode.
  ///
  /// In en, this message translates to:
  /// **'Enter a 6-digit authenticator code'**
  String get enterADigitAuthenticatorCode;

  /// No description provided for @enterARecoveryCode.
  ///
  /// In en, this message translates to:
  /// **'Enter a recovery code'**
  String get enterARecoveryCode;

  /// No description provided for @signedInWithPasskey.
  ///
  /// In en, this message translates to:
  /// **'Signed in with passkey'**
  String get signedInWithPasskey;

  /// No description provided for @passkeyVerifiedContinueWithAdditionalVerification.
  ///
  /// In en, this message translates to:
  /// **'Passkey verified. Continue with additional verification'**
  String get passkeyVerifiedContinueWithAdditionalVerification;

  /// No description provided for @signedInWithApple.
  ///
  /// In en, this message translates to:
  /// **'Signed in with Apple'**
  String get signedInWithApple;

  /// No description provided for @appleVerifiedContinueWithAdditionalVerification.
  ///
  /// In en, this message translates to:
  /// **'Apple verified. Continue with additional verification'**
  String get appleVerifiedContinueWithAdditionalVerification;

  /// No description provided for @signedInWithQrCode.
  ///
  /// In en, this message translates to:
  /// **'Signed in with QR code'**
  String get signedInWithQrCode;

  /// No description provided for @qrCodeVerifiedContinueWithAdditionalVerification.
  ///
  /// In en, this message translates to:
  /// **'QR code verified. Continue with additional verification'**
  String get qrCodeVerifiedContinueWithAdditionalVerification;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get signIn;

  /// No description provided for @signInToKsuserSecurityToManage.
  ///
  /// In en, this message translates to:
  /// **'Sign in to Ksuser Authentication Center to manage your account and devices.'**
  String get signInToKsuserSecurityToManage;

  /// No description provided for @password.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get password;

  /// No description provided for @emailCode.
  ///
  /// In en, this message translates to:
  /// **'Email code'**
  String get emailCode;

  /// No description provided for @emailAddress.
  ///
  /// In en, this message translates to:
  /// **'Email address'**
  String get emailAddress;

  /// No description provided for @digitEmailCode.
  ///
  /// In en, this message translates to:
  /// **'6-digit email code'**
  String get digitEmailCode;

  /// No description provided for @sendCode.
  ///
  /// In en, this message translates to:
  /// **'Send code'**
  String get sendCode;

  /// No description provided for @otherSignInMethods.
  ///
  /// In en, this message translates to:
  /// **'Other sign-in methods'**
  String get otherSignInMethods;

  /// No description provided for @signInWithApple.
  ///
  /// In en, this message translates to:
  /// **'Sign in with Apple'**
  String get signInWithApple;

  /// No description provided for @qrSignIn2.
  ///
  /// In en, this message translates to:
  /// **'QR sign-in'**
  String get qrSignIn2;

  /// No description provided for @newToKsuserRegisterOnTheWeb.
  ///
  /// In en, this message translates to:
  /// **'New to Ksuser? Register on the web'**
  String get newToKsuserRegisterOnTheWeb;

  /// No description provided for @accountSecurityConsole.
  ///
  /// In en, this message translates to:
  /// **'Account security console'**
  String get accountSecurityConsole;

  /// No description provided for @workspace.
  ///
  /// In en, this message translates to:
  /// **'Workspace'**
  String get workspace;

  /// No description provided for @accountOverview.
  ///
  /// In en, this message translates to:
  /// **'Account overview'**
  String get accountOverview;

  /// No description provided for @accountProfile.
  ///
  /// In en, this message translates to:
  /// **'Account profile'**
  String get accountProfile;

  /// No description provided for @securitySettings.
  ///
  /// In en, this message translates to:
  /// **'Security settings'**
  String get securitySettings;

  /// No description provided for @deviceManagement.
  ///
  /// In en, this message translates to:
  /// **'Device management'**
  String get deviceManagement;

  /// No description provided for @activityLogs.
  ///
  /// In en, this message translates to:
  /// **'Activity logs'**
  String get activityLogs;

  /// No description provided for @accessAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Access authorization'**
  String get accessAuthorization;

  /// No description provided for @crossDeviceAccess.
  ///
  /// In en, this message translates to:
  /// **'Cross-device access'**
  String get crossDeviceAccess;

  /// No description provided for @openTheWebAppAndSignIn.
  ///
  /// In en, this message translates to:
  /// **'Open the web app and sign in automatically'**
  String get openTheWebAppAndSignIn;

  /// No description provided for @openingTheWebApp.
  ///
  /// In en, this message translates to:
  /// **'Opening the web app...'**
  String get openingTheWebApp;

  /// No description provided for @webAppOpenedAndSignedInAutomatically.
  ///
  /// In en, this message translates to:
  /// **'Web app opened and signed in automatically'**
  String get webAppOpenedAndSignedInAutomatically;

  /// No description provided for @syncWebSignInToDesktop.
  ///
  /// In en, this message translates to:
  /// **'Sync web sign-in to desktop'**
  String get syncWebSignInToDesktop;

  /// No description provided for @openingWebSignIn.
  ///
  /// In en, this message translates to:
  /// **'Opening web sign-in...'**
  String get openingWebSignIn;

  /// No description provided for @webSignInOpenedYourSessionWill.
  ///
  /// In en, this message translates to:
  /// **'Web sign-in opened. Your session will sync to desktop after you sign in'**
  String get webSignInOpenedYourSessionWill;

  /// No description provided for @openWebApp.
  ///
  /// In en, this message translates to:
  /// **'Open web app'**
  String get openWebApp;

  /// No description provided for @syncWebSignIn.
  ///
  /// In en, this message translates to:
  /// **'Sync web sign-in'**
  String get syncWebSignIn;

  /// No description provided for @accountActions.
  ///
  /// In en, this message translates to:
  /// **'Account actions'**
  String get accountActions;

  /// No description provided for @signOut.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get signOut;

  /// No description provided for @desktopDataRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Desktop data refreshed'**
  String get desktopDataRefreshed;

  /// No description provided for @refresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// No description provided for @profileSummary.
  ///
  /// In en, this message translates to:
  /// **'Profile summary'**
  String get profileSummary;

  /// No description provided for @yourAccountAtAGlance.
  ///
  /// In en, this message translates to:
  /// **'Your account at a glance'**
  String get yourAccountAtAGlance;

  /// No description provided for @fullName.
  ///
  /// In en, this message translates to:
  /// **'Full name'**
  String get fullName;

  /// No description provided for @notProvided.
  ///
  /// In en, this message translates to:
  /// **'Not provided'**
  String get notProvided;

  /// No description provided for @region.
  ///
  /// In en, this message translates to:
  /// **'Region'**
  String get region;

  /// No description provided for @gender.
  ///
  /// In en, this message translates to:
  /// **'Gender'**
  String get gender;

  /// No description provided for @profileUpdated.
  ///
  /// In en, this message translates to:
  /// **'Profile updated'**
  String get profileUpdated;

  /// No description provided for @signInMethods.
  ///
  /// In en, this message translates to:
  /// **'Sign-in methods'**
  String get signInMethods;

  /// No description provided for @availableAuthenticationMethods.
  ///
  /// In en, this message translates to:
  /// **'Available authentication methods'**
  String get availableAuthenticationMethods;

  /// No description provided for @recentDeviceActivity.
  ///
  /// In en, this message translates to:
  /// **'Recent device activity'**
  String get recentDeviceActivity;

  /// No description provided for @recentDevicesAndSessions.
  ///
  /// In en, this message translates to:
  /// **'Recent devices and sessions'**
  String get recentDevicesAndSessions;

  /// No description provided for @noDeviceActivityYet.
  ///
  /// In en, this message translates to:
  /// **'No device activity yet'**
  String get noDeviceActivityYet;

  /// No description provided for @recentSensitiveActions.
  ///
  /// In en, this message translates to:
  /// **'Recent sensitive actions'**
  String get recentSensitiveActions;

  /// No description provided for @recentAccountSecurityActivity.
  ///
  /// In en, this message translates to:
  /// **'Recent account security activity'**
  String get recentAccountSecurityActivity;

  /// No description provided for @noSensitiveActionsYet.
  ///
  /// In en, this message translates to:
  /// **'No sensitive actions yet'**
  String get noSensitiveActionsYet;

  /// No description provided for @accountIdentity.
  ///
  /// In en, this message translates to:
  /// **'Account identity'**
  String get accountIdentity;

  /// No description provided for @securityScore.
  ///
  /// In en, this message translates to:
  /// **'Security score'**
  String get securityScore;

  /// No description provided for @totpProtectionEnabled.
  ///
  /// In en, this message translates to:
  /// **'TOTP protection enabled'**
  String get totpProtectionEnabled;

  /// No description provided for @enableTotpProtection.
  ///
  /// In en, this message translates to:
  /// **'Enable TOTP protection'**
  String get enableTotpProtection;

  /// No description provided for @onlineDevices.
  ///
  /// In en, this message translates to:
  /// **'Online devices'**
  String get onlineDevices;

  /// No description provided for @devicesOnline.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 device online} other{{arg0} devices online}}'**
  String devicesOnline(int arg0);

  /// No description provided for @activityLogs2.
  ///
  /// In en, this message translates to:
  /// **'Activity logs'**
  String get activityLogs2;

  /// No description provided for @recentAccountSecurityActions.
  ///
  /// In en, this message translates to:
  /// **'Recent account security actions'**
  String get recentAccountSecurityActions;

  /// No description provided for @authorizedApps.
  ///
  /// In en, this message translates to:
  /// **'Authorized apps'**
  String get authorizedApps;

  /// No description provided for @viewThirdPartyAppsThatCanAccess.
  ///
  /// In en, this message translates to:
  /// **'View third-party apps that can access your account and their permissions.'**
  String get viewThirdPartyAppsThatCanAccess;

  /// No description provided for @apps.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 app} other{{arg0} apps}}'**
  String apps(int arg0);

  /// No description provided for @authorizationInformationIsCurrentlyUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Authorization information is currently unavailable'**
  String get authorizationInformationIsCurrentlyUnavailable;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @noAuthorizedAppsYet.
  ///
  /// In en, this message translates to:
  /// **'No authorized apps yet'**
  String get noAuthorizedAppsYet;

  /// No description provided for @thirdPartyAppsYouAuthorizeWillAppear.
  ///
  /// In en, this message translates to:
  /// **'Third-party apps you authorize will appear here.'**
  String get thirdPartyAppsYouAuthorizeWillAppear;

  /// No description provided for @aboutAuthorization.
  ///
  /// In en, this message translates to:
  /// **'About authorization'**
  String get aboutAuthorization;

  /// No description provided for @youCanManageThirdPartyAppsOn.
  ///
  /// In en, this message translates to:
  /// **'You can manage third-party apps on the web authorization page at any time.'**
  String get youCanManageThirdPartyAppsOn;

  /// No description provided for @afterYouRevokeAccessTheAppCan.
  ///
  /// In en, this message translates to:
  /// **'After you revoke access, the app can no longer use its existing authorization to access your account.'**
  String get afterYouRevokeAccessTheAppCan;

  /// No description provided for @revokeAccessFor.
  ///
  /// In en, this message translates to:
  /// **'Revoke access for {arg0}?'**
  String revokeAccessFor(String arg0);

  /// No description provided for @theAppWillNoLongerBeAble.
  ///
  /// In en, this message translates to:
  /// **'The app will no longer be able to access the account information you authorized. Using it again requires a new authorization.'**
  String get theAppWillNoLongerBeAble;

  /// No description provided for @keepAccess.
  ///
  /// In en, this message translates to:
  /// **'Keep access'**
  String get keepAccess;

  /// No description provided for @revokeAccess.
  ///
  /// In en, this message translates to:
  /// **'Revoke access'**
  String get revokeAccess;

  /// No description provided for @accessRevokedFor.
  ///
  /// In en, this message translates to:
  /// **'Access revoked for {arg0}'**
  String accessRevokedFor(String arg0);

  /// No description provided for @oneTimeAccess.
  ///
  /// In en, this message translates to:
  /// **'One-time access'**
  String get oneTimeAccess;

  /// No description provided for @noExpiration.
  ///
  /// In en, this message translates to:
  /// **'No expiration'**
  String get noExpiration;

  /// No description provided for @expires.
  ///
  /// In en, this message translates to:
  /// **'Expires {arg0}'**
  String expires(String arg0);

  /// No description provided for @developer.
  ///
  /// In en, this message translates to:
  /// **'Developer: {arg0}'**
  String developer(String arg0);

  /// No description provided for @contact.
  ///
  /// In en, this message translates to:
  /// **'Contact: {arg0}'**
  String contact(String arg0);

  /// No description provided for @lastUsed.
  ///
  /// In en, this message translates to:
  /// **'Last used {arg0}'**
  String lastUsed(String arg0);

  /// No description provided for @revokeAccess2.
  ///
  /// In en, this message translates to:
  /// **'Revoke access'**
  String get revokeAccess2;

  /// No description provided for @timeLimitedAccess.
  ///
  /// In en, this message translates to:
  /// **'Time-limited access'**
  String get timeLimitedAccess;

  /// No description provided for @permanentAccess.
  ///
  /// In en, this message translates to:
  /// **'Permanent access'**
  String get permanentAccess;

  /// No description provided for @accountIdentifier.
  ///
  /// In en, this message translates to:
  /// **'Account identifier'**
  String get accountIdentifier;

  /// No description provided for @basicProfile.
  ///
  /// In en, this message translates to:
  /// **'Basic profile'**
  String get basicProfile;

  /// No description provided for @email.
  ///
  /// In en, this message translates to:
  /// **'Email'**
  String get email;

  /// No description provided for @phoneNumber.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get phoneNumber;

  /// No description provided for @images.
  ///
  /// In en, this message translates to:
  /// **'Images'**
  String get images;

  /// No description provided for @chooseAvatar.
  ///
  /// In en, this message translates to:
  /// **'Choose avatar'**
  String get chooseAvatar;

  /// No description provided for @imageMustBeNoLargerThanMb.
  ///
  /// In en, this message translates to:
  /// **'Image must be no larger than 3 MB'**
  String get imageMustBeNoLargerThanMb;

  /// No description provided for @onlyJpgPngWebpOrGifImages.
  ///
  /// In en, this message translates to:
  /// **'Only JPG, PNG, WebP, or GIF images are supported'**
  String get onlyJpgPngWebpOrGifImages;

  /// No description provided for @avatarUpdated.
  ///
  /// In en, this message translates to:
  /// **'Avatar updated'**
  String get avatarUpdated;

  /// No description provided for @personalInformation.
  ///
  /// In en, this message translates to:
  /// **'Personal information'**
  String get personalInformation;

  /// No description provided for @keepYourBasicProfileAccurateAndComplete.
  ///
  /// In en, this message translates to:
  /// **'Keep your basic profile accurate and complete.'**
  String get keepYourBasicProfileAccurateAndComplete;

  /// No description provided for @clickToChangeAvatar.
  ///
  /// In en, this message translates to:
  /// **'Click to change avatar'**
  String get clickToChangeAvatar;

  /// No description provided for @clickToChangeAvatarJpgPngWebp.
  ///
  /// In en, this message translates to:
  /// **'Click to change avatar · JPG, PNG, WebP, or GIF · Up to 3 MB'**
  String get clickToChangeAvatarJpgPngWebp;

  /// No description provided for @changeAvatar.
  ///
  /// In en, this message translates to:
  /// **'Change avatar'**
  String get changeAvatar;

  /// No description provided for @username.
  ///
  /// In en, this message translates to:
  /// **'Username'**
  String get username;

  /// No description provided for @changeUsername.
  ///
  /// In en, this message translates to:
  /// **'Change username'**
  String get changeUsername;

  /// No description provided for @extendedProfile.
  ///
  /// In en, this message translates to:
  /// **'Extended profile'**
  String get extendedProfile;

  /// No description provided for @viewAndEditYourPersonalInformation.
  ///
  /// In en, this message translates to:
  /// **'View and edit your personal information'**
  String get viewAndEditYourPersonalInformation;

  /// No description provided for @changeFullName.
  ///
  /// In en, this message translates to:
  /// **'Change full name'**
  String get changeFullName;

  /// No description provided for @selectGender.
  ///
  /// In en, this message translates to:
  /// **'Select gender'**
  String get selectGender;

  /// No description provided for @male.
  ///
  /// In en, this message translates to:
  /// **'Male'**
  String get male;

  /// No description provided for @female.
  ///
  /// In en, this message translates to:
  /// **'Female'**
  String get female;

  /// No description provided for @preferNotToSay.
  ///
  /// In en, this message translates to:
  /// **'Prefer not to say'**
  String get preferNotToSay;

  /// No description provided for @dateOfBirth.
  ///
  /// In en, this message translates to:
  /// **'Date of birth'**
  String get dateOfBirth;

  /// No description provided for @changeDateOfBirth.
  ///
  /// In en, this message translates to:
  /// **'Change date of birth'**
  String get changeDateOfBirth;

  /// No description provided for @changeRegion.
  ///
  /// In en, this message translates to:
  /// **'Change region'**
  String get changeRegion;

  /// No description provided for @bio.
  ///
  /// In en, this message translates to:
  /// **'Bio'**
  String get bio;

  /// No description provided for @changeBio.
  ///
  /// In en, this message translates to:
  /// **'Change bio'**
  String get changeBio;

  /// No description provided for @securityOptions.
  ///
  /// In en, this message translates to:
  /// **'Security options'**
  String get securityOptions;

  /// No description provided for @manageYourAccountSecurityPreferences.
  ///
  /// In en, this message translates to:
  /// **'Manage your account security preferences'**
  String get manageYourAccountSecurityPreferences;

  /// No description provided for @enableMfa.
  ///
  /// In en, this message translates to:
  /// **'Enable MFA'**
  String get enableMfa;

  /// No description provided for @addASecondVerificationStepWhenSigning.
  ///
  /// In en, this message translates to:
  /// **'Add a second verification step when signing in.'**
  String get addASecondVerificationStepWhenSigning;

  /// No description provided for @mfaEnabled.
  ///
  /// In en, this message translates to:
  /// **'MFA enabled'**
  String get mfaEnabled;

  /// No description provided for @mfaDisabled.
  ///
  /// In en, this message translates to:
  /// **'MFA disabled'**
  String get mfaDisabled;

  /// No description provided for @unusualLocationDetection.
  ///
  /// In en, this message translates to:
  /// **'Unusual location detection'**
  String get unusualLocationDetection;

  /// No description provided for @getAlertsWhenASignInComes.
  ///
  /// In en, this message translates to:
  /// **'Get alerts when a sign-in comes from an unusual location.'**
  String get getAlertsWhenASignInComes;

  /// No description provided for @unusualLocationDetectionEnabled.
  ///
  /// In en, this message translates to:
  /// **'Unusual location detection enabled'**
  String get unusualLocationDetectionEnabled;

  /// No description provided for @unusualLocationDetectionDisabled.
  ///
  /// In en, this message translates to:
  /// **'Unusual location detection disabled'**
  String get unusualLocationDetectionDisabled;

  /// No description provided for @sensitiveActionEmails.
  ///
  /// In en, this message translates to:
  /// **'Sensitive action emails'**
  String get sensitiveActionEmails;

  /// No description provided for @getNotificationsForPasswordEmailAndOther.
  ///
  /// In en, this message translates to:
  /// **'Get notifications for password, email, and other sensitive changes.'**
  String get getNotificationsForPasswordEmailAndOther;

  /// No description provided for @notificationsEnabled.
  ///
  /// In en, this message translates to:
  /// **'Notifications enabled'**
  String get notificationsEnabled;

  /// No description provided for @notificationsDisabled.
  ///
  /// In en, this message translates to:
  /// **'Notifications disabled'**
  String get notificationsDisabled;

  /// No description provided for @subscribeToEmails.
  ///
  /// In en, this message translates to:
  /// **'Subscribe to emails'**
  String get subscribeToEmails;

  /// No description provided for @receiveProductUpdatesAndSystemAnnouncements.
  ///
  /// In en, this message translates to:
  /// **'Receive product updates and system announcements.'**
  String get receiveProductUpdatesAndSystemAnnouncements;

  /// No description provided for @emailSubscriptionUpdated.
  ///
  /// In en, this message translates to:
  /// **'Email subscription updated'**
  String get emailSubscriptionUpdated;

  /// No description provided for @emailSubscriptionDisabled.
  ///
  /// In en, this message translates to:
  /// **'Email subscription disabled'**
  String get emailSubscriptionDisabled;

  /// No description provided for @signInMfaPreferencesUpdated.
  ///
  /// In en, this message translates to:
  /// **'Sign-in MFA preferences updated'**
  String get signInMfaPreferencesUpdated;

  /// No description provided for @signInMfaPreferences.
  ///
  /// In en, this message translates to:
  /// **'Sign-in MFA preferences'**
  String get signInMfaPreferences;

  /// No description provided for @sensitiveActionVerificationPreferencesUpdated.
  ///
  /// In en, this message translates to:
  /// **'Sensitive action verification preferences updated'**
  String get sensitiveActionVerificationPreferencesUpdated;

  /// No description provided for @sensitiveActionVerificationPreferences.
  ///
  /// In en, this message translates to:
  /// **'Sensitive action verification preferences'**
  String get sensitiveActionVerificationPreferences;

  /// No description provided for @authenticationMethods.
  ///
  /// In en, this message translates to:
  /// **'Authentication methods'**
  String get authenticationMethods;

  /// No description provided for @configureVerificationMethods.
  ///
  /// In en, this message translates to:
  /// **'Configure verification methods'**
  String get configureVerificationMethods;

  /// No description provided for @passwordSignIn.
  ///
  /// In en, this message translates to:
  /// **'Password sign-in'**
  String get passwordSignIn;

  /// No description provided for @enabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled'**
  String get enabled;

  /// No description provided for @emailCodeSignIn.
  ///
  /// In en, this message translates to:
  /// **'Email code sign-in'**
  String get emailCodeSignIn;

  /// No description provided for @notConfigured.
  ///
  /// In en, this message translates to:
  /// **'Not configured'**
  String get notConfigured;

  /// No description provided for @itemCount.
  ///
  /// In en, this message translates to:
  /// **'{arg0}'**
  String itemCount(String arg0);

  /// No description provided for @disabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get disabled;

  /// No description provided for @changeEmail.
  ///
  /// In en, this message translates to:
  /// **'Change email'**
  String get changeEmail;

  /// No description provided for @changePassword.
  ///
  /// In en, this message translates to:
  /// **'Change password'**
  String get changePassword;

  /// No description provided for @authorizeAccountRecovery.
  ///
  /// In en, this message translates to:
  /// **'Authorize account recovery'**
  String get authorizeAccountRecovery;

  /// No description provided for @generateAOneTimeRecoveryQrCode.
  ///
  /// In en, this message translates to:
  /// **'Generate a one-time recovery QR code and recovery code for another device, valid for 5 minutes. Existing sessions will be revoked after successful recovery.'**
  String get generateAOneTimeRecoveryQrCode;

  /// No description provided for @adaptiveContinuousAuthentication.
  ///
  /// In en, this message translates to:
  /// **'Adaptive continuous authentication'**
  String get adaptiveContinuousAuthentication;

  /// No description provided for @useSessionContextRiskSignalsAndRecent.
  ///
  /// In en, this message translates to:
  /// **'Use session context, risk signals, and recent verification to determine whether additional verification is needed.'**
  String get useSessionContextRiskSignalsAndRecent;

  /// No description provided for @totpManagement.
  ///
  /// In en, this message translates to:
  /// **'TOTP management'**
  String get totpManagement;

  /// No description provided for @manageYourAuthenticatorCodes.
  ///
  /// In en, this message translates to:
  /// **'Manage your authenticator codes'**
  String get manageYourAuthenticatorCodes;

  /// No description provided for @passkeys.
  ///
  /// In en, this message translates to:
  /// **'Passkeys'**
  String get passkeys;

  /// No description provided for @useNativePasskeysForSignInAnd.
  ///
  /// In en, this message translates to:
  /// **'Use native passkeys for sign-in and sensitive action verification'**
  String get useNativePasskeysForSignInAnd;

  /// No description provided for @passkeyAdded.
  ///
  /// In en, this message translates to:
  /// **'Passkey added'**
  String get passkeyAdded;

  /// No description provided for @addPasskey.
  ///
  /// In en, this message translates to:
  /// **'Add passkey'**
  String get addPasskey;

  /// No description provided for @noPasskeysRegisteredYet.
  ///
  /// In en, this message translates to:
  /// **'No passkeys registered yet.'**
  String get noPasskeysRegisteredYet;

  /// No description provided for @onlineSessions.
  ///
  /// In en, this message translates to:
  /// **'Online sessions'**
  String get onlineSessions;

  /// No description provided for @manageYourSignInAndOnlineActivity.
  ///
  /// In en, this message translates to:
  /// **'Manage your sign-in and online activity'**
  String get manageYourSignInAndOnlineActivity;

  /// No description provided for @allSessions.
  ///
  /// In en, this message translates to:
  /// **'All sessions'**
  String get allSessions;

  /// No description provided for @thisDevice.
  ///
  /// In en, this message translates to:
  /// **'This device'**
  String get thisDevice;

  /// No description provided for @sessionsRefreshed.
  ///
  /// In en, this message translates to:
  /// **'Sessions refreshed'**
  String get sessionsRefreshed;

  /// No description provided for @refreshList.
  ///
  /// In en, this message translates to:
  /// **'Refresh list'**
  String get refreshList;

  /// No description provided for @noOnlineSessions.
  ///
  /// In en, this message translates to:
  /// **'No online sessions'**
  String get noOnlineSessions;

  /// No description provided for @sessionsWillAppearHereWhenYouSign.
  ///
  /// In en, this message translates to:
  /// **'Sessions will appear here when you sign in on other devices.'**
  String get sessionsWillAppearHereWhenYouSign;

  /// No description provided for @dangerZone.
  ///
  /// In en, this message translates to:
  /// **'Danger zone'**
  String get dangerZone;

  /// No description provided for @signOutOfAllDevicesIncludingThe.
  ///
  /// In en, this message translates to:
  /// **'Sign out of all devices, including the web app.'**
  String get signOutOfAllDevicesIncludingThe;

  /// No description provided for @thisRevokesAllSessionsIncludingThisDevice.
  ///
  /// In en, this message translates to:
  /// **'This revokes all sessions, including this device. You will need to sign in again after the access token expires.'**
  String get thisRevokesAllSessionsIncludingThisDevice;

  /// No description provided for @signOutAllDevices.
  ///
  /// In en, this message translates to:
  /// **'Sign out all devices?'**
  String get signOutAllDevices;

  /// No description provided for @thisDesktopSessionWillAlsoExpireImmediately.
  ///
  /// In en, this message translates to:
  /// **'This desktop session will also expire immediately.'**
  String get thisDesktopSessionWillAlsoExpireImmediately;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @signOut2.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get signOut2;

  /// No description provided for @signedOutAllDevices.
  ///
  /// In en, this message translates to:
  /// **'Signed out all devices'**
  String get signedOutAllDevices;

  /// No description provided for @signOutAllDevices2.
  ///
  /// In en, this message translates to:
  /// **'Sign out all devices'**
  String get signOutAllDevices2;

  /// No description provided for @theSessionEnvironmentIsStableNoAdditional.
  ///
  /// In en, this message translates to:
  /// **'The session environment is stable. No additional risk signals were detected'**
  String get theSessionEnvironmentIsStableNoAdditional;

  /// No description provided for @additionalVerificationIsRecommended.
  ///
  /// In en, this message translates to:
  /// **'Additional verification is recommended'**
  String get additionalVerificationIsRecommended;

  /// No description provided for @sessionTrustIsStable.
  ///
  /// In en, this message translates to:
  /// **'Session trust is stable'**
  String get sessionTrustIsStable;

  /// No description provided for @risk.
  ///
  /// In en, this message translates to:
  /// **'{arg0} risk {arg1}'**
  String risk(String arg0, String arg1);

  /// No description provided for @policyFreezeSession.
  ///
  /// In en, this message translates to:
  /// **'Policy: freeze session'**
  String get policyFreezeSession;

  /// No description provided for @policyRequireVerification.
  ///
  /// In en, this message translates to:
  /// **'Policy: require verification'**
  String get policyRequireVerification;

  /// No description provided for @policyAllow.
  ///
  /// In en, this message translates to:
  /// **'Policy: allow'**
  String get policyAllow;

  /// No description provided for @continuousAuthenticationStatusUpdated.
  ///
  /// In en, this message translates to:
  /// **'Continuous authentication status updated'**
  String get continuousAuthenticationStatusUpdated;

  /// No description provided for @verified.
  ///
  /// In en, this message translates to:
  /// **'Verified'**
  String get verified;

  /// No description provided for @verifyNow.
  ///
  /// In en, this message translates to:
  /// **'Verify now'**
  String get verifyNow;

  /// No description provided for @riskAlert.
  ///
  /// In en, this message translates to:
  /// **'Risk alert'**
  String get riskAlert;

  /// No description provided for @riskSignalsDetectedPleaseTakeActionSoon.
  ///
  /// In en, this message translates to:
  /// **'Risk signals detected. Please take action soon'**
  String get riskSignalsDetectedPleaseTakeActionSoon;

  /// No description provided for @alertExpiresInSeconds.
  ///
  /// In en, this message translates to:
  /// **'Alert expires in {arg0} seconds'**
  String alertExpiresInSeconds(String arg0);

  /// No description provided for @sensitiveActionVerification.
  ///
  /// In en, this message translates to:
  /// **'Sensitive action verification'**
  String get sensitiveActionVerification;

  /// No description provided for @validForSeconds.
  ///
  /// In en, this message translates to:
  /// **'Valid for {arg0} seconds'**
  String validForSeconds(String arg0);

  /// No description provided for @notVerified.
  ///
  /// In en, this message translates to:
  /// **'Not verified'**
  String get notVerified;

  /// No description provided for @sessionAge.
  ///
  /// In en, this message translates to:
  /// **'Session age'**
  String get sessionAge;

  /// No description provided for @idleTime.
  ///
  /// In en, this message translates to:
  /// **'Idle time'**
  String get idleTime;

  /// No description provided for @currentEnvironment.
  ///
  /// In en, this message translates to:
  /// **'Current environment'**
  String get currentEnvironment;

  /// No description provided for @unknownDevice.
  ///
  /// In en, this message translates to:
  /// **'Unknown device'**
  String get unknownDevice;

  /// No description provided for @riskSignals.
  ///
  /// In en, this message translates to:
  /// **'Risk signals'**
  String get riskSignals;

  /// No description provided for @viewSensitiveAccountActionsAndVerificationRecords.
  ///
  /// In en, this message translates to:
  /// **'View sensitive account actions and verification records'**
  String get viewSensitiveAccountActionsAndVerificationRecords;

  /// No description provided for @filterActivity.
  ///
  /// In en, this message translates to:
  /// **'Filter activity'**
  String get filterActivity;

  /// No description provided for @reset.
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get reset;

  /// No description provided for @actionType.
  ///
  /// In en, this message translates to:
  /// **'Action type'**
  String get actionType;

  /// No description provided for @register.
  ///
  /// In en, this message translates to:
  /// **'Register'**
  String get register;

  /// No description provided for @enableTotp.
  ///
  /// In en, this message translates to:
  /// **'Enable TOTP'**
  String get enableTotp;

  /// No description provided for @disableTotp.
  ///
  /// In en, this message translates to:
  /// **'Disable TOTP'**
  String get disableTotp;

  /// No description provided for @result.
  ///
  /// In en, this message translates to:
  /// **'Result'**
  String get result;

  /// No description provided for @success.
  ///
  /// In en, this message translates to:
  /// **'Success'**
  String get success;

  /// No description provided for @failure.
  ///
  /// In en, this message translates to:
  /// **'Failure'**
  String get failure;

  /// No description provided for @searching.
  ///
  /// In en, this message translates to:
  /// **'Searching'**
  String get searching;

  /// No description provided for @searchRecords.
  ///
  /// In en, this message translates to:
  /// **'Search records'**
  String get searchRecords;

  /// No description provided for @searchResults.
  ///
  /// In en, this message translates to:
  /// **'Search results'**
  String get searchResults;

  /// No description provided for @records.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 record} other{{arg0} records}}'**
  String records(int arg0);

  /// No description provided for @noMatchingRecords.
  ///
  /// In en, this message translates to:
  /// **'No matching records'**
  String get noMatchingRecords;

  /// No description provided for @changeTheActionTypeOrResultAnd.
  ///
  /// In en, this message translates to:
  /// **'Change the action type or result and search again.'**
  String get changeTheActionTypeOrResultAnd;

  /// No description provided for @additionalVerification.
  ///
  /// In en, this message translates to:
  /// **'Additional verification'**
  String get additionalVerification;

  /// No description provided for @currentChallenge.
  ///
  /// In en, this message translates to:
  /// **'Current challenge: {arg0}'**
  String currentChallenge(String arg0);

  /// No description provided for @authenticatorCode.
  ///
  /// In en, this message translates to:
  /// **'Authenticator code'**
  String get authenticatorCode;

  /// No description provided for @recoveryCode.
  ///
  /// In en, this message translates to:
  /// **'Recovery code'**
  String get recoveryCode;

  /// No description provided for @passkeyUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Passkey unavailable'**
  String get passkeyUnavailable;

  /// No description provided for @digitAuthenticatorCode.
  ///
  /// In en, this message translates to:
  /// **'6-digit authenticator code'**
  String get digitAuthenticatorCode;

  /// No description provided for @useAPasskeyForAdditionalVerification.
  ///
  /// In en, this message translates to:
  /// **'Use a passkey for additional verification'**
  String get useAPasskeyForAdditionalVerification;

  /// No description provided for @passkeyIsCurrentlyUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Passkey is currently unavailable'**
  String get passkeyIsCurrentlyUnavailable;

  /// No description provided for @verifyWithPasskey.
  ///
  /// In en, this message translates to:
  /// **'Verify with passkey'**
  String get verifyWithPasskey;

  /// No description provided for @completeMfaVerification.
  ///
  /// In en, this message translates to:
  /// **'Complete MFA verification'**
  String get completeMfaVerification;

  /// No description provided for @securityScoreSyncedDevicesAndLogs.
  ///
  /// In en, this message translates to:
  /// **'Security score: {arg0}%. Synced {arg1} devices and {arg2} logs.'**
  String securityScoreSyncedDevicesAndLogs(
    String arg0,
    String arg1,
    String arg2,
  );

  /// No description provided for @edit.
  ///
  /// In en, this message translates to:
  /// **'Edit'**
  String get edit;

  /// No description provided for @totpEnabled.
  ///
  /// In en, this message translates to:
  /// **'TOTP enabled'**
  String get totpEnabled;

  /// No description provided for @totpIsNotEnabled.
  ///
  /// In en, this message translates to:
  /// **'TOTP is not enabled'**
  String get totpIsNotEnabled;

  /// No description provided for @recoveryCodesRemaining.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 recovery code remaining} other{{arg0} recovery codes remaining}}'**
  String recoveryCodesRemaining(int arg0);

  /// No description provided for @registerWithTheQrCodeOrSecret.
  ///
  /// In en, this message translates to:
  /// **'Register with the QR code or secret key.'**
  String get registerWithTheQrCodeOrSecret;

  /// No description provided for @totpDisabled.
  ///
  /// In en, this message translates to:
  /// **'TOTP disabled'**
  String get totpDisabled;

  /// No description provided for @disable.
  ///
  /// In en, this message translates to:
  /// **'Disable'**
  String get disable;

  /// No description provided for @enable.
  ///
  /// In en, this message translates to:
  /// **'Enable'**
  String get enable;

  /// No description provided for @recoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Recovery codes'**
  String get recoveryCodes;

  /// No description provided for @viewRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'View recovery codes'**
  String get viewRecoveryCodes;

  /// No description provided for @newRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'New recovery codes'**
  String get newRecoveryCodes;

  /// No description provided for @regenerateRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Regenerate recovery codes'**
  String get regenerateRecoveryCodes;

  /// No description provided for @created.
  ///
  /// In en, this message translates to:
  /// **'Created {arg0}'**
  String created(String arg0);

  /// No description provided for @neverUsed.
  ///
  /// In en, this message translates to:
  /// **'Never used'**
  String get neverUsed;

  /// No description provided for @renamePasskey.
  ///
  /// In en, this message translates to:
  /// **'Rename passkey'**
  String get renamePasskey;

  /// No description provided for @rename.
  ///
  /// In en, this message translates to:
  /// **'Rename'**
  String get rename;

  /// No description provided for @deletePasskey.
  ///
  /// In en, this message translates to:
  /// **'Delete passkey'**
  String get deletePasskey;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete {arg0}?'**
  String delete(String arg0);

  /// No description provided for @delete2.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete2;

  /// No description provided for @passkeyDeleted.
  ///
  /// In en, this message translates to:
  /// **'Passkey deleted'**
  String get passkeyDeleted;

  /// No description provided for @current.
  ///
  /// In en, this message translates to:
  /// **'Current'**
  String get current;

  /// No description provided for @online.
  ///
  /// In en, this message translates to:
  /// **'Online'**
  String get online;

  /// No description provided for @offline.
  ///
  /// In en, this message translates to:
  /// **'Offline'**
  String get offline;

  /// No description provided for @active.
  ///
  /// In en, this message translates to:
  /// **'Active {arg0}'**
  String active(String arg0);

  /// No description provided for @sessionRevoked.
  ///
  /// In en, this message translates to:
  /// **'Session revoked'**
  String get sessionRevoked;

  /// No description provided for @revoke.
  ///
  /// In en, this message translates to:
  /// **'Revoke'**
  String get revoke;

  /// No description provided for @risk2.
  ///
  /// In en, this message translates to:
  /// **'Risk {arg0}'**
  String risk2(String arg0);

  /// No description provided for @verifyYourIdentityFirst.
  ///
  /// In en, this message translates to:
  /// **'Verify your identity first'**
  String get verifyYourIdentityFirst;

  /// No description provided for @invalidQrVerificationResultRefreshTheQr.
  ///
  /// In en, this message translates to:
  /// **'Invalid QR verification result. Refresh the QR code and try again'**
  String get invalidQrVerificationResultRefreshTheQr;

  /// No description provided for @verified2.
  ///
  /// In en, this message translates to:
  /// **'Verified'**
  String get verified2;

  /// No description provided for @qrRequestRejectedPleaseRefreshTheQr.
  ///
  /// In en, this message translates to:
  /// **'QR request rejected. Please refresh the QR code'**
  String get qrRequestRejectedPleaseRefreshTheQr;

  /// No description provided for @enterYourSignInPassword.
  ///
  /// In en, this message translates to:
  /// **'Enter your sign-in password'**
  String get enterYourSignInPassword;

  /// No description provided for @enterADigitEmailCode.
  ///
  /// In en, this message translates to:
  /// **'Enter a 6-digit email code'**
  String get enterADigitEmailCode;

  /// No description provided for @scanTheQrCodeWithYourPhone.
  ///
  /// In en, this message translates to:
  /// **'Scan the QR code with your phone to verify'**
  String get scanTheQrCodeWithYourPhone;

  /// No description provided for @openingTheNativePasskeyService.
  ///
  /// In en, this message translates to:
  /// **'Opening the native {arg0} passkey service'**
  String openingTheNativePasskeyService(String arg0);

  /// No description provided for @sensitiveActionVerification2.
  ///
  /// In en, this message translates to:
  /// **'Sensitive action verification'**
  String get sensitiveActionVerification2;

  /// No description provided for @verifyYourIdentityToContinueWithThis.
  ///
  /// In en, this message translates to:
  /// **'Verify your identity to continue with this action.'**
  String get verifyYourIdentityToContinueWithThis;

  /// No description provided for @signInPassword.
  ///
  /// In en, this message translates to:
  /// **'Sign-in password'**
  String get signInPassword;

  /// No description provided for @passkeyVerificationAvailable.
  ///
  /// In en, this message translates to:
  /// **'Passkey verification available'**
  String get passkeyVerificationAvailable;

  /// No description provided for @verifying.
  ///
  /// In en, this message translates to:
  /// **'Verifying...'**
  String get verifying;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @saved.
  ///
  /// In en, this message translates to:
  /// **'Saved'**
  String get saved;

  /// No description provided for @newEmail.
  ///
  /// In en, this message translates to:
  /// **'New email'**
  String get newEmail;

  /// No description provided for @verificationCode.
  ///
  /// In en, this message translates to:
  /// **'Verification code'**
  String get verificationCode;

  /// No description provided for @emailUpdated.
  ///
  /// In en, this message translates to:
  /// **'Email updated'**
  String get emailUpdated;

  /// No description provided for @saving2.
  ///
  /// In en, this message translates to:
  /// **'Saving...'**
  String get saving2;

  /// No description provided for @newPassword.
  ///
  /// In en, this message translates to:
  /// **'New password'**
  String get newPassword;

  /// No description provided for @passwordUpdated.
  ///
  /// In en, this message translates to:
  /// **'Password updated'**
  String get passwordUpdated;

  /// No description provided for @scanTheQrCodeOrEnterThe.
  ///
  /// In en, this message translates to:
  /// **'Scan the QR code or enter the secret key manually, then enter the 6-digit code generated by your authenticator.'**
  String get scanTheQrCodeOrEnterThe;

  /// No description provided for @initialRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Initial recovery codes'**
  String get initialRecoveryCodes;

  /// No description provided for @saveYourRecoveryCodes.
  ///
  /// In en, this message translates to:
  /// **'Save your recovery codes'**
  String get saveYourRecoveryCodes;

  /// No description provided for @enabling.
  ///
  /// In en, this message translates to:
  /// **'Enabling...'**
  String get enabling;

  /// No description provided for @confirmAndEnable.
  ///
  /// In en, this message translates to:
  /// **'Confirm and enable'**
  String get confirmAndEnable;

  /// No description provided for @couldNotGenerateQrCodePleaseRefresh.
  ///
  /// In en, this message translates to:
  /// **'Could not generate QR code. Please refresh'**
  String get couldNotGenerateQrCodePleaseRefresh;

  /// No description provided for @qrVerification.
  ///
  /// In en, this message translates to:
  /// **'QR verification'**
  String get qrVerification;

  /// No description provided for @enterYourCurrentSignInPasswordTo.
  ///
  /// In en, this message translates to:
  /// **'Enter your current sign-in password to verify.'**
  String get enterYourCurrentSignInPasswordTo;

  /// No description provided for @sendAOneTimeCodeToYour.
  ///
  /// In en, this message translates to:
  /// **'Send a one-time code to your linked email address.'**
  String get sendAOneTimeCodeToYour;

  /// No description provided for @enterTheDigitCodeGeneratedByYour.
  ///
  /// In en, this message translates to:
  /// **'Enter the 6-digit code generated by your authenticator.'**
  String get enterTheDigitCodeGeneratedByYour;

  /// No description provided for @scanWithYourSignedInMobileApp.
  ///
  /// In en, this message translates to:
  /// **'Scan with your signed-in mobile app to confirm this sensitive action.'**
  String get scanWithYourSignedInMobileApp;

  /// No description provided for @viewAccountStatusSignInMethodsAnd.
  ///
  /// In en, this message translates to:
  /// **'View account status, sign-in methods, and recent activity on desktop.'**
  String get viewAccountStatusSignInMethodsAnd;

  /// No description provided for @manageYourAccountProfileAndExtendedInformation.
  ///
  /// In en, this message translates to:
  /// **'Manage your account profile and extended information.'**
  String get manageYourAccountProfileAndExtendedInformation;

  /// No description provided for @configureMfaTotpPasskeysAndSensitiveAction.
  ///
  /// In en, this message translates to:
  /// **'Configure MFA, TOTP, passkeys, and sensitive action verification preferences.'**
  String get configureMfaTotpPasskeysAndSensitiveAction;

  /// No description provided for @viewOnlineDevicesSessionsAndTheirCurrent.
  ///
  /// In en, this message translates to:
  /// **'View online devices, sessions, and their current status.'**
  String get viewOnlineDevicesSessionsAndTheirCurrent;

  /// No description provided for @filterSensitiveActionsAndVerificationRecords.
  ///
  /// In en, this message translates to:
  /// **'Filter sensitive actions and verification records.'**
  String get filterSensitiveActionsAndVerificationRecords;

  /// No description provided for @viewThirdPartyAppsAuthorizedToAccess.
  ///
  /// In en, this message translates to:
  /// **'View third-party apps authorized to access your account.'**
  String get viewThirdPartyAppsAuthorizedToAccess;

  /// No description provided for @desktopApp.
  ///
  /// In en, this message translates to:
  /// **'Desktop app'**
  String get desktopApp;

  /// No description provided for @mobileApp.
  ///
  /// In en, this message translates to:
  /// **'Mobile app'**
  String get mobileApp;

  /// No description provided for @unknownBrowser.
  ///
  /// In en, this message translates to:
  /// **'Unknown browser'**
  String get unknownBrowser;

  /// No description provided for @passwordMfa.
  ///
  /// In en, this message translates to:
  /// **'Password + MFA'**
  String get passwordMfa;

  /// No description provided for @codeSignIn.
  ///
  /// In en, this message translates to:
  /// **'Code sign-in'**
  String get codeSignIn;

  /// No description provided for @codeMfa.
  ///
  /// In en, this message translates to:
  /// **'Code + MFA'**
  String get codeMfa;

  /// No description provided for @passkeySignIn.
  ///
  /// In en, this message translates to:
  /// **'Passkey sign-in'**
  String get passkeySignIn;

  /// No description provided for @passkeyMfa.
  ///
  /// In en, this message translates to:
  /// **'Passkey + MFA'**
  String get passkeyMfa;

  /// No description provided for @qrCodeMfa.
  ///
  /// In en, this message translates to:
  /// **'QR code + MFA'**
  String get qrCodeMfa;

  /// No description provided for @bridgeSignIn.
  ///
  /// In en, this message translates to:
  /// **'Bridge sign-in'**
  String get bridgeSignIn;

  /// No description provided for @desktopBridge.
  ///
  /// In en, this message translates to:
  /// **'Desktop bridge'**
  String get desktopBridge;

  /// No description provided for @webBridge.
  ///
  /// In en, this message translates to:
  /// **'Web bridge'**
  String get webBridge;

  /// No description provided for @mobileBridge.
  ///
  /// In en, this message translates to:
  /// **'Mobile bridge'**
  String get mobileBridge;

  /// No description provided for @accountRecovery.
  ///
  /// In en, this message translates to:
  /// **'Account recovery'**
  String get accountRecovery;

  /// No description provided for @generalAction.
  ///
  /// In en, this message translates to:
  /// **'General action'**
  String get generalAction;

  /// No description provided for @high.
  ///
  /// In en, this message translates to:
  /// **'High'**
  String get high;

  /// No description provided for @medium.
  ///
  /// In en, this message translates to:
  /// **'Medium'**
  String get medium;

  /// No description provided for @low.
  ///
  /// In en, this message translates to:
  /// **'Low'**
  String get low;

  /// No description provided for @seconds.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 second} other{{arg0} seconds}}'**
  String seconds(int arg0);

  /// No description provided for @minutes.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 minute} other{{arg0} minutes}}'**
  String minutes(int arg0);

  /// No description provided for @hours.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 hour} other{{arg0} hours}}'**
  String hours(int arg0);

  /// No description provided for @days.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 day} other{{arg0} days}}'**
  String days(int arg0);

  /// No description provided for @justNow.
  ///
  /// In en, this message translates to:
  /// **'Just now'**
  String get justNow;

  /// No description provided for @minutesAgo.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 minute ago} other{{arg0} minutes ago}}'**
  String minutesAgo(int arg0);

  /// No description provided for @hoursAgo.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 hour ago} other{{arg0} hours ago}}'**
  String hoursAgo(int arg0);

  /// No description provided for @daysAgo.
  ///
  /// In en, this message translates to:
  /// **'{arg0, plural, one{1 day ago} other{{arg0} days ago}}'**
  String daysAgo(int arg0);

  /// No description provided for @invalidDataFormat.
  ///
  /// In en, this message translates to:
  /// **'Invalid data format'**
  String get invalidDataFormat;

  /// No description provided for @unnamedApp.
  ///
  /// In en, this message translates to:
  /// **'Unnamed app'**
  String get unnamedApp;

  /// No description provided for @passwordMustBeBetweenAndCharactersLong.
  ///
  /// In en, this message translates to:
  /// **'Password must be between {arg0} and {arg1} characters long'**
  String passwordMustBeBetweenAndCharactersLong(String arg0, String arg1);

  /// No description provided for @passwordMustContainAtLeastOneUppercase.
  ///
  /// In en, this message translates to:
  /// **'Password must contain at least one uppercase letter'**
  String get passwordMustContainAtLeastOneUppercase;

  /// No description provided for @passwordMustContainAtLeastOneLowercase.
  ///
  /// In en, this message translates to:
  /// **'Password must contain at least one lowercase letter'**
  String get passwordMustContainAtLeastOneLowercase;

  /// No description provided for @passwordMustContainAtLeastOneDigit.
  ///
  /// In en, this message translates to:
  /// **'Password must contain at least one digit'**
  String get passwordMustContainAtLeastOneDigit;

  /// No description provided for @passwordMustContainAtLeastOneSpecial.
  ///
  /// In en, this message translates to:
  /// **'Password must contain at least one special character'**
  String get passwordMustContainAtLeastOneSpecial;

  /// No description provided for @timedOutWaitingForTheBrowserResult.
  ///
  /// In en, this message translates to:
  /// **'Timed out waiting for the browser result'**
  String get timedOutWaitingForTheBrowserResult;

  /// No description provided for @browserBridgeStateVerificationFailed.
  ///
  /// In en, this message translates to:
  /// **'Browser bridge state verification failed'**
  String get browserBridgeStateVerificationFailed;

  /// No description provided for @theBrowserDidNotCompleteThePasskey.
  ///
  /// In en, this message translates to:
  /// **'The browser did not complete the passkey operation'**
  String get theBrowserDidNotCompleteThePasskey;

  /// No description provided for @theDesktopAppReceivedThePasskeyResult.
  ///
  /// In en, this message translates to:
  /// **'The desktop app received the passkey result. You can close this page.'**
  String get theDesktopAppReceivedThePasskeyResult;

  /// No description provided for @hActionCompletedHPPP.
  ///
  /// In en, this message translates to:
  /// **'<h2>Action completed</h2><p>{arg0}</p><p>You can return to the desktop app.</p></body></html>'**
  String hActionCompletedHPPP(String arg0);

  /// No description provided for @theDesktopAppDidNotReceiveA.
  ///
  /// In en, this message translates to:
  /// **'The desktop app did not receive a valid result.'**
  String get theDesktopAppDidNotReceiveA;

  /// No description provided for @hActionFailedHPPP.
  ///
  /// In en, this message translates to:
  /// **'<h2>Action failed</h2><p>{arg0}</p><p>Return to the desktop app and try again.</p></body></html>'**
  String hActionFailedHPPP(String arg0);

  /// No description provided for @couldNotOpenTheExternalBrowser.
  ///
  /// In en, this message translates to:
  /// **'Could not open the external browser'**
  String get couldNotOpenTheExternalBrowser;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @languageSettingExplanation.
  ///
  /// In en, this message translates to:
  /// **'Follow your device language or choose a language for this device.'**
  String get languageSettingExplanation;

  /// No description provided for @languageSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save your language preference. It may reset after restarting the app.'**
  String get languageSaveFailed;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when language+script codes are specified.
  switch (locale.languageCode) {
    case 'zh':
      {
        switch (locale.scriptCode) {
          case 'Hans':
            return AppLocalizationsZhHans();
        }
        break;
      }
  }

  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
