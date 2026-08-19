package com.batterybuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.batterybuddy.event.EventMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "battery_buddy_settings")

data class OverlayPreferences(
    val overlayEnabled: Boolean = false,
    val overlayY: Int = 0,
    val overlaySize: Int = 22,
    val showPercentage: Boolean = true,
    val startOnBoot: Boolean = false,
    val isPetMode: Boolean = true,
    val minX: Int = 10,
    val maxX: Int = 320,
    val weatherEnabled: Boolean = false,
    val weatherLatitudeE6: Int = 10_776_900,
    val weatherLongitudeE6: Int = 106_700_900,
    val eventMode: EventMode = EventMode.AUTO
)

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val KEY_OVERLAY_Y = intPreferencesKey("overlay_y")
        val KEY_OVERLAY_SIZE = intPreferencesKey("overlay_size")
        val KEY_SHOW_PERCENTAGE = booleanPreferencesKey("show_percentage")
        val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val KEY_IS_PET_MODE = booleanPreferencesKey("is_pet_mode")
        val KEY_MIN_X = intPreferencesKey("min_x")
        val KEY_MAX_X = intPreferencesKey("max_x")
        val KEY_WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val KEY_WEATHER_LATITUDE_E6 = intPreferencesKey("weather_latitude_e6")
        val KEY_WEATHER_LONGITUDE_E6 = intPreferencesKey("weather_longitude_e6")
        val KEY_EVENT_MODE = stringPreferencesKey("event_mode")
    }

    val overlayPreferencesFlow: Flow<OverlayPreferences> = context.dataStore.data
        .map { preferences ->
            OverlayPreferences(
                overlayEnabled = preferences[KEY_OVERLAY_ENABLED] ?: false,
                overlayY = preferences[KEY_OVERLAY_Y] ?: 0,
                overlaySize = preferences[KEY_OVERLAY_SIZE] ?: 22,
                showPercentage = preferences[KEY_SHOW_PERCENTAGE] ?: true,
                startOnBoot = preferences[KEY_START_ON_BOOT] ?: false,
                isPetMode = preferences[KEY_IS_PET_MODE] ?: true,
                minX = preferences[KEY_MIN_X] ?: 10,
                maxX = preferences[KEY_MAX_X] ?: 320,
                weatherEnabled = preferences[KEY_WEATHER_ENABLED] ?: false,
                weatherLatitudeE6 = preferences[KEY_WEATHER_LATITUDE_E6] ?: 10_776_900,
                weatherLongitudeE6 = preferences[KEY_WEATHER_LONGITUDE_E6] ?: 106_700_900,
                eventMode = preferences[KEY_EVENT_MODE]
                    ?.let { stored -> runCatching { EventMode.valueOf(stored) }.getOrNull() }
                    ?: EventMode.AUTO
            )
        }

    suspend fun updateOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_OVERLAY_ENABLED] = enabled }
    }

    suspend fun updateOverlayY(y: Int) {
        context.dataStore.edit { preferences -> preferences[KEY_OVERLAY_Y] = y }
    }

    suspend fun updateOverlaySize(size: Int) {
        context.dataStore.edit { preferences -> preferences[KEY_OVERLAY_SIZE] = size }
    }

    suspend fun updateShowPercentage(show: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_SHOW_PERCENTAGE] = show }
    }

    suspend fun updateStartOnBoot(startOnBoot: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_START_ON_BOOT] = startOnBoot }
    }

    suspend fun updateIsPetMode(isPetMode: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_IS_PET_MODE] = isPetMode }
    }

    suspend fun updateMinX(minX: Int) {
        context.dataStore.edit { preferences -> preferences[KEY_MIN_X] = minX }
    }

    suspend fun updateMaxX(maxX: Int) {
        context.dataStore.edit { preferences -> preferences[KEY_MAX_X] = maxX }
    }

    suspend fun updateWeatherEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[KEY_WEATHER_ENABLED] = enabled }
    }

    suspend fun updateWeatherLocation(latitude: Double, longitude: Double) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WEATHER_LATITUDE_E6] = (latitude * 1_000_000).toInt()
            preferences[KEY_WEATHER_LONGITUDE_E6] = (longitude * 1_000_000).toInt()
        }
    }

    suspend fun updateEventMode(mode: EventMode) {
        context.dataStore.edit { preferences -> preferences[KEY_EVENT_MODE] = mode.name }
    }
}
