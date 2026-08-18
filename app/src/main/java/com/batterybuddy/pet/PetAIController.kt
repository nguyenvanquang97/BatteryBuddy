package com.batterybuddy.pet

import com.batterybuddy.battery.BatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class PetAIController {

    var minX: Int = 10
    var maxX: Int = 300
    var currentX: Int = 150
        private set

    var currentBehavior: PetBehaviorState = PetBehaviorState.IDLE
        private set

    private var currentBatteryState = BatteryState()
    private var aiJob: Job? = null

    var onPositionChanged: ((x: Int, isFacingRight: Boolean) -> Unit)? = null
    var onStateChanged: ((state: PetBehaviorState) -> Unit)? = null

    fun start(scope: CoroutineScope) {
        stop()
        aiJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                decideAndPerformNextAction()
            }
        }
    }

    fun stop() {
        aiJob?.cancel()
        aiJob = null
    }

    fun updateBatteryState(state: BatteryState) {
        currentBatteryState = state
    }

    private suspend fun decideAndPerformNextAction() {
        val pct = currentBatteryState.percentage
        val isCharging = currentBatteryState.isCharging

        // Determine probabilities based on battery status
        val behavior = when {
            isCharging -> {
                val r = Random.nextFloat()
                if (r < 0.6f) PetBehaviorState.WALK else PetBehaviorState.CHARGING_HAPPY
            }
            pct >= 80 -> {
                if (Random.nextFloat() < 0.7f) PetBehaviorState.WALK else PetBehaviorState.IDLE
            }
            pct >= 40 -> {
                val r = Random.nextFloat()
                when {
                    r < 0.4f -> PetBehaviorState.WALK
                    r < 0.8f -> PetBehaviorState.IDLE
                    else -> PetBehaviorState.SIT
                }
            }
            pct >= 15 -> {
                val r = Random.nextFloat()
                when {
                    r < 0.2f -> PetBehaviorState.WALK
                    r < 0.6f -> PetBehaviorState.SIT
                    else -> PetBehaviorState.SLEEP
                }
            }
            else -> {
                if (Random.nextFloat() < 0.8f) PetBehaviorState.SLEEP else PetBehaviorState.WALK
            }
        }

        currentBehavior = behavior
        onStateChanged?.invoke(behavior)

        when (behavior) {
            PetBehaviorState.WALK -> performWalkAction()
            PetBehaviorState.IDLE -> delay(Random.nextLong(3000, 6000))
            PetBehaviorState.SIT -> delay(Random.nextLong(4000, 8000))
            PetBehaviorState.SLEEP -> delay(Random.nextLong(6000, 12000))
            PetBehaviorState.CHARGING_HAPPY -> delay(Random.nextLong(3000, 6000))
        }
    }

    private suspend fun performWalkAction() {
        val effectiveMinX = minX.coerceAtLeast(0)
        val effectiveMaxX = maxX.coerceAtLeast(effectiveMinX + 50)

        val targetX = Random.nextInt(effectiveMinX, effectiveMaxX)
        val distance = Math.abs(targetX - currentX)

        if (distance < 10) {
            transitionToIdle()
            delay(1000)
            return
        }

        val isFacingRight = targetX > currentX

        val stepDelayMs = when {
            currentBatteryState.isCharging -> 15L
            currentBatteryState.percentage >= 80 -> 20L
            currentBatteryState.percentage >= 40 -> 30L
            currentBatteryState.percentage >= 15 -> 50L
            else -> 80L
        }

        val stepPx = if (targetX > currentX) 2 else -2

        while (Math.abs(targetX - currentX) > 2) {
            currentX += stepPx
            onPositionChanged?.invoke(currentX, isFacingRight)
            delay(stepDelayMs)
        }

        currentX = targetX
        onPositionChanged?.invoke(currentX, isFacingRight)

        transitionToIdle()
        delay(Random.nextLong(1000, 3000))
    }

    private fun transitionToIdle() {
        currentBehavior = PetBehaviorState.IDLE
        onStateChanged?.invoke(PetBehaviorState.IDLE)
    }
}
