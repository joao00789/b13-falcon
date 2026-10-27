package com.b13.falcon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.DashPathEffect
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import kotlin.math.max

internal class CameraFrameView(context: Context) : FrameLayout(context) {
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
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
