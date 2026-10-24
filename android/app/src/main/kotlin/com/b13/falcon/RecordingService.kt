package com.b13.falcon

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import androidx.core.app.NotificationCompat

class RecordingService : Service() {
    companion object {
        const val ACTION_START = "com.b13.falcon.action.START"
        const val ACTION_STOP = "com.b13.falcon.action.STOP"
        const val ACTION_PAUSE = "com.b13.falcon.action.PAUSE"
        const val ACTION_RESUME = "com.b13.falcon.action.RESUME"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val CHANNEL_ID = "b13_falcon_recording"
        private const val NOTIFICATION_ID = 101
        private const val VIDEO_MIME = "video/avc"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var outputDescriptor: android.os.ParcelFileDescriptor? = null
    private var muxerTrack = -1
    private var muxerStarted = false
    private var recording = false
    private var paused = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var frameRate = 30
    private var bitRate = 8_000_000
    private var outputTitle = "B13_FALCON"
    private var drainThread: Thread? = null
    private val bufferInfo = MediaCodec.BufferInfo()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopRecording()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundCompat(buildNotification("Preparando gravação"))
                try {
                    startRecording(intent)
                } catch (error: Exception) {
                    stopRecording()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    sendStatus("error", error.message ?: "Não foi possível iniciar a captura.")
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_PAUSE -> {
                if (recording && !paused) {
                    virtualDisplay?.setSurface(null)
                    paused = true
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("Gravação pausada"))
                    sendStatus("paused", "Gravação pausada.")
                }
            }
            ACTION_RESUME -> {
                if (recording && paused) {
                    virtualDisplay?.setSurface(encoder?.inputSurface)
                    paused = false
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("B13 FALCON gravando..."))
                    sendStatus("recording", "Gravação retomada.")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(intent: Intent) {
        if (recording) return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val permissionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        } ?: throw IllegalArgumentException("Autorização de captura inválida.")

        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(displayMetrics)
        val sourceWidth = displayMetrics.widthPixels
        val sourceHeight = displayMetrics.heightPixels
        val resolution = intent.getStringExtra("resolution") ?: "720p"
        val maxDimension = when (resolution.lowercase(Locale.ROOT)) {
            "480p" -> 854
            "1080p" -> 1920
            else -> 1280
        }
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(sourceWidth, sourceHeight))
        displayWidth = ((sourceWidth * scale).toInt() / 2) * 2
        displayHeight = ((sourceHeight * scale).toInt() / 2) * 2
        frameRate = (intent.getIntExtra("fps", 30)).coerceIn(24, 60)
        bitRate = (intent.getIntExtra("bitrateKbps", 8000) * 1000).coerceAtLeast(500_000)
        outputTitle = (intent.getStringExtra("title") ?: "B13_FALCON").replace(Regex("[^A-Za-z0-9_-]"), "_")

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, permissionData).also {
            it.registerCallback(projectionCallback, android.os.Handler(mainLooper))
        }

        val format = MediaFormat.createVideoFormat(VIDEO_MIME, displayWidth, displayHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        encoder = MediaCodec.createEncoderByType(VIDEO_MIME).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        val inputSurface = encoder!!.createInputSurface()
        openOutput()
        encoder!!.start()
        virtualDisplay = projection!!.createVirtualDisplay(
            "B13 FALCON Capture",
            displayWidth,
            displayHeight,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface,
            null,
            null,
        ) ?: throw IOException("O Android não conseguiu criar a tela virtual.")
        recording = true
        paused = false
        drainThread = Thread(::drainEncoder, "B13FalconEncoderDrain").also { it.start() }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("B13 FALCON gravando..."))
        sendStatus("recording", "Gravação em andamento.")
    }

    private fun openOutput() {
        val displayName = "${outputTitle}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/B13 Falcon")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Não foi possível criar o arquivo de vídeo.")
            outputDescriptor = contentResolver.openFileDescriptor(outputUri!!, "w")
                ?: throw IOException("Não foi possível abrir o arquivo de vídeo.")
            muxer = MediaMuxer(outputDescriptor!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "B13 Falcon")
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Não foi possível criar a pasta de gravações.")
            outputFile = File(directory, displayName)
            muxer = MediaMuxer(outputFile!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
    }

    private fun drainEncoder() {
        val localEncoder = encoder ?: return
        var outputEnded = false
        try {
            while (!outputEnded) {
                val index = localEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxerTrack = muxer!!.addTrack(localEncoder.outputFormat)
                        muxer!!.start()
                        muxerStarted = true
                    }
                    index >= 0 -> {
                        val buffer = localEncoder.getOutputBuffer(index)
                        if (buffer != null && bufferInfo.size > 0 && muxerStarted) {
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(muxerTrack, buffer, bufferInfo)
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        localEncoder.releaseOutputBuffer(index, false)
                    }
                }
            }
        } catch (error: Exception) {
            sendStatus("error", error.message ?: "Falha ao codificar o vídeo.")
        } finally {
            finishOutput()
        }
    }

    private fun stopRecording() {
        if (!recording) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        recording = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            encoder?.signalEndOfInputStream()
        } catch (_: Exception) {
            finishOutput()
        }
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        drainThread?.join(3000)
        drainThread = null
        encoder?.runCatching { stop() }
        encoder?.release()
        encoder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        sendStatus("stopped", "Gravação finalizada.")
    }

    @Synchronized
    private fun finishOutput() {
        runCatching { if (muxerStarted) muxer?.stop() }
        runCatching { muxer?.release() }
        muxer = null
        muxerStarted = false
        runCatching { outputDescriptor?.close() }
        outputDescriptor = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputUri?.let { uri ->
                val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                runCatching { contentResolver.update(uri, values, null, null) }
            }
        } else {
            outputFile?.let { file ->
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            }
        }
    }

    private fun sendStatus(state: String, message: String) {
        sendBroadcast(Intent("com.b13.falcon.RECORDING_STATUS").setPackage(packageName).apply {
            putExtra("state", state)
            putExtra("message", message)
            putExtra("uri", outputUri?.toString())
            putExtra("path", outputFile?.absolutePath)
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "B13 FALCON gravação",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).apply { action = if (paused) ACTION_RESUME else ACTION_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("B13 FALCON")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sdcard)
            .addAction(if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause, if (paused) "Retomar" else "Pausar", pauseIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        super.onDestroy()
    }
}
