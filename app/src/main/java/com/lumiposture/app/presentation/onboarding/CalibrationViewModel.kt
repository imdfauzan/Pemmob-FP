package com.lumiposture.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.data.sensor.EnvironmentSensorManager
import com.lumiposture.app.data.sensor.FaceLandmarkerHelper
import com.lumiposture.app.domain.model.SensorCapabilities
import com.lumiposture.app.domain.model.VisionTelemetry
import com.lumiposture.app.domain.usecase.CalculateBaselineCalibrationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalibrationUiState(
    val permissionsGranted: Boolean = false,
    val capabilities: SensorCapabilities = SensorCapabilities(),
    val visionTelemetry: VisionTelemetry = VisionTelemetry(),
    val isCalibrating: Boolean = false,
    val countdownSeconds: Int = 10,
    val collectedSamplesCount: Int = 0,
    val calibrationCompleted: Boolean = false,
    val savedBaselineIpdPx: Float? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    val faceLandmarkerHelper: FaceLandmarkerHelper,
    private val environmentSensorManager: EnvironmentSensorManager,
    private val userDataStore: UserCalibrationDataStore,
    private val calculateBaselineUseCase: CalculateBaselineCalibrationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CalibrationUiState(capabilities = environmentSensorManager.getSensorCapabilities()),
    )
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private var calibrationJob: Job? = null
    private val ipdSamples = mutableListOf<Float>()

    init {
        faceLandmarkerHelper.initialize()
        faceLandmarkerHelper.resetSessionCounters()

        viewModelScope.launch {
            faceLandmarkerHelper.telemetryFlow.collect { vision ->
                _uiState.update { state ->
                    if (state.isCalibrating && (vision.faceDetected && vision.ipdPixels > 10f)) {
                        ipdSamples += vision.ipdPixels
                    }
                    state.copy(
                        visionTelemetry = vision,
                        collectedSamplesCount = ipdSamples.size,
                    )
                }
            }
        }
    }

    fun updatePermissionState(granted: Boolean) {
        _uiState.update {
            it.copy(
                permissionsGranted = granted,
                capabilities = environmentSensorManager.getSensorCapabilities(),
            )
        }
    }

    fun startCalibration() {
        if (_uiState.value.isCalibrating) return
        ipdSamples.clear()
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCalibrating = true,
                    countdownSeconds = 10,
                    collectedSamplesCount = 0,
                    errorMessage = null,
                )
            }
            for (sec in 10 downTo 1) {
                _uiState.update { it.copy(countdownSeconds = sec) }
                delay(1000L)
            }
            _uiState.update { it.copy(countdownSeconds = 0) }

            val medianIpd = calculateBaselineUseCase.computeMedianIpd(ipdSamples.toList())
            if (medianIpd != null) {
                userDataStore.saveCalibrationBaseline(medianIpd)
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        calibrationCompleted = true,
                        savedBaselineIpdPx = medianIpd,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCalibrating = false,
                        errorMessage = "Wajah tidak terdeteksi cukup stabil. Pastikan pencahayaan cukup dan ulangi kalibrasi.",
                    )
                }
            }
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel()
        ipdSamples.clear()
        _uiState.update {
            it.copy(
                isCalibrating = false,
                countdownSeconds = 10,
                collectedSamplesCount = 0,
            )
        }
    }

    override fun onCleared() {
        calibrationJob?.cancel()
        faceLandmarkerHelper.close()
    }
}
