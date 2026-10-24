import 'package:flutter/services.dart';

class RecordingNativeBridge {
  static const MethodChannel _channel = MethodChannel(
    'com.b13.falcon/recorder',
  );

  Future<Map<String, dynamic>> startRecording({
    required String title,
    required String resolution,
    required int fps,
    required int bitrateKbps,
    required String audioMode,
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
}
