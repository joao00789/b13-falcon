import 'package:flutter/material.dart';

class AppSettings {
  const AppSettings({
    this.isDarkMode = true,
    this.preferredResolution = '720p',
    this.preferredFps = 30,
    this.bitrateKbps = 8000,
    this.audioMode = 'Microfone',
    this.keepScreenOn = true,
    this.showFloatingControls = false,
    this.useInternalAudio = false,
  });

  final bool isDarkMode;
  final String preferredResolution;
  final int preferredFps;
  final int bitrateKbps;
  final String audioMode;
  final bool keepScreenOn;
  final bool showFloatingControls;
  final bool useInternalAudio;

  AppSettings copyWith({
    bool? isDarkMode,
    String? preferredResolution,
    int? preferredFps,
    int? bitrateKbps,
    String? audioMode,
    bool? keepScreenOn,
    bool? showFloatingControls,
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
    'useInternalAudio': useInternalAudio,
  };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return AppSettings(
      isDarkMode: json['isDarkMode'] as bool? ?? true,
      preferredResolution: json['preferredResolution'] as String? ?? '720p',
      preferredFps: json['preferredFps'] as int? ?? 30,
      bitrateKbps: json['bitrateKbps'] as int? ?? 8000,
      audioMode: json['audioMode'] as String? ?? 'Microfone',
      keepScreenOn: json['keepScreenOn'] as bool? ?? true,
      showFloatingControls: json['showFloatingControls'] as bool? ?? false,
      useInternalAudio: json['useInternalAudio'] as bool? ?? false,
    );
  }
}
