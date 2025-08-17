package com.example.litechat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for managing app settings and preferences
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        private val HARDWARE_ACCELERATION_KEY = stringPreferencesKey("hardware_acceleration")
        private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
        private val TOP_K_KEY = intPreferencesKey("top_k")
        private val TOP_P_KEY = floatPreferencesKey("top_p")
        private val HUGGINGFACE_ACCESS_TOKEN_KEY = stringPreferencesKey("huggingface_access_token")
        private val SHOW_PERFORMANCE_METRICS_KEY = booleanPreferencesKey("show_performance_metrics")
    }
    
    /**
     * Get current app settings
     */
    val appSettings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            hardwareAcceleration = preferences[HARDWARE_ACCELERATION_KEY]?.let { 
                HardwareAcceleration.valueOf(it) 
            } ?: HardwareAcceleration.GPU,
            temperature = preferences[TEMPERATURE_KEY] ?: 0.7f,
            topK = preferences[TOP_K_KEY] ?: 40,
            topP = preferences[TOP_P_KEY] ?: 0.95f,
            huggingFaceAccessToken = preferences[HUGGINGFACE_ACCESS_TOKEN_KEY] ?: ModelConstants.DEFAULT_HUGGINGFACE_TOKEN,
            showPerformanceMetrics = preferences[SHOW_PERFORMANCE_METRICS_KEY] ?: true
        )
    }
    
    /**
     * Update hardware acceleration setting
     */
    suspend fun updateHardwareAcceleration(acceleration: HardwareAcceleration) {
        context.dataStore.edit { preferences ->
            preferences[HARDWARE_ACCELERATION_KEY] = acceleration.name
        }
    }
    
    /**
     * Update temperature setting
     */
    suspend fun updateTemperature(temperature: Float) {
        context.dataStore.edit { preferences ->
            preferences[TEMPERATURE_KEY] = temperature
        }
    }
    

    

    
    /**
     * Update top-k setting
     */
    suspend fun updateTopK(topK: Int) {
        context.dataStore.edit { preferences ->
            preferences[TOP_K_KEY] = topK
        }
    }
    
    /**
     * Update top-p setting
     */
    suspend fun updateTopP(topP: Float) {
        context.dataStore.edit { preferences ->
            preferences[TOP_P_KEY] = topP
        }
    }
    
    /**
     * Update HuggingFace access token
     */
    suspend fun updateHuggingFaceAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[HUGGINGFACE_ACCESS_TOKEN_KEY] = token
        }
    }
    
    /**
     * Update show performance metrics setting
     */
    suspend fun updateShowPerformanceMetrics(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_PERFORMANCE_METRICS_KEY] = show
        }
    }
    
    /**
     * Update all settings at once
     */
    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[HARDWARE_ACCELERATION_KEY] = settings.hardwareAcceleration.name
            preferences[TEMPERATURE_KEY] = settings.temperature
            preferences[TOP_K_KEY] = settings.topK
            preferences[TOP_P_KEY] = settings.topP
            preferences[HUGGINGFACE_ACCESS_TOKEN_KEY] = settings.huggingFaceAccessToken
            preferences[SHOW_PERFORMANCE_METRICS_KEY] = settings.showPerformanceMetrics
        }
    }
}
