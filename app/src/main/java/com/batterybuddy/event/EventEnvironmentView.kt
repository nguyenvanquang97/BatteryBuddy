package com.batterybuddy.event

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.batterybuddy.R
import com.batterybuddy.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

class EventEnvironmentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var environment: EventEnvironment = EventEnvironment.DEFAULT
        set(value) {
            field = value
            updateVisibility()
            invalidate()
        }

    var weatherCondition: WeatherCondition = WeatherCondition.CLEAR
        set(value) {
            field = value
            updateVisibility()
            invalidate()
        }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val sourceRect = Rect()
    private val drawRect = RectF()
    private val badge: Bitmap by lazy { decode(R.drawable.qixi_badge) }
    private val sparkles: Bitmap by lazy { decode(R.drawable.qixi_sparkles) }
    private val petals: Bitmap by lazy { decode(R.drawable.qixi_petals) }
    private val magpieFrames: List<Bitmap> by lazy {
        listOf(
            R.drawable.qixi_magpie_01,
            R.drawable.qixi_magpie_02,
            R.drawable.qixi_magpie_03,
            R.drawable.qixi_magpie_04
        ).map(::decode)
    }
    private val butterflyFrames: List<Bitmap> by lazy {
        listOf(
            R.drawable.butterfly_01,
            R.drawable.butterfly_02,
            R.drawable.butterfly_03,
            R.drawable.butterfly_04
        ).map(::decode)
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

        val density = resources.displayMetrics.density
        if (environment == EventEnvironment.QIXI) {
            drawQixiEnvironment(canvas, density)
        }
        drawWeather(canvas, density)
        if (isButterflyVisible) {
            drawButterfly(canvas, density)
        }
    }

    private fun drawQixiEnvironment(canvas: Canvas, density: Float) {
        val twinkle = 0.82f + sin(animationProgress * 2f * Math.PI).toFloat() * 0.18f

        bitmapPaint.alpha = (78 * twinkle).toInt()
        drawBitmapByWidth(
            canvas = canvas,
            bitmap = sparkles,
            widthPx = height * 1.35f,
            centerX = width * 0.36f,
            bottom = height * 0.82f
        )
        bitmapPaint.alpha = (58 * twinkle).toInt()
        drawBitmapByWidth(
            canvas = canvas,
            bitmap = sparkles,
            widthPx = height * 1.05f,
            centerX = width * 0.72f,
            bottom = height * 0.88f
        )

        drawQixiPetals(canvas)

        bitmapPaint.alpha = 92
        drawBitmapByHeight(
            canvas = canvas,
            bitmap = badge,
            heightPx = height * 0.36f,
            centerX = width * 0.54f,
            bottom = height * 0.78f
        )

        val frameIndex = (animationProgress * magpieFrames.size)
            .toInt()
            .coerceAtMost(magpieFrames.lastIndex)
        val magpie = magpieFrames[frameIndex]
        val magpieHeight = (height * 0.30f).coerceAtLeast(6f * density)
        val magpieWidth = magpieHeight * magpie.width / magpie.height
        val x = -magpieWidth + animationProgress * (width + magpieWidth * 2f)
        val centerY = height * 0.42f + sin(animationProgress * 4f * Math.PI).toFloat() * density
        drawRect.set(
            x,
            centerY - magpieHeight / 2f,
            x + magpieWidth,
            centerY + magpieHeight / 2f
        )
        bitmapPaint.alpha = 118
        canvas.drawBitmap(magpie, null, drawRect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun decode(resourceId: Int): Bitmap =
        BitmapFactory.decodeResource(resources, resourceId)

    private fun drawBitmapByWidth(
        canvas: Canvas,
        bitmap: Bitmap,
        widthPx: Float,
        centerX: Float,
        bottom: Float
    ) {
        val heightPx = widthPx * bitmap.height / bitmap.width
        drawRect.set(
            centerX - widthPx / 2f,
            bottom - heightPx,
            centerX + widthPx / 2f,
            bottom
        )
        canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
    }

    private fun drawBitmapByHeight(
        canvas: Canvas,
        bitmap: Bitmap,
        heightPx: Float,
        centerX: Float,
        bottom: Float
    ) {
        val widthPx = heightPx * bitmap.width / bitmap.height
        drawRect.set(
            centerX - widthPx / 2f,
            bottom - heightPx,
            centerX + widthPx / 2f,
            bottom
        )
        canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint)
    }

    private fun drawQixiPetals(canvas: Canvas) {
        val sourcePetalCount = 6
        val visiblePetalCount = 3
        val frameWidth = petals.width / sourcePetalCount
        val petalHeight = height * 0.075f
        val petalWidth = petalHeight * frameWidth / petals.height

        repeat(visiblePetalCount) { index ->
            val phase = (animationProgress * 0.48f + index * 0.31f) % 1f
            val baseX = width * PETAL_X_FRACTIONS[index]
            val driftX = sin((phase * 2f + index) * Math.PI).toFloat() * width * 0.012f
            val centerX = baseX + driftX
            val centerY = -petalHeight + phase * (height + petalHeight * 2f)

            sourceRect.set(
                index * frameWidth,
                0,
                (index + 1) * frameWidth,
                petals.height
            )
            drawRect.set(
                centerX - petalWidth / 2f,
                centerY - petalHeight / 2f,
                centerX + petalWidth / 2f,
                centerY + petalHeight / 2f
            )
            bitmapPaint.alpha = 56
            canvas.save()
            canvas.rotate(-12f + index * 10f + phase * 18f, centerX, centerY)
            canvas.drawBitmap(petals, sourceRect, drawRect, bitmapPaint)
            canvas.restore()
        }
    }

    private fun drawWeather(canvas: Canvas, density: Float) {
        when (weatherCondition) {
            WeatherCondition.RAIN -> drawRain(canvas, density, 12)
            WeatherCondition.HEAVY_RAIN -> drawRain(canvas, density, 22)
            WeatherCondition.STORM -> {
                drawRain(canvas, density, 26)
                drawWind(canvas, density)
            }
            WeatherCondition.WIND -> drawWind(canvas, density)
            WeatherCondition.SNOW -> drawSnow(canvas, density)
            WeatherCondition.CLEAR,
            WeatherCondition.CLOUDY -> Unit
        }
    }

    private fun drawRain(canvas: Canvas, density: Float, count: Int) {
        weatherPaint.style = Paint.Style.STROKE
        weatherPaint.color = Color.parseColor("#A8D6FF")
        weatherPaint.alpha = if (environment == EventEnvironment.QIXI) 115 else 190
        weatherPaint.strokeWidth = 1.1f * density
        repeat(count) { index ->
            val phase = (animationProgress * 6f + index * 0.173f) % 1f
            val x = width * (index + 0.5f) / count
            val y = height * phase
            canvas.drawLine(x, y, x - 2.5f * density, y + 7f * density, weatherPaint)
        }
        weatherPaint.alpha = 255
    }

    private fun drawWind(canvas: Canvas, density: Float) {
        weatherPaint.style = Paint.Style.STROKE
        weatherPaint.color = Color.parseColor("#B9EDFF")
        weatherPaint.alpha = if (environment == EventEnvironment.QIXI) 105 else 170
        weatherPaint.strokeWidth = density
        repeat(5) { index ->
            val phase = (animationProgress * 2.5f + index * 0.23f) % 1f
            val x = width * phase
            val y = height * (0.18f + index * 0.16f)
            canvas.drawLine(x, y, x + 13f * density, y, weatherPaint)
        }
        weatherPaint.alpha = 255
    }

    private fun drawSnow(canvas: Canvas, density: Float) {
        weatherPaint.style = Paint.Style.FILL
        weatherPaint.color = Color.WHITE
        weatherPaint.alpha = if (environment == EventEnvironment.QIXI) 135 else 220
        repeat(14) { index ->
            val phase = (animationProgress * 1.8f + index * 0.137f) % 1f
            val x = width * ((index * 0.379f) % 1f)
            val y = height * phase
            canvas.drawCircle(x, y, 1.3f * density, weatherPaint)
        }
        weatherPaint.alpha = 255
    }

    private var isButterflyVisible = false
    private var butterflyX = 0f
    private var butterflyY = 0f
    private var isButterflyFleeing = false
    private var fleeProgress = 0f
    private var fleeDirectionRight = true
    private val butterflyRect = RectF()

    private val fleeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900L
        addUpdateListener { anim ->
            fleeProgress = anim.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isButterflyVisible = false
                isButterflyFleeing = false
                updateVisibility()
                invalidate()
            }
        })
    }

    fun spawnButterfly(x: Float, y: Float) {
        fleeAnimator.cancel()
        fleeProgress = 0f
        butterflyX = x
        butterflyY = y
        isButterflyVisible = true
        isButterflyFleeing = false
        updateVisibility()
        invalidate()
    }

    fun fleeButterfly() {
        if (!isButterflyVisible || isButterflyFleeing) return
        isButterflyFleeing = true
        fleeDirectionRight = butterflyX < width / 2f
        fleeAnimator.start()
    }

    fun dismissButterfly() {
        fleeAnimator.cancel()
        isButterflyVisible = false
        isButterflyFleeing = false
        updateVisibility()
        invalidate()
    }

    private fun drawButterfly(canvas: Canvas, density: Float) {
        if (butterflyFrames.isEmpty()) return

        // 4 flight flapping frames cycling rapidly
        val frameIndex = ((animationProgress * 36f).toInt() % butterflyFrames.size).coerceIn(0, butterflyFrames.size - 1)
        val frame = butterflyFrames[frameIndex]

        val butterflySize = 28f * density
        val currentDrawX: Float
        val currentDrawY: Float
        val currentAlpha: Int

        if (isButterflyFleeing) {
            val fleeXOffset = if (fleeDirectionRight) fleeProgress * 120f * density else -fleeProgress * 120f * density
            val fleeYOffset = -fleeProgress * 80f * density
            currentDrawX = butterflyX + fleeXOffset
            currentDrawY = butterflyY + fleeYOffset
            currentAlpha = ((1f - fleeProgress) * 255).toInt().coerceIn(0, 255)
        } else {
            val hoverX = sin(animationProgress * 10f * Math.PI).toFloat() * 3f * density
            val hoverY = cos(animationProgress * 6f * Math.PI).toFloat() * 3f * density
            currentDrawX = butterflyX + hoverX
            currentDrawY = butterflyY + hoverY
            currentAlpha = 255
        }

        butterflyRect.set(
            currentDrawX - butterflySize / 2f,
            currentDrawY - butterflySize / 2f,
            currentDrawX + butterflySize / 2f,
            currentDrawY + butterflySize / 2f
        )

        bitmapPaint.alpha = currentAlpha
        canvas.drawBitmap(frame, null, butterflyRect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    private fun updateVisibility() {
        val hasWeatherEffect = weatherCondition != WeatherCondition.CLEAR &&
            weatherCondition != WeatherCondition.CLOUDY
        visibility = if (environment != EventEnvironment.DEFAULT || hasWeatherEffect || isButterflyVisible) VISIBLE else GONE
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 6_000L
        private val PETAL_X_FRACTIONS = floatArrayOf(0.28f, 0.58f, 0.82f)

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
