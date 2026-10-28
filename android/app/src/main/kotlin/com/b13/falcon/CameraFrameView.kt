package com.b13.falcon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.widget.FrameLayout
import kotlin.math.max

internal class CameraFrameView(context: Context) : FrameLayout(context) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val nameBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nameTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private var config = CameraOverlayConfig.fromJson("{}")

    init {
        setWillNotDraw(false)
        clipToOutline = true
    }

    fun applyConfig(config: CameraOverlayConfig) {
        this.config = config
        val inset = dp(config.borderWidthDp).toInt()
        setPadding(inset, inset, inset, inset)
        background = GradientDrawable().apply {
            setColor(Color.argb(188, 8, 11, 18))
            when (config.shape) {
                "circle" -> shape = GradientDrawable.OVAL
                "square" -> cornerRadius = 0f
                else -> cornerRadius = dp(config.cornerRadiusDp).toFloat()
            }
        }
        clipToOutline = true
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawNameTag(canvas)
        val width = dp(config.borderWidthDp).toFloat()
        if (width <= 0f) return
        if (config.glow || config.borderStyle == "glow") drawGlow(canvas, width)

        when (config.borderStyle) {
            "dashed" -> {
                borderPaint.pathEffect = DashPathEffect(
                    floatArrayOf(dp(8).toFloat(), dp(5).toFloat()),
                    0f,
                )
                drawStroke(canvas, config.borderColor, width, width / 2f)
                borderPaint.pathEffect = null
            }
            "double" -> {
                val lineWidth = max(1f, width / 3f)
                drawStroke(canvas, config.borderColor, lineWidth, width / 4f)
                drawStroke(canvas, config.borderColor, lineWidth, width * 0.75f)
            }
            "gradient" -> {
                borderPaint.shader = SweepGradient(
                    this.width / 2f,
                    this.height / 2f,
                    intArrayOf(config.borderColor, config.gradientEnd),
                    null,
                )
                drawStroke(canvas, config.borderColor, width, width / 2f)
                borderPaint.shader = null
            }
            "glow" -> drawStroke(canvas, config.borderColor, width, width / 2f)
            else -> drawStroke(canvas, config.borderColor, width, width / 2f)
        }
    }

    private fun drawNameTag(canvas: Canvas) {
        if (!config.nameTagEnabled || config.nameTagText.isBlank()) return

        val frameWidth = width.toFloat().coerceAtLeast(1f)
        val frameHeight = height.toFloat().coerceAtLeast(1f)
        val textSize = dp(15).coerceAtMost(frameHeight * 0.13f)
        nameTextPaint.apply {
            color = config.nameTagTextColor
            this.textSize = textSize
            typeface = when (config.nameTagFont) {
                "serif" -> Typeface.create("serif", Typeface.NORMAL)
                "mono" -> Typeface.create("monospace", Typeface.NORMAL)
                "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                "sans" -> Typeface.create("sans-serif", Typeface.NORMAL)
                else -> Typeface.create("sans-serif", Typeface.BOLD)
            }
        }
        val fontMetrics = nameTextPaint.fontMetrics
        val labelHeight = fontMetrics.descent - fontMetrics.ascent + dp(8)
        val maxTextWidth = (frameWidth - dp(16)).coerceAtLeast(0f)
        val text = TextUtils.ellipsize(
            config.nameTagText,
            android.text.TextPaint(nameTextPaint),
            maxTextWidth,
            TextUtils.TruncateAt.END,
        ).toString()
        val labelWidth = (nameTextPaint.measureText(text) + dp(16))
            .coerceAtMost((frameWidth - dp(8)).coerceAtLeast(0f))
        val centerY = when (config.nameTagPosition) {
            "above" -> dp(10) + labelHeight / 2f
            "below" -> frameHeight - dp(8) - labelHeight / 2f
            else -> frameHeight * 0.78f
        }
        val labelBounds = RectF(
            (frameWidth - labelWidth) / 2f,
            centerY - labelHeight / 2f,
            (frameWidth + labelWidth) / 2f,
            centerY + labelHeight / 2f,
        )
        nameBackgroundPaint.color = config.nameTagBackgroundColor
        val radius = if (config.nameTagStyle == "pill") labelHeight / 2f else dp(4)
        canvas.drawRoundRect(labelBounds, radius, radius, nameBackgroundPaint)
        if (config.nameTagStyle == "bar") {
            canvas.drawRect(labelBounds, nameBackgroundPaint)
        }
        canvas.drawText(
            text,
            frameWidth / 2f,
            centerY - (fontMetrics.ascent + fontMetrics.descent) / 2f,
            nameTextPaint,
        )
    }

    private fun drawGlow(canvas: Canvas, width: Float) {
        for (layer in 3 downTo 1) {
            val strokeWidth = width + dp(layer * 3)
            val alpha = 18 + (4 - layer) * 18
            val color = Color.argb(
                alpha,
                Color.red(config.borderColor),
                Color.green(config.borderColor),
                Color.blue(config.borderColor),
            )
            drawStroke(canvas, color, strokeWidth, strokeWidth / 2f)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        color: Int,
        strokeWidth: Float,
        inset: Float,
    ) {
        borderPaint.color = color
        borderPaint.strokeWidth = strokeWidth
        canvas.drawPath(createShapePath(inset), borderPaint)
    }

    private fun createShapePath(inset: Float): Path {
        val bounds = RectF(
            inset,
            inset,
            (width - inset).coerceAtLeast(inset),
            (height - inset).coerceAtLeast(inset),
        )
        return Path().apply {
            when (config.shape) {
                "circle" -> addOval(bounds, Path.Direction.CW)
                "square" -> addRect(bounds, Path.Direction.CW)
                else -> {
                    val radius = (dp(config.cornerRadiusDp) - inset).coerceAtLeast(0f)
                    addRoundRect(bounds, radius, radius, Path.Direction.CW)
                }
            }
        }
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}
