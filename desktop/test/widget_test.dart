import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ksuser_auth_desktop/main.dart';

void main() {
  testWidgets('shows desktop auth shell', (WidgetTester tester) async {
    await tester.pumpWidget(const KsuserDesktopApp());
    await tester.pump();

    expect(find.text(kDesktopAppName), findsOneWidget);
    expect(find.text('二维码登录'), findsOneWidget);
  });

  testWidgets('desktop workspace renders with sidebar content', (
    WidgetTester tester,
  ) async {
    final AppController controller = AppController(
      KsuserApiClient(baseUrl: 'http://localhost:8000'),
      environmentName: 'Development',
      passkeyOrigin: 'http://localhost:5173',
    );

    controller.user = UserDetails(
      uuid: 'uuid-1',
      username: 'tester',
      email: 'tester@example.com',
      updatedAt: '2026-04-09T12:00:00',
      settings: const UserSettings(),
    );
    controller.sessions = <SessionItem>[
      const SessionItem(
        id: 1,
        ipAddress: '127.0.0.1',
        ipLocation: '本机',
        userAgent: 'macOS',
        browser: 'Desktop',
        deviceType: 'Mac',
        online: true,
        current: true,
        createdAt: '2026-04-09T10:00:00',
        lastSeenAt: '2026-04-09T12:00:00',
        expiresAt: '2026-04-16T12:00:00',
        revokedAt: null,
      ),
    ];
    controller.sensitiveLogs = <SensitiveLogItem>[
      const SensitiveLogItem(
        id: 1,
        operationType: 'LOGIN',
        result: 'SUCCESS',
        loginMethod: 'password',
        ipAddress: '127.0.0.1',
        ipLocation: '本机',
        browser: 'Desktop',
        deviceType: 'Mac',
        failureReason: null,
        riskScore: 0,
        createdAt: '2026-04-09T12:00:00',
      ),
    ];
    controller.passkeys = <PasskeyListItem>[];
    controller.totpStatus = const TotpStatusResponse(
      enabled: false,
      recoveryCodesCount: 0,
    );

    await tester.binding.setSurfaceSize(const Size(1440, 920));
    addTearDown(() async {
      await tester.binding.setSurfaceSize(null);
    });

    await tester.pumpWidget(
      MaterialApp(home: DesktopWorkspace(controller: controller)),
    );
    await tester.pumpAndSettle();

    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byIcon(Icons.logout_rounded), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
