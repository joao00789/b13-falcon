import 'dart:async';

import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/theme/app_theme.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/data/models/saved_recording.dart';
import 'package:b13_falcon/features/home/home_page.dart';
import 'package:b13_falcon/features/library/library_page.dart';
import 'package:b13_falcon/features/settings/settings_page.dart';
import 'package:b13_falcon/services/native_recorder.dart';
import 'package:b13_falcon/services/settings_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class B13FalconApp extends StatefulWidget {
  const B13FalconApp({super.key});

  @override
  State<B13FalconApp> createState() => _B13FalconAppState();
}

class _B13FalconAppState extends State<B13FalconApp> {
  final _navigatorKey = GlobalKey<NavigatorState>();
  final _messengerKey = GlobalKey<ScaffoldMessengerState>();
  final _settingsService = SettingsService();
  final _nativeRecorder = RecordingNativeBridge();
  AppSettings _settings = const AppSettings();
  List<SavedRecording> _recordings = const [];
  bool _isRecording = false;
  bool _isPaused = false;
  bool _floatingMenuExpanded = false;
  Duration _elapsed = Duration.zero;
  Timer? _timer;
  int _selectedIndex = 0;
  String _statusMessage = 'Sistema pronto para iniciar a gravação.';
  String? _recordingsError;

  @override
  void initState() {
    super.initState();
    _nativeRecorder.setRecordingStatusHandler(_onNativeRecordingStatus);
    unawaited(_loadSettings());
    unawaited(_loadRecordings());
  }

  void _onNativeRecordingStatus(Map<dynamic, dynamic> status) {
    if (!mounted) return;
    final state = status['state'] as String?;
    setState(() {
      _isRecording = state == 'recording' || state == 'paused';
      _isPaused = state == 'paused';
      if (state == 'stopped' || state == 'error') {
        _isRecording = false;
        _isPaused = false;
        _elapsed = Duration.zero;
      }
      _statusMessage = status['message'] as String? ?? _statusMessage;
    });
    if (state == 'stopped' || state == 'error') {
      _timer?.cancel();
      unawaited(_loadRecordings());
    } else if (state == 'recording' && _timer == null) {
      _startTicker();
    }
  }

  Future<void> _loadSettings() async {
    final settings = await _settingsService.loadSettings();
    if (!mounted) return;
    setState(() => _settings = settings);
    try {
      await _nativeRecorder.setKeepScreenOn(settings.keepScreenOn);
    } on PlatformException catch (error) {
      if (mounted) {
        setState(
          () => _statusMessage =
              'Não foi possível aplicar a opção de tela ligada: ${error.message}',
        );
      }
    } on MissingPluginException {
      // Native settings are unavailable in widget previews and non-Android builds.
    }
  }

  Future<void> _loadRecordings() async {
    if (mounted) {
      setState(() => _recordingsError = null);
    }
    try {
      final recordings = await _nativeRecorder.getRecordings();
      if (!mounted) return;
      setState(() {
        _recordings = recordings;
        _recordingsError = null;
      });
    } on PlatformException catch (error) {
      if (!mounted) return;
      setState(() {
        _recordingsError =
            error.message ?? 'Não foi possível carregar os vídeos salvos.';
      });
    } on MissingPluginException {
      if (!mounted) return;
      setState(() {
        _recordingsError = 'A biblioteca de vídeos está disponível no Android.';
      });
    }
  }

