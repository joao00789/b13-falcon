package com.b13.falcon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
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
    private var previewFrame: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var cameraUseCase: Preview? = null
    private var overlayAdded = false
    private var cameraBinding = false

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            failCamera("A permissão da câmera foi revogada.")
            return
        }

        val cameraAlreadyBound = cameraUseCase != null && previewView?.isAttachedToWindow == true
        startCameraForeground()
        try {
            ensurePreviewWindow()
        } catch (error: WindowManager.BadTokenException) {
            failCamera("O Android recusou a janela flutuante da câmera.")
            return
        } catch (error: SecurityException) {
            failCamera("A permissão para mostrar a câmera flutuante não está ativa.")
            return
        }
        if (cameraAlreadyBound || cameraBinding) return

        val view = previewView ?: return
        val providerFuture = ProcessCameraProvider.getInstance(this)
        cameraBinding = true
        providerFuture.addListener({
            cameraBinding = false
            if (previewView !== view || !view.isAttachedToWindow) return@addListener
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
        val frame = FrameLayout(this).apply {
            clipToOutline = true
            elevation = dp(12).toFloat()
            addView(
                view,
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
        previewFrame = frame
        layoutParams = params
        applyFrameStyle()
        windowManager.addView(frame, params)
        overlayAdded = true
    }

    private fun applyFrameStyle() {
        val frame = previewFrame ?: return
        frame.background = GradientDrawable().apply {
            setColor(Color.argb(188, 8, 11, 18))
            when (config.shape) {
                "circle" -> shape = GradientDrawable.OVAL
                "square" -> cornerRadius = 0f
                else -> cornerRadius = dp(config.cornerRadiusDp).toFloat()
            }
            setStroke(dp(2), config.borderColor)
        }
        frame.clipToOutline = true
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

    private fun startCameraForeground() {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
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
