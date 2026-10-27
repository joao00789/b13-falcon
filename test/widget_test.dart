import 'package:b13_falcon/app.dart';
import 'package:b13_falcon/core/theme/app_theme_data.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/data/models/camera_overlay_settings.dart';
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
      cameraOverlay: CameraOverlaySettings(
        enabled: true,
        shape: 'rounded',
        borderColor: 0xFF168AAD - 0x100000000,
        borderWidthDp: 7,
        sizeDp: 174,
        nameTagText: 'Canal Falcon',
      ),
    );
    final restored = AppSettings.fromJson(settings.toJson());

    expect(restored.cameraOverlay.enabled, isTrue);
    expect(restored.cameraOverlay.shape, 'rounded');
    expect(
      restored.cameraOverlay.borderColor,
      settings.cameraOverlay.borderColor,
    );
    expect(restored.cameraOverlay.borderWidthDp, 7);
    expect(restored.cameraOverlay.sizeDp, 174);
    expect(restored.cameraOverlay.nameTagText, 'Canal Falcon');

    final recovered = AppSettings.fromJson({
      'cameraConfigJson': {
        'enabled': true,
        'shape': 'invalid',
        'sizeDp': 500,
        'border': {'color': 0xFF123456, 'style': 'invalid', 'widthDp': 100},
        'nameTag': {'text': List.filled(25, 'A').join()},
        'position': {'xFraction': -1, 'yFraction': 2},
      },
      'frontCameraEnabled': 'true',
    });
    expect(recovered.cameraOverlay.shape, 'circle');
    expect(recovered.cameraOverlay.borderColor, 0xFF123456 - 0x100000000);
    expect(recovered.cameraOverlay.borderStyle, 'solid');
    expect(recovered.cameraOverlay.borderWidthDp, 16);
    expect(recovered.cameraOverlay.sizeDp, 260);
    expect(recovered.cameraOverlay.nameTagText, List.filled(24, 'A').join());
    expect(recovered.cameraOverlay.xFraction, 0);
    expect(recovered.cameraOverlay.yFraction, 1);
    expect(recovered.cameraOverlay.enabled, isTrue);
  });

  test('legacy camera settings migrate without losing user choices', () {
    final settings = AppSettings.fromJson({
      'frontCameraEnabled': true,
      'cameraShape': 'rounded',
      'cameraBorderColor': 0xFF123456,
      'cameraBorderWidthDp': 7,
      'cameraPreviewSizeDp': 174,
    });

    expect(settings.cameraOverlay.enabled, isTrue);
    expect(settings.cameraOverlay.shape, 'rounded');
    expect(settings.cameraOverlay.borderColor, 0xFF123456 - 0x100000000);
    expect(settings.cameraOverlay.borderWidthDp, 7);
    expect(settings.cameraOverlay.sizeDp, 174);
    expect(settings.toJson(), contains('cameraConfigJson'));
    expect(settings.toJson(), isNot(contains('frontCameraEnabled')));
  });

  test('camera config can be serialized as the native bridge JSON string', () {
    const camera = CameraOverlaySettings(
      enabled: true,
      mode: 'baked',
      shape: 'square',
      borderStyle: 'gradient',
      nameTagText: 'B13 FALCON',
    );
    final restored = CameraOverlaySettings.fromJson(camera.configJson);

    expect(restored.enabled, isTrue);
    expect(restored.mode, 'baked');
    expect(restored.shape, 'square');
    expect(restored.borderStyle, 'gradient');
    expect(restored.nameTagText, 'B13 FALCON');
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
