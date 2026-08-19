package com.batterybuddy.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository {

    private var cachedSnapshot: WeatherSnapshot? = null
    private var cachedLatitude = Double.NaN
    private var cachedLongitude = Double.NaN

    suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean = false
    ): WeatherSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot
        val sameLocation = kotlin.math.abs(latitude - cachedLatitude) < 0.01 &&
            kotlin.math.abs(longitude - cachedLongitude) < 0.01
        if (!forceRefresh && cached != null && sameLocation && now - cached.fetchedAtMs < CACHE_MS) {
            return@withContext cached
        }

        val endpoint = String.format(
            Locale.US,
            "%s?latitude=%.5f&longitude=%.5f&current=%s&timezone=auto",
            BASE_URL,
            latitude,
            longitude,
            CURRENT_FIELDS
        )
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode !in 200..299) {
                error("Weather request failed with HTTP ${connection.responseCode}")
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(payload).getJSONObject("current")
            val weatherCode = current.getInt("weather_code")
            val precipitation = current.optDouble("precipitation", 0.0)
            val windSpeed = current.optDouble("wind_speed_10m", 0.0)
            val windGust = current.optDouble("wind_gusts_10m", windSpeed)
            val snapshot = WeatherSnapshot(
                condition = WeatherCodeMapper.map(
                    weatherCode = weatherCode,
                    precipitationMm = precipitation,
                    windSpeedKmh = windSpeed,
                    windGustKmh = windGust
                ),
                weatherCode = weatherCode,
                temperatureC = current.optDouble("temperature_2m", 0.0),
                precipitationMm = precipitation,
                windSpeedKmh = windSpeed,
                windGustKmh = windGust,
                isDay = current.optInt("is_day", 1) == 1,
                fetchedAtMs = now
            )
            cachedSnapshot = snapshot
            cachedLatitude = latitude
            cachedLongitude = longitude
            snapshot
        } catch (error: Exception) {
            cachedSnapshot ?: throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val CURRENT_FIELDS =
            "temperature_2m,precipitation,rain,showers,weather_code,wind_speed_10m,wind_gusts_10m,is_day"
        private const val CACHE_MS = 30 * 60 * 1000L
    }
}
