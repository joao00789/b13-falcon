package com.b13.falcon

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
import android.graphics.BitmapFactory
import android.view.WindowManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

class MainActivity : FlutterActivity() {
    companion object {
        const val CHANNEL = "com.b13.falcon/recorder"
        const val REQUEST_VIDEO_PERMISSION = 1002
        const val REQUEST_OVERLAY_PERMISSION = 1003
        const val REQUEST_AUDIO_PERMISSION = 1004
        const val REQUEST_NOTIFICATION_PERMISSION = 1005
        const val REQUEST_CAMERA_PERMISSION = 1006
        const val REQUEST_PICK_CAMERA_IMAGE = 1007
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var channel: MethodChannel
    private var pendingParams: Map<String, Any>? = null
    private var pendingStartResult: MethodChannel.Result? = null
    private var pendingRecordingsResult: MethodChannel.Result? = null
    private var pendingOverlayResult: MethodChannel.Result? = null
    private var pendingCameraPermissionResult: MethodChannel.Result? = null
    private var pendingCameraImageResult: MethodChannel.Result? = null
    private var pendingAudioCall: MethodCall? = null
    private var isRecording = false
    private var isPaused = false
    private var floatingControlsEnabled = true
    private var statusReceiverRegistered = false

    private val recordingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra("state") ?: return
            isRecording = state == "recording" || state == "paused"
            isPaused = state == "paused"
            if (::channel.isInitialized) {
                channel.invokeMethod(
                    "recordingStatusChanged",
                    mapOf(
                        "state" to state,
                        "message" to intent.getStringExtra("message"),
                        "actualWidth" to intent.getIntExtra("actualWidth", 0).takeIf { it > 0 },
                        "actualHeight" to intent.getIntExtra("actualHeight", 0).takeIf { it > 0 },
                    ),
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
                "setFloatingLogoOpacity" -> handleFloatingLogoOpacity(call, result)
                "setFloatingLogoSize" -> handleFloatingLogoSize(call, result)
                "setFloatingControls" -> handleFloatingControls(call, result)
                "saveCameraConfig", "updateCameraLive" -> handleCameraConfig(
                    call,
                    result,
                    call.method == "updateCameraLive",
                )
                "getVideoCapabilities" -> handleGetVideoCapabilities(result)
                "requestOverlayPermission" -> handleOverlayPermission(result)
                "requestCameraPermission" -> handleCameraPermission(result)
                "pickCameraImage" -> handlePickCameraImage(result)
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

    private fun handleCameraPermission(result: MethodChannel.Result) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            result.success(true)
            return
        }
        if (pendingCameraPermissionResult != null) {
            result.error("PERMISSION_PENDING", "A permissão da câmera já está sendo solicitada.", null)
            return
        }
        pendingCameraPermissionResult = result
        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }

    private fun handleStartRecording(call: MethodCall, result: MethodChannel.Result) {
        if (isRecording || pendingStartResult != null) {
            result.success(mapOf("success" to false, "message" to "Já existe uma gravação em andamento."))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioCall = call
            pendingStartResult = result
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION,
            )
            return
        }
        prepareStartRecording(call, result)
    }

