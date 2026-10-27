import 'package:b13_falcon/app.dart';
import 'package:b13_falcon/core/theme/app_theme_data.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/features/settings/settings_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('theme accent settings survive serialization', () {
    const settings = AppSettings(themeAccent: 'black');
    final restored = AppSettings.fromJson(settings.toJson());

    expect(restored.themeAccent, 'black');
    expect(restored.accentColor, const Color(0xFFC9A85A));
  });

  test('settings safely recover invalid saved values', () {
    final settings = AppSettings.fromJson({
      'isDarkMode': 'true',
      'preferredResolution': 1080,
      'preferredFps': 29.97,
      'bitrateKbps': 90000.5,
      'floatingLogoOpacity': 2.0,
      'floatingLogoSizeDp': 120,
      'themeAccent': 'unknown',
      'codec': 'invalid',
    });

    expect(settings.isDarkMode, isTrue);
    expect(settings.preferredResolution, '1080p');
    expect(settings.preferredFps, 30);
    expect(settings.bitrateKbps, 80000);
    expect(settings.floatingLogoOpacity, 1);
    expect(settings.floatingLogoSizeDp, 96);
    expect(settings.themeAccent, 'falcon');
    expect(settings.codec, 'h264');
  });

  test('all built-in theme ids can be saved and loaded', () {
    for (final theme in AppThemeData.themes) {
      final restored = AppSettings.fromJson(
        AppSettings(themeAccent: theme.id).toJson(),
      );
      expect(restored.themeAccent, theme.id);
    }

    expect(AppThemeData.fromId('black').name, 'Black');
  });

  test('camera overlay settings survive serialization safely', () {
    const settings = AppSettings(
      frontCameraEnabled: true,
      cameraShape: 'rounded',
      cameraBorderColor: 0xFF168AAD - 0x100000000,
      cameraBorderWidthDp: 7,
    );
    final restored = AppSettings.fromJson(settings.toJson());

    expect(restored.frontCameraEnabled, isTrue);
    expect(restored.cameraShape, 'rounded');
    expect(restored.cameraBorderColor, settings.cameraBorderColor);
    expect(restored.cameraBorderWidthDp, 7);

    final recovered = AppSettings.fromJson({
      'cameraShape': 'invalid',
      'cameraBorderColor': 0xFF123456,
      'cameraBorderWidthDp': 100,
      'frontCameraEnabled': 'true',
    });
    expect(recovered.cameraShape, 'circle');
    expect(recovered.cameraBorderColor, -1);
    expect(recovered.cameraBorderWidthDp, 12);
    expect(recovered.frontCameraEnabled, isFalse);
  });

  testWidgets('B13 FALCON app loads', (tester) async {
    SharedPreferences.setMockInitialValues({});
    await tester.pumpWidget(const B13FalconApp());
    expect(find.text('B13 FALCON'), findsWidgets);
  });

  test(
    'audio capture mode can be normalized and persisted from legacy labels',
    () {
      expect(AppSettings.normalizeAudioMode('Som do jogo'), 'internal');
      expect(AppSettings.normalizeAudioMode('Microfone'), 'microphone');
      expect(
        AppSettings.fromJson({'audioMode': 'Som do jogo'}).audioMode,
        'internal',
      );
      expect(
        AppSettings.fromJson({'audioMode': 'Microfone'}).audioMode,
        'microphone',
      );
    },
  );

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
