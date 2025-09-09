package com.example.gamified.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreManager @Inject constructor(
    private val context: Context
) {
    // Keys for settings
    private object PreferencesKeys {
        // AI Monitoring
        val AI_MONITORING_ENABLED = booleanPreferencesKey("ai_monitoring_enabled")
        val SOUND_DETECTION_ENABLED = booleanPreferencesKey("sound_detection_enabled")
        val MOTION_DETECTION_ENABLED = booleanPreferencesKey("motion_detection_enabled")
        val SOUND_SENSITIVITY = intPreferencesKey("sound_sensitivity")
        val MOTION_SENSITIVITY = intPreferencesKey("motion_sensitivity")
        
        // Notifications
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    }

    // AI Monitoring
    val isAIMonitoringEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.AI_MONITORING_ENABLED] ?: false
        }

    val isSoundDetectionEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SOUND_DETECTION_ENABLED] ?: true
        }

    val isMotionDetectionEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MOTION_DETECTION_ENABLED] ?: true
        }

    val soundSensitivity: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SOUND_SENSITIVITY] ?: 70
        }

    val motionSensitivity: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MOTION_SENSITIVITY] ?: 60
        }

    // Notifications
    val areNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
        }

    val isVibrationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true
        }

    val isSoundEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] ?: true
        }

    // Setters
    suspend fun setAIMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_MONITORING_ENABLED] = enabled
        }
    }

    suspend fun setSoundDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_DETECTION_ENABLED] = enabled
        }
    }

    suspend fun setMotionDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOTION_DETECTION_ENABLED] = enabled
        }
    }

    suspend fun setSoundSensitivity(sensitivity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_SENSITIVITY] = sensitivity.coerceIn(0, 100)
        }
    }

    suspend fun setMotionSensitivity(sensitivity: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOTION_SENSITIVITY] = sensitivity.coerceIn(0, 100)
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    // Clear all settings (for testing or logout)
    suspend fun clearAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
