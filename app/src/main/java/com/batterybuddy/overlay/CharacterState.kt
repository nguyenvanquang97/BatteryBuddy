package com.batterybuddy.overlay

enum class CharacterState(val defaultEmoji: String, val description: String) {
    CHARGING("⚡🐱", "Charging"),
    HAPPY("😺", "Happy (≥80%)"),
    NORMAL("🐱", "Normal (≥50%)"),
    TIRED("😿", "Tired (≥20%)"),
    CRITICAL("🙀", "Critical (<20%)")
}
