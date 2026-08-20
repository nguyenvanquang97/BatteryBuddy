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
    private var lastButterflyAtMs = 0L

    var onPositionChanged: ((x: Int, isFacingRight: Boolean) -> Unit)? = null
    var onStateChanged: ((state: PetBehaviorState) -> Unit)? = null
    var onButterflySpawn: ((x: Float, y: Float) -> Unit)? = null
    var onButterflyFlee: (() -> Unit)? = null
    var onButterflyDismiss: (() -> Unit)? = null
    var onLightningStrike: ((x: Float, y: Float) -> Unit)? = null

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

    fun pause() {
        aiJob?.cancel()
        weatherJob?.cancel()
        specialEventJob?.cancel()
        aiJob = null
        weatherJob = null
        specialEventJob = null
    }

    fun resume() {
        val scope = controllerScope ?: return
        if (aiJob == null && specialEventJob == null) {
            launchAiLoop()
            if (weatherJob == null) {
                weatherJob = scope.launch(Dispatchers.Main) {
                    while (isActive) {
                        delay(WEATHER_ROLL_INTERVAL_MS)
                        maybeTriggerLightning()
                    }
                }
            }
        }
    }

    fun stop() {
        pause()
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

    fun forceButterfly() {
        triggerButterfly(force = true)
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

        val now = System.currentTimeMillis()
        if (!isCharging && pct >= 30 && now - lastButterflyAtMs >= BUTTERFLY_COOLDOWN_MS && Random.nextFloat() < BUTTERFLY_SPAWN_CHANCE) {
            triggerButterfly(force = false)
            return
        }

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
        when (behavior) {
            PetBehaviorState.WALK -> performWalkAction()
            else -> {
                setBehavior(behavior)
                when (behavior) {
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
                    PetBehaviorState.POUNCE -> delay(750L)
                    PetBehaviorState.CONFUSED -> delay(Random.nextLong(2000, 4000))
                    PetBehaviorState.WALK,
                    PetBehaviorState.RUN -> Unit
                }
            }
        }
    }

    private suspend fun performWalkAction() {
        val effectiveMinX = effectiveMinX()
        val effectiveMaxX = effectiveMaxX()

        val safeCurrentX = currentX.coerceIn(effectiveMinX, effectiveMaxX)
        if (safeCurrentX != currentX) {
            currentX = safeCurrentX
            onPositionChanged?.invoke(currentX, currentFacingRight)
        }

        val targetX = randomSafeTargetX(
            minX = effectiveMinX,
            maxX = effectiveMaxX,
            avoidX = currentX,
            minDistancePx = MIN_WALK_DISTANCE_PX
        )
        val distance = Math.abs(targetX - currentX)

        if (distance < MIN_WALK_DISTANCE_PX) {
            delayAfterStopping(transitionAfterWalk())
            return
        }

        val isFacingRight = targetX > currentX
        currentFacingRight = isFacingRight

        setBehavior(PetBehaviorState.WALK)

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
        if (state != PetBehaviorState.WALK && state != PetBehaviorState.RUN && state != PetBehaviorState.POUNCE) {
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
            val strikeX = (currentX + petWidthPx / 2).toFloat()
            onLightningStrike?.invoke(strikeX, 0f)
            setBehavior(PetBehaviorState.LIGHTNING_HIT)
            delay(LIGHTNING_HIT_DURATION_MS)
            setBehavior(PetBehaviorState.SHOCKED)
            delay(SHOCKED_DURATION_MS)
            launchAiLoop()
        }
    }

    private fun randomSafeTargetX(
        minX: Int,
        maxX: Int,
        avoidX: Int? = null,
        minDistancePx: Int = 0
    ): Int {
        if (maxX <= minX) return minX

        val targetRanges = safeTargetRanges(minX, maxX)
        val walkableRanges = if (avoidX != null && minDistancePx > 0) {
            targetRanges.flatMap { range ->
                listOfNotNull(
                    (range.first..minOf(range.last, avoidX - minDistancePx))
                        .takeIf { !it.isEmpty() },
                    (maxOf(range.first, avoidX + minDistancePx)..range.last)
                        .takeIf { !it.isEmpty() }
                )
            }
        } else {
            targetRanges
        }

        if (walkableRanges.isEmpty()) return avoidX?.coerceIn(minX, maxX) ?: minX

        val zoneLeft = noStopZoneLeft
        val zoneRight = noStopZoneRight
        if (zoneLeft != null && zoneRight != null && avoidX != null) {
            val petIsLeftOfCutout = avoidX + petWidthPx <= zoneLeft
            val petIsRightOfCutout = avoidX >= zoneRight
            val rightRanges = walkableRanges.filter { it.last >= zoneRight }
            val leftRanges = walkableRanges.filter { it.first <= zoneLeft - petWidthPx }
            if (petIsLeftOfCutout && rightRanges.isNotEmpty() && Random.nextFloat() < CROSS_CUTOUT_CHANCE) {
                return randomFromRanges(rightRanges)
            }
            if (petIsRightOfCutout && leftRanges.isNotEmpty() && Random.nextFloat() < CROSS_CUTOUT_CHANCE) {
                return randomFromRanges(leftRanges)
            }
        }

        return randomFromRanges(walkableRanges)
    }

    private fun safeTargetRanges(minX: Int, maxX: Int): List<IntRange> {
        val zoneLeft = noStopZoneLeft ?: return listOf(minX..maxX)
        val zoneRight = noStopZoneRight ?: return listOf(minX..maxX)
        val leftMax = minOf(maxX, zoneLeft - petWidthPx - noStopMarginPx)
        val rightMin = maxOf(minX, zoneRight + noStopMarginPx)
        return listOfNotNull(
            (minX..leftMax).takeIf { !it.isEmpty() },
            (rightMin..maxX).takeIf { !it.isEmpty() }
        )
    }

    private fun randomFromRanges(ranges: List<IntRange>): Int {
        val totalCount = ranges.sumOf { it.last - it.first + 1 }
        var offset = Random.nextInt(totalCount)
        ranges.forEach { range ->
            val count = range.last - range.first + 1
            if (offset < count) return range.first + offset
            offset -= count
        }
        return ranges.last().last
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

    private fun triggerButterfly(force: Boolean, targetX: Int? = null) {
        val scope = controllerScope ?: return
        if (force) {
            specialEventJob?.cancel()
        } else if (specialEventJob?.isActive == true) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastButterflyAtMs < BUTTERFLY_COOLDOWN_MS) return

        lastButterflyAtMs = now
        aiJob?.cancel()
        specialEventJob = scope.launch(Dispatchers.Main) {
            val effMin = effectiveMinX()
            val effMax = effectiveMaxX()

            val spawnX = targetX ?: run {
                val leftSpace = (currentX - effMin).coerceAtLeast(0)
                val rightSpace = (effMax - currentX).coerceAtLeast(0)

                // Choose side with more available running room
                val goRight = if (leftSpace >= 300 && rightSpace >= 300) {
                    Random.nextBoolean()
                } else {
                    rightSpace >= leftSpace
                }

                val availableSpace = if (goRight) rightSpace else leftSpace
                // Aim for 60% to 90% of available space (at least 280px if possible)
                val minDistance = minOf(280, availableSpace)
                val maxDistance = availableSpace.coerceAtLeast(minDistance)
                val distance = if (maxDistance > minDistance) {
                    Random.nextInt(minDistance, maxDistance + 1)
                } else {
                    minDistance
                }

                val rawX = if (goRight) currentX + distance else currentX - distance
                rawX.coerceIn(effMin, effMax)
            }

            // 1. Butterfly appears
            onButterflySpawn?.invoke(spawnX.toFloat(), 8f)

            // 2. Cat turns to face butterfly and gets surprised
            val isFacingRight = spawnX > currentX
            currentFacingRight = isFacingRight
            onPositionChanged?.invoke(currentX, currentFacingRight)

            setBehavior(PetBehaviorState.POKE_JUMP)
            delay(BUTTERFLY_SURPRISE_DURATION_MS)

            // 3. Cat runs quickly towards butterfly (sprint)
            setBehavior(PetBehaviorState.RUN)
            val sprintStep = 5
            val sprintDelay = 14L

            val targetPounceX = if (isFacingRight) {
                (spawnX - petWidthPx + 28).coerceIn(effMin, effMax)
            } else {
                (spawnX - 28).coerceIn(effMin, effMax)
            }

            var reachedClose = false
            while (currentX != targetPounceX) {
                if (!reachedClose && kotlin.math.abs(currentX - targetPounceX) <= 15) {
                    reachedClose = true
                    // 4. Cat POUNCES at butterfly!
                    setBehavior(PetBehaviorState.POUNCE)
                    onButterflyFlee?.invoke()
                    delay(750L)
                    break
                }
                currentX = if (isFacingRight) {
                    minOf(currentX + sprintStep, targetPounceX)
                } else {
                    maxOf(currentX - sprintStep, targetPounceX)
                }
                onPositionChanged?.invoke(currentX, isFacingRight)
                delay(sprintDelay)
            }

            if (!reachedClose) {
                setBehavior(PetBehaviorState.POUNCE)
                onButterflyFlee?.invoke()
                delay(750L)
            }

            // 5. Cat sits up scratching its ear in confusion
            setBehavior(PetBehaviorState.CONFUSED)
            delay(2200L)

            // 6. Dismiss butterfly
            onButterflyDismiss?.invoke()

            // 7. Resume normal AI
            launchAiLoop()
        }
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
        private const val CROSS_CUTOUT_CHANCE = 0.65f
        private const val MIN_WALK_DISTANCE_PX = 28
        private const val BUTTERFLY_COOLDOWN_MS = 60_000L
        private const val BUTTERFLY_SPAWN_CHANCE = 0.12f
        private const val BUTTERFLY_SURPRISE_DURATION_MS = 900L
        private const val BUTTERFLY_LOOK_DURATION_MS = 2_200L
    }
}
