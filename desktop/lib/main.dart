// ignore_for_file: use_build_context_synchronously

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

const Color kPrimaryColor = Color(0xFFFFB90F);
const Color kSurfaceTint = Color(0xFFFFF2CC);
const String kDefaultApiBaseUrl = 'https://api.ksuser.cn';
const String kDesktopAppName = 'Ksuser安全';
const String kDesktopAppVersion = '1.0.0';
const String kDesktopBuildNumber = '1';
const int kDesktopSessionBridgePort = 43921;
const String kSidebarLogoAsset = 'assets/logo/sidebar_logo.png';
const String kUserAgreementUrl = 'https://docs.ksuser.cn/agreement/user.html';
const String kPrivacyPolicyUrl =
    'https://docs.ksuser.cn/agreement/privacy.html';
const String kThirdPartySharingUrl =
    'https://docs.ksuser.cn/agreement/third-party-information-sharing.html';

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
  bool? _lastMenuBarAuthenticated;
  String? _lastMenuBarDisplayName;

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
    _controller.addListener(_handleControllerChanged);
    unawaited(_syncMenuBarState());
  }

  @override
  void dispose() {
    _appMenuChannel.setMethodCallHandler(null);
    _controller.removeListener(_handleControllerChanged);
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
      case 'openAbout':
        _openAboutPanel();
        return true;
      case 'openSettings':
        _openSettingsPanel();
        return true;
      case 'refresh':
        try {
          if (_controller.isAuthenticated) {
            await _controller.refreshWorkspace();
            await _showMenuBarMessage('数据刷新完成', success: true);
          } else {
            await _controller.fetchPasswordRequirement();
            await _showMenuBarMessage('登录页数据已刷新', success: true);
          }
        } catch (error) {
          await _showMenuBarMessage(error.toString(), success: false);
          rethrow;
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

  void _openAboutPanel() {
    final BuildContext? context = _navigatorKey.currentContext;
    if (context == null) {
      return;
    }
    unawaited(showDesktopAboutDialog(context, _controller));
  }

  void _handleControllerChanged() {
    unawaited(_syncMenuBarState());
  }

  Future<void> _syncMenuBarState() async {
    if (!Platform.isMacOS) {
      return;
    }

    final bool authenticated = _controller.isAuthenticated;
    final String displayName = authenticated
        ? _controller.user?.username.trim() ?? ''
        : '';

    if (_lastMenuBarAuthenticated == authenticated &&
        _lastMenuBarDisplayName == displayName) {
      return;
    }

    try {
      await _menuBarChannel.invokeMethod<bool>('updateState', <String, dynamic>{
        'authenticated': authenticated,
        'displayName': displayName,
      });
      _lastMenuBarAuthenticated = authenticated;
      _lastMenuBarDisplayName = displayName;
    } on PlatformException {
      // Ignore menu bar sync failures and keep the app usable.
    }
  }

  Future<void> _showMenuBarMessage(
    String message, {
    required bool success,
  }) async {
    if (!Platform.isMacOS) {
      return;
    }
    try {
      await _menuBarChannel.invokeMethod<bool>('showMessage', <String, dynamic>{
        'message': message,
        'success': success,
      });
    } on PlatformException {
      // Ignore menu bar message failures and keep the app usable.
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (BuildContext context, _) {
        return MaterialApp(
          title: kDesktopAppName,
          navigatorKey: _navigatorKey,
          debugShowCheckedModeBanner: false,
          theme: _buildTheme(Brightness.light),
          darkTheme: _buildTheme(Brightness.dark),
          themeMode: _controller.themeMode,
          themeAnimationDuration: _controller.reduceMotion
              ? Duration.zero
              : const Duration(milliseconds: 220),
          builder: (BuildContext context, Widget? child) {
            return Stack(
              children: <Widget>[
                child ?? const SizedBox.shrink(),
                if (_controller.isUiBusy)
                  _GlobalLoadingOverlay(message: _controller.currentBusyLabel),
              ],
            );
          },
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
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(44, 44),
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(42, 42),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
          side: outline,
          textStyle: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.w600),
        ),
      ),
      dividerTheme: DividerThemeData(
        color: isDark
            ? Colors.white.withValues(alpha: 0.08)
            : Colors.black.withValues(alpha: 0.07),
        thickness: 1,
        space: 1,
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
        if (controller.agreementLoading) {
          return const DesktopAgreementSplash();
        }
        if (!controller.agreementsAccepted) {
          return DesktopAgreementGate(controller: controller);
        }
        if (controller.isAuthenticated) {
          return DesktopWorkspace(controller: controller);
        }
        return DesktopAuthPortal(controller: controller);
      },
    );
  }
}

