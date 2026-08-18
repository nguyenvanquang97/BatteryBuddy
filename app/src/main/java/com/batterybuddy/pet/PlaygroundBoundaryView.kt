package com.batterybuddy.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PlaygroundBoundaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minX: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var maxX: Int = 320
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#404A90E2") // Semi-transparent blue fill
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4A90E2") // Solid blue line
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF00E676") // Bright green indicator lines at bounds
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val totalHeight = height.toFloat()
        val totalWidth = width.toFloat()

        // Correct Screen Coordinates (Origin X = 0 at Far Left Edge of Screen)
        val leftX = minX.toFloat().coerceIn(0f, totalWidth)
        val rightX = maxX.toFloat().coerceIn(leftX + 20f, totalWidth)

        if (rightX > leftX) {
            rect.set(leftX, 0f, rightX, totalHeight)
            val cornerRadius = 8 * density
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

            // Draw left & right boundary markers
            canvas.drawLine(leftX, 0f, leftX, totalHeight, linePaint)
            canvas.drawLine(rightX, 0f, rightX, totalHeight, linePaint)

            // Label in center of playground
            val textY = totalHeight / 2f + 8f
            val centerX = (leftX + rightX) / 2f
            canvas.drawText("🐱 Playground (${minX}px - ${maxX}px)", centerX, textY, labelPaint)
        }
    }
}
