import 'package:b13_falcon/app.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/features/settings/settings_page.dart';
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
    var settings = const AppSettings();

    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            return SettingsPage(
              settings: settings,
              onChanged: (nextSettings) {
                setState(() => settings = nextSettings);
              },
              onReset: () {},
              onBack: () {},
              supportedResolutions: const ['720p', '1080p'],
              supportedFps: const [30, 60],
              hevcSupported: false,
            );
          },
        ),
      ),
    );

    final gameAudioOption = find.widgetWithText(ChoiceChip, 'Som do jogo');
    expect(tester.widget<ChoiceChip>(gameAudioOption).selected, isFalse);

    await tester.ensureVisible(gameAudioOption);
    await tester.pumpAndSettle();
    await tester.tap(gameAudioOption);
    await tester.pumpAndSettle();

    expect(settings.audioMode, 'internal');
    expect(tester.widget<ChoiceChip>(gameAudioOption).selected, isTrue);
  });

  testWidgets('maximum quality preset provides high-resolution bitrate', (
    tester,
  ) async {
    const settings = AppSettings(
      preferredResolution: '1440p',
      preferredFps: 90,
    );

    expect(
      SettingsPage.recommendedBitrate(
        '1440p',
        90,
        'maximum',
        sourceWidth: 1440,
        sourceHeight: 2560,
      ),
      80000,
    );
    expect(
      settings
          .copyWith(bitratePreset: 'maximum', bitrateKbps: 80000)
          .bitrateKbps,
      80000,
    );
  });

  test('native bitrate uses the physical display dimensions', () {
    expect(
      SettingsPage.recommendedBitrate(
        'native',
        30,
        'high',
        sourceWidth: 1440,
        sourceHeight: 3200,
      ),
      27648,
    );
  });
}
