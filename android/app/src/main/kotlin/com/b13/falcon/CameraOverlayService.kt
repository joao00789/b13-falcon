package com.b13.falcon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import java.io.IOException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService

class CameraOverlayService : LifecycleService() {
    companion object {
        const val ACTION_UPDATE = "com.b13.falcon.camera.UPDATE"
        const val ACTION_SHOW = "com.b13.falcon.camera.SHOW"
        const val ACTION_HIDE = "com.b13.falcon.camera.HIDE"
        const val ACTION_STOP = "com.b13.falcon.camera.STOP"
        private const val CHANNEL_ID = "b13_falcon_camera"
        private const val NOTIFICATION_ID = 102
    }

    private lateinit var windowManager: WindowManager
    private var config = CameraOverlayConfig.fromJson("{}")
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var imageView: ImageView? = null
    private var previewFrame: CameraFrameView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var cameraUseCase: Preview? = null
    private var overlayAdded = false
    private var cameraBinding = false
    private var cameraBindingGeneration = 0
    private var imageLoadGeneration = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP, ACTION_HIDE -> stopOverlay()
            ACTION_SHOW, ACTION_UPDATE -> {
                val json = intent.getStringExtra("cameraConfigJson")
                    ?: CameraOverlayConfig.loadFromPreferences(
                        getSharedPreferences("b13_falcon_recording", MODE_PRIVATE),
                    )
                try {
                    config = CameraOverlayConfig.fromJson(json)
                    if (!config.enabled || config.mode != "overlay") {
                        stopOverlay()
                    } else {
                        startCameraOverlay()
                    }
                } catch (error: Exception) {
                    failCamera(error.message ?: "Não foi possível preparar a câmera.")
                }
            }
            else -> stopOverlay()
        }
        return START_NOT_STICKY
    }

    private fun startCameraOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            failCamera("A permissão para mostrar a câmera flutuante foi revogada.")
            return
        }
        val showingImage = !config.imageUri.isNullOrBlank()
        if (!showingImage &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            failCamera("A permissão da câmera foi revogada.")
            return
        }

        val cameraAlreadyBound =
            !showingImage && cameraUseCase != null && previewView?.isAttachedToWindow == true
        startCameraForeground(useCamera = !showingImage)
        try {
            ensurePreviewWindow()
        } catch (error: WindowManager.BadTokenException) {
            failCamera("O Android recusou a janela flutuante da câmera.")
            return
        } catch (error: SecurityException) {
            failCamera("A permissão para mostrar a câmera flutuante não está ativa.")
            return
        }
        if (showingImage) {
            cameraBindingGeneration++
            cameraBinding = false
            cameraProvider?.unbindAll()
            cameraUseCase = null
            previewView?.visibility = View.GONE
            imageView?.visibility = View.INVISIBLE
            loadSelectedImage(Uri.parse(config.imageUri))
            return
        }
        imageLoadGeneration++
        imageView?.setImageDrawable(null)
        imageView?.visibility = View.GONE
        previewView?.visibility = View.VISIBLE
        if (cameraAlreadyBound || cameraBinding) return

        val view = previewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        val bindingGeneration = ++cameraBindingGeneration
        cameraBinding = true
        providerFuture.addListener({
            if (bindingGeneration != cameraBindingGeneration) return@addListener
            cameraBinding = false
            if (config.imageUri != null || previewView !== view || !view.isAttachedToWindow) {
                return@addListener
            }
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                provider.unbindAll()
                val useCase = Preview.Builder().build().apply {
                    setSurfaceProvider(view.surfaceProvider)
                }
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCase,
                )
                cameraUseCase = useCase
            } catch (error: Exception) {
                failCamera(error.cause?.message ?: error.message ?: "A câmera frontal não iniciou.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadSelectedImage(uri: Uri) {
        val generation = ++imageLoadGeneration
        val targetSize = dp(config.sizeDp)
        Thread({
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                } ?: throw IOException("Não foi possível abrir a imagem selecionada.")
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IOException("O arquivo selecionado não contém uma imagem válida.")
                }
                var sampleSize = 1
                val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (longestSide / (sampleSize * 2) >= targetSize) {
                    sampleSize *= 2
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: throw IOException("Não foi possível decodificar a imagem selecionada.")
                android.os.Handler(mainLooper).post {
                    if (generation != imageLoadGeneration ||
                        config.imageUri != uri.toString() ||
                        imageView?.isAttachedToWindow != true
                    ) {
                        bitmap.recycle()
                        return@post
                    }
                    imageView?.apply {
                        setImageBitmap(bitmap)
                        visibility = View.VISIBLE
                    }
                }
            } catch (error: Exception) {
                android.os.Handler(mainLooper).post {
                    if (generation == imageLoadGeneration) {
                        failCamera(
                            error.message
                                ?: "Não foi possível carregar a imagem selecionada.",
                        )
                    }
                }
            }
        }, "B13FalconImageLoader").start()
    }

    private fun ensurePreviewWindow() {
        val sizePx = dp(config.sizeDp)
        if (previewFrame != null && overlayAdded) {
            applyFrameStyle()
            layoutParams?.let { params ->
                params.width = sizePx
                params.height = sizePx
                windowManager.updateViewLayout(previewFrame, params)
            }
            return
        }

        val view = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.TRANSPARENT)
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val frame = CameraFrameView(this).apply {
            elevation = dp(12).toFloat()
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                image,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (config.xFraction * (screenWidth() - sizePx).coerceAtLeast(0)).toInt()
            y = (config.yFraction * (screenHeight() - sizePx).coerceAtLeast(0)).toInt()
        }
        frame.setOnTouchListener(createDragListener(frame, params))
        previewView = view
        imageView = image
        previewFrame = frame
        layoutParams = params
        applyFrameStyle()
        windowManager.addView(frame, params)
        overlayAdded = true
    }

    private fun applyFrameStyle() {
        val frame = previewFrame ?: return
        frame.applyConfig(config)
    }

    private fun createDragListener(
        frame: View,
        params: WindowManager.LayoutParams,
    ): View.OnTouchListener {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX - startRawX).toInt()
                    params.y = (startY + event.rawY - startRawY).toInt()
                    clampPosition(params)
                    try {
                        windowManager.updateViewLayout(frame, params)
                    } catch (error: WindowManager.BadTokenException) {
                        failCamera("A janela flutuante da câmera foi desconectada.")
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val width = (params.width).coerceAtLeast(1)
                    val height = (params.height).coerceAtLeast(1)
                    config = config.copy(
                        xFraction = params.x.toFloat() /
                            (screenWidth() - width).coerceAtLeast(1),
                        yFraction = params.y.toFloat() /
                            (screenHeight() - height).coerceAtLeast(1),
                    )
                    getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
                        .edit()
                        .putString(
                            "cameraConfigJson",
                            config.toJson(),
                        )
                        .apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        params.x = params.x.coerceIn(
            0,
            (screenWidth() - params.width).coerceAtLeast(0),
        )
        params.y = params.y.coerceIn(
            0,
            (screenHeight() - params.height).coerceAtLeast(0),
        )
    }

    private fun startCameraForeground(useCamera: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Câmera frontal ativa")
            .setContentText("A prévia flutuante está pronta.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ocultar",
                PendingIntent.getService(
                    this,
                    102,
                    Intent(this, CameraOverlayService::class.java).setAction(ACTION_HIDE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34 && !useCamera) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (useCamera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun failCamera(message: String) {
        android.util.Log.e("B13FalconCamera", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        releaseCameraAndWindow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopOverlay() {
        releaseCameraAndWindow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseCameraAndWindow() {
        cameraBindingGeneration++
        imageLoadGeneration++
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraUseCase = null
        cameraBinding = false
        val frame = previewFrame
        if (frame != null && overlayAdded) {
            runCatching { windowManager.removeViewImmediate(frame) }
        }
        overlayAdded = false
        previewFrame = null
        previewView = null
        imageView = null
        layoutParams = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Prévia da câmera",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onDestroy() {
        releaseCameraAndWindow()
        super.onDestroy()
    }
}
