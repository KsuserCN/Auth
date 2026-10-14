import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:ksuser_auth_desktop/api_result_messages.dart';
import 'package:ksuser_auth_desktop/generated/api_result_codes.dart';
import 'package:ksuser_auth_desktop/i18n.dart';
import 'package:ksuser_auth_desktop/main.dart';
import 'package:ksuser_auth_desktop/security_log.dart';

void main() {
  late Directory directory;
  setUpAll(() async {
    await initializeDateFormatting('zh');
    await initializeDateFormatting('en');
  });
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('desktop-i18n-');
    desktopLocaleController = DesktopLocaleController(
      preference: DesktopLanguage.simplifiedChinese,
      store: DesktopLanguageStore(directory: directory),
    );
  });
  tearDown(() async {
    desktopLocaleController.dispose();
    await directory.delete(recursive: true);
  });

  test('resolves device language and overrides consistently', () {
    for (final Locale locale in <Locale>[
      const Locale('zh'),
      const Locale('zh', 'CN'),
      const Locale('zh', 'TW'),
      const Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hant'),
    ]) {
      expect(
        resolveDesktopLocale(DesktopLanguage.system, <Locale>[
          locale,
        ]).toLanguageTag(),
        'zh-Hans',
      );
    }
    expect(
      resolveDesktopLocale(DesktopLanguage.system, <Locale>[
        const Locale('en', 'US'),
      ]).toLanguageTag(),
      'en',
    );
    expect(
      resolveDesktopLocale(DesktopLanguage.system, <Locale>[
        const Locale('fr'),
        const Locale('zh'),
      ]).toLanguageTag(),
      'en',
    );
    expect(
      resolveDesktopLocale(DesktopLanguage.system, <Locale>[]).toLanguageTag(),
      'en',
    );
    expect(
      resolveDesktopLocale(DesktopLanguage.english, <Locale>[
        const Locale('zh'),
      ]).toLanguageTag(),
      'en',
    );
    expect(
      resolveDesktopLocale(DesktopLanguage.simplifiedChinese, <Locale>[
        const Locale('en'),
      ]).toLanguageTag(),
      'zh-Hans',
    );
  });

  test(
    'persists selection, survives restart, and tolerates corrupt data',
    () async {
      final DesktopLanguageStore store = DesktopLanguageStore(
        directory: directory,
      );
      expect(await store.load(), DesktopLanguage.system);
      await desktopLocaleController.setLanguage(DesktopLanguage.english);
      expect(await store.load(), DesktopLanguage.english);
      final DesktopLocaleController restarted = DesktopLocaleController(
        preference: await store.load(),
        store: store,
      );
      expect(restarted.languageTag, 'en');
      restarted.dispose();
      await desktopLocaleController.setLanguage(DesktopLanguage.system);
      expect(await store.load(), DesktopLanguage.system);
      final File file = File('${directory.path}/language_preference.json');
      await file.writeAsString('{broken json');
      expect(await store.load(), DesktopLanguage.system);
      await file.writeAsString('{"language":"unsupported"}');
      expect(await store.load(), DesktopLanguage.system);
    },
  );

  test('device language changes notify only in system mode', () async {
    List<Locale> locales = <Locale>[const Locale('en')];
    final DesktopLocaleController controller = DesktopLocaleController(
      deviceLocales: () => locales,
      store: DesktopLanguageStore(directory: directory),
    );
    int changes = 0;
    controller.addListener(() => changes++);
    locales = <Locale>[const Locale('zh', 'TW')];
    controller.didChangeLocales(locales);
    expect(controller.languageTag, 'zh-Hans');
    expect(changes, 1);
    await controller.setLanguage(DesktopLanguage.english);
    final int manualChanges = changes;
    controller.didChangeLocales(locales);
    expect(changes, manualChanges);
    expect(controller.languageTag, 'en');
    controller.dispose();
  });

  test(
    'stored API descriptors retranslate without reloading business data',
    () async {
      final ApiException error = ApiException.fromResponse(<String, dynamic>{
        'errorCode': 'FORBIDDEN',
        'msg': 'untrusted text',
      }, 403);
      final PasswordRequirement policy = PasswordRequirement.fromJson(
        <String, dynamic>{
          'requirementCode': 'AUTH_020',
          'requirementParams': <String, int>{'min': 8, 'max': 64},
        },
      );
      final AdaptiveAuthStatus adaptive = AdaptiveAuthStatus.fromJson(
        <String, dynamic>{
          'reasonCodes': <Map<String, String>>[
            <String, String>{'errorCode': 'FORBIDDEN'},
          ],
          'recommendedActionCode': 'SENSITIVE_VERIFICATION_REQUIRED',
          'alertTitleCode': 'FORBIDDEN',
          'alertMessageCode': 'UNAUTHENTICATED',
          'currentLocation': '上海',
        },
      );
      final SensitiveLogItem log = SensitiveLogItem.fromJson(<String, dynamic>{
        'failureReason': 'FORBIDDEN',
      });
      final SensitiveLogItem history = SensitiveLogItem.fromJson(
        <String, dynamic>{'failureReason': '旧审计原文'},
      );
      final BrowserPasskeyBridgeResponse bridge =
          BrowserPasskeyBridgeResponse.fromJson(<String, dynamic>{
            'status': 'error',
            'errorCode': 'BAD_REQUEST',
          });
      final OAuth2AuthorizedApp app = OAuth2AuthorizedApp.fromJson(
        <String, dynamic>{'appName': '用户命名应用'},
      );
      expect(error.message, apiResultMessagesByLocale['zh-Hans']!['FORBIDDEN']);
      final String chinesePolicy = policy.requirementMessage;
      final PasswordRequirement missingPolicy = PasswordRequirement.fromJson(
        <String, dynamic>{},
      );
      final PasswordRequirement unknownPolicy = PasswordRequirement.fromJson(
        <String, dynamic>{'requirementCode': 'UNKNOWN_POLICY'},
      );
      final BrowserPasskeyBridgeResponse missingBridge =
          BrowserPasskeyBridgeResponse.fromJson(<String, dynamic>{
            'status': 'error',
          });
      final ApiException localError = ApiException.localized(
        () => desktopL10n.invalidDataFormat,
      );
      await desktopLocaleController.setLanguage(DesktopLanguage.english);
      final Map<String, String> english = apiResultMessagesByLocale['en']!;
      expect(error.message, english['FORBIDDEN']);
      expect(log.failureReason, english['FORBIDDEN']);
      expect(history.failureReason, '旧审计原文');
      expect(adaptive.reasons, <String>[english['FORBIDDEN']!]);
      expect(adaptive.alertTitle, english['FORBIDDEN']);
      expect(adaptive.alertMessage, english['UNAUTHENTICATED']);
      expect(
        adaptive.recommendedAction,
        english['SENSITIVE_VERIFICATION_REQUIRED'],
      );
      expect(adaptive.currentLocation, '上海');
      expect(app.appName, '用户命名应用');
      expect(bridge.message, english['BAD_REQUEST']);
      expect(policy.requirementMessage, isNot(chinesePolicy));
      expect(missingPolicy.requirementMessage, english['REQUEST_FAILED']);
      expect(unknownPolicy.requirementMessage, english['REQUEST_FAILED']);
      expect(missingBridge.message, english['BAD_REQUEST']);
      expect(localError.message, 'Invalid data format');
      expect(policy.requirementMessage, contains('8'));
      expect(policy.requirementMessage, contains('64'));
      expect(policy.validate('x'), contains('characters'));
      expect(
        resolveApiResultMessage(errorCode: 'UNKNOWN', statusCode: 403),
        english['FORBIDDEN'],
      );
      expect(
        resolveApiResultMessage(errorCode: 'AUTH_020', statusCode: 400),
        english['BAD_REQUEST'],
      );
    },
  );

  test(
    'activity methods and security actions follow live language changes',
    () async {
      final SensitiveLogItem log = SensitiveLogItem.fromJson(<String, dynamic>{
        'operationType': 'LOGIN',
        'loginMethod': 'PASSWORD_MFA',
        'loginMethods': <String>[],
      });
      final SensitiveLogItem event =
          SensitiveLogItem.fromJson(<String, dynamic>{
            'operationType': 'ADAPTIVE_POLICY',
            'loginMethod': 'adaptive-auth-status',
            'actionTaken': 'FREEZE',
          });
      expect(log.methodLabels, <String>['密码', '二步验证']);
      expect(event.methodLabels, isEmpty);
      expect(event.title, '会话已冻结');
      expect(parseLogMethods('["email_mfa", weixin, MFA, null]'), <String>[
        'EMAIL_CODE',
        'MFA',
        'WECHAT',
      ]);
      expect(logReason('Invalid password'), '密码不正确');
      await desktopLocaleController.setLanguage(DesktopLanguage.english);
      expect(log.methodLabels, <String>['Password', 'Two-step verification']);
      expect(event.title, 'Session frozen');
      expect(logReason('invalid_password'), 'Incorrect password');
    },
  );

  test('busy labels and reset follow the selected language', () async {
    final AppController controller = AppController(
      KsuserApiClient(baseUrl: 'http://localhost:8000'),
      environmentName: 'Test',
      passkeyOrigin: 'http://localhost:5173',
      loadAgreementAcceptance: () async => true,
    );
    final Completer<void> finish = Completer<void>();
    final Future<void> action = controller.runBusyAction(
      () => desktopL10n.savingSettings,
      () => finish.future,
    );
    expect(controller.currentBusyLabel, '正在保存设置...');
    await desktopLocaleController.setLanguage(DesktopLanguage.english);
    expect(controller.currentBusyLabel, 'Saving settings...');
    await controller.resetLocalPreferences();
    expect(desktopLocaleController.preference, DesktopLanguage.system);
    finish.complete();
    await action;
    controller.dispose();
  });

  test(
    'every normal and multipart request uses the current language',
    () async {
      final HttpServer server = await HttpServer.bind(
        InternetAddress.loopbackIPv4,
        0,
      );
      final List<MapEntry<String, String?>> headers =
          <MapEntry<String, String?>>[];
      final StreamSubscription<HttpRequest> subscription = server.listen((
        HttpRequest request,
      ) async {
        headers.add(
          MapEntry<String, String?>(
            request.uri.path,
            request.headers.value(HttpHeaders.acceptLanguageHeader),
          ),
        );
        await request.drain<void>();
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode(<String, dynamic>{
            'code': 200,
            'errorCode': 'REQUEST_SUCCESS',
            'data': <String, bool>{'ok': true},
          }),
        );
        await request.response.close();
      });
      final KsuserApiClient client = KsuserApiClient(
        baseUrl: 'http://127.0.0.1:${server.port}',
      );
      await client.get('/chinese');
      await desktopLocaleController.setLanguage(DesktopLanguage.english);
      await client.post(
        '/english',
        body: <String, String>{'data': 'unchanged'},
      );
      await client.postMultipartFile(
        '/upload',
        fieldName: 'avatar',
        fileName: 'test.png',
        contentType: 'image/png',
        bytes: Uint8List.fromList(<int>[1, 2, 3]),
      );
      expect(
        headers.firstWhere((entry) => entry.key == '/chinese').value,
        'zh-Hans',
      );
      expect(
        headers.firstWhere((entry) => entry.key == '/english').value,
        'en',
      );
      expect(headers.firstWhere((entry) => entry.key == '/upload').value, 'en');
      await subscription.cancel();
      await server.close(force: true);
    },
  );

  test('dates and relative time reflect the locale', () async {
    expect(formatDateTime('2026-04-09T12:00:00'), contains('2026/4/9'));
    expect(
      formatRelativeTime(
        DateTime.now().subtract(const Duration(minutes: 2)).toIso8601String(),
      ),
      contains('分钟前'),
    );
    await desktopLocaleController.setLanguage(DesktopLanguage.english);
    expect(formatDateTime('2026-04-09T12:00:00'), contains('4/9/2026'));
    expect(formatDurationSeconds(1), '1 second');
    expect(formatDurationSeconds(60), '1 minute');
    expect(formatDurationSeconds(3600), '1 hour');
    expect(formatDurationSeconds(86400), '1 day');
    expect(desktopL10n.minutesAgo(1), '1 minute ago');
    expect(desktopL10n.hoursAgo(1), '1 hour ago');
    expect(desktopL10n.daysAgo(1), '1 day ago');
    expect(desktopL10n.devicesOnline(1), '1 device online');
    expect(desktopL10n.apps(2), '2 apps');
    expect(
      formatRelativeTime(
        DateTime.now().subtract(const Duration(minutes: 2)).toIso8601String(),
      ),
      contains('minutes ago'),
    );
  });
}
