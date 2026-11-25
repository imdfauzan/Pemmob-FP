package com.lumiposture.app.presentation.session

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.data.repository.SessionRepository
import com.lumiposture.app.data.sensor.EnvironmentSensorManager
import com.lumiposture.app.data.sensor.FaceLandmarkerHelper
import com.lumiposture.app.data.sensor.NoiseLevelSampler
import com.lumiposture.app.domain.engine.AdaptiveInterventionManager
import com.lumiposture.app.domain.engine.SensorFusionEngine
import com.lumiposture.app.domain.model.ActiveAlert
import com.lumiposture.app.domain.model.SensorSnapshot
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import com.lumiposture.app.domain.model.UserPreferences
import com.lumiposture.app.domain.usecase.AnalyzeSessionWithAiUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class ActiveSessionUiState(
    val snapshot: SensorSnapshot = SensorSnapshot(),
    val activeAlert: ActiveAlert = ActiveAlert.None,
    val studyMode: StudyMode = StudyMode.HANDHELD,
    val preferences: UserPreferences = UserPreferences(),
    val showCameraPreview: Boolean = true,
    val showMeshOverlay: Boolean = true,
    val debugTelemetryEnabled: Boolean = false,
    val isEndingSession: Boolean = false,
    val completedSessionId: Long? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val faceLandmarkerHelper: FaceLandmarkerHelper,
    private val environmentSensorManager: EnvironmentSensorManager,
    private val noiseLevelSampler: NoiseLevelSampler,
    private val userDataStore: UserCalibrationDataStore,
    private val sensorFusionEngine: SensorFusionEngine,
    private val interventionManager: AdaptiveInterventionManager,
    private val analyzeSessionUseCase: AnalyzeSessionWithAiUseCase,
    private val sessionRepository: SessionRepository,
    private val vibrator: Vibrator,
) : ViewModel() {

    private val initialModeArg: String = savedStateHandle["mode"] ?: StudyMode.HANDHELD.apiValue
    private val initialMode = StudyMode.entries.firstOrNull { it.apiValue == initialModeArg }
        ?: StudyMode.HANDHELD

    private val studyModeFlow = MutableStateFlow(initialMode)
    private val _uiState = MutableStateFlow(ActiveSessionUiState(studyMode = initialMode))
    val uiState: StateFlow<ActiveSessionUiState> = _uiState.asStateFlow()

    private val sessionStartMillis = System.currentTimeMillis()
    private val telemetryBuffer = mutableListOf<TelemetrySamplePoint>()
    private val brightnessSamples = mutableListOf<Int>()
    private var lastSampleElapsedSec = -10
    private var lastHapticAtMs = 0L
    private var currentPrefs = UserPreferences()
    private var monitoringJob: Job? = null

    init {
        faceLandmarkerHelper.initialize()
        faceLandmarkerHelper.resetSessionCounters()
        sensorFusionEngine.resetSmoothing()
        interventionManager.reset(sessionStartMillis)
        startMonitoring()
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            launch {
                userDataStore.preferencesFlow.collect { prefs ->
                    currentPrefs = prefs
                    _uiState.update {
                        it.copy(
                            preferences = prefs,
                            debugTelemetryEnabled = prefs.debugTelemetryEnabled,
                        )
                    }
                }
            }

            launch {
                // Keep UI clock ticking smoothly even if camera/sensors idle briefly
                while (true) {
                    delay(1000L)
                    val elapsed = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).toInt()
                    _uiState.update {
                        if (it.snapshot.elapsedSeconds < elapsed) {
                            it.copy(snapshot = it.snapshot.copy(elapsedSeconds = elapsed))
                        } else {
                            it
                        }
                    }
                }
            }

            sensorFusionEngine.fuseStream(
                visionFlow = faceLandmarkerHelper.telemetryFlow,
                environmentFlow = environmentSensorManager.observeEnvironmentTelemetry(userDataStore.preferencesFlow),
                noiseDbFlow = noiseLevelSampler.observeNoiseDb(
                    userDataStore.preferencesFlow.map { it.enableMicrophone }.distinctUntilChanged(),
                ),
                studyModeFlow = studyModeFlow,
                preferencesFlow = userDataStore.preferencesFlow,
                sessionStartTimeMs = { sessionStartMillis },
            )
                .sample(200L) // 5 Hz reactive HUD updates
                .collect { snapshot ->
                    val alert = interventionManager.evaluate(snapshot, currentPrefs)
                    maybeTriggerHaptic(alert)
                    recordSampleIfDue(snapshot)

                    _uiState.update { state ->
                        state.copy(
                            snapshot = snapshot,
                            activeAlert = alert,
                            studyMode = snapshot.studyMode,
                        )
                    }
                }
        }
    }

    private fun recordSampleIfDue(snapshot: SensorSnapshot) {
        if (snapshot.elapsedSeconds - lastSampleElapsedSec >= 10) {
            lastSampleElapsedSec = snapshot.elapsedSeconds
            brightnessSamples += snapshot.screenBrightnessPct
            telemetryBuffer += TelemetrySamplePoint(
                elapsedSeconds = snapshot.elapsedSeconds,
                ergonomicScore = snapshot.ergonomicScore,
                blinkRateBpm = snapshot.blinkRateBpm,
                distanceCm = snapshot.distanceCm,
                neckAngleDeg = snapshot.neckAngleDeg,
                ambientLux = snapshot.ambientLux,
                noiseDb = snapshot.noiseDb,
                isViolationActive = snapshot.hasAnyViolation,
            )
        }
    }

    private fun maybeTriggerHaptic(alert: ActiveAlert) {
        if (alert is ActiveAlert.None || !currentPrefs.hapticsEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastHapticAtMs < HAPTIC_COOLDOWN_MS) return
        lastHapticAtMs = now

        runCatching {
            if (!vibrator.hasVibrator()) return
            val duration = if (alert is ActiveAlert.EyeBreakRequired) 240L else 140L
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    fun setStudyMode(mode: StudyMode) {
        studyModeFlow.value = mode
        _uiState.update { it.copy(studyMode = mode) }
    }

    fun toggleCameraPreview() {
        _uiState.update { it.copy(showCameraPreview = !it.showCameraPreview) }
    }

    fun toggleMeshOverlay() {
        _uiState.update { it.copy(showMeshOverlay = !it.showMeshOverlay) }
    }

    fun completeEyeBreak() {
        interventionManager.acknowledgeEyeBreakCompleted()
        _uiState.update { it.copy(activeAlert = ActiveAlert.None) }
    }

    fun snoozeEyeBreak() {
        interventionManager.snoozeEyeBreak()
        _uiState.update { it.copy(activeAlert = ActiveAlert.None) }
    }

    fun endSession() {
        if (_uiState.value.isEndingSession) return
        _uiState.update { it.copy(isEndingSession = true) }
        monitoringJob?.cancel()

        viewModelScope.launch {
            val endMillis = System.currentTimeMillis()
            val durationSec = ((endMillis - sessionStartMillis) / 1000L).toInt().coerceAtLeast(1)
            val latestSnap = _uiState.value.snapshot

            if (telemetryBuffer.isEmpty()) {
                telemetryBuffer += TelemetrySamplePoint(
                    elapsedSeconds = durationSec,
                    ergonomicScore = latestSnap.ergonomicScore,
                    blinkRateBpm = latestSnap.blinkRateBpm,
                    distanceCm = latestSnap.distanceCm,
                    neckAngleDeg = latestSnap.neckAngleDeg,
                    ambientLux = latestSnap.ambientLux,
                    noiseDb = latestSnap.noiseDb,
                    isViolationActive = latestSnap.hasAnyViolation,
                )
            }
            if (brightnessSamples.isEmpty()) {
                brightnessSamples += latestSnap.screenBrightnessPct
            }

            val samplesSnapshot = telemetryBuffer.toList()
            val avgScore = samplesSnapshot.asSequence().map { it.ergonomicScore }.average().roundToInt().coerceIn(0, 100)
            val avgBrightness = brightnessSamples.average().roundToInt().coerceIn(0, 100)
            val mode = studyModeFlow.value

            val aggregate = analyzeSessionUseCase.buildAggregateInput(
                durationSeconds = durationSec,
                studyMode = mode,
                overallScore = avgScore,
                samples = samplesSnapshot,
                avgBrightnessPct = avgBrightness,
            )

            val insight = analyzeSessionUseCase.analyze(aggregate, samplesSnapshot)
            val sessionId = sessionRepository.saveCompletedSession(
                startTimeMillis = sessionStartMillis,
                endTimeMillis = endMillis,
                durationSeconds = durationSec,
                studyMode = mode,
                overallScore = avgScore,
                avgBlinkRate = aggregate.avgBlinkRateBpm,
                avgDistanceCm = aggregate.avgDistanceCm,
                avgNeckAngleDeg = aggregate.avgNeckAngleDeg,
                avgLux = aggregate.avgAmbientLux,
                avgNoiseDb = aggregate.avgNoiseDb,
                totalWarningsTriggered = interventionManager.getTotalWarningsTriggered(),
                samples = samplesSnapshot,
                insight = insight,
            )

            _uiState.update {
                it.copy(
                    isEndingSession = false,
                    completedSessionId = sessionId,
                )
            }
        }
    }

    override fun onCleared() {
        monitoringJob?.cancel()
        faceLandmarkerHelper.close()
    }

    companion object {
        private const val HAPTIC_COOLDOWN_MS = 10_000L
    }
}
