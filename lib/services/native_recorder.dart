import 'package:flutter/services.dart';

import '../data/models/saved_recording.dart';

class RecordingNativeBridge {
  static const MethodChannel _channel = MethodChannel(
    'com.b13.falcon/recorder',
  );

  void setRecordingStatusHandler(ValueChanged<Map<dynamic, dynamic>>? handler) {
    _channel.setMethodCallHandler(
      handler == null
          ? null
          : (call) async {
              if (call.method == 'recordingStatusChanged' &&
                  call.arguments is Map) {
                handler(call.arguments as Map<dynamic, dynamic>);
              }
            },
    );
  }

  Future<bool> requestOverlayPermission() async {
    try {
      return await _channel.invokeMethod<bool>('requestOverlayPermission') ??
          false;
    } on PlatformException {
      rethrow;
    }
  }

  Future<Map<String, dynamic>> startRecording({
    required String title,
    required String resolution,
    required int fps,
    required int bitrateKbps,
    required String audioMode,
    required bool showFloatingControls,
  }) async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'startRecording',
        {
          'title': title,
          'resolution': resolution,
          'fps': fps,
          'bitrateKbps': bitrateKbps,
          'audioMode': audioMode,
          'showFloatingControls': showFloatingControls,
        },
      );
      return Map<String, dynamic>.from(result ?? const <dynamic, dynamic>{});
    } on PlatformException catch (error) {
      return {
        'success': false,
        'message': error.message ?? 'Não foi possível iniciar a gravação.',
      };
    }
  }

  Future<Map<String, dynamic>> stopRecording() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'stopRecording',
      );
      return Map<String, dynamic>.from(result ?? const <dynamic, dynamic>{});
    } on PlatformException catch (error) {
      return {
        'success': false,
        'message': error.message ?? 'Não foi possível interromper a gravação.',
      };
    }
  }

  Future<Map<String, dynamic>> pauseRecording() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'pauseRecording',
      );
      return Map<String, dynamic>.from(result ?? const <dynamic, dynamic>{});
    } on PlatformException catch (error) {
      return {
        'success': false,
        'message': error.message ?? 'Não foi possível pausar a gravação.',
      };
    }
  }

  Future<Map<String, dynamic>> resumeRecording() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'resumeRecording',
      );
      return Map<String, dynamic>.from(result ?? const <dynamic, dynamic>{});
    } on PlatformException catch (error) {
      return {
        'success': false,
        'message': error.message ?? 'Não foi possível retomar a gravação.',
      };
    }
  }

  Future<Map<String, dynamic>> getStatus() async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
        'getStatus',
      );
      return Map<String, dynamic>.from(result ?? const <dynamic, dynamic>{});
    } on PlatformException catch (error) {
      return {
        'success': false,
        'message': error.message ?? 'Status indisponível.',
      };
    }
  }

  Future<List<SavedRecording>> getRecordings() async {
    try {
      final result = await _channel.invokeMethod<List<dynamic>>(
        'getRecordings',
      );
      return (result ?? const <dynamic>[])
          .whereType<Map<dynamic, dynamic>>()
          .map(SavedRecording.fromMap)
          .toList();
    } on PlatformException {
      rethrow;
    }
  }

  Future<void> openRecording(String uri) async {
    await _channel.invokeMethod<void>('openRecording', {'uri': uri});
  }

  Future<void> deleteRecording(String uri) async {
    await _channel.invokeMethod<void>('deleteRecording', {'uri': uri});
  }

  Future<void> setKeepScreenOn(bool enabled) async {
    await _channel.invokeMethod<void>('setKeepScreenOn', {'enabled': enabled});
  }
}
