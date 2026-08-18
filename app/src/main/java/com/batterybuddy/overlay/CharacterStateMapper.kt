package com.batterybuddy.overlay

import com.batterybuddy.battery.BatteryState

object CharacterStateMapper {
    fun map(batteryState: BatteryState): CharacterState {
        return when {
            batteryState.isCharging -> CharacterState.CHARGING
            batteryState.percentage >= 80 -> CharacterState.HAPPY
            batteryState.percentage >= 50 -> CharacterState.NORMAL
            batteryState.percentage >= 20 -> CharacterState.TIRED
            else -> CharacterState.CRITICAL
        }
    }
}
