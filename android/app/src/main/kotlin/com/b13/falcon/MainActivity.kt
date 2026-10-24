package com.b13.falcon

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL = "com.b13.falcon/recorder"
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var channel: MethodChannel
    private var pendingParams: Map<String, Any>? = null
    private var pendingStartResult: MethodChannel.Result? = null
    private var isRecording = false
    private var isPaused = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecording" -> handleStartRecording(call, result)
                "stopRecording" -> handleStopRecording(result)
                "pauseRecording" -> handlePauseRecording(result)
                "resumeRecording" -> handleResumeRecording(result)
                "getStatus" -> handleGetStatus(result)
                else -> result.notImplemented()
            }
        }
    }

    private fun handleStartRecording(call: MethodCall, result: MethodChannel.Result) {
        if (isRecording || pendingStartResult != null) {
            result.success(mapOf("success" to false, "message" to "Já existe uma gravação em andamento."))
            return
        }
        val title = call.argument<String>("title") ?: "B13_FALCON_RECORDING"
        val resolution = call.argument<String>("resolution") ?: "720p"
        val fps = call.argument<Int>("fps") ?: 30
        val bitrate = call.argument<Int>("bitrateKbps") ?: 8000
        val audioMode = call.argument<String>("audioMode") ?: "Microfone"
        pendingParams = mapOf(
            "title" to title,
            "resolution" to resolution,
            "fps" to fps,
            "bitrateKbps" to bitrate,
            "audioMode" to audioMode,
        )

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val captureIntent = projectionManager.createScreenCaptureIntent()
        pendingStartResult = result
        try {
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
        } catch (error: Exception) {
            pendingStartResult = null
            result.success(mapOf("success" to false, "message" to (error.message ?: "Não foi possível solicitar a captura.")))
        }
    }

    private fun handleStopRecording(result: MethodChannel.Result) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isRecording = false
        isPaused = false
        result.success(mapOf("success" to true, "message" to "Gravação finalizada."))
    }

    private fun handlePauseRecording(result: MethodChannel.Result) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isPaused = true
        result.success(mapOf("success" to true, "message" to "Gravação pausada."))
    }

    private fun handleResumeRecording(result: MethodChannel.Result) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isPaused = false
        result.success(mapOf("success" to true, "message" to "Gravação retomada."))
    }

    private fun handleGetStatus(result: MethodChannel.Result) {
        result.success(mapOf("success" to true, "recording" to isRecording, "paused" to isPaused))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            val startResult = pendingStartResult
            pendingStartResult = null
            if (resultCode == Activity.RESULT_OK && data != null) {
                val intent = Intent(this, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START
                    putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(RecordingService.EXTRA_DATA, data)
                    pendingParams?.let { params ->
                        putExtra("resolution", params["resolution"] as String?)
                        putExtra("fps", params["fps"] as Int?)
                        putExtra("bitrateKbps", params["bitrateKbps"] as Int?)
                        putExtra("audioMode", params["audioMode"] as String?)
                        putExtra("title", params["title"] as String?)
                    }
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    isRecording = true
                    isPaused = false
                    startResult?.success(mapOf("success" to true, "message" to "Gravação em andamento."))
                } catch (error: Exception) {
                    isRecording = false
                    startResult?.success(mapOf("success" to false, "message" to (error.message ?: "Não foi possível iniciar o serviço de gravação.")))
                }
            } else {
                startResult?.success(mapOf("success" to false, "message" to "A autorização de captura foi cancelada."))
            }
        }
    }
}
