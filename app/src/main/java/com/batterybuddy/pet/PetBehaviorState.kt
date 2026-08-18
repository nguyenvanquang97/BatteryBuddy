package com.batterybuddy.pet

import com.batterybuddy.R

enum class PetBehaviorState(
    val frameResIds: IntArray,
    val badgeText: String,
    val description: String
) {
    WALK(
        intArrayOf(R.drawable.pet_walk_01, R.drawable.pet_walk_02),
        "",
        "Walking around"
    ),
    IDLE(
        intArrayOf(R.drawable.pet_idle_01, R.drawable.pet_idle_02),
        "",
        "Looking around"
    ),
    SIT(
        intArrayOf(R.drawable.pet_sit),
        "",
        "Sitting & resting"
    ),
    SLEEP(
        intArrayOf(R.drawable.pet_sleep_01, R.drawable.pet_sleep_02),
        "Zzz",
        "Sleeping (Low Energy)"
    ),
    CHARGING_HAPPY(
        intArrayOf(R.drawable.pet_charging_01, R.drawable.pet_charging_02),
        "⚡",
        "Recharging energy!"
    )
}
