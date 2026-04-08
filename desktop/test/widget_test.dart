import 'package:flutter_test/flutter_test.dart';
import 'package:ksuser_auth_desktop/main.dart';

void main() {
  testWidgets('shows desktop auth shell', (WidgetTester tester) async {
    await tester.pumpWidget(const KsuserDesktopApp());
    await tester.pump();

    expect(find.text('Ksuser Auth 统一认证中心'), findsOneWidget);
  });
}
