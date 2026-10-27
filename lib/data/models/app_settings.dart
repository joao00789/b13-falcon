import 'package:flutter/material.dart';

import 'camera_overlay_settings.dart';

class AppSettings {
  const AppSettings({
    this.isDarkMode = true,
    this.preferredResolution = '1080p',
    this.preferredFps = 30,
    this.bitrateKbps = 16000,
    this.audioMode = 'both',
    this.keepScreenOn = true,
    this.showFloatingControls = true,
    this.floatingLogoOpacity = 0.85,
    this.floatingLogoSizeDp = 56,
    this.useInternalAudio = false,
    this.themeAccent = 'falcon',
    this.codec = 'h264',
    this.orientation = 'auto',
    this.bitratePreset = 'custom',
    this.cameraOverlay = const CameraOverlaySettings(),
  });

  static String normalizeAudioMode(Object? value) {
    return switch (value?.toString()) {
      'both' || 'Interno + microfone' => 'both',
      'internal' || 'Som do jogo' => 'internal',
      'microphone' || 'Microfone' => 'microphone',
      'none' || 'Sem áudio' => 'none',
      _ => 'both',
    };
  }

  static const _validResolutions = {
    'native',
    '360p',
    '480p',
    '540p',
    '720p',
    '1080p',
    '1440p',
  };
  static const _validFps = {15, 24, 30, 45, 60, 90};
  static const _validThemes = {
    'falcon',
    'ocean',
    'emerald',
    'violet',
    'sunset',
    'rose',
    'arctic',
    'black',
  };
  static int _readInt(Object? value, int fallback) =>
      value is num && value.isFinite ? value.round() : fallback;

  static String _readString(Object? value, String fallback) =>
      value is String ? value : fallback;

  static bool _readBool(Object? value, bool fallback) =>
      value is bool ? value : fallback;

  final bool isDarkMode;
  final String preferredResolution;
  final int preferredFps;
  final int bitrateKbps;
  final String audioMode;
  final bool keepScreenOn;
  final bool showFloatingControls;
  final double floatingLogoOpacity;
  final int floatingLogoSizeDp;
  final bool useInternalAudio;
  final String themeAccent;
  final String codec;
  final String orientation;
  final String bitratePreset;
  final CameraOverlaySettings cameraOverlay;

  AppSettings copyWith({
    bool? isDarkMode,
    String? preferredResolution,
    int? preferredFps,
    int? bitrateKbps,
    String? audioMode,
    bool? keepScreenOn,
    bool? showFloatingControls,
    double? floatingLogoOpacity,
    int? floatingLogoSizeDp,
    bool? useInternalAudio,
    String? themeAccent,
    String? codec,
    String? orientation,
    String? bitratePreset,
    CameraOverlaySettings? cameraOverlay,
  }) {
    return AppSettings(
      isDarkMode: isDarkMode ?? this.isDarkMode,
      preferredResolution: preferredResolution ?? this.preferredResolution,
      preferredFps: preferredFps ?? this.preferredFps,
      bitrateKbps: bitrateKbps ?? this.bitrateKbps,
      audioMode: audioMode ?? this.audioMode,
      keepScreenOn: keepScreenOn ?? this.keepScreenOn,
      showFloatingControls: showFloatingControls ?? this.showFloatingControls,
      floatingLogoOpacity: floatingLogoOpacity ?? this.floatingLogoOpacity,
      floatingLogoSizeDp: floatingLogoSizeDp ?? this.floatingLogoSizeDp,
      useInternalAudio: useInternalAudio ?? this.useInternalAudio,
      themeAccent: themeAccent ?? this.themeAccent,
      codec: codec ?? this.codec,
      orientation: orientation ?? this.orientation,
      bitratePreset: bitratePreset ?? this.bitratePreset,
      cameraOverlay: cameraOverlay ?? this.cameraOverlay,
    );
  }

  ThemeMode get themeMode => isDarkMode ? ThemeMode.dark : ThemeMode.light;

  Color get accentColor => switch (themeAccent) {
    'ocean' => const Color(0xFF168AAD),
    'emerald' => const Color(0xFF16865A),
    'violet' => const Color(0xFF7950B8),
    'sunset' => const Color(0xFFE45B37),
    'rose' => const Color(0xFFD64078),
    'arctic' => const Color(0xFF4E9CC7),
    'black' => const Color(0xFFC9A85A),
    _ => const Color(0xFFB81C2B),
  };

  Map<String, dynamic> toJson() => {
    'isDarkMode': isDarkMode,
    'preferredResolution': preferredResolution,
    'preferredFps': preferredFps,
    'bitrateKbps': bitrateKbps,
    'audioMode': audioMode,
    'keepScreenOn': keepScreenOn,
    'showFloatingControls': showFloatingControls,
    'floatingLogoOpacity': floatingLogoOpacity,
    'floatingLogoSizeDp': floatingLogoSizeDp,
    'useInternalAudio': useInternalAudio,
    'themeAccent': themeAccent,
    'codec': codec,
    'orientation': orientation,
    'bitratePreset': bitratePreset,
    'cameraConfigJson': cameraOverlay.configJson,
  };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    final resolution = _readString(json['preferredResolution'], '1080p');
    final fps = _readInt(json['preferredFps'], 30);
    final theme = _readString(json['themeAccent'], 'falcon');
    final opacityValue = json['floatingLogoOpacity'];
    final opacity = opacityValue is num && opacityValue.isFinite
        ? opacityValue.toDouble().clamp(0, 1).toDouble()
        : 0.85;
    final size = _readInt(json['floatingLogoSizeDp'], 56);
    final bitrate = _readInt(json['bitrateKbps'], 16000);
    final cameraConfig = json['cameraConfigJson'] == null
        ? CameraOverlaySettings.fromLegacy(json)
        : CameraOverlaySettings.fromJson(json['cameraConfigJson']);

    return AppSettings(
      isDarkMode: _readBool(json['isDarkMode'], true),
      preferredResolution: _validResolutions.contains(resolution)
          ? resolution
          : '1080p',
      preferredFps: _validFps.contains(fps) ? fps : 30,
      bitrateKbps: bitrate.clamp(4000, 80000).toInt(),
      audioMode: normalizeAudioMode(json['audioMode']),
      keepScreenOn: _readBool(json['keepScreenOn'], true),
      showFloatingControls: _readBool(json['showFloatingControls'], true),
      floatingLogoOpacity: opacity,
      floatingLogoSizeDp: size.clamp(32, 96).toInt(),
      useInternalAudio: _readBool(json['useInternalAudio'], false),
      themeAccent: _validThemes.contains(theme) ? theme : 'falcon',
      codec: json['codec'] == 'hevc' ? 'hevc' : 'h264',
      orientation: switch (json['orientation']) {
        'portrait' || 'landscape' => json['orientation'] as String,
        _ => 'auto',
      },
      bitratePreset: switch (json['bitratePreset']) {
        'economy' ||
        'balanced' ||
        'high' ||
        'maximum' => json['bitratePreset'] as String,
        _ => 'custom',
      },
      cameraOverlay: cameraConfig,
    );
  }
}
