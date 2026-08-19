package com.batterybuddy.event

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.batterybuddy.R
import kotlin.math.min
import kotlin.math.sin

class EventEnvironmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var environment: EventEnvironment = EventEnvironment.DEFAULT
        set(value) {
            field = value
            visibility = if (value == EventEnvironment.DEFAULT) GONE else VISIBLE
            invalidate()
        }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawRect = RectF()
    private val background: Bitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.qixi_background)
    }
    private val magpieFrames: List<Bitmap> by lazy {
        listOf(
            R.drawable.qixi_magpie_01,
            R.drawable.qixi_magpie_02,
            R.drawable.qixi_magpie_03,
            R.drawable.qixi_magpie_04
        ).map { resourceId -> BitmapFactory.decodeResource(resources, resourceId) }
    }

    private var animationProgress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { valueAnimator ->
            animationProgress = valueAnimator.animatedValue as Float
            invalidate()
        }
    }

    init {
        visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (environment != EventEnvironment.QIXI) return

        val density = resources.displayMetrics.density
        val backgroundAspectRatio = background.width.toFloat() / background.height
        val maxBackgroundWidth = min(
            width * 0.52f,
            height * 0.88f * backgroundAspectRatio
        )
        val backgroundHeight = maxBackgroundWidth * background.height / background.width
        val left = (width - maxBackgroundWidth) / 2f
        val top = (height - backgroundHeight) / 2f
        drawRect.set(left, top, left + maxBackgroundWidth, top + backgroundHeight)
        bitmapPaint.alpha = 225
        canvas.drawBitmap(background, null, drawRect, bitmapPaint)

        val frameIndex = (animationProgress * magpieFrames.size)
            .toInt()
            .coerceAtMost(magpieFrames.lastIndex)
        val magpie = magpieFrames[frameIndex]
        val magpieSize = (height * 0.28f).coerceAtLeast(10f * density)
        val x = -magpieSize + animationProgress * (width + magpieSize * 2f)
        val y = height * 0.10f + sin(animationProgress * 4f * Math.PI).toFloat() * 2f * density
        drawRect.set(x, y, x + magpieSize, y + magpieSize)
        bitmapPaint.alpha = 235
        canvas.drawBitmap(magpie, null, drawRect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 6_000L

        fun desiredHeightPx(context: Context): Int {
            val density = context.resources.displayMetrics.density
            val resourceId = context.resources.getIdentifier(
                "status_bar_height",
                "dimen",
                "android"
            )
            return if (resourceId > 0) {
                context.resources.getDimensionPixelSize(resourceId)
            } else {
                (24f * density).toInt()
            }
        }
    }
}
