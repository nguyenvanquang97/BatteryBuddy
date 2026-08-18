package com.batterybuddy.battery

data class BatteryState(
    val percentage: Int = 100,
    val isCharging: Boolean = false
)
