import 'package:b13_falcon/app.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('B13 FALCON app loads', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const B13FalconApp());
    expect(find.text('B13 FALCON'), findsOneWidget);
  });
}
