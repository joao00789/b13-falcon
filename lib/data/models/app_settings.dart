import 'package:flutter/material.dart';

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
    );
  }

  ThemeMode get themeMode => isDarkMode ? ThemeMode.dark : ThemeMode.light;

  Color get accentColor => switch (themeAccent) {
    'ocean' => const Color(0xFF168AAD),
    'emerald' => const Color(0xFF16865A),
    'violet' => const Color(0xFF7950B8),
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
  };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return AppSettings(
      isDarkMode: json['isDarkMode'] as bool? ?? true,
      preferredResolution: json['preferredResolution'] as String? ?? '1080p',
      preferredFps: json['preferredFps'] as int? ?? 30,
      bitrateKbps: json['bitrateKbps'] as int? ?? 16000,
      audioMode: normalizeAudioMode(json['audioMode']),
      keepScreenOn: json['keepScreenOn'] as bool? ?? true,
      showFloatingControls: json['showFloatingControls'] as bool? ?? true,
      floatingLogoOpacity:
          (json['floatingLogoOpacity'] as num?)
              ?.toDouble()
              .clamp(0, 1.0)
              .toDouble() ??
          0.85,
      floatingLogoSizeDp:
          (json['floatingLogoSizeDp'] as num?)?.toInt().clamp(32, 96) ?? 56,
      useInternalAudio: json['useInternalAudio'] as bool? ?? false,
      themeAccent: switch (json['themeAccent'] as String?) {
        'ocean' || 'emerald' || 'violet' => json['themeAccent'] as String,
        _ => 'falcon',
      },
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
    );
  }
}
