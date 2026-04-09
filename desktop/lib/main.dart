// ignore_for_file: use_build_context_synchronously

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const Color kPrimaryColor = Color(0xFFFFB90F);
const Color kSurfaceTint = Color(0xFFFFF2CC);
const String kDefaultApiBaseUrl = 'https://api.ksuser.cn';
const String kDesktopAppVersion = '1.0.0';
const int kDesktopSessionBridgePort = 43921;
const String kSidebarLogoAsset = 'assets/logo/sidebar_logo.png';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final EnvConfig envConfig = await EnvConfig.load();
  runApp(
    KsuserDesktopApp(
      initialApiBaseUrl: envConfig.apiBaseUrl,
      environmentName: envConfig.environmentName,
      passkeyOrigin: envConfig.passkeyOrigin,
    ),
  );
}

class KsuserDesktopApp extends StatefulWidget {
  const KsuserDesktopApp({
    super.key,
    this.initialApiBaseUrl = kDefaultApiBaseUrl,
    this.environmentName = 'Production',
    this.passkeyOrigin = 'https://auth.ksuser.cn',
  });

  final String initialApiBaseUrl;
  final String environmentName;
  final String passkeyOrigin;

  @override
  State<KsuserDesktopApp> createState() => _KsuserDesktopAppState();
}

class _KsuserDesktopAppState extends State<KsuserDesktopApp> {
  late final AppController _controller;
  DesktopSessionBridgeServer? _sessionBridge;
  final GlobalKey<NavigatorState> _navigatorKey = GlobalKey<NavigatorState>();
  bool _settingsDialogOpen = false;

  @override
  void initState() {
    super.initState();
    _controller = AppController(
      KsuserApiClient(baseUrl: widget.initialApiBaseUrl),
      environmentName: widget.environmentName,
      passkeyOrigin: widget.passkeyOrigin,
    );
    _sessionBridge = DesktopSessionBridgeServer(
      controller: _controller,
      allowedOrigins: _buildAllowedOrigins(widget.passkeyOrigin),
    );
    unawaited(_sessionBridge!.start());
    _appMenuChannel.setMethodCallHandler(_handleAppMenuCall);
  }

  @override
  void dispose() {
    _appMenuChannel.setMethodCallHandler(null);
    unawaited(_sessionBridge?.stop());
    _controller.dispose();
    super.dispose();
  }

  Set<String> _buildAllowedOrigins(String passkeyOrigin) {
    final Set<String> allowed = <String>{};
    final Uri? uri = Uri.tryParse(passkeyOrigin.trim());
    if (uri != null &&
        (uri.scheme == 'http' || uri.scheme == 'https') &&
        uri.host.isNotEmpty) {
      allowed.add(uri.origin);
      if (uri.host == 'localhost') {
        allowed.add(
          uri.hasPort
              ? Uri(
                  scheme: uri.scheme,
                  host: '127.0.0.1',
                  port: uri.port,
                ).origin
              : Uri(scheme: uri.scheme, host: '127.0.0.1').origin,
        );
      } else if (uri.host == '127.0.0.1') {
        allowed.add(
          uri.hasPort
              ? Uri(
                  scheme: uri.scheme,
                  host: 'localhost',
                  port: uri.port,
                ).origin
              : Uri(scheme: uri.scheme, host: 'localhost').origin,
        );
      }
    }
    return allowed;
  }

  Future<dynamic> _handleAppMenuCall(MethodCall call) async {
    switch (call.method) {
      case 'openSettings':
        _openSettingsPanel();
        return true;
      case 'refresh':
        if (_controller.isAuthenticated) {
          await _controller.refreshWorkspace();
        } else {
          await _controller.fetchPasswordRequirement();
        }
        return true;
      case 'logout':
        if (_controller.isAuthenticated) {
          await _controller.logout();
        }
        return true;
      case 'showOverview':
        if (_controller.isAuthenticated) {
          _controller.setSection(DesktopSection.overview);
        }
        return true;
      case 'showProfile':
        if (_controller.isAuthenticated) {
          _controller.setSection(DesktopSection.profile);
        }
        return true;
      case 'showSecurity':
        if (_controller.isAuthenticated) {
          _controller.setSection(DesktopSection.security);
        }
        return true;
      case 'showDevices':
        if (_controller.isAuthenticated) {
          _controller.setSection(DesktopSection.devices);
        }
        return true;
      case 'showActivity':
        if (_controller.isAuthenticated) {
          _controller.setSection(DesktopSection.activity);
        }
        return true;
      default:
        throw MissingPluginException('Unknown menu command: ${call.method}');
    }
  }

  void _openSettingsPanel() {
    final BuildContext? context = _navigatorKey.currentContext;
    if (context == null || _settingsDialogOpen) {
      return;
    }
    _settingsDialogOpen = true;
    unawaited(
      showDesktopSettingsDialog(context, _controller).whenComplete(() {
        _settingsDialogOpen = false;
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (BuildContext context, _) {
        return MaterialApp(
          title: 'Ksuser Auth 统一认证中心',
          navigatorKey: _navigatorKey,
          debugShowCheckedModeBanner: false,
          theme: _buildTheme(Brightness.light),
          darkTheme: _buildTheme(Brightness.dark),
          themeMode: _controller.themeMode,
          themeAnimationDuration: _controller.reduceMotion
              ? Duration.zero
              : const Duration(milliseconds: 220),
          home: DesktopRoot(controller: _controller),
        );
      },
    );
  }

  ThemeData _buildTheme(Brightness brightness) {
    final bool isDark = brightness == Brightness.dark;
    final ColorScheme scheme = ColorScheme.fromSeed(
      seedColor: kPrimaryColor,
      brightness: brightness,
    );
    final BorderSide outline = BorderSide(
      color: isDark
          ? Colors.white.withValues(alpha: 0.08)
          : Colors.black.withValues(alpha: 0.08),
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      brightness: brightness,
      visualDensity: _controller.compactMode
          ? const VisualDensity(horizontal: -2, vertical: -2)
          : VisualDensity.standard,
      scaffoldBackgroundColor: isDark
          ? const Color(0xFF171717)
          : const Color(0xFFF6F4EE),
      cardTheme: CardThemeData(
        color: isDark ? const Color(0xFF232323) : Colors.white,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(
            color: isDark
                ? Colors.white.withValues(alpha: 0.06)
                : Colors.black.withValues(alpha: 0.05),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: isDark ? const Color(0xFF262626) : const Color(0xFFFBFAF5),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: outline,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: outline,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: kPrimaryColor, width: 1.4),
        ),
        contentPadding: EdgeInsets.symmetric(
          horizontal: 16,
          vertical: _controller.compactMode ? 14 : 18,
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: isDark ? const Color(0xFF232323) : Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      ),
    );
  }
}

class DesktopRoot extends StatelessWidget {
  const DesktopRoot({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        if (controller.isAuthenticated) {
          return DesktopWorkspace(controller: controller);
        }
        return DesktopAuthPortal(controller: controller);
      },
    );
  }
}

enum DesktopSection { overview, profile, security, devices, activity }

enum AuthTab { login, register }

enum LoginFactor { password, emailCode }

enum MfaMode { code, recoveryCode, passkey }

enum SensitiveVerificationMethod { password, emailCode, totp, passkey }

const MethodChannel _passkeyChannel = MethodChannel('ksuser/passkey');
const MethodChannel _appMenuChannel = MethodChannel('ksuser/app_menu');

void showAppMessage(
  BuildContext context,
  String message, {
  bool error = false,
}) {
  final ScaffoldMessengerState? messenger = ScaffoldMessenger.maybeOf(context);
  if (messenger == null) {
    return;
  }
  messenger
    ..removeCurrentSnackBar()
    ..clearSnackBars()
    ..showSnackBar(
      SnackBar(
        content: Text(message),
        duration: const Duration(seconds: 3),
        backgroundColor: error ? Colors.red.shade700 : null,
      ),
    );
}

Future<void> showDesktopSettingsDialog(
  BuildContext context,
  AppController controller,
) {
  return showDialog<void>(
    context: context,
    builder: (BuildContext dialogContext) {
      return AnimatedBuilder(
        animation: controller,
        builder: (BuildContext context, _) {
          return AlertDialog(
            title: const Text('设置'),
            content: SizedBox(
              width: 520,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text('外观', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    SegmentedButton<ThemeMode>(
                      segments: const <ButtonSegment<ThemeMode>>[
                        ButtonSegment<ThemeMode>(
                          value: ThemeMode.system,
                          icon: Icon(Icons.settings_suggest_rounded),
                          label: Text('跟随系统'),
                        ),
                        ButtonSegment<ThemeMode>(
                          value: ThemeMode.light,
                          icon: Icon(Icons.light_mode_rounded),
                          label: Text('浅色'),
                        ),
                        ButtonSegment<ThemeMode>(
                          value: ThemeMode.dark,
                          icon: Icon(Icons.dark_mode_rounded),
                          label: Text('深色'),
                        ),
                      ],
                      selected: <ThemeMode>{controller.themeMode},
                      onSelectionChanged: (Set<ThemeMode> selection) {
                        controller.setThemeMode(selection.first);
                      },
                    ),
                    const SizedBox(height: 20),
                    Text(
                      '本地调试',
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 8),
                    SwitchListTile.adaptive(
                      contentPadding: EdgeInsets.zero,
                      value: controller.compactMode,
                      onChanged: controller.setCompactMode,
                      title: const Text('紧凑布局'),
                      subtitle: const Text('压缩控件间距，便于桌面联调时查看更多内容'),
                    ),
                    SwitchListTile.adaptive(
                      contentPadding: EdgeInsets.zero,
                      value: controller.reduceMotion,
                      onChanged: controller.setReduceMotion,
                      title: const Text('减少动画'),
                      subtitle: const Text('切换主题和弹窗时尽量减少动画效果'),
                    ),
                  ],
                ),
              ),
            ),
            actions: <Widget>[
              TextButton(
                onPressed: controller.resetLocalPreferences,
                child: const Text('恢复默认'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('完成'),
              ),
            ],
          );
        },
      );
    },
  );
}

