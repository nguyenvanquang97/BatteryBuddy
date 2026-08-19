package com.batterybuddy.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherModelsTest {

    @Test
    fun `WMO thunderstorm maps to storm`() {
        assertEquals(
            WeatherCondition.STORM,
            WeatherCodeMapper.map(95, 2.0, 20.0, 35.0)
        )
    }

    @Test
    fun `strong wind overrides otherwise clear weather`() {
        assertEquals(
            WeatherCondition.WIND,
            WeatherCodeMapper.map(0, 0.0, 31.0, 42.0)
        )
    }

    @Test
    fun `lightning respects one minute cooldown`() {
        val firstStrike = LightningPolicy.shouldStrike(
            condition = WeatherCondition.STORM,
            nowMs = 120_000L,
            lastStrikeAtMs = 0L,
            randomValue = 0f
        )
        val blockedStrike = LightningPolicy.shouldStrike(
            condition = WeatherCondition.STORM,
            nowMs = 150_000L,
            lastStrikeAtMs = 120_000L,
            randomValue = 0f
        )
        val nextStrike = LightningPolicy.shouldStrike(
            condition = WeatherCondition.STORM,
            nowMs = 180_000L,
            lastStrikeAtMs = 120_000L,
            randomValue = 0f
        )

        assertTrue(firstStrike)
        assertFalse(blockedStrike)
        assertTrue(nextStrike)
    }

    @Test
    fun `lightning chances are visible enough for rainy weather`() {
        assertEquals(0.04f, LightningPolicy.strikeChance(WeatherCondition.RAIN))
        assertEquals(0.10f, LightningPolicy.strikeChance(WeatherCondition.HEAVY_RAIN))
        assertEquals(0.25f, LightningPolicy.strikeChance(WeatherCondition.STORM))
    }
}
