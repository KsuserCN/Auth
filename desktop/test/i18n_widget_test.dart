import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ksuser_auth_desktop/i18n.dart';
import 'package:ksuser_auth_desktop/l10n/generated/app_localizations.dart';
import 'package:ksuser_auth_desktop/main.dart';

class MemoryLanguageStore extends DesktopLanguageStore {
  DesktopLanguage language = DesktopLanguage.system;
  @override
  Future<DesktopLanguage> load() async => language;
  @override
  Future<void> save(DesktopLanguage value) async {
    language = value;
  }
}

void main() {
  setUp(() {
    desktopLocaleController = DesktopLocaleController(
      preference: DesktopLanguage.simplifiedChinese,
      store: MemoryLanguageStore(),
    );
  });

  AppController buildController(KsuserApiClient client) => AppController(
    client,
    environmentName: 'Test',
    passkeyOrigin: 'http://localhost:5173',
    loadAgreementAcceptance: () async => true,
  );

  Widget testApp(AppController controller, {Widget? home}) => AnimatedBuilder(
    animation: controller,
    builder: (BuildContext context, _) => MaterialApp(
      locale: desktopLocaleController.locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: home ?? DesktopRoot(controller: controller),
    ),
  );

  void desktopSize(WidgetTester tester, {double width = 1440}) {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = Size(width, 920);
    addTearDown(() {
      tester.view.resetPhysicalSize();
      tester.view.resetDevicePixelRatio();
    });
  }

  testWidgets('live switch preserves entered sign-in credentials', (
    WidgetTester tester,
  ) async {
    desktopSize(tester);
    final AppController controller = buildController(
      KsuserApiClient(baseUrl: 'http://localhost:8000'),
    );

    await tester.pumpWidget(testApp(controller));

    await tester.pumpAndSettle();
    final Finder email = find.byWidgetPredicate(
      (Widget widget) =>
          widget is TextField && widget.decoration?.labelText == '邮箱地址',
    );
    final Finder password = find.byWidgetPredicate(
      (Widget widget) => widget is TextField && widget.obscureText,
    );

    await tester.enterText(email, 'person@example.com');
    await tester.enterText(password, 'EnteredPassword');

    desktopLocaleController.setLanguage(DesktopLanguage.english);

    await tester.pumpAndSettle();

    expect(find.text('Email address'), findsOneWidget);
    expect(find.text('person@example.com'), findsOneWidget);
    expect(
      tester.widget<TextField>(password).controller?.text,
      'EnteredPassword',
    );
    expect(find.text('QR sign-in'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
  });

  testWidgets('open settings translate immediately and reset to system', (
    WidgetTester tester,
  ) async {
    desktopSize(tester);
    final AppController controller = buildController(
      KsuserApiClient(baseUrl: 'http://localhost:8000'),
    );
    await tester.pumpWidget(
      testApp(
        controller,
        home: Builder(
          builder: (BuildContext context) => Scaffold(
            body: TextButton(
              onPressed: () => showDesktopSettingsDialog(context, controller),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text('语言'), findsOneWidget);
    await tester.tap(find.byType(DropdownButtonFormField<DesktopLanguage>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('English').last);
    await tester.pumpAndSettle();
    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('Language'), findsOneWidget);
    expect(find.text('Appearance'), findsOneWidget);
    expect(desktopLocaleController.preference, DesktopLanguage.english);
    await tester.tap(find.text('Restore defaults'));
    await tester.pumpAndSettle();
    expect(desktopLocaleController.preference, DesktopLanguage.system);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text(desktopL10n.done));
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
  });

  testWidgets(
    'English workspace fits the desktop minimum width and keeps session',
    (WidgetTester tester) async {
      desktopSize(tester, width: 1240);
      final KsuserApiClient client = KsuserApiClient(
        baseUrl: 'http://localhost:8000',
      )..accessToken = 'session-token';
      final AppController controller = buildController(client);
      controller.user = const UserDetails(
        uuid: 'uuid',
        username: 'UserName',
        email: 'user@example.com',
        settings: UserSettings(),
      );
      controller.adaptiveAuthStatus = AdaptiveAuthStatus.fromJson(
        <String, dynamic>{},
      );
      controller.totpStatus = const TotpStatusResponse(
        enabled: false,
        recoveryCodesCount: 0,
      );
      await tester.pumpWidget(testApp(controller));
      await tester.pumpAndSettle();
      desktopLocaleController.setLanguage(DesktopLanguage.english);
      await tester.pumpAndSettle();
      expect(controller.isAuthenticated, isTrue);
      expect(client.accessToken, 'session-token');
      expect(find.text('Account overview'), findsWidgets);
      expect(find.text('UserName'), findsWidgets);
      expect(tester.takeException(), isNull);
      for (final DesktopSection section in <DesktopSection>[
        DesktopSection.profile,
        DesktopSection.security,
        DesktopSection.devices,
        DesktopSection.activity,
      ]) {
        controller.setSection(section);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull, reason: section.name);
      }
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    },
  );
}
