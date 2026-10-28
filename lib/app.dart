import 'dart:async';

import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/theme/app_theme.dart';
import 'package:b13_falcon/core/theme/app_theme_data.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/data/models/saved_recording.dart';
import 'package:b13_falcon/features/home/home_page.dart';
import 'package:b13_falcon/features/help/help_page.dart';
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
  int _screenWidth = 1080;
  int _screenHeight = 1920;

  @override
  void initState() {
    super.initState();
    _nativeRecorder.setRecordingStatusHandler(_onNativeRecordingStatus);
    unawaited(_restoreRecordingStatus());
    unawaited(_loadSettings());
    unawaited(_loadRecordings());
    unawaited(_loadVideoCapabilities());
  }

  Future<void> _restoreRecordingStatus() async {
    try {
      final status = await _nativeRecorder.getStatus();
      if (!mounted || status['success'] != true) return;
      final recording = status['recording'] as bool? ?? false;
      final paused = status['paused'] as bool? ?? false;
      final elapsedSeconds = (status['elapsedSeconds'] as num?)?.toInt() ?? 0;
      setState(() {
        _isRecording = recording;
        _isPaused = paused;
        _elapsed = Duration(seconds: elapsedSeconds.clamp(0, 1 << 31).toInt());
        _statusMessage = recording
            ? paused
                  ? 'Gravação pausada.'
                  : 'Gravação em andamento.'
            : _statusMessage;
      });
      if (recording && !paused) _startTicker();
    } on PlatformException catch (error) {
      if (mounted) {
        _showMessage(error.message ?? 'Não foi possível consultar a gravação.');
      }
    } on MissingPluginException {
      // Native recording status is unavailable in widget previews.
    }
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
        _screenWidth = (capabilities['screenWidth'] as int?) ?? _screenWidth;
        _screenHeight = (capabilities['screenHeight'] as int?) ?? _screenHeight;
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
    var settings = await _settingsService.loadSettings();
    if (!mounted) return;
    setState(() => _settings = settings);
    try {
      await _nativeRecorder.setKeepScreenOn(settings.keepScreenOn);
      await _nativeRecorder.saveCameraConfig(settings.cameraOverlay.configJson);
      if (settings.cameraOverlay.enabled) {
        await _nativeRecorder.updateCameraLive(
          settings.cameraOverlay.configJson,
        );
      }
      if (settings.showFloatingControls) {
        final enabled = await _nativeRecorder.setFloatingControls(
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
          cameraConfigJson: settings.cameraOverlay.configJson,
          microphoneGain: settings.microphoneGain,
          gameAudioGain: settings.gameAudioGain,
          microphoneNoiseSuppression: settings.microphoneNoiseSuppression,
        );
        if (!enabled) {
          settings = settings.copyWith(showFloatingControls: false);
          await _settingsService.saveSettings(settings);
          if (mounted) {
            setState(() {
              _settings = settings;
              _statusMessage =
                  'Controles flutuantes desativados: autorize a sobreposição '
                  'novamente nos ajustes.';
            });
          }
        }
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
      if (_settings.showFloatingControls || _settings.cameraOverlay.enabled) {
        try {
          final granted = await _nativeRecorder.requestOverlayPermission();
          if (!mounted) return;
          if (!granted) {
            if (_settings.cameraOverlay.enabled) {
              _showMessage(
                'A permissão de sobreposição é necessária para mostrar a '
                'câmera flutuante durante a gravação.',
              );
              return;
            }
            _showMessage(
              'Sem permissão de sobreposição: a gravação começará sem os '
              'controles flutuantes.',
            );
          }
        } on PlatformException catch (error) {
          if (_settings.cameraOverlay.enabled) {
            _showMessage(
              error.message ??
                  'Não foi possível autorizar a câmera flutuante na tela.',
            );
            return;
          }
          _showMessage(
            error.message ??
                'Não foi possível solicitar a permissão flutuante.',
          );
        } on MissingPluginException {
          if (_settings.cameraOverlay.enabled) {
            _showMessage(
              'A câmera flutuante está disponível somente no Android.',
            );
            return;
          }
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
        cameraConfigJson: _settings.cameraOverlay.configJson,
        microphoneGain: _settings.microphoneGain,
        gameAudioGain: _settings.gameAudioGain,
        microphoneNoiseSuppression: _settings.microphoneNoiseSuppression,
      );
      if (!mounted) return;
      if (result['success'] == true) {
        setState(() {
          _isRecording = true;
          _isPaused = false;
          _elapsed = Duration.zero;
          _statusMessage = 'Gravação em andamento.';
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
    final needsLiveCamera =
        nextSettings.cameraOverlay.enabled &&
        nextSettings.cameraOverlay.imageUri == null;
    final previouslyUsedLiveCamera =
        _settings.cameraOverlay.enabled &&
        _settings.cameraOverlay.imageUri == null;
    if (needsLiveCamera && !previouslyUsedLiveCamera) {
      try {
        final granted = await _nativeRecorder.requestCameraPermission();
        if (!granted) {
          _showMessage(
            'A permissão da câmera é necessária para incluir sua imagem no vídeo.',
          );
          nextSettings = nextSettings.copyWith(
            cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
          );
        }
      } on PlatformException catch (error) {
        _showMessage(
          error.message ?? 'Não foi possível solicitar acesso à câmera.',
        );
        nextSettings = nextSettings.copyWith(
          cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
        );
      } on MissingPluginException {
        _showMessage('A câmera frontal está disponível somente no Android.');
        nextSettings = nextSettings.copyWith(
          cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
        );
      }
    }
    if (nextSettings.cameraOverlay.enabled &&
        !_settings.cameraOverlay.enabled) {
      try {
        final granted = await _nativeRecorder.requestOverlayPermission();
        if (!granted) {
          _showMessage(
            'A permissão de sobreposição é necessária para exibir a facecam '
            'sobre outros apps.',
          );
          nextSettings = nextSettings.copyWith(
            cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
          );
        }
      } on PlatformException catch (error) {
        _showMessage(
          error.message ?? 'Não foi possível autorizar a facecam flutuante.',
        );
        nextSettings = nextSettings.copyWith(
          cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
        );
      } on MissingPluginException {
        _showMessage('A facecam flutuante está disponível somente no Android.');
        nextSettings = nextSettings.copyWith(
          cameraOverlay: nextSettings.cameraOverlay.copyWith(enabled: false),
        );
      }
    }
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
          cameraConfigJson: nextSettings.cameraOverlay.configJson,
          microphoneGain: nextSettings.microphoneGain,
          gameAudioGain: nextSettings.gameAudioGain,
          microphoneNoiseSuppression: nextSettings.microphoneNoiseSuppression,
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
          cameraConfigJson: nextSettings.cameraOverlay.configJson,
          microphoneGain: nextSettings.microphoneGain,
          gameAudioGain: nextSettings.gameAudioGain,
          microphoneNoiseSuppression: nextSettings.microphoneNoiseSuppression,
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
    try {
      await _nativeRecorder.saveCameraConfig(
        nextSettings.cameraOverlay.configJson,
      );
      await _nativeRecorder.updateCameraLive(
        nextSettings.cameraOverlay.configJson,
      );
    } on PlatformException catch (error) {
      _showMessage(
        'Configuração salva, mas não foi possível aplicar a câmera: '
        '${error.message ?? 'erro desconhecido'}',
      );
    } on MissingPluginException {
      _showMessage('A câmera frontal está disponível somente no Android.');
    }
    if (mounted) setState(() => _settings = nextSettings);
  }

  Future<String?> _pickCameraImage() async {
    try {
      return await _nativeRecorder.pickCameraImage();
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Não foi possível abrir as imagens.');
      return null;
    } on MissingPluginException {
      _showMessage('A escolha de imagem está disponível apenas no Android.');
      return null;
    }
  }

  Future<void> _resetSettings() async {
    await _settingsService.resetSettings();
    if (!mounted) return;
    setState(() => _settings = const AppSettings());
    try {
      await _nativeRecorder.saveCameraConfig(
        _settings.cameraOverlay.configJson,
      );
      await _nativeRecorder.updateCameraLive(
        _settings.cameraOverlay.configJson,
      );
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
        cameraConfigJson: _settings.cameraOverlay.configJson,
        microphoneGain: _settings.microphoneGain,
        gameAudioGain: _settings.gameAudioGain,
        microphoneNoiseSuppression: _settings.microphoneNoiseSuppression,
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
      _selectedIndex = 0;
    });
    unawaited(_loadRecordings());
  }

  void _openSettings() {
    setState(() {
      _selectedIndex = 2;
    });
  }

  @override
  Widget build(BuildContext context) {
    final themeData = AppThemeData.fromId(_settings.themeAccent);
    final pages = [
      LibraryPage(
        recordings: _recordings,
        errorMessage: _recordingsError,
        onRefresh: _loadRecordings,
        onOpenRecording: _openRecording,
        onDeleteRecording: _deleteRecording,
        onBack: _goHome,
      ),
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
      SettingsPage(
        settings: _settings,
        onChanged: _saveSettings,
        onPickCameraImage: _pickCameraImage,
        onReset: _resetSettings,
        onBack: _goHome,
        supportedResolutions: _supportedResolutions,
        supportedFps: _supportedFps,
        hevcSupported: _hevcSupported,
        sourceWidth: _screenWidth,
        sourceHeight: _screenHeight,
      ),
      const HelpPage(),
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
            floatingActionButton: FloatingActionButton(
              heroTag: 'capture-action',
              tooltip: _isRecording ? 'Parar gravação' : 'Iniciar gravação',
              onPressed: _toggleRecording,
              backgroundColor: _isRecording
                  ? Colors.red.shade700
                  : Theme.of(context).colorScheme.primary,
              foregroundColor: Colors.white,
              child: Icon(
                _isRecording ? Icons.stop_rounded : Icons.videocam_rounded,
              ),
            ),
            floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
            bottomNavigationBar: _buildFloatingNavigation(context),
          ),
        ),
      ),
    );
  }

  Widget _buildFloatingNavigation(BuildContext context) {
    const destinations = [
      (Icons.videocam_outlined, 'Vídeos'),
      (Icons.photo_camera_outlined, 'Capturas'),
      (Icons.settings_outlined, 'Ajustes'),
      (Icons.help_outline_rounded, 'Ajuda'),
    ];
    return NavigationBar(
      selectedIndex: _selectedIndex,
      height: 66,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      onDestinationSelected: (index) {
        setState(() => _selectedIndex = index);
        if (index == 0) unawaited(_loadRecordings());
      },
      destinations: [
        for (final destination in destinations)
          NavigationDestination(
            icon: Icon(destination.$1),
            selectedIcon: Icon(destination.$1),
            label: destination.$2,
          ),
      ],
    );
  }

  @override
  void dispose() {
    _timer?.cancel();
    _nativeRecorder.setRecordingStatusHandler(null);
    super.dispose();
  }
}
