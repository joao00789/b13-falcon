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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.AudioPlaybackCaptureConfiguration
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
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
        const val STATUS_ACTION = "com.b13.falcon.RECORDING_STATUS"
        private const val NOTIFICATION_ID = 101
        private const val VIDEO_MIME = "video/avc"
        private const val HEVC_MIME = "video/hevc"
        private const val AUDIO_MIME = "audio/mp4a-latm"
        private const val AUDIO_SAMPLE_RATE = 48_000
        private const val AUDIO_FRAME_SAMPLES = 960
        private const val PLAYBACK_CHANNELS = 2
    }

    private data class EncodedSample(
        val track: Int,
        val data: java.nio.ByteBuffer,
        val info: MediaCodec.BufferInfo,
    )

    private data class PauseInterval(
        val startRealtimeUs: Long,
        var endRealtimeUs: Long? = null,
    )

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var microphoneRecorder: AudioRecord? = null
    private var playbackRecorder: AudioRecord? = null
    private var muxer: MediaMuxer? = null
    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var outputDescriptor: android.os.ParcelFileDescriptor? = null
    private var muxerStarted = false
    @Volatile private var recording = false
    @Volatile private var paused = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var frameRate = 30
    private var bitRate = 8_000_000
    private var videoMime = VIDEO_MIME
    private var outputTitle = "B13_FALCON"
    private var drainThread: Thread? = null
    private var audioDrainThread: Thread? = null
    private var microphoneReaderThread: Thread? = null
    private var playbackReaderThread: Thread? = null
    private var audioMixerThread: Thread? = null
    private var audioRequired = false
    private var microphoneRequired = false
    private var playbackRequired = false
    private var audioChannelCount = 1
    private var videoTrack = -1
    private var audioTrack = -1
    @Volatile private var stopping = false
    private val muxerLock = Any()
    private val stopLock = Any()
    private val pendingSamples = mutableListOf<EncodedSample>()
    private val pauseIntervals = mutableListOf<PauseInterval>()
    private var videoClockOffsetUs: Long? = null
    private var videoStartPresentationTimeUs: Long? = null
    private val microphoneFrames = ArrayBlockingQueue<ShortArray>(16)
    private val playbackFrames = ArrayBlockingQueue<ShortArray>(4)

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
                if (stopping) {
                    sendStatus("error", "A gravação anterior ainda está sendo finalizada.")
                } else {
                    val audioMode = intent.getStringExtra("audioMode")
                    val needsMicrophone =
                        audioMode == "both" || audioMode == "microphone" || audioMode == "Microfone"
                    startForegroundCompat(buildNotification("Preparando gravação"), needsMicrophone)
                    try {
                        startRecording(intent)
                    } catch (error: Exception) {
                        stopRecording(
                            state = "error",
                            message = error.message ?: "Não foi possível iniciar a captura.",
                        )
                    }
                }
            }
            ACTION_STOP -> {
                stopRecording()
            }
            ACTION_PAUSE -> {
                if (recording && !paused) {
                    virtualDisplay?.setSurface(null)
                    synchronized(pauseIntervals) {
                        pauseIntervals.add(PauseInterval(System.nanoTime() / 1000L))
                    }
                    paused = true
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("Gravação pausada"))
                    sendStatus("paused", "Gravação pausada.")
                }
                if (!recording && !stopping) stopSelf()
            }
            ACTION_RESUME -> {
                if (recording && paused) {
                    virtualDisplay?.setSurface(encoderInputSurface)
                    synchronized(pauseIntervals) {
                        pauseIntervals.lastOrNull()?.endRealtimeUs = System.nanoTime() / 1000L
                    }
                    paused = false
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification("B13 FALCON gravando..."))
                    sendStatus("recording", "Gravação retomada.")
                }
                if (!recording && !stopping) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification, withMicrophone: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (withMicrophone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startRecording(intent: Intent) {
        if (stopping) {
            throw IOException("A gravação anterior ainda está sendo finalizada.")
        }
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
        val requestedOrientation = intent.getStringExtra("orientation") ?: "auto"
        var sourceWidth = if (requestedOrientation == "auto") {
            intent.getIntExtra("sourceWidth", 0).takeIf { it > 0 }
                ?: displayMetrics.widthPixels
        } else {
            displayMetrics.widthPixels
        }
        var sourceHeight = if (requestedOrientation == "auto") {
            intent.getIntExtra("sourceHeight", 0).takeIf { it > 0 }
                ?: displayMetrics.heightPixels
        } else {
            displayMetrics.heightPixels
        }
        when (requestedOrientation) {
            "portrait" -> if (sourceWidth > sourceHeight) {
                val oldWidth = sourceWidth
                sourceWidth = sourceHeight
                sourceHeight = oldWidth
            }
            "landscape" -> if (sourceHeight > sourceWidth) {
                val oldWidth = sourceWidth
                sourceWidth = sourceHeight
                sourceHeight = oldWidth
            }
        }
        val resolution = intent.getStringExtra("resolution") ?: "720p"
        val maxDimension = when (resolution.lowercase(Locale.ROOT)) {
            "360p" -> 640
            "480p" -> 854
            "540p" -> 960
            "1080p" -> 1920
            "1440p" -> 2560
            "native" -> maxOf(sourceWidth, sourceHeight)
            else -> 1280
        }
        val scale = minOf(1f, maxDimension.toFloat() / maxOf(sourceWidth, sourceHeight))
        displayWidth = ((sourceWidth * scale).toInt() / 2) * 2
        displayHeight = ((sourceHeight * scale).toInt() / 2) * 2
        frameRate = (intent.getIntExtra("fps", 30)).coerceIn(15, 90)
        bitRate = (intent.getIntExtra("bitrateKbps", 16000).coerceIn(4_000, 80_000)) * 1000
        val requestedMime = if (intent.getStringExtra("codec") == "hevc") HEVC_MIME else VIDEO_MIME
        videoMime = if (supportsVideoFormat(requestedMime)) requestedMime else VIDEO_MIME
        outputTitle = (intent.getStringExtra("title") ?: "B13_FALCON").replace(Regex("[^A-Za-z0-9_-]"), "_")
        val audioMode = when (intent.getStringExtra("audioMode")) {
            "Microfone", "microphone" -> "microphone"
            "internal" -> "internal"
            "none" -> "none"
            else -> "both"
        }
        microphoneRequired = audioMode == "microphone" || audioMode == "both"
        playbackRequired = audioMode == "internal" || audioMode == "both"
        audioChannelCount = if (playbackRequired && !microphoneRequired) {
            PLAYBACK_CHANNELS
        } else {
            1
        }
        var playbackFallback = false
        if (playbackRequired && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (audioMode == "internal") {
                throw IOException("A captura do áudio interno exige Android 10 ou superior.")
            }
            playbackRequired = false
            playbackFallback = true
        }
        audioRequired = microphoneRequired || playbackRequired
        videoTrack = -1
        audioTrack = -1
        muxerStarted = false
        synchronized(muxerLock) { pendingSamples.clear() }
        synchronized(pauseIntervals) { pauseIntervals.clear() }
        videoClockOffsetUs = null
        videoStartPresentationTimeUs = null
        microphoneFrames.clear()
        playbackFrames.clear()

        val manager = getSystemService(MediaProjectionManager::class.java)
        val activeProjection = manager.getMediaProjection(resultCode, permissionData)
            ?: throw IOException("O Android não iniciou a projeção da tela.")
        projection = activeProjection
        activeProjection.registerCallback(projectionCallback, android.os.Handler(mainLooper))
        if (audioRequired) prepareAudioCapture(activeProjection)

        val format = MediaFormat.createVideoFormat(videoMime, displayWidth, displayHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        val videoEncoder = createVideoEncoder(format)
        encoder = videoEncoder
        val inputSurface = videoEncoder.createInputSurface()
        encoderInputSurface = inputSurface
        openOutput()
        videoEncoder.start()
        virtualDisplay = activeProjection.createVirtualDisplay(
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
        if (audioRequired) {
            audioDrainThread = Thread(::drainAudioEncoder, "B13FalconAudioDrain").also { it.start() }
            startAudioCaptureThreads()
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification("B13 FALCON gravando..."))
        sendStatus(
            "recording",
            buildString {
                append("Gravação em andamento.")
                if (requestedMime == HEVC_MIME && videoMime != HEVC_MIME) {
                    append(" HEVC não suportado nesta resolução/FPS; usando H.264.")
                }
                if (playbackFallback) {
                    append(" Áudio interno exige Android 10 ou superior; gravando pelo microfone.")
                }
            },
        )
    }

    private fun supportsVideoFormat(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { codecInfo ->
            if (!codecInfo.isEncoder ||
                codecInfo.supportedTypes.none { it.equals(mime, ignoreCase = true) }
            ) {
                false
            } else {
                runCatching {
                    val codecCapabilities = codecInfo.getCapabilitiesForType(mime)
                    codecCapabilities.colorFormats.contains(
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                    ) && codecCapabilities.videoCapabilities.areSizeAndRateSupported(
                        displayWidth,
                        displayHeight,
                        frameRate.toDouble(),
                    )
                }.getOrDefault(false)
            }
        }

    private fun createVideoEncoder(format: MediaFormat): MediaCodec {
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { codecInfo ->
                codecInfo.isEncoder &&
                    codecInfo.supportedTypes.any { it.equals(videoMime, ignoreCase = true) }
            }
            .sortedBy { codecInfo ->
                val softwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    codecInfo.isSoftwareOnly
                } else {
                    codecInfo.name.contains("google", ignoreCase = true) ||
                        codecInfo.name.contains("software", ignoreCase = true)
                }
                if (softwareOnly) 1 else 0
            }

        var lastConfigurationError: Exception? = null
        for (codecInfo in candidates) {
            val capabilities = try {
                codecInfo.getCapabilitiesForType(videoMime)
            } catch (error: IllegalArgumentException) {
                lastConfigurationError = error
                continue
            }
            if (!capabilities.colorFormats.contains(
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
            ) {
                continue
            }
            val videoCapabilities = capabilities.videoCapabilities
            val supportsRequestedFormat = try {
                videoCapabilities.areSizeAndRateSupported(
                    displayWidth,
                    displayHeight,
                    frameRate.toDouble(),
                )
            } catch (error: IllegalArgumentException) {
                lastConfigurationError = error
                false
            }
            if (!supportsRequestedFormat) continue

            val candidate = try {
                MediaCodec.createByCodecName(codecInfo.name)
            } catch (error: Exception) {
                lastConfigurationError = error
                continue
            }
            try {
                format.setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    videoCapabilities.bitrateRange.clamp(bitRate),
                )
                candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                return candidate
            } catch (error: Exception) {
                candidate.release()
                lastConfigurationError = error
            }
        }

        val fallback = try {
            MediaCodec.createEncoderByType(videoMime)
        } catch (error: Exception) {
            throw IOException("O Android não encontrou um codificador de vídeo compatível.", error)
        }
        try {
            val capabilities = fallback.codecInfo.getCapabilitiesForType(videoMime)
            format.setInteger(
                MediaFormat.KEY_BIT_RATE,
                capabilities.videoCapabilities.bitrateRange.clamp(bitRate),
            )
            fallback.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            return fallback
        } catch (error: Exception) {
            fallback.release()
            lastConfigurationError?.let(error::addSuppressed)
            throw IOException(
                "O Android não conseguiu configurar o codificador de vídeo para "
                    + "${displayWidth}x${displayHeight} a ${frameRate} FPS.",
                error,
            )
        }

    }

    private fun prepareAudioCapture(activeProjection: MediaProjection) {
        val audioFormat = MediaFormat.createAudioFormat(
            AUDIO_MIME,
            AUDIO_SAMPLE_RATE,
            audioChannelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(
                MediaFormat.KEY_MAX_INPUT_SIZE,
                AUDIO_FRAME_SAMPLES * audioChannelCount * Short.SIZE_BYTES,
            )
        }
        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME).apply {
            configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        if (microphoneRequired) {
            val bufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (bufferSize <= 0) throw IOException("Não foi possível preparar o microfone.")
            val frameBufferSize = AUDIO_FRAME_SAMPLES * Short.SIZE_BYTES * 8
            microphoneRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(bufferSize, frameBufferSize),
            ).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) {
                    throw IOException("O microfone não pôde ser inicializado.")
                }
            }
        }

        if (playbackRequired) {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(activeProjection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val minBufferSize = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferSize <= 0) throw IOException("Não foi possível preparar o áudio interno.")
            playbackRecorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .setBufferSizeInBytes(
                    maxOf(
                        minBufferSize * 2,
                        AUDIO_FRAME_SAMPLES * PLAYBACK_CHANNELS * Short.SIZE_BYTES * 2,
                    ),
                )
                .build()
                .also {
                    if (it.state != AudioRecord.STATE_INITIALIZED) {
                        throw IOException("O áudio interno não pôde ser inicializado.")
                    }
                    it.startRecording()
                }
        }
    }

    private fun startAudioCaptureThreads() {
        microphoneRecorder?.let { recorder ->
            microphoneReaderThread = Thread({
                try {
                    while (recording) {
                        if (paused) {
                            microphoneFrames.clear()
                            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                recorder.stop()
                            }
                            TimeUnit.MILLISECONDS.sleep(10)
                            continue
                        }
                        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            recorder.startRecording()
                        }
                        val frame = readMicrophoneFrame(recorder) ?: continue
                        if (!recording || paused) continue
                        while (recording && !paused &&
                            !microphoneFrames.offer(frame, 100, TimeUnit.MILLISECONDS)
                        ) {
                            // Apply backpressure rather than discarding captured microphone audio.
                        }
                    }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    if (recording) {
                        sendStatus("error", error.message ?: "Falha ao capturar o áudio do microfone.")
                        android.os.Handler(mainLooper).post {
                            if (recording) stopRecording()
                        }
                    }
                }
            }, "B13FalconMicReader").also { it.start() }
        }
        playbackRecorder?.let { recorder ->
            playbackReaderThread = Thread({
                try {
                    while (recording) {
                        val stereo = readPlaybackFrame(recorder) ?: continue
                        if (!recording || paused) continue

                        val frame = if (microphoneRequired) {
                            ShortArray(AUDIO_FRAME_SAMPLES) { index ->
                                (
                                    (stereo[index * PLAYBACK_CHANNELS].toInt() +
                                        stereo[index * PLAYBACK_CHANNELS + 1].toInt()) / 2
                                    ).toShort()
                            }
                        } else {
                            stereo
                        }
                        while (recording && !paused &&
                            !playbackFrames.offer(frame, 100, TimeUnit.MILLISECONDS)
                        ) {
                            // Keep audio in order instead of dropping old samples.
                        }
                    }
                } catch (error: Exception) {
                    if (recording) {
                        sendStatus(
                            "error",
                            error.message ?: "Falha ao capturar o áudio interno.",
                        )
                        android.os.Handler(mainLooper).post {
                            if (recording) stopRecording()
                        }
                    }
                }
            }, "B13FalconPlaybackReader").also { it.start() }
        }
        audioMixerThread = Thread(::mixAudioFrames, "B13FalconAudioMixer").also { it.start() }
    }

    private fun readMicrophoneFrame(recorder: AudioRecord): ShortArray? {
        val frame = ShortArray(AUDIO_FRAME_SAMPLES)
        var samplesRead = 0
        while (samplesRead < frame.size && recording && !paused) {
            val read = recorder.read(
                frame,
                samplesRead,
                frame.size - samplesRead,
                AudioRecord.READ_BLOCKING,
            )
            if (read < 0) {
                if (!recording || paused) return null
                throw IOException("Falha ao ler o microfone (AudioRecord: $read).")
            }
            samplesRead += read
        }
        return frame.takeIf { samplesRead == frame.size }
    }

    private fun readPlaybackFrame(recorder: AudioRecord): ShortArray? {
        val frame = ShortArray(AUDIO_FRAME_SAMPLES * PLAYBACK_CHANNELS)
        var samplesRead = 0
        while (samplesRead < frame.size && recording) {
            val read = recorder.read(
                frame,
                samplesRead,
                frame.size - samplesRead,
                AudioRecord.READ_BLOCKING,
            )
            if (read < 0) {
                if (!recording) return null
                throw IOException("Falha ao ler áudio interno (AudioRecord: $read).")
            }
            samplesRead += read
        }
        return frame.takeIf { samplesRead == frame.size }
    }

    private fun mixAudioFrames() {
        val audioCodec = audioEncoder ?: return
        if (playbackRequired && !microphoneRequired) {
            mixPlaybackOnly(audioCodec)
            return
        }
        var presentationTimeUs = 0L
        while (recording) {
            if (paused) {
                microphoneFrames.clear()
                playbackFrames.clear()
                TimeUnit.MILLISECONDS.sleep(10)
                continue
            }
            val microphone = if (microphoneRequired) {
                microphoneFrames.poll(100, TimeUnit.MILLISECONDS)
            } else null
            if (microphoneRequired && microphone == null) {
                continue
            }
            val playback = if (playbackRequired && !paused) {
                playbackFrames.poll(10, TimeUnit.MILLISECONDS)
            } else {
                null
            }
            val inputIndex = audioCodec.dequeueInputBuffer(10_000)
            if (inputIndex < 0) continue
            val inputBuffer = audioCodec.getInputBuffer(inputIndex)
                ?.order(java.nio.ByteOrder.LITTLE_ENDIAN) ?: continue
            inputBuffer.clear()
            for (index in 0 until AUDIO_FRAME_SAMPLES) {
                val mic = microphone?.getOrNull(index)?.toInt() ?: 0
                val game = playback?.getOrNull(index)?.toInt() ?: 0
                val combined = when {
                    microphone != null && playback != null -> (mic + game) / 2
                    microphone != null -> mic
                    playback != null -> game
                    else -> 0
                }
                inputBuffer.putShort(
                    combined.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                )
            }
            audioCodec.queueInputBuffer(
                inputIndex,
                0,
                AUDIO_FRAME_SAMPLES * 2,
                presentationTimeUs,
                0,
            )
            presentationTimeUs += AUDIO_FRAME_SAMPLES * 1_000_000L / AUDIO_SAMPLE_RATE
        }
        val inputIndex = audioCodec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            audioCodec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
        }
    }

    private fun mixPlaybackOnly(audioCodec: MediaCodec) {
        var presentationTimeUs = 0L
        var pendingFrame: ShortArray? = null
        while (recording) {
            if (paused) {
                playbackFrames.clear()
                pendingFrame = null
                TimeUnit.MILLISECONDS.sleep(10)
                continue
            }
            if (pendingFrame == null) {
                pendingFrame = playbackFrames.poll(10, TimeUnit.MILLISECONDS)
                if (pendingFrame == null) continue
            }

            val inputIndex = audioCodec.dequeueInputBuffer(10_000)
            if (inputIndex < 0) continue
            val frame = pendingFrame ?: continue
            val inputBuffer = audioCodec.getInputBuffer(inputIndex)
                ?.order(java.nio.ByteOrder.LITTLE_ENDIAN) ?: continue
            inputBuffer.clear()
            for (sample in frame) {
                inputBuffer.putShort(sample)
            }
            audioCodec.queueInputBuffer(
                inputIndex,
                0,
                frame.size * Short.SIZE_BYTES,
                presentationTimeUs,
                0,
            )
            presentationTimeUs += AUDIO_FRAME_SAMPLES * 1_000_000L / AUDIO_SAMPLE_RATE
            pendingFrame = null
        }
        val inputIndex = audioCodec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            audioCodec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
        }
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
        val localBufferInfo = MediaCodec.BufferInfo()
        var outputEnded = false
        try {
            while (!outputEnded) {
                val index = localEncoder.dequeueOutputBuffer(localBufferInfo, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            videoTrack = muxer!!.addTrack(localEncoder.outputFormat)
                            startMuxerIfReady()
                        }
                    }
                    index >= 0 -> {
                        val buffer = localEncoder.getOutputBuffer(index)
                        if (buffer != null && localBufferInfo.size > 0) {
                            val adjustedPresentationTimeUs =
                                normalizeVideoPresentationTimeUs(localBufferInfo.presentationTimeUs)
                            if (adjustedPresentationTimeUs != null) {
                                writeMuxerSample(
                                    videoTrack,
                                    buffer,
                                    localBufferInfo,
                                    adjustedPresentationTimeUs,
                                )
                            }
                        }
                        outputEnded =
                            localBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        localEncoder.releaseOutputBuffer(index, false)
                    }
                }
            }
        } catch (error: Exception) {
            sendStatus("error", error.message ?: "Falha ao codificar o vídeo.")
        }
    }

    private fun drainAudioEncoder() {
        val localEncoder = audioEncoder ?: return
        val localBufferInfo = MediaCodec.BufferInfo()
        var outputEnded = false
        try {
            while (!outputEnded) {
                val index = localEncoder.dequeueOutputBuffer(localBufferInfo, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        synchronized(muxerLock) {
                            audioTrack = muxer!!.addTrack(localEncoder.outputFormat)
                            startMuxerIfReady()
                        }
                    }
                    index >= 0 -> {
                        val buffer = localEncoder.getOutputBuffer(index)
                        if (buffer != null && localBufferInfo.size > 0) {
                            writeMuxerSample(audioTrack, buffer, localBufferInfo)
                        }
                        outputEnded =
                            localBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        localEncoder.releaseOutputBuffer(index, false)
                    }
                }
            }
        } catch (error: Exception) {
            sendStatus("error", error.message ?: "Falha ao codificar o áudio.")
        }
    }

    private fun startMuxerIfReady() {
        if (muxerStarted || videoTrack < 0 || (audioRequired && audioTrack < 0)) return
        muxer!!.start()
        muxerStarted = true
        pendingSamples
            .sortedBy { it.info.presentationTimeUs }
            .forEach { sample -> muxer!!.writeSampleData(sample.track, sample.data, sample.info) }
        pendingSamples.clear()
    }

    private fun writeMuxerSample(
        track: Int,
        source: java.nio.ByteBuffer,
        sourceInfo: MediaCodec.BufferInfo,
        presentationTimeUs: Long = sourceInfo.presentationTimeUs,
    ) {
        synchronized(muxerLock) {
            if (track < 0 || sourceInfo.size <= 0) return
            val data = java.nio.ByteBuffer.allocateDirect(sourceInfo.size)
            val sample = source.duplicate().apply {
                position(sourceInfo.offset)
                limit(sourceInfo.offset + sourceInfo.size)
            }
            data.put(sample).flip()
            val info = MediaCodec.BufferInfo().apply {
                set(0, sourceInfo.size, presentationTimeUs, sourceInfo.flags)
            }
            if (muxerStarted) {
                muxer!!.writeSampleData(track, data, info)
            } else {
                pendingSamples.add(EncodedSample(track, data, info))
            }
        }
    }

    private fun normalizeVideoPresentationTimeUs(presentationTimeUs: Long): Long? {
        synchronized(pauseIntervals) {
            if (videoClockOffsetUs == null) {
                videoClockOffsetUs = System.nanoTime() / 1000L - presentationTimeUs
                videoStartPresentationTimeUs = presentationTimeUs
            }
            val clockOffset = videoClockOffsetUs ?: return presentationTimeUs
            val recordingStart = videoStartPresentationTimeUs ?: presentationTimeUs
            var removedPauseDurationUs = 0L
            for (interval in pauseIntervals) {
                val startUs = interval.startRealtimeUs - clockOffset
                val endUs = interval.endRealtimeUs?.minus(clockOffset)
                if (presentationTimeUs >= startUs && (endUs == null || presentationTimeUs < endUs)) {
                    return null
                }
                if (endUs != null && presentationTimeUs >= endUs) {
                    removedPauseDurationUs += endUs - startUs
                }
            }
            return (presentationTimeUs - recordingStart - removedPauseDurationUs)
                .coerceAtLeast(0L)
        }
    }

    private fun stopRecording(
        state: String = "stopped",
        message: String = "Gravação finalizada.",
    ) {
        val wasRecording = recording
        synchronized(stopLock) {
            if (stopping) return
            stopping = true
            recording = false
        }
        var finalState = state
        var finalMessage = message

        Thread({
            try {
                runCatching { virtualDisplay?.release() }
                virtualDisplay = null
                runCatching { microphoneRecorder?.stop() }
                runCatching { playbackRecorder?.stop() }
                joinThread(microphoneReaderThread, 1000)
                joinThread(playbackReaderThread, 1000)
                joinThread(audioMixerThread, 1000)
                runCatching { encoder?.signalEndOfInputStream() }
                if (audioMixerThread == null) {
                    runCatching { audioEncoder?.signalEndOfInputStream() }
                }
                projection?.unregisterCallback(projectionCallback)
                runCatching { projection?.stop() }
                projection = null
                joinThread(drainThread, 5000)
                drainThread = null
                joinThread(audioDrainThread, 5000)
                audioDrainThread = null
                runCatching { encoder?.stop() }
                runCatching { encoder?.release() }
                encoder = null
                runCatching { audioEncoder?.stop() }
                runCatching { audioEncoder?.release() }
                audioEncoder = null
                runCatching { microphoneRecorder?.release() }
                microphoneRecorder = null
                runCatching { playbackRecorder?.release() }
                playbackRecorder = null
                runCatching { encoderInputSurface?.release() }
                encoderInputSurface = null
                microphoneReaderThread = null
                playbackReaderThread = null
                audioMixerThread = null
                finishOutput()
            } catch (error: Exception) {
                finalState = "error"
                finalMessage = error.message ?: "Não foi possível finalizar a gravação."
            } finally {
                android.os.Handler(mainLooper).post {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (wasRecording || finalState == "error") {
                        sendStatus(finalState, finalMessage)
                    }
                    stopping = false
                    stopSelf()
                }
            }
        }, "B13FalconRecordingStop").start()
    }

    private fun joinThread(thread: Thread?, timeoutMs: Long) {
        try {
            thread?.join(timeoutMs)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
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
        sendBroadcast(Intent(STATUS_ACTION).setPackage(packageName).apply {
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
