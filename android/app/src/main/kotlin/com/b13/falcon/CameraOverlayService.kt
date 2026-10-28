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
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.roundToInt
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
        const val ACTION_TOGGLE_LOCK = "com.b13.falcon.camera.TOGGLE_LOCK"
        private const val CHANNEL_ID = "b13_falcon_camera"
        private const val NOTIFICATION_ID = 103
    }

    private lateinit var windowManager: WindowManager
    private var config = CameraOverlayConfig.fromJson("{}")
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var imageView: ImageView? = null
    private var cameraLockButton: TextView? = null
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
            ACTION_TOGGLE_LOCK -> {
                config = CameraOverlayConfig.fromJson(
                    CameraOverlayConfig.loadFromPreferences(
                        getSharedPreferences("b13_falcon_recording", MODE_PRIVATE),
                    ),
                )
                setCameraLocked(!config.locked)
            }
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
                openImageStream(uri)?.use {
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
                val bitmap = openImageStream(uri)?.use {
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

    private fun openImageStream(uri: Uri): InputStream? {
        return when (uri.scheme) {
            "content" -> contentResolver.openInputStream(uri)
            "file" -> {
                val imageDirectory = File(filesDir, "camera_overlay").canonicalFile
                val imageFile = File(uri.path ?: return null).canonicalFile
                if (imageFile.parentFile != imageDirectory || !imageFile.isFile) {
                    throw IOException("A imagem salva da facecam não está disponível.")
                }
                FileInputStream(imageFile)
            }
            else -> throw IOException("O endereço da imagem selecionada não é suportado.")
        }
    }

    private fun ensurePreviewWindow() {
        val sizePx = dp(config.sizeDp)
        if (previewFrame != null && overlayAdded) {
            applyFrameStyle()
            layoutParams?.let { params ->
                params.width = sizePx
                params.height = sizePx
                clampPosition(params)
                windowManager.updateViewLayout(previewFrame, params)
            }
            previewView?.scaleX = if (config.mirror) -1f else 1f
            cameraLockButton?.text = lockButtonLabel()
            updateWindowTouchability()
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
        val frame = CameraFrameView(this).apply { elevation = dp(12).toFloat() }
        frame.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        frame.addView(
            image,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val toolbar = createCameraToolbar()
        frame.addView(
            toolbar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(32),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(4)
            },
        )
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
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val bounds = usableBounds()
            x = bounds.left +
                (config.xFraction * (bounds.width() - sizePx).coerceAtLeast(0)).roundToInt()
            y = bounds.top +
                (config.yFraction * (bounds.height() - sizePx).coerceAtLeast(0)).roundToInt()
        }
        frame.setOnTouchListener(createDragListener(frame, params))
        view.scaleX = if (config.mirror) -1f else 1f
        previewView = view
        imageView = image
        cameraLockButton = toolbar.getChildAt(1) as? TextView
        previewFrame = frame
        layoutParams = params
        applyFrameStyle()
        updateWindowTouchability()
        windowManager.addView(frame, params)
        overlayAdded = true
    }

    private fun createCameraToolbar(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(3), dp(2), dp(3), dp(2))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xCC111820.toInt())
            cornerRadius = dp(18).toFloat()
        }
        addView(createToolbarButton("−", "Diminuir tamanho da facecam") {
            resizeCamera(-12)
        })
        addView(createToolbarButton(lockButtonLabel(), "Travar ou destravar posição da facecam") {
            setCameraLocked(!config.locked)
        }.also { cameraLockButton = it })
        addView(createToolbarButton("+", "Aumentar tamanho da facecam") {
            resizeCamera(12)
        })
    }

    private fun createToolbarButton(
        label: String,
        description: String,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        gravity = Gravity.CENTER
        textSize = 17f
        setTextColor(Color.WHITE)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick() }
    }

    private fun lockButtonLabel(): String = if (config.locked) "🔒" else "🔓"

    private fun setCameraLocked(locked: Boolean) {
        config = config.copy(locked = locked)
        persistConfig()
        cameraLockButton?.apply {
            text = lockButtonLabel()
            contentDescription = if (locked) {
                "Posição travada. Use a notificação ou os controles flutuantes para destravar."
            } else {
                "Travar posição da facecam"
            }
        }
        updateWindowTouchability()
        startCameraForeground(useCamera = config.imageUri.isNullOrBlank())
    }

    private fun updateWindowTouchability() {
        val params = layoutParams ?: return
        params.flags = if (config.locked) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (overlayAdded) {
            runCatching { windowManager.updateViewLayout(previewFrame, params) }
                .onFailure {
                    android.util.Log.w(
                        "B13FalconCamera",
                        "Não foi possível atualizar o bloqueio da facecam.",
                        it,
                    )
                }
        }
    }

    private fun resizeCamera(deltaDp: Int) {
        config = config.copy(sizeDp = (config.sizeDp + deltaDp).coerceIn(80, 260))
        layoutParams?.let { params ->
            params.width = dp(config.sizeDp)
            params.height = dp(config.sizeDp)
            clampPosition(params)
            if (overlayAdded) windowManager.updateViewLayout(previewFrame, params)
        }
        persistConfig()
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
        var dragging = false
        var pinching = false
        var pinchStartDistance = 0f
        var pinchStartSizeDp = config.sizeDp
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        return View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    pinching = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        pinching = true
                        dragging = false
                        pinchStartDistance = pointerDistance(event)
                        pinchStartSizeDp = config.sizeDp
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pinching && event.pointerCount >= 2) {
                        val deltaDp = (pointerDistance(event) - pinchStartDistance) /
                            resources.displayMetrics.density
                        config = config.copy(
                            sizeDp = (pinchStartSizeDp + deltaDp.roundToInt())
                                .coerceIn(80, 260),
                        )
                        params.width = dp(config.sizeDp)
                        params.height = dp(config.sizeDp)
                        clampPosition(params)
                        windowManager.updateViewLayout(frame, params)
                    } else {
                        val deltaX = event.rawX - startRawX
                        val deltaY = event.rawY - startRawY
                        if (!dragging && hypot(deltaX, deltaY) >= touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            params.x = (startX + deltaX).roundToInt()
                            params.y = (startY + deltaY).roundToInt()
                            clampPosition(params)
                            windowManager.updateViewLayout(frame, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount <= 2) {
                        pinching = false
                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                        if (remainingIndex < event.pointerCount) {
                            startRawX = event.rawX + event.getX(remainingIndex) - event.getX(0)
                            startRawY = event.rawY + event.getY(remainingIndex) - event.getY(0)
                            startX = params.x
                            startY = params.y
                            dragging = false
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val bounds = usableBounds()
                    config = config.copy(
                        xFraction = (params.x - bounds.left).toFloat() /
                            (bounds.width() - params.width).coerceAtLeast(1),
                        yFraction = (params.y - bounds.top).toFloat() /
                            (bounds.height() - params.height).coerceAtLeast(1),
                    )
                    persistConfig()
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition(params: WindowManager.LayoutParams) {
        val bounds = usableBounds()
        params.x = params.x.coerceIn(
            bounds.left,
            (bounds.right - params.width).coerceAtLeast(bounds.left),
        )
        params.y = params.y.coerceIn(
            bounds.top,
            (bounds.bottom - params.height).coerceAtLeast(bounds.top),
        )
    }

    private fun pointerDistance(event: MotionEvent): Float = hypot(
        event.getX(0) - event.getX(1),
        event.getY(0) - event.getY(1),
    )

    private fun persistConfig() {
        getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
            .edit()
            .putString("cameraConfigJson", config.toJson())
            .apply()
    }

    private fun usableBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            return Rect(metrics.bounds).apply {
                val insets = metrics.windowInsets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                inset(insets.left, insets.top, insets.right, insets.bottom)
            }
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutParams?.let { params ->
            val bounds = usableBounds()
            params.x = bounds.left +
                (config.xFraction * (bounds.width() - params.width).coerceAtLeast(0)).roundToInt()
            params.y = bounds.top +
                (config.yFraction * (bounds.height() - params.height).coerceAtLeast(0)).roundToInt()
            clampPosition(params)
            if (overlayAdded) windowManager.updateViewLayout(previewFrame, params)
        }
    }

    private fun startCameraForeground(useCamera: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Câmera frontal ativa")
            .setContentText("A prévia flutuante está pronta.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(
                if (config.locked) android.R.drawable.ic_lock_idle_lock
                else android.R.drawable.ic_lock_lock,
                if (config.locked) "Destravar posição" else "Travar posição",
                PendingIntent.getService(
                    this,
                    103,
                    Intent(this, CameraOverlayService::class.java).setAction(ACTION_TOGGLE_LOCK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ocultar",
                PendingIntent.getService(
                    this,
                    104,
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
        cameraLockButton = null
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun onDestroy() {
        releaseCameraAndWindow()
        super.onDestroy()
    }
}