class DesktopAgreementSplash extends StatelessWidget {
  const DesktopAgreementSplash({super.key});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(strokeWidth: 2.6),
            ),
            const SizedBox(height: 18),
            Text(
              '正在检查使用协议...',
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class DesktopAgreementGate extends StatefulWidget {
  const DesktopAgreementGate({super.key, required this.controller});

  final AppController controller;

  @override
  State<DesktopAgreementGate> createState() => _DesktopAgreementGateState();
}

class _DesktopAgreementGateState extends State<DesktopAgreementGate> {
  bool _accepted = false;
  bool _busy = false;

  Future<void> _openAgreement(String url) async {
    try {
      await openExternalUrl(url);
    } catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.toString(), error: true);
    }
  }

  Future<void> _continue() async {
    if (!_accepted || _busy) {
      return;
    }
    setState(() {
      _busy = true;
    });
    try {
      await widget.controller.acceptAgreements();
    } catch (error) {
      if (!mounted) {
        return;
      }
      showAppMessage(context, error.toString(), error: true);
      setState(() {
        _busy = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 680),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(28),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: <Widget>[
                          Container(
                            width: 48,
                            height: 48,
                            decoration: BoxDecoration(
                              color: kPrimaryColor.withValues(alpha: 0.18),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: const Icon(
                              Icons.verified_user_rounded,
                              color: Color(0xFF9A6500),
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: <Widget>[
                                Text(
                                  '开始使用前请确认',
                                  style: theme.textTheme.headlineSmall
                                      ?.copyWith(fontWeight: FontWeight.w800),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  kDesktopAppName,
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                    color: theme.colorScheme.onSurfaceVariant,
                                    fontWeight: FontWeight.w600,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      Text(
                        '首次安装并使用本应用前，请阅读并同意以下协议和清单。未同意前将无法继续登录或使用桌面端功能。',
                        style: theme.textTheme.bodyLarge?.copyWith(
                          height: 1.55,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 20),
                      _AgreementLinkTile(
                        icon: Icons.description_outlined,
                        title: '服务条款',
                        url: kUserAgreementUrl,
                        onPressed: () => _openAgreement(kUserAgreementUrl),
                      ),
                      const SizedBox(height: 10),
                      _AgreementLinkTile(
                        icon: Icons.privacy_tip_outlined,
                        title: '隐私政策',
                        url: kPrivacyPolicyUrl,
                        onPressed: () => _openAgreement(kPrivacyPolicyUrl),
                      ),
                      const SizedBox(height: 10),
                      _AgreementLinkTile(
                        icon: Icons.hub_outlined,
                        title: '第三方信息共享清单',
                        url: kThirdPartySharingUrl,
                        onPressed: () => _openAgreement(kThirdPartySharingUrl),
                      ),
                      const SizedBox(height: 22),
                      Material(
                        color: isDark
                            ? Colors.white.withValues(alpha: 0.04)
                            : const Color(0xFFFFFBF0),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                          side: BorderSide(
                            color: kPrimaryColor.withValues(alpha: 0.35),
                          ),
                        ),
                        clipBehavior: Clip.antiAlias,
                        child: CheckboxListTile(
                          value: _accepted,
                          onChanged: _busy
                              ? null
                              : (bool? value) {
                                  setState(() {
                                    _accepted = value ?? false;
                                  });
                                },
                          controlAffinity: ListTileControlAffinity.leading,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 4,
                          ),
                          title: Text(
                            '我已阅读并同意以上服务条款、隐私政策和第三方信息共享清单',
                            style: theme.textTheme.bodyMedium?.copyWith(
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 22),
                      SizedBox(
                        width: double.infinity,
                        height: 48,
                        child: FilledButton.icon(
                          onPressed: _accepted && !_busy ? _continue : null,
                          icon: _busy
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Icon(Icons.arrow_forward_rounded),
                          label: Text(_busy ? '正在保存...' : '同意并继续'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _AgreementLinkTile extends StatelessWidget {
  const _AgreementLinkTile({
    required this.icon,
    required this.title,
    required this.url,
    required this.onPressed,
  });

  final IconData icon;
  final String title;
  final String url;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return OutlinedButton(
      onPressed: onPressed,
      style: OutlinedButton.styleFrom(
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      child: Row(
        children: <Widget>[
          Icon(icon, size: 22),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  title,
                  style: theme.textTheme.bodyLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 3),
                Text(
                  url,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 14),
          const Icon(Icons.open_in_new_rounded, size: 18),
        ],
      ),
    );
  }
}

class _GlobalLoadingOverlay extends StatelessWidget {
  const _GlobalLoadingOverlay({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    return Positioned.fill(
      child: ColoredBox(
        color: Colors.black.withValues(alpha: isDark ? 0.34 : 0.22),
        child: Center(
          child: Container(
            constraints: const BoxConstraints(minWidth: 240, maxWidth: 320),
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
            decoration: BoxDecoration(
              color: isDark ? const Color(0xFF262626) : Colors.white,
              borderRadius: BorderRadius.circular(20),
              boxShadow: <BoxShadow>[
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.10),
                  blurRadius: 28,
                  offset: const Offset(0, 14),
                ),
              ],
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                const SizedBox(
                  width: 22,
                  height: 22,
                  child: CircularProgressIndicator(strokeWidth: 2.4),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    message,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

enum DesktopSection {
  overview,
  profile,
  security,
  devices,
  activity,
  authorizations,
}

enum LoginFactor { password, emailCode }

enum MfaMode { code, recoveryCode, passkey }

enum SensitiveVerificationMethod { password, emailCode, totp, passkey, qr }

const MethodChannel _passkeyChannel = MethodChannel('ksuser/passkey');
const MethodChannel _appleLoginChannel = MethodChannel('ksuser/apple_login');
const MethodChannel _localAuthChannel = MethodChannel('ksuser/local_auth');
const MethodChannel _appMenuChannel = MethodChannel('ksuser/app_menu');
const MethodChannel _menuBarChannel = MethodChannel('ksuser/menu_bar');
const MethodChannel _windowControlChannel = MethodChannel(
  'ksuser/window_control',
);

class DesktopWindowPlatform {
  static bool get supportsMoveToMenuBar => Platform.isMacOS;

  static Future<void> moveToMenuBar() async {
    if (!supportsMoveToMenuBar) {
      return;
    }
    try {
      final bool ok =
          await _windowControlChannel.invokeMethod<bool>('moveToMenuBar') ??
          false;
      if (!ok) {
        throw ApiException('窗口收起到菜单栏失败');
      }
    } on PlatformException catch (error) {
      final String message = error.message?.trim().isNotEmpty == true
          ? error.message!.trim()
          : '窗口收起到菜单栏失败';
      throw ApiException(message);
    }
  }
}

class AgreementAcceptanceStore {
  static const String _fileName = 'agreement_acceptance.json';

  static Future<bool> isAccepted() async {
    final File file = await _file();
    if (!await file.exists()) {
      return false;
    }
    try {
      final Object? decoded = jsonDecode(await file.readAsString());
      if (decoded is! Map<String, dynamic>) {
        return false;
      }
      return decoded['accepted'] == true;
    } catch (_) {
      return false;
    }
  }

  static Future<void> markAccepted() async {
    final File file = await _file();
    await file.parent.create(recursive: true);
    final Map<String, Object> payload = <String, Object>{
      'accepted': true,
      'acceptedAt': DateTime.now().toUtc().toIso8601String(),
      'appVersion': kDesktopAppVersion,
      'agreements': <String, String>{
        'userAgreement': kUserAgreementUrl,
        'privacyPolicy': kPrivacyPolicyUrl,
        'thirdPartyInformationSharing': kThirdPartySharingUrl,
      },
    };
    await file.writeAsString(jsonEncode(payload));
  }

  static Future<File> _file() async {
    final Directory directory = await _appSupportDirectory();
    return File('${directory.path}${Platform.pathSeparator}$_fileName');
  }

  static Future<Directory> _appSupportDirectory() async {
    final Map<String, String> environment = Platform.environment;
    if (Platform.isMacOS) {
      final String home = environment['HOME'] ?? Directory.current.path;
      return Directory('$home/Library/Application Support/ksuser_auth_desktop');
    }
    if (Platform.isWindows) {
      final String base =
          environment['APPDATA'] ??
          environment['LOCALAPPDATA'] ??
          Directory.current.path;
      return Directory('$base${Platform.pathSeparator}ksuser_auth_desktop');
    }
    final String base =
        environment['XDG_CONFIG_HOME'] ??
        '${environment['HOME'] ?? Directory.current.path}/.config';
    return Directory('$base${Platform.pathSeparator}ksuser_auth_desktop');
  }
}

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

String buildMobileTransferQrText(String transferCode) {
  return 'KSUSER-AUTH-XFER:v1:${transferCode.trim()}';
}

Uri buildWebAccountRecoveryUri({
  required String passkeyOrigin,
  required String apiBaseUrl,
  String? recoveryCode,
}) {
  final String origin = passkeyOrigin.trim();
  final Uri baseUri = Uri.parse(origin.endsWith('/') ? origin : '$origin/');
  return baseUri
      .resolve('forgot-password')
      .replace(
        queryParameters: <String, String>{
          if (recoveryCode != null && recoveryCode.trim().isNotEmpty)
            'recoveryCode': recoveryCode.trim(),
          if (apiBaseUrl.trim().isNotEmpty) 'apiBaseUrl': apiBaseUrl.trim(),
        },
      );
}

String buildRemoteQrCodeImageUrl(String text, {bool useDarkModeStyle = false}) {
  final Map<String, String> query = <String, String>{
    'size': '240',
    'margin': '0',
    'data': text,
  };
  if (useDarkModeStyle) {
    query['dark'] = 'FFFFFF';
    query['light'] = '0000';
  } else {
    query['dark'] = '000000';
    query['light'] = 'FFFFFF';
  }
  return Uri.https('quickchart.io', '/qr', query).toString();
}

Future<void> showMobileBridgeQrDialog(
  BuildContext context,
  AppController controller,
) async {
  final ThemeData theme = Theme.of(context);
  final bool isDark = theme.brightness == Brightness.dark;
  final bool available = await LocalAuthPlatform.isAvailable();
  if (!available) {
    throw ApiException('当前设备不支持系统本地认证');
  }

  await LocalAuthPlatform.authenticate(reason: '请先完成身份验证后再展示手机登录二维码');
  if (!context.mounted) {
    return;
  }

  String qrPayload = '';
  int expiresInSeconds = 0;
  bool busy = false;
  String? errorText;
  bool initialized = false;
  Timer? countdownTimer;

  Future<void> refreshTicket(void Function(void Function()) setState) async {
    setState(() {
      busy = true;
      errorText = null;
    });
    try {
      final SessionTransferTicket ticket = await controller
          .createSessionTransferTicket(target: 'mobile');
      countdownTimer?.cancel();
      countdownTimer = null;
      final String qrText = buildMobileTransferQrText(ticket.transferCode);
      setState(() {
        qrPayload = qrText;
        expiresInSeconds = ticket.expiresInSeconds;
      });
      countdownTimer = Timer.periodic(const Duration(seconds: 1), (
        Timer timer,
      ) {
        if (expiresInSeconds <= 0) {
          timer.cancel();
          return;
        }
        setState(() {
          expiresInSeconds -= 1;
        });
      });
    } catch (error) {
      setState(() {
        errorText = error.toString();
      });
    } finally {
      setState(() {
        busy = false;
      });
    }
  }

  await showDialog<void>(
    context: context,
    builder: (BuildContext dialogContext) {
      return StatefulBuilder(
        builder:
            (
              BuildContext dialogContext,
              void Function(void Function()) setState,
            ) {
              if (!initialized) {
                initialized = true;
                unawaited(refreshTicket(setState));
              }
              return AlertDialog(
                title: const Text('手机扫码登录'),
                content: SizedBox(
                  width: 360,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      if (errorText != null &&
                          errorText!.isNotEmpty) ...<Widget>[
                        Text(
                          errorText!,
                          style: const TextStyle(color: Colors.red),
                        ),
                        const SizedBox(height: 12),
                      ],
                      if (busy && qrPayload.isEmpty)
                        const SizedBox(
                          height: 240,
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else if (qrPayload.isNotEmpty)
                        Center(
                          child: _buildLocalQrCode(
                            qrPayload,
                            width: 240,
                            height: 240,
                            isDark: isDark,
                          ),
                        )
                      else
                        const SizedBox(
                          width: 240,
                          height: 240,
                          child: Center(child: Text('正在生成二维码...')),
                        ),
                      const SizedBox(height: 12),
                      Text(
                        '剩余有效期：${max(expiresInSeconds, 0)} 秒',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '二维码一次性有效，过期后请刷新。',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: () => Navigator.of(dialogContext).pop(),
                    child: const Text('关闭'),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: busy
                        ? null
                        : () => unawaited(refreshTicket(setState)),
                    icon: busy
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.refresh_rounded),
                    label: const Text('刷新二维码'),
                  ),
                ],
              );
            },
      );
    },
  );

  countdownTimer?.cancel();
}

Future<void> showAccountRecoveryDialog(
  BuildContext context,
  AppController controller,
) async {
  final ThemeData theme = Theme.of(context);
  final bool isDark = theme.brightness == Brightness.dark;
  AccountRecoveryTicket? ticket;
  int expiresInSeconds = 0;
  bool refreshing = false;
  bool initialized = false;
  String? errorText;
  Timer? countdownTimer;

  Future<void> refreshTicket(void Function(void Function()) setState) async {
    setState(() {
      refreshing = true;
      errorText = null;
    });
    try {
      final AccountRecoveryTicket nextTicket = await controller
          .issueAccountRecoveryTicket();
      countdownTimer?.cancel();
      countdownTimer = Timer.periodic(const Duration(seconds: 1), (
        Timer timer,
      ) {
        if (expiresInSeconds <= 0) {
          timer.cancel();
          return;
        }
        setState(() {
          expiresInSeconds -= 1;
        });
      });
      setState(() {
        ticket = nextTicket;
        expiresInSeconds = nextTicket.expiresInSeconds;
      });
    } catch (error) {
      setState(() {
        errorText = error.toString();
      });
    } finally {
      setState(() {
        refreshing = false;
      });
    }
  }

  await showDialog<void>(
    context: context,
    builder: (BuildContext dialogContext) {
      return StatefulBuilder(
        builder: (BuildContext dialogContext, void Function(void Function()) setState) {
          if (!initialized) {
            initialized = true;
            unawaited(refreshTicket(setState));
          }
          final Uri? recoveryUri = ticket == null
              ? null
              : buildWebAccountRecoveryUri(
                  passkeyOrigin: controller.passkeyOrigin,
                  apiBaseUrl: controller.apiBaseUrl,
                  recoveryCode: ticket!.recoveryCode,
                );
          return AlertDialog(
            title: const Text('账号恢复授权'),
            content: SizedBox(
              width: 380,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  if (errorText != null && errorText!.isNotEmpty) ...<Widget>[
                    Text(
                      errorText!,
                      style: const TextStyle(color: Colors.red),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),
                  ],
                  if (refreshing && ticket == null)
                    const SizedBox(
                      height: 240,
                      child: Center(child: CircularProgressIndicator()),
                    )
                  else if (recoveryUri != null)
                    Center(
                      child: _buildLocalQrCode(
                        recoveryUri.toString(),
                        width: 240,
                        height: 240,
                        isDark: isDark,
                      ),
                    )
                  else
                    const SizedBox(
                      height: 240,
                      child: Center(child: Text('正在生成恢复二维码...')),
                    ),
                  const SizedBox(height: 12),
                  if (ticket != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text(
                            '${ticket!.username} · ${ticket!.maskedEmail}',
                            style: const TextStyle(fontWeight: FontWeight.w700),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            '背书设备：${ticket!.sponsorClientName.isNotEmpty ? ticket!.sponsorClientName : '当前桌面端'}',
                            style: TextStyle(
                              fontSize: 12,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          Text(
                            '位置：${ticket!.sponsorIpLocation.isNotEmpty ? ticket!.sponsorIpLocation : '未知位置'}',
                            style: TextStyle(
                              fontSize: 12,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                  const SizedBox(height: 12),
                  Text(
                    '剩余有效期：${max(expiresInSeconds, 0)} 秒',
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  SelectableText(
                    ticket?.recoveryCode ?? '恢复码生成中...',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.6,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '请在另一台设备打开网页恢复页后扫码，或手动输入上面的恢复码。恢复成功后旧会话会自动失效。',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 12,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('关闭'),
              ),
              TextButton.icon(
                onPressed: ticket == null
                    ? null
                    : () async {
                        try {
                          await Clipboard.setData(
                            ClipboardData(text: ticket!.recoveryCode),
                          );
                          if (dialogContext.mounted) {
                            showAppMessage(dialogContext, '恢复码已复制');
                          }
                        } catch (error) {
                          if (dialogContext.mounted) {
                            showAppMessage(
                              dialogContext,
                              error.toString(),
                              error: true,
                            );
                          }
                        }
                      },
                icon: const Icon(Icons.copy_rounded),
                label: const Text('复制恢复码'),
              ),
              FilledButton.tonalIcon(
                onPressed: recoveryUri == null
                    ? null
                    : () async {
                        try {
                          await openExternalUrl(recoveryUri.toString());
                        } catch (error) {
                          if (dialogContext.mounted) {
                            showAppMessage(
                              dialogContext,
                              error.toString(),
                              error: true,
                            );
                          }
                        }
                      },
                icon: const Icon(Icons.open_in_new_rounded),
                label: const Text('打开网页恢复页'),
              ),
              FilledButton.tonalIcon(
                onPressed: refreshing ? null : () => refreshTicket(setState),
                icon: refreshing
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh_rounded),
                label: Text(refreshing ? '刷新中...' : '刷新授权'),
              ),
            ],
          );
        },
      );
    },
  );

  countdownTimer?.cancel();
}

Future<void> showDesktopLoginQrDialog(
  BuildContext context,
  AppController controller,
) async {
  final ThemeData theme = Theme.of(context);
  final bool isDark = theme.brightness == Brightness.dark;
  String qrPayload = '';
  String challengeId = '';
  String pollToken = '';
  int expiresInSeconds = 0;
  String? errorText;
  bool refreshing = false;
  bool initialized = false;
  bool completed = false;
  Timer? countdownTimer;
  Timer? pollingTimer;

  Future<void> stopPolling() async {
    countdownTimer?.cancel();
    countdownTimer = null;
    pollingTimer?.cancel();
    pollingTimer = null;
  }

  MFAChallenge? buildMfaChallengeFromStatus(QrChallengeStatus status) {
    if (status.mfaChallengeId == null || status.mfaChallengeId!.isEmpty) {
      return null;
    }
    final List<String> normalizedMethods = status.methods
        .where(
          (String method) =>
              method == 'totp' || method == 'passkey' || method == 'qr',
        )
        .toList();
    final String fallbackMethod =
        status.method == 'passkey' || status.method == 'qr'
        ? status.method!
        : 'totp';
    return MFAChallenge(
      challengeId: status.mfaChallengeId!,
      method: fallbackMethod,
      methods: normalizedMethods.isNotEmpty
          ? normalizedMethods
          : <String>[fallbackMethod],
    );
  }

  Future<void> handleApprovedStatus(
    BuildContext dialogContext,
    QrChallengeStatus status,
  ) async {
    await stopPolling();
    completed = true;
    if (status.transferCode != null && status.transferCode!.isNotEmpty) {
      await controller.importSessionTransferTicket(status.transferCode!);
      if (dialogContext.mounted) {
        Navigator.of(dialogContext).pop();
      }
      return;
    }

    final MFAChallenge? nextMfa = buildMfaChallengeFromStatus(status);
    if (nextMfa != null) {
      controller.updatePendingMfaChallenge(nextMfa);
      if (dialogContext.mounted) {
        Navigator.of(dialogContext).pop();
      }
      return;
    }

    throw ApiException('扫码登录结果无效，请重试');
  }

  Future<void> pollStatus(
    BuildContext dialogContext,
    void Function(void Function()) setState,
  ) async {
    if (challengeId.isEmpty || pollToken.isEmpty || completed) {
      return;
    }
    try {
      final QrChallengeStatus status = await controller.pollQrStatus(
        challengeId: challengeId,
        pollToken: pollToken,
      );
      if (!dialogContext.mounted) {
        return;
      }
      setState(() {
        expiresInSeconds = status.expiresInSeconds;
      });
      if (status.status == 'pending') {
        return;
      }
      if (status.status == 'approved') {
        await handleApprovedStatus(dialogContext, status);
        return;
      }
      await stopPolling();
      if (!dialogContext.mounted) {
        return;
      }
      setState(() {
        if (status.status == 'rejected') {
          errorText = '扫码请求已被拒绝，请刷新二维码后重试';
        } else {
          errorText = '二维码已过期，请刷新二维码';
        }
      });
    } catch (error) {
      await stopPolling();
      if (!dialogContext.mounted) {
        return;
      }
      setState(() {
        errorText = error.toString();
      });
    }
  }

  Future<void> refreshChallenge(
    BuildContext dialogContext,
    void Function(void Function()) setState,
  ) async {
    setState(() {
      refreshing = true;
      errorText = null;
    });
    try {
      final QrLoginChallenge challenge = await controller.initQrLogin();
      await stopPolling();
      if (!dialogContext.mounted) {
        return;
      }
      setState(() {
        challengeId = challenge.challengeId;
        pollToken = challenge.pollToken;
        qrPayload = challenge.qrText;
        expiresInSeconds = challenge.expiresInSeconds;
      });
      countdownTimer = Timer.periodic(const Duration(seconds: 1), (
        Timer timer,
      ) {
        if (expiresInSeconds <= 0) {
          timer.cancel();
          return;
        }
        setState(() {
          expiresInSeconds -= 1;
        });
      });
      pollingTimer = Timer.periodic(const Duration(seconds: 2), (_) {
        unawaited(pollStatus(dialogContext, setState));
      });
      unawaited(pollStatus(dialogContext, setState));
    } catch (error) {
      if (!dialogContext.mounted) {
        return;
      }
      setState(() {
        errorText = error.toString();
      });
    } finally {
      if (dialogContext.mounted) {
        setState(() {
          refreshing = false;
        });
      }
    }
  }

  await showDialog<void>(
    context: context,
    builder: (BuildContext dialogContext) {
      return StatefulBuilder(
        builder:
            (
              BuildContext dialogContext,
              void Function(void Function()) setState,
            ) {
              if (!initialized) {
                initialized = true;
                unawaited(refreshChallenge(dialogContext, setState));
              }
              return AlertDialog(
                title: const Text('二维码登录'),
                content: SizedBox(
                  width: 360,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      if (errorText != null &&
                          errorText!.isNotEmpty) ...<Widget>[
                        Text(
                          errorText!,
                          style: const TextStyle(color: Colors.red),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 12),
                      ],
                      if (refreshing && qrPayload.isEmpty)
                        const SizedBox(
                          height: 240,
                          child: Center(child: CircularProgressIndicator()),
                        )
                      else
                        Center(
                          child: SizedBox(
                            width: 240,
                            height: 240,
                            child: qrPayload.isEmpty
                                ? const Center(child: Text('正在生成二维码...'))
                                : _buildLocalQrCode(
                                    qrPayload,
                                    width: 240,
                                    height: 240,
                                    isDark: isDark,
                                  ),
                          ),
                        ),
                      const SizedBox(height: 16),
                      const Text(
                        '请使用已登录的手机端扫描二维码，授权当前桌面端登录。',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 10),
                      Text(
                        '剩余有效期：${max(expiresInSeconds, 0)} 秒',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '若账号开启了 MFA，扫码后还需在桌面端完成其他 MFA 验证。',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                actions: <Widget>[
                  TextButton(
                    onPressed: () => Navigator.of(dialogContext).pop(),
                    child: const Text('关闭'),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: refreshing
                        ? null
                        : () => unawaited(
                            refreshChallenge(dialogContext, setState),
                          ),
                    icon: refreshing
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.refresh_rounded),
                    label: const Text('刷新二维码'),
                  ),
                ],
              );
            },
      );
    },
  );

  await stopPolling();
}

class _DesktopAboutCard extends StatelessWidget {
  const _DesktopAboutCard({required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme colors = theme.colorScheme;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: 0.52),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: colors.outlineVariant.withValues(alpha: 0.55),
        ),
      ),
      child: Row(
        children: <Widget>[
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: BorderRadius.circular(17),
              boxShadow: <BoxShadow>[
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.08),
                  blurRadius: 10,
                  offset: const Offset(0, 3),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(14),
              child: Image.asset(
                kSidebarLogoAsset,
                width: 54,
                height: 54,
                fit: BoxFit.cover,
              ),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  kDesktopAppName,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  '版本 $kDesktopAppVersion ($kDesktopBuildNumber)',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: colors.onSurfaceVariant,
                    fontWeight: FontWeight.w600,
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

class _SettingsDocumentTile extends StatelessWidget {
  const _SettingsDocumentTile({
    required this.icon,
    required this.title,
    required this.url,
  });

  final IconData icon;
  final String title;
  final String url;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(13),
          onTap: () async {
            try {
              await openExternalUrl(url);
            } catch (error) {
              if (context.mounted) {
                showAppMessage(context, error.toString(), error: true);
              }
            }
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 11),
            child: Row(
              children: <Widget>[
                Icon(icon, size: 19, color: theme.colorScheme.onSurfaceVariant),
                const SizedBox(width: 11),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                ),
                Icon(
                  Icons.open_in_new_rounded,
                  size: 17,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
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
                    const SizedBox(height: 14),
                    const Divider(height: 1),
                    const SizedBox(height: 18),
                    Text('关于', style: Theme.of(context).textTheme.titleMedium),
                    const SizedBox(height: 12),
                    _DesktopAboutCard(controller: controller),
                    const SizedBox(height: 12),
                    _SettingsDocumentTile(
                      icon: Icons.description_outlined,
                      title: '服务条款',
                      url: kUserAgreementUrl,
                    ),
                    _SettingsDocumentTile(
                      icon: Icons.privacy_tip_outlined,
                      title: '隐私协议',
                      url: kPrivacyPolicyUrl,
                    ),
                    _SettingsDocumentTile(
                      icon: Icons.share_outlined,
                      title: '第三方信息共享清单',
                      url: kThirdPartySharingUrl,
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

Future<void> showDesktopAboutDialog(
  BuildContext context,
  AppController controller,
) {
  return showDialog<void>(
    context: context,
    builder: (BuildContext context) {
      return AboutDialog(
        applicationName: kDesktopAppName,
        applicationVersion: kDesktopAppVersion,
        applicationIcon: ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Image.asset(
            kSidebarLogoAsset,
            width: 56,
            height: 56,
            fit: BoxFit.cover,
          ),
        ),
        children: <Widget>[
          const SizedBox(height: 8),
          const Text('桌面统一认证客户端'),
          const SizedBox(height: 8),
          Text('主题模式：${themeModeLabel(controller.themeMode)}'),
          Text(
            '本地调试：${controller.compactMode ? '紧凑布局，' : ''}${controller.reduceMotion ? '减少动画' : '标准动画'}',
          ),
        ],
      );
    },
  );
}

String themeModeLabel(ThemeMode mode) {
  switch (mode) {
    case ThemeMode.system:
      return '跟随系统';
    case ThemeMode.light:
      return '浅色';
    case ThemeMode.dark:
      return '深色';
  }
}

String nativePasskeyPlatformName() {
  if (Platform.isMacOS) {
    return 'macOS';
  }
  if (Platform.isWindows) {
    return 'Windows';
  }
  return '系统';
}

String nativePasskeyAvailableDescription({String action = '验证'}) {
  return '将直接调用 ${nativePasskeyPlatformName()} 系统原生 Passkey 完成$action。';
}

String nativePasskeyUnavailableDescription() {
  return '当前设备未启用 ${nativePasskeyPlatformName()} 原生 Passkey。';
}

class PasskeyPlatform {
  static bool get supportsAssertion => Platform.isWindows || Platform.isMacOS;

  static Future<bool> isAvailable() async {
    if (!supportsAssertion) {
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
    if (!supportsAssertion) {
      throw ApiException('当前平台暂不支持原生 Passkey');
    }

    try {
      final Map<dynamic, dynamic>? result = await _passkeyChannel
          .invokeMethod<Map<dynamic, dynamic>>(
            'performAssertion',
            <String, dynamic>{
              'challenge': options.challenge,
              'timeout': options.timeout,
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

  static Future<PasskeyRegistrationResult> createCredential({
    required PasskeyRegistrationOptions options,
    required String origin,
  }) async {
    if (!supportsAssertion) {
      throw ApiException('当前平台暂不支持原生 Passkey');
    }

    try {
      final Map<dynamic, dynamic>? result = await _passkeyChannel
          .invokeMethod<Map<dynamic, dynamic>>(
            'performRegistration',
            <String, dynamic>{
              'challenge': options.challenge,
              'rpId': options.rpId,
              'origin': origin,
              'name': options.userName,
              'displayName': options.userDisplayName,
              'userId': options.userId,
              'userVerification': options.userVerification,
              'attestation': options.attestation,
            },
          );
      if (result == null) {
        throw ApiException('未创建 Passkey 凭证');
      }
      return PasskeyRegistrationResult.fromChannelMap(result);
    } on PlatformException catch (error) {
      final String message = error.message?.trim().isNotEmpty == true
          ? error.message!.trim()
          : 'Passkey 创建失败';
      throw ApiException(message);
    }
  }
}

class AppleLoginPlatform {
  static bool get isAvailable => Platform.isMacOS;

  static Future<String> clientId() async {
    if (!isAvailable) {
      throw ApiException('Apple 登录仅在 macOS 上提供');
    }
    try {
      final String? value = await _appleLoginChannel.invokeMethod<String>(
        'clientId',
      );
      if (value == null || value.trim().isEmpty) {
        throw ApiException('未获取到 macOS Apple 登录配置');
      }
      return value;
    } on PlatformException catch (error) {
      throw ApiException(error.message ?? '无法读取 Apple 登录配置');
    }
  }

  static Future<Map<String, dynamic>> signIn({
    required String nonce,
    required String state,
  }) async {
    if (!isAvailable) {
      throw ApiException('Apple 登录仅在 macOS 上提供');
    }
    try {
      final Map<dynamic, dynamic>? response = await _appleLoginChannel
          .invokeMethod<Map<dynamic, dynamic>>('signIn', <String, dynamic>{
            'nonce': nonce,
            'state': state,
          });
      if (response == null ||
          asString(response['identityToken'])?.isNotEmpty != true ||
          asString(response['authorizationCode'])?.isNotEmpty != true) {
        throw ApiException('Apple 未返回完整的登录凭据');
      }
      return <String, dynamic>{
        'identityToken': asString(response['identityToken']),
        'authorizationCode': asString(response['authorizationCode']),
        'state': asString(response['state']),
        if (asString(response['givenName']) != null)
          'givenName': asString(response['givenName']),
        if (asString(response['familyName']) != null)
          'familyName': asString(response['familyName']),
      };
    } on PlatformException catch (error) {
      if (error.code == 'cancelled') {
        throw ApiException('已取消 Apple 登录');
      }
      throw ApiException(error.message ?? 'Apple 登录失败，请重试');
    }
  }
}

class LocalAuthPlatform {
  static bool get supportsProtectedActions =>
      Platform.isMacOS || Platform.isWindows;

  static Future<bool> isAvailable() async {
    if (!supportsProtectedActions) {
      return false;
    }
    try {
      return await _localAuthChannel.invokeMethod<bool>('isAvailable') ?? false;
    } on PlatformException {
      return false;
    }
  }

  static Future<void> authenticate({required String reason}) async {
    if (!supportsProtectedActions) {
      throw ApiException('当前平台暂不支持系统本地认证');
    }

    try {
      final bool ok =
          await _localAuthChannel.invokeMethod<bool>(
            'authenticate',
            <String, dynamic>{'reason': reason},
          ) ??
          false;
      if (!ok) {
        throw ApiException('本地认证未通过');
      }
    } on PlatformException catch (error) {
      final String code = error.code.trim();
      if (Platform.isWindows) {
        if (code == 'canceled') {
          throw ApiException('用户取消了系统身份验证');
        }
        if (code == 'not_available') {
          throw ApiException('当前设备未启用 Windows Hello、PIN 或其他系统身份验证方式');
        }
        final String message = error.message?.trim().isNotEmpty == true
            ? error.message!.trim()
            : 'Windows 系统身份验证失败';
        throw ApiException(message);
      }
      if (code == 'canceled') {
        throw ApiException('用户取消了本地认证');
      }
      final String message = error.message?.trim().isNotEmpty == true
          ? error.message!.trim()
          : '本地认证失败';
      throw ApiException(message);
    }
  }
}

class AppController extends ChangeNotifier {
  AppController(
    this._apiClient, {
    required this.environmentName,
    required this.passkeyOrigin,
    Future<bool> Function()? loadAgreementAcceptance,
  }) {
    _loadAgreementAcceptanceValue =
        loadAgreementAcceptance ?? AgreementAcceptanceStore.isAccepted;
    _apiClient.onSessionExpired = _handleSessionExpired;
    unawaited(_loadAgreementAcceptance());
  }

  final KsuserApiClient _apiClient;
  final String environmentName;
  final String passkeyOrigin;
  late final Future<bool> Function() _loadAgreementAcceptanceValue;

  DesktopSection selectedSection = DesktopSection.overview;
  UserDetails? user;
  List<SessionItem> sessions = <SessionItem>[];
  List<SensitiveLogItem> sensitiveLogs = <SensitiveLogItem>[];
  List<OAuth2AuthorizedApp> authorizedApps = <OAuth2AuthorizedApp>[];
  bool authorizationsLoading = false;
  String? authorizationsError;
  String? revokingAuthorizationId;
  List<PasskeyListItem> passkeys = <PasskeyListItem>[];
  TotpStatusResponse? totpStatus;
  AdaptiveAuthStatus? adaptiveAuthStatus;
  PasswordRequirement? passwordRequirement;
  MFAChallenge? pendingMfaChallenge;
  bool workspaceLoading = false;
  bool authBusy = false;
  ThemeMode themeMode = ThemeMode.system;
  bool compactMode = false;
  bool reduceMotion = false;
  bool agreementLoading = true;
  bool agreementsAccepted = false;
  String? workspaceError;
  final List<String> _busyLabelStack = <String>[];

  bool get isAuthenticated => _apiClient.accessToken != null;
  String get apiBaseUrl => _apiClient.baseUrl;
  bool get hasPendingUiAction => _busyLabelStack.isNotEmpty;
  bool get isUiBusy => authBusy || workspaceLoading || hasPendingUiAction;
  String get currentBusyLabel {
    if (_busyLabelStack.isNotEmpty) {
      return _busyLabelStack.last;
    }
    if (authBusy) {
      return '正在处理认证请求...';
    }
    if (workspaceLoading) {
      return '正在同步桌面数据...';
    }
    return '正在处理中...';
  }

  Future<void> _loadAgreementAcceptance() async {
    try {
      agreementsAccepted = await _loadAgreementAcceptanceValue();
    } catch (_) {
      agreementsAccepted = false;
    } finally {
      agreementLoading = false;
      notifyListeners();
    }
  }

  Future<void> acceptAgreements() async {
    await AgreementAcceptanceStore.markAccepted();
    agreementLoading = false;
    agreementsAccepted = true;
    notifyListeners();
  }

  Future<T> runBusyAction<T>(String label, Future<T> Function() action) async {
    _busyLabelStack.add(label);
    notifyListeners();
    try {
      return await action();
    } finally {
      if (_busyLabelStack.isNotEmpty) {
        _busyLabelStack.removeLast();
      }
      notifyListeners();
    }
  }

  void _resetLocalSessionState() {
    _apiClient.clearSession();
    user = null;
    sessions = <SessionItem>[];
    sensitiveLogs = <SensitiveLogItem>[];
    authorizedApps = <OAuth2AuthorizedApp>[];
    authorizationsError = null;
    passkeys = <PasskeyListItem>[];
    totpStatus = null;
    adaptiveAuthStatus = null;
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
    if (section == DesktopSection.authorizations) {
      unawaited(refreshAuthorizations());
    }
  }

  Future<void> refreshAuthorizations() async {
    if (!isAuthenticated || authorizationsLoading) return;
    authorizationsLoading = true;
    authorizationsError = null;
    notifyListeners();
    try {
      final dynamic data = await _apiClient.get(
        '/oauth2/authorizations',
        authorized: true,
      );
      authorizedApps = asList(data)
          .map((dynamic item) => OAuth2AuthorizedApp.fromJson(asMap(item)))
          .toList();
    } catch (error) {
      authorizationsError = error is ApiException
          ? error.message
          : '加载已授权应用失败，请稍后重试';
    } finally {
      authorizationsLoading = false;
      notifyListeners();
    }
  }

  Future<void> revokeAuthorization(String appId) async {
    if (revokingAuthorizationId != null) return;
    revokingAuthorizationId = appId;
    notifyListeners();
    try {
      await _apiClient.delete(
        '/oauth2/authorizations/${Uri.encodeComponent(appId)}',
        authorized: true,
      );
      authorizedApps = authorizedApps
          .where((OAuth2AuthorizedApp app) => app.appId != appId)
          .toList();
      authorizationsError = null;
    } finally {
      revokingAuthorizationId = null;
      notifyListeners();
    }
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

  void updatePendingMfaChallenge(MFAChallenge? value) {
    if (pendingMfaChallenge == value) {
      return;
    }
    pendingMfaChallenge = value;
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
    await runBusyAction('正在登录...', () async {
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
    });
  }

  Future<void> sendLoginCode(String email) {
    return runBusyAction('正在发送登录验证码...', () {
      return _apiClient.post(
        '/auth/send-code',
        body: <String, dynamic>{'email': email.trim(), 'type': 'login'},
      );
    });
  }

  Future<void> loginWithCode({
    required String email,
    required String code,
  }) async {
    await runBusyAction('正在登录...', () async {
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
    });
  }

  Future<void> loginWithApple() async {
    if (!AppleLoginPlatform.isAvailable) {
      throw ApiException('Apple 登录仅在 macOS 上提供');
    }
    await runBusyAction('正在使用 Apple 登录...', () async {
      authBusy = true;
      pendingMfaChallenge = null;
      notifyListeners();
      try {
        final String clientId = await AppleLoginPlatform.clientId();
        final Map<String, dynamic> challenge = asMap(
          await _apiClient.post(
            '/oauth/apple/challenge',
            body: <String, dynamic>{'purpose': 'login', 'clientId': clientId},
          ),
        );
        final String challengeId = asString(challenge['challengeId']) ?? '';
        final String nonce = asString(challenge['nonce']) ?? '';
        final String state = asString(challenge['state']) ?? '';
        if (challengeId.isEmpty || nonce.isEmpty || state.isEmpty) {
          throw ApiException('Apple 登录挑战信息不完整，请重试');
        }
        final Map<String, dynamic> credential = await AppleLoginPlatform.signIn(
          nonce: nonce,
          state: state,
        );
        final Map<String, dynamic> payload = <String, dynamic>{
          'challengeId': challengeId,
          ...credential,
        };
        final dynamic response = await _apiClient.post(
          '/oauth/apple/mobile-login',
          body: payload,
        );
        final Map<String, dynamic> result = asMap(response);
        if (asBool(result['needBind'])) {
          throw ApiException('需前往网页端或移动端绑定账号之后才可以使用该第三方登录');
        }
        await _consumeLoginPayload(response);
      } finally {
        authBusy = false;
        notifyListeners();
      }
    });
  }

  Future<void> completeTotpMfa({
    required String challengeId,
    String? code,
    String? recoveryCode,
  }) async {
    await runBusyAction('正在验证二次认证...', () async {
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
    });
  }

  Future<void> completePasskeyMfa({required String challengeId}) async {
    await runBusyAction('正在等待 Passkey 完成二次验证...', () async {
      authBusy = true;
      notifyListeners();
      try {
        if (usesNativePasskey) {
          if (!await PasskeyPlatform.isAvailable()) {
            throw ApiException(passkeyUnavailableMessage);
          }
          final PasskeyAssertionOptions options =
              await getPasskeyAuthenticationOptions();
          final PasskeyAssertionResult assertion =
              await PasskeyPlatform.getAssertion(
                options: options,
                origin: passkeyOrigin,
              );
          final dynamic data = await _apiClient.post(
            '/auth/passkey/mfa-verify',
            body: <String, dynamic>{
              'mfaChallengeId': challengeId,
              'passkeyChallengeId': options.challengeId,
              ...assertion.toJson(),
            },
          );
          pendingMfaChallenge = null;
          await _consumeLoginPayload(data);
        } else if (usesBrowserPasskeyBridge) {
          if (!supportsBrowserPasskeyBridge) {
            throw ApiException(passkeyUnavailableMessage);
          }
          try {
            final BrowserPasskeyBridgeResponse response =
                await BrowserPasskeyBridge.start(
                  passkeyOrigin: passkeyOrigin,
                  apiBaseUrl: apiBaseUrl,
                  mode: BrowserPasskeyBridgeMode.mfa,
                  mfaChallengeId: challengeId,
                );
            if (response.transferCode != null &&
                response.transferCode!.isNotEmpty) {
              await importSessionTransferTicket(response.transferCode!);
              pendingMfaChallenge = null;
              return;
            }
            if (response.accessToken == null || response.accessToken!.isEmpty) {
              throw ApiException(response.message ?? '浏览器未返回登录结果');
            }
            _apiClient.accessToken = response.accessToken;
            pendingMfaChallenge = null;
            await refreshWorkspace();
          } on SocketException catch (error) {
            throw ApiException(_passkeyBridgeSocketErrorMessage(error));
          }
        } else {
          throw ApiException(passkeyUnavailableMessage);
        }
      } finally {
        authBusy = false;
        notifyListeners();
      }
    });
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
    return runBusyAction('正在加载密码规则...', () async {
      final dynamic data = await _apiClient.get('/info/password-requirement');
      final PasswordRequirement requirement = PasswordRequirement.fromJson(
        asMap(data),
      );
      passwordRequirement = requirement;
      notifyListeners();
      return requirement;
    });
  }

  Future<void> sendRegisterCode(String email) {
    return runBusyAction('正在发送注册验证码...', () {
      return _apiClient.post(
        '/auth/send-code',
        body: <String, dynamic>{'email': email.trim(), 'type': 'register'},
      );
    });
  }

  Future<void> register({
    required String username,
    required String email,
    required String password,
    required String code,
  }) async {
    await runBusyAction('正在创建账户...', () async {
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
    });
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
      adaptiveAuthStatus = await _loadAdaptiveAuthStatus();
    } catch (error) {
      workspaceError = error is ApiException ? error.message : '加载桌面数据失败';
      rethrow;
    } finally {
      workspaceLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshSensitiveLogs({SensitiveLogsQuery? query}) async {
    await runBusyAction('正在刷新敏感日志...', () async {
      sensitiveLogs = await _loadSensitiveLogs(
        query ?? const SensitiveLogsQuery(),
      );
      notifyListeners();
    });
  }

  Future<void> refreshSessions() async {
    await runBusyAction('正在刷新在线设备...', () async {
      sessions = await _loadSessions();
      notifyListeners();
    });
  }

  Future<void> refreshPasskeys() async {
    await runBusyAction('正在刷新 Passkey 列表...', () async {
      passkeys = await _loadPasskeys();
      notifyListeners();
    });
  }

  Future<void> refreshTotpStatus() async {
    await runBusyAction('正在刷新 TOTP 状态...', () async {
      totpStatus = await _loadTotpStatus();
      notifyListeners();
    });
  }

  Future<void> refreshAdaptiveAuthStatus() async {
    await runBusyAction('正在刷新连续认证状态...', () async {
      adaptiveAuthStatus = await _loadAdaptiveAuthStatus();
      notifyListeners();
    });
  }

  Future<void> updateSetting({
    required String field,
    bool? value,
    String? stringValue,
  }) async {
    await runBusyAction('正在保存设置...', () async {
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
    });
  }

  Future<void> updateProfileField({
    required String key,
    required String value,
  }) async {
    await runBusyAction('正在保存资料...', () async {
      final dynamic data = await _apiClient.post(
        '/auth/update/profile',
        authorized: true,
        body: <String, dynamic>{'key': key, 'value': value.trim()},
      );
      user = UserDetails.fromJson(asMap(data));
      notifyListeners();
    });
  }

  Future<void> uploadAvatar({
    required Uint8List bytes,
    required String fileName,
    required String contentType,
  }) async {
    await runBusyAction('正在上传头像...', () async {
      final dynamic data = await _apiClient.postMultipartFile(
        '/auth/upload/avatar',
        fieldName: 'file',
        fileName: fileName,
        contentType: contentType,
        bytes: bytes,
        authorized: true,
      );
      user = UserDetails.fromJson(asMap(data));
      notifyListeners();
    });
  }

  Future<SensitiveVerificationStatus> checkSensitiveVerification() async {
    return runBusyAction('正在检查验证状态...', () async {
      final dynamic data = await _apiClient.get(
        '/auth/check-sensitive-verification',
        authorized: true,
      );
      return SensitiveVerificationStatus.fromJson(asMap(data));
    });
  }

  Future<AdaptiveAuthStatus> getAdaptiveAuthStatus() async {
    return runBusyAction('正在检查连续认证状态...', () async {
      final dynamic data = await _apiClient.get(
        '/auth/adaptive-auth/status',
        authorized: true,
      );
      return AdaptiveAuthStatus.fromJson(asMap(data));
    });
  }

  Future<void> sendSensitiveVerificationCode() {
    return runBusyAction('正在发送验证邮件...', () {
      return _apiClient.post(
        '/auth/send-code',
        authorized: true,
        body: const <String, dynamic>{'type': 'sensitive-verification'},
      );
    });
  }

  Future<void> verifySensitiveOperation({
    required String method,
    String? password,
    String? code,
  }) {
    return runBusyAction('正在验证身份...', () {
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
    });
  }

  Future<PasskeyAssertionOptions> getPasskeyAuthenticationOptions() async {
    final dynamic data = await _apiClient.post(
      '/auth/passkey/authentication-options',
    );
    return PasskeyAssertionOptions.fromJson(asMap(data));
  }

  Future<PasskeyRegistrationOptions> getPasskeyRegistrationOptions({
    String? preferredName,
  }) async {
    final dynamic data = await _apiClient.post(
      '/auth/passkey/registration-options',
      authorized: true,
      body: <String, dynamic>{
        'passkeyName': preferredName ?? 'Ksuser Desktop',
        'authenticatorType': 'auto',
      },
    );
    return PasskeyRegistrationOptions.fromJson(asMap(data));
  }

  bool get supportsBrowserPasskeyBridge =>
      BrowserPasskeyBridge.isSupported(passkeyOrigin);

  bool get usesNativePasskey => Platform.isWindows || Platform.isMacOS;

  bool get usesBrowserPasskeyBridge => false;

  String get passkeyUnavailableMessage {
    if (usesNativePasskey) {
      return nativePasskeyUnavailableDescription();
    }
    if (usesBrowserPasskeyBridge) {
      return '当前环境未配置可用的 Passkey 浏览器桥接地址';
    }
    return '当前平台暂不支持 Passkey';
  }

  Future<bool> isPasskeyAvailable() async {
    if (usesNativePasskey) {
      return PasskeyPlatform.isAvailable();
    }
    if (usesBrowserPasskeyBridge) {
      return supportsBrowserPasskeyBridge;
    }
    return false;
  }

  Future<void> loginWithPasskey() async {
    await runBusyAction('正在等待 Passkey 登录...', () async {
      authBusy = true;
      pendingMfaChallenge = null;
      notifyListeners();
      try {
        if (usesNativePasskey) {
          if (!await PasskeyPlatform.isAvailable()) {
            throw ApiException(passkeyUnavailableMessage);
          }
          final PasskeyAssertionOptions options =
              await getPasskeyAuthenticationOptions();
          final PasskeyAssertionResult assertion =
              await PasskeyPlatform.getAssertion(
                options: options,
                origin: passkeyOrigin,
              );
          final dynamic data = await _apiClient.post(
            '/auth/passkey/authentication-verify',
            query: <String, String>{'challengeId': options.challengeId},
            body: assertion.toJson(),
          );
          await _consumeLoginPayload(data);
        } else if (usesBrowserPasskeyBridge) {
          if (!supportsBrowserPasskeyBridge) {
            throw ApiException(passkeyUnavailableMessage);
          }
          try {
            final BrowserPasskeyBridgeResponse response =
                await BrowserPasskeyBridge.start(
                  passkeyOrigin: passkeyOrigin,
                  apiBaseUrl: apiBaseUrl,
                  mode: BrowserPasskeyBridgeMode.login,
                );
            if (response.transferCode != null &&
                response.transferCode!.isNotEmpty) {
              await importSessionTransferTicket(response.transferCode!);
              return;
            }
            if (response.accessToken == null || response.accessToken!.isEmpty) {
              throw ApiException(response.message ?? '浏览器未返回登录结果');
            }
            _apiClient.accessToken = response.accessToken;
            await refreshWorkspace();
          } on SocketException catch (error) {
            throw ApiException(_passkeyBridgeSocketErrorMessage(error));
          }
        } else {
          throw ApiException(passkeyUnavailableMessage);
        }
      } finally {
        authBusy = false;
        notifyListeners();
      }
    });
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

  Future<void> performPasskeySensitiveVerification() {
    return runBusyAction('正在等待 Passkey 验证...', () async {
      if (usesNativePasskey) {
        if (!await PasskeyPlatform.isAvailable()) {
          throw ApiException(passkeyUnavailableMessage);
        }
        final PasskeyAssertionOptions options =
            await getPasskeySensitiveVerificationOptions();
        final PasskeyAssertionResult assertion =
            await PasskeyPlatform.getAssertion(
              options: options,
              origin: passkeyOrigin,
            );
        await verifySensitiveOperationWithPasskey(
          challengeId: options.challengeId,
          assertion: assertion,
        );
        return;
      }

      if (!usesBrowserPasskeyBridge) {
        throw ApiException(passkeyUnavailableMessage);
      }

      if (!supportsBrowserPasskeyBridge) {
        throw ApiException(passkeyUnavailableMessage);
      }

      try {
        final BrowserPasskeyBridgeResponse response =
            await BrowserPasskeyBridge.start(
              passkeyOrigin: passkeyOrigin,
              apiBaseUrl: apiBaseUrl,
              mode: BrowserPasskeyBridgeMode.sensitive,
              accessToken: _apiClient.accessToken,
            );
        if (!response.verified) {
          throw ApiException(response.message ?? '浏览器未完成敏感验证');
        }
      } on SocketException catch (error) {
        throw ApiException(_passkeyBridgeSocketErrorMessage(error));
      }
    });
  }

  Future<void> verifySensitiveOperationWithPasskeyInBrowser() {
    return runBusyAction('正在等待 Passkey 验证...', () async {
      try {
        final BrowserPasskeyBridgeResponse response =
            await BrowserPasskeyBridge.start(
              passkeyOrigin: passkeyOrigin,
              apiBaseUrl: apiBaseUrl,
              mode: BrowserPasskeyBridgeMode.sensitive,
              accessToken: _apiClient.accessToken,
            );
        if (!response.verified) {
          throw ApiException(response.message ?? '浏览器未完成敏感验证');
        }
      } on SocketException catch (error) {
        throw ApiException(_passkeyBridgeSocketErrorMessage(error));
      }
    });
  }

  Future<void> registerPasskey({String? preferredName}) async {
    await runBusyAction('正在等待 Passkey 登记...', () async {
      if (usesNativePasskey) {
        if (!await PasskeyPlatform.isAvailable()) {
          throw ApiException(passkeyUnavailableMessage);
        }
        final String passkeyName = preferredName?.trim().isNotEmpty == true
            ? preferredName!.trim()
            : 'Ksuser Desktop';
        final PasskeyRegistrationOptions options =
            await getPasskeyRegistrationOptions(preferredName: passkeyName);
        final PasskeyRegistrationResult credential =
            await PasskeyPlatform.createCredential(
              options: options,
              origin: passkeyOrigin,
            );
        await _apiClient.post(
          '/auth/passkey/registration-verify',
          authorized: true,
          body: <String, dynamic>{
            ...credential.toJson(),
            'passkeyName': passkeyName,
          },
        );
        await refreshPasskeys();
        return;
      }

      if (!usesBrowserPasskeyBridge) {
        throw ApiException(passkeyUnavailableMessage);
      }
      if (!supportsBrowserPasskeyBridge) {
        throw ApiException(passkeyUnavailableMessage);
      }

      try {
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
      } on SocketException catch (error) {
        throw ApiException(_passkeyBridgeSocketErrorMessage(error));
      }
    });
  }

  static bool _isLoopbackBindDenied(SocketException error) {
    final int? code = error.osError?.errorCode;
    final String message = '${error.message} ${error.osError?.message ?? ''}'
        .toLowerCase();
    return code == 1 ||
        message.contains('operation not permitted') ||
        message.contains('failed to create server socket');
  }

  static String _passkeyBridgeSocketErrorMessage(SocketException error) {
    if (_isLoopbackBindDenied(error)) {
      return '当前环境禁止创建本地回调端口，无法使用浏览器 Passkey 桥接。'
          '请检查系统网络入站权限，或在 macOS 上启用原生 Passkey。';
    }
    return '无法创建本地 Passkey 回调服务：${error.message}';
  }

  Future<void> sendChangeEmailCode(String email) {
    return runBusyAction('正在发送邮箱验证码...', () {
      return _apiClient.post(
        '/auth/send-code',
        authorized: true,
        body: <String, dynamic>{'email': email.trim(), 'type': 'change-email'},
      );
    });
  }

  Future<void> changeEmail({
    required String newEmail,
    required String code,
  }) async {
    await runBusyAction('正在更新邮箱...', () async {
      final dynamic data = await _apiClient.post(
        '/auth/update/email',
        authorized: true,
        body: <String, dynamic>{
          'newEmail': newEmail.trim(),
          'code': code.trim(),
        },
      );
      final Map<String, dynamic> payload = asMap(data);
      if (user != null) {
        user = user!.copyWith(
          email: asString(payload['email']) ?? newEmail.trim(),
        );
      }
      notifyListeners();
    });
  }

  Future<void> changePassword(String newPassword) {
    return runBusyAction('正在更新密码...', () {
      return _apiClient.post(
        '/auth/update/password',
        authorized: true,
        body: <String, dynamic>{'newPassword': newPassword},
      );
    });
  }

  Future<void> revokeSession(int sessionId) async {
    await runBusyAction('正在撤销会话...', () async {
      await _apiClient.post(
        '/auth/sessions/$sessionId/revoke',
        authorized: true,
      );
      sessions = await _loadSessions();
      notifyListeners();
    });
  }

  Future<void> logoutAll() async {
    await runBusyAction('正在退出所有设备...', () async {
      await _apiClient.post('/auth/logout/all', authorized: true);
      await logout();
    });
  }

  Future<void> renamePasskey(int passkeyId, String name) async {
    await runBusyAction('正在重命名 Passkey...', () async {
      await _apiClient.put(
        '/auth/passkey/$passkeyId/rename',
        authorized: true,
        body: <String, dynamic>{'newName': name.trim()},
      );
      passkeys = await _loadPasskeys();
      notifyListeners();
    });
  }

  Future<void> deletePasskey(int passkeyId) async {
    await runBusyAction('正在删除 Passkey...', () async {
      await _apiClient.delete('/auth/passkey/$passkeyId', authorized: true);
      passkeys = await _loadPasskeys();
      notifyListeners();
    });
  }

  Future<TotpRegistrationOptionsResponse> getTotpRegistrationOptions() async {
    return runBusyAction('正在获取 TOTP 配置...', () async {
      final dynamic data = await _apiClient.post(
        '/auth/totp/registration-options',
        authorized: true,
      );
      return TotpRegistrationOptionsResponse.fromJson(asMap(data));
    });
  }

  Future<void> verifyTotpRegistration({
    required String code,
    required List<String> recoveryCodes,
  }) async {
    await runBusyAction('正在启用 TOTP...', () async {
      await _apiClient.post(
        '/auth/totp/registration-verify',
        authorized: true,
        body: <String, dynamic>{
          'code': code.trim(),
          'recoveryCodes': recoveryCodes,
        },
      );
      totpStatus = await _loadTotpStatus();
      notifyListeners();
    });
  }

  Future<List<String>> getRecoveryCodes() async {
    return runBusyAction('正在加载恢复码...', () async {
      final dynamic data = await _apiClient.get(
        '/auth/totp/recovery-codes',
        authorized: true,
      );
      return asList(data).map((dynamic item) => item.toString()).toList();
    });
  }

  Future<List<String>> regenerateRecoveryCodes() async {
    return runBusyAction('正在生成新的恢复码...', () async {
      final dynamic data = await _apiClient.post(
        '/auth/totp/recovery-codes/regenerate',
        authorized: true,
      );
      totpStatus = await _loadTotpStatus();
      notifyListeners();
      return asList(data).map((dynamic item) => item.toString()).toList();
    });
  }

  Future<void> disableTotp() async {
    await runBusyAction('正在关闭 TOTP...', () async {
      await _apiClient.post('/auth/totp/disable', authorized: true);
      totpStatus = await _loadTotpStatus();
      notifyListeners();
    });
  }

  Future<void> logout() async {
    await runBusyAction('正在退出登录...', () async {
      try {
        if (isAuthenticated) {
          await _apiClient.post('/auth/logout', authorized: true);
        }
      } catch (_) {
        // Ignore logout API failures and clear local session anyway.
      }
      _resetLocalSessionState();
      notifyListeners();
    });
  }

  Future<SessionTransferTicket> createSessionTransferTicket({
    required String target,
  }) async {
    return runBusyAction('正在生成跨端登录票据...', () async {
      if (!isAuthenticated) {
        throw ApiException('当前桌面端尚未登录');
      }
      final dynamic data = await _apiClient.post(
        '/auth/session-transfer/create',
        authorized: true,
        body: <String, dynamic>{'target': target},
      );
      return SessionTransferTicket.fromJson(asMap(data));
    });
  }

  Future<AccountRecoveryTicket> issueAccountRecoveryTicket() async {
    return runBusyAction('正在生成账号恢复授权...', () async {
      if (!isAuthenticated) {
        throw ApiException('当前桌面端尚未登录');
      }
      final dynamic data = await _apiClient.post(
        '/auth/account-recovery/issue',
        authorized: true,
      );
      return AccountRecoveryTicket.fromJson(asMap(data));
    });
  }

  Future<void> importSessionTransferTicket(String transferCode) async {
    await runBusyAction('正在同步网页登录状态...', () async {
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
    });
  }

  Future<QrLoginChallenge> initQrLogin() async {
    final dynamic data = await _apiClient.post(
      '/auth/qr/login/init',
      query: const <String, String>{'target': 'desktop'},
    );
    return QrLoginChallenge.fromJson(asMap(data));
  }

  Future<QrLoginChallenge> initQrSensitive() async {
    final dynamic data = await _apiClient.post(
      '/auth/qr/sensitive/init',
      authorized: true,
    );
    return QrLoginChallenge.fromJson(asMap(data));
  }

  Future<QrChallengeStatus> pollQrStatus({
    required String challengeId,
    required String pollToken,
  }) async {
    final dynamic data = await _apiClient.get(
      '/auth/qr/status',
      query: <String, String>{
        'challengeId': challengeId.trim(),
        'pollToken': pollToken.trim(),
      },
    );
    return QrChallengeStatus.fromJson(asMap(data));
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

  Future<AdaptiveAuthStatus> _loadAdaptiveAuthStatus() async {
    final dynamic data = await _apiClient.get(
      '/auth/adaptive-auth/status',
      authorized: true,
    );
    return AdaptiveAuthStatus.fromJson(asMap(data));
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

  Future<dynamic> postMultipartFile(
    String path, {
    required String fieldName,
    required String fileName,
    required String contentType,
    required Uint8List bytes,
    bool authorized = false,
    bool csrfRetried = false,
    bool tokenRetried = false,
  }) async {
    await _refreshCsrfToken();
    final String boundary = 'Ksuser-${DateTime.now().microsecondsSinceEpoch}';
    final String safeName = fileName.replaceAll(RegExp(r'["\\\r\n]'), '_');
    final BytesBuilder payload = BytesBuilder(copy: false)
      ..add(
        utf8.encode(
          '--$boundary\r\n'
          'Content-Disposition: form-data; name="$fieldName"; filename="$safeName"\r\n'
          'Content-Type: $contentType\r\n\r\n',
        ),
      )
      ..add(bytes)
      ..add(utf8.encode('\r\n--$boundary--\r\n'));

    final Uri baseUri = Uri.parse(
      baseUrl.endsWith('/') ? baseUrl : '$baseUrl/',
    );
    final Uri uri = baseUri.resolve(
      path.startsWith('/') ? path.substring(1) : path,
    );
    try {
      final HttpClientRequest request = await _httpClient.postUrl(uri);
      request.headers.set(HttpHeaders.userAgentHeader, _desktopUserAgent);
      request.headers.set(HttpHeaders.acceptHeader, 'application/json');
      request.headers.contentType = ContentType(
        'multipart',
        'form-data',
        parameters: <String, String>{'boundary': boundary},
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
      if (xsrf != null) request.headers.set('X-XSRF-TOKEN', xsrf.value);
      request.add(payload.takeBytes());

      final HttpClientResponse response = await request.close();
      _captureCookies(response);
      final String content = await response.transform(utf8.decoder).join();
      final dynamic decoded = content.isEmpty ? null : jsonDecode(content);
      final String? responseMessage = _extractMessage(decoded);
      if (response.statusCode == 401 &&
          _canAttemptTokenRefresh(
            path,
            authorized: authorized,
            tokenRetried: tokenRetried,
          )) {
        await _refreshAccessToken();
        return await postMultipartFile(
          path,
          fieldName: fieldName,
          fileName: fileName,
          contentType: contentType,
          bytes: bytes,
          authorized: authorized,
          csrfRetried: csrfRetried,
          tokenRetried: true,
        );
      }
      if (!csrfRetried &&
          response.statusCode == 403 &&
          responseMessage == '无权限') {
        await _refreshCsrfToken(force: true);
        return await postMultipartFile(
          path,
          fieldName: fieldName,
          fileName: fileName,
          contentType: contentType,
          bytes: bytes,
          authorized: authorized,
          csrfRetried: true,
          tokenRetried: tokenRetried,
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
        if (code != null && code >= 400) {
          throw ApiException(responseMessage ?? '请求失败', statusCode: code);
        }
        return decoded.containsKey('data') ? decoded['data'] : decoded;
      }
      return decoded;
    } on ApiException {
      rethrow;
    } on SocketException catch (error) {
      throw ApiException('网络连接失败：${error.message}');
    } catch (error) {
      throw ApiException('头像上传失败：$error');
    }
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
            return await _request(
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
        return await _request(
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
            return await _request(
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

class AccountRecoveryTicket {
  const AccountRecoveryTicket({
    required this.recoveryCode,
    required this.expiresInSeconds,
    required this.username,
    required this.maskedEmail,
    required this.sponsorClientName,
    required this.sponsorBrowser,
    required this.sponsorSystem,
    required this.sponsorIpLocation,
  });

  factory AccountRecoveryTicket.fromJson(Map<String, dynamic> json) {
    return AccountRecoveryTicket(
      recoveryCode: asString(json['recoveryCode']) ?? '',
      expiresInSeconds: asInt(json['expiresInSeconds']) ?? 0,
      username: asString(json['username']) ?? '',
      maskedEmail: asString(json['maskedEmail']) ?? '',
      sponsorClientName: asString(json['sponsorClientName']) ?? '',
      sponsorBrowser: asString(json['sponsorBrowser']) ?? '',
      sponsorSystem: asString(json['sponsorSystem']) ?? '',
      sponsorIpLocation: asString(json['sponsorIpLocation']) ?? '',
    );
  }

  final String recoveryCode;
  final int expiresInSeconds;
  final String username;
  final String maskedEmail;
  final String sponsorClientName;
  final String sponsorBrowser;
  final String sponsorSystem;
  final String sponsorIpLocation;
}

class QrLoginChallenge {
  const QrLoginChallenge({
    required this.challengeId,
    required this.pollToken,
    required this.qrText,
    required this.expiresInSeconds,
  });

  factory QrLoginChallenge.fromJson(Map<String, dynamic> json) {
    return QrLoginChallenge(
      challengeId: asString(json['challengeId']) ?? '',
      pollToken: asString(json['pollToken']) ?? '',
      qrText: asString(json['qrText']) ?? '',
      expiresInSeconds: asInt(json['expiresInSeconds']) ?? 0,
    );
  }

  final String challengeId;
  final String pollToken;
  final String qrText;
  final int expiresInSeconds;
}

class QrChallengeStatus {
  const QrChallengeStatus({
    required this.status,
    required this.expiresInSeconds,
    this.transferCode,
    this.mfaChallengeId,
    this.method,
    this.methods = const <String>[],
    this.verified = false,
  });

  factory QrChallengeStatus.fromJson(Map<String, dynamic> json) {
    return QrChallengeStatus(
      status: asString(json['status']) ?? 'expired',
      expiresInSeconds: asInt(json['expiresInSeconds']) ?? 0,
      transferCode: asString(json['transferCode']),
      mfaChallengeId: asString(json['mfaChallengeId']),
      method: asString(json['method']),
      methods: asList(
        json['methods'],
      ).map((dynamic item) => item.toString()).toList(),
      verified: asBool(json['verified']),
    );
  }

  final String status;
  final int expiresInSeconds;
  final String? transferCode;
  final String? mfaChallengeId;
  final String? method;
  final List<String> methods;
  final bool verified;
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
  LoginFactor _loginFactor = LoginFactor.password;
  MfaMode _mfaMode = MfaMode.code;
  bool _passkeyAvailable = false;

  @override
  void initState() {
    super.initState();
    _apiController.text = widget.controller.apiBaseUrl;
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
    super.dispose();
  }

  Future<void> _detectPasskeyAvailability() async {
    final bool available = await widget.controller.isPasskeyAvailable();
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
      _showError(widget.controller.passkeyUnavailableMessage);
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

  Future<void> _loginWithApple() async {
    if (!AppleLoginPlatform.isAvailable) {
      _showError('Apple 登录仅在 macOS 上提供');
      return;
    }
    widget.controller.updateApiBaseUrl(_apiController.text);
    try {
      await widget.controller.loginWithApple();
      if (mounted && widget.controller.pendingMfaChallenge == null) {
        _showSuccess('Apple 登录成功');
      } else if (mounted) {
        _syncMfaModeWithChallenge();
        _showSuccess('Apple 验证成功，请继续完成二次验证');
      }
    } catch (error) {
      _showError(error.toString());
    }
  }

  Future<void> _openLoginQrDialog() async {
    try {
      widget.controller.updateApiBaseUrl(_apiController.text);
      await showDesktopLoginQrDialog(context, widget.controller);
      if (!mounted) {
        return;
      }
      if (widget.controller.pendingMfaChallenge == null &&
          widget.controller.isAuthenticated) {
        _showSuccess('扫码登录成功');
      } else if (widget.controller.pendingMfaChallenge != null) {
        _syncMfaModeWithChallenge();
        _showSuccess('扫码验证成功，请继续完成二次验证');
      }
    } catch (error) {
      _showError(error.toString());
    }
  }

  void _showError(String message) {
    showAppMessage(context, message, error: true);
  }

  void _showSuccess(String message) {
    showAppMessage(context, message);
  }

  Uri _buildWebRegisterUri() {
    final String origin = widget.controller.passkeyOrigin.trim();
    final Uri baseUri = Uri.parse(origin.endsWith('/') ? origin : '$origin/');
    return baseUri
        .resolve('register')
        .replace(
          queryParameters: <String, String>{
            if (widget.controller.apiBaseUrl.trim().isNotEmpty)
              'apiBaseUrl': widget.controller.apiBaseUrl.trim(),
          },
        );
  }

  Future<void> _openWebRegister() async {
    try {
      widget.controller.updateApiBaseUrl(_apiController.text);
      await openExternalUrl(_buildWebRegisterUri().toString());
    } catch (error) {
      _showError(error.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints constraints) {
            final bool compactHeight = constraints.maxHeight < 700;
            final double outerPadding = compactHeight ? 16 : 28;
            final double cardPadding = compactHeight ? 20 : 28;
            final double sectionGap = compactHeight ? 10 : 14;
            final ThemeData theme = Theme.of(context);
            final Color outline = theme.brightness == Brightness.dark
                ? Colors.white.withValues(alpha: 0.08)
                : Colors.black.withValues(alpha: 0.06);

            final Widget authCard = AnimatedBuilder(
              animation: widget.controller,
              builder: (BuildContext context, _) {
                return Container(
                  width: 480,
                  padding: EdgeInsets.all(cardPadding),
                  decoration: BoxDecoration(
                    color: theme.cardTheme.color,
                    borderRadius: BorderRadius.circular(24),
                    border: Border.all(color: outline),
                    boxShadow: <BoxShadow>[
                      BoxShadow(
                        color: Colors.black.withValues(
                          alpha: theme.brightness == Brightness.dark
                              ? 0.12
                              : 0.05,
                        ),
                        blurRadius: 32,
                        offset: const Offset(0, 14),
                      ),
                    ],
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      Text(
                        '登录',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        '登录 Ksuser 安全中心，继续管理你的账户与设备。',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurface.withValues(
                            alpha: 0.62,
                          ),
                        ),
                      ),
                      SizedBox(height: sectionGap + 2),
                      SegmentedButton<LoginFactor>(
                        showSelectedIcon: false,
                        segments: const <ButtonSegment<LoginFactor>>[
                          ButtonSegment<LoginFactor>(
                            value: LoginFactor.password,
                            icon: Icon(Icons.password_rounded, size: 18),
                            label: Text('密码'),
                          ),
                          ButtonSegment<LoginFactor>(
                            value: LoginFactor.emailCode,
                            icon: Icon(Icons.mark_email_read_rounded, size: 18),
                            label: Text('邮箱验证码'),
                          ),
                        ],
                        selected: <LoginFactor>{_loginFactor},
                        onSelectionChanged: (Set<LoginFactor> selection) {
                          setState(() => _loginFactor = selection.first);
                        },
                      ),
                      SizedBox(height: sectionGap),
                      TextField(
                        controller: _loginEmailController,
                        textInputAction: _loginFactor == LoginFactor.password
                            ? TextInputAction.next
                            : TextInputAction.done,
                        decoration: const InputDecoration(
                          labelText: '邮箱地址',
                          prefixIcon: Icon(Icons.alternate_email_rounded),
                        ),
                      ),
                      SizedBox(height: sectionGap),
                      if (_loginFactor == LoginFactor.password)
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          onSubmitted: (_) => _login(),
                          decoration: const InputDecoration(
                            labelText: '密码',
                            prefixIcon: Icon(Icons.lock_outline_rounded),
                          ),
                        )
                      else
                        TextField(
                          controller: _codeController,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          onSubmitted: (_) => _login(),
                          decoration: InputDecoration(
                            labelText: '6 位邮箱验证码',
                            prefixIcon: const Icon(Icons.pin_outlined),
                            suffixIcon: TextButton(
                              onPressed: widget.controller.authBusy
                                  ? null
                                  : _sendLoginCode,
                              child: const Text('发送验证码'),
                            ),
                          ),
                        ),
                      SizedBox(height: sectionGap + 2),
                      SizedBox(
                        height: 48,
                        child: FilledButton.icon(
                          onPressed: widget.controller.authBusy ? null : _login,
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
                      if (AppleLoginPlatform.isAvailable) ...<Widget>[
                        SizedBox(height: sectionGap),
                        Row(
                          children: <Widget>[
                            Expanded(child: Divider(color: theme.dividerColor)),
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                              ),
                              child: Text(
                                '其他登录方式',
                                style: theme.textTheme.bodySmall,
                              ),
                            ),
                            Expanded(child: Divider(color: theme.dividerColor)),
                          ],
                        ),
                        SizedBox(height: sectionGap),
                        SizedBox(
                          width: double.infinity,
                          child: OutlinedButton.icon(
                            onPressed: widget.controller.authBusy
                                ? null
                                : _loginWithApple,
                            style: OutlinedButton.styleFrom(
                              minimumSize: const Size.fromHeight(44),
                              foregroundColor: theme.colorScheme.onSurface,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(14),
                              ),
                            ),
                            icon: const Icon(Icons.apple, size: 20),
                            label: const Text('使用 Apple 登录'),
                          ),
                        ),
                      ],
                      if (AppleLoginPlatform.isAvailable)
                        const SizedBox(height: 16),
                      Row(
                        children: <Widget>[
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed: widget.controller.authBusy
                                  ? null
                                  : _openLoginQrDialog,
                              style: OutlinedButton.styleFrom(
                                minimumSize: const Size.fromHeight(42),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(13),
                                ),
                              ),
                              icon: const Icon(Icons.qr_code_rounded, size: 18),
                              label: const Text('扫码登录'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: OutlinedButton.icon(
                              onPressed:
                                  !_passkeyAvailable ||
                                      widget.controller.authBusy
                                  ? null
                                  : _loginWithPasskey,
                              style: OutlinedButton.styleFrom(
                                minimumSize: const Size.fromHeight(42),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(13),
                                ),
                              ),
                              icon: const Icon(
                                Icons.fingerprint_rounded,
                                size: 19,
                              ),
                              label: const Text('Passkey'),
                            ),
                          ),
                        ],
                      ),
                      if (widget.controller.pendingMfaChallenge !=
                          null) ...<Widget>[
                        SizedBox(height: sectionGap + 2),
                        _MfaPanel(
                          challenge: widget.controller.pendingMfaChallenge!,
                          mode: _mfaMode,
                          codeController: _mfaCodeController,
                          recoveryController: _mfaRecoveryController,
                          busy: widget.controller.authBusy,
                          passkeyAvailable: _passkeyAvailable,
                          onModeChanged: (MfaMode mode) {
                            setState(() => _mfaMode = mode);
                          },
                          onSubmit: _completeMfa,
                        ),
                      ],
                      SizedBox(height: sectionGap),
                      TextButton(
                        onPressed: _openWebRegister,
                        child: const Text('还没有账号？前往网页端注册'),
                      ),
                    ],
                  ),
                );
              },
            );

            final Widget pageContent = Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    Image.asset(
                      kSidebarLogoAsset,
                      width: 42,
                      height: 42,
                      fit: BoxFit.contain,
                    ),
                    const SizedBox(width: 11),
                    Text(
                      kDesktopAppName,
                      style: theme.textTheme.titleLarge?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.3,
                      ),
                    ),
                  ],
                ),
                SizedBox(height: compactHeight ? 12 : 18),
                authCard,
              ],
            );

            return SingleChildScrollView(
              padding: EdgeInsets.all(outerPadding),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: constraints.maxHeight - outerPadding * 2,
                ),
                child: Center(child: pageContent),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _DesktopSidebarItem extends StatelessWidget {
  const _DesktopSidebarItem({
    required this.icon,
    required this.label,
    required this.selected,
    required this.extended,
    required this.isDark,
    required this.selectedColor,
    required this.unselectedColor,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final bool extended;
  final bool isDark;
  final Color selectedColor;
  final Color unselectedColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final Color activeSurface = kPrimaryColor.withValues(
      alpha: isDark ? 0.18 : 0.15,
    );
    final Color iconSurface = kPrimaryColor.withValues(
      alpha: isDark ? 0.20 : 0.18,
    );
    final Color activeIcon = isDark
        ? const Color(0xFFFFD35E)
        : const Color(0xFF795600);

    return Padding(
      padding: EdgeInsets.only(bottom: extended ? 5 : 7),
      child: Tooltip(
        message: extended ? '' : label,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(16),
            hoverColor: kPrimaryColor.withValues(alpha: 0.08),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 160),
              curve: Curves.easeOutCubic,
              height: extended ? 54 : 54,
              padding: EdgeInsets.symmetric(horizontal: extended ? 10 : 0),
              decoration: BoxDecoration(
                color: selected ? activeSurface : Colors.transparent,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: selected
                      ? kPrimaryColor.withValues(alpha: isDark ? 0.24 : 0.28)
                      : Colors.transparent,
                ),
              ),
              child: Row(
                mainAxisAlignment: extended
                    ? MainAxisAlignment.start
                    : MainAxisAlignment.center,
                children: <Widget>[
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 160),
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: selected
                          ? iconSurface
                          : (isDark
                                ? Colors.white.withValues(alpha: 0.045)
                                : Colors.white.withValues(alpha: 0.78)),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      icon,
                      size: 19,
                      color: selected ? activeIcon : unselectedColor,
                    ),
                  ),
                  if (extended) ...<Widget>[
                    const SizedBox(width: 11),
                    Expanded(
                      child: Text(
                        label,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          color: selected ? selectedColor : unselectedColor,
                          fontSize: 14,
                          fontWeight: selected
                              ? FontWeight.w700
                              : FontWeight.w600,
                          letterSpacing: 0.1,
                        ),
                      ),
                    ),
                    if (selected)
                      Icon(
                        Icons.chevron_right_rounded,
                        size: 18,
                        color: activeIcon.withValues(alpha: 0.8),
                      ),
                  ],
                ],
              ),
            ),
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
        ? const Color(0xFF202124)
        : const Color(0xFFF8F6F0);
    final Color sidebarBorder = isDark
        ? Colors.white.withValues(alpha: 0.07)
        : const Color(0xFFE9E4D8);
    final Color sidebarTitleColor = isDark
        ? Colors.white
        : const Color(0xFF2F281C);
    final Color sidebarSubtitleColor = isDark
        ? Colors.white70
        : const Color(0xFF6B614E);
    final Color railSelectedLabelColor = isDark
        ? const Color(0xFFFFE8AD)
        : const Color(0xFF4B390B);
    final Color railUnselectedColor = isDark
        ? Colors.white70
        : const Color(0xFF756A55);
    final bool supportsProtectedMobileBridge =
        LocalAuthPlatform.supportsProtectedActions;
    final bool supportsMoveToMenuBar =
        DesktopWindowPlatform.supportsMoveToMenuBar;

    return Scaffold(
      body: SafeArea(
        child: Row(
          children: <Widget>[
            LayoutBuilder(
              builder: (BuildContext context, BoxConstraints constraints) {
                final bool extended = MediaQuery.of(context).size.width > 1240;
                final double railExtendedWidth = 286;
                final double railContentWidth = 230;
                final double railWidth = extended ? railExtendedWidth : 88;

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
                      child: Column(
                        children: <Widget>[
                          Padding(
                            padding: const EdgeInsets.fromLTRB(18, 18, 18, 8),
                            child: SizedBox(
                              width: extended ? railContentWidth : 48,
                              child: extended
                                  ? Column(
                                      mainAxisSize: MainAxisSize.min,
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: <Widget>[
                                        Row(
                                          children: <Widget>[
                                            Container(
                                              padding: const EdgeInsets.all(3),
                                              decoration: BoxDecoration(
                                                color: isDark
                                                    ? Colors.white.withValues(
                                                        alpha: 0.08,
                                                      )
                                                    : Colors.white,
                                                borderRadius:
                                                    BorderRadius.circular(17),
                                                boxShadow: <BoxShadow>[
                                                  BoxShadow(
                                                    color: Colors.black
                                                        .withValues(
                                                          alpha: isDark
                                                              ? 0.12
                                                              : 0.06,
                                                        ),
                                                    blurRadius: 10,
                                                    offset: const Offset(0, 3),
                                                  ),
                                                ],
                                              ),
                                              child: ClipRRect(
                                                borderRadius:
                                                    BorderRadius.circular(14),
                                                child: Image.asset(
                                                  kSidebarLogoAsset,
                                                  width: 42,
                                                  height: 42,
                                                  fit: BoxFit.cover,
                                                ),
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
                                                    kDesktopAppName,
                                                    maxLines: 1,
                                                    overflow:
                                                        TextOverflow.ellipsis,
                                                    style: TextStyle(
                                                      color: sidebarTitleColor,
                                                      fontWeight:
                                                          FontWeight.w800,
                                                      fontSize: 20,
                                                    ),
                                                  ),
                                                  const SizedBox(height: 2),
                                                  Text(
                                                    '账户安全控制台',
                                                    style: TextStyle(
                                                      color:
                                                          sidebarSubtitleColor,
                                                      fontSize: 11,
                                                      fontWeight:
                                                          FontWeight.w500,
                                                      letterSpacing: 0.15,
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ],
                                        ),
                                        const SizedBox(height: 18),
                                        Container(
                                          height: 1,
                                          width: double.infinity,
                                          color: sidebarBorder,
                                        ),
                                        const SizedBox(height: 13),
                                        Row(
                                          children: <Widget>[
                                            Icon(
                                              Icons.grid_view_rounded,
                                              size: 15,
                                              color: sidebarSubtitleColor,
                                            ),
                                            const SizedBox(width: 7),
                                            Text(
                                              '工作台',
                                              style: TextStyle(
                                                color: sidebarSubtitleColor,
                                                fontSize: 11,
                                                fontWeight: FontWeight.w700,
                                                letterSpacing: 0.3,
                                              ),
                                            ),
                                          ],
                                        ),
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
                          Expanded(
                            child: ListView(
                              padding: EdgeInsets.fromLTRB(
                                extended ? 12 : 17,
                                8,
                                extended ? 12 : 17,
                                10,
                              ),
                              children: <Widget>[
                                _DesktopSidebarItem(
                                  icon: Icons.dashboard_rounded,
                                  label: '账号总览',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.overview,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.overview,
                                  ),
                                ),
                                _DesktopSidebarItem(
                                  icon: Icons.badge_outlined,
                                  label: '账号资料',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.profile,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.profile,
                                  ),
                                ),
                                _DesktopSidebarItem(
                                  icon: Icons.lock_outline_rounded,
                                  label: '安全设置',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.security,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.security,
                                  ),
                                ),
                                _DesktopSidebarItem(
                                  icon: Icons.devices_other_outlined,
                                  label: '设备管理',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.devices,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.devices,
                                  ),
                                ),
                                _DesktopSidebarItem(
                                  icon: Icons.history_rounded,
                                  label: '操作日志',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.activity,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.activity,
                                  ),
                                ),
                                _DesktopSidebarItem(
                                  icon: Icons.admin_panel_settings_outlined,
                                  label: '访问授权',
                                  selected:
                                      controller.selectedSection ==
                                      DesktopSection.authorizations,
                                  extended: extended,
                                  isDark: isDark,
                                  selectedColor: railSelectedLabelColor,
                                  unselectedColor: railUnselectedColor,
                                  onTap: () => controller.setSection(
                                    DesktopSection.authorizations,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.fromLTRB(14, 8, 14, 16),
                            child: SizedBox(
                              width: extended ? railContentWidth : 48,
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                mainAxisAlignment: MainAxisAlignment.end,
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: <Widget>[
                                  if (extended)
                                    Row(
                                      children: <Widget>[
                                        Icon(
                                          Icons.devices_rounded,
                                          size: 15,
                                          color: sidebarSubtitleColor,
                                        ),
                                        const SizedBox(width: 7),
                                        Text(
                                          '跨端联通',
                                          style: TextStyle(
                                            color: sidebarSubtitleColor,
                                            fontSize: 11,
                                            fontWeight: FontWeight.w700,
                                            letterSpacing: 0.2,
                                          ),
                                        ),
                                      ],
                                    ),
                                  if (extended) const SizedBox(height: 10),
                                  if (extended && supportsProtectedMobileBridge)
                                    FilledButton.icon(
                                      onPressed: () async {
                                        try {
                                          await showMobileBridgeQrDialog(
                                            context,
                                            controller,
                                          );
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
                                      icon: const Icon(Icons.qr_code_rounded),
                                      label: const Text('手机扫码登录'),
                                    ),
                                  if (!extended)
                                    Column(
                                      children: <Widget>[
                                        if (supportsProtectedMobileBridge) ...<
                                          Widget
                                        >[
                                          Tooltip(
                                            message: '手机扫码登录',
                                            child: IconButton.filled(
                                              onPressed: () async {
                                                try {
                                                  await showMobileBridgeQrDialog(
                                                    context,
                                                    controller,
                                                  );
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
                                              icon: const Icon(
                                                Icons.qr_code_rounded,
                                              ),
                                            ),
                                          ),
                                          const SizedBox(height: 8),
                                        ],
                                        Tooltip(
                                          message: '打开网页端并自动登录',
                                          child: IconButton.filledTonal(
                                            onPressed: () async {
                                              try {
                                                await controller.runBusyAction(
                                                  '正在打开网页端...',
                                                  () async {
                                                    final SessionTransferTicket
                                                    ticket = await controller
                                                        .createSessionTransferTicket(
                                                          target: 'web',
                                                        );
                                                    final Uri
                                                    baseUri = Uri.parse(
                                                      controller.passkeyOrigin
                                                              .endsWith('/')
                                                          ? controller
                                                                .passkeyOrigin
                                                          : '${controller.passkeyOrigin}/',
                                                    );
                                                    final Uri
                                                    launchUri = baseUri
                                                        .resolve('login')
                                                        .replace(
                                                          queryParameters:
                                                              <String, String>{
                                                                'transferCode':
                                                                    ticket
                                                                        .transferCode,
                                                                'from':
                                                                    'desktop',
                                                                'apiBaseUrl':
                                                                    controller
                                                                        .apiBaseUrl,
                                                              },
                                                        );
                                                    await openExternalUrl(
                                                      launchUri.toString(),
                                                    );
                                                  },
                                                );
                                                if (context.mounted) {
                                                  showAppMessage(
                                                    context,
                                                    '已在浏览器打开网页端并自动登录',
                                                  );
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
                                            icon: const Icon(
                                              Icons.open_in_browser_rounded,
                                            ),
                                          ),
                                        ),
                                        const SizedBox(height: 8),
                                        Tooltip(
                                          message: '通过网页登录同步回桌面端',
                                          child: IconButton.filledTonal(
                                            onPressed: () async {
                                              try {
                                                await controller.runBusyAction(
                                                  '正在打开网页登录页...',
                                                  () async {
                                                    final Uri
                                                    baseUri = Uri.parse(
                                                      controller.passkeyOrigin
                                                              .endsWith('/')
                                                          ? controller
                                                                .passkeyOrigin
                                                          : '${controller.passkeyOrigin}/',
                                                    );
                                                    final Uri
                                                    launchUri = baseUri
                                                        .resolve('login')
                                                        .replace(
                                                          queryParameters:
                                                              <String, String>{
                                                                'desktopBridge':
                                                                    '1',
                                                                'from':
                                                                    'desktop',
                                                                'apiBaseUrl':
                                                                    controller
                                                                        .apiBaseUrl,
                                                              },
                                                        );
                                                    await openExternalUrl(
                                                      launchUri.toString(),
                                                    );
                                                  },
                                                );
                                                if (context.mounted) {
                                                  showAppMessage(
                                                    context,
                                                    '已打开网页登录页；网页登录成功后会自动同步回桌面端',
                                                  );
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
                                            icon: const Icon(
                                              Icons.sync_alt_rounded,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  if (extended) const SizedBox(height: 8),
                                  if (extended)
                                    FilledButton.tonalIcon(
                                      onPressed: () async {
                                        try {
                                          await controller.runBusyAction(
                                            '正在打开网页端...',
                                            () async {
                                              final SessionTransferTicket
                                              ticket = await controller
                                                  .createSessionTransferTicket(
                                                    target: 'web',
                                                  );
                                              final Uri baseUri = Uri.parse(
                                                controller.passkeyOrigin
                                                        .endsWith('/')
                                                    ? controller.passkeyOrigin
                                                    : '${controller.passkeyOrigin}/',
                                              );
                                              final Uri launchUri = baseUri
                                                  .resolve('login')
                                                  .replace(
                                                    queryParameters:
                                                        <String, String>{
                                                          'transferCode': ticket
                                                              .transferCode,
                                                          'from': 'desktop',
                                                          'apiBaseUrl':
                                                              controller
                                                                  .apiBaseUrl,
                                                        },
                                                  );
                                              await openExternalUrl(
                                                launchUri.toString(),
                                              );
                                            },
                                          );
                                          if (context.mounted) {
                                            showAppMessage(
                                              context,
                                              '已在浏览器打开网页端并自动登录',
                                            );
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
                                      icon: const Icon(
                                        Icons.open_in_browser_rounded,
                                      ),
                                      label: const Text('打开网页端'),
                                    ),
                                  if (extended) const SizedBox(height: 8),
                                  if (extended)
                                    FilledButton.tonalIcon(
                                      onPressed: () async {
                                        try {
                                          await controller.runBusyAction(
                                            '正在打开网页登录页...',
                                            () async {
                                              final Uri baseUri = Uri.parse(
                                                controller.passkeyOrigin
                                                        .endsWith('/')
                                                    ? controller.passkeyOrigin
                                                    : '${controller.passkeyOrigin}/',
                                              );
                                              final Uri launchUri = baseUri
                                                  .resolve('login')
                                                  .replace(
                                                    queryParameters:
                                                        <String, String>{
                                                          'desktopBridge': '1',
                                                          'from': 'desktop',
                                                          'apiBaseUrl':
                                                              controller
                                                                  .apiBaseUrl,
                                                        },
                                                  );
                                              await openExternalUrl(
                                                launchUri.toString(),
                                              );
                                            },
                                          );
                                          if (context.mounted) {
                                            showAppMessage(
                                              context,
                                              '已打开网页登录页；网页登录成功后会自动同步回桌面端',
                                            );
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
                                      icon: const Icon(Icons.sync_alt_rounded),
                                      label: const Text('网页登录同步'),
                                    ),
                                  const SizedBox(height: 12),
                                  if (extended)
                                    Container(
                                      height: 1,
                                      margin: const EdgeInsets.only(bottom: 12),
                                      color: sidebarBorder,
                                    ),
                                  if (extended)
                                    Row(
                                      children: <Widget>[
                                        Icon(
                                          Icons.manage_accounts_rounded,
                                          size: 15,
                                          color: sidebarSubtitleColor,
                                        ),
                                        const SizedBox(width: 7),
                                        Text(
                                          '账户操作',
                                          style: TextStyle(
                                            color: sidebarSubtitleColor,
                                            fontSize: 11,
                                            fontWeight: FontWeight.w700,
                                            letterSpacing: 0.2,
                                          ),
                                        ),
                                      ],
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
                                        foregroundColor:
                                            theme.colorScheme.error,
                                        backgroundColor: theme.colorScheme.error
                                            .withValues(
                                              alpha: isDark ? 0.16 : 0.09,
                                            ),
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 14,
                                          vertical: 12,
                                        ),
                                        shape: RoundedRectangleBorder(
                                          borderRadius: BorderRadius.circular(
                                            16,
                                          ),
                                        ),
                                      ),
                                    )
                                  else
                                    IconButton.filledTonal(
                                      onPressed: () async {
                                        await controller.logout();
                                      },
                                      style: IconButton.styleFrom(
                                        foregroundColor:
                                            theme.colorScheme.error,
                                        backgroundColor: theme.colorScheme.error
                                            .withValues(
                                              alpha: isDark ? 0.16 : 0.09,
                                            ),
                                      ),
                                      icon: const Icon(Icons.logout_rounded),
                                    ),
                                ],
                              ),
                            ),
                          ),
                        ],
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
                                  Row(
                                    children: <Widget>[
                                      Container(
                                        width: 38,
                                        height: 38,
                                        decoration: BoxDecoration(
                                          color: kPrimaryColor.withValues(
                                            alpha: isDark ? 0.18 : 0.16,
                                          ),
                                          borderRadius: BorderRadius.circular(
                                            12,
                                          ),
                                        ),
                                        child: Icon(
                                          sectionIcon(
                                            controller.selectedSection,
                                          ),
                                          color: isDark
                                              ? kPrimaryColor
                                              : const Color(0xFF755400),
                                          size: 21,
                                        ),
                                      ),
                                      const SizedBox(width: 12),
                                      Text(
                                        sectionTitle(
                                          controller.selectedSection,
                                        ),
                                        style: theme.textTheme.headlineSmall
                                            ?.copyWith(
                                              fontWeight: FontWeight.w700,
                                              letterSpacing: -0.35,
                                            ),
                                      ),
                                    ],
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
                            if (supportsMoveToMenuBar) ...<Widget>[
                              const SizedBox(width: 8),
                              IconButton(
                                tooltip: '收起到菜单栏',
                                onPressed: () async {
                                  try {
                                    await DesktopWindowPlatform.moveToMenuBar();
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
                                icon: const Icon(
                                  Icons.vertical_align_top_rounded,
                                ),
                              ),
                            ],
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
                              : AnimatedSwitcher(
                                  duration: controller.reduceMotion
                                      ? Duration.zero
                                      : const Duration(milliseconds: 180),
                                  switchInCurve: Curves.easeOutCubic,
                                  switchOutCurve: Curves.easeInCubic,
                                  child: KeyedSubtree(
                                    key: ValueKey<DesktopSection>(
                                      controller.selectedSection,
                                    ),
                                    child: _buildSection(context),
                                  ),
                                ),
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
      case DesktopSection.authorizations:
        return AuthorizationsPage(controller: controller);
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

    final Widget profileAndMethods = _OverviewSplit(
      left: _SectionCard(
        title: '资料摘要',
        subtitle: '账户资料一览',
        child: Wrap(
          spacing: 12,
          runSpacing: 12,
          children: <Widget>[
            _InfoChip(label: 'UUID', value: user.uuid),
            _InfoChip(label: '真实姓名', value: user.realName ?? '未填写'),
            _InfoChip(label: '地区', value: user.region ?? '未填写'),
            _InfoChip(label: '性别', value: displayGender(user.gender)),
            _InfoChip(label: '资料更新时间', value: formatDateTime(user.updatedAt)),
          ],
        ),
      ),
      right: _SectionCard(
        title: '登录方式',
        subtitle: '当前可用的认证方式',
        child: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: methods
              .map(
                (String item) => Chip(
                  avatar: const Icon(Icons.check_circle_rounded, size: 17),
                  label: Text(item),
                  visualDensity: VisualDensity.compact,
                  side: BorderSide.none,
                  backgroundColor: Theme.of(
                    context,
                  ).colorScheme.surfaceContainerHighest,
                ),
              )
              .toList(),
        ),
      ),
    );
    final Widget activity = _OverviewSplit(
      left: _SectionCard(
        title: '近期设备活动',
        subtitle: '最近登录的设备与会话',
        child: controller.sessions.isEmpty
            ? const _OverviewEmptyState(
                icon: Icons.devices_other_rounded,
                message: '暂无设备活动',
              )
            : Column(
                children: controller.sessions
                    .take(4)
                    .map(
                      (SessionItem item) =>
                          _SessionRow(item: item, compact: true),
                    )
                    .toList(),
              ),
      ),
      right: _SectionCard(
        title: '近期敏感操作',
        subtitle: '账户安全相关的最近记录',
        child: controller.sensitiveLogs.isEmpty
            ? const _OverviewEmptyState(
                icon: Icons.fact_check_outlined,
                message: '暂无敏感操作记录',
              )
            : Column(
                children: controller.sensitiveLogs
                    .take(4)
                    .map(
                      (SensitiveLogItem item) =>
                          _LogTile(log: item, compact: true),
                    )
                    .toList(),
              ),
      ),
    );

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double width = constraints.maxWidth;
        final int metricColumns = (width / 250).floor().clamp(1, 4);
        final double metricWidth =
            (width - (metricColumns - 1) * 14) / metricColumns;
        return SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Wrap(
                spacing: 14,
                runSpacing: 14,
                children: <Widget>[
                  _MetricCard(
                    width: metricWidth,
                    title: '账户身份',
                    value: user.username,
                    caption: user.email,
                    icon: Icons.person_outline_rounded,
                  ),
                  _MetricCard(
                    width: metricWidth,
                    title: '安全评分',
                    value: '$securityScore%',
                    caption: controller.totpStatus?.enabled == true
                        ? '已启用 TOTP 防护'
                        : '建议开启 TOTP 防护',
                    icon: Icons.shield_moon_outlined,
                    progress: securityScore / 100,
                  ),
                  _MetricCard(
                    width: metricWidth,
                    title: '在线设备',
                    value: '${controller.sessions.length}',
                    caption:
                        '${controller.sessions.where((SessionItem item) => item.online).length} 台当前在线',
                    icon: Icons.devices_other_rounded,
                  ),
                  _MetricCard(
                    width: metricWidth,
                    title: '敏感日志',
                    value: '${controller.sensitiveLogs.length}',
                    caption: '近期账户安全操作',
                    icon: Icons.rule_folder_outlined,
                  ),
                ],
              ),
              const SizedBox(height: 18),
              profileAndMethods,
              const SizedBox(height: 18),
              activity,
            ],
          ),
        );
      },
    );
  }
}

class AuthorizationsPage extends StatelessWidget {
  const AuthorizationsPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          _SectionCard(
            title: '已授权应用',
            subtitle: '查看可访问您账号信息的第三方应用及授权范围。',
            child: Column(
              children: <Widget>[
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        '${controller.authorizedApps.length} 个应用',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    OutlinedButton.icon(
                      onPressed: controller.authorizationsLoading
                          ? null
                          : () => controller.refreshAuthorizations(),
                      icon: controller.authorizationsLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.refresh_rounded, size: 18),
                      label: const Text('刷新'),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                if (controller.authorizationsError != null)
                  _InlineStateCard(
                    icon: Icons.error_outline_rounded,
                    title: '暂时无法获取授权信息',
                    message: controller.authorizationsError!,
                    action: TextButton.icon(
                      onPressed: () => controller.refreshAuthorizations(),
                      icon: const Icon(Icons.refresh_rounded),
                      label: const Text('重试'),
                    ),
                  )
                else if (controller.authorizationsLoading &&
                    controller.authorizedApps.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(36),
                    child: Center(child: CircularProgressIndicator()),
                  )
                else if (controller.authorizedApps.isEmpty)
                  const _InlineStateCard(
                    icon: Icons.verified_user_outlined,
                    title: '还没有授权应用',
                    message: '您确认授权的第三方应用会显示在这里。',
                  )
                else
                  Column(
                    children: controller.authorizedApps
                        .map(
                          (OAuth2AuthorizedApp app) => _AuthorizedAppTile(
                            app: app,
                            controller: controller,
                          ),
                        )
                        .toList(),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          _SectionCard(
            title: '授权说明',
            subtitle: '您可以随时在网页端访问授权页面管理第三方应用。',
            child: const Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Icon(Icons.info_outline_rounded, size: 20),
                SizedBox(width: 10),
                Expanded(child: Text('撤销授权后，该应用将无法继续使用现有授权访问您的账户信息。')),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AuthorizedAppTile extends StatelessWidget {
  const _AuthorizedAppTile({required this.app, required this.controller});

  final OAuth2AuthorizedApp app;
  final AppController controller;

  Future<void> _confirmRevoke(BuildContext context) async {
    final bool confirmed =
        await showDialog<bool>(
          context: context,
          builder: (BuildContext dialogContext) {
            final Color errorColor = Theme.of(dialogContext).colorScheme.error;
            return AlertDialog(
              icon: Icon(Icons.link_off_rounded, color: errorColor),
              title: Text('取消「${app.appName}」的授权？'),
              content: Text('取消后，该应用将无法继续访问您已授权的账号信息。若再次使用，需要重新确认授权。'),
              actions: <Widget>[
                TextButton(
                  onPressed: () => Navigator.of(dialogContext).pop(false),
                  child: const Text('保留授权'),
                ),
                FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: errorColor,
                    foregroundColor: Theme.of(
                      dialogContext,
                    ).colorScheme.onError,
                  ),
                  onPressed: () => Navigator.of(dialogContext).pop(true),
                  icon: const Icon(Icons.link_off_rounded, size: 18),
                  label: const Text('确认取消'),
                ),
              ],
            );
          },
        ) ??
        false;
    if (!confirmed || !context.mounted) {
      return;
    }
    try {
      await controller.revokeAuthorization(app.appId);
      if (context.mounted) {
        showAppMessage(context, '已取消「${app.appName}」的授权');
      }
    } catch (error) {
      if (context.mounted) {
        showAppMessage(context, error.toString(), error: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final Color border = theme.colorScheme.outlineVariant.withValues(
      alpha: 0.55,
    );
    final String expiry = app.expiresAt == null
        ? (app.grantMode == 'ONE_TIME' ? '单次授权' : '长期有效')
        : '有效期至 ${formatDateTime(app.expiresAt)}';
    final String developer = app.creatorName?.isNotEmpty == true
        ? '开发者：${app.creatorName}'
        : app.contactInfo.isNotEmpty
        ? '联系信息：${app.contactInfo}'
        : app.redirectUri;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              ClipRRect(
                borderRadius: BorderRadius.circular(13),
                child: app.logoUrl == null || app.logoUrl!.isEmpty
                    ? Container(
                        width: 48,
                        height: 48,
                        color: kPrimaryColor.withValues(alpha: 0.18),
                        child: const Icon(
                          Icons.apps_rounded,
                          color: Color(0xFF755400),
                        ),
                      )
                    : Image.network(
                        app.logoUrl!,
                        width: 48,
                        height: 48,
                        fit: BoxFit.cover,
                        errorBuilder: (context, error, stackTrace) => Container(
                          width: 48,
                          height: 48,
                          color: kPrimaryColor.withValues(alpha: 0.18),
                          child: const Icon(
                            Icons.apps_rounded,
                            color: Color(0xFF755400),
                          ),
                        ),
                      ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      app.appName,
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      developer,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              _StatusPill(
                label: grantModeLabel(app.grantMode),
                icon: Icons.verified_rounded,
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: app.scopes
                .map((String scope) => _ScopePill(label: scopeLabel(scope)))
                .toList(),
          ),
          const SizedBox(height: 12),
          Divider(height: 1, color: border),
          const SizedBox(height: 10),
          Wrap(
            spacing: 18,
            runSpacing: 6,
            children: <Widget>[
              _MetadataLine(
                icon: Icons.schedule_rounded,
                label: '最近使用 ${formatDateTime(app.lastAuthorizedAt)}',
              ),
              _MetadataLine(icon: Icons.timer_outlined, label: expiry),
              OutlinedButton.icon(
                onPressed: controller.revokingAuthorizationId == null
                    ? () => _confirmRevoke(context)
                    : null,
                style: OutlinedButton.styleFrom(
                  foregroundColor: theme.colorScheme.error,
                  side: BorderSide(
                    color: theme.colorScheme.error.withValues(alpha: 0.45),
                  ),
                  minimumSize: const Size(0, 38),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 8,
                  ),
                ),
                icon: controller.revokingAuthorizationId == app.appId
                    ? SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: theme.colorScheme.error,
                        ),
                      )
                    : const Icon(Icons.link_off_rounded, size: 17),
                label: const Text('取消授权'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.icon});
  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final Color color = Theme.of(context).colorScheme.primary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(icon, size: 14, color: color),
          const SizedBox(width: 5),
          Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ScopePill extends StatelessWidget {
  const _ScopePill({required this.label});
  final String label;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Theme.of(
          context,
        ).colorScheme.surfaceContainerHighest.withValues(alpha: 0.55),
        borderRadius: BorderRadius.circular(9),
      ),
      child: Text(label, style: Theme.of(context).textTheme.labelMedium),
    );
  }
}

class _MetadataLine extends StatelessWidget {
  const _MetadataLine({required this.icon, required this.label});
  final IconData icon;
  final String label;
  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: <Widget>[
      Icon(
        icon,
        size: 15,
        color: Theme.of(context).colorScheme.onSurfaceVariant,
      ),
      const SizedBox(width: 6),
      Text(label, style: Theme.of(context).textTheme.bodySmall),
    ],
  );
}

String grantModeLabel(String value) => value == 'TIME_LIMITED'
    ? '限时授权'
    : value == 'ONE_TIME'
    ? '单次授权'
    : '永久授权';

String scopeLabel(String scope) {
  switch (scope) {
    case 'openid':
      return '账号标识';
    case 'profile':
      return '基本资料';
    case 'email':
      return '邮箱';
    case 'phone':
      return '手机号';
    default:
      return scope;
  }
}

class _ProfileAvatarFallback extends StatelessWidget {
  const _ProfileAvatarFallback({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final String initial = name.trim().isEmpty
        ? 'K'
        : name.trim().characters.first.toUpperCase();
    return Container(
      color: colors.primary.withValues(alpha: 0.14),
      alignment: Alignment.center,
      child: Text(
        initial,
        style: TextStyle(
          color: colors.primary,
          fontSize: 34,
          fontWeight: FontWeight.w700,
        ),
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
    Future<void> changeAvatar() async {
      try {
        const XTypeGroup imageTypes = XTypeGroup(
          label: '图片',
          extensions: <String>['jpg', 'jpeg', 'png', 'webp', 'gif'],
          uniformTypeIdentifiers: <String>['public.image'],
        );
        final XFile? image = await openFile(
          acceptedTypeGroups: <XTypeGroup>[imageTypes],
          confirmButtonText: '选择头像',
        );
        if (image == null) return;
        final int size = await image.length();
        if (size > 3 * 1024 * 1024) {
          if (context.mounted) {
            showAppMessage(context, '图片大小不能超过 3MB', error: true);
          }
          return;
        }
        final String extension = image.name.split('.').last.toLowerCase();
        const Map<String, String> mimeTypes = <String, String>{
          'jpg': 'image/jpeg',
          'jpeg': 'image/jpeg',
          'png': 'image/png',
          'webp': 'image/webp',
          'gif': 'image/gif',
        };
        final String? contentType = mimeTypes[extension];
        if (contentType == null) {
          if (context.mounted) {
            showAppMessage(context, '仅支持 JPG、PNG、WebP 或 GIF 图片', error: true);
          }
          return;
        }
        await controller.uploadAvatar(
          bytes: await image.readAsBytes(),
          fileName: image.name,
          contentType: contentType,
        );
        if (context.mounted) showAppMessage(context, '头像已更新');
      } catch (error) {
        if (context.mounted) {
          showAppMessage(context, error.toString(), error: true);
        }
      }
    }

    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '个人信息',
            subtitle: '维护您的基础资料，让账号信息保持准确、完整。',
            child: Column(
              children: <Widget>[
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 16),
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surfaceContainerHighest
                        .withValues(alpha: 0.30),
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(
                      color: Theme.of(
                        context,
                      ).colorScheme.outlineVariant.withValues(alpha: 0.48),
                    ),
                  ),
                  child: Row(
                    children: <Widget>[
                      Tooltip(
                        message: '点击更换头像',
                        child: InkWell(
                          onTap: changeAvatar,
                          customBorder: const CircleBorder(),
                          child: SizedBox(
                            width: 88,
                            height: 88,
                            child: Stack(
                              children: <Widget>[
                                Positioned.fill(
                                  child: ClipOval(
                                    child: user.avatarUrl?.isNotEmpty == true
                                        ? Image.network(
                                            user.avatarUrl!,
                                            fit: BoxFit.cover,
                                            errorBuilder:
                                                (context, error, stackTrace) =>
                                                    _ProfileAvatarFallback(
                                                      name: user.username,
                                                    ),
                                          )
                                        : _ProfileAvatarFallback(
                                            name: user.username,
                                          ),
                                  ),
                                ),
                                Positioned(
                                  right: 0,
                                  bottom: 0,
                                  child: Container(
                                    width: 28,
                                    height: 28,
                                    decoration: BoxDecoration(
                                      color: Theme.of(
                                        context,
                                      ).colorScheme.primary,
                                      shape: BoxShape.circle,
                                      border: Border.all(
                                        color: Theme.of(
                                          context,
                                        ).colorScheme.surface,
                                        width: 2,
                                      ),
                                    ),
                                    child: Icon(
                                      Icons.camera_alt_rounded,
                                      size: 14,
                                      color: Theme.of(
                                        context,
                                      ).colorScheme.onPrimary,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 18),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: <Widget>[
                            Text(
                              user.username,
                              style: Theme.of(context).textTheme.titleLarge
                                  ?.copyWith(
                                    fontWeight: FontWeight.w700,
                                    letterSpacing: -0.3,
                                  ),
                            ),
                            const SizedBox(height: 5),
                            Text(
                              user.email,
                              style: Theme.of(context).textTheme.bodyMedium
                                  ?.copyWith(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.onSurfaceVariant,
                                  ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              '点击头像更换 · JPG、PNG、WebP 或 GIF · 最大 3MB',
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(
                                    color: Theme.of(
                                      context,
                                    ).colorScheme.onSurfaceVariant,
                                  ),
                            ),
                          ],
                        ),
                      ),
                      OutlinedButton.icon(
                        onPressed: changeAvatar,
                        icon: const Icon(Icons.photo_camera_outlined, size: 18),
                        label: const Text('更换头像'),
                      ),
                    ],
                  ),
                ),
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
                              initialValue:
                                  settings.preferredMfaMethod == 'totp' ||
                                      settings.preferredMfaMethod == 'passkey'
                                  ? settings.preferredMfaMethod
                                  : null,
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
                              initialValue:
                                  settings.preferredSensitiveMethod ==
                                          'password' ||
                                      settings.preferredSensitiveMethod ==
                                          'email-code' ||
                                      settings.preferredSensitiveMethod ==
                                          'passkey' ||
                                      settings.preferredSensitiveMethod ==
                                          'totp'
                                  ? settings.preferredSensitiveMethod
                                  : null,
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
                      const SizedBox(height: 10),
                      FilledButton.tonalIcon(
                        onPressed: () => _runWithSensitiveVerification(
                          context,
                          controller: controller,
                          action: () =>
                              showAccountRecoveryDialog(context, controller),
                        ),
                        icon: const Icon(Icons.qr_code_2_rounded),
                        label: const Text('发起账号恢复'),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '为另一台设备生成 5 分钟有效的一次性恢复二维码和恢复码，恢复成功后旧会话会自动撤销。',
                        style: TextStyle(
                          fontSize: 12,
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                          height: 1.5,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          _SectionCard(
            title: '自适应连续认证引擎',
            subtitle: '根据当前会话环境、风险信号和最近验证状态动态判断是否需要 step-up。',
            child: _AdaptiveAuthPanel(controller: controller),
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
                  subtitle: '使用系统原生 Passkey 完成登录与敏感验证',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Align(
                        alignment: Alignment.centerLeft,
                        child: FilledButton.tonalIcon(
                          onPressed: () async {
                            try {
                              await controller.registerPasskey(
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
    final int onlineCount = controller.sessions
        .where((SessionItem item) => item.online)
        .length;
    final int currentCount = controller.sessions
        .where((SessionItem item) => item.current)
        .length;
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '在线会话',
            subtitle: '管理您的登录与在线信息',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: <Widget>[
                    _SessionSummaryChip(
                      icon: Icons.devices_other_rounded,
                      label: '全部会话',
                      value: '${controller.sessions.length}',
                    ),
                    _SessionSummaryChip(
                      icon: Icons.wifi_tethering_rounded,
                      label: '在线设备',
                      value: '$onlineCount',
                      tone: Colors.green,
                    ),
                    _SessionSummaryChip(
                      icon: Icons.laptop_mac_rounded,
                      label: '当前设备',
                      value: '$currentCount',
                      tone: const Color(0xFF147D74),
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                Align(
                  alignment: Alignment.centerRight,
                  child: OutlinedButton.icon(
                    onPressed: () async {
                      await _runWithFeedback(
                        context,
                        controller.refreshSessions,
                        success: '会话已刷新',
                      );
                    },
                    icon: const Icon(Icons.refresh_rounded),
                    label: const Text('刷新列表'),
                  ),
                ),
                const SizedBox(height: 8),
                if (controller.sessions.isEmpty)
                  const _InlineStateCard(
                    icon: Icons.devices_other_rounded,
                    title: '没有在线会话',
                    message: '登录其他设备后，会话信息会显示在这里。',
                  )
                else
                  Column(
                    children: controller.sessions.map((SessionItem item) {
                      return _SessionRow(
                        item: item,
                        compact: false,
                        controller: controller,
                      );
                    }).toList(),
                  ),
              ],
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
                    backgroundColor:
                        Theme.of(context).brightness == Brightness.dark
                        ? Colors.red.withValues(alpha: 0.18)
                        : Colors.red.shade50,
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

class _AdaptiveAuthPanel extends StatelessWidget {
  const _AdaptiveAuthPanel({required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final AdaptiveAuthStatus? status = controller.adaptiveAuthStatus;
    if (status == null) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    final ColorScheme colorScheme = Theme.of(context).colorScheme;
    final Color tone = adaptiveRiskTone(status.riskLevel, colorScheme);
    final List<String> reasons = status.reasons.isEmpty
        ? const <String>['当前会话环境稳定，未发现额外风险信号']
        : status.reasons;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(18),
            color: tone.withValues(alpha: 0.10),
            border: Border.all(color: tone.withValues(alpha: 0.24)),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      status.requiresStepUp ? '建议立即补做验证' : '当前会话可信度稳定',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      status.recommendedAction,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: <Widget>[
                  Chip(
                    avatar: Icon(
                      status.trusted
                          ? Icons.verified_user_rounded
                          : Icons.warning_amber_rounded,
                      size: 18,
                      color: tone,
                    ),
                    label: Text(
                      '${adaptiveRiskLabel(status.riskLevel)}风险 ${status.riskScore}',
                    ),
                  ),
                  const SizedBox(height: 8),
                  Chip(
                    avatar: Icon(
                      status.policyDecision == 'FREEZE'
                          ? Icons.lock_rounded
                          : status.policyDecision == 'STEP_UP'
                          ? Icons.gpp_maybe_rounded
                          : Icons.check_circle_rounded,
                      size: 18,
                      color: tone,
                    ),
                    label: Text(
                      status.policyDecision == 'FREEZE'
                          ? '策略：冻结会话'
                          : status.policyDecision == 'STEP_UP'
                          ? '策略：强制补验'
                          : '策略：放行',
                    ),
                  ),
                  const SizedBox(height: 8),
                  FilledButton.tonalIcon(
                    onPressed: status.sensitiveVerified
                        ? null
                        : () async {
                            final bool verified =
                                await _showSensitiveVerificationDialog(
                                  context,
                                  controller: controller,
                                );
                            if (verified) {
                              await controller.refreshAdaptiveAuthStatus();
                              if (context.mounted) {
                                showAppMessage(context, '连续认证状态已更新');
                              }
                            }
                          },
                    icon: const Icon(Icons.verified_user_rounded),
                    label: Text(status.sensitiveVerified ? '已完成验证' : '立即验证'),
                  ),
                ],
              ),
            ],
          ),
        ),
        if (status.multiEndpointAlert)
          Padding(
            padding: const EdgeInsets.only(top: 14),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color:
                    (status.alertLevel == 'high' ? Colors.red : Colors.orange)
                        .withValues(alpha: 0.10),
                border: Border.all(
                  color:
                      (status.alertLevel == 'high' ? Colors.red : Colors.orange)
                          .withValues(alpha: 0.32),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    status.alertTitle ?? '风险告警',
                    style: const TextStyle(fontWeight: FontWeight.w700),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    status.alertMessage ?? '检测到风险信号，请尽快处理',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                      height: 1.45,
                    ),
                  ),
                  if (status.alertRemainingSeconds > 0) ...<Widget>[
                    const SizedBox(height: 6),
                    Text(
                      '告警剩余有效期：${status.alertRemainingSeconds} 秒',
                      style: TextStyle(
                        fontSize: 12,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          children: <Widget>[
            _InfoChip(
              label: '敏感验证状态',
              value: status.sensitiveVerified
                  ? '有效 ${status.sensitiveVerificationRemainingSeconds} 秒'
                  : '未验证',
            ),
            _InfoChip(
              label: '会话年龄',
              value: formatDurationSeconds(status.authAgeSeconds),
            ),
            _InfoChip(
              label: '空闲时长',
              value: formatDurationSeconds(status.idleSeconds),
            ),
            _InfoChip(
              label: '当前环境',
              value:
                  '${status.currentLocation ?? '未知位置'} · ${status.deviceType ?? '未知设备'}',
            ),
          ],
        ),
        const SizedBox(height: 16),
        const Text('风险信号', style: TextStyle(fontWeight: FontWeight.w700)),
        const SizedBox(height: 10),
        Wrap(
          spacing: 10,
          runSpacing: 10,
          children: reasons
              .map((String reason) => Chip(label: Text(reason)))
              .toList(),
        ),
      ],
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

  Future<void> _resetFilters() async {
    setState(() {
      _operationType = null;
      _result = null;
    });
    await _loadLogs();
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Column(
        children: <Widget>[
          _SectionCard(
            title: '操作日志',
            subtitle: '在这里查看账号敏感操作和验证记录',
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surfaceContainerHighest
                        .withValues(alpha: 0.30),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: Theme.of(
                        context,
                      ).colorScheme.outlineVariant.withValues(alpha: 0.5),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Row(
                        children: <Widget>[
                          Icon(
                            Icons.filter_list_rounded,
                            size: 18,
                            color: Theme.of(context).colorScheme.primary,
                          ),
                          const SizedBox(width: 8),
                          Text(
                            '筛选记录',
                            style: Theme.of(context).textTheme.titleSmall
                                ?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          const Spacer(),
                          TextButton.icon(
                            onPressed: _loading ? null : _resetFilters,
                            icon: const Icon(
                              Icons.restart_alt_rounded,
                              size: 17,
                            ),
                            label: const Text('重置'),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 12,
                        runSpacing: 10,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: <Widget>[
                          SizedBox(
                            width: 245,
                            child: DropdownButtonFormField<String>(
                              initialValue: _operationType,
                              decoration: const InputDecoration(
                                labelText: '操作类型',
                                prefixIcon: Icon(Icons.manage_search_rounded),
                              ),
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
                              onChanged: (String? value) =>
                                  setState(() => _operationType = value),
                            ),
                          ),
                          SizedBox(
                            width: 210,
                            child: DropdownButtonFormField<String>(
                              initialValue: _result,
                              decoration: const InputDecoration(
                                labelText: '执行结果',
                                prefixIcon: Icon(Icons.fact_check_outlined),
                              ),
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
                              onChanged: (String? value) =>
                                  setState(() => _result = value),
                            ),
                          ),
                          FilledButton.icon(
                            onPressed: _loading ? null : _loadLogs,
                            icon: _loading
                                ? const SizedBox(
                                    width: 17,
                                    height: 17,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.search_rounded, size: 19),
                            label: Text(_loading ? '查询中' : '查询记录'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        '查询结果',
                        style: Theme.of(context).textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    Text(
                      '${widget.controller.sensitiveLogs.length} 条记录',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                if (widget.controller.sensitiveLogs.isEmpty)
                  const _InlineStateCard(
                    icon: Icons.manage_search_rounded,
                    title: '没有匹配的记录',
                    message: '调整操作类型或执行结果后重新查询。',
                  )
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF2A2A2A) : const Color(0xFFF8F7F2),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.08)
              : Colors.black.withValues(alpha: 0.06),
        ),
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final bool supportsPasskey = challenge.methods.contains('passkey');
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF3A3218) : kSurfaceTint,
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
                  ? nativePasskeyAvailableDescription(action: '二次验证')
                  : nativePasskeyUnavailableDescription(),
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final int score = computeSecurityScore(user, controller);
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: isDark
              ? <Color>[const Color(0xFF4A3D1A), const Color(0xFF5A4510)]
              : <Color>[const Color(0xFFFFF4D2), const Color(0xFFFFE5A0)],
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
    final ColorScheme scheme = Theme.of(context).colorScheme;
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
      backgroundColor: scheme.primaryContainer,
      child: Text(
        initial,
        style: TextStyle(
          color: scheme.onPrimaryContainer,
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
    required this.width,
    required this.title,
    required this.value,
    required this.caption,
    required this.icon,
    this.progress,
  });

  final double width;
  final String title;
  final String value;
  final String caption;
  final IconData icon;
  final double? progress;

  @override
  Widget build(BuildContext context) {
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final ColorScheme colors = Theme.of(context).colorScheme;
    return SizedBox(
      width: width,
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF262626) : Colors.white,
          borderRadius: BorderRadius.circular(22),
          border: Border.all(
            color: isDark
                ? Colors.white.withValues(alpha: 0.07)
                : Colors.black.withValues(alpha: 0.055),
          ),
          boxShadow: <BoxShadow>[
            BoxShadow(
              color: Colors.black.withValues(alpha: isDark ? 0.10 : 0.035),
              blurRadius: 18,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(
                    color: kPrimaryColor.withValues(
                      alpha: isDark ? 0.16 : 0.13,
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    icon,
                    color: isDark ? kPrimaryColor : const Color(0xFF755400),
                    size: 20,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    title,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: colors.onSurfaceVariant,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Text(
              value,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                fontWeight: FontWeight.w800,
                letterSpacing: -0.7,
              ),
            ),
            if (progress != null) ...<Widget>[
              const SizedBox(height: 10),
              ClipRRect(
                borderRadius: BorderRadius.circular(99),
                child: LinearProgressIndicator(
                  value: progress,
                  minHeight: 5,
                  backgroundColor: kPrimaryColor.withValues(alpha: 0.14),
                  valueColor: AlwaysStoppedAnimation<Color>(
                    isDark ? kPrimaryColor : const Color(0xFFE5A500),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 7),
            Text(
              caption,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: colors.onSurfaceVariant,
                height: 1.4,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _OverviewSplit extends StatelessWidget {
  const _OverviewSplit({required this.left, required this.right});

  final Widget left;
  final Widget right;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        if (constraints.maxWidth < 900) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[left, const SizedBox(height: 14), right],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Expanded(flex: 3, child: left),
            const SizedBox(width: 14),
            Expanded(flex: 2, child: right),
          ],
        );
      },
    );
  }
}

class _OverviewEmptyState extends StatelessWidget {
  const _OverviewEmptyState({required this.icon, required this.message});

  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) {
    final Color tint = Theme.of(context).colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 14),
      child: Row(
        children: <Widget>[
          Icon(icon, size: 18, color: tint),
          const SizedBox(width: 9),
          Text(message, style: TextStyle(color: tint)),
        ],
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF262626) : Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.07)
              : Colors.black.withValues(alpha: 0.055),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.08 : 0.025),
            blurRadius: 18,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            title,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
              letterSpacing: -0.15,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: colors.onSurfaceVariant,
              height: 1.45,
            ),
          ),
          const SizedBox(height: 18),
          child,
        ],
      ),
    );
  }
}

class _InlineStateCard extends StatelessWidget {
  const _InlineStateCard({
    required this.icon,
    required this.title,
    required this.message,
    this.action,
  });
  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.outlineVariant.withValues(alpha: 0.5)),
      ),
      child: Column(
        children: <Widget>[
          Icon(icon, size: 28, color: colors.onSurfaceVariant),
          const SizedBox(height: 10),
          Text(
            title,
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(
            message,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          if (action != null) ...<Widget>[const SizedBox(height: 8), action!],
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
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    return Container(
      constraints: const BoxConstraints(minWidth: 220),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF323232) : Colors.white,
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            label,
            style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
          ),
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
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: isDark ? const Color(0xFF323232) : Colors.white,
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: <Widget>[
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    label,
                    style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
                  ),
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final TotpStatusResponse? status = controller.totpStatus;
    final bool enabled = status?.enabled == true;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: enabled
                ? (isDark ? const Color(0xFF1F3A24) : const Color(0xFFEFFAF1))
                : (isDark ? const Color(0xFF323232) : Colors.white),
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF323232) : Colors.white,
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
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    final bool isDesktop = sessionIsDesktopApp(item);
    final bool isMobile = sessionIsMobileApp(item);
    final String clientLabel = sessionClientLabel(item);
    final String systemLabel = sessionSystemLabel(item);
    final Color accent = isDesktop
        ? kPrimaryColor
        : isMobile
        ? Colors.green
        : Colors.blueGrey;
    final Color statusColor = item.current
        ? Colors.teal
        : item.online
        ? Colors.green
        : Colors.blueGrey;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: EdgeInsets.all(compact ? 14 : 16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF2D2D2D) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.08)
              : Colors.black.withValues(alpha: 0.06),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            blurRadius: 18,
            offset: const Offset(0, 8),
            color: Colors.black.withValues(alpha: isDark ? 0.22 : 0.04),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(deviceIcon(item.deviceType), color: accent),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Expanded(
                      child: Text(
                        clientLabel,
                        style: theme.textTheme.titleSmall?.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: statusColor.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        item.current
                            ? '当前'
                            : item.online
                            ? '在线'
                            : '离线',
                        style: TextStyle(
                          fontSize: 12,
                          color: statusColor,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  '$systemLabel · ${item.ipAddress}',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 6,
                  children: <Widget>[
                    _SessionMetaPill(
                      icon: Icons.location_on_outlined,
                      text: item.ipLocation ?? '未知位置',
                    ),
                    _SessionMetaPill(
                      icon: Icons.schedule_rounded,
                      text: '活跃 ${formatRelativeTime(item.lastSeenAt)}',
                    ),
                  ],
                ),
              ],
            ),
          ),
          if (!compact && controller != null)
            Padding(
              padding: const EdgeInsets.only(left: 10),
              child: item.current
                  ? OutlinedButton(onPressed: null, child: const Text('当前设备'))
                  : FilledButton.tonalIcon(
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.red.withValues(alpha: 0.08),
                      ),
                      onPressed: () async {
                        await _runWithFeedback(
                          context,
                          () => controller!.revokeSession(item.id),
                          success: '会话已撤销',
                        );
                      },
                      icon: const Icon(
                        Icons.logout_rounded,
                        color: Colors.red,
                        size: 18,
                      ),
                      label: const Text(
                        '撤销',
                        style: TextStyle(color: Colors.red),
                      ),
                    ),
            ),
        ],
      ),
    );
  }
}

class _SessionSummaryChip extends StatelessWidget {
  const _SessionSummaryChip({
    required this.icon,
    required this.label,
    required this.value,
    this.tone = Colors.blueGrey,
  });

  final IconData icon;
  final String label;
  final String value;
  final Color tone;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? tone.withValues(alpha: 0.12)
            : tone.withValues(alpha: 0.075),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: tone.withValues(alpha: 0.16)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Container(
            width: 30,
            height: 30,
            decoration: BoxDecoration(
              color: tone.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, size: 16, color: tone),
          ),
          const SizedBox(width: 9),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(
                label,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 1),
              Text(
                value,
                style: theme.textTheme.titleSmall?.copyWith(
                  fontWeight: FontWeight.w800,
                  color: theme.colorScheme.onSurface,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SessionMetaPill extends StatelessWidget {
  const _SessionMetaPill({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isDark = theme.brightness == Brightness.dark;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF3A3A3A) : const Color(0xFFF6F6F6),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(icon, size: 14, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 4),
          Text(
            text,
            style: TextStyle(
              fontSize: 12,
              color: theme.colorScheme.onSurfaceVariant,
            ),
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
    final bool isDark = Theme.of(context).brightness == Brightness.dark;
    final Color statusColor = log.result == 'SUCCESS'
        ? Colors.green
        : Colors.red;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF2D2D2D) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.075)
              : Colors.black.withValues(alpha: 0.055),
        ),
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.08 : 0.025),
            blurRadius: 14,
            offset: const Offset(0, 4),
          ),
        ],
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
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 9,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: statusColor.withValues(alpha: 0.10),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        log.result == 'SUCCESS' ? '成功' : '失败',
                        style: TextStyle(
                          color: statusColor,
                          fontSize: 12,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 14,
                  runSpacing: 6,
                  children: <Widget>[
                    _SessionMetaPill(
                      icon: Icons.login_rounded,
                      text: loginMethodLabel(log.loginMethod),
                    ),
                    _SessionMetaPill(
                      icon: Icons.schedule_rounded,
                      text: formatDateTime(log.createdAt),
                    ),
                  ],
                ),
                if (!compact) ...<Widget>[
                  const SizedBox(height: 9),
                  Wrap(
                    spacing: 14,
                    runSpacing: 6,
                    children: <Widget>[
                      _SessionMetaPill(
                        icon: Icons.location_on_outlined,
                        text: log.ipLocation ?? '未知位置',
                      ),
                      _SessionMetaPill(
                        icon: Icons.language_rounded,
                        text: log.ipAddress,
                      ),
                      _SessionMetaPill(
                        icon: Icons.shield_outlined,
                        text: '风险 ${log.riskScore}',
                      ),
                    ],
                  ),
                  if (log.failureReason != null &&
                      log.failureReason!.isNotEmpty)
                    Container(
                      width: double.infinity,
                      margin: const EdgeInsets.only(top: 10),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 11,
                        vertical: 9,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.red.withValues(
                          alpha: isDark ? 0.12 : 0.06,
                        ),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Text(
                        log.failureReason!,
                        style: TextStyle(
                          color: isDark
                              ? Colors.red.shade200
                              : Colors.red.shade700,
                          fontSize: 12,
                        ),
                      ),
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
  Timer? _qrPollingTimer;
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
  bool _passkeyAvailable = false;
  bool _qrRefreshing = false;
  String _qrPayload = '';
  String _qrChallengeId = '';
  String _qrPollToken = '';
  int _qrExpiresInSeconds = 0;
  String? _qrErrorText;

  @override
  void initState() {
    super.initState();
    _loadStatus();
  }

  @override
  void dispose() {
    _countdownTimer?.cancel();
    _cleanupQrPolling();
    _passwordController.dispose();
    _codeController.dispose();
    _totpController.dispose();
    super.dispose();
  }

  Future<void> _loadStatus() async {
    try {
      final bool passkeyAvailable = await widget.controller
          .isPasskeyAvailable();
      final SensitiveVerificationStatus status =
          widget.initialStatus ??
          await widget.controller.checkSensitiveVerification();
      if (status.verified) {
        if (mounted) {
          Navigator.of(context).pop(true);
        }
        return;
      }
      final List<SensitiveVerificationMethod> normalizedMethods =
          status.methods.isEmpty
          ? const <SensitiveVerificationMethod>[
              SensitiveVerificationMethod.password,
              SensitiveVerificationMethod.emailCode,
              SensitiveVerificationMethod.totp,
              SensitiveVerificationMethod.passkey,
            ]
          : status.methods;
      final List<SensitiveVerificationMethod> methods =
          <SensitiveVerificationMethod>{
            ...normalizedMethods,
            SensitiveVerificationMethod.qr,
          }.toList();
      final List<SensitiveVerificationMethod> preferredOrder =
          <SensitiveVerificationMethod>[
            ...<SensitiveVerificationMethod?>[
              status.preferredMethod,
            ].whereType<SensitiveVerificationMethod>(),
            ...methods,
          ];
      final SensitiveVerificationMethod selected = preferredOrder.firstWhere(
        (SensitiveVerificationMethod method) =>
            method != SensitiveVerificationMethod.passkey || passkeyAvailable,
        orElse: () => preferredOrder.first,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _methods = methods;
        _selectedMethod = selected;
        _passkeyAvailable = passkeyAvailable;
        _loading = false;
      });
      if (selected == SensitiveVerificationMethod.emailCode) {
        await _sendCode();
      } else if (selected == SensitiveVerificationMethod.qr) {
        await _startQrVerification();
      }
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _passkeyAvailable = false;
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
    if (method != SensitiveVerificationMethod.qr) {
      _cleanupQrPolling();
    }
    if (method == SensitiveVerificationMethod.emailCode &&
        _codeCountdown == 0) {
      await _sendCode();
    } else if (method == SensitiveVerificationMethod.qr) {
      await _startQrVerification();
    }
  }

  void _cleanupQrPolling() {
    _qrPollingTimer?.cancel();
    _qrPollingTimer = null;
  }

  Future<void> _pollQrVerification() async {
    if (_qrChallengeId.isEmpty || _qrPollToken.isEmpty) {
      return;
    }
    try {
      final QrChallengeStatus status = await widget.controller.pollQrStatus(
        challengeId: _qrChallengeId,
        pollToken: _qrPollToken,
      );
      if (!mounted) {
        return;
      }
      setState(() {
        _qrExpiresInSeconds = status.expiresInSeconds;
      });
      if (status.status == 'pending') {
        return;
      }
      _cleanupQrPolling();
      if (status.status == 'approved') {
        if (!status.verified) {
          setState(() {
            _qrErrorText = '扫码验证结果无效，请刷新二维码后重试';
          });
          return;
        }
        showAppMessage(context, '验证成功');
        Navigator.of(context).pop(true);
        return;
      }
      if (status.status == 'rejected') {
        setState(() {
          _qrErrorText = '扫码请求已被拒绝，请刷新二维码';
        });
        return;
      }
      setState(() {
        _qrErrorText = '二维码已过期，请刷新二维码';
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      _cleanupQrPolling();
      setState(() {
        _qrErrorText = error.toString();
      });
    }
  }

  void _startQrPolling() {
    _cleanupQrPolling();
    _qrPollingTimer = Timer.periodic(const Duration(seconds: 2), (_) {
      unawaited(_pollQrVerification());
    });
    unawaited(_pollQrVerification());
  }

  Future<void> _startQrVerification() async {
    if (_qrRefreshing || !mounted) {
      return;
    }
    setState(() {
      _qrRefreshing = true;
      _qrErrorText = null;
    });
    try {
      final QrLoginChallenge challenge = await widget.controller
          .initQrSensitive();
      if (!mounted) {
        return;
      }
      setState(() {
        _qrChallengeId = challenge.challengeId;
        _qrPollToken = challenge.pollToken;
        _qrExpiresInSeconds = challenge.expiresInSeconds;
        _qrPayload = challenge.qrText;
      });
      _startQrPolling();
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _qrErrorText = error.toString();
      });
    } finally {
      if (mounted) {
        setState(() {
          _qrRefreshing = false;
        });
      }
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
        !_passkeyAvailable) {
      message = widget.controller.passkeyUnavailableMessage;
    } else if (_selectedMethod == SensitiveVerificationMethod.qr) {
      message = '请使用手机扫码完成验证';
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
            showAppMessage(
              context,
              '即将调用 ${nativePasskeyPlatformName()} 系统 Passkey',
            );
          }
          await widget.controller.performPasskeySensitiveVerification();
          break;
        case SensitiveVerificationMethod.qr:
          return;
      }
      await widget.controller.refreshAdaptiveAuthStatus();
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
                            _passkeyAvailable;
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
                            child: _sendingCode
                                ? const SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : Text(
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
                    else if (_selectedMethod == SensitiveVerificationMethod.qr)
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          if (_qrPayload.isNotEmpty)
                            Center(
                              child: _buildLocalQrCode(
                                _qrPayload,
                                width: 220,
                                height: 220,
                                isDark:
                                    Theme.of(context).brightness ==
                                    Brightness.dark,
                              ),
                            )
                          else
                            const Center(
                              child: Padding(
                                padding: EdgeInsets.symmetric(vertical: 24),
                                child: CircularProgressIndicator(),
                              ),
                            ),
                          const SizedBox(height: 12),
                          Text(
                            '剩余有效期：${_qrExpiresInSeconds > 0 ? _qrExpiresInSeconds : 0} 秒',
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                          const SizedBox(height: 8),
                          if (_qrErrorText != null &&
                              _qrErrorText!.trim().isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(bottom: 8),
                              child: Text(
                                _qrErrorText!,
                                style: TextStyle(
                                  color: Theme.of(context).colorScheme.error,
                                ),
                              ),
                            ),
                          OutlinedButton.icon(
                            onPressed: _qrRefreshing
                                ? null
                                : () => _startQrVerification(),
                            icon: _qrRefreshing
                                ? const SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : const Icon(Icons.refresh_rounded),
                            label: Text(_qrRefreshing ? '刷新中...' : '刷新二维码'),
                          ),
                        ],
                      )
                    else
                      _DisabledCapabilityCard(
                        title: _passkeyAvailable
                            ? 'Passkey 验证已接入'
                            : 'Passkey 当前不可用',
                        description: _passkeyAvailable
                            ? nativePasskeyAvailableDescription()
                            : widget.controller.passkeyUnavailableMessage,
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
        FilledButton.icon(
          onPressed:
              _loading ||
                  _verifying ||
                  _selectedMethod == SensitiveVerificationMethod.qr
              ? null
              : _verify,
          icon: _verifying
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.verified_user_rounded),
          label: Text(_verifying ? '验证中...' : '验证'),
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
  String value = initialValue;
  final String? nextValue = await showDialog<String>(
    context: context,
    builder: (BuildContext context) {
      return AlertDialog(
        title: Text(title),
        content: TextFormField(
          initialValue: initialValue,
          maxLines: maxLines,
          decoration: InputDecoration(hintText: hintText),
          onChanged: (String next) {
            value = next;
          },
        ),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(value),
            child: const Text('保存'),
          ),
        ],
      );
    },
  );
  await _awaitDialogTeardown();
  if (nextValue == null || !context.mounted) {
    return;
  }

  try {
    await onSubmit(nextValue);
    if (context.mounted) {
      showAppMessage(context, '已保存');
    }
  } catch (error) {
    if (context.mounted) {
      showAppMessage(context, error.toString(), error: true);
    }
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
  final String? nextValue = await showDialog<String>(
    context: context,
    builder: (BuildContext context) {
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
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.of(context).pop(value),
                    child: const Text('保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  await _awaitDialogTeardown();
  if (nextValue == null || !context.mounted) {
    return;
  }

  try {
    await onSubmit(nextValue);
    if (context.mounted) {
      showAppMessage(context, '已保存');
    }
  } catch (error) {
    if (context.mounted) {
      showAppMessage(context, error.toString(), error: true);
    }
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
                          child: busy
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : const Text('发送验证码'),
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
                  FilledButton.icon(
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
                    icon: busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.save_rounded),
                    label: Text(busy ? '保存中...' : '保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  await _disposeTextControllers(<TextEditingController>[
    emailController,
    codeController,
  ]);
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
                  FilledButton.icon(
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
                    icon: busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.save_rounded),
                    label: Text(busy ? '保存中...' : '保存'),
                  ),
                ],
              );
            },
      );
    },
  );
  await _disposeTextControllers(<TextEditingController>[passwordController]);
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
                        _buildQrWidget(context, options.qrCodeUrl),
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
                  FilledButton.icon(
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
                    icon: busy
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.verified_user_rounded),
                    label: Text(busy ? '启用中...' : '确认启用'),
                  ),
                ],
              );
            },
      );
    },
  );
  await _disposeTextControllers(<TextEditingController>[codeController]);
}

Future<void> _disposeTextControllers(
  List<TextEditingController> controllers,
) async {
  await Future<void>.delayed(Duration.zero);
  for (final TextEditingController controller in controllers) {
    controller.dispose();
  }
}

Future<void> _awaitDialogTeardown() async {
  await WidgetsBinding.instance.endOfFrame;
  await WidgetsBinding.instance.endOfFrame;
  await Future<void>.delayed(const Duration(milliseconds: 180));
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

Widget _buildQrWidget(BuildContext context, String qrCodeUrl) {
  final bool isDark = Theme.of(context).brightness == Brightness.dark;
  if (qrCodeUrl.startsWith('data:image')) {
    final String encoded = qrCodeUrl.split(',').last;
    final Uint8List bytes = base64Decode(encoded);
    return Center(child: Image.memory(bytes, width: 220, height: 220));
  }
  Uri uri = Uri.parse(qrCodeUrl);
  if (isDark &&
      ((uri.host == 'quickchart.io' && uri.path == '/qr') ||
          (uri.host == 'api.qrserver.com' &&
              uri.path.startsWith('/v1/create-qr-code')))) {
    final Map<String, String> query = Map<String, String>.from(
      uri.queryParameters,
    );
    query['dark'] = 'FFFFFF';
    query['light'] = '0000';
    query['color'] = 'FFFFFF';
    query['bgcolor'] = '00000000';
    uri = uri.replace(queryParameters: query);
  }
  return Center(child: Image.network(uri.toString(), width: 220, height: 220));
}

Widget _buildLocalQrCode(
  String payload, {
  required double width,
  required double height,
  required bool isDark,
}) {
  final Color codeColor = isDark ? Colors.white : Colors.black;
  return SizedBox(
    width: width,
    height: height,
    child: QrImageView(
      data: payload,
      version: QrVersions.auto,
      size: min(width, height),
      gapless: true,
      padding: EdgeInsets.zero,
      backgroundColor: Colors.transparent,
      eyeStyle: QrEyeStyle(eyeShape: QrEyeShape.square, color: codeColor),
      dataModuleStyle: QrDataModuleStyle(
        dataModuleShape: QrDataModuleShape.square,
        color: codeColor,
      ),
      errorStateBuilder: (BuildContext context, Object? error) {
        return const Center(child: Text('二维码生成失败，请刷新'));
      },
    ),
  );
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
    case SensitiveVerificationMethod.qr:
      return 'qr';
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
    case 'qr':
      return SensitiveVerificationMethod.qr;
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
    case SensitiveVerificationMethod.qr:
      return '扫码验证';
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
      return nativePasskeyAvailableDescription();
    case SensitiveVerificationMethod.qr:
      return '使用已登录手机端扫码确认当前敏感操作。';
  }
}

String sectionTitle(DesktopSection section) {
  switch (section) {
    case DesktopSection.overview:
      return '账号总览';
    case DesktopSection.profile:
      return '账号资料';
    case DesktopSection.security:
      return '安全设置';
    case DesktopSection.devices:
      return '设备管理';
    case DesktopSection.activity:
      return '操作日志';
    case DesktopSection.authorizations:
      return '访问授权';
  }
}

String sectionSubtitle(DesktopSection section) {
  switch (section) {
    case DesktopSection.overview:
      return '查看桌面端的账号状态、登录方式和近期活动。';
    case DesktopSection.profile:
      return '管理账户资料与扩展信息。';
    case DesktopSection.security:
      return '配置 MFA、TOTP、Passkey 和敏感验证偏好。';
    case DesktopSection.devices:
      return '查看在线设备、登录会话和当前状态。';
    case DesktopSection.activity:
      return '筛选查看敏感操作与验证记录。';
    case DesktopSection.authorizations:
      return '查看已授权访问您账号信息的第三方应用。';
  }
}

IconData sectionIcon(DesktopSection section) {
  switch (section) {
    case DesktopSection.overview:
      return Icons.dashboard_customize_rounded;
    case DesktopSection.profile:
      return Icons.badge_rounded;
    case DesktopSection.security:
      return Icons.verified_user_rounded;
    case DesktopSection.devices:
      return Icons.devices_rounded;
    case DesktopSection.activity:
      return Icons.history_rounded;
    case DesktopSection.authorizations:
      return Icons.verified_user_outlined;
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

bool sessionIsDesktopApp(SessionItem item) {
  final String ua = (item.userAgent ?? '').toLowerCase();
  return ua.contains('ksuserauthdesktop');
}

bool sessionIsMobileApp(SessionItem item) {
  final String ua = (item.userAgent ?? '').toLowerCase();
  return ua.contains('ksuserauthmobile');
}

String sessionClientLabel(SessionItem item) {
  if (sessionIsDesktopApp(item)) {
    return '桌面端';
  }
  if (sessionIsMobileApp(item)) {
    return '移动端';
  }
  return item.browser ?? '未知浏览器';
}

String sessionSystemLabel(SessionItem item) {
  final String value = ((item.userAgent ?? '') + (item.deviceType ?? ''))
      .toLowerCase();
  if (value.contains('windows')) {
    return 'Windows';
  }
  if (value.contains('mac os') ||
      value.contains('macintosh') ||
      value.contains('mac')) {
    return 'macOS';
  }
  if (value.contains('android')) {
    return 'Android';
  }
  if (value.contains('iphone') ||
      value.contains('ipad') ||
      value.contains('ios')) {
    return 'iOS';
  }
  if (value.contains('linux')) {
    return 'Linux';
  }
  if (sessionIsDesktopApp(item)) {
    return '桌面端';
  }
  if (sessionIsMobileApp(item)) {
    return '移动端';
  }
  return item.deviceType ?? '未知设备';
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
    'QR': '扫码登录',
    'QR_MFA': '扫码 + 二步验证',
    'BRIDGE': '桥接登录',
    'BRIDGE_FROM_DESKTOP': '电脑端桥接',
    'BRIDGE_FROM_WEB': '网页端桥接',
    'BRIDGE_TO_MOBILE': '桥接到手机端',
    'ACCOUNT_RECOVERY': '账号恢复',
  };
  return labels[loginMethod] ?? (loginMethod ?? '通用操作');
}

String adaptiveRiskLabel(String riskLevel) {
  switch (riskLevel.toLowerCase()) {
    case 'high':
      return '高';
    case 'medium':
      return '中';
    default:
      return '低';
  }
}

Color adaptiveRiskTone(String riskLevel, ColorScheme colorScheme) {
  switch (riskLevel.toLowerCase()) {
    case 'high':
      return colorScheme.error;
    case 'medium':
      return Colors.orange;
    default:
      return Colors.green;
  }
}

String formatDurationSeconds(int seconds) {
  if (seconds < 60) {
    return '$seconds 秒';
  }
  if (seconds < 3600) {
    return '${seconds ~/ 60} 分钟';
  }
  if (seconds < 86400) {
    return '${seconds ~/ 3600} 小时';
  }
  return '${seconds ~/ 86400} 天';
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

class OAuth2AuthorizedApp {
  const OAuth2AuthorizedApp({
    required this.appId,
    required this.appName,
    required this.contactInfo,
    required this.redirectUri,
    required this.scopes,
    required this.authorizedAt,
    required this.lastAuthorizedAt,
    required this.grantMode,
    this.logoUrl,
    this.creatorName,
    this.expiresAt,
  });

  factory OAuth2AuthorizedApp.fromJson(Map<String, dynamic> json) {
    return OAuth2AuthorizedApp(
      appId: asString(json['appId']) ?? '',
      appName: asString(json['appName']) ?? '未命名应用',
      logoUrl: asString(json['logoUrl']),
      creatorName: asString(json['creatorName']),
      contactInfo: asString(json['contactInfo']) ?? '',
      redirectUri: asString(json['redirectUri']) ?? '',
      scopes: asList(
        json['scopes'],
      ).map((dynamic scope) => scope.toString()).toList(),
      authorizedAt: asString(json['authorizedAt']) ?? '',
      lastAuthorizedAt: asString(json['lastAuthorizedAt']) ?? '',
      grantMode: asString(json['grantMode']) ?? 'PERMANENT',
      expiresAt: asString(json['expiresAt']),
    );
  }

  final String appId;
  final String appName;
  final String? logoUrl;
  final String? creatorName;
  final String contactInfo;
  final String redirectUri;
  final List<String> scopes;
  final String authorizedAt;
  final String lastAuthorizedAt;
  final String grantMode;
  final String? expiresAt;
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

class AdaptiveAuthStatus {
  const AdaptiveAuthStatus({
    required this.sessionId,
    required this.riskScore,
    required this.riskLevel,
    required this.policyDecision,
    required this.policyVersion,
    required this.trusted,
    required this.requiresStepUp,
    required this.sessionFrozen,
    required this.sensitiveVerified,
    required this.sensitiveVerificationRemainingSeconds,
    required this.authAgeSeconds,
    required this.idleSeconds,
    required this.currentIp,
    required this.currentLocation,
    required this.sessionIp,
    required this.sessionLocation,
    required this.browser,
    required this.deviceType,
    required this.multiEndpointAlert,
    required this.alertLevel,
    required this.alertTitle,
    required this.alertMessage,
    required this.alertRemainingSeconds,
    required this.recommendedAction,
    required this.reasons,
  });

  factory AdaptiveAuthStatus.fromJson(Map<String, dynamic> json) {
    return AdaptiveAuthStatus(
      sessionId: asInt(json['sessionId']),
      riskScore: asInt(json['riskScore']) ?? 0,
      riskLevel: asString(json['riskLevel']) ?? 'low',
      policyDecision: asString(json['policyDecision']) ?? 'ALLOW',
      policyVersion: asString(json['policyVersion']) ?? '1.0.0',
      trusted: asBool(json['trusted']),
      requiresStepUp: asBool(json['requiresStepUp']),
      sessionFrozen: asBool(json['sessionFrozen']),
      sensitiveVerified: asBool(json['sensitiveVerified']),
      sensitiveVerificationRemainingSeconds:
          asInt(json['sensitiveVerificationRemainingSeconds']) ?? 0,
      authAgeSeconds: asInt(json['authAgeSeconds']) ?? 0,
      idleSeconds: asInt(json['idleSeconds']) ?? 0,
      currentIp: asString(json['currentIp']),
      currentLocation: asString(json['currentLocation']),
      sessionIp: asString(json['sessionIp']),
      sessionLocation: asString(json['sessionLocation']),
      browser: asString(json['browser']),
      deviceType: asString(json['deviceType']),
      multiEndpointAlert: asBool(json['multiEndpointAlert']),
      alertLevel: asString(json['alertLevel']),
      alertTitle: asString(json['alertTitle']),
      alertMessage: asString(json['alertMessage']),
      alertRemainingSeconds: asInt(json['alertRemainingSeconds']) ?? 0,
      recommendedAction: asString(json['recommendedAction']) ?? '',
      reasons: asList(json['reasons'])
          .map((dynamic item) => item.toString())
          .where((String item) => item.trim().isNotEmpty)
          .toList(),
    );
  }

  final int? sessionId;
  final int riskScore;
  final String riskLevel;
  final String policyDecision;
  final String policyVersion;
  final bool trusted;
  final bool requiresStepUp;
  final bool sessionFrozen;
  final bool sensitiveVerified;
  final int sensitiveVerificationRemainingSeconds;
  final int authAgeSeconds;
  final int idleSeconds;
  final String? currentIp;
  final String? currentLocation;
  final String? sessionIp;
  final String? sessionLocation;
  final String? browser;
  final String? deviceType;
  final bool multiEndpointAlert;
  final String? alertLevel;
  final String? alertTitle;
  final String? alertMessage;
  final int alertRemainingSeconds;
  final String recommendedAction;
  final List<String> reasons;
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

class PasskeyRegistrationOptions {
  const PasskeyRegistrationOptions({
    required this.challenge,
    required this.rpId,
    required this.userId,
    required this.userName,
    required this.userDisplayName,
    required this.timeout,
    required this.attestation,
    required this.userVerification,
  });

  factory PasskeyRegistrationOptions.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> rp = _decodeNestedJsonObject(json['rp']);
    final Map<String, dynamic> user = _decodeNestedJsonObject(json['user']);
    final Map<String, dynamic> authenticatorSelection = _decodeNestedJsonObject(
      json['authenticatorSelection'],
    );

    return PasskeyRegistrationOptions(
      challenge: asString(json['challenge']) ?? '',
      rpId: asString(rp['id']) ?? '',
      userId: asString(user['id']) ?? '',
      userName: asString(user['name']) ?? '',
      userDisplayName:
          asString(user['displayName']) ?? asString(user['name']) ?? '',
      timeout: asString(json['timeout']) ?? '300000',
      attestation: asString(json['attestation']) ?? 'none',
      userVerification:
          asString(authenticatorSelection['userVerification']) ?? 'preferred',
    );
  }

  final String challenge;
  final String rpId;
  final String userId;
  final String userName;
  final String userDisplayName;
  final String timeout;
  final String attestation;
  final String userVerification;

  static Map<String, dynamic> _decodeNestedJsonObject(dynamic value) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    if (value is Map) {
      return value.map(
        (dynamic key, dynamic item) => MapEntry(key.toString(), item),
      );
    }
    if (value is String && value.trim().isNotEmpty) {
      try {
        return asMap(jsonDecode(value));
      } catch (_) {
        return <String, dynamic>{};
      }
    }
    return <String, dynamic>{};
  }
}

class PasskeyRegistrationResult {
  const PasskeyRegistrationResult({
    required this.credentialRawId,
    required this.clientDataJSON,
    required this.attestationObject,
    required this.transports,
  });

  factory PasskeyRegistrationResult.fromChannelMap(Map<dynamic, dynamic> map) {
    return PasskeyRegistrationResult(
      credentialRawId: map['credentialRawId']?.toString() ?? '',
      clientDataJSON: map['clientDataJSON']?.toString() ?? '',
      attestationObject: map['attestationObject']?.toString() ?? '',
      transports: map['transports']?.toString() ?? 'internal',
    );
  }

  final String credentialRawId;
  final String clientDataJSON;
  final String attestationObject;
  final String transports;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'credentialRawId': credentialRawId,
      'clientDataJSON': clientDataJSON,
      'attestationObject': attestationObject,
      'transports': transports,
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
    this.transferCode,
    this.verified = false,
    this.registered = false,
  });

  factory BrowserPasskeyBridgeResponse.fromJson(Map<String, dynamic> json) {
    return BrowserPasskeyBridgeResponse(
      success: asString(json['status']) != 'error',
      message: asString(json['message']),
      accessToken: asString(json['accessToken']),
      transferCode: asString(json['transferCode']),
      verified: asBool(json['verified']),
      registered: asBool(json['registered']),
    );
  }

  final bool success;
  final String? message;
  final String? accessToken;
  final String? transferCode;
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
      ..set(HttpHeaders.accessControlAllowMethodsHeader, 'GET, POST, OPTIONS')
      ..set(HttpHeaders.contentTypeHeader, 'text/html; charset=utf-8');

    if (request.method == 'OPTIONS') {
      request.response.statusCode = HttpStatus.noContent;
      await request.response.close();
      return;
    }

    if (request.uri.path != '/desktop-passkey-callback' ||
        (request.method != 'POST' && request.method != 'GET')) {
      request.response.statusCode = HttpStatus.notFound;
      request.response.write('<html><body>Not found</body></html>');
      await request.response.close();
      return;
    }

    final Map<String, dynamic> payload;
    if (request.method == 'GET') {
      final String rawPayload =
          request.uri.queryParameters['bridgePayload'] ?? '';
      payload = rawPayload.isEmpty
          ? <String, dynamic>{}
          : asMap(jsonDecode(rawPayload));
    } else {
      final String raw = await utf8.decoder.bind(request).join();
      payload = raw.isEmpty ? <String, dynamic>{} : asMap(jsonDecode(raw));
    }
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
