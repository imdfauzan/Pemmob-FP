package com.lumiposture.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isGeminiKeyConfigured: Boolean = false,
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
                isGeminiKeyConfigured = prefs.geminiApiKey.isNotBlank(),
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

    fun setDebugTelemetryEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setDebugTelemetryEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setHapticsEnabled(enabled) }
    }

    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch { userDataStore.updateGeminiApiKey(apiKey) }
    }

    fun clearGeminiApiKey() {
        viewModelScope.launch { userDataStore.updateGeminiApiKey("") }
    }

    fun setFrontCameraEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setFrontCameraEnabled(enabled) }
    }

    fun setLightSensorEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setLightSensorEnabled(enabled) }
    }

    fun setProximitySensorEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setProximitySensorEnabled(enabled) }
    }

    fun setAccelerometerEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setAccelerometerEnabled(enabled) }
    }

    fun setGyroscopeEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setGyroscopeEnabled(enabled) }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        viewModelScope.launch { userDataStore.setMicrophoneEnabled(enabled) }
    }

    fun clearAllHistory() {
        viewModelScope.launch { sessionRepository.clearAllSessions() }
    }
}
