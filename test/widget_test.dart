import 'package:b13_falcon/app.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('theme accent settings survive serialization', () {
    const settings = AppSettings(themeAccent: 'ocean');
    final restored = AppSettings.fromJson(settings.toJson());

    expect(restored.themeAccent, 'ocean');
    expect(restored.accentColor, const Color(0xFF168AAD));
  });

  testWidgets('B13 FALCON app loads', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const B13FalconApp());
    expect(find.text('B13 FALCON'), findsOneWidget);
  });

  testWidgets('audio capture mode can be changed in settings', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const B13FalconApp());
    await tester.pumpAndSettle();

    await tester.tap(find.text('Ajustes').first);
    await tester.pumpAndSettle();

    final gameAudioOption = find.widgetWithText(ChoiceChip, 'Som do jogo');
    expect(tester.widget<ChoiceChip>(gameAudioOption).selected, isFalse);

    await tester.ensureVisible(gameAudioOption);
    await tester.pumpAndSettle();
    await tester.tap(gameAudioOption);
    await tester.pumpAndSettle();

    expect(tester.widget<ChoiceChip>(gameAudioOption).selected, isTrue);
  });
}
