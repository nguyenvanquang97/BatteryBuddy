package com.batterybuddy.pet

import com.batterybuddy.R

enum class PetBehaviorState(
    val frameResIds: IntArray,
    val badgeText: String,
    val description: String
) {
    WALK(
        intArrayOf(
            R.drawable.pet_walk_v2_01,
            R.drawable.pet_walk_v2_02,
            R.drawable.pet_walk_v2_03,
            R.drawable.pet_walk_v2_04,
            R.drawable.pet_walk_v2_05
        ),
        "",
        "Walking around"
    ),
    RUN(
        intArrayOf(
            R.drawable.pet_run_01,
            R.drawable.pet_run_02,
            R.drawable.pet_run_03,
            R.drawable.pet_run_04
        ),
        "",
        "Running fast"
    ),
    IDLE(
        intArrayOf(
            R.drawable.pet_idle_v2_01,
            R.drawable.pet_idle_v2_02,
            R.drawable.pet_idle_v2_03,
            R.drawable.pet_idle_v2_04
        ),
        "",
        "Looking around"
    ),
    SIT(
        intArrayOf(
            R.drawable.pet_sit_v2_01,
            R.drawable.pet_sit_v2_02,
            R.drawable.pet_sit_v2_03,
            R.drawable.pet_sit_v2_04
        ),
        "",
        "Sitting & resting"
    ),
    SIT_DOWN(
        intArrayOf(
            R.drawable.pet_sit_down_01,
            R.drawable.pet_sit_down_02,
            R.drawable.pet_sit_down_03,
            R.drawable.pet_sit_down_04
        ),
        "",
        "Settling down"
    ),
    LOOK_FRONT(
        intArrayOf(
            R.drawable.pet_look_front_01,
            R.drawable.pet_look_front_02,
            R.drawable.pet_look_front_03,
            R.drawable.pet_look_front_04
        ),
        "",
        "Greeting you"
    ),
    SLEEP(
        intArrayOf(
            R.drawable.pet_sleep_v2_01,
            R.drawable.pet_sleep_v2_02,
            R.drawable.pet_sleep_v2_03,
            R.drawable.pet_sleep_v2_04
        ),
        "Zzz",
        "Sleeping (Low Energy)"
    ),
    CHARGING_HAPPY(
        intArrayOf(
            R.drawable.pet_charging_v2_01,
            R.drawable.pet_charging_v2_02,
            R.drawable.pet_charging_v2_03,
            R.drawable.pet_charging_v2_04
        ),
        "⚡",
        "Recharging energy!"
    ),
    DRINK_START(
        intArrayOf(
            R.drawable.pet_drink_start_01,
            R.drawable.pet_drink_start_02,
            R.drawable.pet_drink_start_03,
            R.drawable.pet_drink_start_04
        ),
        "⚡",
        "Getting ready to drink"
    ),
    DRINK_MILK(
        intArrayOf(
            R.drawable.pet_drink_milk_01,
            R.drawable.pet_drink_milk_02,
            R.drawable.pet_drink_milk_03,
            R.drawable.pet_drink_milk_04
        ),
        "⚡",
        "Drinking milk while charging"
    ),
    LIGHTNING_HIT(
        intArrayOf(
            R.drawable.pet_lightning_hit_01,
            R.drawable.pet_lightning_hit_02,
            R.drawable.pet_lightning_hit_03,
            R.drawable.pet_lightning_hit_04
        ),
        "!",
        "Struck by lightning"
    ),
    SHOCKED(
        intArrayOf(
            R.drawable.pet_shocked_01,
            R.drawable.pet_shocked_02,
            R.drawable.pet_shocked_03,
            R.drawable.pet_shocked_04
        ),
        "",
        "Recovering from a shock"
    ),
    POKE_JUMP(
        intArrayOf(
            R.drawable.pet_poke_jump_01,
            R.drawable.pet_poke_jump_02,
            R.drawable.pet_poke_jump_03,
            R.drawable.pet_poke_jump_04
        ),
        "!",
        "Startled by a poke"
    ),
    ANGRY_LOOK(
        intArrayOf(
            R.drawable.pet_angry_look_01,
            R.drawable.pet_angry_look_02,
            R.drawable.pet_angry_look_03,
            R.drawable.pet_angry_look_04
        ),
        "",
        "Staring back angrily"
    ),
    POUNCE(
        intArrayOf(
            R.drawable.pet_pounce_01,
            R.drawable.pet_pounce_02,
            R.drawable.pet_pounce_03,
            R.drawable.pet_pounce_04
        ),
        "!",
        "Pouncing at butterfly"
    ),
    CONFUSED(
        intArrayOf(
            R.drawable.pet_confused_01,
            R.drawable.pet_confused_02
        ),
        "?",
        "Puzzled and confused"
    )
}
