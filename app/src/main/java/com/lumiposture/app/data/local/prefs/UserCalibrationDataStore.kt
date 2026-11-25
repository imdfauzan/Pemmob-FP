package com.lumiposture.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lumiposture.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lumiDataStore: DataStore<Preferences> by preferencesDataStore(name = "lumiposture_prefs")

@Singleton
class UserCalibrationDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val BASELINE_IPD_PX = floatPreferencesKey("baseline_ipd_px")
        val IS_CALIBRATED = booleanPreferencesKey("is_calibrated")
        val MIN_DISTANCE_CM = intPreferencesKey("min_distance_cm")
        val MAX_NECK_ANGLE_DEG = intPreferencesKey("max_neck_angle_deg")
        val MIN_LUX_THRESHOLD = intPreferencesKey("min_lux_threshold")
        val EYE_BREAK_MINUTES = intPreferencesKey("eye_break_minutes")
        val DEBUG_TELEMETRY = booleanPreferencesKey("debug_telemetry_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    val preferencesFlow: Flow<UserPreferences> = context.lumiDataStore.data.map { prefs ->
        UserPreferences(
            baselineIpdPx = prefs[Keys.BASELINE_IPD_PX] ?: 0f,
            isCalibrated = prefs[Keys.IS_CALIBRATED] ?: false,
            minDistanceThresholdCm = prefs[Keys.MIN_DISTANCE_CM] ?: 25,
            maxNeckAngleThresholdDeg = prefs[Keys.MAX_NECK_ANGLE_DEG] ?: 30,
            minLuxThreshold = prefs[Keys.MIN_LUX_THRESHOLD] ?: 80,
            eyeBreakIntervalMinutes = prefs[Keys.EYE_BREAK_MINUTES] ?: 20,
            debugTelemetryEnabled = prefs[Keys.DEBUG_TELEMETRY] ?: false,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY] ?: "",
        )
    }

    suspend fun saveCalibrationBaseline(ipdPx: Float) {
        context.lumiDataStore.edit { prefs ->
            prefs[Keys.BASELINE_IPD_PX] = ipdPx
            prefs[Keys.IS_CALIBRATED] = true
        }
    }

    @Suppress("unused")
    suspend fun resetCalibration() {
        context.lumiDataStore.edit { prefs ->
            prefs[Keys.BASELINE_IPD_PX] = 0f
            prefs[Keys.IS_CALIBRATED] = false
        }
    }

    suspend fun updateMinDistanceThreshold(cm: Int) {
        context.lumiDataStore.edit { it[Keys.MIN_DISTANCE_CM] = cm.coerceIn(15, 40) }
    }

    suspend fun updateMaxNeckAngleThreshold(deg: Int) {
        context.lumiDataStore.edit { it[Keys.MAX_NECK_ANGLE_DEG] = deg.coerceIn(20, 45) }
    }

    suspend fun updateMinLuxThreshold(lux: Int) {
        context.lumiDataStore.edit { it[Keys.MIN_LUX_THRESHOLD] = lux.coerceIn(40, 200) }
    }

    suspend fun updateEyeBreakInterval(minutes: Int) {
        context.lumiDataStore.edit { it[Keys.EYE_BREAK_MINUTES] = minutes.coerceIn(5, 45) }
    }

    suspend fun setDebugTelemetryEnabled(enabled: Boolean) {
        context.lumiDataStore.edit { it[Keys.DEBUG_TELEMETRY] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.lumiDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        context.lumiDataStore.edit { it[Keys.GEMINI_API_KEY] = apiKey.trim() }
    }
}
