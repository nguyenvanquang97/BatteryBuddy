package com.batterybuddy.weather

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    RAIN,
    HEAVY_RAIN,
    WIND,
    STORM,
    SNOW
}

data class WeatherSnapshot(
    val condition: WeatherCondition,
    val weatherCode: Int,
    val temperatureC: Double,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    val windGustKmh: Double,
    val isDay: Boolean,
    val fetchedAtMs: Long
)

object WeatherCodeMapper {
    fun map(
        weatherCode: Int,
        precipitationMm: Double,
        windSpeedKmh: Double,
        windGustKmh: Double
    ): WeatherCondition = when {
        weatherCode in setOf(95, 96, 99) -> WeatherCondition.STORM
        weatherCode in 71..77 || weatherCode in 85..86 -> WeatherCondition.SNOW
        weatherCode in setOf(65, 67, 82) || precipitationMm >= 4.0 ->
            WeatherCondition.HEAVY_RAIN
        weatherCode in 51..67 || weatherCode in 80..82 -> WeatherCondition.RAIN
        windGustKmh >= 40.0 || windSpeedKmh >= 30.0 -> WeatherCondition.WIND
        weatherCode in 1..3 || weatherCode in setOf(45, 48) -> WeatherCondition.CLOUDY
        else -> WeatherCondition.CLEAR
    }
}

object LightningPolicy {
    const val COOLDOWN_MS = 60_000L

    fun strikeChance(condition: WeatherCondition): Float = when (condition) {
        WeatherCondition.STORM -> 0.05f
        WeatherCondition.HEAVY_RAIN -> 0.01f
        WeatherCondition.RAIN,
        WeatherCondition.WIND -> 0.002f
        else -> 0f
    }

    fun shouldStrike(
        condition: WeatherCondition,
        nowMs: Long,
        lastStrikeAtMs: Long,
        randomValue: Float
    ): Boolean {
        if (nowMs - lastStrikeAtMs < COOLDOWN_MS) return false
        return randomValue < strikeChance(condition)
    }
}
