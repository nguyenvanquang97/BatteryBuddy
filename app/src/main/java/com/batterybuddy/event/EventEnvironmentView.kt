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
import android.graphics.Path
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

    fun pauseAnimation() {
        animator.cancel()
    }

    fun resumeAnimation() {
        if (!isAttachedToWindow) return
        if (visibility == VISIBLE && !animator.isStarted) {
            animator.start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        if (environment == EventEnvironment.QIXI) {
            drawQixiEnvironment(canvas, density)
        } else if (environment == EventEnvironment.NATIONAL_DAY) {
            drawNationalDayEnvironment(canvas, density)
        }
        drawWeather(canvas, density)
        if (isButterflyVisible) {
            drawButterfly(canvas, density)
        }
        if (isLightningStriking) {
            drawLightningStrike(canvas, density)
        }
    }

    private val mausoleumBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#78909C")
    }
    private val mausoleumLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B0BEC5")
    }
    private val mausoleumDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#546E7A")
    }
    private val flagRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#D32F2F")
    }
    private val flagGoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD700")
    }
    private val flagpolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#CFD8DC")
    }
    private val flagPath = Path()
    private val starPath = Path()

    private fun drawNationalDayEnvironment(canvas: Canvas, density: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Place Ba Dinh Square & Mausoleum centrally in background
        val centerX = w * 0.5f
        val groundY = h - 2f * density
        val mausoleumH = (h * 0.58f).coerceAtMost(22f * density).coerceAtLeast(14f * density)
        val mausoleumW = mausoleumH * 1.7f

        drawBaDinhMausoleum(canvas, centerX, groundY, mausoleumW, mausoleumH, density)
    }

    private fun drawBaDinhMausoleum(
        canvas: Canvas,
        centerX: Float,
        groundY: Float,
        w: Float,
        h: Float,
        density: Float
    ) {
        val alpha = 110 // Soft elegant semi-transparency (~43%) so cat pops out
        mausoleumBasePaint.alpha = alpha
        mausoleumLightPaint.alpha = (alpha * 1.15f).toInt().coerceIn(0, 255)
        mausoleumDarkPaint.alpha = (alpha * 0.9f).toInt().coerceIn(0, 255)

        // 1. Plinth / Steps (3 tiered stepped base at the bottom)
        val step1W = w * 1.12f
        val step1H = h * 0.10f
        val step1Y = groundY - step1H
        canvas.drawRect(centerX - step1W / 2f, step1Y, centerX + step1W / 2f, groundY, mausoleumBasePaint)

        val step2W = w * 1.04f
        val step2H = h * 0.08f
        val step2Y = step1Y - step2H
        canvas.drawRect(centerX - step2W / 2f, step2Y, centerX + step2W / 2f, step1Y, mausoleumLightPaint)

        val step3W = w * 0.96f
        val step3H = h * 0.08f
        val step3Y = step2Y - step3H
        canvas.drawRect(centerX - step3W / 2f, step3Y, centerX + step3W / 2f, step2Y, mausoleumBasePaint)

        // 2. Colonnade & Inner Wall (Hàng cột vuông đặc trưng)
        val colonnadeH = h * 0.44f
        val colonnadeY = step3Y - colonnadeH
        val colonnadeW = w * 0.86f
        // Inner recessed wall (darker)
        canvas.drawRect(
            centerX - colonnadeW / 2f,
            colonnadeY,
            centerX + colonnadeW / 2f,
            step3Y,
            mausoleumDarkPaint
        )

        // 6 Columns of the Mausoleum
        val numColumns = 6
        val columnW = colonnadeW * 0.09f
        val spacing = (colonnadeW - numColumns * columnW) / (numColumns - 1)
        var colX = centerX - colonnadeW / 2f
        for (i in 0 until numColumns) {
            canvas.drawRect(colX, colonnadeY, colX + columnW, step3Y, mausoleumLightPaint)
            colX += columnW + spacing
        }

        // 3. Entablature & Stepped Roof (Mái Lăng Bác)
        val beamH = h * 0.10f
        val beamY = colonnadeY - beamH
        val beamW = w * 0.94f
        canvas.drawRect(centerX - beamW / 2f, beamY, centerX + beamW / 2f, colonnadeY, mausoleumLightPaint)

        // Upper Roof tier 1
        val roofTier1H = h * 0.12f
        val roofTier1Y = beamY - roofTier1H
        val roofTier1W = w * 0.84f
        canvas.drawRect(centerX - roofTier1W / 2f, roofTier1Y, centerX + roofTier1W / 2f, beamY, mausoleumBasePaint)

        // Upper Roof tier 2 (Top flat roof)
        val roofTier2H = h * 0.08f
        val roofTier2Y = roofTier1Y - roofTier2H
        val roofTier2W = w * 0.72f
        canvas.drawRect(centerX - roofTier2W / 2f, roofTier2Y, centerX + roofTier2W / 2f, roofTier1Y, mausoleumLightPaint)

        // 4. Single National Flagpole on Ba Dinh Square (Left side of Mausoleum)
        val flagpoleX = centerX - w * 0.65f
        val flagpoleTopY = roofTier2Y - h * 0.25f
        val flagW = 13f * density
        val flagH = flagW * 0.65f

        flagpolePaint.alpha = (alpha * 1.3f).toInt().coerceIn(0, 255)
        flagpolePaint.strokeWidth = 1.2f * density
        canvas.drawLine(flagpoleX, flagpoleTopY, flagpoleX, groundY, flagpolePaint)
        flagGoldPaint.alpha = (alpha * 1.4f).toInt().coerceIn(0, 255)
        canvas.drawCircle(flagpoleX, flagpoleTopY, 1.2f * density, flagGoldPaint)

        // Single gentle waving flag attached to the top of flagpole
        val wave = sin(animationProgress * 3.5f * Math.PI).toFloat()
        drawStationaryFlag(canvas, flagpoleX, flagpoleTopY, flagW, flagH, wave, alpha, density)
    }

    private fun drawStationaryFlag(
        canvas: Canvas,
        x: Float,
        y: Float,
        flagWidth: Float,
        flagHeight: Float,
        wave: Float,
        baseAlpha: Int,
        density: Float
    ) {
        val alpha = (baseAlpha * 1.45f).toInt().coerceIn(0, 240)
        flagRedPaint.alpha = alpha
        flagGoldPaint.alpha = alpha

        val waveAmplitude = 1.4f * density * wave

        flagPath.reset()
        flagPath.moveTo(x, y)
        flagPath.cubicTo(
            x + flagWidth * 0.35f, y + waveAmplitude,
            x + flagWidth * 0.65f, y - waveAmplitude,
            x + flagWidth, y + waveAmplitude * 0.4f
        )
        flagPath.lineTo(x + flagWidth, y + flagHeight + waveAmplitude * 0.4f)
        flagPath.cubicTo(
            x + flagWidth * 0.65f, y + flagHeight - waveAmplitude,
            x + flagWidth * 0.35f, y + flagHeight + waveAmplitude,
            x, y + flagHeight
        )
        flagPath.close()
        canvas.drawPath(flagPath, flagRedPaint)

        // Center Gold Star
        val starCenterX = x + flagWidth * 0.45f
        val starCenterY = y + flagHeight * 0.5f + waveAmplitude * 0.2f
        val starSize = flagHeight * 0.32f
        drawMiniStar(canvas, starCenterX, starCenterY, starSize, flagGoldPaint)
    }

    private fun drawMiniStar(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        starPath.reset()
        for (i in 0 until 5) {
            val angleOuter = (i * 72 - 18) * Math.PI / 180.0
            val xOuter = cx + (size * kotlin.math.cos(angleOuter)).toFloat()
            val yOuter = cy + (size * kotlin.math.sin(angleOuter)).toFloat()
            if (i == 0) starPath.moveTo(xOuter, yOuter) else starPath.lineTo(xOuter, yOuter)

            val angleInner = ((i * 72) + 36 - 18) * Math.PI / 180.0
            val xInner = cx + (size * 0.45f * kotlin.math.cos(angleInner)).toFloat()
            val yInner = cy + (size * 0.45f * kotlin.math.sin(angleInner)).toFloat()
            starPath.lineTo(xInner, yInner)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
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
        visibility = if (environment != EventEnvironment.DEFAULT || hasWeatherEffect || isButterflyVisible || isLightningStriking) VISIBLE else GONE
    }

    private var isLightningStriking = false
    private var lightningStrikeX = 0f
    private var lightningProgress = 0f
    private val mainLightningPath = Path()
    private val branchLightningPath = Path()

    private val lightningGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFD700") // Neon Gold Glow
    }
    private val lightningCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FFFFFF") // Brilliant White Core
    }
    private val lightningFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#40FFF59D") // Soft cartoon ambient flash
    }

    private var lightningAnimator: ValueAnimator? = null

    fun triggerLightningStrike(targetX: Float, targetY: Float) {
        lightningStrikeX = targetX
        isLightningStriking = true
        lightningAnimator?.cancel()
        lightningAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500L
            addUpdateListener { anim ->
                lightningProgress = anim.animatedValue as Float
                buildLightningPaths(targetX, density = resources.displayMetrics.density)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isLightningStriking = false
                    updateVisibility()
                    invalidate()
                }
            })
            start()
        }
        updateVisibility()
    }

    private fun buildLightningPaths(targetX: Float, density: Float) {
        mainLightningPath.reset()
        branchLightningPath.reset()

        val h = height.toFloat().coerceAtLeast(40f * density)
        // Main zigzag bolt from screen top y=0 down to the cat head
        val startX = targetX + if ((lightningProgress * 14).toInt() % 2 == 0) -8f * density else 8f * density
        mainLightningPath.moveTo(startX, 0f)
        mainLightningPath.lineTo(targetX - 10f * density, h * 0.25f)
        mainLightningPath.lineTo(targetX + 12f * density, h * 0.50f)
        mainLightningPath.lineTo(targetX - 6f * density, h * 0.72f)
        mainLightningPath.lineTo(targetX, h * 0.88f)

        // Branch bolt shooting to the side
        branchLightningPath.moveTo(targetX + 12f * density, h * 0.50f)
        branchLightningPath.lineTo(targetX + 22f * density, h * 0.65f)
        branchLightningPath.lineTo(targetX + 30f * density, h * 0.78f)
    }

    private fun drawLightningStrike(canvas: Canvas, density: Float) {
        // 1. Ambient Cartoon Flash (first 180ms)
        if (lightningProgress < 0.35f) {
            val flashAlpha = ((1f - lightningProgress / 0.35f) * 70).toInt().coerceIn(0, 255)
            lightningFlashPaint.alpha = flashAlpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), lightningFlashPaint)
        }

        // 2. Jagged Cartoon Lightning Bolt with flickering alpha
        val flickers = (sin(lightningProgress * 12f * Math.PI) > -0.2f)
        val boltAlpha = if (flickers && lightningProgress < 0.85f) {
            ((1f - lightningProgress) * 255).toInt().coerceIn(0, 255)
        } else 0

        if (boltAlpha > 0) {
            // Outer Gold Glow
            lightningGlowPaint.strokeWidth = 6.5f * density
            lightningGlowPaint.alpha = (boltAlpha * 0.85f).toInt()
            canvas.drawPath(mainLightningPath, lightningGlowPaint)
            lightningGlowPaint.strokeWidth = 4f * density
            canvas.drawPath(branchLightningPath, lightningGlowPaint)

            // Inner Pure White Core
            lightningCorePaint.strokeWidth = 2.4f * density
            lightningCorePaint.alpha = boltAlpha
            canvas.drawPath(mainLightningPath, lightningCorePaint)
            lightningCorePaint.strokeWidth = 1.5f * density
            canvas.drawPath(branchLightningPath, lightningCorePaint)
        }
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
