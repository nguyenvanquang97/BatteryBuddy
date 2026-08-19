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
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.batterybuddy.battery.BatteryState
import kotlin.math.PI
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
        val drawSize = baseSize * scale
        spriteRect.set(
            centerX - drawSize / 2f,
            centerY - drawSize / 2f + verticalOffset,
            centerX + drawSize / 2f,
            centerY + drawSize / 2f + verticalOffset
        )

        val frames = spriteFrames.getValue(behaviorState)
        val frame = frames[frameIndex(frames.size)]

        canvas.save()
        // Source sprites face left, so mirror them while the pet moves right.
        if (isFacingRight) {
            canvas.scale(-1f, 1f, centerX, centerY)
        }
        previousFrame?.let { oldFrame ->
            spritePaint.alpha = ((1f - stateTransitionProgress) * 255).roundToInt()
            canvas.drawBitmap(oldFrame, null, spriteRect, spritePaint)
        }
        spritePaint.alpha = (stateTransitionProgress * 255).roundToInt()
        canvas.drawBitmap(frame, null, spriteRect, spritePaint)
        spritePaint.alpha = 255
        canvas.restore()

        // Draw Status Badge (e.g. Zzz, ⚡, or Battery %)
        val badgeText = when {
            behaviorState.badgeText.isNotBlank() -> behaviorState.badgeText
            showPercentageBadge -> "${batteryState.percentage}%"
            else -> ""
        }

        if (badgeText.isNotBlank()) {
            val badgeX = spriteRect.right - 10 * density
            val badgeY = spriteRect.top + 12 * density

            val bw = badgeTextPaint.measureText(badgeText) + 8 * density
            val bh = 14 * density

            badgeRect.set(badgeX - bw / 2f, badgeY - bh, badgeX + bw / 2f, badgeY + 2 * density)

            badgePaint.color = when {
                batteryState.isCharging -> Color.parseColor("#FFD700") // Gold
                behaviorState == PetBehaviorState.SLEEP -> Color.parseColor("#7B61FF") // Purple
                batteryState.percentage < 20 -> Color.parseColor("#FF5252") // Red
                else -> Color.parseColor("#26A69A") // Teal
            }

            canvas.drawRoundRect(badgeRect, 8 * density, 8 * density, badgePaint)
            canvas.drawText(badgeText, badgeX, badgeY - 2 * density, badgeTextPaint)
        }
    }

    private fun frameIndex(frameCount: Int): Int {
        if (frameCount <= 1) return 0

        return when (behaviorState) {
            PetBehaviorState.IDLE,
            PetBehaviorState.SIT,
            PetBehaviorState.LOOK_FRONT,
            PetBehaviorState.SLEEP -> calmFrameIndex(frameCount)
            PetBehaviorState.WALK,
            PetBehaviorState.SIT_DOWN,
            PetBehaviorState.DRINK_START,
            PetBehaviorState.DRINK_MILK,
            PetBehaviorState.LIGHTNING_HIT,
            PetBehaviorState.SHOCKED,
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
    }

    companion object {
        private const val WALK_ANIMATION_DURATION_MS = 700L
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
        private const val STATE_TRANSITION_DURATION_MS = 180L
        private const val SPRITE_SIZE_MULTIPLIER = 2.6f
    }
}
