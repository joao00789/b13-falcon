import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../data/models/app_settings.dart';

class SettingsService {
  static const String _storageKey = 'b13_falcon_settings';

  Future<AppSettings> loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null || raw.isEmpty) {
      return const AppSettings();
    }

    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) {
        return AppSettings.fromJson(decoded);
      }
    } catch (_) {}

    return const AppSettings();
  }

  Future<void> saveSettings(AppSettings settings) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, jsonEncode(settings.toJson()));
  }

  Future<void> resetSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_storageKey);
  }
}
