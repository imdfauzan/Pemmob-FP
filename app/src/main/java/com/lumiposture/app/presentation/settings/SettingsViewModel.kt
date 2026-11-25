package com.lumiposture.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.BuildConfig
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.data.sensor.EnvironmentSensorManager
import com.lumiposture.app.domain.model.SensorCapabilities
import com.lumiposture.app.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val capabilities: SensorCapabilities = SensorCapabilities(),
    val isGeminiKeyConfigured: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userDataStore: UserCalibrationDataStore,
    private val sessionRepository: SessionRepository,
    environmentSensorManager: EnvironmentSensorManager,
) : ViewModel() {

    private val caps = environmentSensorManager.getSensorCapabilities()

    val uiState: StateFlow<SettingsUiState> = userDataStore.preferencesFlow
        .map { prefs ->
            SettingsUiState(
                preferences = prefs,
                capabilities = caps,
                isGeminiKeyConfigured = BuildConfig.GEMINI_API_KEY.isNotBlank(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(capabilities = caps),
        )

    fun updateMinDistanceCm(cm: Int) {
        viewModelScope.launch { userDataStore.updateMinDistanceThreshold(cm) }
    }

    fun updateMaxNeckAngleDeg(deg: Int) {
        viewModelScope.launch { userDataStore.updateMaxNeckAngleThreshold(deg) }
    }

    fun updateMinLuxThreshold(lux: Int) {
        viewModelScope.launch { userDataStore.updateMinLuxThreshold(lux) }
    }

    fun updateEyeBreakMinutes(minutes: Int) {
        viewModelScope.launch { userDataStore.updateEyeBreakInterval(minutes) }
    }

    fun setDebugTelemetryEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setDebugTelemetryEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setHapticsEnabled(enabled) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { sessionRepository.clearAllSessions() }
    }
}
