package com.batterybuddy.event

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventEnvironmentResolverTest {

    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `auto mode resolves Qixi on lunar seventh day of seventh month`() {
        listOf(
            LocalDate.of(2024, 8, 10),
            LocalDate.of(2025, 8, 29),
            LocalDate.of(2026, 8, 19)
        ).forEach { date ->
            assertEquals(
                "Expected Qixi on $date",
                EventEnvironment.QIXI,
                EventEnvironmentResolver.resolve(EventMode.AUTO, date, vietnamZone)
            )
        }
    }

    @Test
    fun `auto mode falls back to default outside Qixi`() {
        assertEquals(
            EventEnvironment.DEFAULT,
            EventEnvironmentResolver.resolve(
                EventMode.AUTO,
                LocalDate.of(2026, 8, 20),
                vietnamZone
            )
        )
    }

    @Test
    fun `manual modes ignore calendar date`() {
        val ordinaryDate = LocalDate.of(2026, 3, 1)
        assertEquals(
            EventEnvironment.DEFAULT,
            EventEnvironmentResolver.resolve(EventMode.DEFAULT, ordinaryDate, vietnamZone)
        )
        assertEquals(
            EventEnvironment.QIXI,
            EventEnvironmentResolver.resolve(EventMode.QIXI, ordinaryDate, vietnamZone)
        )
    }

    @Test
    fun `lunar converter recognizes Vietnamese new year`() {
        val lunarDate = VietnameseLunarCalendar.fromSolar(
            LocalDate.of(2026, 2, 17),
            7.0
        )
        assertEquals(LunarDate(1, 1, 2026, false), lunarDate)
    }
}