    private fun prepareStartRecording(call: MethodCall, result: MethodChannel.Result) {
        if (requiresLiveCameraPermission(cameraConfigFor(call)) &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioCall = call
            pendingStartResult = result
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION,
            )
            return
        }
        val audioMode = call.argument<String>("audioMode") ?: "both"
        if ((audioMode == "both" || audioMode == "microphone" || audioMode == "Microfone") &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAudioCall = call
            pendingStartResult = result
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION,
            )
            return
        }
        prepareRecording(call, result, audioMode)
    }

    private fun prepareRecording(
        call: MethodCall,
        result: MethodChannel.Result,
        audioMode: String,
    ) {
        val title = call.argument<String>("title") ?: "B13_FALCON_RECORDING"
        val resolution = call.argument<String>("resolution") ?: "720p"
        val fps = call.argument<Int>("fps") ?: 30
        val bitrate = call.argument<Int>("bitrateKbps") ?: 8000
        val showFloatingControls = call.argument<Boolean>("showFloatingControls") ?: true
        val floatingLogoOpacity = (call.argument<Double>("floatingLogoOpacity") ?: 0.85)
            .coerceIn(0.0, 1.0)
        val floatingLogoSizeDp = (call.argument<Int>("floatingLogoSizeDp") ?: 56)
            .coerceIn(32, 96)
        val requestedOrientation = call.argument<String>("orientation") ?: "auto"
        val (sourceWidth, sourceHeight) = currentDisplaySizeForOrientation(requestedOrientation)
        pendingParams = mapOf(
            "title" to title,
            "resolution" to resolution,
            "fps" to fps,
            "bitrateKbps" to bitrate,
            "audioMode" to audioMode,
            "showFloatingControls" to showFloatingControls,
            "floatingLogoOpacity" to floatingLogoOpacity,
            "floatingLogoSizeDp" to floatingLogoSizeDp,
            "codec" to (call.argument<String>("codec") ?: "h264"),
            "orientation" to requestedOrientation,
            "bitratePreset" to (call.argument<String>("bitratePreset") ?: "custom"),
            "cameraConfigJson" to cameraConfigFor(call),
            "sourceWidth" to sourceWidth,
            "sourceHeight" to sourceHeight,
            "microphoneGain" to (call.argument<Double>("microphoneGain") ?: 1.0)
                .coerceIn(0.0, 2.0),
            "gameAudioGain" to (call.argument<Double>("gameAudioGain") ?: 1.0)
                .coerceIn(0.0, 2.0),
            "microphoneNoiseSuppression" to
                (call.argument<Boolean>("microphoneNoiseSuppression") ?: false),
        )
        persistRecordingParams(pendingParams!!)
        val cameraConfig = CameraOverlayConfig.fromJson(
            pendingParams?.get("cameraConfigJson") as? String ?: "{}",
        )
        if (cameraConfig.enabled && cameraConfig.mode == "overlay") {
            val cameraIntent = Intent(this, CameraOverlayService::class.java)
                .setAction(CameraOverlayService.ACTION_UPDATE)
                .putExtra("cameraConfigJson", cameraConfig.toJson())
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(cameraIntent)
                } else {
                    startService(cameraIntent)
                }
            } catch (error: Exception) {
                android.widget.Toast.makeText(
                    this,
                    error.message ?: "Não foi possível iniciar a prévia da câmera.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }

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

    private fun persistRecordingParams(params: Map<String, Any>) {
        getSharedPreferences("b13_falcon_recording", MODE_PRIVATE).edit()
            .putString("title", params["title"] as? String)
            .putString("resolution", params["resolution"] as? String)
            .putInt("fps", params["fps"] as? Int ?: 30)
            .putInt("bitrateKbps", params["bitrateKbps"] as? Int ?: 16000)
            .putString("audioMode", params["audioMode"] as? String ?: "both")
            .putBoolean("showFloatingControls", params["showFloatingControls"] as? Boolean ?: true)
            .putFloat("floatingLogoOpacity", (params["floatingLogoOpacity"] as? Double ?: 0.85).toFloat())
            .putInt("floatingLogoSizeDp", params["floatingLogoSizeDp"] as? Int ?: 56)
            .putString("codec", params["codec"] as? String ?: "h264")
            .putString("orientation", params["orientation"] as? String ?: "auto")
            .putString("bitratePreset", params["bitratePreset"] as? String ?: "custom")
            .putString("cameraConfigJson", params["cameraConfigJson"] as? String ?: "{}")
            .putFloat("microphoneGain", (params["microphoneGain"] as? Double ?: 1.0).toFloat())
            .putFloat("gameAudioGain", (params["gameAudioGain"] as? Double ?: 1.0).toFloat())
            .putBoolean(
                "microphoneNoiseSuppression",
                params["microphoneNoiseSuppression"] as? Boolean ?: false,
            )
            .apply()
        }

    private fun cameraConfigFor(call: MethodCall): String =
        call.argument<String>("cameraConfigJson")
            ?: CameraOverlayConfig.loadFromPreferences(
                getSharedPreferences("b13_falcon_recording", MODE_PRIVATE),
            )

    private fun requiresLiveCameraPermission(cameraConfigJson: String): Boolean {
        val config = CameraOverlayConfig.fromJson(cameraConfigJson)
        return config.enabled && config.imageUri.isNullOrBlank()
    }

    private fun handlePickCameraImage(result: MethodChannel.Result) {
        if (pendingCameraImageResult != null) {
            result.error("PICKER_PENDING", "O seletor de imagem já está aberto.", null)
            return
        }
        pendingCameraImageResult = result
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                REQUEST_PICK_CAMERA_IMAGE,
            )
        } catch (error: Exception) {
            pendingCameraImageResult = null
            result.error(
                "IMAGE_PICKER_FAILED",
                error.message ?: "Não foi possível abrir as imagens.",
                null,
            )
        }
    }

    private fun currentDisplaySizeForOrientation(requestedOrientation: String): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        var sourceWidth = metrics.widthPixels
        var sourceHeight = metrics.heightPixels
        @Suppress("DEPRECATION")
        val rotation = display.rotation
        val isLandscape = when (resources.configuration.orientation) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE -> true
            android.content.res.Configuration.ORIENTATION_PORTRAIT -> false
            else -> rotation == android.view.Surface.ROTATION_90 ||
                rotation == android.view.Surface.ROTATION_270
        }
        if (requestedOrientation == "auto") {
            if (isLandscape && sourceWidth < sourceHeight || !isLandscape && sourceWidth > sourceHeight) {
                val originalWidth = sourceWidth
                sourceWidth = sourceHeight
                sourceHeight = originalWidth
            }
            return sourceWidth to sourceHeight
        }
        if (requestedOrientation == "portrait" && sourceWidth > sourceHeight ||
            requestedOrientation == "landscape" && sourceHeight > sourceWidth
        ) {
            val originalWidth = sourceWidth
            sourceWidth = sourceHeight
            sourceHeight = originalWidth
        }
        return sourceWidth to sourceHeight
    }

    private fun handleCameraConfig(
        call: MethodCall,
        result: MethodChannel.Result,
        updateLive: Boolean,
    ) {
        val cameraConfigJson = call.argument<String>("cameraConfigJson")
        if (cameraConfigJson == null) {
            result.error("INVALID_CAMERA_CONFIG", "A configuração da câmera está ausente.", null)
            return
        }
        val incomingConfig = try {
            CameraOverlayConfig.fromJson(cameraConfigJson)
        } catch (error: org.json.JSONException) {
            result.error("INVALID_CAMERA_CONFIG", "A configuração da câmera não é um JSON válido.", null)
            return
        }
        val preferences = getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
        val savedConfig = preferences.getString("cameraConfigJson", null)
            ?.let { savedJson ->
                runCatching { CameraOverlayConfig.fromJson(savedJson) }
                    .onFailure {
                        android.util.Log.w(
                            "B13FalconCamera",
                            "Não foi possível ler a posição salva da facecam.",
                            it,
                        )
                    }
                    .getOrNull()
            }
        val config = if (savedConfig == null) {
            incomingConfig
        } else {
            incomingConfig.copy(
                xFraction = savedConfig.xFraction,
                yFraction = savedConfig.yFraction,
            )
        }
        val effectiveConfigJson = config.toJson()
        preferences
            .edit()
            .putString("cameraConfigJson", effectiveConfigJson)
            .apply()
        if (updateLive) {
            if (config.enabled && config.mode == "overlay") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(this)
                ) {
                    result.error(
                        "OVERLAY_PERMISSION_REQUIRED",
                        "A permissão de sobreposição é necessária para mostrar a câmera.",
                        null,
                    )
                    return
                }
                if (config.imageUri.isNullOrBlank() &&
                    checkSelfPermission(Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    result.error(
                        "CAMERA_PERMISSION_REQUIRED",
                        "A permissão da câmera é necessária para mostrar a prévia.",
                        null,
                    )
                    return
                }
                val serviceIntent = Intent(this, CameraOverlayService::class.java)
                    .setAction(CameraOverlayService.ACTION_UPDATE)
                    .putExtra("cameraConfigJson", effectiveConfigJson)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (error: Exception) {
                    result.error(
                        "CAMERA_OVERLAY_START_FAILED",
                        error.message ?: "Não foi possível iniciar a prévia da câmera.",
                        null,
                    )
                    return
                }
            } else {
                stopService(Intent(this, CameraOverlayService::class.java))
            }
        }
        result.success(null)
    }

    override fun onDestroy() {
        if (statusReceiverRegistered) {
            unregisterReceiver(recordingStatusReceiver)
            statusReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun handleStopRecording(result: MethodChannel.Result) {
        if (!isRecording) {
            result.success(mapOf("success" to true, "message" to "Nenhuma gravação está ativa."))
            return
        }
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        startService(intent)
        isRecording = false
        isPaused = false
        result.success(mapOf("success" to true, "message" to "Finalizando a gravação."))
    }

    private fun handlePauseRecording(result: MethodChannel.Result) {
        if (!isRecording || isPaused) {
            result.success(mapOf("success" to false, "message" to "Não há gravação ativa para pausar."))
            return
        }
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE
        }
        startService(intent)
        isPaused = true
        result.success(mapOf("success" to true, "message" to "Gravação pausada."))
    }

    private fun handleResumeRecording(result: MethodChannel.Result) {
        if (!isRecording || !isPaused) {
            result.success(mapOf("success" to false, "message" to "Não há gravação pausada para retomar."))
            return
        }
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME
        }
        startService(intent)
        isPaused = false
        result.success(mapOf("success" to true, "message" to "Gravação retomada."))
    }

    private fun handleGetStatus(result: MethodChannel.Result) {
        val state = RecordingService.currentStatus()
        isRecording = state == "recording" || state == "paused"
        isPaused = state == "paused"
        result.success(
            mapOf(
                "success" to true,
                "recording" to isRecording,
                "paused" to isPaused,
                "elapsedSeconds" to RecordingService.currentElapsedSeconds(),
            ),
        )
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

    private fun handleFloatingLogoOpacity(call: MethodCall, result: MethodChannel.Result) {
        val opacity = (call.argument<Double>("opacity") ?: 0.85).coerceIn(0.0, 1.0)
        if (floatingControlsEnabled) {
            startService(
                Intent(this, FloatingControlsService::class.java)
                    .setAction(FloatingControlsService.ACTION_UPDATE_OPACITY)
                    .putExtra("logoOpacity", opacity),
            )
        }
        result.success(null)
    }

    private fun handleFloatingLogoSize(call: MethodCall, result: MethodChannel.Result) {
        val sizeDp = (call.argument<Int>("sizeDp") ?: 56).coerceIn(32, 96)
        if (floatingControlsEnabled) {
            startService(
                Intent(this, FloatingControlsService::class.java)
                    .setAction(FloatingControlsService.ACTION_UPDATE_SIZE)
                    .putExtra("sizeDp", sizeDp),
            )
        }
        result.success(null)
    }

    private fun handleFloatingControls(call: MethodCall, result: MethodChannel.Result) {
        val enabled = call.argument<Boolean>("enabled") ?: false
        val opacity = (call.argument<Double>("opacity") ?: 0.85).coerceIn(0.0, 1.0)
        val sizeDp = (call.argument<Int>("sizeDp") ?: 56).coerceIn(32, 96)
        val params = mapOf(
            "title" to "B13_FALCON",
            "resolution" to (call.argument<String>("resolution") ?: "1080p"),
            "fps" to (call.argument<Int>("fps") ?: 30),
            "bitrateKbps" to (call.argument<Int>("bitrateKbps") ?: 16000),
            "audioMode" to (call.argument<String>("audioMode") ?: "both"),
            "showFloatingControls" to enabled,
            "floatingLogoOpacity" to opacity,
            "floatingLogoSizeDp" to sizeDp,
            "codec" to (call.argument<String>("codec") ?: "h264"),
            "orientation" to (call.argument<String>("orientation") ?: "auto"),
            "bitratePreset" to (call.argument<String>("bitratePreset") ?: "custom"),
            "cameraConfigJson" to cameraConfigFor(call),
            "microphoneGain" to (call.argument<Double>("microphoneGain") ?: 1.0)
                .coerceIn(0.0, 2.0),
            "gameAudioGain" to (call.argument<Double>("gameAudioGain") ?: 1.0)
                .coerceIn(0.0, 2.0),
            "microphoneNoiseSuppression" to
                (call.argument<Boolean>("microphoneNoiseSuppression") ?: false),
        )
        persistRecordingParams(params)
        if (!enabled) {
            floatingControlsEnabled = false
            stopService(Intent(this, FloatingControlsService::class.java))
            result.success(true)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            result.success(false)
            return
        }
        floatingControlsEnabled = true
        val intent = Intent(this, FloatingControlsService::class.java)
            .putExtra("logoOpacity", opacity)
            .putExtra("sizeDp", sizeDp)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            result.success(true)
        } catch (error: Exception) {
            floatingControlsEnabled = false
            result.error(
                "FLOATING_CONTROLS_FAILED",
                error.message ?: "Não foi possível iniciar os controles flutuantes.",
                null,
            )
        }
    }

    private fun handleGetVideoCapabilities(result: MethodChannel.Result) {
        try {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(metrics)
            val sourceWidth = metrics.widthPixels
            val sourceHeight = metrics.heightPixels
            val codecInfos = android.media.MediaCodecList(
                android.media.MediaCodecList.REGULAR_CODECS,
            ).codecInfos.filter { it.isEncoder }
            val avcEncoders = codecInfos.filter { info ->
                info.supportedTypes.any { it.equals("video/avc", ignoreCase = true) }
            }
            val hevcSupported = codecInfos.any { info ->
                runCatching {
                    info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } &&
                        info.getCapabilitiesForType("video/hevc").colorFormats.contains(
                            android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                        )
                }.getOrDefault(false)
            }
            val resolutionBounds = listOf(
                "native" to 0,
                "360p" to 640,
                "480p" to 854,
                "540p" to 960,
                "720p" to 1280,
                "1080p" to 1920,
                "1440p" to 2560,
            )
            val supportedResolutions = resolutionBounds.filter { (_, bound) ->
                val scale = if (bound == 0) 1f else
                    minOf(1f, bound.toFloat() / maxOf(sourceWidth, sourceHeight))
                val width = ((sourceWidth * scale).toInt() / 2) * 2
                val height = ((sourceHeight * scale).toInt() / 2) * 2
                avcEncoders.any { encoder ->
                    runCatching {
                        encoder.getCapabilitiesForType("video/avc")
                            .videoCapabilities.areSizeAndRateSupported(width, height, 30.0)
                    }.getOrDefault(false)
                }
            }.map { it.first }
            val supportedFps = listOf(15, 24, 30, 45, 60, 90).filter { fps ->
                avcEncoders.any { encoder ->
                    runCatching {
                        val capabilities =
                            encoder.getCapabilitiesForType("video/avc").videoCapabilities
                        val scale = minOf(1f, 1920f / maxOf(sourceWidth, sourceHeight))
                        val width = ((sourceWidth * scale).toInt() / 2) * 2
                        val height = ((sourceHeight * scale).toInt() / 2) * 2
                        capabilities.areSizeAndRateSupported(width, height, fps.toDouble())
                    }.getOrDefault(false)
                }
            }
            result.success(
                mapOf(
                    "resolutions" to supportedResolutions,
                    "fps" to supportedFps,
                    "hevc" to hevcSupported,
                    "screenWidth" to sourceWidth,
                    "screenHeight" to sourceHeight,
                ),
            )
        } catch (error: Exception) {
            result.error(
                "VIDEO_CAPABILITIES_FAILED",
                error.message ?: "Não foi possível consultar os codecs disponíveis.",
                null,
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_CAMERA_IMAGE) {
            val result = pendingCameraImageResult
            pendingCameraImageResult = null
            val imageUri = data?.data
            if (resultCode != RESULT_OK || imageUri == null) {
                result?.success(null)
                return
            }
            copyCameraImageToAppStorage(imageUri, result)
            return
        }
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            val result = pendingStartResult
            pendingStartResult = null
            val params = pendingParams
            pendingParams = null
            if (resultCode != RESULT_OK || data == null || result == null || params == null) {
                result?.success(
                    mapOf(
                        "success" to false,
                        "message" to "A permissão para capturar a tela foi cancelada.",
                    ),
                )
                return
            }

            val serviceIntent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START
                putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(RecordingService.EXTRA_DATA, data)
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putExtra(key, value)
                        is Int -> putExtra(key, value)
                        is Boolean -> putExtra(key, value)
                        is Double -> putExtra(key, value)
                    }
                }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                result.success(mapOf("success" to true, "message" to "Preparando gravação."))
            } catch (error: Exception) {
                result.success(
                    mapOf(
                        "success" to false,
                        "message" to (error.message ?: "Não foi possível iniciar a gravação."),
                    ),
                )
            }
            return
        }
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            val result = pendingOverlayResult
            pendingOverlayResult = null
            result?.success(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
        }
    }

    private fun copyCameraImageToAppStorage(
        sourceUri: Uri,
        result: MethodChannel.Result?,
    ) {
        Thread({
            var copiedFile: File? = null
            try {
                val imageDirectory = File(filesDir, "camera_overlay")
                if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
                    throw IOException("Não foi possível preparar o armazenamento da facecam.")
                }
                val imageFile = File(imageDirectory, "${UUID.randomUUID()}.img")
                val temporaryFile = File(imageDirectory, "${UUID.randomUUID()}.tmp")
                try {
                    val input = contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("O seletor não forneceu acesso de leitura à imagem.")
                    input.use { source ->
                        FileOutputStream(temporaryFile).use { destination ->
                            source.copyTo(destination)
                            destination.fd.sync()
                        }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(temporaryFile.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        throw IOException("O arquivo selecionado não contém uma imagem válida.")
                    }
                    if (!temporaryFile.renameTo(imageFile)) {
                        throw IOException("Não foi possível salvar a imagem selecionada.")
                    }
                    copiedFile = imageFile
                } finally {
                    if (temporaryFile.exists() && !temporaryFile.delete()) {
                        android.util.Log.w(
                            "B13FalconCamera",
                            "Não foi possível remover um arquivo temporário de imagem.",
                        )
                    }
                }
            } catch (error: Exception) {
                android.os.Handler(mainLooper).post {
                    result?.error(
                        "IMAGE_COPY_FAILED",
                        error.message ?: "Não foi possível abrir a imagem selecionada.",
                        null,
                    )
                }
                return@Thread
            }
            val savedFile = copiedFile
            if (savedFile == null) {
                android.os.Handler(mainLooper).post {
                    result?.error(
                        "IMAGE_COPY_FAILED",
                        "A cópia da imagem terminou sem gerar um arquivo.",
                        null,
                    )
                }
                return@Thread
            }
            val savedUri = Uri.fromFile(savedFile).toString()
            android.os.Handler(mainLooper).post {
                result?.success(savedUri)
            }
        }, "B13FalconImageCopy").start()
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            val call = pendingAudioCall
            val result = pendingStartResult
            pendingAudioCall = null
            pendingStartResult = null
            if (call != null && result != null) {
                prepareStartRecording(call, result)
            }
        } else if (requestCode == REQUEST_AUDIO_PERMISSION) {
            val call = pendingAudioCall
            val result = pendingStartResult
            pendingAudioCall = null
            pendingStartResult = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
                call != null && result != null) {
                prepareRecording(
                    call,
                    result,
                    call.argument<String>("audioMode") ?: "both",
                )
            } else {
                result?.success(
                    mapOf(
                        "success" to false,
                        "message" to "Permita o acesso ao microfone para gravar esse modo de áudio.",
                    ),
                )
            }
        } else if (requestCode == REQUEST_VIDEO_PERMISSION) {
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
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            val permissionGranted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            pendingCameraPermissionResult?.let { result ->
                pendingCameraPermissionResult = null
                result.success(permissionGranted)
            }
            val call = pendingAudioCall
            val result = pendingStartResult
            pendingAudioCall = null
            pendingStartResult = null
            if (call != null && result != null) {
                if (permissionGranted) {
                    prepareStartRecording(call, result)
                } else {
                    result.success(
                        mapOf(
                            "success" to false,
                            "message" to "Permita o acesso à câmera frontal para incluí-la no vídeo.",
                        ),
                    )
                }
            }
        }
    }
}
