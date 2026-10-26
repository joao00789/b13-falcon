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
    this.useInternalAudio = false,
  });

  final bool isDarkMode;
  final String preferredResolution;
  final int preferredFps;
  final int bitrateKbps;
  final String audioMode;
  final bool keepScreenOn;
  final bool showFloatingControls;
  final double floatingLogoOpacity;
  final bool useInternalAudio;

  AppSettings copyWith({
    bool? isDarkMode,
    String? preferredResolution,
    int? preferredFps,
    int? bitrateKbps,
    String? audioMode,
    bool? keepScreenOn,
    bool? showFloatingControls,
    double? floatingLogoOpacity,
    bool? useInternalAudio,
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
      useInternalAudio: useInternalAudio ?? this.useInternalAudio,
    );
  }

  ThemeMode get themeMode => isDarkMode ? ThemeMode.dark : ThemeMode.light;

  Map<String, dynamic> toJson() => {
    'isDarkMode': isDarkMode,
    'preferredResolution': preferredResolution,
    'preferredFps': preferredFps,
    'bitrateKbps': bitrateKbps,
    'audioMode': audioMode,
    'keepScreenOn': keepScreenOn,
    'showFloatingControls': showFloatingControls,
    'floatingLogoOpacity': floatingLogoOpacity,
    'useInternalAudio': useInternalAudio,
  };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return AppSettings(
      isDarkMode: json['isDarkMode'] as bool? ?? true,
      preferredResolution: json['preferredResolution'] as String? ?? '1080p',
      preferredFps: json['preferredFps'] as int? ?? 30,
      bitrateKbps: json['bitrateKbps'] as int? ?? 16000,
      audioMode: json['audioMode'] as String? ?? 'Microfone',
      keepScreenOn: json['keepScreenOn'] as bool? ?? true,
      showFloatingControls: json['showFloatingControls'] as bool? ?? true,
      floatingLogoOpacity:
          (json['floatingLogoOpacity'] as num?)
              ?.toDouble()
              .clamp(0.2, 1.0)
              .toDouble() ??
          0.85,
      useInternalAudio: json['useInternalAudio'] as bool? ?? false,
    );
  }
}
