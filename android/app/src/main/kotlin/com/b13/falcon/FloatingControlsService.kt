package com.b13.falcon

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

class FloatingControlsService : Service() {
    companion object {
        const val ACTION_DISMISS = "com.b13.falcon.overlay.DISMISS"
        const val ACTION_UPDATE_OPACITY = "com.b13.falcon.overlay.UPDATE_OPACITY"
    }

    private lateinit var windowManager: WindowManager
    private var controlsView: View? = null
    private var settingsView: View? = null
    private var drawingView: DrawingOverlayView? = null
    private var actionButtons: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private var bubbleDragged = false
    private var paused = false
    private var logoOpacity = 0.85f
    private var brushColor = Color.YELLOW
    private var brushOpacity = 1f
    private var brushWidthDp = 5f
    private data class Stroke(val path: Path, val paint: Paint)
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
                    paused = true
                    refreshPauseButton()
                }
                "recording" -> {
                    paused = false
                    refreshPauseButton()
                }
                "stopped", "error" -> stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(RecordingService.STATUS_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(recordingReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            stopSelf()
        } else if (intent?.action == ACTION_UPDATE_OPACITY) {
            updateLogoOpacity(intent)
        } else {
            logoOpacity = (intent?.getDoubleExtra("logoOpacity", 0.85) ?: 0.85)
                .toFloat()
                .coerceIn(0.2f, 1f)
            showControls()
        }
        return START_NOT_STICKY
    }

    private fun updateLogoOpacity(intent: Intent?) {
        logoOpacity = (intent?.getDoubleExtra("logoOpacity", logoOpacity.toDouble())
            ?: logoOpacity.toDouble())
            .toFloat()
            .coerceIn(0.2f, 1f)
        controlsView?.findViewWithTag<ImageView>("bubble_logo")?.alpha = logoOpacity
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
            contentDescription = "Abrir controles de gravação"
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(0xFFB81C2B.toInt(), dp(28).toFloat())
            clipToOutline = true
            alpha = logoOpacity
            tag = "bubble_logo"
            setImageDrawable(packageManager.getApplicationIcon(packageName))
            setOnTouchListener(::handleBubbleTouch)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = if (expanded) View.VISIBLE else View.GONE
        }
        val pauseButton = createButton(if (paused) "▶" else "Ⅱ", "Pausar ou retomar gravação") {
            sendRecordingAction(
                if (paused) RecordingService.ACTION_RESUME else RecordingService.ACTION_PAUSE,
            )
        }
        pauseButton.tag = "pause"
        buttons.addView(pauseButton, buttonLayoutParams())
        buttons.addView(
            createButton("✎", "Ativar pincel para desenhar na tela") {
                showDrawingLayer()
            },
            buttonLayoutParams(),
        )
        buttons.addView(
            createButton("⚙", "Configurar pincel e opacidade") {
                showSettingsPanel()
            },
            buttonLayoutParams(),
        )
        buttons.addView(
            createButton("×", "Parar gravação") {
                sendRecordingAction(RecordingService.ACTION_STOP)
            },
            buttonLayoutParams(),
        )

        actionButtons = buttons
        panel.addView(buttons)
        panel.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        controlsView = panel
        overlayParams = overlayLayoutParams()
        windowManager.addView(panel, overlayParams)
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                bubbleDragged = false
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
                    view.contentDescription =
                        if (expanded) "Fechar controles de gravação" else "Abrir controles de gravação"
                }
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
            max = 80,
            progress = ((logoOpacity - 0.2f) * 100).toInt().coerceIn(0, 80),
            valueText = { "${((it + 20))}%" },
        ) { progress ->
            logoOpacity = (progress + 20) / 100f
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
            createButton("✎", "Ativar pincel com estas configurações") {
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

    private fun refreshPauseButton() {
        val panel = controlsView as? LinearLayout ?: return
        val button = panel.findViewWithTag<TextView>("pause") ?: return
        button.text = if (paused) "▶" else "Ⅱ"
        button.contentDescription = if (paused) "Retomar gravação" else "Pausar gravação"
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
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = roundedBackground(0xFFB81C2B.toInt(), dp(22).toFloat())
        setOnClickListener { onClick() }
    }

    private fun buttonLayoutParams() = LinearLayout.LayoutParams(dp(46), dp(46)).apply {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        controlsView?.let(windowManager::removeView)
        settingsView?.let(windowManager::removeView)
        drawingView?.let(windowManager::removeView)
        controlsView = null
        settingsView = null
        actionButtons = null
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
        private var activePath: Path? = null
        private val toolbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE620252D.toInt()
        }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(16).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val doneBounds get() = width - dp(112)
        private val clearBounds get() = width - dp(194)

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            contentDescription = "Tela de desenho. Toque em concluir para voltar aos controles."
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            strokes.forEach { canvas.drawPath(it.path, it.paint) }
            activePath?.let { path ->
                canvas.drawPath(path, brushPaint())
            }
            canvas.drawRoundRect(
                dp(12).toFloat(),
                dp(18).toFloat(),
                width - dp(12).toFloat(),
                dp(68).toFloat(),
                dp(18).toFloat(),
                dp(18).toFloat(),
                toolbarPaint,
            )
            canvas.drawText("Pincel ativo", dp(26).toFloat(), dp(49).toFloat(), labelPaint)
            canvas.drawText("Limpar", clearBounds.toFloat(), dp(49).toFloat(), labelPaint)
            canvas.drawText("Concluir", doneBounds.toFloat(), dp(49).toFloat(), labelPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.x
            val y = event.y
            if (event.action == MotionEvent.ACTION_DOWN && y < dp(76).toFloat()) {
                if (x >= doneBounds - dp(8)) {
                    onDone()
                    return true
                }
                if (x >= clearBounds - dp(8)) {
                    strokes.clear()
                    invalidate()
                    return true
                }
                return true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    activePath = Path().apply { moveTo(x, y) }
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    activePath?.lineTo(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePath?.let { path ->
                        strokes.add(Stroke(path, brushPaint()))
                    }
                    activePath = null
                    invalidate()
                }
            }
            return true
        }

        private fun brushPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brushColor
            alpha = (brushOpacity * 255).roundToInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = dp(brushWidthDp.roundToInt()).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }
}
