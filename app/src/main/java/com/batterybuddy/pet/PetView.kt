package com.batterybuddy.pet

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
            field = value
            animationProgress = 0f
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

    private val spriteAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animator ->
            animationProgress = animator.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!spriteAnimator.isStarted) spriteAnimator.start()
    }

    override fun onDetachedFromWindow() {
        spriteAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val spriteSize = (customSizeSp * density * SPRITE_SIZE_MULTIPLIER).roundToInt()
        badgeTextPaint.textSize = customSizeSp * density * 0.45f

        val totalWidth = spriteSize + (24 * density).roundToInt()
        val totalHeight = spriteSize + (18 * density).roundToInt()

        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
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
            PetBehaviorState.SLEEP -> wave * 0.5f * density
            PetBehaviorState.CHARGING_HAPPY -> wave * 2f * density
        }
        val scale = when (behaviorState) {
            PetBehaviorState.SLEEP -> 1f + wave * 0.015f
            PetBehaviorState.CHARGING_HAPPY -> 1f + fastWave * 0.02f
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
        canvas.drawBitmap(frame, null, spriteRect, spritePaint)
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
            PetBehaviorState.IDLE -> if (animationProgress > 0.82f) 1 else 0
            PetBehaviorState.SLEEP -> if (animationProgress > 0.68f) 1 else 0
            PetBehaviorState.WALK,
            PetBehaviorState.CHARGING_HAPPY ->
                ((animationProgress * frameCount * 2).toInt() % frameCount)
            PetBehaviorState.SIT -> 0
        }
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 1_200L
        private const val SPRITE_SIZE_MULTIPLIER = 2.6f
    }
}
