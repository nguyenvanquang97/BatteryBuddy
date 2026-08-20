package com.batterybuddy.pet

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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.batterybuddy.battery.BatteryState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class PetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var behaviorState: PetBehaviorState = PetBehaviorState.IDLE
        set(value) {
            if (field == value) return
            stateTransitionAnimator.cancel()
            val oldFrames = spriteFrames.getValue(field)
            previousFrame = oldFrames[frameIndex(oldFrames.size)]
            field = value
            animationProgress = 0f
            restartAnimation()
            startStateTransition()
            requestLayout()
            invalidate()
        }

    var batteryState: BatteryState = BatteryState()
        set(value) {
            field = value
            invalidate()
        }

    var isFacingRight: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var customSizeSp: Float = 24f
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var showPercentageBadge: Boolean = true
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A90E2")
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val badgeRect = RectF()
    private val spriteRect = RectF()
    private val auraRect = RectF()
    private val sparkPath = Path()

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.parseColor("#FFEB3B"), PorterDuff.Mode.SRC_IN)
    }

    private val electricGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val electricSparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val spriteFrames: Map<PetBehaviorState, List<Bitmap>> by lazy {
        PetBehaviorState.values().associateWith { state ->
            state.frameResIds.map { resourceId ->
                BitmapFactory.decodeResource(resources, resourceId)
            }
        }
    }

    private var animationProgress = 0f
    private var previousFrame: Bitmap? = null
    private var stateTransitionProgress = 1f

    private val spriteAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = animationDurationFor(behaviorState)
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            animationProgress = animator.animatedValue as Float
            invalidate()
        }
    }

    private val stateTransitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = STATE_TRANSITION_DURATION_MS
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            stateTransitionProgress = animator.animatedValue as Float
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                previousFrame = null
                stateTransitionProgress = 1f
                invalidate()
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!spriteAnimator.isStarted) {
            spriteAnimator.duration = animationDurationFor(behaviorState)
            spriteAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        spriteAnimator.cancel()
        stateTransitionAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val spriteSize = (customSizeSp * density * SPRITE_SIZE_MULTIPLIER).roundToInt()
        badgeTextPaint.textSize = customSizeSp * density * 0.45f

        val totalWidth = desiredWidthPx()
        val totalHeight = spriteSize + (18 * density).roundToInt()

        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    fun desiredWidthPx(): Int {
        val density = resources.displayMetrics.density
        val spriteSize = (customSizeSp * density * SPRITE_SIZE_MULTIPLIER).roundToInt()
        return spriteSize + (24 * density).roundToInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val baseSize = customSizeSp * density * SPRITE_SIZE_MULTIPLIER
        val wave = sin(animationProgress * 2f * PI).toFloat()
        val fastWave = sin(animationProgress * 4f * PI).toFloat()

        val verticalOffset = when (behaviorState) {
            PetBehaviorState.WALK -> -kotlin.math.abs(fastWave) * 2.5f * density
            PetBehaviorState.RUN -> -kotlin.math.abs(fastWave) * 1.5f * density
            PetBehaviorState.IDLE -> wave * density
            PetBehaviorState.SIT -> wave * 0.4f * density
            PetBehaviorState.SIT_DOWN -> 0f
            PetBehaviorState.LOOK_FRONT -> wave * 0.6f * density
            PetBehaviorState.SLEEP -> wave * 0.5f * density
            PetBehaviorState.CHARGING_HAPPY -> wave * 2f * density
            PetBehaviorState.DRINK_START -> 0f
            PetBehaviorState.DRINK_MILK -> wave * 0.25f * density
            PetBehaviorState.LIGHTNING_HIT -> -kotlin.math.abs(fastWave) * 3f * density
            PetBehaviorState.SHOCKED -> wave * 1.2f * density
            PetBehaviorState.POKE_JUMP ->
                -sin(animationProgress * PI).toFloat() * 12f * density
            PetBehaviorState.POUNCE ->
                if (animationProgress in 0.35f..0.75f) {
                    -sin((animationProgress - 0.35f) / 0.4f * PI).toFloat() * 8f * density
                } else 0f
            PetBehaviorState.CONFUSED -> wave * 0.4f * density
            PetBehaviorState.ANGRY_LOOK -> fastWave * 0.4f * density
        }
        val scale = when (behaviorState) {
            PetBehaviorState.SLEEP -> 1f + wave * 0.015f
            PetBehaviorState.CHARGING_HAPPY -> 1f + fastWave * 0.02f
            PetBehaviorState.DRINK_MILK -> 1f + wave * 0.008f
            PetBehaviorState.LIGHTNING_HIT -> 1f + fastWave * 0.04f
            PetBehaviorState.POKE_JUMP -> 1f + kotlin.math.abs(wave) * 0.035f
            else -> 1f
        }
        val frames = spriteFrames.getValue(behaviorState)
        val frame = frames[frameIndex(frames.size)]

        val drawHeight = baseSize * scale
        val aspect = frame.width.toFloat() / frame.height.toFloat()
        val drawWidth = drawHeight * aspect

        val isLightning = behaviorState == PetBehaviorState.LIGHTNING_HIT
        val shakeX = if (isLightning) sin(animationProgress * 36f * PI).toFloat() * 2.5f * density else 0f
        val shakeY = if (isLightning) cos(animationProgress * 28f * PI).toFloat() * 2f * density else 0f

        val drawCenterX = centerX + shakeX
        val drawCenterY = centerY + verticalOffset + shakeY

        spriteRect.set(
            drawCenterX - drawWidth / 2f,
            drawCenterY - drawHeight / 2f,
            drawCenterX + drawWidth / 2f,
            drawCenterY + drawHeight / 2f
        )

        canvas.save()
        // Source sprites face left, so mirror them while the pet moves right.
        if (isFacingRight) {
            canvas.scale(-1f, 1f, drawCenterX, drawCenterY)
        }

        // 1. Draw Cartoon Golden Aura / Outline when struck by lightning
        if (isLightning) {
            val auraAlpha = ((sin(animationProgress * 20f * PI) * 0.35f + 0.65f) * 230).toInt().coerceIn(0, 255)
            auraPaint.alpha = auraAlpha
            val outlineOffset = 2.5f * density
            val offsets = arrayOf(
                -outlineOffset to 0f,
                outlineOffset to 0f,
                0f to -outlineOffset,
                0f to outlineOffset,
                -outlineOffset * 0.7f to -outlineOffset * 0.7f,
                outlineOffset * 0.7f to -outlineOffset * 0.7f,
                -outlineOffset * 0.7f to outlineOffset * 0.7f,
                outlineOffset * 0.7f to outlineOffset * 0.7f
            )
            for ((dx, dy) in offsets) {
                auraRect.set(spriteRect.left + dx, spriteRect.top + dy, spriteRect.right + dx, spriteRect.bottom + dy)
                canvas.drawBitmap(frame, null, auraRect, auraPaint)
            }
        }

        previousFrame?.let { oldFrame ->
            val oldAspect = oldFrame.width.toFloat() / oldFrame.height.toFloat()
            val oldDrawWidth = drawHeight * oldAspect
            val oldRect = RectF(
                drawCenterX - oldDrawWidth / 2f,
                drawCenterY - drawHeight / 2f,
                drawCenterX + oldDrawWidth / 2f,
                drawCenterY + drawHeight / 2f
            )
            spritePaint.alpha = ((1f - stateTransitionProgress) * 255).roundToInt()
            canvas.drawBitmap(oldFrame, null, oldRect, spritePaint)
        }
        spritePaint.alpha = (stateTransitionProgress * 255).roundToInt()
        canvas.drawBitmap(frame, null, spriteRect, spritePaint)
        spritePaint.alpha = 255

        // 2. Draw dynamic cartoon electric sparks crackling around cat
        if (isLightning) {
            drawElectricSparks(canvas, spriteRect, density)
        }

        canvas.restore()
    }

    private fun drawElectricSparks(canvas: Canvas, rect: RectF, density: Float) {
        val flickers = (sin(animationProgress * 24f * PI) > -0.3f)
        if (!flickers) return

        val sparkAlpha = ((sin(animationProgress * 30f * PI) * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255)
        electricGlowPaint.alpha = sparkAlpha
        electricSparkPaint.alpha = sparkAlpha

        val w = rect.width()
        val h = rect.height()

        // 4 Dynamic spark arcs around ears, paws, and body
        val sparkPoints = arrayOf(
            floatArrayOf(rect.left + w * 0.25f, rect.top + h * 0.15f, rect.left + w * 0.18f, rect.top + h * 0.05f, rect.left + w * 0.10f, rect.top + h * 0.12f),
            floatArrayOf(rect.right - w * 0.25f, rect.top + h * 0.20f, rect.right - w * 0.12f, rect.top + h * 0.10f, rect.right - w * 0.05f, rect.top + h * 0.18f),
            floatArrayOf(rect.left + w * 0.20f, rect.bottom - h * 0.20f, rect.left + w * 0.08f, rect.bottom - h * 0.15f, rect.left + w * 0.15f, rect.bottom - h * 0.05f),
            floatArrayOf(rect.right - w * 0.20f, rect.bottom - h * 0.25f, rect.right - w * 0.08f, rect.bottom - h * 0.35f, rect.right - w * 0.15f, rect.bottom - h * 0.45f)
        )

        for (pts in sparkPoints) {
            sparkPath.reset()
            sparkPath.moveTo(pts[0], pts[1])
            sparkPath.lineTo(pts[2], pts[3])
            sparkPath.lineTo(pts[4], pts[5])

            electricGlowPaint.strokeWidth = 3.5f * density
            canvas.drawPath(sparkPath, electricGlowPaint)

            electricSparkPaint.strokeWidth = 1.5f * density
            canvas.drawPath(sparkPath, electricSparkPaint)
        }
    }

    private fun frameIndex(frameCount: Int): Int {
        if (frameCount <= 1) return 0

        return when (behaviorState) {
            PetBehaviorState.IDLE,
            PetBehaviorState.SIT,
            PetBehaviorState.LOOK_FRONT,
            PetBehaviorState.CONFUSED,
            PetBehaviorState.SLEEP -> calmFrameIndex(frameCount)
            PetBehaviorState.WALK,
            PetBehaviorState.RUN,
            PetBehaviorState.SIT_DOWN,
            PetBehaviorState.DRINK_START,
            PetBehaviorState.DRINK_MILK,
            PetBehaviorState.LIGHTNING_HIT,
            PetBehaviorState.SHOCKED,
            PetBehaviorState.POUNCE,
            PetBehaviorState.POKE_JUMP ->
                (animationProgress * frameCount).toInt().coerceAtMost(frameCount - 1)
            PetBehaviorState.ANGRY_LOOK -> angryFrameIndex(frameCount)
            PetBehaviorState.CHARGING_HAPPY ->
                ((animationProgress * frameCount * 2).toInt() % frameCount)
        }
    }

    private fun angryFrameIndex(frameCount: Int): Int {
        val index = when {
            animationProgress < 0.12f -> 0
            animationProgress < 0.24f -> 1
            animationProgress < 0.36f -> 2
            else -> 3
        }
        return index.coerceAtMost(frameCount - 1)
    }

    private fun calmFrameIndex(frameCount: Int): Int {
        val index = when {
            animationProgress < 0.55f -> 0
            animationProgress < 0.70f -> 1
            animationProgress < 0.77f -> 2
            else -> 3
        }
        return index.coerceAtMost(frameCount - 1)
    }

    fun pauseAnimation() {
        spriteAnimator.cancel()
        stateTransitionAnimator.cancel()
    }

    fun resumeAnimation() {
        if (!isAttachedToWindow) return
        if (!spriteAnimator.isStarted) {
            spriteAnimator.duration = animationDurationFor(behaviorState)
            spriteAnimator.start()
        }
    }

    private fun restartAnimation() {
        if (!isAttachedToWindow) return

        spriteAnimator.cancel()
        spriteAnimator.duration = animationDurationFor(behaviorState)
        spriteAnimator.start()
    }

    private fun startStateTransition() {
        if (!isAttachedToWindow) {
            previousFrame = null
            stateTransitionProgress = 1f
            return
        }

        stateTransitionProgress = 0f
        stateTransitionAnimator.start()
    }

    private fun animationDurationFor(state: PetBehaviorState): Long = when (state) {
        PetBehaviorState.WALK -> WALK_ANIMATION_DURATION_MS
        PetBehaviorState.RUN -> RUN_ANIMATION_DURATION_MS
        PetBehaviorState.IDLE -> IDLE_ANIMATION_DURATION_MS
        PetBehaviorState.SIT -> SIT_ANIMATION_DURATION_MS
        PetBehaviorState.SIT_DOWN -> SIT_DOWN_ANIMATION_DURATION_MS
        PetBehaviorState.LOOK_FRONT -> LOOK_FRONT_ANIMATION_DURATION_MS
        PetBehaviorState.SLEEP -> SLEEP_ANIMATION_DURATION_MS
        PetBehaviorState.CHARGING_HAPPY -> CHARGING_ANIMATION_DURATION_MS
        PetBehaviorState.DRINK_START -> DRINK_START_ANIMATION_DURATION_MS
        PetBehaviorState.DRINK_MILK -> DRINK_MILK_ANIMATION_DURATION_MS
        PetBehaviorState.LIGHTNING_HIT -> LIGHTNING_HIT_ANIMATION_DURATION_MS
        PetBehaviorState.SHOCKED -> SHOCKED_ANIMATION_DURATION_MS
        PetBehaviorState.POKE_JUMP -> POKE_JUMP_ANIMATION_DURATION_MS
        PetBehaviorState.ANGRY_LOOK -> ANGRY_LOOK_ANIMATION_DURATION_MS
        PetBehaviorState.POUNCE -> POUNCE_ANIMATION_DURATION_MS
        PetBehaviorState.CONFUSED -> CONFUSED_ANIMATION_DURATION_MS
    }

    companion object {
        private const val WALK_ANIMATION_DURATION_MS = 700L
        private const val RUN_ANIMATION_DURATION_MS = 480L
        private const val IDLE_ANIMATION_DURATION_MS = 3_200L
        private const val SIT_ANIMATION_DURATION_MS = 3_600L
        private const val SIT_DOWN_ANIMATION_DURATION_MS = 700L
        private const val LOOK_FRONT_ANIMATION_DURATION_MS = 3_200L
        private const val SLEEP_ANIMATION_DURATION_MS = 2_400L
        private const val CHARGING_ANIMATION_DURATION_MS = 900L
        private const val DRINK_START_ANIMATION_DURATION_MS = 800L
        private const val DRINK_MILK_ANIMATION_DURATION_MS = 1_400L
        private const val LIGHTNING_HIT_ANIMATION_DURATION_MS = 700L
        private const val SHOCKED_ANIMATION_DURATION_MS = 1_000L
        private const val POKE_JUMP_ANIMATION_DURATION_MS = 650L
        private const val ANGRY_LOOK_ANIMATION_DURATION_MS = 3_000L
        private const val POUNCE_ANIMATION_DURATION_MS = 750L
        private const val CONFUSED_ANIMATION_DURATION_MS = 2_000L
        private const val STATE_TRANSITION_DURATION_MS = 180L
        private const val SPRITE_SIZE_MULTIPLIER = 2.6f
    }
}
