package com.b13.falcon

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

class FloatingControlsService : Service() {
    private enum class DrawingTool { FREEHAND, RECTANGLE, CIRCLE }

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
                    val hsv = FloatArray(3)
                    Color.colorToHSV(color, hsv)
                    selectedHue = hsv[0]
                    selectedSaturation = hsv[1]
                    selectedBrightness = hsv[2]
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
        private val pickerLeft get() = dp(18).toFloat()
        private val pickerRight get() = width - dp(18).toFloat()
        private val pickerTop get() = dp(132).toFloat()
        private val pickerBottom get() = pickerTop + dp(156)
        private val hueTop get() = pickerBottom + dp(14)
        private val hueBottom get() = hueTop + dp(18)
        private val opacityTop get() = hueBottom + dp(18)
        private val opacityBottom get() = opacityTop + dp(18)

        init {
            contentDescription =
                "Ferramentas: cor, pincel livre, caixa, círculo, desfazer e limpar."
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
            val labels = listOf("Cor", "Livre", "Caixa", "Círculo", "Voltar", "Limpar", "Fechar")
            labels.forEachIndexed { index, label ->
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
                if (index == 0) {
                    controlPaint.color = brushColor
                    controlPaint.alpha = (brushOpacity * 255).roundToInt()
                    canvas.drawCircle(centerX, dp(34).toFloat(), dp(8).toFloat(), controlPaint)
                    controlPaint.alpha = 255
                }
                canvas.drawText(label, centerX, dp(56).toFloat(), labelPaint)
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

        private fun drawColorPicker(canvas: Canvas) {
            canvas.drawRoundRect(
                dp(12).toFloat(),
                dp(120).toFloat(),
                width - dp(12).toFloat(),
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
