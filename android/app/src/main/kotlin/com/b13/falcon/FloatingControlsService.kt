package com.b13.falcon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.util.DisplayMetrics
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class FloatingControlsService : Service() {
    private enum class DrawingTool { FREEHAND, RECTANGLE, CIRCLE }

    companion object {
        const val ACTION_DISMISS = "com.b13.falcon.overlay.DISMISS"
        const val ACTION_UPDATE_OPACITY = "com.b13.falcon.overlay.UPDATE_OPACITY"
        const val ACTION_UPDATE_SIZE = "com.b13.falcon.overlay.UPDATE_SIZE"
        const val ACTION_NOTIFICATION_START = "com.b13.falcon.overlay.NOTIFICATION_START"
        const val ACTION_NOTIFICATION_BRUSH = "com.b13.falcon.overlay.NOTIFICATION_BRUSH"
        const val ACTION_NOTIFICATION_PAUSE = "com.b13.falcon.overlay.NOTIFICATION_PAUSE"
        const val ACTION_NOTIFICATION_CAMERA_LOCK =
            "com.b13.falcon.overlay.NOTIFICATION_CAMERA_LOCK"
        private const val CHANNEL_ID = "b13_falcon_floating"
        private const val NOTIFICATION_ID = 102
    }

    private lateinit var windowManager: WindowManager
    private var controlsView: View? = null
    private var settingsView: View? = null
    private var drawingView: DrawingOverlayView? = null
    private var actionButtons: LinearLayout? = null
    private var startButton: ImageView? = null
    private var pauseButton: ImageView? = null
    private var drawButton: ImageView? = null
    private var stopButton: ImageView? = null
    private var cameraLockButton: ImageView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var bubbleDragged = false
    private var paused = false
    private var recording = false
    private var logoOpacity = 0.85f
    private var logoSizeDp = 56
    private var brushColor = Color.YELLOW
    private var brushOpacity = 1f
    private var brushWidthDp = 5f
    private data class Stroke(
        val path: Path,
        val paint: Paint,
        val tool: DrawingTool = DrawingTool.FREEHAND,
        val startX: Float = 0f,
        val startY: Float = 0f,
        var endX: Float = 0f,
        var endY: Float = 0f,
    )
    private val brushColors = listOf(
        Color.YELLOW,
        Color.WHITE,
        Color.RED,
        Color.BLUE,
        Color.GREEN,
        Color.CYAN,
        Color.MAGENTA,
    )

    private val recordingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("state")) {
                "paused" -> {
                    recording = true
                    paused = true
                    refreshControls()
                    refreshNotification()
                }
                "recording" -> {
                    recording = true
                    paused = false
                    refreshControls()
                    refreshNotification()
                }
                "stopped", "error" -> {
                    recording = false
                    paused = false
                    refreshControls()
                    refreshNotification()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val filter = IntentFilter(RecordingService.STATUS_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(recordingReceiver, filter)
        }
        when (RecordingService.currentStatus()) {
            "recording" -> {
                recording = true
                paused = false
            }
            "paused" -> {
                recording = true
                paused = true
            }
            else -> {
                recording = false
                paused = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (intent?.action == ACTION_NOTIFICATION_START) {
            startCaptureFromFloating()
        } else if (intent?.action == ACTION_NOTIFICATION_BRUSH) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !android.provider.Settings.canDrawOverlays(this)
            ) {
                android.widget.Toast.makeText(
                    this,
                    "Permita a sobreposição para usar o pincel.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
                stopSelf()
                return START_NOT_STICKY
            }
            startForegroundCompat(buildNotification())
            showDrawingLayer()
        } else if (intent?.action == ACTION_NOTIFICATION_PAUSE) {
            startForegroundCompat(buildNotification())
            sendRecordingAction(
                if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
            )
        } else if (intent?.action == ACTION_NOTIFICATION_CAMERA_LOCK) {
            toggleCameraLock()
        } else if (intent?.action == ACTION_UPDATE_OPACITY) {
            updateLogoOpacity(intent)
        } else if (intent?.action == ACTION_UPDATE_SIZE) {
            updateLogoSize(intent)
        } else {
            logoOpacity = (intent?.getDoubleExtra("logoOpacity", 0.85) ?: 0.85)
                .toFloat()
                .coerceIn(0f, 1f)
            logoSizeDp = (intent?.getIntExtra("sizeDp", 56) ?: 56).coerceIn(32, 96)
            startForegroundCompat(buildNotification())
            showControls()
        }
        return START_NOT_STICKY
    }

    private fun updateLogoOpacity(intent: Intent?) {
        logoOpacity = (intent?.getDoubleExtra("logoOpacity", logoOpacity.toDouble())
            ?: logoOpacity.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        updatePanelOpacity()
    }

    private fun updateLogoSize(intent: Intent?) {
        logoSizeDp = (intent?.getIntExtra("sizeDp", logoSizeDp) ?: logoSizeDp)
            .coerceIn(32, 96)
        controlsView?.let {
            windowManager.removeView(it)
            controlsView = null
            actionButtons = null
            showControls()
        }
    }

    private fun showControls() {
        if (controlsView != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(0xE620252D.toInt(), dp(32).toFloat())
        }
        val logo = ImageView(this).apply {
            contentDescription = "Abrir controles flutuantes"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(0xFFB81C2B.toInt(), dp(logoSizeDp / 2).toFloat())
            clipToOutline = true
            alpha = 1f
            tag = "bubble_logo"
            setImageDrawable(packageManager.getApplicationIcon(packageName))
            setOnTouchListener(::handleBubbleTouch)
            foreground = roundedOutline()
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        pauseButton = createIconButton(
            if (paused) R.drawable.ic_lucide_play else R.drawable.ic_lucide_pause,
            "Pausar ou retomar gravação",
        ) {
            sendRecordingAction(
                if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
            )
        }.also {
            it.tag = "pause"
            buttons.addView(it, buttonLayoutParams())
        }
        startButton = createIconButton(R.drawable.ic_lucide_play, "Iniciar gravação") {
            startCaptureFromFloating()
        }.also { buttons.addView(it, buttonLayoutParams()) }
        drawButton = createIconButton(
            R.drawable.ic_lucide_brush,
            "Ativar pincel para desenhar na tela",
        ) {
            showDrawingLayer()
        }.also { buttons.addView(it, buttonLayoutParams()) }
        buttons.addView(
            createIconButton(R.drawable.ic_lucide_settings, "Configurar pincel e opacidade") {
                showSettingsPanel()
            },
            buttonLayoutParams(),
        )
        cameraLockButton = createIconButton(
            cameraLockIcon(),
            "Travar ou destravar posição da facecam",
        ) {
            toggleCameraLock()
        }.also { buttons.addView(it, buttonLayoutParams()) }
        stopButton = createIconButton(R.drawable.ic_lucide_square, "Parar gravação") {
            sendRecordingAction(RecordingService.ACTION_STOP)
        }.also { buttons.addView(it, buttonLayoutParams()) }
        buttons.addView(
            createIconButton(R.drawable.ic_lucide_close, "Fechar controles flutuantes") {
                startService(Intent(this, FloatingControlsService::class.java).setAction(ACTION_DISMISS))
            },
            buttonLayoutParams(),
        )

        actionButtons = buttons
        panel.addView(buttons)
        panel.addView(logo, LinearLayout.LayoutParams(dp(logoSizeDp), dp(logoSizeDp)))
        controlsView = panel
        overlayParams = overlayLayoutParams()
        updatePanelOpacity()
        windowManager.addView(panel, overlayParams)
        refreshControls()
    }

    private fun startCaptureFromFloating() {
        val preferences = getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(displayMetrics)
        val rotation = display.rotation
        val isLandscape = when (resources.configuration.orientation) {
            android.content.res.Configuration.ORIENTATION_LANDSCAPE -> true
            android.content.res.Configuration.ORIENTATION_PORTRAIT -> false
            else -> rotation == android.view.Surface.ROTATION_90 ||
                rotation == android.view.Surface.ROTATION_270
        }
        var sourceWidth = displayMetrics.widthPixels
        var sourceHeight = displayMetrics.heightPixels
        if (isLandscape && sourceWidth < sourceHeight ||
            !isLandscape && sourceWidth > sourceHeight
        ) {
            val originalWidth = sourceWidth
            sourceWidth = sourceHeight
            sourceHeight = originalWidth
        }
        val intent = Intent(this, CaptureConsentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", "B13_FALCON_${System.currentTimeMillis()}")
            putExtra("resolution", preferences.getString("resolution", "1080p"))
            putExtra("fps", preferences.getInt("fps", 30))
            putExtra("bitrateKbps", preferences.getInt("bitrateKbps", 16000))
            putExtra("audioMode", preferences.getString("audioMode", "both"))
            putExtra("codec", preferences.getString("codec", "h264"))
            putExtra("orientation", preferences.getString("orientation", "auto"))
            putExtra(
                "cameraConfigJson",
                CameraOverlayConfig.loadFromPreferences(preferences),
            )
            putExtra("sourceWidth", sourceWidth)
            putExtra("sourceHeight", sourceHeight)
        }
        try {
            startActivity(intent)
        } catch (error: Exception) {
            android.widget.Toast.makeText(
                this,
                error.message ?: "Não foi possível abrir a autorização de captura.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                bubbleDragged = false
                view.foreground = roundedOutline(pressed = true)
                updatePanelOpacity(touching = true)
                view.tag = floatArrayOf(event.rawX, event.rawY, params.x.toFloat(), params.y.toFloat())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val start = view.tag as? FloatArray ?: return true
                val deltaX = event.rawX - start[0]
                val deltaY = event.rawY - start[1]
                if (kotlin.math.abs(deltaX) > dp(4) || kotlin.math.abs(deltaY) > dp(4)) {
                    bubbleDragged = true
                    params.x = (start[2] - deltaX).toInt().coerceAtLeast(0)
                    params.y = (start[3] + deltaY).toInt()
                    controlsView?.let { windowManager.updateViewLayout(it, params) }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!bubbleDragged && event.action == MotionEvent.ACTION_UP) {
                    expanded = !expanded
                    actionButtons?.visibility = if (expanded) View.VISIBLE else View.GONE
                    refreshControls()
                    view.contentDescription =
                        if (expanded) "Fechar controles de gravação" else "Abrir controles de gravação"
                }
                view.foreground = roundedOutline()
                updatePanelOpacity()
                return true
            }
        }
        return false
    }

    private fun showDrawingLayer() {
        if (drawingView != null) return
        settingsView?.let {
            windowManager.removeView(it)
            settingsView = null
        }
        controlsView?.let {
            windowManager.removeView(it)
            controlsView = null
            actionButtons = null
        }
        val view = DrawingOverlayView(this) {
            drawingView?.let(windowManager::removeView)
            drawingView = null
            showControls()
        }
        drawingView = view
        windowManager.addView(view, drawingLayoutParams())
    }

    private fun showSettingsPanel() {
        settingsView?.let(windowManager::removeView)
        settingsView = null
        controlsView?.let {
            windowManager.removeView(it)
            controlsView = null
            actionButtons = null
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = roundedBackground(0xF020252D.toInt(), dp(20).toFloat())
        }
        panel.addView(
            TextView(this).apply {
                text = "Configurações flutuantes"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            },
        )

        val logoOpacityLabel = addSlider(
            panel,
            "Opacidade da bolha",
            max = 100,
            progress = (logoOpacity * 100).roundToInt().coerceIn(0, 100),
            valueText = { "$it%" },
        ) { progress ->
            logoOpacity = progress / 100f
            controlsView?.findViewWithTag<ImageView>("bubble_logo")?.alpha = logoOpacity
        }
        val brushOpacityLabel = addSlider(
            panel,
            "Opacidade do pincel",
            max = 100,
            progress = (brushOpacity * 100).toInt(),
            valueText = { "$it%" },
        ) { progress ->
            brushOpacity = progress / 100f
            drawingView?.invalidate()
        }
        val brushWidthLabel = addSlider(
            panel,
            "Espessura do pincel",
            max = 18,
            progress = (brushWidthDp - 2f).toInt().coerceIn(0, 18),
            valueText = { "${it + 2} dp" },
        ) { progress ->
            brushWidthDp = (progress + 2).toFloat()
            drawingView?.invalidate()
        }
        panel.addView(
            TextView(this).apply {
                text = "Cor do pincel"
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, dp(8), 0, dp(4))
            },
        )
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        populateBrushColors(colorRow)
        panel.addView(colorRow)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(
            createButton("Voltar", "Voltar aos controles flutuantes") {
                settingsView?.let(windowManager::removeView)
                settingsView = null
                showControls()
            },
            LinearLayout.LayoutParams(dp(92), dp(46)),
        )
        actions.addView(
            createIconButton(
                R.drawable.ic_lucide_brush,
                "Ativar pincel com estas configurações",
            ) {
                showDrawingLayer()
            },
            buttonLayoutParams(),
        )
        panel.addView(actions)

        logoOpacityLabel.text = "Opacidade da bolha: ${((logoOpacity * 100).roundToInt())}%"
        brushOpacityLabel.text = "Opacidade do pincel: ${((brushOpacity * 100).roundToInt())}%"
        brushWidthLabel.text = "Espessura do pincel: ${brushWidthDp.roundToInt()} dp"
        settingsView = panel
        windowManager.addView(panel, settingsLayoutParams())
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        max: Int,
        progress: Int,
        valueText: (Int) -> String,
        onChanged: (Int) -> Unit,
    ): TextView {
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        }
        val seekBar = SeekBar(this).apply {
            this.max = max
            this.progress = progress.coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                    label.text = "$title: ${valueText(value)}"
                    onChanged(value)
                    controlsView?.findViewWithTag<ImageView>("bubble_logo")?.alpha = logoOpacity
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        label.text = "$title: ${valueText(progress)}"
        parent.addView(label)
        parent.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        return label
    }

    private fun populateBrushColors(row: LinearLayout) {
        row.removeAllViews()
        brushColors.forEach { color ->
            val swatch = TextView(this).apply {
                text = "●"
                textSize = 25f
                gravity = Gravity.CENTER
                setTextColor(color)
                contentDescription = "Selecionar cor do pincel"
                background = roundedBackground(
                    if (brushColor == color) 0xFF596273.toInt() else Color.TRANSPARENT,
                    dp(18).toFloat(),
                )
                setOnClickListener {
                    drawingView?.updateSelectedBrushColor(color)
                    brushColor = color
                    drawingView?.invalidate()
                    populateBrushColors(row)
                }
            }
            row.addView(
                swatch,
                LinearLayout.LayoutParams(0, dp(42), 1f),
            )
        }
    }

    private fun refreshControls() {
        startButton?.visibility = if (recording) View.GONE else View.VISIBLE
        pauseButton?.let {
            it.visibility = if (recording) View.VISIBLE else View.GONE
            it.setImageResource(
                if (paused) R.drawable.ic_lucide_play else R.drawable.ic_lucide_pause,
            )
            it.contentDescription = if (paused) "Retomar gravação" else "Pausar gravação"
        }
        drawButton?.visibility = if (recording) View.VISIBLE else View.GONE
        stopButton?.visibility = if (recording) View.VISIBLE else View.GONE
        cameraLockButton?.apply {
            setImageResource(cameraLockIcon())
            contentDescription = if (cameraIsLocked()) {
                "Destravar posição da facecam"
            } else {
                "Travar posição da facecam"
            }
        }
        actionButtons?.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun cameraOverlayConfig(): CameraOverlayConfig =
        CameraOverlayConfig.fromJson(
            CameraOverlayConfig.loadFromPreferences(
                getSharedPreferences("b13_falcon_recording", MODE_PRIVATE),
            ),
        )

    private fun cameraIsLocked(): Boolean = cameraOverlayConfig().locked

    private fun cameraLockIcon(): Int = if (cameraIsLocked()) {
        android.R.drawable.ic_lock_idle_lock
    } else {
        android.R.drawable.ic_lock_lock
    }

    private fun toggleCameraLock() {
        val config = cameraOverlayConfig()
        if (!config.enabled || config.mode != "overlay") {
            android.widget.Toast.makeText(
                this,
                "Ative a câmera flutuante antes de travar sua posição.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val updatedConfig = config.copy(locked = !config.locked)
        val json = updatedConfig.toJson()
        getSharedPreferences("b13_falcon_recording", MODE_PRIVATE)
            .edit()
            .putString("cameraConfigJson", json)
            .apply()
        cameraLockButton?.apply {
            setImageResource(if (updatedConfig.locked) {
                android.R.drawable.ic_lock_idle_lock
            } else {
                android.R.drawable.ic_lock_lock
            })
            contentDescription = if (updatedConfig.locked) {
                "Destravar posição da facecam"
            } else {
                "Travar posição da facecam"
            }
        }
        val intent = Intent(this, CameraOverlayService::class.java)
            .setAction(CameraOverlayService.ACTION_UPDATE)
            .putExtra("cameraConfigJson", json)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun updatePanelOpacity(touching: Boolean = false) {
        controlsView?.alpha = when {
            expanded -> 1f
            touching && logoOpacity == 0f -> 0.18f
            else -> logoOpacity
        }
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    private fun sendRecordingAction(action: String) {
        startService(Intent(this, RecordingService::class.java).setAction(action))
    }

    private fun createButton(
        label: String,
        description: String,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        contentDescription = description
        textSize = (logoSizeDp * 0.44f).coerceIn(16f, 42f)
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = roundedBackground(
            0xFFB81C2B.toInt(),
            dp(logoSizeDp / 2).toFloat(),
        )
        setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    button.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
        setOnClickListener { onClick() }
    }

    private fun createIconButton(
        icon: Int,
        description: String,
        onClick: () -> Unit,
    ): ImageView = ImageView(this).apply {
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(logoSizeDp / 4), dp(logoSizeDp / 4), dp(logoSizeDp / 4), dp(logoSizeDp / 4))
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        background = roundedBackground(
            0xFFB81C2B.toInt(),
            dp(logoSizeDp / 2).toFloat(),
        )
        setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    button.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false
        }
        setOnClickListener { onClick() }
    }

    private fun buttonLayoutParams() =
        LinearLayout.LayoutParams(dp(logoSizeDp), dp(logoSizeDp)).apply {
        marginStart = dp(4)
        marginEnd = dp(4)
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        x = dp(4)
    }

    private fun drawingLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private fun settingsLayoutParams() = WindowManager.LayoutParams(
        minOf(resources.displayMetrics.widthPixels - dp(32), dp(420)),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.CENTER
        x = dp(16)
        y = 0
    }

    private fun overlayWindowType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun roundedOutline(pressed: Boolean = false) = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        setStroke(dp(if (pressed) 2 else 1), if (pressed) 0x99FFFFFF.toInt() else 0x44FFFFFF)
        cornerRadius = dp(logoSizeDp / 2).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Controles flutuantes",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("B13 FALCON")
            .setContentText(
                if (recording) {
                    if (paused) "Gravação pausada" else "Gravação em andamento"
                } else {
                    "Pronto para iniciar uma captura"
                },
            )
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
        val cameraConfig = cameraOverlayConfig()
        if (cameraConfig.enabled && cameraConfig.mode == "overlay") {
            val cameraLockIntent = PendingIntent.getService(
                this,
                6,
                Intent(this, FloatingControlsService::class.java).apply {
                    action = ACTION_NOTIFICATION_CAMERA_LOCK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                if (cameraConfig.locked) android.R.drawable.ic_lock_idle_lock
                else android.R.drawable.ic_lock_lock,
                if (cameraConfig.locked) "Destravar facecam" else "Travar facecam",
                cameraLockIntent,
            )
        }
        if (recording) {
            val pauseIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, FloatingControlsService::class.java).apply {
                    action = ACTION_NOTIFICATION_PAUSE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val brushIntent = PendingIntent.getService(
                this,
                4,
                Intent(this, FloatingControlsService::class.java).apply {
                    action = ACTION_NOTIFICATION_BRUSH
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder
                .addAction(
                    if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    if (paused) "Retomar" else "Pausar",
                    pauseIntent,
                )
                .addAction(R.drawable.ic_lucide_brush, "Pincel", brushIntent)
        } else {
            val startIntent = PendingIntent.getActivity(
                this,
                5,
                Intent(this, CaptureConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_lucide_play, "Iniciar", startIntent)
        }
        return builder.build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        controlsView?.let(windowManager::removeView)
        settingsView?.let(windowManager::removeView)
        drawingView?.let(windowManager::removeView)
        controlsView = null
        settingsView = null
        actionButtons = null
        startButton = null
        pauseButton = null
        drawButton = null
        stopButton = null
        cameraLockButton = null
        drawingView = null
        unregisterReceiver(recordingReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class DrawingOverlayView(
        context: Context,
        private val onDone: () -> Unit,
    ) : View(context) {
        private val strokes = mutableListOf<Stroke>()
        private var activeStroke: Stroke? = null
        private var selectedTool = DrawingTool.FREEHAND
        private var colorPickerVisible = false
        private var selectedHue = 60f
        private var selectedSaturation = 1f
        private var selectedBrightness = 1f
        private var activeColorControl = -1
        private var activeSlider = -1
        private val toolbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE620252D.toInt()
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(11).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hueColors = intArrayOf(
            Color.RED,
            0xFFFF00FF.toInt(),
            Color.BLUE,
            Color.CYAN,
            Color.GREEN,
            Color.YELLOW,
            Color.RED,
        )
        private val pickerLeft get() =
            ((width - minOf(dp(280), width - dp(36))) / 2f)
        private val pickerRight get() = pickerLeft + minOf(dp(280), width - dp(36))
        private val pickerTop get() = dp(132).toFloat()
        private val pickerBottom get() = pickerTop + dp(96)
        private val hueTop get() = pickerBottom + dp(10)
        private val hueBottom get() = hueTop + dp(12)
        private val opacityTop get() = hueBottom + dp(12)
        private val opacityBottom get() = opacityTop + dp(12)

        init {
            contentDescription =
                "Ferramentas: cor, pincel livre, caixa, círculo, desfazer e limpar."
            updateSelectedBrushColor(brushColor)
        }

        fun updateSelectedBrushColor(color: Int) {
            brushColor = color
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            selectedHue = hsv[0]
            selectedSaturation = hsv[1]
            selectedBrightness = hsv[2]
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            strokes.forEach { drawStroke(canvas, it) }
            activeStroke?.let { drawStroke(canvas, it) }
            canvas.drawRoundRect(
                dp(12).toFloat(),
                dp(18).toFloat(),
                width - dp(12).toFloat(),
                dp(112).toFloat(),
                dp(18).toFloat(),
                dp(18).toFloat(),
                toolbarPaint,
            )
            drawToolbar(canvas)
            if (colorPickerVisible) drawColorPicker(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            if (colorPickerVisible && y >= dp(118) && y <= opacityBottom + dp(10)) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        activeColorControl = colorControlAt(y)
                        updateColorControl(x, y)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activeColorControl >= 0) updateColorControl(x, y)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        activeColorControl = -1
                }
                return true
            }
            if (y < dp(112)) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (y < dp(68)) {
                            handleToolTap(x)
                        } else {
                            activeSlider = if (x < width / 2f) 0 else 1
                            updateSlider(x)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (activeSlider >= 0) updateSlider(x)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeSlider = -1
                }
                return true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    activeStroke = Stroke(
                        path = Path().apply { moveTo(x, y) },
                        paint = brushPaint(),
                        tool = selectedTool,
                        startX = x,
                        startY = y,
                        endX = x,
                        endY = y,
                    )
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    activeStroke?.let { stroke ->
                        stroke.endX = x
                        stroke.endY = y
                        if (stroke.tool == DrawingTool.FREEHAND) stroke.path.lineTo(x, y)
                    }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeStroke?.let(strokes::add)
                    activeStroke = null
                    invalidate()
                }
            }
            return true
        }

        private fun drawToolbar(canvas: Canvas) {
            val cellWidth = width / 7f
            val iconResources = intArrayOf(
                R.drawable.ic_lucide_palette,
                R.drawable.ic_lucide_pencil,
                R.drawable.ic_lucide_square,
                R.drawable.ic_lucide_circle,
                R.drawable.ic_lucide_undo,
                R.drawable.ic_lucide_trash,
                R.drawable.ic_lucide_close,
            )
            iconResources.forEachIndexed { index, iconResource ->
                val centerX = cellWidth * (index + 0.5f)
                val selected = when (index) {
                    0 -> colorPickerVisible
                    1 -> selectedTool == DrawingTool.FREEHAND
                    2 -> selectedTool == DrawingTool.RECTANGLE
                    3 -> selectedTool == DrawingTool.CIRCLE
                    else -> false
                }
                if (selected) {
                    controlPaint.color = 0x663A9DFF
                    canvas.drawRoundRect(
                        centerX - cellWidth * 0.43f,
                        dp(22).toFloat(),
                        centerX + cellWidth * 0.43f,
                        dp(62).toFloat(),
                        dp(10).toFloat(),
                        dp(10).toFloat(),
                        controlPaint,
                    )
                }
                drawToolbarIcon(
                    canvas,
                    iconResource,
                    centerX,
                    dp(42).toFloat(),
                    if (index == 0 && colorPickerVisible) brushColor else Color.WHITE,
                )
            }
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.textSize = dp(10).toFloat()
            canvas.drawText("Espessura", dp(20).toFloat(), dp(98).toFloat(), labelPaint)
            canvas.drawText("Opacidade", (width / 2f + dp(10)).toFloat(), dp(98).toFloat(), labelPaint)
            drawSlider(
                canvas,
                dp(86).toFloat(),
                (width / 2f - dp(12)).toFloat(),
                dp(94).toFloat(),
                (brushWidthDp - 2f) / 18f,
                brushColor,
            )
            drawSlider(
                canvas,
                (width / 2f + dp(74)).toFloat(),
                (width - dp(20)).toFloat(),
                dp(94).toFloat(),
                brushOpacity,
                brushColor,
            )
            labelPaint.textAlign = Paint.Align.CENTER
        }

        private fun drawToolbarIcon(
            canvas: Canvas,
            iconResource: Int,
            centerX: Float,
            centerY: Float,
            tint: Int,
        ) {
            val icon = getDrawable(iconResource)?.mutate() ?: return
            icon.setTint(tint)
            val size = dp(20).toInt()
            icon.setBounds(
                (centerX - size / 2).toInt(),
                (centerY - size / 2).toInt(),
                (centerX + size / 2).toInt(),
                (centerY + size / 2).toInt(),
            )
            val saveCount = canvas.save()
            icon.draw(canvas)
            canvas.restoreToCount(saveCount)
        }

        private fun drawColorPicker(canvas: Canvas) {
            canvas.drawRoundRect(
                pickerLeft - dp(12).toFloat(),
                dp(120).toFloat(),
                pickerRight + dp(12).toFloat(),
                opacityBottom + dp(12),
                dp(16).toFloat(),
                dp(16).toFloat(),
                toolbarPaint,
            )
            val pickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    pickerLeft,
                    pickerTop,
                    pickerRight,
                    pickerTop,
                    Color.WHITE,
                    Color.HSVToColor(floatArrayOf(selectedHue, 1f, 1f)),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(
                pickerLeft,
                pickerTop,
                pickerRight,
                pickerBottom,
                dp(8).toFloat(),
                dp(8).toFloat(),
                pickerPaint,
            )
            pickerPaint.shader = LinearGradient(
                0f,
                pickerTop,
                0f,
                pickerBottom,
                0x00000000,
                Color.BLACK,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(
                pickerLeft,
                pickerTop,
                pickerRight,
                pickerBottom,
                dp(8).toFloat(),
                dp(8).toFloat(),
                pickerPaint,
            )
            pickerPaint.shader = null
            pickerPaint.style = Paint.Style.STROKE
            pickerPaint.strokeWidth = dp(3).toFloat()
            pickerPaint.color = Color.WHITE
            val selectedX = pickerLeft + selectedSaturation * (pickerRight - pickerLeft)
            val selectedY = pickerTop + (1f - selectedBrightness) * (pickerBottom - pickerTop)
            canvas.drawCircle(selectedX, selectedY, dp(8).toFloat(), pickerPaint)

            pickerPaint.style = Paint.Style.FILL
            pickerPaint.shader = LinearGradient(
                pickerLeft,
                hueTop,
                pickerRight,
                hueTop,
                hueColors,
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(
                pickerLeft,
                hueTop,
                pickerRight,
                hueBottom,
                dp(8).toFloat(),
                dp(8).toFloat(),
                pickerPaint,
            )
            pickerPaint.shader = null
            pickerPaint.style = Paint.Style.STROKE
            pickerPaint.strokeWidth = dp(3).toFloat()
            pickerPaint.color = Color.WHITE
            val hueX = pickerLeft + selectedHue / 360f * (pickerRight - pickerLeft)
            canvas.drawCircle(hueX, (hueTop + hueBottom) / 2, dp(10).toFloat(), pickerPaint)

            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.textSize = dp(10).toFloat()
            canvas.drawText("Opacidade", pickerLeft, opacityTop - dp(4), labelPaint)
            drawSlider(
                canvas,
                pickerLeft + dp(78),
                pickerRight,
                (opacityTop + opacityBottom) / 2,
                brushOpacity,
                brushColor,
            )
            labelPaint.textAlign = Paint.Align.CENTER
        }

        private fun drawSlider(
            canvas: Canvas,
            left: Float,
            right: Float,
            y: Float,
            value: Float,
            color: Int,
        ) {
            controlPaint.color = 0xFF68717D.toInt()
            controlPaint.strokeWidth = dp(3).toFloat()
            controlPaint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(left, y, right, y, controlPaint)
            val thumbX = left + (right - left) * value.coerceIn(0f, 1f)
            controlPaint.color = color
            canvas.drawLine(left, y, thumbX, y, controlPaint)
            canvas.drawCircle(thumbX, y, dp(6).toFloat(), controlPaint)
        }

        private fun drawStroke(canvas: Canvas, stroke: Stroke) {
            when (stroke.tool) {
                DrawingTool.FREEHAND -> canvas.drawPath(stroke.path, stroke.paint)
                DrawingTool.RECTANGLE -> canvas.drawRect(
                    minOf(stroke.startX, stroke.endX),
                    minOf(stroke.startY, stroke.endY),
                    maxOf(stroke.startX, stroke.endX),
                    maxOf(stroke.startY, stroke.endY),
                    stroke.paint,
                )
                DrawingTool.CIRCLE -> canvas.drawOval(
                    RectF(
                        minOf(stroke.startX, stroke.endX),
                        minOf(stroke.startY, stroke.endY),
                        maxOf(stroke.startX, stroke.endX),
                        maxOf(stroke.startY, stroke.endY),
                    ),
                    stroke.paint,
                )
            }
        }

        private fun handleToolTap(x: Float) {
            when ((x / (width / 7f)).toInt().coerceIn(0, 6)) {
                0 -> colorPickerVisible = !colorPickerVisible
                1 -> selectedTool = DrawingTool.FREEHAND
                2 -> selectedTool = DrawingTool.RECTANGLE
                3 -> selectedTool = DrawingTool.CIRCLE
                4 -> if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                5 -> {
                    strokes.clear()
                    activeStroke = null
                }
                6 -> onDone()
            }
            invalidate()
        }

        private fun colorControlAt(y: Float): Int = when {
            y <= pickerBottom -> 0
            y <= hueBottom -> 1
            else -> 2
        }

        private fun updateColorControl(x: Float, y: Float) {
            when (activeColorControl) {
                0 -> {
                    selectedSaturation = ((x - pickerLeft) / (pickerRight - pickerLeft))
                        .coerceIn(0f, 1f)
                    selectedBrightness = (1f - (y - pickerTop) / (pickerBottom - pickerTop))
                        .coerceIn(0f, 1f)
                    brushColor = Color.HSVToColor(
                        floatArrayOf(selectedHue, selectedSaturation, selectedBrightness),
                    )
                }
                1 -> {
                    selectedHue = ((x - pickerLeft) / (pickerRight - pickerLeft) * 360f)
                        .coerceIn(0f, 360f)
                    brushColor = Color.HSVToColor(
                        floatArrayOf(selectedHue, selectedSaturation, selectedBrightness),
                    )
                }
                2 -> brushOpacity =
                    ((x - pickerLeft) / (pickerRight - pickerLeft)).coerceIn(0.05f, 1f)
            }
            invalidate()
        }

        private fun updateSlider(x: Float) {
            if (activeSlider == 0) {
                val left = dp(86).toFloat()
                val right = (width / 2f - dp(12)).toFloat()
                brushWidthDp = 2f +
                    ((x - left) / (right - left)).coerceIn(0f, 1f) * 18f
            } else {
                val left = (width / 2f + dp(74)).toFloat()
                val right = (width - dp(20)).toFloat()
                brushOpacity =
                    ((x - left) / (right - left)).coerceIn(0.05f, 1f)
            }
            invalidate()
        }

        private fun brushPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brushColor
            alpha = (brushOpacity * 255).roundToInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = dp(brushWidthDp.roundToInt()).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private fun dp(value: Float): Float = value * resources.displayMetrics.density
    }
}
