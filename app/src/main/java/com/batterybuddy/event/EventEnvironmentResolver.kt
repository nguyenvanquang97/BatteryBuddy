package com.batterybuddy.event

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

data class LunarDate(
    val day: Int,
    val month: Int,
    val year: Int,
    val isLeapMonth: Boolean
)

object EventEnvironmentResolver {

    fun resolve(
        mode: EventMode,
        date: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): EventEnvironment = when (mode) {
        EventMode.DEFAULT -> EventEnvironment.DEFAULT
        EventMode.QIXI -> EventEnvironment.QIXI
        EventMode.NATIONAL_DAY -> EventEnvironment.NATIONAL_DAY
        EventMode.AUTO -> {
            if (date.monthValue == 9 && date.dayOfMonth in 1..3) {
                EventEnvironment.NATIONAL_DAY
            } else {
                val utcOffsetHours = zoneId.rules
                    .getOffset(date.atTime(12, 0))
                    .totalSeconds / 3600.0
                val lunarDate = VietnameseLunarCalendar.fromSolar(date, utcOffsetHours)
                if (lunarDate.day == 7 && lunarDate.month == 7 && !lunarDate.isLeapMonth) {
                    EventEnvironment.QIXI
                } else {
                    EventEnvironment.DEFAULT
                }
            }
        }
    }
}

object VietnameseLunarCalendar {

    fun fromSolar(date: LocalDate, timeZoneHours: Double): LunarDate {
        val dayNumber = julianDay(date.dayOfMonth, date.monthValue, date.year)
        var k = floor((dayNumber - NEW_MOON_BASE) / SYNODIC_MONTH).toInt()
        var monthStart = newMoonDay(k + 1, timeZoneHours)
        if (monthStart > dayNumber) {
            monthStart = newMoonDay(k, timeZoneHours)
        }

        var lunarYear: Int
        var yearStart = lunarMonthEleven(date.year, timeZoneHours)
        var nextYearStart = yearStart
        if (yearStart >= monthStart) {
            lunarYear = date.year
            yearStart = lunarMonthEleven(date.year - 1, timeZoneHours)
        } else {
            lunarYear = date.year + 1
            nextYearStart = lunarMonthEleven(date.year + 1, timeZoneHours)
        }

        val lunarDay = dayNumber - monthStart + 1
        val monthOffset = floor((monthStart - yearStart) / 29.0).toInt()
        var lunarMonth = monthOffset + 11
        var isLeapMonth = false

        if (nextYearStart - yearStart > 365) {
            val leapMonthOffset = leapMonthOffset(yearStart, timeZoneHours)
            if (monthOffset >= leapMonthOffset) {
                lunarMonth = monthOffset + 10
                isLeapMonth = monthOffset == leapMonthOffset
            }
        }

        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && monthOffset < 4) lunarYear -= 1

        return LunarDate(lunarDay, lunarMonth, lunarYear, isLeapMonth)
    }

    private fun julianDay(day: Int, month: Int, year: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        var result = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (result < 2_299_161) {
            result = day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return result
    }

    private fun newMoonDay(k: Int, timeZoneHours: Double): Int =
        floor(newMoon(k) + 0.5 + timeZoneHours / 24.0).toInt()

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val radians = PI / 180.0
        var result = 2_415_020.75933 + SYNODIC_MONTH * k + 0.0001178 * t2 - 0.000000155 * t3
        result += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * radians)

        val sunAnomaly = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val moonAnomaly = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val moonLatitude = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var correction = (0.1734 - 0.000393 * t) * sin(sunAnomaly * radians)
        correction += 0.0021 * sin(2 * sunAnomaly * radians)
        correction -= 0.4068 * sin(moonAnomaly * radians)
        correction += 0.0161 * sin(2 * moonAnomaly * radians)
        correction -= 0.0004 * sin(3 * moonAnomaly * radians)
        correction += 0.0104 * sin(2 * moonLatitude * radians)
        correction -= 0.0051 * sin((sunAnomaly + moonAnomaly) * radians)
        correction -= 0.0074 * sin((sunAnomaly - moonAnomaly) * radians)
        correction += 0.0004 * sin((2 * moonLatitude + sunAnomaly) * radians)
        correction -= 0.0004 * sin((2 * moonLatitude - sunAnomaly) * radians)
        correction -= 0.0006 * sin((2 * moonLatitude + moonAnomaly) * radians)
        correction += 0.0010 * sin((2 * moonLatitude - moonAnomaly) * radians)
        correction += 0.0005 * sin((2 * moonAnomaly + sunAnomaly) * radians)

        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return result + correction - deltaT
    }

    private fun sunLongitude(dayNumber: Int, timeZoneHours: Double): Int {
        val t = (dayNumber - 2_451_545.5 - timeZoneHours / 24.0) / 36_525.0
        val t2 = t * t
        val radians = PI / 180.0
        val meanAnomaly = 357.52910 + 35_999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val meanLongitude = 280.46645 + 36_000.76983 * t + 0.0003032 * t2
        var delta = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(radians * meanAnomaly)
        delta += (0.019993 - 0.000101 * t) * sin(2 * radians * meanAnomaly)
        delta += 0.000290 * sin(3 * radians * meanAnomaly)
        var longitude = (meanLongitude + delta) * radians
        longitude -= 2 * PI * floor(longitude / (2 * PI))
        return floor(longitude / PI * 6).toInt()
    }

    private fun lunarMonthEleven(year: Int, timeZoneHours: Double): Int {
        val offset = julianDay(31, 12, year) - 2_415_021
        val k = floor(offset / SYNODIC_MONTH).toInt()
        var newMoon = newMoonDay(k, timeZoneHours)
        if (sunLongitude(newMoon, timeZoneHours) >= 9) {
            newMoon = newMoonDay(k - 1, timeZoneHours)
        }
        return newMoon
    }

    private fun leapMonthOffset(yearStart: Int, timeZoneHours: Double): Int {
        val k = floor((yearStart - NEW_MOON_BASE) / SYNODIC_MONTH + 0.5).toInt()
        var lastLongitude = sunLongitude(newMoonDay(k + 1, timeZoneHours), timeZoneHours)
        var index = 2
        var currentLongitude: Int
        do {
            currentLongitude = sunLongitude(newMoonDay(k + index, timeZoneHours), timeZoneHours)
            if (currentLongitude == lastLongitude) break
            lastLongitude = currentLongitude
            index++
        } while (index < 14)
        return index - 1
    }

    private const val NEW_MOON_BASE = 2_415_021.076998695
    private const val SYNODIC_MONTH = 29.530588853
}
