package com.batterybuddy.overlay

import com.batterybuddy.battery.BatteryState
import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterStateMapperTest {

    @Test
    fun testChargingStateHasHighestPriority() {
        val batteryState = BatteryState(percentage = 10, isCharging = true)
        val result = CharacterStateMapper.map(batteryState)
        assertEquals(CharacterState.CHARGING, result)
    }

    @Test
    fun testHappyStateBoundary() {
        val happyState = BatteryState(percentage = 80, isCharging = false)
        assertEquals(CharacterState.HAPPY, CharacterStateMapper.map(happyState))

        val happy100State = BatteryState(percentage = 100, isCharging = false)
        assertEquals(CharacterState.HAPPY, CharacterStateMapper.map(happy100State))
    }

    @Test
    fun testNormalStateBoundary() {
        val normalState = BatteryState(percentage = 50, isCharging = false)
        assertEquals(CharacterState.NORMAL, CharacterStateMapper.map(normalState))

        val normalUpperState = BatteryState(percentage = 79, isCharging = false)
        assertEquals(CharacterState.NORMAL, CharacterStateMapper.map(normalUpperState))
    }

    @Test
    fun testTiredStateBoundary() {
        val tiredState = BatteryState(percentage = 20, isCharging = false)
        assertEquals(CharacterState.TIRED, CharacterStateMapper.map(tiredState))

        val tiredUpperState = BatteryState(percentage = 49, isCharging = false)
        assertEquals(CharacterState.TIRED, CharacterStateMapper.map(tiredUpperState))
    }

    @Test
    fun testCriticalStateBoundary() {
        val criticalState = BatteryState(percentage = 19, isCharging = false)
        assertEquals(CharacterState.CRITICAL, CharacterStateMapper.map(criticalState))

        val zeroState = BatteryState(percentage = 0, isCharging = false)
        assertEquals(CharacterState.CRITICAL, CharacterStateMapper.map(zeroState))
    }
}
