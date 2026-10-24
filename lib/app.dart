import 'dart:async';

import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/theme/app_theme.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/features/home/home_page.dart';
import 'package:b13_falcon/features/library/library_page.dart';
import 'package:b13_falcon/features/settings/settings_page.dart';
import 'package:b13_falcon/services/native_recorder.dart';
import 'package:b13_falcon/services/settings_service.dart';
import 'package:flutter/material.dart';

class B13FalconApp extends StatefulWidget {
  const B13FalconApp({super.key});

  @override
  State<B13FalconApp> createState() => _B13FalconAppState();
}

class _B13FalconAppState extends State<B13FalconApp> {
  final _settingsService = SettingsService();
  final _nativeRecorder = RecordingNativeBridge();
  AppSettings _settings = const AppSettings();
  bool _isRecording = false;
  bool _isPaused = false;
  Duration _elapsed = Duration.zero;
  Timer? _timer;
  int _selectedIndex = 0;
  String _statusMessage = 'Sistema pronto para iniciar a gravação.';

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final settings = await _settingsService.loadSettings();
    if (!mounted) return;
    setState(() {
      _settings = settings;
    });
  }

  void startTicker() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() {
        if (_isRecording && !_isPaused) {
          _elapsed += const Duration(seconds: 1);
        }
      });
    });
  }

  Future<void> _toggleRecording() async {
    if (!_isRecording) {
      final result = await _nativeRecorder.startRecording(
        title: 'B13_FALCON_${DateTime.now().millisecondsSinceEpoch}',
        resolution: _settings.preferredResolution,
        fps: _settings.preferredFps,
        bitrateKbps: _settings.bitrateKbps,
        audioMode: _settings.audioMode,
      );
      if (!mounted) return;
      if (result['success'] == true) {
        setState(() {
          _isRecording = true;
          _isPaused = false;
          _statusMessage = 'Gravação em andamento.';
        });
        startTicker();
        return;
      }
      setState(() {
        _statusMessage = result['message'] as String? ?? 'Não foi possível iniciar a gravação.';
      });
      return;
    }

    final result = await _nativeRecorder.stopRecording();
    if (!mounted) return;
    if (result['success'] == true) {
      setState(() {
        _isRecording = false;
        _isPaused = false;
        _elapsed = Duration.zero;
        _statusMessage = 'Gravação finalizada com sucesso.';
      });
      _timer?.cancel();
      return;
    }
    setState(() {
      _statusMessage = result['message'] as String? ?? 'Erro ao encerrar a gravação.';
    });
  }

  Future<void> _togglePause() async {
    if (!_isRecording) return;
    final result = _isPaused
        ? await _nativeRecorder.resumeRecording()
        : await _nativeRecorder.pauseRecording();
    if (!mounted) return;
    if (result['success'] == true) {
      setState(() {
        _isPaused = !_isPaused;
        _statusMessage = _isPaused ? 'Gravação pausada.' : 'Gravação retomada.';
      });
      return;
    }
    setState(() {
      _statusMessage = result['message'] as String? ?? 'Não foi possível alterar o estado da gravação.';
    });
  }

  Future<void> _saveSettings(AppSettings nextSettings) async {
    await _settingsService.saveSettings(nextSettings);
    if (!mounted) return;
    setState(() {
      _settings = nextSettings;
    });
  }

  Future<void> _resetSettings() async {
    await _settingsService.resetSettings();
    if (!mounted) return;
    setState(() {
      _settings = const AppSettings();
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomePage(
        settings: _settings,
        isRecording: _isRecording,
        elapsed: _elapsed,
        statusMessage: _statusMessage,
        onRecordToggle: _toggleRecording,
        onPauseToggle: _togglePause,
        onOpenLibrary: () => setState(() => _selectedIndex = 1),
        onOpenSettings: () => setState(() => _selectedIndex = 2),
      ),
      const LibraryPage(),
      SettingsPage(
        settings: _settings,
        onChanged: _saveSettings,
        onReset: _resetSettings,
      ),
    ];

    return MaterialApp(
      title: AppStrings.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme(),
      darkTheme: AppTheme.darkTheme(),
      themeMode: _settings.themeMode,
      home: pages[_selectedIndex],
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
