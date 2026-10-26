import 'dart:async';

import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/theme/app_theme.dart';
import 'package:b13_falcon/core/theme/app_theme_data.dart';
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
  String? _actualCaptureResolution;
  String? _recordingsError;
  List<String> _supportedResolutions = const [
    'native',
    '360p',
    '480p',
    '540p',
    '720p',
    '1080p',
    '1440p',
  ];
  List<int> _supportedFps = const [15, 24, 30, 45, 60, 90];
  bool _hevcSupported = false;

  @override
  void initState() {
    super.initState();
    _nativeRecorder.setRecordingStatusHandler(_onNativeRecordingStatus);
    unawaited(_loadSettings());
    unawaited(_loadRecordings());
    unawaited(_loadVideoCapabilities());
  }

  Future<void> _loadVideoCapabilities() async {
    try {
      final capabilities = await _nativeRecorder.getVideoCapabilities();
      if (!mounted) return;
      setState(() {
        _supportedResolutions =
            (capabilities['resolutions'] as List<dynamic>?)
                ?.whereType<String>()
                .toList() ??
            _supportedResolutions;
        _supportedFps =
            (capabilities['fps'] as List<dynamic>?)
                ?.whereType<int>()
                .toList() ??
            _supportedFps;
        _hevcSupported = capabilities['hevc'] as bool? ?? false;
      });
    } on PlatformException catch (error) {
      if (mounted) {
        _showMessage(
          error.message ?? 'Não foi possível consultar os codecs do aparelho.',
        );
      }
    } on MissingPluginException {
      // Native codec capabilities are unavailable in widget previews.
    }
  }

  void _onNativeRecordingStatus(Map<dynamic, dynamic> status) {
    if (!mounted) return;
    final state = status['state'] as String?;
    final actualWidth = status['actualWidth'] as int?;
    final actualHeight = status['actualHeight'] as int?;
    setState(() {
      if (actualWidth != null && actualHeight != null) {
        _actualCaptureResolution = '$actualWidth×$actualHeight';
      }
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
      if (settings.showFloatingControls) {
        await _nativeRecorder.setFloatingControls(
          enabled: true,
          opacity: settings.floatingLogoOpacity,
          sizeDp: settings.floatingLogoSizeDp,
          resolution: settings.preferredResolution,
          fps: settings.preferredFps,
          bitrateKbps: settings.bitrateKbps,
          audioMode: settings.audioMode,
          codec: settings.codec,
          orientation: settings.orientation,
          bitratePreset: settings.bitratePreset,
        );
      }
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
        floatingLogoOpacity: _settings.floatingLogoOpacity,
        floatingLogoSizeDp: _settings.floatingLogoSizeDp,
        codec: _settings.codec,
        orientation: _settings.orientation,
        bitratePreset: _settings.bitratePreset,
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
    if (nextSettings.showFloatingControls != _settings.showFloatingControls) {
      if (nextSettings.showFloatingControls) {
        final permitted = await _nativeRecorder.requestOverlayPermission();
        if (!permitted) {
          _showMessage(
            'A permissão de sobreposição é necessária para a bolha.',
          );
          nextSettings = nextSettings.copyWith(showFloatingControls: false);
        }
      }
      try {
        final enabled = await _nativeRecorder.setFloatingControls(
          enabled: nextSettings.showFloatingControls,
          opacity: nextSettings.floatingLogoOpacity,
          sizeDp: nextSettings.floatingLogoSizeDp,
          resolution: nextSettings.preferredResolution,
          fps: nextSettings.preferredFps,
          bitrateKbps: nextSettings.bitrateKbps,
          audioMode: nextSettings.audioMode,
          codec: nextSettings.codec,
          orientation: nextSettings.orientation,
          bitratePreset: nextSettings.bitratePreset,
        );
        if (nextSettings.showFloatingControls && !enabled) {
          nextSettings = nextSettings.copyWith(showFloatingControls: false);
          _showMessage('Não foi possível iniciar o menu flutuante.');
        }
      } on PlatformException catch (error) {
        _showMessage(
          error.message ?? 'Não foi possível atualizar o flutuante.',
        );
        return;
      } on MissingPluginException {
        _showMessage('O menu flutuante está disponível no aplicativo Android.');
        return;
      }
    }
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
    if ((nextSettings.floatingLogoOpacity != _settings.floatingLogoOpacity ||
            nextSettings.floatingLogoSizeDp != _settings.floatingLogoSizeDp) &&
        nextSettings.showFloatingControls) {
      try {
        if (nextSettings.floatingLogoOpacity != _settings.floatingLogoOpacity) {
          await _nativeRecorder.setFloatingLogoOpacity(
            nextSettings.floatingLogoOpacity,
          );
        }
        if (nextSettings.floatingLogoSizeDp != _settings.floatingLogoSizeDp) {
          await _nativeRecorder.setFloatingLogoSize(
            nextSettings.floatingLogoSizeDp,
          );
        }
      } on PlatformException catch (error) {
        _showMessage(
          'Não foi possível atualizar a opacidade da bolha: '
          '${error.message ?? 'erro desconhecido'}',
        );
      } on MissingPluginException {
        _showMessage(
          'A bolha flutuante está disponível no aplicativo Android.',
        );
      }
    }
    if (nextSettings.showFloatingControls &&
        nextSettings.showFloatingControls == _settings.showFloatingControls) {
      try {
        await _nativeRecorder.setFloatingControls(
          enabled: true,
          opacity: nextSettings.floatingLogoOpacity,
          sizeDp: nextSettings.floatingLogoSizeDp,
          resolution: nextSettings.preferredResolution,
          fps: nextSettings.preferredFps,
          bitrateKbps: nextSettings.bitrateKbps,
          audioMode: nextSettings.audioMode,
          codec: nextSettings.codec,
          orientation: nextSettings.orientation,
          bitratePreset: nextSettings.bitratePreset,
        );
      } on PlatformException catch (error) {
        _showMessage(
          error.message ?? 'Não foi possível atualizar o flutuante.',
        );
        return;
      } on MissingPluginException {
        _showMessage('O menu flutuante está disponível no aplicativo Android.');
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
      await _nativeRecorder.setFloatingControls(
        enabled: _settings.showFloatingControls,
        opacity: _settings.floatingLogoOpacity,
        sizeDp: _settings.floatingLogoSizeDp,
        resolution: _settings.preferredResolution,
        fps: _settings.preferredFps,
        bitrateKbps: _settings.bitrateKbps,
        audioMode: _settings.audioMode,
        codec: _settings.codec,
        orientation: _settings.orientation,
        bitratePreset: _settings.bitratePreset,
      );
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
    final themeData = AppThemeData.fromId(_settings.themeAccent);
    final pages = [
      HomePage(
        settings: _settings,
        isRecording: _isRecording,
        isPaused: _isPaused,
        elapsed: _elapsed,
        statusMessage: _statusMessage,
        actualCaptureResolution: _actualCaptureResolution,
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
        supportedResolutions: _supportedResolutions,
        supportedFps: _supportedFps,
        hevcSupported: _hevcSupported,
      ),
    ];

    return MaterialApp(
      navigatorKey: _navigatorKey,
      scaffoldMessengerKey: _messengerKey,
      title: AppStrings.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme(
        accentColor: _settings.accentColor,
        themeData: themeData,
      ),
      darkTheme: AppTheme.darkTheme(
        accentColor: _settings.accentColor,
        themeData: themeData,
      ),
      themeMode: _settings.themeMode,
      home: Builder(
        builder: (context) => PopScope(
          canPop: _selectedIndex == 0,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) _goHome();
          },
          child: Scaffold(
            body: AnimatedThemeBackground(
              themeData: themeData,
              isDark: _settings.isDarkMode,
              child: Stack(
                fit: StackFit.expand,
                children: List.generate(pages.length, (index) {
                  final selected = _selectedIndex == index;
                  return IgnorePointer(
                    ignoring: !selected,
                    child: AnimatedOpacity(
                      duration: MediaQuery.disableAnimationsOf(context)
                          ? Duration.zero
                          : const Duration(milliseconds: 280),
                      curve: Curves.easeInOut,
                      opacity: selected ? 1 : 0,
                      child: AnimatedSlide(
                        duration: MediaQuery.disableAnimationsOf(context)
                            ? Duration.zero
                            : const Duration(milliseconds: 280),
                        curve: Curves.easeOutCubic,
                        offset: selected ? Offset.zero : const Offset(0.035, 0),
                        child: pages[index],
                      ),
                    ),
                  );
                }),
              ),
            ),
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
    return AnimatedSize(
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      alignment: Alignment.bottomRight,
      child: Column(
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
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 180),
              child: _floatingMenuExpanded
                  ? const Icon(Icons.close_rounded, key: ValueKey('close'))
                  : ClipOval(
                      key: const ValueKey('logo'),
                      child: Opacity(
                        opacity: _settings.floatingLogoOpacity,
                        child: Image.asset(
                          'logo B13 falcon.png',
                          fit: BoxFit.cover,
                          width: 56,
                          height: 56,
                        ),
                      ),
                    ),
            ),
          ),
        ],
      ),
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
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 240),
                    curve: Curves.easeOutCubic,
                    margin: const EdgeInsets.symmetric(horizontal: 3),
                    padding: const EdgeInsets.symmetric(vertical: 7),
                    decoration: BoxDecoration(
                      color: selected
                          ? Theme.of(context).colorScheme.primary
                                .withValues(alpha: 0.13)
                          : Colors.transparent,
                      borderRadius: BorderRadius.circular(22),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        AnimatedScale(
                          duration: const Duration(milliseconds: 220),
                          curve: Curves.easeOutBack,
                          scale: selected ? 1.08 : 1,
                          child: Icon(
                            destination.$1,
                            color: selected
                                ? Theme.of(context).colorScheme.primary
                                : Theme.of(context).iconTheme.color,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          destination.$2,
                          style: Theme.of(context).textTheme.labelSmall
                              ?.copyWith(
                                color: selected
                                    ? Theme.of(context).colorScheme.primary
                                    : null,
                                fontWeight: selected ? FontWeight.w700 : null,
                              ),
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