class PasskeyPlatform {
  static Future<bool> isAvailable() async {
    if (!Platform.isMacOS) {
      return false;
    }
    try {
      return await _passkeyChannel.invokeMethod<bool>('isAvailable') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<PasskeyAssertionResult> getAssertion({
    required PasskeyAssertionOptions options,
    required String origin,
  }) async {
    if (!Platform.isMacOS) {
      throw ApiException('当前平台暂不支持原生 Passkey');
    }

    try {
      final Map<dynamic, dynamic>? result = await _passkeyChannel
          .invokeMethod<Map<dynamic, dynamic>>(
            'performAssertion',
            <String, dynamic>{
              'challenge': options.challenge,
              'rpId': options.rpId,
              'origin': origin,
              'userVerification': options.userVerification,
              'allowCredentials': options.allowedCredentials
                  .map((PasskeyAllowedCredential item) => item.toJson())
                  .toList(),
            },
          );
      if (result == null) {
        throw ApiException('未获取到 Passkey 凭证');
      }
      return PasskeyAssertionResult.fromChannelMap(result);
    } on PlatformException catch (error) {
      final String message = error.message?.trim().isNotEmpty == true
          ? error.message!.trim()
          : 'Passkey 验证失败';
      throw ApiException(message);
    }
  }
}

class AppController extends ChangeNotifier {
  AppController(
    this._apiClient, {
    required this.environmentName,
    required this.passkeyOrigin,
  }) {
    _apiClient.onSessionExpired = _handleSessionExpired;
  }

  final KsuserApiClient _apiClient;
  final String environmentName;
  final String passkeyOrigin;

  DesktopSection selectedSection = DesktopSection.overview;
  UserDetails? user;
  List<SessionItem> sessions = <SessionItem>[];
  List<SensitiveLogItem> sensitiveLogs = <SensitiveLogItem>[];
  List<PasskeyListItem> passkeys = <PasskeyListItem>[];
  TotpStatusResponse? totpStatus;
  PasswordRequirement? passwordRequirement;
  MFAChallenge? pendingMfaChallenge;
  bool workspaceLoading = false;
  bool authBusy = false;
  ThemeMode themeMode = ThemeMode.system;
  bool compactMode = false;
  bool reduceMotion = false;
  String? workspaceError;

  bool get isAuthenticated => _apiClient.accessToken != null;
  String get apiBaseUrl => _apiClient.baseUrl;

  void _resetLocalSessionState() {
    _apiClient.clearSession();
    user = null;
    sessions = <SessionItem>[];
    sensitiveLogs = <SensitiveLogItem>[];
    passkeys = <PasskeyListItem>[];
    totpStatus = null;
    pendingMfaChallenge = null;
    workspaceError = null;
    selectedSection = DesktopSection.overview;
  }

  void _handleSessionExpired() {
    _resetLocalSessionState();
    notifyListeners();
  }

  void updateApiBaseUrl(String value) {
    final String nextValue = value.trim().isEmpty
        ? kDefaultApiBaseUrl
        : value.trim();
    if (_apiClient.baseUrl == nextValue) {
      return;
    }
    _apiClient.updateBaseUrl(nextValue);
    notifyListeners();
  }

  void setSection(DesktopSection section) {
    if (selectedSection == section) {
      return;
    }
    selectedSection = section;
    notifyListeners();
  }

  void setThemeMode(ThemeMode value) {
    if (themeMode == value) {
      return;
    }
    themeMode = value;
    notifyListeners();
  }

  void setCompactMode(bool value) {
    if (compactMode == value) {
      return;
    }
    compactMode = value;
    notifyListeners();
  }

  void setReduceMotion(bool value) {
    if (reduceMotion == value) {
      return;
    }
    reduceMotion = value;
    notifyListeners();
  }

  void resetLocalPreferences() {
    themeMode = ThemeMode.system;
    compactMode = false;
    reduceMotion = false;
    notifyListeners();
  }

  Future<void> loginWithPassword({
    required String email,
    required String password,
  }) async {
    authBusy = true;
    pendingMfaChallenge = null;
    notifyListeners();
    try {
      final dynamic data = await _apiClient.post(
        '/auth/login',
        body: <String, dynamic>{'email': email.trim(), 'password': password},
      );
      await _consumeLoginPayload(data);
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<void> sendLoginCode(String email) {
    return _apiClient.post(
      '/auth/send-code',
      body: <String, dynamic>{'email': email.trim(), 'type': 'login'},
    );
  }

  Future<void> loginWithCode({
    required String email,
    required String code,
  }) async {
    authBusy = true;
    pendingMfaChallenge = null;
    notifyListeners();
    try {
      final dynamic data = await _apiClient.post(
        '/auth/login-with-code',
        body: <String, dynamic>{'email': email.trim(), 'code': code.trim()},
      );
      await _consumeLoginPayload(data);
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<void> completeTotpMfa({
    required String challengeId,
    String? code,
    String? recoveryCode,
  }) async {
    authBusy = true;
    notifyListeners();
    try {
      final Map<String, dynamic> payload = <String, dynamic>{
        'challengeId': challengeId,
      };
      if (code != null && code.isNotEmpty) {
        payload['code'] = code.trim();
      }
      if (recoveryCode != null && recoveryCode.isNotEmpty) {
        payload['recoveryCode'] = recoveryCode.trim();
      }
      final dynamic data = await _apiClient.post(
        '/auth/totp/mfa-verify',
        body: payload,
      );
      pendingMfaChallenge = null;
      await _consumeLoginPayload(data);
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<void> completePasskeyMfa({required String challengeId}) async {
    authBusy = true;
    notifyListeners();
    try {
      final BrowserPasskeyBridgeResponse response =
          await BrowserPasskeyBridge.start(
            passkeyOrigin: passkeyOrigin,
            apiBaseUrl: apiBaseUrl,
            mode: BrowserPasskeyBridgeMode.mfa,
            mfaChallengeId: challengeId,
          );
      if (response.accessToken == null || response.accessToken!.isEmpty) {
        throw ApiException(response.message ?? '浏览器未返回登录结果');
      }
      _apiClient.accessToken = response.accessToken;
      pendingMfaChallenge = null;
      await refreshWorkspace();
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<bool> checkUsername(String username) async {
    try {
      final dynamic data = await _apiClient.get(
        '/auth/check-username',
        query: <String, String>{'username': username.trim()},
      );
      return !(data['exists'] == true);
    } catch (_) {
      return false;
    }
  }

  Future<PasswordRequirement> fetchPasswordRequirement() async {
    final dynamic data = await _apiClient.get('/info/password-requirement');
    final PasswordRequirement requirement = PasswordRequirement.fromJson(
      asMap(data),
    );
    passwordRequirement = requirement;
    notifyListeners();
    return requirement;
  }

  Future<void> sendRegisterCode(String email) {
    return _apiClient.post(
      '/auth/send-code',
      body: <String, dynamic>{'email': email.trim(), 'type': 'register'},
    );
  }

  Future<void> register({
    required String username,
    required String email,
    required String password,
    required String code,
  }) async {
    authBusy = true;
    notifyListeners();
    try {
      final dynamic data = await _apiClient.post(
        '/auth/register',
        body: <String, dynamic>{
          'username': username.trim(),
          'email': email.trim(),
          'password': password,
          'code': code.trim(),
        },
      );
      final Map<String, dynamic> json = asMap(data);
      _apiClient.accessToken = asString(json['accessToken']);
      pendingMfaChallenge = null;
      await refreshWorkspace();
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<void> refreshWorkspace({SensitiveLogsQuery? logsQuery}) async {
    if (!isAuthenticated) {
      return;
    }
    workspaceLoading = true;
    workspaceError = null;
    notifyListeners();

    try {
      final dynamic userData = await _apiClient.get(
        '/auth/info',
        query: <String, String>{'type': 'details'},
        authorized: true,
      );
      user = UserDetails.fromJson(asMap(userData));

      sessions = await _loadSessions();
      sensitiveLogs = await _loadSensitiveLogs(
        logsQuery ?? const SensitiveLogsQuery(),
      );
      passkeys = await _loadPasskeys();
      totpStatus = await _loadTotpStatus();
    } catch (error) {
      workspaceError = error is ApiException ? error.message : '加载桌面数据失败';
      rethrow;
    } finally {
      workspaceLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshSensitiveLogs({SensitiveLogsQuery? query}) async {
    sensitiveLogs = await _loadSensitiveLogs(
      query ?? const SensitiveLogsQuery(),
    );
    notifyListeners();
  }

  Future<void> refreshSessions() async {
    sessions = await _loadSessions();
    notifyListeners();
  }

  Future<void> refreshPasskeys() async {
    passkeys = await _loadPasskeys();
    notifyListeners();
  }

  Future<void> refreshTotpStatus() async {
    totpStatus = await _loadTotpStatus();
    notifyListeners();
  }

  Future<void> updateSetting({
    required String field,
    bool? value,
    String? stringValue,
  }) async {
    final Map<String, dynamic> payload = <String, dynamic>{'field': field};
    if (value != null) {
      payload['value'] = value;
    }
    if (stringValue != null) {
      payload['stringValue'] = stringValue;
    }
    final dynamic data = await _apiClient.post(
      '/auth/update/setting',
      authorized: true,
      body: payload,
    );
    final UserSettings settings = UserSettings.fromJson(asMap(data));
    if (user != null) {
      user = user!.copyWith(settings: settings);
    }
    notifyListeners();
  }

  Future<void> updateProfileField({
    required String key,
    required String value,
  }) async {
    final dynamic data = await _apiClient.post(
      '/auth/update/profile',
      authorized: true,
      body: <String, dynamic>{'key': key, 'value': value.trim()},
    );
    user = UserDetails.fromJson(asMap(data));
    notifyListeners();
  }

  Future<SensitiveVerificationStatus> checkSensitiveVerification() async {
    final dynamic data = await _apiClient.get(
      '/auth/check-sensitive-verification',
      authorized: true,
    );
    return SensitiveVerificationStatus.fromJson(asMap(data));
  }

  Future<void> sendSensitiveVerificationCode() {
    return _apiClient.post(
      '/auth/send-code',
      authorized: true,
      body: const <String, dynamic>{'type': 'sensitive-verification'},
    );
  }

  Future<void> verifySensitiveOperation({
    required String method,
    String? password,
    String? code,
  }) {
    final Map<String, dynamic> payload = <String, dynamic>{'method': method};
    if (password != null && password.trim().isNotEmpty) {
      payload['password'] = password;
    }
    if (code != null && code.trim().isNotEmpty) {
      payload['code'] = code.trim();
    }
    return _apiClient.post(
      '/auth/verify-sensitive',
      authorized: true,
      body: payload,
    );
  }

  Future<PasskeyAssertionOptions> getPasskeyAuthenticationOptions() async {
    final dynamic data = await _apiClient.post(
      '/auth/passkey/authentication-options',
    );
    return PasskeyAssertionOptions.fromJson(asMap(data));
  }

  Future<void> loginWithPasskey() async {
    authBusy = true;
    pendingMfaChallenge = null;
    notifyListeners();
    try {
      final BrowserPasskeyBridgeResponse response =
          await BrowserPasskeyBridge.start(
            passkeyOrigin: passkeyOrigin,
            apiBaseUrl: apiBaseUrl,
            mode: BrowserPasskeyBridgeMode.login,
          );
      if (response.accessToken == null || response.accessToken!.isEmpty) {
        throw ApiException(response.message ?? '浏览器未返回登录结果');
      }
      _apiClient.accessToken = response.accessToken;
      await refreshWorkspace();
    } finally {
      authBusy = false;
      notifyListeners();
    }
  }

  Future<PasskeyAssertionOptions>
  getPasskeySensitiveVerificationOptions() async {
    final dynamic data = await _apiClient.post(
      '/auth/passkey/sensitive-verification-options',
      authorized: true,
    );
    return PasskeyAssertionOptions.fromJson(asMap(data));
  }

  Future<void> verifySensitiveOperationWithPasskey({
    required String challengeId,
    required PasskeyAssertionResult assertion,
  }) {
    return _apiClient.post(
      '/auth/passkey/sensitive-verification-verify',
      authorized: true,
      query: <String, String>{'challengeId': challengeId},
      body: assertion.toJson(),
    );
  }

  Future<void> verifySensitiveOperationWithPasskeyInBrowser() {
    return BrowserPasskeyBridge.start(
      passkeyOrigin: passkeyOrigin,
      apiBaseUrl: apiBaseUrl,
      mode: BrowserPasskeyBridgeMode.sensitive,
      accessToken: _apiClient.accessToken,
    ).then((BrowserPasskeyBridgeResponse response) {
      if (!response.verified) {
        throw ApiException(response.message ?? '浏览器未完成敏感验证');
      }
    });
  }

  Future<void> registerPasskeyInBrowser({String? preferredName}) async {
    final BrowserPasskeyBridgeResponse response =
        await BrowserPasskeyBridge.start(
          passkeyOrigin: passkeyOrigin,
          apiBaseUrl: apiBaseUrl,
          mode: BrowserPasskeyBridgeMode.register,
          accessToken: _apiClient.accessToken,
          passkeyName: preferredName,
        );
    if (!response.registered) {
      throw ApiException(response.message ?? '浏览器未完成 Passkey 登记');
    }
    await refreshPasskeys();
  }

  Future<void> sendChangeEmailCode(String email) {
    return _apiClient.post(
      '/auth/send-code',
      authorized: true,
      body: <String, dynamic>{'email': email.trim(), 'type': 'change-email'},
    );
  }

  Future<void> changeEmail({
    required String newEmail,
    required String code,
  }) async {
    final dynamic data = await _apiClient.post(
      '/auth/update/email',
      authorized: true,
      body: <String, dynamic>{'newEmail': newEmail.trim(), 'code': code.trim()},
    );
    final Map<String, dynamic> payload = asMap(data);
    if (user != null) {
      user = user!.copyWith(
        email: asString(payload['email']) ?? newEmail.trim(),
      );
    }
    notifyListeners();
  }

  Future<void> changePassword(String newPassword) {
    return _apiClient.post(
      '/auth/update/password',
      authorized: true,
      body: <String, dynamic>{'newPassword': newPassword},
    );
  }

  Future<void> revokeSession(int sessionId) async {
    await _apiClient.post('/auth/sessions/$sessionId/revoke', authorized: true);
    await refreshSessions();
  }

  Future<void> logoutAll() async {
    await _apiClient.post('/auth/logout/all', authorized: true);
    logout();
  }

  Future<void> renamePasskey(int passkeyId, String name) async {
    await _apiClient.put(
      '/auth/passkey/$passkeyId/rename',
      authorized: true,
      body: <String, dynamic>{'newName': name.trim()},
    );
    await refreshPasskeys();
  }

  Future<void> deletePasskey(int passkeyId) async {
    await _apiClient.delete('/auth/passkey/$passkeyId', authorized: true);
    await refreshPasskeys();
  }

  Future<TotpRegistrationOptionsResponse> getTotpRegistrationOptions() async {
    final dynamic data = await _apiClient.post(
      '/auth/totp/registration-options',
      authorized: true,
    );
    return TotpRegistrationOptionsResponse.fromJson(asMap(data));
  }

  Future<void> verifyTotpRegistration({
    required String code,
    required List<String> recoveryCodes,
  }) async {
    await _apiClient.post(
      '/auth/totp/registration-verify',
      authorized: true,
      body: <String, dynamic>{
        'code': code.trim(),
        'recoveryCodes': recoveryCodes,
      },
    );
    await refreshTotpStatus();
  }

  Future<List<String>> getRecoveryCodes() async {
    final dynamic data = await _apiClient.get(
      '/auth/totp/recovery-codes',
      authorized: true,
    );
    return asList(data).map((dynamic item) => item.toString()).toList();
  }

  Future<List<String>> regenerateRecoveryCodes() async {
    final dynamic data = await _apiClient.post(
      '/auth/totp/recovery-codes/regenerate',
      authorized: true,
    );
    await refreshTotpStatus();
    return asList(data).map((dynamic item) => item.toString()).toList();
  }

  Future<void> disableTotp() async {
    await _apiClient.post('/auth/totp/disable', authorized: true);
    await refreshTotpStatus();
  }

  Future<void> logout() async {
    try {
      if (isAuthenticated) {
        await _apiClient.post('/auth/logout', authorized: true);
      }
    } catch (_) {
      // Ignore logout API failures and clear local session anyway.
    }
    _resetLocalSessionState();
    notifyListeners();
  }

  Future<SessionTransferTicket> createSessionTransferTicket({
    required String target,
  }) async {
    if (!isAuthenticated) {
      throw ApiException('当前桌面端尚未登录');
    }
    final dynamic data = await _apiClient.post(
      '/auth/session-transfer/create',
      authorized: true,
      body: <String, dynamic>{'target': target},
    );
    return SessionTransferTicket.fromJson(asMap(data));
  }

  Future<void> importSessionTransferTicket(String transferCode) async {
    final dynamic data = await _apiClient.post(
      '/auth/session-transfer/exchange',
      body: <String, dynamic>{
        'transferCode': transferCode.trim(),
        'target': 'desktop',
      },
    );
    final String? nextAccessToken = asString(asMap(data)['accessToken']);
    if (nextAccessToken == null || nextAccessToken.isEmpty) {
      throw ApiException('跨端登录失败，未获取到 accessToken');
    }
    _apiClient.accessToken = nextAccessToken;
    pendingMfaChallenge = null;
    await refreshWorkspace();
  }

  Future<void> _consumeLoginPayload(dynamic data) async {
    final Map<String, dynamic> json = asMap(data);
    if (json.containsKey('challengeId')) {
      pendingMfaChallenge = MFAChallenge.fromJson(json);
      notifyListeners();
      return;
    }
    _apiClient.accessToken = asString(json['accessToken']);
    await refreshWorkspace();
  }

  Future<List<SessionItem>> _loadSessions() async {
    final dynamic data = await _apiClient.get(
      '/auth/sessions',
      authorized: true,
    );
    return asList(
      data,
    ).map((dynamic item) => SessionItem.fromJson(asMap(item))).toList();
  }

  Future<List<SensitiveLogItem>> _loadSensitiveLogs(
    SensitiveLogsQuery query,
  ) async {
    final dynamic data = await _apiClient.get(
      '/auth/sensitive-logs',
      authorized: true,
      query: query.toQuery(),
    );
    final Map<String, dynamic> payload = asMap(data);
    return asList(
      payload['data'],
    ).map((dynamic item) => SensitiveLogItem.fromJson(asMap(item))).toList();
  }

  Future<List<PasskeyListItem>> _loadPasskeys() async {
    final dynamic data = await _apiClient.get(
      '/auth/passkey/list',
      authorized: true,
    );
    final Map<String, dynamic> payload = asMap(data);
    return asList(
      payload['passkeys'],
    ).map((dynamic item) => PasskeyListItem.fromJson(asMap(item))).toList();
  }

  Future<TotpStatusResponse> _loadTotpStatus() async {
    final dynamic data = await _apiClient.get(
      '/auth/totp/status',
      authorized: true,
    );
    return TotpStatusResponse.fromJson(asMap(data));
  }
}

class KsuserApiClient {
  KsuserApiClient({required this.baseUrl});

  final HttpClient _httpClient = HttpClient()
    ..connectionTimeout = const Duration(seconds: 12);
  final Map<String, Cookie> _cookies = <String, Cookie>{};
  final String _desktopUserAgent = _buildDesktopUserAgent();
  String baseUrl;
  String? accessToken;
  VoidCallback? onSessionExpired;
  bool _warmingUp = false;
  Completer<String?>? _refreshCompleter;

  void updateBaseUrl(String value) {
    if (baseUrl == value) {
      return;
    }
    baseUrl = value;
    _cookies.clear();
  }

  Future<dynamic> get(
    String path, {
    Map<String, String>? query,
    bool authorized = false,
  }) {
    return _request('GET', path, query: query, authorized: authorized);
  }

  Future<dynamic> post(
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
    bool authorized = false,
  }) {
    return _request(
      'POST',
      path,
      query: query,
      body: body,
      authorized: authorized,
    );
  }

  Future<dynamic> put(
    String path, {
    Map<String, dynamic>? body,
    bool authorized = false,
  }) {
    return _request('PUT', path, body: body, authorized: authorized);
  }

  Future<dynamic> delete(String path, {bool authorized = false}) {
    return _request('DELETE', path, authorized: authorized);
  }

  void clearSession() {
    accessToken = null;
    _cookies.clear();
  }

  Future<void> _refreshCsrfToken({bool force = false}) async {
    if (!force && (_hasUsableCookie('XSRF-TOKEN') || _warmingUp)) {
      return;
    }
    _warmingUp = true;
    try {
      await _request('GET', '/auth/health', bypassWarmup: true);
    } catch (_) {
      // Ignore warmup errors and let the real request decide the outcome.
    } finally {
      _warmingUp = false;
    }
  }

  Future<dynamic> _request(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
    bool authorized = false,
    bool bypassWarmup = false,
    bool csrfRetried = false,
    bool tokenRetried = false,
  }) async {
    if (!bypassWarmup && method != 'GET') {
      await _refreshCsrfToken();
    }

    final Uri baseUri = Uri.parse(
      baseUrl.endsWith('/') ? baseUrl : '$baseUrl/',
    );
    final Uri uri = baseUri
        .resolve(path.startsWith('/') ? path.substring(1) : path)
        .replace(
          queryParameters: query == null || query.isEmpty ? null : query,
        );

    try {
      final HttpClientRequest request = await _httpClient.openUrl(method, uri);
      request.headers.set(HttpHeaders.userAgentHeader, _desktopUserAgent);
      request.headers.set(HttpHeaders.acceptHeader, 'application/json');
      request.headers.set(
        HttpHeaders.contentTypeHeader,
        'application/json; charset=utf-8',
      );
      if (authorized && accessToken != null) {
        request.headers.set(
          HttpHeaders.authorizationHeader,
          'Bearer $accessToken',
        );
      }
      if (_cookies.isNotEmpty) {
        request.headers.set(
          HttpHeaders.cookieHeader,
          _cookies.values
              .map((Cookie cookie) => '${cookie.name}=${cookie.value}')
              .join('; '),
        );
      }
      final Cookie? xsrf = _cookies['XSRF-TOKEN'];
      if (xsrf != null) {
        request.headers.set('X-XSRF-TOKEN', xsrf.value);
      }
      if (body != null) {
        request.add(utf8.encode(jsonEncode(body)));
      }

      final HttpClientResponse response = await request.close();
      _captureCookies(response);
      final String content = await response.transform(utf8.decoder).join();
      final dynamic decoded = content.isEmpty ? null : jsonDecode(content);
      final String? responseMessage = _extractMessage(decoded);

      if (response.statusCode == 401) {
        if (_canAttemptTokenRefresh(
          path,
          authorized: authorized,
          tokenRetried: tokenRetried,
        )) {
          try {
            await _refreshAccessToken();
            return _request(
              method,
              path,
              query: query,
              body: body,
              authorized: authorized,
              bypassWarmup: true,
              csrfRetried: csrfRetried,
              tokenRetried: true,
            );
          } on ApiException {
            // Fall through to the standard unauthorized error below.
          }
        }
        throw ApiException(responseMessage ?? '认证已失效，请重新登录', statusCode: 401);
      }
      if (!csrfRetried &&
          method != 'GET' &&
          response.statusCode == 403 &&
          responseMessage == '无权限') {
        await _refreshCsrfToken(force: true);
        return _request(
          method,
          path,
          query: query,
          body: body,
          authorized: authorized,
          bypassWarmup: true,
          csrfRetried: true,
        );
      }
      if (response.statusCode >= 400) {
        throw ApiException(
          responseMessage ?? '请求失败',
          statusCode: response.statusCode,
        );
      }
      if (decoded is Map<String, dynamic>) {
        final int? code = asInt(decoded['code']);
        if (code == 401 &&
            _canAttemptTokenRefresh(
              path,
              authorized: authorized,
              tokenRetried: tokenRetried,
            )) {
          try {
            await _refreshAccessToken();
            return _request(
              method,
              path,
              query: query,
              body: body,
              authorized: authorized,
              bypassWarmup: true,
              csrfRetried: csrfRetried,
              tokenRetried: true,
            );
          } on ApiException {
            throw ApiException(
              responseMessage ?? '认证已失效，请重新登录',
              statusCode: 401,
            );
          }
        }
        if (code != null && code >= 400) {
          throw ApiException(responseMessage ?? '请求失败', statusCode: code);
        }
        return decoded.containsKey('data') ? decoded['data'] : decoded;
      }
      return decoded;
    } on SocketException catch (error) {
      throw ApiException('无法连接到 ${uri.host}：${error.message}');
    } on HandshakeException catch (_) {
      throw ApiException('TLS 握手失败，请检查服务证书');
    } on TimeoutException catch (_) {
      throw ApiException('请求超时，请稍后重试');
    } on FormatException catch (_) {
      throw ApiException('服务返回了无法解析的数据');
    }
  }

  void _captureCookies(HttpClientResponse response) {
    final List<String>? headers = response.headers[HttpHeaders.setCookieHeader];
    if (headers == null) {
      return;
    }
    for (final String header in headers) {
      final Cookie cookie = Cookie.fromSetCookieValue(header);
      if (cookie.value.isEmpty || cookie.maxAge == 0) {
        _cookies.remove(cookie.name);
        continue;
      }
      _cookies[cookie.name] = cookie;
    }
  }

  String? _extractMessage(dynamic decoded) {
    if (decoded is Map<String, dynamic> && decoded['msg'] != null) {
      return decoded['msg'].toString();
    }
    return null;
  }

  bool _isRefreshRequest(String path) {
    return path.contains('/auth/refresh');
  }

  bool _hasUsableCookie(String name) {
    final Cookie? cookie = _cookies[name];
    if (cookie == null) {
      return false;
    }
    if (cookie.value.isEmpty || cookie.maxAge == 0) {
      _cookies.remove(name);
      return false;
    }
    return true;
  }

  bool _canAttemptTokenRefresh(
    String path, {
    required bool authorized,
    required bool tokenRetried,
  }) {
    if (tokenRetried || accessToken == null || accessToken!.isEmpty) {
      return false;
    }
    if (_isRefreshRequest(path) || _isAuthenticationBootstrapEndpoint(path)) {
      return false;
    }
    return authorized || path.startsWith('/auth/');
  }

  bool _isAuthenticationBootstrapEndpoint(String path) {
    const List<String> endpoints = <String>[
      '/auth/login',
      '/auth/login-with-code',
      '/auth/register',
      '/auth/totp/mfa-verify',
      '/auth/passkey/mfa-verify',
      '/auth/passkey/authentication-verify',
    ];
    return endpoints.any(path.contains);
  }

  Future<String> _refreshAccessToken() async {
    final Completer<String?>? existingCompleter = _refreshCompleter;
    if (existingCompleter != null) {
      final String? existingToken = await existingCompleter.future;
      if (existingToken == null || existingToken.isEmpty) {
        throw ApiException('认证已失效，请重新登录', statusCode: 401);
      }
      return existingToken;
    }

    final Completer<String?> completer = Completer<String?>();
    _refreshCompleter = completer;

    try {
      final dynamic data = await _request(
        'POST',
        '/auth/refresh',
        body: const <String, dynamic>{},
        bypassWarmup: false,
        tokenRetried: true,
      );
      final String? token = asString(asMap(data)['accessToken']);
      if (token == null || token.isEmpty) {
        throw ApiException('刷新 Token 失败', statusCode: 401);
      }
      accessToken = token;
      completer.complete(token);
      return token;
    } catch (_) {
      clearSession();
      onSessionExpired?.call();
      completer.complete(null);
      throw ApiException('认证已失效，请重新登录', statusCode: 401);
    } finally {
      _refreshCompleter = null;
    }
  }

  static String _buildDesktopUserAgent() {
    final String operatingSystem;
    if (Platform.isMacOS) {
      operatingSystem = 'macOS';
    } else if (Platform.isWindows) {
      operatingSystem = 'Windows';
    } else if (Platform.isLinux) {
      operatingSystem = 'Linux';
    } else {
      operatingSystem = Platform.operatingSystem;
    }

    return 'KsuserAuthDesktop/$kDesktopAppVersion ($operatingSystem; Flutter Desktop)';
  }
}

class ApiException implements Exception {
  ApiException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class SessionTransferTicket {
  const SessionTransferTicket({
    required this.transferCode,
    required this.expiresInSeconds,
  });

  factory SessionTransferTicket.fromJson(Map<String, dynamic> json) {
    return SessionTransferTicket(
      transferCode: asString(json['transferCode']) ?? '',
      expiresInSeconds: asInt(json['expiresInSeconds']) ?? 0,
    );
  }

  final String transferCode;
  final int expiresInSeconds;
}

class DesktopAuthPortal extends StatefulWidget {
  const DesktopAuthPortal({super.key, required this.controller});

  final AppController controller;

  @override
  State<DesktopAuthPortal> createState() => _DesktopAuthPortalState();
}

class _DesktopAuthPortalState extends State<DesktopAuthPortal> {
  final TextEditingController _apiController = TextEditingController();
  final TextEditingController _loginEmailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _codeController = TextEditingController();
  final TextEditingController _mfaCodeController = TextEditingController();
  final TextEditingController _mfaRecoveryController = TextEditingController();
  final TextEditingController _registerUsernameController =
      TextEditingController();
  final TextEditingController _registerEmailController =
      TextEditingController();
  final TextEditingController _registerPasswordController =
      TextEditingController();
  final TextEditingController _registerConfirmController =
      TextEditingController();
  final TextEditingController _registerCodeController = TextEditingController();

  AuthTab _tab = AuthTab.login;
  LoginFactor _loginFactor = LoginFactor.password;
  MfaMode _mfaMode = MfaMode.code;
  bool _checkingUsername = false;
  bool? _usernameAvailable;
  String? _usernameHint;
  bool _passkeyAvailable = false;
  bool _bridgeBusy = false;

  @override
  void initState() {
    super.initState();
    _apiController.text = widget.controller.apiBaseUrl;
    _loadPasswordRequirement();
    _detectPasskeyAvailability();
  }

  @override
  void dispose() {
    _apiController.dispose();
    _loginEmailController.dispose();
    _passwordController.dispose();
    _codeController.dispose();
    _mfaCodeController.dispose();
    _mfaRecoveryController.dispose();
    _registerUsernameController.dispose();
    _registerEmailController.dispose();
    _registerPasswordController.dispose();
    _registerConfirmController.dispose();
    _registerCodeController.dispose();
    super.dispose();
  }

  Future<void> _loadPasswordRequirement() async {
    try {
      widget.controller.updateApiBaseUrl(_apiController.text);
      await widget.controller.fetchPasswordRequirement();
    } catch (_) {
      // Show validation hints only when the user actually submits.
    }
  }

  Future<void> _detectPasskeyAvailability() async {
    final bool available = BrowserPasskeyBridge.isSupported(
      widget.controller.passkeyOrigin,
    );
    if (!mounted) {
      return;
    }
    setState(() {
      _passkeyAvailable = available;
    });
  }

  void _syncMfaModeWithChallenge() {
    final MFAChallenge? challenge = widget.controller.pendingMfaChallenge;
    if (challenge == null) {
      return;
    }
    final MfaMode nextMode;
    if (challenge.method == 'passkey' &&
        challenge.methods.contains('passkey')) {
      nextMode = MfaMode.passkey;
    } else {
      nextMode = MfaMode.code;
    }
    if (_mfaMode == nextMode) {
      return;
    }
    setState(() {
      _mfaMode = nextMode;
    });
  }

  Future<void> _login() async {
    final String email = _loginEmailController.text.trim();
    if (email.isEmpty) {
      _showError('请先输入邮箱地址');
      return;
    }

    widget.controller.updateApiBaseUrl(_apiController.text);

    try {
      if (_loginFactor == LoginFactor.password) {
        if (_passwordController.text.isEmpty) {
          _showError('请输入密码');
          return;
        }
        await widget.controller.loginWithPassword(
          email: email,
          password: _passwordController.text,
        );
      } else {
        if (_codeController.text.trim().length != 6) {
          _showError('请输入 6 位验证码');
          return;
        }
        await widget.controller.loginWithCode(
          email: email,
          code: _codeController.text,
        );
      }
      if (mounted && widget.controller.pendingMfaChallenge == null) {
        _showSuccess('登录成功');
      } else if (mounted) {
        _syncMfaModeWithChallenge();
        _showSuccess('第一步验证成功，请继续完成二次验证');
      }
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _sendLoginCode() async {
    final String email = _loginEmailController.text.trim();
    if (email.isEmpty) {
      _showError('请先输入邮箱地址');
      return;
    }
    widget.controller.updateApiBaseUrl(_apiController.text);
    try {
      await widget.controller.sendLoginCode(email);
      _showSuccess('验证码已发送');
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _completeMfa() async {
    final MFAChallenge? challenge = widget.controller.pendingMfaChallenge;
    if (challenge == null) {
      return;
    }
    if (_mfaMode == MfaMode.passkey) {
      try {
        await widget.controller.completePasskeyMfa(
          challengeId: challenge.challengeId,
        );
        _showSuccess('二次验证通过');
      } catch (error) {
        _showError(error.toString());
      }
      return;
    }
    if (_mfaMode == MfaMode.code &&
        _mfaCodeController.text.trim().length != 6) {
      _showError('请输入 6 位动态码');
      return;
    }
    if (_mfaMode == MfaMode.recoveryCode &&
        _mfaRecoveryController.text.trim().isEmpty) {
      _showError('请输入恢复码');
      return;
    }
    try {
      await widget.controller.completeTotpMfa(
        challengeId: challenge.challengeId,
        code: _mfaMode == MfaMode.code ? _mfaCodeController.text : null,
        recoveryCode: _mfaMode == MfaMode.recoveryCode
            ? _mfaRecoveryController.text
            : null,
      );
      _showSuccess('二次验证通过');
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _loginWithPasskey() async {
    if (!_passkeyAvailable) {
      _showError('当前环境未配置浏览器 Passkey 桥接');
      return;
    }
    widget.controller.updateApiBaseUrl(_apiController.text);
    try {
      await widget.controller.loginWithPasskey();
      if (mounted && widget.controller.pendingMfaChallenge == null) {
        _showSuccess('Passkey 登录成功');
      } else if (mounted) {
        _syncMfaModeWithChallenge();
        _showSuccess('Passkey 验证成功，请继续完成二次验证');
      }
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _checkUsername() async {
    final String username = _registerUsernameController.text.trim();
    if (username.isEmpty) {
      _showError('请先输入用户名');
      return;
    }
    setState(() {
      _checkingUsername = true;
      _usernameHint = null;
    });
    widget.controller.updateApiBaseUrl(_apiController.text);
    final bool available = await widget.controller.checkUsername(username);
    if (!mounted) {
      return;
    }
    setState(() {
      _checkingUsername = false;
      _usernameAvailable = available;
      _usernameHint = available ? '用户名可用' : '用户名已被占用';
    });
  }

  Future<void> _sendRegisterCode() async {
    final String email = _registerEmailController.text.trim();
    if (email.isEmpty) {
      _showError('请先输入注册邮箱');
      return;
    }
    widget.controller.updateApiBaseUrl(_apiController.text);
    try {
      await widget.controller.sendRegisterCode(email);
      _showSuccess('注册验证码已发送');
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _register() async {
    final String username = _registerUsernameController.text.trim();
    final String email = _registerEmailController.text.trim();
    final String password = _registerPasswordController.text;
    final String confirm = _registerConfirmController.text;
    final String code = _registerCodeController.text.trim();

    if (username.isEmpty || email.isEmpty || password.isEmpty || code.isEmpty) {
      _showError('请完整填写注册信息');
      return;
    }
    if (password != confirm) {
      _showError('两次输入的密码不一致');
      return;
    }
    final PasswordRequirement? requirement =
        widget.controller.passwordRequirement;
    if (requirement != null) {
      final String? error = requirement.validate(password);
      if (error != null) {
        _showError(error);
        return;
      }
    }

    widget.controller.updateApiBaseUrl(_apiController.text);
    try {
      await widget.controller.register(
        username: username,
        email: email,
        password: password,
        code: code,
      );
      _showSuccess('账号创建完成');
    } catch (error) {
      _showError(error.toString());
    }
  }

  Uri _buildWebLoginUri({
    String? transferCode,
    bool desktopBridgeHint = false,
  }) {
    final String origin = widget.controller.passkeyOrigin.trim();
    final Uri baseUri = Uri.parse(origin.endsWith('/') ? origin : '$origin/');
    return baseUri
        .resolve('login')
        .replace(
          queryParameters: <String, String>{
            if (desktopBridgeHint) 'desktopBridge': '1',
            if (transferCode != null && transferCode.isNotEmpty)
              'transferCode': transferCode,
            if (widget.controller.apiBaseUrl.trim().isNotEmpty)
              'apiBaseUrl': widget.controller.apiBaseUrl.trim(),
          },
        );
  }

  Future<void> _openBrowserLoginForDesktopSync() async {
    if (_bridgeBusy) {
      return;
    }
    setState(() {
      _bridgeBusy = true;
    });
    try {
      await openExternalUrl(
        _buildWebLoginUri(desktopBridgeHint: true).toString(),
      );
      if (mounted) {
        _showSuccess('已打开浏览器，请在网页端登录，桌面端会自动接收登录状态');
      }
    } catch (error) {
      _showError(error.toString());
    } finally {
      if (mounted) {
        setState(() {
          _bridgeBusy = false;
        });
      }
    }
  }

  void _showError(String message) {
    showAppMessage(context, message, error: true);
  }

  void _showSuccess(String message) {
    showAppMessage(context, message);
  }

  @override
  Widget build(BuildContext context) {
    final PasswordRequirement? requirement =
        widget.controller.passwordRequirement;
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: LayoutBuilder(
            builder: (BuildContext context, BoxConstraints constraints) {
              final bool compact = constraints.maxWidth < 980;
              final Widget rightPane = AnimatedBuilder(
                animation: widget.controller,
                builder: (context, _) {
                  final Widget formContent = SingleChildScrollView(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        if (_tab == AuthTab.login) ...<Widget>[
                          Text(
                            '账户登录',
                            style: Theme.of(context).textTheme.headlineSmall
                                ?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 8),
                          const Text('桌面端采用工作台布局，保留网页端相同的认证信息与流程。'),
                          const SizedBox(height: 20),
                          SegmentedButton<LoginFactor>(
                            segments: const <ButtonSegment<LoginFactor>>[
                              ButtonSegment<LoginFactor>(
                                value: LoginFactor.password,
                                icon: Icon(Icons.password_rounded),
                                label: Text('密码'),
                              ),
                              ButtonSegment<LoginFactor>(
                                value: LoginFactor.emailCode,
                                icon: Icon(Icons.mark_email_read_rounded),
                                label: Text('邮箱验证码'),
                              ),
                            ],
                            selected: <LoginFactor>{_loginFactor},
                            onSelectionChanged: (Set<LoginFactor> selection) {
                              setState(() {
                                _loginFactor = selection.first;
                              });
                            },
                          ),
                          const SizedBox(height: 16),
                          TextField(
                            controller: _loginEmailController,
                            decoration: const InputDecoration(
                              labelText: '邮箱地址',
                              prefixIcon: Icon(Icons.alternate_email_rounded),
                            ),
                          ),
                          const SizedBox(height: 16),
                          if (_loginFactor == LoginFactor.password)
                            TextField(
                              controller: _passwordController,
                              obscureText: true,
                              decoration: const InputDecoration(
                                labelText: '密码',
                                prefixIcon: Icon(Icons.lock_outline_rounded),
                              ),
                            )
                          else
                            Column(
                              children: <Widget>[
                                TextField(
                                  controller: _codeController,
                                  decoration: const InputDecoration(
                                    labelText: '6 位验证码',
                                    prefixIcon: Icon(Icons.pin_outlined),
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Align(
                                  alignment: Alignment.centerLeft,
                                  child: OutlinedButton.icon(
                                    onPressed: widget.controller.authBusy
                                        ? null
                                        : _sendLoginCode,
                                    icon: const Icon(
                                      Icons.send_to_mobile_rounded,
                                    ),
                                    label: const Text('发送验证码'),
                                  ),
                                ),
                              ],
                            ),
                          const SizedBox(height: 20),
                          SizedBox(
                            width: double.infinity,
                            child: FilledButton.icon(
                              onPressed: widget.controller.authBusy
                                  ? null
                                  : _login,
                              icon: widget.controller.authBusy
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.login_rounded),
                              label: const Text('登录'),
                            ),
                          ),
                          const SizedBox(height: 12),
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(18),
                              border: Border.all(
                                color: Colors.black.withValues(alpha: 0.06),
                              ),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: <Widget>[
                                Row(
                                  children: <Widget>[
                                    const Icon(
                                      Icons.language_rounded,
                                      color: kPrimaryColor,
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        children: const <Widget>[
                                          Text(
                                            '从网页登录到桌面端',
                                            style: TextStyle(
                                              fontWeight: FontWeight.w700,
                                            ),
                                          ),
                                          SizedBox(height: 4),
                                          Text(
                                            '打开浏览器登录页；登录成功后，网页会把登录态自动同步回当前桌面应用。',
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                FilledButton.tonalIcon(
                                  onPressed: _bridgeBusy
                                      ? null
                                      : _openBrowserLoginForDesktopSync,
                                  icon: _bridgeBusy
                                      ? const SizedBox(
                                          width: 18,
                                          height: 18,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                          ),
                                        )
                                      : const Icon(
                                          Icons.open_in_browser_rounded,
                                        ),
                                  label: const Text('打开网页登录页'),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 18),
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: const Color(0xFFFBFAF5),
                              borderRadius: BorderRadius.circular(18),
                              border: Border.all(
                                color: Colors.black.withValues(alpha: 0.06),
                              ),
                            ),
                            child: Row(
                              children: <Widget>[
                                const Icon(
                                  Icons.fingerprint_rounded,
                                  color: kPrimaryColor,
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: <Widget>[
                                      const Text(
                                        'Passkey 登录',
                                        style: TextStyle(
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        _passkeyAvailable
                                            ? '将自动打开浏览器完成 WebAuthn，然后把结果直接回传到桌面端。'
                                            : '当前环境未配置可用的 Passkey 浏览器桥接地址。',
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 12),
                                FilledButton.tonalIcon(
                                  onPressed:
                                      !_passkeyAvailable ||
                                          widget.controller.authBusy
                                      ? null
                                      : _loginWithPasskey,
                                  icon: const Icon(Icons.fingerprint_rounded),
                                  label: const Text('Passkey 登录'),
                                ),
                              ],
                            ),
                          ),
                          if (widget.controller.pendingMfaChallenge !=
                              null) ...<Widget>[
                            const SizedBox(height: 18),
                            _MfaPanel(
                              challenge: widget.controller.pendingMfaChallenge!,
                              mode: _mfaMode,
                              codeController: _mfaCodeController,
                              recoveryController: _mfaRecoveryController,
                              busy: widget.controller.authBusy,
                              passkeyAvailable: _passkeyAvailable,
                              onModeChanged: (MfaMode mode) {
                                setState(() {
                                  _mfaMode = mode;
                                });
                              },
                              onSubmit: _completeMfa,
                            ),
                          ],
                        ] else ...<Widget>[
                          Text(
                            '创建新账户',
                            style: Theme.of(context).textTheme.headlineSmall
                                ?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 8),
                          const Text('桌面端注册改为并排信息表单，不沿用网页端的分步卡片。'),
                          const SizedBox(height: 20),
                          Row(
                            children: <Widget>[
                              Expanded(
                                child: TextField(
                                  controller: _registerUsernameController,
                                  decoration: InputDecoration(
                                    labelText: '用户名',
                                    prefixIcon: const Icon(
                                      Icons.person_outline_rounded,
                                    ),
                                    suffixIcon: IconButton(
                                      onPressed: _checkingUsername
                                          ? null
                                          : _checkUsername,
                                      icon: _checkingUsername
                                          ? const SizedBox(
                                              width: 18,
                                              height: 18,
                                              child: CircularProgressIndicator(
                                                strokeWidth: 2,
                                              ),
                                            )
                                          : const Icon(
                                              Icons.verified_user_outlined,
                                            ),
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: TextField(
                                  controller: _registerEmailController,
                                  decoration: const InputDecoration(
                                    labelText: '邮箱',
                                    prefixIcon: Icon(
                                      Icons.mail_outline_rounded,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          if (_usernameHint != null) ...<Widget>[
                            const SizedBox(height: 10),
                            Text(
                              _usernameHint!,
                              style: TextStyle(
                                color: _usernameAvailable == true
                                    ? Colors.green.shade700
                                    : Colors.red.shade700,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                          const SizedBox(height: 16),
                          Row(
                            children: <Widget>[
                              Expanded(
                                child: TextField(
                                  controller: _registerPasswordController,
                                  obscureText: true,
                                  decoration: const InputDecoration(
                                    labelText: '密码',
                                    prefixIcon: Icon(Icons.key_rounded),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: TextField(
                                  controller: _registerConfirmController,
                                  obscureText: true,
                                  decoration: const InputDecoration(
                                    labelText: '确认密码',
                                    prefixIcon: Icon(Icons.task_alt_rounded),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),
                          Row(
                            children: <Widget>[
                              Expanded(
                                child: TextField(
                                  controller: _registerCodeController,
                                  decoration: const InputDecoration(
                                    labelText: '邮箱验证码',
                                    prefixIcon: Icon(
                                      Icons.confirmation_number_outlined,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              OutlinedButton.icon(
                                onPressed: widget.controller.authBusy
                                    ? null
                                    : _sendRegisterCode,
                                icon: const Icon(Icons.send_rounded),
                                label: const Text('发送验证码'),
                              ),
                            ],
                          ),
                          const SizedBox(height: 18),
                          _PasswordRequirementCard(requirement: requirement),
                          const SizedBox(height: 18),
                          SizedBox(
                            width: double.infinity,
                            child: FilledButton.icon(
                              onPressed: widget.controller.authBusy
                                  ? null
                                  : _register,
                              icon: widget.controller.authBusy
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.rocket_launch_rounded),
                              label: const Text('完成注册'),
                            ),
                          ),
                        ],
                      ],
                    ),
                  );

                  return Card(
                    child: Padding(
                      padding: const EdgeInsets.all(28),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(
                            'Ksuser Auth 统一认证中心',
                            style: Theme.of(context).textTheme.headlineSmall
                                ?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 8),
                          const Text('桌面端默认使用当前环境配置，不再暴露环境和 API 地址选择。'),
                          const SizedBox(height: 24),
                          SegmentedButton<AuthTab>(
                            segments: const <ButtonSegment<AuthTab>>[
                              ButtonSegment<AuthTab>(
                                value: AuthTab.login,
                                label: Text('登录'),
                                icon: Icon(Icons.login_rounded),
                              ),
                              ButtonSegment<AuthTab>(
                                value: AuthTab.register,
                                label: Text('注册'),
                                icon: Icon(Icons.person_add_alt_1_rounded),
                              ),
                            ],
                            selected: <AuthTab>{_tab},
                            onSelectionChanged: (Set<AuthTab> selection) {
                              setState(() {
                                _tab = selection.first;
                              });
                            },
                          ),
                          const SizedBox(height: 24),
                          Expanded(child: formContent),
                        ],
                      ),
                    ),
                  );
                },
              );

              return Align(
                alignment: Alignment.topCenter,
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: compact ? 760 : 920),
                  child: rightPane,
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}

class DesktopWorkspace extends StatelessWidget {
  const DesktopWorkspace({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final UserDetails? user = controller.user;
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    final Color sidebarBackground = isDark
        ? const Color(0xFF222222)
        : const Color(0xFFF1ECE0);
    final Color sidebarBorder = isDark
        ? Colors.white.withValues(alpha: 0.06)
        : const Color(0xFFE1D8C4);
    final Color sidebarTitleColor = isDark
        ? Colors.white
        : const Color(0xFF2F281C);
    final Color sidebarSubtitleColor = isDark
        ? Colors.white70
        : const Color(0xFF6B614E);
    final Color railSelectedTextColor = isDark
        ? const Color(0xFF2A2204)
        : const Color(0xFF3A2C00);
    final Color railUnselectedColor = isDark
        ? Colors.white70
        : const Color(0xFF756A55);

    return Scaffold(
      body: SafeArea(
        child: Row(
          children: <Widget>[
            LayoutBuilder(
              builder: (BuildContext context, BoxConstraints constraints) {
                final bool extended = MediaQuery.of(context).size.width > 1240;
                final double railWidth = extended ? 248 : 88;

                return Padding(
                  padding: const EdgeInsets.fromLTRB(18, 18, 0, 18),
                  child: SizedBox(
                    width: railWidth,
                    child: Container(
                      decoration: BoxDecoration(
                        color: sidebarBackground,
                        borderRadius: BorderRadius.circular(28),
                        border: Border.all(color: sidebarBorder),
                        boxShadow: <BoxShadow>[
                          BoxShadow(
                            color: Colors.black.withValues(
                              alpha: isDark ? 0.18 : 0.04,
                            ),
                            blurRadius: 24,
                            offset: const Offset(0, 10),
                          ),
                        ],
                      ),
                      child: NavigationRail(
                        backgroundColor: Colors.transparent,
                        selectedIndex: DesktopSection.values.indexOf(
                          controller.selectedSection,
                        ),
                        extended: extended,
                        groupAlignment: -1,
                        minWidth: 88,
                        minExtendedWidth: 248,
                        onDestinationSelected: (int index) {
                          controller.setSection(DesktopSection.values[index]);
                        },
                        leading: Padding(
                          padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
                          child: SizedBox(
                            width: extended ? 194 : 48,
                            child: extended
                                ? Column(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: <Widget>[
                                      Row(
                                        children: <Widget>[
                                          ClipRRect(
                                            borderRadius: BorderRadius.circular(
                                              16,
                                            ),
                                            child: Image.asset(
                                              kSidebarLogoAsset,
                                              width: 48,
                                              height: 48,
                                              fit: BoxFit.cover,
                                            ),
                                          ),
                                          const SizedBox(width: 12),
                                          Expanded(
                                            child: Column(
                                              mainAxisSize: MainAxisSize.min,
                                              crossAxisAlignment:
                                                  CrossAxisAlignment.start,
                                              children: <Widget>[
                                                Text(
                                                  'Ksuser Auth',
                                                  maxLines: 1,
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                  style: TextStyle(
                                                    color: sidebarTitleColor,
                                                    fontWeight: FontWeight.w800,
                                                    fontSize: 18,
                                                  ),
                                                ),
                                                const SizedBox(height: 2),
                                                Text(
                                                  '统一认证中心',
                                                  style: TextStyle(
                                                    color: sidebarSubtitleColor,
                                                    fontSize: 12,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                        ],
                                      ),
                                      if (user != null) ...<Widget>[
                                        const SizedBox(height: 16),
                                        Container(
                                          padding: const EdgeInsets.all(14),
                                          decoration: BoxDecoration(
                                            color: isDark
                                                ? Colors.white.withValues(
                                                    alpha: 0.04,
                                                  )
                                                : Colors.white.withValues(
                                                    alpha: 0.72,
                                                  ),
                                            borderRadius: BorderRadius.circular(
                                              18,
                                            ),
                                            border: Border.all(
                                              color: isDark
                                                  ? Colors.white.withValues(
                                                      alpha: 0.06,
                                                    )
                                                  : Colors.black.withValues(
                                                      alpha: 0.04,
                                                    ),
                                            ),
                                          ),
                                          child: Column(
                                            mainAxisSize: MainAxisSize.min,
                                            crossAxisAlignment:
                                                CrossAxisAlignment.start,
                                            children: <Widget>[
                                              Text(
                                                user.username,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: TextStyle(
                                                  color: sidebarTitleColor,
                                                  fontWeight: FontWeight.w700,
                                                ),
                                              ),
                                              const SizedBox(height: 4),
                                              Text(
                                                user.email,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: TextStyle(
                                                  color: sidebarSubtitleColor,
                                                  fontSize: 12,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ],
                                    ],
                                  )
                                : ClipRRect(
                                    borderRadius: BorderRadius.circular(16),
                                    child: Image.asset(
                                      kSidebarLogoAsset,
                                      width: 48,
                                      height: 48,
                                      fit: BoxFit.cover,
                                    ),
                                  ),
                          ),
                        ),
                        unselectedIconTheme: IconThemeData(
                          color: railUnselectedColor,
                        ),
                        unselectedLabelTextStyle: TextStyle(
                          color: railUnselectedColor,
                          fontWeight: FontWeight.w600,
                        ),
                        selectedIconTheme: IconThemeData(
                          color: railSelectedTextColor,
                        ),
                        selectedLabelTextStyle: TextStyle(
                          color: railSelectedTextColor,
                          fontWeight: FontWeight.w700,
                        ),
                        indicatorColor: kPrimaryColor,
                        destinations: const <NavigationRailDestination>[
                          NavigationRailDestination(
                            icon: Icon(Icons.dashboard_outlined),
                            selectedIcon: Icon(
                              Icons.dashboard_customize_rounded,
                            ),
                            label: Text('概览'),
                          ),
                          NavigationRailDestination(
                            icon: Icon(Icons.badge_outlined),
                            selectedIcon: Icon(Icons.badge_rounded),
                            label: Text('资料'),
                          ),
                          NavigationRailDestination(
                            icon: Icon(Icons.lock_outline_rounded),
                            selectedIcon: Icon(Icons.verified_user_rounded),
                            label: Text('安全'),
                          ),
                          NavigationRailDestination(
                            icon: Icon(Icons.devices_other_outlined),
                            selectedIcon: Icon(Icons.devices_rounded),
                            label: Text('设备'),
                          ),
                          NavigationRailDestination(
                            icon: Icon(Icons.history_outlined),
                            selectedIcon: Icon(Icons.history_rounded),
                            label: Text('日志'),
                          ),
                        ],
                        trailing: Padding(
                          padding: const EdgeInsets.fromLTRB(14, 8, 14, 16),
                          child: SizedBox(
                            width: extended ? 194 : 48,
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: <Widget>[
                                if (extended)
                                  Text(
                                    '账户操作',
                                    style: TextStyle(
                                      color: sidebarSubtitleColor,
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                if (extended) const SizedBox(height: 10),
                                if (extended)
                                  FilledButton.tonalIcon(
                                    onPressed: () async {
                                      await controller.logout();
                                    },
                                    icon: const Icon(Icons.logout_rounded),
                                    label: const Text('退出登录'),
                                    style: FilledButton.styleFrom(
                                      foregroundColor: sidebarTitleColor,
                                      backgroundColor: isDark
                                          ? Colors.white.withValues(alpha: 0.06)
                                          : Colors.white.withValues(
                                              alpha: 0.78,
                                            ),
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 14,
                                        vertical: 12,
                                      ),
                                      shape: RoundedRectangleBorder(
                                        borderRadius: BorderRadius.circular(16),
                                      ),
                                    ),
                                  )
                                else
                                  IconButton.filledTonal(
                                    onPressed: () async {
                                      await controller.logout();
                                    },
                                    style: IconButton.styleFrom(
                                      foregroundColor: sidebarTitleColor,
                                      backgroundColor: isDark
                                          ? Colors.white.withValues(alpha: 0.06)
                                          : Colors.white.withValues(
                                              alpha: 0.78,
                                            ),
                                    ),
                                    icon: const Icon(Icons.logout_rounded),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(24, 22, 24, 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Row(
                          children: <Widget>[
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                mainAxisSize: MainAxisSize.min,
                                children: <Widget>[
                                  Text(
                                    sectionTitle(controller.selectedSection),
                                    style: theme.textTheme.headlineSmall
                                        ?.copyWith(fontWeight: FontWeight.w700),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    sectionSubtitle(controller.selectedSection),
                                  ),
                                ],
                              ),
                            ),
                            IconButton(
                              tooltip: '设置',
                              onPressed: () {
                                showDesktopSettingsDialog(context, controller);
                              },
                              icon: const Icon(Icons.settings_rounded),
                            ),
                            const SizedBox(width: 8),
                            FilledButton.icon(
                              onPressed: controller.workspaceLoading
                                  ? null
                                  : () async {
                                      try {
                                        await controller.refreshWorkspace();
                                        if (context.mounted) {
                                          showAppMessage(context, '桌面数据已刷新');
                                        }
                                      } catch (error) {
                                        if (context.mounted) {
                                          showAppMessage(
                                            context,
                                            error.toString(),
                                            error: true,
                                          );
                                        }
                                      }
                                    },
                              icon: controller.workspaceLoading
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                      ),
                                    )
                                  : const Icon(Icons.refresh_rounded),
                              label: const Text('刷新'),
                            ),
                          ],
                        ),
                        if (user != null) ...<Widget>[
                          const SizedBox(height: 18),
                          _WorkspaceBanner(user: user, controller: controller),
                        ],
                        const SizedBox(height: 22),
                        if (controller.workspaceError != null)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 16),
                            child: Material(
                              color: Colors.red.shade50,
                              borderRadius: BorderRadius.circular(18),
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Row(
                                  children: <Widget>[
                                    Icon(
                                      Icons.error_outline_rounded,
                                      color: Colors.red.shade700,
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: Text(controller.workspaceError!),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        Expanded(
                          child:
                              controller.workspaceLoading &&
                                  controller.user == null
                              ? const Center(child: CircularProgressIndicator())
                              : _buildSection(context),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSection(BuildContext context) {
    switch (controller.selectedSection) {
      case DesktopSection.overview:
        return OverviewPage(controller: controller);
      case DesktopSection.profile:
        return ProfilePage(controller: controller);
      case DesktopSection.security:
        return SecurityPage(controller: controller);
      case DesktopSection.devices:
        return DevicesPage(controller: controller);
      case DesktopSection.activity:
        return ActivityPage(controller: controller);
    }
  }
}

class OverviewPage extends StatelessWidget {
  const OverviewPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final UserDetails? user = controller.user;
    if (user == null) {
      return const SizedBox.shrink();
    }
    final int securityScore = computeSecurityScore(user, controller);
    final List<String> methods = <String>[
      '密码',
      '邮箱验证码',
      if (controller.passkeys.isNotEmpty) 'Passkey',
      if (controller.totpStatus?.enabled == true) 'TOTP',
    ];

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _SectionCard(
            title: '跨端联通',
            subtitle: '桌面端与网页端可直接互通，无需重复输入密码。',
            child: Wrap(
              spacing: 12,
              runSpacing: 12,
              children: <Widget>[
                FilledButton.icon(
                  onPressed: () async {
                    try {
                      final SessionTransferTicket ticket = await controller
                          .createSessionTransferTicket(target: 'web');
                      final Uri baseUri = Uri.parse(
                        controller.passkeyOrigin.endsWith('/')
                            ? controller.passkeyOrigin
                            : '${controller.passkeyOrigin}/',
                      );
                      final Uri launchUri = baseUri
                          .resolve('login')
                          .replace(
                            queryParameters: <String, String>{
                              'transferCode': ticket.transferCode,
                              'from': 'desktop',
                              'apiBaseUrl': controller.apiBaseUrl,
                            },
                          );
                      await openExternalUrl(launchUri.toString());
                      if (context.mounted) {
                        showAppMessage(context, '已在浏览器打开网页端并自动登录');
                      }
                    } catch (error) {
                      if (context.mounted) {
                        showAppMessage(context, error.toString(), error: true);
                      }
                    }
                  },
                  icon: const Icon(Icons.open_in_browser_rounded),
                  label: const Text('打开网页端并自动登录'),
                ),
                FilledButton.tonalIcon(
                  onPressed: () async {
                    try {
                      final Uri baseUri = Uri.parse(
                        controller.passkeyOrigin.endsWith('/')
                            ? controller.passkeyOrigin
                            : '${controller.passkeyOrigin}/',
                      );
                      final Uri launchUri = baseUri
                          .resolve('login')
                          .replace(
                            queryParameters: <String, String>{
                              'desktopBridge': '1',
                              'from': 'desktop',
                              'apiBaseUrl': controller.apiBaseUrl,
                            },
                          );
                      await openExternalUrl(launchUri.toString());
                      if (context.mounted) {
                        showAppMessage(context, '已打开网页登录页；网页登录成功后会自动同步回桌面端');
                      }
                    } catch (error) {
                      if (context.mounted) {
                        showAppMessage(context, error.toString(), error: true);
                      }
                    }
                  },
                  icon: const Icon(Icons.sync_alt_rounded),
                  label: const Text('通过网页登录同步回桌面端'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 16,
            runSpacing: 16,
            children: <Widget>[
              _MetricCard(
                title: '账户身份',
                value: user.username,
                caption: user.email,
                icon: Icons.person_outline_rounded,
              ),
              _MetricCard(
                title: '安全评分',
                value: '$securityScore%',
                caption: controller.totpStatus?.enabled == true
                    ? '已启用 TOTP 防护'
                    : '建议开启 TOTP',
                icon: Icons.shield_moon_outlined,
              ),
              _MetricCard(
                title: '在线设备',
                value: '${controller.sessions.length}',
                caption:
                    '${controller.sessions.where((SessionItem item) => item.online).length} 台在线',
                icon: Icons.devices_other_rounded,
              ),
              _MetricCard(
                title: '敏感日志',
                value: '${controller.sensitiveLogs.length}',
                caption: '桌面端同步最近操作',
                icon: Icons.rule_folder_outlined,
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                flex: 3,
                child: _SectionCard(
                  title: '资料摘要',
                  subtitle: '与网页端相同的信息字段，改为桌面式信息栅格。',
                  child: Wrap(
                    spacing: 16,
                    runSpacing: 16,
                    children: <Widget>[
                      _InfoChip(label: 'UUID', value: user.uuid),
                      _InfoChip(label: '真实姓名', value: user.realName ?? '未填写'),
                      _InfoChip(label: '地区', value: user.region ?? '未填写'),
                      _InfoChip(label: '性别', value: displayGender(user.gender)),
                      _InfoChip(
                        label: '资料更新时间',
                        value: formatDateTime(user.updatedAt),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 2,
                child: _SectionCard(
                  title: '登录方式',
                  subtitle: '展示与网页端一致的认证能力状态。',
                  child: Wrap(
                    spacing: 10,
                    runSpacing: 10,
                    children: methods
                        .map(
                          (String item) => Chip(
                            label: Text(item),
                            avatar: const Icon(
                              Icons.check_circle_outline_rounded,
                              size: 18,
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                flex: 3,
                child: _SectionCard(
                  title: '近期设备活动',
                  subtitle: '最近会话按桌面列表展示。',
                  child: Column(
                    children: controller.sessions.take(4).map((
                      SessionItem item,
                    ) {
                      return _SessionRow(item: item, compact: true);
                    }).toList(),
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 2,
                child: _SectionCard(
                  title: '近期敏感操作',
                  subtitle: '与后端 `sensitive-logs` 数据同步。',
                  child: Column(
                    children: controller.sensitiveLogs.take(4).map((
                      SensitiveLogItem item,
                    ) {
                      return _LogTile(log: item, compact: true);
                    }).toList(),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final UserDetails? user = controller.user;
    if (user == null) {
      return const SizedBox.shrink();
    }

    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '基础信息',
            subtitle: '在这里查看你的基础信息',
            child: Column(
              children: <Widget>[
                _EditableRow(
                  label: '用户名',
                  value: user.username,
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改用户名',
                    initialValue: user.username,
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'username',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(
                  label: '头像 URL',
                  value: user.avatarUrl ?? '未设置',
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改头像地址',
                    initialValue: user.avatarUrl ?? '',
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'avatarUrl',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(label: '邮箱', value: user.email, onEdit: null),
                _EditableRow(label: 'UUID', value: user.uuid, onEdit: null),
              ],
            ),
          ),
          const SizedBox(height: 16),
          _SectionCard(
            title: '扩展资料',
            subtitle: '在这里查看与修改你的个人信息',
            child: Column(
              children: <Widget>[
                _EditableRow(
                  label: '真实姓名',
                  value: user.realName ?? '未填写',
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改真实姓名',
                    initialValue: user.realName ?? '',
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'realName',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(
                  label: '性别',
                  value: displayGender(user.gender),
                  onEdit: () => _showSelectionDialog(
                    context,
                    title: '选择性别',
                    currentValue: user.gender ?? 'secret',
                    options: const <_SelectionOption>[
                      _SelectionOption(value: 'male', label: '男'),
                      _SelectionOption(value: 'female', label: '女'),
                      _SelectionOption(value: 'secret', label: '保密'),
                    ],
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'gender',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(
                  label: '出生日期',
                  value: user.birthDate ?? '未填写',
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改出生日期',
                    initialValue: user.birthDate ?? '',
                    hintText: 'YYYY-MM-DD',
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'birthDate',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(
                  label: '地区',
                  value: user.region ?? '未填写',
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改地区',
                    initialValue: user.region ?? '',
                    onSubmit: (String value) => controller.updateProfileField(
                      key: 'region',
                      value: value,
                    ),
                  ),
                ),
                _EditableRow(
                  label: '简介',
                  value: user.bio ?? '未填写',
                  onEdit: () => _showEditDialog(
                    context,
                    title: '修改简介',
                    initialValue: user.bio ?? '',
                    maxLines: 4,
                    onSubmit: (String value) =>
                        controller.updateProfileField(key: 'bio', value: value),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class SecurityPage extends StatelessWidget {
  const SecurityPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final UserDetails? user = controller.user;
    final UserSettings settings = user?.settings ?? const UserSettings();
    final TotpStatusResponse? totp = controller.totpStatus;

    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                flex: 3,
                child: _SectionCard(
                  title: '安全开关',
                  subtitle: '在这里管理你的账号安全偏好',
                  child: Column(
                    children: <Widget>[
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('启用 MFA'),
                        subtitle: const Text('登录时增加第二道验证。'),
                        value: settings.mfaEnabled,
                        onChanged: (bool value) async {
                          await _runWithFeedback(
                            context,
                            () => controller.updateSetting(
                              field: 'mfaEnabled',
                              value: value,
                            ),
                            success: value ? '已开启 MFA' : '已关闭 MFA',
                          );
                        },
                      ),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('异地登录检测'),
                        subtitle: const Text('发现异常位置登录时提醒。'),
                        value: settings.detectUnusualLogin,
                        onChanged: (bool value) async {
                          await _runWithFeedback(
                            context,
                            () => controller.updateSetting(
                              field: 'detectUnusualLogin',
                              value: value,
                            ),
                            success: value ? '已开启异地登录检测' : '已关闭异地登录检测',
                          );
                        },
                      ),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('敏感操作邮件提醒'),
                        subtitle: const Text('修改密码、邮箱等操作发送通知。'),
                        value: settings.notifySensitiveActionEmail,
                        onChanged: (bool value) async {
                          await _runWithFeedback(
                            context,
                            () => controller.updateSetting(
                              field: 'notifySensitiveActionEmail',
                              value: value,
                            ),
                            success: value ? '已开启提醒' : '已关闭提醒',
                          );
                        },
                      ),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('订阅通知邮件'),
                        subtitle: const Text('接收产品更新和系统消息。'),
                        value: settings.subscribeNewsEmail,
                        onChanged: (bool value) async {
                          await _runWithFeedback(
                            context,
                            () => controller.updateSetting(
                              field: 'subscribeNewsEmail',
                              value: value,
                            ),
                            success: value ? '已更新邮件订阅' : '已关闭邮件订阅',
                          );
                        },
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: <Widget>[
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              initialValue: settings.preferredMfaMethod,
                              items: <DropdownMenuItem<String>>[
                                DropdownMenuItem<String>(
                                  value: 'totp',
                                  enabled: totp?.enabled == true,
                                  child: const Text('TOTP'),
                                ),
                                DropdownMenuItem<String>(
                                  value: 'passkey',
                                  enabled: controller.passkeys.isNotEmpty,
                                  child: const Text('Passkey'),
                                ),
                              ],
                              onChanged: !settings.mfaEnabled
                                  ? null
                                  : (String? value) async {
                                      if (value == null) {
                                        return;
                                      }
                                      await _runWithFeedback(
                                        context,
                                        () => controller.updateSetting(
                                          field: 'preferredMfaMethod',
                                          stringValue: value,
                                        ),
                                        success: '登录 MFA 偏好已更新',
                                      );
                                    },
                              decoration: const InputDecoration(
                                labelText: '登录 MFA 偏好',
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: DropdownButtonFormField<String>(
                              initialValue: settings.preferredSensitiveMethod,
                              items: <DropdownMenuItem<String>>[
                                const DropdownMenuItem<String>(
                                  value: 'password',
                                  child: Text('密码'),
                                ),
                                const DropdownMenuItem<String>(
                                  value: 'email-code',
                                  child: Text('邮箱验证码'),
                                ),
                                DropdownMenuItem<String>(
                                  value: 'passkey',
                                  enabled: controller.passkeys.isNotEmpty,
                                  child: const Text('Passkey'),
                                ),
                                DropdownMenuItem<String>(
                                  value: 'totp',
                                  enabled: totp?.enabled == true,
                                  child: const Text('TOTP'),
                                ),
                              ],
                              onChanged: (String? value) async {
                                if (value == null) {
                                  return;
                                }
                                await _runWithFeedback(
                                  context,
                                  () => controller.updateSetting(
                                    field: 'preferredSensitiveMethod',
                                    stringValue: value,
                                  ),
                                  success: '敏感操作验证偏好已更新',
                                );
                              },
                              decoration: const InputDecoration(
                                labelText: '敏感操作验证偏好',
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                flex: 2,
                child: _SectionCard(
                  title: '身份验证能力',
                  subtitle: '配置验证方式',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      _CapabilityLine(
                        icon: Icons.key_rounded,
                        title: '密码登录',
                        trailing: const Text('已启用'),
                      ),
                      _CapabilityLine(
                        icon: Icons.mark_email_read_rounded,
                        title: '邮箱验证码登录',
                        trailing: const Text('已启用'),
                      ),
                      _CapabilityLine(
                        icon: Icons.fingerprint_rounded,
                        title: 'Passkey',
                        trailing: Text(
                          controller.passkeys.isEmpty
                              ? '未配置'
                              : '${controller.passkeys.length} 个',
                        ),
                      ),
                      _CapabilityLine(
                        icon: Icons.security_rounded,
                        title: 'TOTP',
                        trailing: Text(totp?.enabled == true ? '已启用' : '未启用'),
                      ),
                      const Divider(height: 32),
                      FilledButton.tonalIcon(
                        onPressed: () => _runWithSensitiveVerification(
                          context,
                          controller: controller,
                          action: () =>
                              _showChangeEmailDialog(context, controller),
                        ),
                        icon: const Icon(Icons.alternate_email_rounded),
                        label: const Text('修改邮箱'),
                      ),
                      const SizedBox(height: 10),
                      FilledButton.tonalIcon(
                        onPressed: () => _runWithSensitiveVerification(
                          context,
                          controller: controller,
                          action: () =>
                              _showChangePasswordDialog(context, controller),
                        ),
                        icon: const Icon(Icons.password_rounded),
                        label: const Text('修改密码'),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: _SectionCard(
                  title: 'TOTP 管理',
                  subtitle: '在这里管理你的一次性验证码',
                  child: _TotpPanel(controller: controller),
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: _SectionCard(
                  title: 'Passkey 列表',
                  subtitle: '当前该功能使用新增能力改为浏览器桥接完成',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Align(
                        alignment: Alignment.centerLeft,
                        child: FilledButton.tonalIcon(
                          onPressed: () async {
                            try {
                              await controller.registerPasskeyInBrowser(
                                preferredName:
                                    'Ksuser Desktop (${Platform.localHostname})',
                              );
                              if (context.mounted) {
                                showAppMessage(context, 'Passkey 已添加');
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            }
                          },
                          icon: const Icon(Icons.add_link_rounded),
                          label: const Text('新增 Passkey'),
                        ),
                      ),
                      const SizedBox(height: 12),
                      if (controller.passkeys.isEmpty)
                        const Text('当前没有已登记的 Passkey。')
                      else
                        Column(
                          children: controller.passkeys.map((
                            PasskeyListItem item,
                          ) {
                            return _PasskeyRow(
                              controller: controller,
                              item: item,
                            );
                          }).toList(),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class DevicesPage extends StatelessWidget {
  const DevicesPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '在线会话',
            subtitle: '管理您的登录与在线信息',
            child: controller.sessions.isEmpty
                ? const Text('暂无在线设备。')
                : Column(
                    children: controller.sessions.map((SessionItem item) {
                      return _SessionRow(
                        item: item,
                        compact: false,
                        controller: controller,
                      );
                    }).toList(),
                  ),
          ),
          const SizedBox(height: 16),
          _SectionCard(
            title: '危险操作',
            subtitle: '对应网页端的“退出所有设备”。',
            child: Row(
              children: <Widget>[
                const Expanded(
                  child: Text('这会撤销所有会话，包括当前设备。AccessToken 失效后需要重新登录。'),
                ),
                const SizedBox(width: 12),
                FilledButton.tonalIcon(
                  style: FilledButton.styleFrom(
                    backgroundColor: Colors.red.shade50,
                  ),
                  onPressed: () async {
                    final bool confirmed =
                        await showDialog<bool>(
                          context: context,
                          builder: (BuildContext context) {
                            return AlertDialog(
                              title: const Text('确认退出所有设备'),
                              content: const Text('当前桌面会话也会立即失效。'),
                              actions: <Widget>[
                                TextButton(
                                  onPressed: () =>
                                      Navigator.of(context).pop(false),
                                  child: const Text('取消'),
                                ),
                                FilledButton(
                                  onPressed: () =>
                                      Navigator.of(context).pop(true),
                                  child: const Text('确认退出'),
                                ),
                              ],
                            );
                          },
                        ) ??
                        false;
                    if (!confirmed || !context.mounted) {
                      return;
                    }
                    await _runWithFeedback(
                      context,
                      controller.logoutAll,
                      success: '已退出所有设备',
                    );
                  },
                  icon: const Icon(Icons.logout_rounded, color: Colors.red),
                  label: const Text(
                    '退出所有设备',
                    style: TextStyle(color: Colors.red),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class ActivityPage extends StatefulWidget {
  const ActivityPage({super.key, required this.controller});

  final AppController controller;

  @override
  State<ActivityPage> createState() => _ActivityPageState();
}

class _ActivityPageState extends State<ActivityPage> {
  String? _operationType;
  String? _result;
  bool _loading = false;

  Future<void> _loadLogs() async {
    setState(() {
      _loading = true;
    });
    try {
      await widget.controller.refreshSensitiveLogs(
        query: SensitiveLogsQuery(
          operationType: _operationType,
          result: _result,
          pageSize: 20,
        ),
      );
    } catch (error) {
      if (mounted) {
        showAppMessage(context, error.toString(), error: true);
      }
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '敏感操作日志',
            subtitle: '在这里查看你的敏感操作记录',
            child: Column(
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        initialValue: _operationType,
                        decoration: const InputDecoration(labelText: '操作类型'),
                        items: const <DropdownMenuItem<String>>[
                          DropdownMenuItem<String>(
                            value: 'LOGIN',
                            child: Text('登录'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'REGISTER',
                            child: Text('注册'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'CHANGE_PASSWORD',
                            child: Text('修改密码'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'CHANGE_EMAIL',
                            child: Text('修改邮箱'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'ENABLE_TOTP',
                            child: Text('启用 TOTP'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'DISABLE_TOTP',
                            child: Text('禁用 TOTP'),
                          ),
                        ],
                        onChanged: (String? value) {
                          setState(() {
                            _operationType = value;
                          });
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<String>(
                        initialValue: _result,
                        decoration: const InputDecoration(labelText: '执行结果'),
                        items: const <DropdownMenuItem<String>>[
                          DropdownMenuItem<String>(
                            value: 'SUCCESS',
                            child: Text('成功'),
                          ),
                          DropdownMenuItem<String>(
                            value: 'FAILURE',
                            child: Text('失败'),
                          ),
                        ],
                        onChanged: (String? value) {
                          setState(() {
                            _result = value;
                          });
                        },
                      ),
                    ),
                    const SizedBox(width: 12),
                    FilledButton.icon(
                      onPressed: _loading ? null : _loadLogs,
                      icon: _loading
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.filter_alt_rounded),
                      label: const Text('应用过滤'),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                if (widget.controller.sensitiveLogs.isEmpty)
                  const Text('暂无可展示的敏感日志。')
                else
                  Column(
                    children: widget.controller.sensitiveLogs
                        .map(
                          (SensitiveLogItem item) =>
                              _LogTile(log: item, compact: false),
                        )
                        .toList(),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _PasswordRequirementCard extends StatelessWidget {
  const _PasswordRequirementCard({required this.requirement});

  final PasswordRequirement? requirement;

  @override
  Widget build(BuildContext context) {
    if (requirement == null) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xFFFBFAF5),
          borderRadius: BorderRadius.circular(18),
        ),
        child: const Text('当前无法获取密码规则，提交时将直接依赖后端校验。'),
      );
    }
    final PasswordRequirement activeRequirement = requirement!;

    final List<String> rules = <String>[
      '长度 ${activeRequirement.minLength}-${activeRequirement.maxLength} 位',
      if (activeRequirement.requireUppercase) '至少 1 个大写字母',
      if (activeRequirement.requireLowercase) '至少 1 个小写字母',
      if (activeRequirement.requireDigits) '至少 1 个数字',
      if (activeRequirement.requireSpecialChars) '至少 1 个特殊字符',
      if (activeRequirement.rejectCommonWeakPasswords) '禁止常见弱密码',
    ];

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFFBFAF5),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          const Text('密码要求', style: TextStyle(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text(activeRequirement.requirementMessage),
          const SizedBox(height: 10),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: rules
                .map(
                  (String item) => Chip(
                    avatar: const Icon(Icons.done_rounded, size: 16),
                    label: Text(item),
                  ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _DisabledCapabilityCard extends StatelessWidget {
  const _DisabledCapabilityCard({
    required this.title,
    required this.description,
    required this.icon,
  });

  final String title;
  final String description;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF8F7F2),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.black.withValues(alpha: 0.06)),
      ),
      child: Row(
        children: <Widget>[
          Icon(icon, color: kPrimaryColor),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  title,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                Text(description),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MfaPanel extends StatelessWidget {
  const _MfaPanel({
    required this.challenge,
    required this.mode,
    required this.codeController,
    required this.recoveryController,
    required this.busy,
    required this.passkeyAvailable,
    required this.onModeChanged,
    required this.onSubmit,
  });

  final MFAChallenge challenge;
  final MfaMode mode;
  final TextEditingController codeController;
  final TextEditingController recoveryController;
  final bool busy;
  final bool passkeyAvailable;
  final ValueChanged<MfaMode> onModeChanged;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final bool supportsPasskey = challenge.methods.contains('passkey');
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: kSurfaceTint,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          const Text(
            '二次验证',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text('当前挑战：${challenge.methods.join(' / ')}'),
          const SizedBox(height: 14),
          SegmentedButton<MfaMode>(
            segments: <ButtonSegment<MfaMode>>[
              const ButtonSegment<MfaMode>(
                value: MfaMode.code,
                label: Text('动态码'),
              ),
              const ButtonSegment<MfaMode>(
                value: MfaMode.recoveryCode,
                label: Text('恢复码'),
              ),
              if (supportsPasskey)
                ButtonSegment<MfaMode>(
                  value: MfaMode.passkey,
                  enabled: passkeyAvailable,
                  label: Text(passkeyAvailable ? 'Passkey' : 'Passkey 不可用'),
                ),
            ],
            selected: <MfaMode>{mode},
            onSelectionChanged: (Set<MfaMode> selection) =>
                onModeChanged(selection.first),
          ),
          const SizedBox(height: 14),
          if (mode == MfaMode.code)
            TextField(
              controller: codeController,
              decoration: const InputDecoration(labelText: '6 位动态码'),
            )
          else if (mode == MfaMode.recoveryCode)
            TextField(
              controller: recoveryController,
              decoration: const InputDecoration(labelText: '恢复码'),
            )
          else
            _DisabledCapabilityCard(
              title: passkeyAvailable ? '使用 Passkey 完成二次验证' : 'Passkey 当前不可用',
              description: passkeyAvailable
                  ? '点击下方按钮后将自动跳转浏览器完成 WebAuthn，再回到桌面端继续。'
                  : '当前环境未配置可用的 Passkey 浏览器桥接地址。',
              icon: Icons.fingerprint_rounded,
            ),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed: busy ? null : onSubmit,
            icon: busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    mode == MfaMode.passkey
                        ? Icons.fingerprint_rounded
                        : Icons.verified_rounded,
                  ),
            label: Text(
              mode == MfaMode.passkey ? '使用 Passkey 验证' : '完成 MFA 验证',
            ),
          ),
        ],
      ),
    );
  }
}

class _WorkspaceBanner extends StatelessWidget {
  const _WorkspaceBanner({required this.user, required this.controller});

  final UserDetails user;
  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final int score = computeSecurityScore(user, controller);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: <Color>[Color(0xFFFFF4D2), Color(0xFFFFE5A0)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        children: <Widget>[
          _UserAvatar(
            imageUrl: user.avatarUrl,
            username: user.username,
            radius: 28,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  user.username,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(user.email),
                const SizedBox(height: 8),
                Text(
                  '当前安全评分 $score%，已同步 ${controller.sessions.length} 台设备和 ${controller.sensitiveLogs.length} 条日志。',
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _UserAvatar extends StatelessWidget {
  const _UserAvatar({
    required this.imageUrl,
    required this.username,
    this.radius = 20,
  });

  final String? imageUrl;
  final String username;
  final double radius;

  @override
  Widget build(BuildContext context) {
    final String normalizedName = username.trim();
    final String initial = normalizedName.isEmpty
        ? 'K'
        : normalizedName.substring(0, 1).toUpperCase();

    final String? normalizedUrl = imageUrl?.trim();
    final bool hasRemoteAvatar =
        normalizedUrl != null &&
        normalizedUrl.isNotEmpty &&
        (normalizedUrl.startsWith('http://') ||
            normalizedUrl.startsWith('https://'));

    final Widget fallback = CircleAvatar(
      radius: radius,
      backgroundColor: Colors.black,
      child: Text(
        initial,
        style: TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.bold,
          fontSize: radius * 0.72,
        ),
      ),
    );

    if (!hasRemoteAvatar) {
      return fallback;
    }

    final String remoteUrl = normalizedUrl;

    return ClipOval(
      child: Image.network(
        remoteUrl,
        width: radius * 2,
        height: radius * 2,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => fallback,
        loadingBuilder:
            (
              BuildContext context,
              Widget child,
              ImageChunkEvent? loadingProgress,
            ) {
              if (loadingProgress == null) {
                return child;
              }
              return fallback;
            },
      ),
    );
  }
}

class _MetricCard extends StatelessWidget {
  const _MetricCard({
    required this.title,
    required this.value,
    required this.caption,
    required this.icon,
  });

  final String title;
  final String value;
  final String caption;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 260,
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: const Color(0xFFFBFAF5),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: Colors.black.withValues(alpha: 0.05)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(icon, color: kPrimaryColor),
            const SizedBox(height: 16),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 4),
            Text(
              value,
              style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 6),
            Text(caption),
          ],
        ),
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.subtitle,
    required this.child,
  });

  final String title;
  final String subtitle;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: const Color(0xFFFBFAF5),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: Colors.black.withValues(alpha: 0.05)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(subtitle),
          const SizedBox(height: 18),
          child,
        ],
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minWidth: 220),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(label, style: const TextStyle(color: Colors.black54)),
          const SizedBox(height: 6),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}

class _EditableRow extends StatelessWidget {
  const _EditableRow({
    required this.label,
    required this.value,
    required this.onEdit,
  });

  final String label;
  final String value;
  final VoidCallback? onEdit;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(label, style: const TextStyle(color: Colors.black54)),
                  const SizedBox(height: 6),
                  Text(
                    value,
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
            if (onEdit != null)
              TextButton.icon(
                onPressed: onEdit,
                icon: const Icon(Icons.edit_outlined),
                label: const Text('编辑'),
              ),
          ],
        ),
      ),
    );
  }
}

class _CapabilityLine extends StatelessWidget {
  const _CapabilityLine({
    required this.icon,
    required this.title,
    required this.trailing,
  });

  final IconData icon;
  final String title;
  final Widget trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: <Widget>[
          Icon(icon, color: kPrimaryColor),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.w700),
            ),
          ),
          trailing,
        ],
      ),
    );
  }
}

class _TotpPanel extends StatelessWidget {
  const _TotpPanel({required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final TotpStatusResponse? status = controller.totpStatus;
    final bool enabled = status?.enabled == true;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: enabled ? const Color(0xFFEFFAF1) : Colors.white,
            borderRadius: BorderRadius.circular(18),
          ),
          child: Row(
            children: <Widget>[
              Icon(
                enabled ? Icons.verified_user_rounded : Icons.shield_outlined,
                color: kPrimaryColor,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      enabled ? 'TOTP 已启用' : 'TOTP 尚未启用',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      enabled
                          ? '恢复码剩余 ${status?.recoveryCodesCount ?? 0} 个'
                          : '可通过二维码或密钥完成注册。',
                    ),
                  ],
                ),
              ),
              FilledButton.tonal(
                onPressed: () async {
                  if (!enabled) {
                    await _runWithSensitiveVerification(
                      context,
                      controller: controller,
                      action: () => _showEnableTotpDialog(context, controller),
                    );
                    return;
                  }
                  await _runWithSensitiveVerification(
                    context,
                    controller: controller,
                    action: () => _runWithFeedback(
                      context,
                      controller.disableTotp,
                      success: 'TOTP 已关闭',
                    ),
                  );
                },
                child: Text(enabled ? '禁用' : '启用'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: <Widget>[
            OutlinedButton.icon(
              onPressed: enabled
                  ? () async {
                      await _runWithSensitiveVerification(
                        context,
                        controller: controller,
                        action: () async {
                          final List<String> codes = await controller
                              .getRecoveryCodes();
                          if (context.mounted) {
                            await _showRecoveryCodesDialog(
                              context,
                              '恢复码列表',
                              codes,
                            );
                          }
                        },
                      );
                    }
                  : null,
              icon: const Icon(Icons.visibility_outlined),
              label: const Text('查看恢复码'),
            ),
            OutlinedButton.icon(
              onPressed: enabled
                  ? () async {
                      await _runWithSensitiveVerification(
                        context,
                        controller: controller,
                        action: () async {
                          final List<String> codes = await controller
                              .regenerateRecoveryCodes();
                          if (context.mounted) {
                            await _showRecoveryCodesDialog(
                              context,
                              '新恢复码',
                              codes,
                            );
                          }
                        },
                      );
                    }
                  : null,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('重新生成恢复码'),
            ),
          ],
        ),
      ],
    );
  }
}

class _PasskeyRow extends StatelessWidget {
  const _PasskeyRow({required this.controller, required this.item});

  final AppController controller;
  final PasskeyListItem item;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        children: <Widget>[
          const Icon(Icons.fingerprint_rounded, color: kPrimaryColor),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  item.name,
                  style: const TextStyle(fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 4),
                Text('创建于 ${formatDateTime(item.createdAt)}'),
                Text(
                  item.lastUsedAt == null
                      ? '从未使用'
                      : '最近使用 ${formatDateTime(item.lastUsedAt)}',
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: () => _showEditDialog(
              context,
              title: '重命名 Passkey',
              initialValue: item.name,
              onSubmit: (String value) =>
                  controller.renamePasskey(item.id, value),
            ),
            child: const Text('重命名'),
          ),
          TextButton(
            onPressed: () async {
              final bool confirmed =
                  await showDialog<bool>(
                    context: context,
                    builder: (BuildContext context) {
                      return AlertDialog(
                        title: const Text('删除 Passkey'),
                        content: Text('确认删除 ${item.name}？'),
                        actions: <Widget>[
                          TextButton(
                            onPressed: () => Navigator.of(context).pop(false),
                            child: const Text('取消'),
                          ),
                          FilledButton(
                            onPressed: () => Navigator.of(context).pop(true),
                            child: const Text('删除'),
                          ),
                        ],
                      );
                    },
                  ) ??
                  false;
              if (!confirmed || !context.mounted) {
                return;
              }
              await _runWithFeedback(
                context,
                () => controller.deletePasskey(item.id),
                success: 'Passkey 已删除',
              );
            },
            child: const Text('删除'),
          ),
        ],
      ),
    );
  }
}

class _SessionRow extends StatelessWidget {
  const _SessionRow({
    required this.item,
    required this.compact,
    this.controller,
  });

  final SessionItem item;
  final bool compact;
  final AppController? controller;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: const Color(0xFFF3EFE3),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(deviceIcon(item.deviceType), color: kPrimaryColor),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        '${item.browser ?? '未知浏览器'} · ${item.deviceType ?? '未知设备'}',
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    if (item.current)
                      const Chip(label: Text('当前'))
                    else if (item.online)
                      const Chip(label: Text('在线')),
                  ],
                ),
                const SizedBox(height: 4),
                Text('${item.ipLocation ?? '未知位置'} · ${item.ipAddress}'),
                const SizedBox(height: 4),
                Text('最后活跃 ${formatRelativeTime(item.lastSeenAt)}'),
              ],
            ),
          ),
          if (!compact && controller != null && !item.current)
            TextButton(
              onPressed: () async {
                await _runWithFeedback(
                  context,
                  () => controller!.revokeSession(item.id),
                  success: '会话已撤销',
                );
              },
              child: const Text('撤销'),
            ),
        ],
      ),
    );
  }
}

class _LogTile extends StatelessWidget {
  const _LogTile({required this.log, required this.compact});

  final SensitiveLogItem log;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final Color statusColor = log.result == 'SUCCESS'
        ? Colors.green
        : Colors.red;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(
              log.result == 'SUCCESS'
                  ? Icons.task_alt_rounded
                  : Icons.error_outline_rounded,
              color: statusColor,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        operationLabel(log.operationType),
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ),
                    Chip(label: Text(loginMethodLabel(log.loginMethod))),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  '${log.result == 'SUCCESS' ? '成功' : '失败'} · ${formatDateTime(log.createdAt)}',
                ),
                if (!compact) ...<Widget>[
                  const SizedBox(height: 4),
                  Text(
                    '${log.ipLocation ?? '未知位置'} · ${log.ipAddress} · 风险 ${log.riskScore}',
                  ),
                  if (log.failureReason != null &&
                      log.failureReason!.isNotEmpty)
                    Text(
                      log.failureReason!,
                      style: const TextStyle(color: Colors.red),
                    ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SelectionOption {
  const _SelectionOption({required this.value, required this.label});

  final String value;
  final String label;
}

Future<void> _runWithSensitiveVerification(
  BuildContext context, {
  required AppController controller,
  required Future<void> Function() action,
}) async {
  Future<void> runAction() async {
    try {
      await action();
    } on ApiException catch (error) {
      if (error.statusCode == 403 &&
          error.message == '请先完成敏感操作验证' &&
          context.mounted) {
        final bool verified = await _showSensitiveVerificationDialog(
          context,
          controller: controller,
        );
        if (verified && context.mounted) {
          await action();
        }
        return;
      }
      rethrow;
    }
  }

  try {
    final SensitiveVerificationStatus status = await controller
        .checkSensitiveVerification();
    if (status.verified) {
      await runAction();
      return;
    }
    if (!context.mounted) {
      return;
    }
    showAppMessage(context, '需要先完成身份验证');
    final bool verified = await _showSensitiveVerificationDialog(
      context,
      controller: controller,
      initialStatus: status,
    );
    if (verified && context.mounted) {
      await runAction();
    }
  } catch (_) {
    if (!context.mounted) {
      return;
    }
    showAppMessage(context, '需要先完成身份验证');
    final bool verified = await _showSensitiveVerificationDialog(
      context,
      controller: controller,
    );
    if (verified && context.mounted) {
      await runAction();
    }
  }
}

Future<bool> _showSensitiveVerificationDialog(
  BuildContext context, {
  required AppController controller,
  SensitiveVerificationStatus? initialStatus,
}) async {
  final bool? verified = await showDialog<bool>(
    context: context,
    builder: (BuildContext context) {
      return _SensitiveVerificationDialog(
        controller: controller,
        initialStatus: initialStatus,
      );
    },
  );
  return verified ?? false;
}

class _SensitiveVerificationDialog extends StatefulWidget {
  const _SensitiveVerificationDialog({
    required this.controller,
    this.initialStatus,
  });

  final AppController controller;
  final SensitiveVerificationStatus? initialStatus;

  @override
  State<_SensitiveVerificationDialog> createState() =>
      _SensitiveVerificationDialogState();
}

class _SensitiveVerificationDialogState
    extends State<_SensitiveVerificationDialog> {
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _codeController = TextEditingController();
  final TextEditingController _totpController = TextEditingController();

  Timer? _countdownTimer;
  List<SensitiveVerificationMethod> _methods =
      const <SensitiveVerificationMethod>[
        SensitiveVerificationMethod.password,
        SensitiveVerificationMethod.emailCode,
        SensitiveVerificationMethod.totp,
      ];
  SensitiveVerificationMethod _selectedMethod =
      SensitiveVerificationMethod.password;
  bool _loading = true;
  bool _sendingCode = false;
  bool _verifying = false;
  int _codeCountdown = 0;
  bool _browserPasskeyAvailable = false;

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _passwordController.dispose();
    _codeController.dispose();
    _totpController.dispose();
    super.dispose();
  }

  Future<void> _loadStatus() async {
    try {
      final bool browserPasskeyAvailable = BrowserPasskeyBridge.isSupported(
        widget.controller.passkeyOrigin,
      );
      final SensitiveVerificationStatus status =
          widget.initialStatus ??
          await widget.controller.checkSensitiveVerification();
      if (status.verified) {
        if (mounted) {
          Navigator.of(context).pop(true);
        }
        return;
      }
      final List<SensitiveVerificationMethod> methods =
          status.methods.length <= 1
          ? const <SensitiveVerificationMethod>[
              SensitiveVerificationMethod.password,
              SensitiveVerificationMethod.emailCode,
              SensitiveVerificationMethod.totp,
              SensitiveVerificationMethod.passkey,
            ]
          : status.methods;
      final List<SensitiveVerificationMethod> preferredOrder =
          <SensitiveVerificationMethod>[
            ...<SensitiveVerificationMethod?>[
              status.preferredMethod,
            ].whereType<SensitiveVerificationMethod>(),
            ...methods,
          ];
      final SensitiveVerificationMethod selected = preferredOrder.firstWhere(
        (SensitiveVerificationMethod method) =>
            method != SensitiveVerificationMethod.passkey ||
            browserPasskeyAvailable,
        orElse: () => preferredOrder.first,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _methods = methods;
        _selectedMethod = selected;
        _browserPasskeyAvailable = browserPasskeyAvailable;
        _loading = false;
      });
      if (selected == SensitiveVerificationMethod.emailCode) {
        await _sendCode();
      }
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _browserPasskeyAvailable = false;
        _loading = false;
      });
    }
  }

  Future<void> _selectMethod(SensitiveVerificationMethod method) async {
    if (_selectedMethod == method || _verifying || _sendingCode) {
      return;
    }
    setState(() {
      _selectedMethod = method;
    });
    if (method == SensitiveVerificationMethod.emailCode &&
        _codeCountdown == 0) {
      await _sendCode();
    }
  }

  Future<void> _sendCode() async {
    if (_sendingCode) {
      return;
    }
    setState(() {
      _sendingCode = true;
    });
    try {
      await widget.controller.sendSensitiveVerificationCode();
      _startCountdown();
      if (mounted) {
        showAppMessage(context, '验证码已发送');
      }
    } catch (error) {
      if (mounted) {
        showAppMessage(context, error.toString(), error: true);
      }
    } finally {
      if (mounted) {
        setState(() {
          _sendingCode = false;
        });
      }
    }
  }

  void _startCountdown() {
    _countdownTimer?.cancel();
    setState(() {
      _codeCountdown = 60;
    });
    _countdownTimer = Timer.periodic(const Duration(seconds: 1), (Timer timer) {
      if (!mounted || _codeCountdown <= 1) {
        timer.cancel();
        if (mounted) {
          setState(() {
            _codeCountdown = 0;
          });
        }
        return;
      }
      setState(() {
        _codeCountdown -= 1;
      });
    });
  }

  Future<void> _verify() async {
    String? message;
    if (_selectedMethod == SensitiveVerificationMethod.password &&
        _passwordController.text.trim().isEmpty) {
      message = '请输入登录密码';
    } else if (_selectedMethod == SensitiveVerificationMethod.emailCode &&
        _codeController.text.trim().length != 6) {
      message = '请输入 6 位邮箱验证码';
    } else if (_selectedMethod == SensitiveVerificationMethod.totp &&
        _totpController.text.trim().length != 6) {
      message = '请输入 6 位动态码';
    } else if (_selectedMethod == SensitiveVerificationMethod.passkey &&
        !_browserPasskeyAvailable) {
      message = '当前环境未配置可用的 Passkey 浏览器桥接';
    }
    if (message != null) {
      showAppMessage(context, message, error: true);
      return;
    }

    setState(() {
      _verifying = true;
    });
    try {
      switch (_selectedMethod) {
        case SensitiveVerificationMethod.password:
          await widget.controller.verifySensitiveOperation(
            method: sensitiveVerificationMethodValue(_selectedMethod),
            password: _passwordController.text,
          );
          break;
        case SensitiveVerificationMethod.emailCode:
          await widget.controller.verifySensitiveOperation(
            method: sensitiveVerificationMethodValue(_selectedMethod),
            code: _codeController.text,
          );
          break;
        case SensitiveVerificationMethod.totp:
          await widget.controller.verifySensitiveOperation(
            method: sensitiveVerificationMethodValue(_selectedMethod),
            code: _totpController.text,
          );
          break;
        case SensitiveVerificationMethod.passkey:
          if (mounted) {
            showAppMessage(context, '即将打开浏览器完成 Passkey 验证');
          }
          await widget.controller
              .verifySensitiveOperationWithPasskeyInBrowser();
          break;
      }
      if (mounted) {
        showAppMessage(context, '验证成功');
        Navigator.of(context).pop(true);
      }
    } catch (error) {
      if (mounted) {
        showAppMessage(context, error.toString(), error: true);
      }
    } finally {
      if (mounted) {
        setState(() {
          _verifying = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('敏感操作验证'),
      content: SizedBox(
        width: 560,
        child: _loading
            ? const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: CircularProgressIndicator()),
              )
            : SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    const Text('请先验证身份后继续执行当前操作。'),
                    const SizedBox(height: 16),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: _methods.map((
                        SensitiveVerificationMethod method,
                      ) {
                        final bool supported =
                            method != SensitiveVerificationMethod.passkey ||
                            _browserPasskeyAvailable;
                        return ChoiceChip(
                          label: Text(
                            sensitiveVerificationMethodChipLabel(method),
                          ),
                          selected: _selectedMethod == method,
                          onSelected: supported
                              ? (_) => _selectMethod(method)
                              : null,
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      sensitiveVerificationMethodDescription(_selectedMethod),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                    const SizedBox(height: 16),
                    if (_selectedMethod == SensitiveVerificationMethod.password)
                      TextField(
                        controller: _passwordController,
                        obscureText: true,
                        decoration: const InputDecoration(labelText: '登录密码'),
                      )
                    else if (_selectedMethod ==
                        SensitiveVerificationMethod.emailCode)
                      Row(
                        children: <Widget>[
                          Expanded(
                            child: TextField(
                              controller: _codeController,
                              keyboardType: TextInputType.number,
                              decoration: const InputDecoration(
                                labelText: '邮箱验证码',
                              ),
                              inputFormatters: <TextInputFormatter>[
                                FilteringTextInputFormatter.digitsOnly,
                                LengthLimitingTextInputFormatter(6),
                              ],
                            ),
                          ),
                          const SizedBox(width: 12),
                          OutlinedButton(
                            onPressed: _sendingCode || _codeCountdown > 0
                                ? null
                                : _sendCode,
                            child: Text(
                              _codeCountdown > 0
                                  ? '${_codeCountdown}s'
                                  : '发送验证码',
                            ),
                          ),
                        ],
                      )
                    else if (_selectedMethod ==
                        SensitiveVerificationMethod.totp)
                      TextField(
                        controller: _totpController,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(labelText: '6 位动态码'),
                        inputFormatters: <TextInputFormatter>[
                          FilteringTextInputFormatter.digitsOnly,
                          LengthLimitingTextInputFormatter(6),
                        ],
                      )
                    else
                      _DisabledCapabilityCard(
                        title: _browserPasskeyAvailable
                            ? 'Passkey 浏览器验证已接入'
                            : 'Passkey 当前不可用',
                        description: _browserPasskeyAvailable
                            ? '点击验证后会自动打开默认浏览器完成 WebAuthn，成功后当前弹窗会自动继续。'
                            : '当前环境未配置可用的 Passkey 浏览器桥接地址。',
                        icon: Icons.fingerprint_rounded,
                      ),
                  ],
                ),
              ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: _verifying ? null : () => Navigator.of(context).pop(false),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: _loading || _verifying ? null : _verify,
          child: _verifying
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('验证'),
        ),
      ],
    );
  }
}

Future<void> _showEditDialog(
  BuildContext context, {
  required String title,
  required String initialValue,
  required Future<void> Function(String value) onSubmit,
  String? hintText,
  int maxLines = 1,
}) async {
  final TextEditingController controller = TextEditingController(
    text: initialValue,
  );
  final bool? saved = await showDialog<bool>(
    context: context,
    builder: (BuildContext context) {
      bool busy = false;
      return StatefulBuilder(
        builder:
            (BuildContext context, void Function(void Function()) setState) {
              return AlertDialog(
                title: Text(title),
                content: TextField(
                  controller: controller,
                  maxLines: maxLines,
                  decoration: InputDecoration(hintText: hintText),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => Navigator.of(context).pop(false),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () async {
                            setState(() {
                              busy = true;
                            });
                            try {
                              await onSubmit(controller.text);
                              if (context.mounted) {
                                Navigator.of(context).pop(true);
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            } finally {
                              if (context.mounted) {
                                setState(() {
                                  busy = false;
                                });
                              }
                            }
                          },
                    child: const Text('保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  controller.dispose();
  if (saved == true && context.mounted) {
    showAppMessage(context, '已保存');
  }
}

Future<void> _showSelectionDialog(
  BuildContext context, {
  required String title,
  required String currentValue,
  required List<_SelectionOption> options,
  required Future<void> Function(String value) onSubmit,
}) async {
  String value = currentValue;
  final bool? saved = await showDialog<bool>(
    context: context,
    builder: (BuildContext context) {
      bool busy = false;
      return StatefulBuilder(
        builder:
            (BuildContext context, void Function(void Function()) setState) {
              return AlertDialog(
                title: Text(title),
                content: Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: options.map((_SelectionOption option) {
                    return ChoiceChip(
                      label: Text(option.label),
                      selected: value == option.value,
                      onSelected: (_) {
                        setState(() {
                          value = option.value;
                        });
                      },
                    );
                  }).toList(),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => Navigator.of(context).pop(false),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () async {
                            setState(() {
                              busy = true;
                            });
                            try {
                              await onSubmit(value);
                              if (context.mounted) {
                                Navigator.of(context).pop(true);
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            } finally {
                              if (context.mounted) {
                                setState(() {
                                  busy = false;
                                });
                              }
                            }
                          },
                    child: const Text('保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  if (saved == true && context.mounted) {
    showAppMessage(context, '已保存');
  }
}

Future<void> _showChangeEmailDialog(
  BuildContext context,
  AppController controller,
) async {
  final TextEditingController emailController = TextEditingController();
  final TextEditingController codeController = TextEditingController();
  await showDialog<void>(
    context: context,
    builder: (BuildContext context) {
      bool busy = false;
      return StatefulBuilder(
        builder:
            (BuildContext context, void Function(void Function()) setState) {
              return AlertDialog(
                title: const Text('修改邮箱'),
                content: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    TextField(
                      controller: emailController,
                      decoration: const InputDecoration(labelText: '新邮箱'),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: TextField(
                            controller: codeController,
                            decoration: const InputDecoration(labelText: '验证码'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        OutlinedButton(
                          onPressed: busy
                              ? null
                              : () async {
                                  try {
                                    await controller.sendChangeEmailCode(
                                      emailController.text,
                                    );
                                    if (context.mounted) {
                                      showAppMessage(context, '验证码已发送');
                                    }
                                  } catch (error) {
                                    if (context.mounted) {
                                      showAppMessage(
                                        context,
                                        error.toString(),
                                        error: true,
                                      );
                                    }
                                  }
                                },
                          child: const Text('发送验证码'),
                        ),
                      ],
                    ),
                  ],
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: busy ? null : () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () async {
                            setState(() {
                              busy = true;
                            });
                            try {
                              await controller.changeEmail(
                                newEmail: emailController.text,
                                code: codeController.text,
                              );
                              if (context.mounted) {
                                Navigator.of(context).pop();
                                showAppMessage(context, '邮箱已更新');
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            } finally {
                              if (context.mounted) {
                                setState(() {
                                  busy = false;
                                });
                              }
                            }
                          },
                    child: const Text('保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  emailController.dispose();
  codeController.dispose();
}

Future<void> _showChangePasswordDialog(
  BuildContext context,
  AppController controller,
) async {
  final TextEditingController passwordController = TextEditingController();
  await showDialog<void>(
    context: context,
    builder: (BuildContext context) {
      bool busy = false;
      return StatefulBuilder(
        builder:
            (BuildContext context, void Function(void Function()) setState) {
              return AlertDialog(
                title: const Text('修改密码'),
                content: TextField(
                  controller: passwordController,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: '新密码'),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: busy ? null : () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () async {
                            setState(() {
                              busy = true;
                            });
                            try {
                              await controller.changePassword(
                                passwordController.text,
                              );
                              if (context.mounted) {
                                Navigator.of(context).pop();
                                showAppMessage(context, '密码已更新');
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            } finally {
                              if (context.mounted) {
                                setState(() {
                                  busy = false;
                                });
                              }
                            }
                          },
                    child: const Text('保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  passwordController.dispose();
}

Future<void> _showEnableTotpDialog(
  BuildContext context,
  AppController controller,
) async {
  final NavigatorState navigator = Navigator.of(context);
  final ScaffoldMessengerState messenger = ScaffoldMessenger.of(context);
  final TotpRegistrationOptionsResponse options = await controller
      .getTotpRegistrationOptions();
  if (!navigator.context.mounted) {
    return;
  }
  final TextEditingController codeController = TextEditingController();
  await showDialog<void>(
    context: navigator.context,
    builder: (BuildContext context) {
      bool busy = false;
      return StatefulBuilder(
        builder:
            (BuildContext context, void Function(void Function()) setState) {
              return AlertDialog(
                title: const Text('启用 TOTP'),
                content: SizedBox(
                  width: 480,
                  child: SingleChildScrollView(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        const Text('扫描二维码或手动录入密钥，然后输入身份验证器生成的 6 位动态码。'),
                        const SizedBox(height: 16),
                        _buildQrWidget(options.qrCodeUrl),
                        const SizedBox(height: 16),
                        SelectableText(
                          options.secret,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: codeController,
                          decoration: const InputDecoration(labelText: '动态码'),
                        ),
                        const SizedBox(height: 16),
                        const Text('初始恢复码'),
                        const SizedBox(height: 8),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: options.recoveryCodes
                              .map((String item) => Chip(label: Text(item)))
                              .toList(),
                        ),
                      ],
                    ),
                  ),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: busy ? null : () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: busy
                        ? null
                        : () async {
                            setState(() {
                              busy = true;
                            });
                            try {
                              await controller.verifyTotpRegistration(
                                code: codeController.text,
                                recoveryCodes: options.recoveryCodes,
                              );
                              if (navigator.context.mounted) {
                                Navigator.of(context).pop();
                                await _showRecoveryCodesDialog(
                                  navigator.context,
                                  '请保存恢复码',
                                  options.recoveryCodes,
                                );
                                showAppMessage(messenger.context, 'TOTP 已启用');
                              }
                            } catch (error) {
                              if (context.mounted) {
                                showAppMessage(
                                  messenger.context,
                                  error.toString(),
                                  error: true,
                                );
                              }
                            } finally {
                              if (context.mounted) {
                                setState(() {
                                  busy = false;
                                });
                              }
                            }
                          },
                    child: const Text('确认启用'),
                  ),
                ],
              );
            },
      );
    },
  );
  codeController.dispose();
}

Future<void> _showRecoveryCodesDialog(
  BuildContext context,
  String title,
  List<String> codes,
) {
  return showDialog<void>(
    context: context,
    builder: (BuildContext context) {
      return AlertDialog(
        title: Text(title),
        content: SizedBox(
          width: 420,
          child: Wrap(
            spacing: 10,
            runSpacing: 10,
            children: codes
                .map((String item) => Chip(label: Text(item)))
                .toList(),
          ),
        ),
        actions: <Widget>[
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('关闭'),
          ),
        ],
      );
    },
  );
}

Widget _buildQrWidget(String qrCodeUrl) {
  if (qrCodeUrl.startsWith('data:image')) {
    final String encoded = qrCodeUrl.split(',').last;
    final Uint8List bytes = base64Decode(encoded);
    return Center(child: Image.memory(bytes, width: 220, height: 220));
  }
  return Center(child: Image.network(qrCodeUrl, width: 220, height: 220));
}

Future<void> _runWithFeedback(
  BuildContext context,
  Future<void> Function() action, {
  required String success,
}) async {
  try {
    await action();
    if (context.mounted) {
      showAppMessage(context, success);
    }
  } catch (error) {
    if (context.mounted) {
      showAppMessage(context, error.toString(), error: true);
    }
  }
}

String sensitiveVerificationMethodValue(SensitiveVerificationMethod method) {
  switch (method) {
    case SensitiveVerificationMethod.password:
      return 'password';
    case SensitiveVerificationMethod.emailCode:
      return 'email-code';
    case SensitiveVerificationMethod.totp:
      return 'totp';
    case SensitiveVerificationMethod.passkey:
      return 'passkey';
  }
}

SensitiveVerificationMethod? parseSensitiveVerificationMethod(String? value) {
  switch (value) {
    case 'password':
      return SensitiveVerificationMethod.password;
    case 'email-code':
      return SensitiveVerificationMethod.emailCode;
    case 'totp':
      return SensitiveVerificationMethod.totp;
    case 'passkey':
      return SensitiveVerificationMethod.passkey;
    default:
      return null;
  }
}

String sensitiveVerificationMethodChipLabel(
  SensitiveVerificationMethod method,
) {
  switch (method) {
    case SensitiveVerificationMethod.password:
      return '密码';
    case SensitiveVerificationMethod.emailCode:
      return '邮箱验证码';
    case SensitiveVerificationMethod.totp:
      return 'TOTP';
    case SensitiveVerificationMethod.passkey:
      return 'Passkey';
  }
}

String sensitiveVerificationMethodDescription(
  SensitiveVerificationMethod method,
) {
  switch (method) {
    case SensitiveVerificationMethod.password:
      return '输入当前登录密码完成验证。';
    case SensitiveVerificationMethod.emailCode:
      return '向当前绑定邮箱发送一次性验证码。';
    case SensitiveVerificationMethod.totp:
      return '输入身份验证器生成的 6 位动态码。';
    case SensitiveVerificationMethod.passkey:
      return '将自动打开浏览器完成 WebAuthn，再把敏感验证结果回传到桌面端。';
  }
}

String sectionTitle(DesktopSection section) {
  switch (section) {
    case DesktopSection.overview:
      return '账户概览';
    case DesktopSection.profile:
      return '个人资料';
    case DesktopSection.security:
      return '安全与登录';
    case DesktopSection.devices:
      return '设备与会话';
    case DesktopSection.activity:
      return '敏感操作日志';
  }
}

String sectionSubtitle(DesktopSection section) {
  switch (section) {
    case DesktopSection.overview:
      return '查看桌面版汇总信息、登录方式与近期活动。';
    case DesktopSection.profile:
      return '管理用户资料与扩展字段。';
    case DesktopSection.security:
      return '配置 MFA、TOTP、Passkey 与敏感操作偏好。';
    case DesktopSection.devices:
      return '管理登录设备和在线会话。';
    case DesktopSection.activity:
      return '过滤查看敏感操作日志。';
  }
}

int computeSecurityScore(UserDetails user, AppController controller) {
  int score = 40;
  if (user.settings?.mfaEnabled == true) {
    score += 20;
  }
  if (controller.totpStatus?.enabled == true) {
    score += 20;
  }
  if (controller.passkeys.isNotEmpty) {
    score += 10;
  }
  if (user.settings?.detectUnusualLogin == true) {
    score += 5;
  }
  if (user.settings?.notifySensitiveActionEmail == true) {
    score += 5;
  }
  return score.clamp(0, 100);
}

IconData deviceIcon(String? deviceType) {
  final String value = (deviceType ?? '').toLowerCase();
  if (value.contains('android') ||
      value.contains('ios') ||
      value.contains('mobile')) {
    return Icons.smartphone_rounded;
  }
  if (value.contains('ipad') || value.contains('tablet')) {
    return Icons.tablet_mac_rounded;
  }
  return Icons.laptop_mac_rounded;
}

String displayGender(String? gender) {
  switch (gender) {
    case 'male':
      return '男';
    case 'female':
      return '女';
    default:
      return '保密';
  }
}

String operationLabel(String operationType) {
  const Map<String, String> labels = <String, String>{
    'REGISTER': '注册',
    'LOGIN': '登录',
    'SENSITIVE_VERIFY': '敏感操作验证',
    'CHANGE_PASSWORD': '修改密码',
    'CHANGE_EMAIL': '修改邮箱',
    'ADD_PASSKEY': '新增 Passkey',
    'DELETE_PASSKEY': '删除 Passkey',
    'ENABLE_TOTP': '启用 TOTP',
    'DISABLE_TOTP': '禁用 TOTP',
  };
  return labels[operationType] ?? operationType;
}

String loginMethodLabel(String? loginMethod) {
  const Map<String, String> labels = <String, String>{
    'PASSWORD': '密码登录',
    'PASSWORD_MFA': '密码 + 二步验证',
    'EMAIL_CODE': '验证码登录',
    'EMAIL_CODE_MFA': '验证码 + 二步验证',
    'PASSKEY': 'Passkey 登录',
    'PASSKEY_MFA': 'Passkey + 二步验证',
  };
  return labels[loginMethod] ?? (loginMethod ?? '通用操作');
}

String formatDateTime(String? value) {
  if (value == null || value.isEmpty) {
    return '—';
  }
  final DateTime? time = DateTime.tryParse(value)?.toLocal();
  if (time == null) {
    return value;
  }
  return '${time.year}-${twoDigits(time.month)}-${twoDigits(time.day)} ${twoDigits(time.hour)}:${twoDigits(time.minute)}';
}

String formatRelativeTime(String? value) {
  if (value == null || value.isEmpty) {
    return '—';
  }
  final DateTime? time = DateTime.tryParse(value)?.toLocal();
  if (time == null) {
    return value;
  }
  final Duration diff = DateTime.now().difference(time);
  if (diff.inMinutes < 1) {
    return '刚刚';
  }
  if (diff.inMinutes < 60) {
    return '${diff.inMinutes} 分钟前';
  }
  if (diff.inHours < 24) {
    return '${diff.inHours} 小时前';
  }
  if (diff.inDays < 7) {
    return '${diff.inDays} 天前';
  }
  return formatDateTime(value);
}

String twoDigits(int value) => value.toString().padLeft(2, '0');

Map<String, dynamic> asMap(dynamic value) {
  if (value is Map<String, dynamic>) {
    return value;
  }
  if (value is Map) {
    return value.map(
      (dynamic key, dynamic data) => MapEntry(key.toString(), data),
    );
  }
  throw ApiException('数据格式错误');
}

List<dynamic> asList(dynamic value) {
  if (value is List<dynamic>) {
    return value;
  }
  if (value is List) {
    return value.cast<dynamic>();
  }
  return <dynamic>[];
}

String? asString(dynamic value) {
  if (value == null) {
    return null;
  }
  return value.toString();
}

int? asInt(dynamic value) {
  if (value is int) {
    return value;
  }
  return int.tryParse(value?.toString() ?? '');
}

bool asBool(dynamic value, {bool fallback = false}) {
  if (value is bool) {
    return value;
  }
  if (value is String) {
    return value.toLowerCase() == 'true';
  }
  return fallback;
}

class MFAChallenge {
  const MFAChallenge({
    required this.challengeId,
    required this.method,
    required this.methods,
  });

  factory MFAChallenge.fromJson(Map<String, dynamic> json) {
    return MFAChallenge(
      challengeId: asString(json['challengeId']) ?? '',
      method: asString(json['method']) ?? 'totp',
      methods: asList(
        json['methods'],
      ).map((dynamic item) => item.toString()).toList(),
    );
  }

  final String challengeId;
  final String method;
  final List<String> methods;
}

class UserSettings {
  const UserSettings({
    this.mfaEnabled = false,
    this.detectUnusualLogin = false,
    this.notifySensitiveActionEmail = false,
    this.subscribeNewsEmail = false,
    this.preferredMfaMethod,
    this.preferredSensitiveMethod,
  });

  factory UserSettings.fromJson(Map<String, dynamic> json) {
    return UserSettings(
      mfaEnabled: asBool(json['mfaEnabled']),
      detectUnusualLogin: asBool(json['detectUnusualLogin']),
      notifySensitiveActionEmail: asBool(json['notifySensitiveActionEmail']),
      subscribeNewsEmail: asBool(json['subscribeNewsEmail']),
      preferredMfaMethod: asString(json['preferredMfaMethod']),
      preferredSensitiveMethod: asString(json['preferredSensitiveMethod']),
    );
  }

  final bool mfaEnabled;
  final bool detectUnusualLogin;
  final bool notifySensitiveActionEmail;
  final bool subscribeNewsEmail;
  final String? preferredMfaMethod;
  final String? preferredSensitiveMethod;
}

class UserDetails {
  const UserDetails({
    required this.uuid,
    required this.username,
    required this.email,
    this.avatarUrl,
    this.realName,
    this.gender,
    this.birthDate,
    this.region,
    this.bio,
    this.updatedAt,
    this.settings,
  });

  factory UserDetails.fromJson(Map<String, dynamic> json) {
    return UserDetails(
      uuid: asString(json['uuid']) ?? '',
      username: asString(json['username']) ?? '',
      email: asString(json['email']) ?? '',
      avatarUrl: asString(json['avatarUrl']),
      realName: asString(json['realName']),
      gender: asString(json['gender']),
      birthDate: asString(json['birthDate']),
      region: asString(json['region']),
      bio: asString(json['bio']),
      updatedAt: asString(json['updatedAt']),
      settings: json['settings'] == null
          ? null
          : UserSettings.fromJson(asMap(json['settings'])),
    );
  }

  final String uuid;
  final String username;
  final String email;
  final String? avatarUrl;
  final String? realName;
  final String? gender;
  final String? birthDate;
  final String? region;
  final String? bio;
  final String? updatedAt;
  final UserSettings? settings;

  UserDetails copyWith({
    String? uuid,
    String? username,
    String? email,
    String? avatarUrl,
    String? realName,
    String? gender,
    String? birthDate,
    String? region,
    String? bio,
    String? updatedAt,
    UserSettings? settings,
  }) {
    return UserDetails(
      uuid: uuid ?? this.uuid,
      username: username ?? this.username,
      email: email ?? this.email,
      avatarUrl: avatarUrl ?? this.avatarUrl,
      realName: realName ?? this.realName,
      gender: gender ?? this.gender,
      birthDate: birthDate ?? this.birthDate,
      region: region ?? this.region,
      bio: bio ?? this.bio,
      updatedAt: updatedAt ?? this.updatedAt,
      settings: settings ?? this.settings,
    );
  }
}

class PasswordRequirement {
  const PasswordRequirement({
    required this.minLength,
    required this.maxLength,
    required this.requireUppercase,
    required this.requireLowercase,
    required this.requireDigits,
    required this.requireSpecialChars,
    required this.rejectCommonWeakPasswords,
    required this.requirementMessage,
  });

  factory PasswordRequirement.fromJson(Map<String, dynamic> json) {
    return PasswordRequirement(
      minLength: asInt(json['minLength']) ?? 6,
      maxLength: asInt(json['maxLength']) ?? 66,
      requireUppercase: asBool(json['requireUppercase']),
      requireLowercase: asBool(json['requireLowercase']),
      requireDigits: asBool(json['requireDigits']),
      requireSpecialChars: asBool(json['requireSpecialChars']),
      rejectCommonWeakPasswords: asBool(json['rejectCommonWeakPasswords']),
      requirementMessage: asString(json['requirementMessage']) ?? '',
    );
  }

  final int minLength;
  final int maxLength;
  final bool requireUppercase;
  final bool requireLowercase;
  final bool requireDigits;
  final bool requireSpecialChars;
  final bool rejectCommonWeakPasswords;
  final String requirementMessage;

  String? validate(String password) {
    if (password.length < minLength || password.length > maxLength) {
      return '密码长度必须在 $minLength-$maxLength 个字符之间';
    }
    if (requireUppercase && !RegExp(r'[A-Z]').hasMatch(password)) {
      return '密码必须包含至少一个大写字母';
    }
    if (requireLowercase && !RegExp(r'[a-z]').hasMatch(password)) {
      return '密码必须包含至少一个小写字母';
    }
    if (requireDigits && !RegExp(r'[0-9]').hasMatch(password)) {
      return '密码必须包含至少一个数字';
    }
    if (requireSpecialChars && !RegExp(r'[^A-Za-z0-9]').hasMatch(password)) {
      return '密码必须包含至少一个特殊字符';
    }
    return null;
  }
}

class TotpStatusResponse {
  const TotpStatusResponse({
    required this.enabled,
    required this.recoveryCodesCount,
  });

  factory TotpStatusResponse.fromJson(Map<String, dynamic> json) {
    return TotpStatusResponse(
      enabled: asBool(json['enabled']),
      recoveryCodesCount: asInt(json['recoveryCodesCount']) ?? 0,
    );
  }

  final bool enabled;
  final int recoveryCodesCount;
}

class SensitiveVerificationStatus {
  const SensitiveVerificationStatus({
    required this.verified,
    required this.remainingSeconds,
    required this.methods,
    this.preferredMethod,
  });

  factory SensitiveVerificationStatus.fromJson(Map<String, dynamic> json) {
    return SensitiveVerificationStatus(
      verified: asBool(json['verified']),
      remainingSeconds: asInt(json['remainingSeconds']) ?? 0,
      preferredMethod: parseSensitiveVerificationMethod(
        asString(json['preferredMethod']),
      ),
      methods: asList(json['methods'])
          .map(
            (dynamic item) => parseSensitiveVerificationMethod(item.toString()),
          )
          .whereType<SensitiveVerificationMethod>()
          .toList(),
    );
  }

  final bool verified;
  final int remainingSeconds;
  final SensitiveVerificationMethod? preferredMethod;
  final List<SensitiveVerificationMethod> methods;
}

class PasskeyAllowedCredential {
  const PasskeyAllowedCredential({
    required this.id,
    this.type,
    this.transports = const <String>[],
  });

  factory PasskeyAllowedCredential.fromJson(Map<String, dynamic> json) {
    return PasskeyAllowedCredential(
      id: asString(json['id']) ?? '',
      type: asString(json['type']),
      transports: asList(
        json['transports'],
      ).map((dynamic item) => item.toString()).toList(),
    );
  }

  final String id;
  final String? type;
  final List<String> transports;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'id': id,
      if (type != null) 'type': type,
      if (transports.isNotEmpty) 'transports': transports,
    };
  }
}

class PasskeyAssertionOptions {
  const PasskeyAssertionOptions({
    required this.challengeId,
    required this.challenge,
    required this.timeout,
    required this.rpId,
    required this.userVerification,
    required this.allowedCredentials,
  });

  factory PasskeyAssertionOptions.fromJson(Map<String, dynamic> json) {
    final dynamic allowCredentialsRaw = json['allowCredentials'];
    List<dynamic> parsedCredentials = <dynamic>[];

    if (allowCredentialsRaw is String &&
        allowCredentialsRaw.trim().isNotEmpty) {
      try {
        final dynamic decoded = jsonDecode(allowCredentialsRaw);
        if (decoded is List<dynamic>) {
          parsedCredentials = decoded;
        }
      } catch (_) {
        parsedCredentials = <dynamic>[];
      }
    } else if (allowCredentialsRaw is List<dynamic>) {
      parsedCredentials = allowCredentialsRaw;
    }

    return PasskeyAssertionOptions(
      challengeId: asString(json['challengeId']) ?? '',
      challenge: asString(json['challenge']) ?? '',
      timeout: asString(json['timeout']) ?? '300000',
      rpId: asString(json['rpId']) ?? '',
      userVerification: asString(json['userVerification']) ?? 'preferred',
      allowedCredentials: parsedCredentials
          .map((dynamic item) => PasskeyAllowedCredential.fromJson(asMap(item)))
          .where((PasskeyAllowedCredential item) => item.id.isNotEmpty)
          .toList(),
    );
  }

  final String challengeId;
  final String challenge;
  final String timeout;
  final String rpId;
  final String userVerification;
  final List<PasskeyAllowedCredential> allowedCredentials;
}

class PasskeyAssertionResult {
  const PasskeyAssertionResult({
    required this.credentialRawId,
    required this.clientDataJSON,
    required this.authenticatorData,
    required this.signature,
  });

  factory PasskeyAssertionResult.fromChannelMap(Map<dynamic, dynamic> map) {
    return PasskeyAssertionResult(
      credentialRawId: map['credentialRawId']?.toString() ?? '',
      clientDataJSON: map['clientDataJSON']?.toString() ?? '',
      authenticatorData: map['authenticatorData']?.toString() ?? '',
      signature: map['signature']?.toString() ?? '',
    );
  }

  final String credentialRawId;
  final String clientDataJSON;
  final String authenticatorData;
  final String signature;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'credentialRawId': credentialRawId,
      'clientDataJSON': clientDataJSON,
      'authenticatorData': authenticatorData,
      'signature': signature,
    };
  }
}

class TotpRegistrationOptionsResponse {
  const TotpRegistrationOptionsResponse({
    required this.secret,
    required this.qrCodeUrl,
    required this.recoveryCodes,
  });

  factory TotpRegistrationOptionsResponse.fromJson(Map<String, dynamic> json) {
    return TotpRegistrationOptionsResponse(
      secret: asString(json['secret']) ?? '',
      qrCodeUrl: asString(json['qrCodeUrl']) ?? '',
      recoveryCodes: asList(
        json['recoveryCodes'],
      ).map((dynamic item) => item.toString()).toList(),
    );
  }

  final String secret;
  final String qrCodeUrl;
  final List<String> recoveryCodes;
}

class PasskeyListItem {
  const PasskeyListItem({
    required this.id,
    required this.name,
    required this.transports,
    required this.lastUsedAt,
    required this.createdAt,
  });

  factory PasskeyListItem.fromJson(Map<String, dynamic> json) {
    return PasskeyListItem(
      id: asInt(json['id']) ?? 0,
      name: asString(json['name']) ?? 'Unnamed passkey',
      transports: asString(json['transports']) ?? '',
      lastUsedAt: asString(json['lastUsedAt']),
      createdAt: asString(json['createdAt']) ?? '',
    );
  }

  final int id;
  final String name;
  final String transports;
  final String? lastUsedAt;
  final String createdAt;
}

class SessionItem {
  const SessionItem({
    required this.id,
    required this.ipAddress,
    required this.ipLocation,
    required this.userAgent,
    required this.browser,
    required this.deviceType,
    required this.createdAt,
    required this.lastSeenAt,
    required this.expiresAt,
    required this.revokedAt,
    required this.online,
    required this.current,
  });

  factory SessionItem.fromJson(Map<String, dynamic> json) {
    return SessionItem(
      id: asInt(json['id']) ?? 0,
      ipAddress: asString(json['ipAddress']) ?? '',
      ipLocation: asString(json['ipLocation']),
      userAgent: asString(json['userAgent']),
      browser: asString(json['browser']),
      deviceType: asString(json['deviceType']),
      createdAt: asString(json['createdAt']) ?? '',
      lastSeenAt: asString(json['lastSeenAt']) ?? '',
      expiresAt: asString(json['expiresAt']) ?? '',
      revokedAt: asString(json['revokedAt']),
      online: asBool(json['online']),
      current: asBool(json['current']),
    );
  }

  final int id;
  final String ipAddress;
  final String? ipLocation;
  final String? userAgent;
  final String? browser;
  final String? deviceType;
  final String createdAt;
  final String lastSeenAt;
  final String expiresAt;
  final String? revokedAt;
  final bool online;
  final bool current;
}

class SensitiveLogItem {
  const SensitiveLogItem({
    required this.id,
    required this.operationType,
    required this.loginMethod,
    required this.ipAddress,
    required this.ipLocation,
    required this.browser,
    required this.deviceType,
    required this.result,
    required this.failureReason,
    required this.riskScore,
    required this.createdAt,
  });

  factory SensitiveLogItem.fromJson(Map<String, dynamic> json) {
    return SensitiveLogItem(
      id: asInt(json['id']) ?? 0,
      operationType: asString(json['operationType']) ?? 'LOGIN',
      loginMethod: asString(json['loginMethod']),
      ipAddress: asString(json['ipAddress']) ?? '',
      ipLocation: asString(json['ipLocation']),
      browser: asString(json['browser']),
      deviceType: asString(json['deviceType']),
      result: asString(json['result']) ?? 'SUCCESS',
      failureReason: asString(json['failureReason']),
      riskScore: asInt(json['riskScore']) ?? 0,
      createdAt: asString(json['createdAt']) ?? '',
    );
  }

  final int id;
  final String operationType;
  final String? loginMethod;
  final String ipAddress;
  final String? ipLocation;
  final String? browser;
  final String? deviceType;
  final String result;
  final String? failureReason;
  final int riskScore;
  final String createdAt;
}

class SensitiveLogsQuery {
  const SensitiveLogsQuery({
    this.page,
    this.pageSize,
    this.operationType,
    this.result,
    this.startDate,
    this.endDate,
  });

  final int? page;
  final int? pageSize;
  final String? operationType;
  final String? result;
  final String? startDate;
  final String? endDate;

  Map<String, String> toQuery() {
    return <String, String>{
      if (page != null) 'page': '$page',
      if (pageSize != null) 'pageSize': '$pageSize',
      if (operationType != null && operationType!.isNotEmpty)
        'operationType': operationType!,
      if (result != null && result!.isNotEmpty) 'result': result!,
      if (startDate != null && startDate!.isNotEmpty) 'startDate': startDate!,
      if (endDate != null && endDate!.isNotEmpty) 'endDate': endDate!,
    };
  }
}

enum BrowserPasskeyBridgeMode { login, mfa, sensitive, register }

class BrowserPasskeyBridgeResponse {
  const BrowserPasskeyBridgeResponse({
    required this.success,
    required this.message,
    this.accessToken,
    this.verified = false,
    this.registered = false,
  });

  factory BrowserPasskeyBridgeResponse.fromJson(Map<String, dynamic> json) {
    return BrowserPasskeyBridgeResponse(
      success: asString(json['status']) != 'error',
      message: asString(json['message']),
      accessToken: asString(json['accessToken']),
      verified: asBool(json['verified']),
      registered: asBool(json['registered']),
    );
  }

  final bool success;
  final String? message;
  final String? accessToken;
  final bool verified;
  final bool registered;
}

class BrowserPasskeyBridge {
  static bool isSupported(String origin) {
    final Uri? uri = Uri.tryParse(origin.trim());
    return uri != null &&
        (uri.scheme == 'http' || uri.scheme == 'https') &&
        (uri.host.isNotEmpty);
  }

  static Future<BrowserPasskeyBridgeResponse> start({
    required String passkeyOrigin,
    required String apiBaseUrl,
    required BrowserPasskeyBridgeMode mode,
    String? mfaChallengeId,
    String? accessToken,
    String? passkeyName,
  }) async {
    if (!isSupported(passkeyOrigin)) {
      throw ApiException('当前环境未配置可用的 Passkey 浏览器桥接地址');
    }

    final HttpServer server = await HttpServer.bind(
      InternetAddress.loopbackIPv4,
      0,
    );
    final Completer<BrowserPasskeyBridgeResponse> completer =
        Completer<BrowserPasskeyBridgeResponse>();
    final String state = _randomState();
    final Uri callbackUri = Uri(
      scheme: 'http',
      host: InternetAddress.loopbackIPv4.address,
      port: server.port,
      path: '/desktop-passkey-callback',
    );
    final Uri launchUri = _buildLaunchUri(
      passkeyOrigin: passkeyOrigin,
      apiBaseUrl: apiBaseUrl,
      mode: mode,
      callbackUri: callbackUri,
      state: state,
      mfaChallengeId: mfaChallengeId,
      accessToken: accessToken,
      passkeyName: passkeyName,
    );

    late final StreamSubscription<HttpRequest> subscription;
    subscription = server.listen((HttpRequest request) async {
      await _handleCallbackRequest(
        request,
        expectedState: state,
        completer: completer,
      );
      if (request.uri.path == '/desktop-passkey-callback' &&
          request.method == 'POST') {
        await subscription.cancel();
        await server.close(force: true);
      }
    });

    try {
      await openExternalUrl(launchUri.toString());
      return await completer.future.timeout(
        const Duration(minutes: 5),
        onTimeout: () => throw ApiException('等待浏览器回传结果超时'),
      );
    } finally {
      if (!completer.isCompleted) {
        await subscription.cancel();
        await server.close(force: true);
      }
    }
  }

  static Uri _buildLaunchUri({
    required String passkeyOrigin,
    required String apiBaseUrl,
    required BrowserPasskeyBridgeMode mode,
    required Uri callbackUri,
    required String state,
    String? mfaChallengeId,
    String? accessToken,
    String? passkeyName,
  }) {
    final Uri originUri = Uri.parse(
      passkeyOrigin.endsWith('/') ? passkeyOrigin : '$passkeyOrigin/',
    );
    final Map<String, String> query = <String, String>{
      'mode': mode.name,
      'callback': callbackUri.toString(),
      'state': state,
      'apiBaseUrl': apiBaseUrl,
      if (mfaChallengeId != null && mfaChallengeId.isNotEmpty)
        'mfaChallengeId': mfaChallengeId,
      if (passkeyName != null && passkeyName.isNotEmpty)
        'passkeyName': passkeyName,
    };
    final String fragment = accessToken != null && accessToken.isNotEmpty
        ? Uri(
            queryParameters: <String, String>{'accessToken': accessToken},
          ).query
        : '';
    return originUri
        .resolve('desktop/passkey-bridge')
        .replace(
          queryParameters: query,
          fragment: fragment.isEmpty ? null : fragment,
        );
  }

  static Future<void> _handleCallbackRequest(
    HttpRequest request, {
    required String expectedState,
    required Completer<BrowserPasskeyBridgeResponse> completer,
  }) async {
    request.response.headers
      ..set(HttpHeaders.accessControlAllowOriginHeader, '*')
      ..set(HttpHeaders.accessControlAllowHeadersHeader, 'Content-Type')
      ..set(HttpHeaders.accessControlAllowMethodsHeader, 'POST, OPTIONS')
      ..set(HttpHeaders.contentTypeHeader, 'text/html; charset=utf-8');

    if (request.method == 'OPTIONS') {
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
      return;
    }

    if (request.method != 'POST' ||
        request.uri.path != '/desktop-passkey-callback') {
      request.response.statusCode = HttpStatus.notFound;
      request.response.write('<html><body>Not found</body></html>');
      await request.response.close();
      return;
    }

    final String raw = await utf8.decoder.bind(request).join();
    final Map<String, dynamic> payload = raw.isEmpty
        ? <String, dynamic>{}
        : asMap(jsonDecode(raw));
    final String? state = asString(payload['state']);
    if (state != expectedState) {
      request.response.statusCode = HttpStatus.forbidden;
      request.response.write('<html><body>Invalid state</body></html>');
      await request.response.close();
      if (!completer.isCompleted) {
        completer.completeError(ApiException('浏览器桥接状态校验失败'));
      }
      return;
    }

    final BrowserPasskeyBridgeResponse response =
        BrowserPasskeyBridgeResponse.fromJson(payload);
    request.response.statusCode = HttpStatus.ok;
    request.response.write(
      response.success
          ? _successHtml(response.message)
          : _errorHtml(response.message),
    );
    await request.response.close();

    if (!completer.isCompleted) {
      if (response.success) {
        completer.complete(response);
      } else {
        completer.completeError(
          ApiException(response.message ?? '浏览器未完成 Passkey 操作'),
        );
      }
    }
  }

  static String _randomState() {
    final Random random = Random.secure();
    final List<int> bytes = List<int>.generate(24, (_) => random.nextInt(256));
    return base64UrlEncode(bytes);
  }

  static String _successHtml(String? message) {
    final String text = htmlEscape.convert(
      message ?? '桌面端已收到 Passkey 结果，可以关闭当前页面。',
    );
    return '<html><body style="font-family:sans-serif;padding:32px;background:#f6f4ee;">'
        '<h2>操作已完成</h2><p>$text</p><p>现在可以回到桌面端。</p></body></html>';
  }

  static String _errorHtml(String? message) {
    final String text = htmlEscape.convert(message ?? '桌面端未收到有效结果。');
    return '<html><body style="font-family:sans-serif;padding:32px;background:#fff4f4;">'
        '<h2>操作失败</h2><p>$text</p><p>请返回桌面端重试。</p></body></html>';
  }
}

Future<void> openExternalUrl(String url) async {
  ProcessResult result;
  if (Platform.isMacOS) {
    result = await Process.run('open', <String>[url]);
  } else if (Platform.isWindows) {
    result = await Process.run('cmd', <String>['/c', 'start', '', url]);
  } else {
    result = await Process.run('xdg-open', <String>[url]);
  }

  if (result.exitCode != 0) {
    final String message = result.stderr?.toString().trim().isNotEmpty == true
        ? result.stderr.toString().trim()
        : '无法打开外部浏览器';
    throw ApiException(message);
  }
}

class DesktopSessionBridgeServer {
  DesktopSessionBridgeServer({
    required this.controller,
    required Set<String> allowedOrigins,
  }) : _allowedOrigins = allowedOrigins;

  final AppController controller;
  final Set<String> _allowedOrigins;

  HttpServer? _server;
  StreamSubscription<HttpRequest>? _subscription;

  Future<void> start() async {
    if (_server != null) {
      return;
    }
    try {
      final HttpServer server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        kDesktopSessionBridgePort,
      );
      _server = server;
      _subscription = server.listen(_handleRequest);
    } on SocketException {
      // Ignore bridge startup failures so the app can continue to function normally.
    }
  }

  Future<void> stop() async {
    await _subscription?.cancel();
    await _server?.close(force: true);
    _subscription = null;
    _server = null;
  }

  Future<void> _handleRequest(HttpRequest request) async {
    final String? origin = request.headers.value('origin');
    final bool originAllowed = _isAllowedOrigin(origin);

    if (origin != null && !originAllowed) {
      request.response.statusCode = HttpStatus.forbidden;
      request.response.write('forbidden');
      await request.response.close();
      return;
    }

    _setCorsHeaders(
      request.response,
      origin: origin,
      allowOrigin: originAllowed,
    );

    if (request.method == 'OPTIONS') {
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
      return;
    }

    try {
      if (request.method == 'GET' &&
          request.uri.path == '/ksuser-auth/bridge/status') {
        await _writeJson(request.response, <String, dynamic>{
          'authenticated': controller.isAuthenticated,
          'environmentName': controller.environmentName,
          'apiBaseUrl': controller.apiBaseUrl,
          if (controller.user != null)
            'user': <String, dynamic>{
              'uuid': controller.user!.uuid,
              'username': controller.user!.username,
              'email': controller.user!.email,
              'avatarUrl': controller.user!.avatarUrl,
            },
        });
        return;
      }

      if (request.method == 'POST' &&
          request.uri.path == '/ksuser-auth/bridge/export') {
        if (!controller.isAuthenticated) {
          await _writeJson(request.response, <String, dynamic>{
            'message': '桌面端当前未登录',
          }, statusCode: HttpStatus.conflict);
          return;
        }
        final SessionTransferTicket ticket = await controller
            .createSessionTransferTicket(target: 'web');
        await _writeJson(request.response, <String, dynamic>{
          'transferCode': ticket.transferCode,
          'expiresInSeconds': ticket.expiresInSeconds,
          if (controller.user != null)
            'user': <String, dynamic>{
              'uuid': controller.user!.uuid,
              'username': controller.user!.username,
              'email': controller.user!.email,
              'avatarUrl': controller.user!.avatarUrl,
            },
        });
        return;
      }

      if (request.method == 'POST' &&
          request.uri.path == '/ksuser-auth/bridge/import') {
        final String raw = await utf8.decoder.bind(request).join();
        final Map<String, dynamic> payload = raw.isEmpty
            ? <String, dynamic>{}
            : asMap(jsonDecode(raw));
        final String? transferCode = asString(payload['transferCode']);
        if (transferCode == null || transferCode.trim().isEmpty) {
          await _writeJson(request.response, <String, dynamic>{
            'message': 'transferCode 不能为空',
          }, statusCode: HttpStatus.badRequest);
          return;
        }
        await controller.importSessionTransferTicket(transferCode);
        await _writeJson(request.response, <String, dynamic>{
          'authenticated': controller.isAuthenticated,
          if (controller.user != null)
            'user': <String, dynamic>{
              'uuid': controller.user!.uuid,
              'username': controller.user!.username,
              'email': controller.user!.email,
              'avatarUrl': controller.user!.avatarUrl,
            },
        });
        return;
      }

      await _writeJson(request.response, <String, dynamic>{
        'message': 'Not found',
      }, statusCode: HttpStatus.notFound);
    } on ApiException catch (error) {
      await _writeJson(request.response, <String, dynamic>{
        'message': error.message,
      }, statusCode: error.statusCode ?? HttpStatus.badRequest);
    } catch (error) {
      await _writeJson(request.response, <String, dynamic>{
        'message': error.toString(),
      }, statusCode: HttpStatus.internalServerError);
    }
  }

  bool _isAllowedOrigin(String? origin) {
    if (origin == null || origin.isEmpty) {
      return true;
    }
    return _allowedOrigins.contains(origin);
  }

  void _setCorsHeaders(
    HttpResponse response, {
    required String? origin,
    required bool allowOrigin,
  }) {
    response.headers
      ..set(HttpHeaders.contentTypeHeader, 'application/json; charset=utf-8')
      ..set(HttpHeaders.accessControlAllowMethodsHeader, 'GET, POST, OPTIONS')
      ..set(HttpHeaders.accessControlAllowHeadersHeader, 'Content-Type');
    if (origin != null && allowOrigin) {
      response.headers
        ..set(HttpHeaders.accessControlAllowOriginHeader, origin)
        ..set(HttpHeaders.varyHeader, 'Origin');
    }
  }

  Future<void> _writeJson(
    HttpResponse response,
    Map<String, dynamic> payload, {
    int statusCode = HttpStatus.ok,
  }) async {
    response.statusCode = statusCode;
    response.write(jsonEncode(payload));
    await response.close();
  }
}

class EnvConfig {
  const EnvConfig({
    required this.apiBaseUrl,
    required this.environmentName,
    required this.passkeyOrigin,
  });

  final String apiBaseUrl;
  final String environmentName;
  final String passkeyOrigin;

  static Future<EnvConfig> load() async {
    final bool isDevelopment = kDebugMode;
    final String assetName = isDevelopment
        ? '.env.development'
        : '.env.production';
    final String environmentName = isDevelopment ? 'Development' : 'Production';

    try {
      final String raw = await rootBundle.loadString(assetName);
      final Map<String, String> values = _parse(raw);
      return EnvConfig(
        apiBaseUrl: values['FLUTTER_API_BASE_URL']?.trim().isNotEmpty == true
            ? values['FLUTTER_API_BASE_URL']!.trim()
            : kDefaultApiBaseUrl,
        environmentName: environmentName,
        passkeyOrigin:
            values['FLUTTER_PASSKEY_ORIGIN']?.trim().isNotEmpty == true
            ? values['FLUTTER_PASSKEY_ORIGIN']!.trim()
            : (isDevelopment
                  ? 'http://localhost:5173'
                  : 'https://auth.ksuser.cn'),
      );
    } catch (_) {
      return EnvConfig(
        apiBaseUrl: kDefaultApiBaseUrl,
        environmentName: environmentName,
        passkeyOrigin: isDevelopment
            ? 'http://localhost:5173'
            : 'https://auth.ksuser.cn',
      );
    }
  }

  static Map<String, String> _parse(String raw) {
    final Map<String, String> result = <String, String>{};

    for (final String line in raw.split('\n')) {
      final String trimmed = line.trim();
      if (trimmed.isEmpty || trimmed.startsWith('#')) {
        continue;
      }

      final int index = trimmed.indexOf('=');
      if (index <= 0) {
        continue;
      }

      final String key = trimmed.substring(0, index).trim();
      final String value = trimmed.substring(index + 1).trim();
      result[key] = value;
    }

    return result;
  }
}
