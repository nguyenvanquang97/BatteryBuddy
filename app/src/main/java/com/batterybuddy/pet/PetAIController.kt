package com.batterybuddy.pet

import com.batterybuddy.battery.BatteryState
import com.batterybuddy.weather.LightningPolicy
import com.batterybuddy.weather.WeatherSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private var weatherJob: Job? = null
    private var specialEventJob: Job? = null
    private var controllerScope: CoroutineScope? = null
    private var chargingRoutinePending = false
    private var petWidthPx = 0
    private var currentFacingRight = true
    private var noStopZoneLeft: Int? = null
    private var noStopZoneRight: Int? = null
    private var noStopMarginPx = 0
    private var weatherSnapshot: WeatherSnapshot? = null
    private var lastLightningAtMs = 0L
    private var lastPokeAtMs = 0L

    var onPositionChanged: ((x: Int, isFacingRight: Boolean) -> Unit)? = null
    var onStateChanged: ((state: PetBehaviorState) -> Unit)? = null

    val isRunning: Boolean
        get() = controllerScope != null

    fun start(scope: CoroutineScope) {
        stop()
        controllerScope = scope
        launchAiLoop()
        weatherJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(WEATHER_ROLL_INTERVAL_MS)
                maybeTriggerLightning()
            }
        }
    }

    fun stop() {
        aiJob?.cancel()
        weatherJob?.cancel()
        specialEventJob?.cancel()
        aiJob = null
        weatherJob = null
        specialEventJob = null
        controllerScope = null
    }

    fun updateBatteryState(state: BatteryState) {
        val chargingChanged = currentBatteryState.isCharging != state.isCharging
        if (chargingChanged) {
            chargingRoutinePending = state.isCharging
        }
        currentBatteryState = state

        if (chargingChanged) {
            restartAiLoop()
        }
    }

    fun updateWeather(snapshot: WeatherSnapshot?) {
        weatherSnapshot = snapshot
    }

    fun forceBehavior(state: PetBehaviorState, durationMs: Long = TEST_STATE_DURATION_MS) {
        val scope = controllerScope ?: return
        specialEventJob?.cancel()
        aiJob?.cancel()
        specialEventJob = scope.launch(Dispatchers.Main) {
            setBehavior(state)
            delay(durationMs)
            launchAiLoop()
        }
    }

    fun forceLightning() {
        triggerLightning(force = true)
    }

    fun reactToPoke() {
        val scope = controllerScope ?: return
        val now = System.currentTimeMillis()
        if (now - lastPokeAtMs < POKE_COOLDOWN_MS) return
        lastPokeAtMs = now

        specialEventJob?.cancel()
        aiJob?.cancel()
        specialEventJob = scope.launch(Dispatchers.Main) {
            setBehavior(PetBehaviorState.POKE_JUMP)
            delay(POKE_JUMP_DURATION_MS)
            setBehavior(PetBehaviorState.ANGRY_LOOK)
            delay(ANGRY_LOOK_DURATION_MS)
            launchAiLoop()
        }
    }

    fun updatePlaygroundBounds(minX: Int, maxX: Int, petWidthPx: Int) {
        this.minX = minX
        this.maxX = maxX
        this.petWidthPx = petWidthPx.coerceAtLeast(0)

        val clampedX = safeStationaryX(
            currentX.coerceIn(effectiveMinX(), effectiveMaxX())
        )
        if (clampedX != currentX) {
            currentX = clampedX
            onPositionChanged?.invoke(currentX, currentFacingRight)
        }
    }

    fun updateNoStopZone(left: Int?, right: Int?, marginPx: Int = 0) {
        noStopZoneLeft = left
        noStopZoneRight = right
        noStopMarginPx = marginPx.coerceAtLeast(0)

        val safeX = safeStationaryX(currentX)
        if (safeX != currentX) {
            currentX = safeX
            onPositionChanged?.invoke(currentX, currentFacingRight)
        }
    }

    private suspend fun decideAndPerformNextAction() {
        val pct = currentBatteryState.percentage
        val isCharging = currentBatteryState.isCharging

        // Determine probabilities based on battery status
        val behavior = when {
            isCharging -> {
                if (chargingRoutinePending) {
                    chargingRoutinePending = false
                    PetBehaviorState.DRINK_MILK
                } else {
                    val r = Random.nextFloat()
                    when {
                        r < 0.6f -> PetBehaviorState.DRINK_MILK
                        r < 0.8f -> PetBehaviorState.CHARGING_HAPPY
                        r < 0.9f -> PetBehaviorState.LOOK_FRONT
                        else -> PetBehaviorState.WALK
                    }
                }
            }
            pct >= 80 -> {
                val r = Random.nextFloat()
                when {
                    r < 0.5f -> PetBehaviorState.WALK
                    r < 0.75f -> PetBehaviorState.IDLE
                    else -> PetBehaviorState.LOOK_FRONT
                }
            }
            pct >= 40 -> {
                val r = Random.nextFloat()
                when {
                    r < 0.3f -> PetBehaviorState.WALK
                    r < 0.55f -> PetBehaviorState.IDLE
                    r < 0.75f -> PetBehaviorState.SIT
                    else -> PetBehaviorState.LOOK_FRONT
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

        if (behavior == PetBehaviorState.SIT && currentBehavior != PetBehaviorState.SIT) {
            setBehavior(PetBehaviorState.SIT_DOWN)
            delay(SIT_DOWN_DURATION_MS)
        }
        if (behavior == PetBehaviorState.DRINK_MILK && currentBehavior != PetBehaviorState.DRINK_MILK) {
            setBehavior(PetBehaviorState.DRINK_START)
            delay(DRINK_START_DURATION_MS)
            if (!currentBatteryState.isCharging) return
        }
        setBehavior(behavior)

        when (behavior) {
            PetBehaviorState.WALK -> performWalkAction()
            PetBehaviorState.IDLE -> delay(Random.nextLong(3000, 6000))
            PetBehaviorState.SIT -> delay(Random.nextLong(4000, 8000))
            PetBehaviorState.SIT_DOWN -> delay(SIT_DOWN_DURATION_MS)
            PetBehaviorState.LOOK_FRONT -> delay(Random.nextLong(3000, 5000))
            PetBehaviorState.SLEEP -> delay(Random.nextLong(6000, 12000))
            PetBehaviorState.CHARGING_HAPPY -> delay(Random.nextLong(3000, 6000))
            PetBehaviorState.DRINK_START -> delay(DRINK_START_DURATION_MS)
            PetBehaviorState.DRINK_MILK -> delay(Random.nextLong(6000, 10000))
            PetBehaviorState.LIGHTNING_HIT -> delay(LIGHTNING_HIT_DURATION_MS)
            PetBehaviorState.SHOCKED -> delay(SHOCKED_DURATION_MS)
            PetBehaviorState.POKE_JUMP -> delay(POKE_JUMP_DURATION_MS)
            PetBehaviorState.ANGRY_LOOK -> delay(ANGRY_LOOK_DURATION_MS)
        }
    }

    private suspend fun performWalkAction() {
        val effectiveMinX = effectiveMinX()
        val effectiveMaxX = effectiveMaxX()

        val targetX = randomSafeTargetX(effectiveMinX, effectiveMaxX)
        val distance = Math.abs(targetX - currentX)

        if (distance < 10) {
            delayAfterStopping(transitionAfterWalk())
            return
        }

        val isFacingRight = targetX > currentX
        currentFacingRight = isFacingRight

        val stepDelayMs = when {
            currentBatteryState.isCharging -> 15L
            currentBatteryState.percentage >= 80 -> 20L
            currentBatteryState.percentage >= 40 -> 30L
            currentBatteryState.percentage >= 15 -> 50L
            else -> 80L
        }

        val stepMagnitude = when {
            currentBatteryState.isCharging -> 4
            currentBatteryState.percentage >= 80 -> 4
            currentBatteryState.percentage >= 40 -> 3
            currentBatteryState.percentage >= 15 -> 2
            else -> 1
        }

        while (currentX != targetX) {
            currentX = if (isFacingRight) {
                minOf(currentX + stepMagnitude, targetX)
            } else {
                maxOf(currentX - stepMagnitude, targetX)
            }
            onPositionChanged?.invoke(currentX, isFacingRight)
            delay(stepDelayMs)
        }

        delayAfterStopping(transitionAfterWalk())
    }

    private fun transitionAfterWalk(): PetBehaviorState {
        val canLookFront = currentBatteryState.isCharging || currentBatteryState.percentage >= 40
        val restingState = if (canLookFront && Random.nextFloat() < 0.35f) {
            PetBehaviorState.LOOK_FRONT
        } else {
            PetBehaviorState.IDLE
        }
        setBehavior(restingState)
        return restingState
    }

    private suspend fun delayAfterStopping(state: PetBehaviorState) {
        val duration = if (state == PetBehaviorState.LOOK_FRONT) {
            Random.nextLong(3000, 5000)
        } else {
            Random.nextLong(1000, 3000)
        }
        delay(duration)
    }

    private fun setBehavior(state: PetBehaviorState) {
        if (state != PetBehaviorState.WALK) {
            val safeX = safeStationaryX(currentX)
            if (safeX != currentX) {
                currentX = safeX
                onPositionChanged?.invoke(currentX, currentFacingRight)
            }
        }
        currentBehavior = state
        onStateChanged?.invoke(state)
    }

    private fun effectiveMinX(): Int = minX.coerceAtLeast(0)

    private fun effectiveMaxX(): Int =
        (maxX - petWidthPx).coerceAtLeast(effectiveMinX())

    private fun launchAiLoop() {
        val scope = controllerScope ?: return
        aiJob?.cancel()
        aiJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                decideAndPerformNextAction()
            }
        }
    }

    private fun restartAiLoop() {
        if (specialEventJob?.isActive == true) return
        launchAiLoop()
    }

    private fun maybeTriggerLightning() {
        val snapshot = weatherSnapshot ?: return
        val now = System.currentTimeMillis()
        if (LightningPolicy.shouldStrike(
                condition = snapshot.condition,
                nowMs = now,
                lastStrikeAtMs = lastLightningAtMs,
                randomValue = Random.nextFloat()
            )
        ) {
            triggerLightning(force = false)
        }
    }

    private fun triggerLightning(force: Boolean) {
        val scope = controllerScope ?: return
        if (force) {
            specialEventJob?.cancel()
        } else if (specialEventJob?.isActive == true) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastLightningAtMs < LightningPolicy.COOLDOWN_MS) return

        lastLightningAtMs = now
        aiJob?.cancel()
        specialEventJob = scope.launch(Dispatchers.Main) {
            setBehavior(PetBehaviorState.LIGHTNING_HIT)
            delay(LIGHTNING_HIT_DURATION_MS)
            setBehavior(PetBehaviorState.SHOCKED)
            delay(SHOCKED_DURATION_MS)
            launchAiLoop()
        }
    }

    private fun randomSafeTargetX(minX: Int, maxX: Int): Int {
        if (maxX <= minX) return minX

        val zoneLeft = noStopZoneLeft ?: return Random.nextInt(minX, maxX + 1)
        val zoneRight = noStopZoneRight ?: return Random.nextInt(minX, maxX + 1)
        val leftMax = minOf(maxX, zoneLeft - petWidthPx - noStopMarginPx)
        val rightMin = maxOf(minX, zoneRight + noStopMarginPx)
        val leftCount = (leftMax - minX + 1).coerceAtLeast(0)
        val rightCount = (maxX - rightMin + 1).coerceAtLeast(0)
        val totalCount = leftCount + rightCount

        if (totalCount == 0) return minX
        val offset = Random.nextInt(totalCount)
        return if (offset < leftCount) minX + offset else rightMin + offset - leftCount
    }

    private fun safeStationaryX(x: Int): Int {
        val zoneLeft = noStopZoneLeft ?: return x
        val zoneRight = noStopZoneRight ?: return x
        val overlapsZone = x + petWidthPx + noStopMarginPx > zoneLeft &&
            x - noStopMarginPx < zoneRight
        if (!overlapsZone) return x

        val minX = effectiveMinX()
        val maxX = effectiveMaxX()
        val leftCandidate = zoneLeft - petWidthPx - noStopMarginPx
        val rightCandidate = zoneRight + noStopMarginPx
        val candidates = listOf(leftCandidate, rightCandidate)
            .filter { it in minX..maxX }
        return candidates.minByOrNull { kotlin.math.abs(it - x) } ?: x
    }

    companion object {
        private const val SIT_DOWN_DURATION_MS = 700L
        private const val DRINK_START_DURATION_MS = 800L
        private const val LIGHTNING_HIT_DURATION_MS = 700L
        private const val SHOCKED_DURATION_MS = 2_500L
        private const val WEATHER_ROLL_INTERVAL_MS = 30_000L
        private const val TEST_STATE_DURATION_MS = 4_000L
        private const val POKE_JUMP_DURATION_MS = 650L
        private const val ANGRY_LOOK_DURATION_MS = 3_000L
        private const val POKE_COOLDOWN_MS = 500L
    }
}