  void _startTicker() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (_isRecording && !_isPaused) {
        setState(() => _elapsed += const Duration(seconds: 1));
      }
    });
  }

  Future<void> _toggleRecording() async {
    if (!_isRecording) {
      if (_settings.showFloatingControls) {
        try {
          final granted = await _nativeRecorder.requestOverlayPermission();
          if (!mounted) return;
          if (!granted) {
            _showMessage(
              'Sem permissão de sobreposição: a gravação começará sem os '
              'controles flutuantes.',
            );
          }
        } on PlatformException catch (error) {
          _showMessage(
            error.message ??
                'Não foi possível solicitar a permissão flutuante.',
          );
        } on MissingPluginException {
          _showMessage('Os controles flutuantes estão disponíveis no Android.');
        }
      }
      final result = await _nativeRecorder.startRecording(
        title: 'B13_FALCON_${DateTime.now().millisecondsSinceEpoch}',
        resolution: _settings.preferredResolution,
        fps: _settings.preferredFps,
        bitrateKbps: _settings.bitrateKbps,
        audioMode: _settings.audioMode,
        showFloatingControls: _settings.showFloatingControls,
      );
      if (!mounted) return;
      if (result['success'] == true) {
        setState(() {
          _isRecording = true;
          _isPaused = false;
          _elapsed = Duration.zero;
          _statusMessage = 'Gravação em andamento.';
          _floatingMenuExpanded = false;
        });
        _startTicker();
      } else {
        setState(() {
          _statusMessage =
              result['message'] as String? ??
              'Não foi possível iniciar a gravação.';
        });
      }
      return;
    }

    final recordingCountBeforeStop = _recordings.length;
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
      for (var attempt = 0; attempt < 6; attempt++) {
        await Future<void>.delayed(const Duration(milliseconds: 500));
        await _loadRecordings();
        if (_recordings.length > recordingCountBeforeStop ||
            _recordingsError != null) {
          break;
        }
      }
    } else {
      setState(() {
        _statusMessage =
            result['message'] as String? ?? 'Erro ao encerrar a gravação.';
      });
    }
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
    } else {
      setState(() {
        _statusMessage =
            result['message'] as String? ??
            'Não foi possível alterar o estado da gravação.';
      });
    }
  }

  Future<void> _saveSettings(AppSettings nextSettings) async {
    if (nextSettings.keepScreenOn != _settings.keepScreenOn) {
      try {
        await _nativeRecorder.setKeepScreenOn(nextSettings.keepScreenOn);
      } on PlatformException catch (error) {
        _showMessage(
          'Não foi possível alterar a opção de tela ligada: '
          '${error.message ?? 'erro desconhecido'}',
        );
        return;
      } on MissingPluginException {
        _showMessage(
          'Esta opção está disponível apenas no aplicativo Android.',
        );
        return;
      }
    }
    await _settingsService.saveSettings(nextSettings);
    if (mounted) setState(() => _settings = nextSettings);
  }

  Future<void> _resetSettings() async {
    await _settingsService.resetSettings();
    if (!mounted) return;
    setState(() => _settings = const AppSettings());
    try {
      await _nativeRecorder.setKeepScreenOn(_settings.keepScreenOn);
    } on PlatformException catch (error) {
      _showMessage(
        'Configurações restauradas, mas não foi possível aplicar a tela ligada: '
        '${error.message ?? 'erro desconhecido'}',
      );
    } on MissingPluginException {
      // Native settings are unavailable in widget previews and non-Android builds.
    }
  }

  Future<void> _openRecording(SavedRecording recording) async {
    try {
      await _nativeRecorder.openRecording(recording.uri);
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Não foi possível abrir o vídeo.');
    } on MissingPluginException {
      _showMessage('A reprodução está disponível no aplicativo Android.');
    }
  }

  Future<void> _deleteRecording(SavedRecording recording) async {
    final dialogContext = _navigatorKey.currentContext;
    if (dialogContext == null) return;
    final confirmed = await showDialog<bool>(
      context: dialogContext,
      builder: (context) => AlertDialog(
        title: const Text('Apagar vídeo?'),
        content: Text('O arquivo "${recording.name}" será removido.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Apagar'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await _nativeRecorder.deleteRecording(recording.uri);
      await _loadRecordings();
      _showMessage('Vídeo apagado.');
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Não foi possível apagar o vídeo.');
    }
  }

  void _showMessage(String message) {
    final messenger = _messengerKey.currentState;
    if (messenger == null) return;
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  void _goHome() {
    if (_selectedIndex != 0) setState(() => _selectedIndex = 0);
  }

  void _openLibrary() {
    setState(() {
      _selectedIndex = 1;
      _floatingMenuExpanded = false;
    });
    unawaited(_loadRecordings());
  }

  void _openSettings() {
    setState(() {
      _selectedIndex = 2;
      _floatingMenuExpanded = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomePage(
        settings: _settings,
        isRecording: _isRecording,
        isPaused: _isPaused,
        elapsed: _elapsed,
        statusMessage: _statusMessage,
        recordings: _recordings,
        onRecordToggle: _toggleRecording,
        onPauseToggle: _togglePause,
        onOpenLibrary: _openLibrary,
        onOpenSettings: _openSettings,
        onOpenRecording: _openRecording,
      ),
      LibraryPage(
        recordings: _recordings,
        errorMessage: _recordingsError,
        onRefresh: _loadRecordings,
        onOpenRecording: _openRecording,
        onDeleteRecording: _deleteRecording,
        onBack: _goHome,
      ),
      SettingsPage(
        settings: _settings,
        onChanged: _saveSettings,
        onReset: _resetSettings,
        onBack: _goHome,
      ),
    ];

    return MaterialApp(
      navigatorKey: _navigatorKey,
      scaffoldMessengerKey: _messengerKey,
      title: AppStrings.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme(),
      darkTheme: AppTheme.darkTheme(),
      themeMode: _settings.themeMode,
      home: Builder(
        builder: (context) => PopScope(
          canPop: _selectedIndex == 0,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) _goHome();
          },
          child: Scaffold(
            body: IndexedStack(index: _selectedIndex, children: pages),
            floatingActionButton: _settings.showFloatingControls
                ? _buildFloatingControls()
                : null,
            floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
            bottomNavigationBar: _buildFloatingNavigation(context),
          ),
        ),
      ),
    );
  }

  Widget _buildFloatingControls() {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        if (_floatingMenuExpanded && _isRecording) ...[
          FloatingActionButton.small(
            heroTag: 'floating-stop',
            tooltip: 'Parar gravação',
            onPressed: _toggleRecording,
            child: const Icon(Icons.stop_rounded),
          ),
          const SizedBox(height: 10),
        ],
        if (_floatingMenuExpanded && !_isRecording) ...[
          FloatingActionButton.small(
            heroTag: 'floating-start',
            tooltip: 'Iniciar gravação',
            onPressed: _toggleRecording,
            child: const Icon(Icons.fiber_manual_record_rounded),
          ),
          const SizedBox(height: 10),
        ],
        if (_floatingMenuExpanded && _isRecording) ...[
          FloatingActionButton.small(
            heroTag: 'floating-pause',
            tooltip: _isPaused ? 'Retomar gravação' : 'Pausar gravação',
            onPressed: _togglePause,
            child: Icon(
              _isPaused ? Icons.play_arrow_rounded : Icons.pause_rounded,
            ),
          ),
          const SizedBox(height: 10),
        ],
        if (_floatingMenuExpanded) ...[
          FloatingActionButton.small(
            heroTag: 'floating-library',
            tooltip: 'Abrir biblioteca',
            onPressed: _openLibrary,
            child: const Icon(Icons.video_library_outlined),
          ),
          const SizedBox(height: 10),
        ],
        FloatingActionButton(
          heroTag: 'floating-main',
          tooltip: _floatingMenuExpanded
              ? 'Fechar menu flutuante'
              : 'Abrir menu flutuante',
          onPressed: () =>
              setState(() => _floatingMenuExpanded = !_floatingMenuExpanded),
          child: Icon(
            _floatingMenuExpanded
                ? Icons.close_rounded
                : Icons.more_horiz_rounded,
          ),
        ),
      ],
    );
  }

  Widget _buildFloatingNavigation(BuildContext context) {
    const destinations = [
      (Icons.home_rounded, 'Início'),
      (Icons.video_library_rounded, 'Vídeos'),
      (Icons.settings_rounded, 'Ajustes'),
    ];
    return SafeArea(
      minimum: const EdgeInsets.fromLTRB(20, 4, 20, 10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: Theme.of(context).dividerColor),
          boxShadow: const [
            BoxShadow(
              color: Color(0x33000000),
              blurRadius: 18,
              offset: Offset(0, 6),
            ),
          ],
        ),
        child: Row(
          children: List.generate(destinations.length, (index) {
            final destination = destinations[index];
            final selected = _selectedIndex == index;
            return Expanded(
              child: Semantics(
                button: true,
                selected: selected,
                label: destination.$2,
                child: InkWell(
                  borderRadius: BorderRadius.circular(22),
                  onTap: () {
                    setState(() {
                      _selectedIndex = index;
                      _floatingMenuExpanded = false;
                    });
                    if (index == 1) unawaited(_loadRecordings());
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          destination.$1,
                          color: selected
                              ? Theme.of(context).colorScheme.primary
                              : Theme.of(context).iconTheme.color,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          destination.$2,
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    _nativeRecorder.setRecordingStatusHandler(null);
    super.dispose();
  }
}
