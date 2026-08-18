package com.batterybuddy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
    val maxX: Int = 320
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
                maxX = preferences[KEY_MAX_X] ?: 320
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
}
