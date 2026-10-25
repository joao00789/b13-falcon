package com.b13.falcon

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL = "com.b13.falcon/recorder"
        const val REQUEST_MEDIA_PROJECTION = 1001
        const val REQUEST_VIDEO_PERMISSION = 1002
        const val REQUEST_OVERLAY_PERMISSION = 1003
    }

    private lateinit var channel: MethodChannel
    private var pendingParams: Map<String, Any>? = null
    private var pendingStartResult: MethodChannel.Result? = null
    private var pendingRecordingsResult: MethodChannel.Result? = null
    private var pendingOverlayResult: MethodChannel.Result? = null
    private var isRecording = false
    private var isPaused = false
    private var statusReceiverRegistered = false

    private val recordingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra("state") ?: return
            isRecording = state == "recording" || state == "paused"
            isPaused = state == "paused"
            if (::channel.isInitialized) {
                channel.invokeMethod(
                    "recordingStatusChanged",
                    mapOf("state" to state, "message" to intent.getStringExtra("message")),
                )
            }
        }
    }

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
                "getRecordings" -> handleGetRecordings(result)
                "openRecording" -> handleOpenRecording(call, result)
                "deleteRecording" -> handleDeleteRecording(call, result)
                "setKeepScreenOn" -> handleKeepScreenOn(call, result)
                "requestOverlayPermission" -> handleOverlayPermission(result)
                else -> result.notImplemented()
            }
        }
        if (!statusReceiverRegistered) {
            val filter = IntentFilter(RecordingService.STATUS_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(recordingStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(recordingStatusReceiver, filter)
            }
            statusReceiverRegistered = true
        }
    }

    private fun handleOverlayPermission(result: MethodChannel.Result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            result.success(true)
            return
        }
        if (pendingOverlayResult != null) {
            result.error("PERMISSION_PENDING", "A autorização do menu flutuante já está aberta.", null)
            return
        }
        pendingOverlayResult = result
        try {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
                REQUEST_OVERLAY_PERMISSION,
            )
        } catch (error: Exception) {
            pendingOverlayResult = null
            result.error(
                "OVERLAY_PERMISSION_FAILED",
                error.message ?: "Não foi possível abrir a autorização de sobreposição.",
                null,
            )
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
        val showFloatingControls = call.argument<Boolean>("showFloatingControls") ?: true
        pendingParams = mapOf(
            "title" to title,
            "resolution" to resolution,
            "fps" to fps,
            "bitrateKbps" to bitrate,
            "audioMode" to audioMode,
            "showFloatingControls" to showFloatingControls,
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

    override fun onDestroy() {
        if (statusReceiverRegistered) {
            unregisterReceiver(recordingStatusReceiver)
            statusReceiverRegistered = false
        }
        super.onDestroy()
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

    private fun handleGetRecordings(result: MethodChannel.Result) {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            if (pendingRecordingsResult != null) {
                result.error("PERMISSION_PENDING", "A autorização para acessar vídeos já está sendo solicitada.", null)
                return
            }
            pendingRecordingsResult = result
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_VIDEO_PERMISSION,
            )
            return
        }
        queryRecordings(result)
    }

    private fun queryRecordings(result: MethodChannel.Result) {
        try {
            val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
            )
            val selection: String
            val selectionArgs: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${MediaStore.Video.Media.RELATIVE_PATH} = ?"
                selectionArgs = arrayOf("Movies/B13 Falcon/")
            } else {
                @Suppress("DEPRECATION")
                val dataColumn = MediaStore.Video.Media.DATA
                selection = "$dataColumn LIKE ?"
                selectionArgs = arrayOf("%/Movies/B13 Falcon/%")
            }
            val recordings = mutableListOf<Map<String, Any>>()
            contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    recordings.add(
                        mapOf(
                            "uri" to Uri.withAppendedPath(collection, id.toString()).toString(),
                            "name" to cursor.getString(nameIndex),
                            "sizeBytes" to cursor.getLong(sizeIndex),
                            "durationMs" to cursor.getLong(durationIndex),
                            "dateAdded" to cursor.getLong(dateIndex),
                        ),
                    )
                }
            }
            result.success(recordings)
        } catch (error: Exception) {
            result.error("RECORDINGS_READ_FAILED", error.message ?: "Não foi possível carregar os vídeos.", null)
        }
    }

    private fun recordingUri(call: MethodCall): Uri? {
        val uri = call.argument<String>("uri")?.let { Uri.parse(it) } ?: return null
        if (uri.scheme != "content" || uri.authority != "media") return null
        return uri
    }

    private fun handleOpenRecording(call: MethodCall, result: MethodChannel.Result) {
        val uri = recordingUri(call)
        if (uri == null) {
            result.error("INVALID_VIDEO_URI", "O endereço do vídeo é inválido.", null)
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            result.success(null)
        } catch (error: Exception) {
            result.error("VIDEO_OPEN_FAILED", error.message ?: "Não foi possível abrir o vídeo.", null)
        }
    }

    private fun handleDeleteRecording(call: MethodCall, result: MethodChannel.Result) {
        val uri = recordingUri(call)
        if (uri == null) {
            result.error("INVALID_VIDEO_URI", "O endereço do vídeo é inválido.", null)
            return
        }
        try {
            if (contentResolver.delete(uri, null, null) == 0) {
                result.error("VIDEO_DELETE_FAILED", "O vídeo não foi encontrado.", null)
            } else {
                result.success(null)
            }
        } catch (error: Exception) {
            result.error("VIDEO_DELETE_FAILED", error.message ?: "Não foi possível apagar o vídeo.", null)
        }
    }

    private fun handleKeepScreenOn(call: MethodCall, result: MethodChannel.Result) {
        val enabled = call.argument<Boolean>("enabled") ?: false
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        result.success(null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            val result = pendingOverlayResult
            pendingOverlayResult = null
            result?.success(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
        } else if (requestCode == REQUEST_MEDIA_PROJECTION) {
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
                    val showFloatingControls =
                        pendingParams?.get("showFloatingControls") as? Boolean ?: true
                    if (showFloatingControls &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
                    ) {
                        startService(Intent(this, FloatingControlsService::class.java))
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

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_VIDEO_PERMISSION) {
            val result = pendingRecordingsResult
            pendingRecordingsResult = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                result?.let(::queryRecordings)
            } else {
                result?.error(
                    "VIDEO_PERMISSION_DENIED",
                    "Permita o acesso a vídeos para mostrar suas gravações.",
                    null,
                )
            }
        }
    }
}
