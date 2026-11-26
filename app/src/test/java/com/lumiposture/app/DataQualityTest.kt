package com.lumiposture.app

import com.lumiposture.app.domain.engine.*
import com.lumiposture.app.domain.model.*
import com.lumiposture.app.domain.usecase.CalculateBaselineCalibrationUseCase
import com.lumiposture.app.data.remote.dto.GeminiMetricsSummaryDto
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent

class DataQualityTest {
    private val fusion = SensorFusionEngine(ErgonomicScoreCalculator())
    private val prefs = UserPreferences(baselineIpdPx = 100f, isCalibrated = true)
    private fun tick(vision: VisionTelemetry, env: EnvironmentTelemetry = EnvironmentTelemetry(), p: UserPreferences = prefs, now: Long = 10000) =
        fusion.fuseSingleTick(vision, env, NoiseTelemetry(), StudyMode.HANDHELD, p, 0, now)
    private fun face() = VisionTelemetry(faceDetected = true, status = DataStatus.AVAILABLE, ipdPixels = 100f, timestampMs = 10000)
    private fun point(second: Int, distance: Float? = null, lux: Float? = null) =
        TelemetrySamplePoint(second, null, null, distance, null, lux, null, false)

    @Test fun emptySourcesCannotProduceGoodScore() {
        val snap = tick(VisionTelemetry())
        assertNull(snap.ergonomicScore)
        assertNull(snap.distanceCm)
        assertNull(snap.ambientLux)
        assertEquals(MetricStatus.UNAVAILABLE, snap.lightingStatus)
    }
    @Test fun scoreRenormalizesOnlyMeasuredComponents() {
        val result = ErgonomicScoreCalculator().calculate(null, 0f, 30f, false, null, false, null, 50, -20f, prefs)
        assertEquals(100, result.overallScore)
        assertEquals(MetricStatus.UNAVAILABLE, result.blinkStatus)
        assertEquals(MetricStatus.UNAVAILABLE, result.noiseStatus)
    }
    @Test fun uncalibratedFaceCannotProduceDistance() {
        val snap = tick(face(), p = prefs.copy(isCalibrated = false))
        assertNull(snap.distanceCm)
        assertEquals(DataStatus.NEEDS_CALIBRATION, snap.distanceDataStatus)
    }
    @Test fun staleFaceAndMotionAreDiscarded() {
        val snap = tick(face(), EnvironmentTelemetry(motionStatus = DataStatus.AVAILABLE, motionTimestampMs = 10000, devicePitchDeg = 40f), now = 14000)
        assertNull(snap.distanceCm)
        assertNull(snap.neckAngleDeg)
        assertEquals(DataStatus.STALE, snap.cameraDataStatus)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun freshEventBetweenClockTicksIsNotMarkedStale() = runTest {
        var now = 10000L
        val vision = MutableStateFlow(face())
        val snapshots = mutableListOf<SensorSnapshot>()
        backgroundScope.launch {
            fusion.fuseStream(vision, MutableStateFlow(EnvironmentTelemetry()), MutableStateFlow(NoiseTelemetry()),
                MutableStateFlow(StudyMode.HANDHELD), MutableStateFlow(prefs), { 0L }, { now })
                .collect { snapshots += it }
        }
        runCurrent()
        now = 10001L
        vision.value = face().copy(timestampMs = now)
        runCurrent()
        assertEquals(DataStatus.AVAILABLE, snapshots.last().cameraDataStatus)
        assertEquals(30f, requireNotNull(snapshots.last().distanceCm), 0.01f)
    }
    @Test fun lostFaceDoesNotReuseLastDistance() {
        assertEquals(30f, requireNotNull(tick(face()).distanceCm), 0.01f)
        assertNull(tick(face().copy(faceDetected = false, status = DataStatus.NO_FACE)).distanceCm)
        assertEquals(15f, requireNotNull(tick(face().copy(ipdPixels = 200f)).distanceCm), 0.01f)
    }
    @Test fun blinkWaitsForObservationWindow() {
        assertNull(tick(face().copy(blinkRateBpm = 0f)).blinkRateBpm)
        assertEquals(0f, requireNotNull(tick(face().copy(blinkRateBpm = 0f, blinkWindowReady = true)).blinkRateBpm), 0f)
    }
    @Test fun unsupportedAndDeniedRemainDistinct() {
        val snap = tick(VisionTelemetry(status = DataStatus.PERMISSION_DENIED), EnvironmentTelemetry(lightStatus = DataStatus.UNSUPPORTED))
        assertEquals(DataStatus.PERMISSION_DENIED, snap.cameraDataStatus)
        assertEquals(DataStatus.UNSUPPORTED, snap.lightDataStatus)
        assertNull(snap.ergonomicScore)
    }
    @Test fun shortSessionCannotHaveTenSecondsOfViolation() {
        val result = SessionAggregator().aggregate(3, StudyMode.HANDHELD, null, listOf(point(0, 20f)), 40)
        assertEquals(3, result.tooCloseDurationSec)
    }
    @Test fun gapsAndUnavailableSamplesDoNotCountAsViolations() {
        val result = SessionAggregator().aggregate(35, StudyMode.HANDHELD, null,
            listOf(point(0, 20f, 100f), point(10), point(30, 20f, 300f)), null)
        assertEquals(15, result.tooCloseDurationSec)
        assertEquals(20f, requireNotNull(result.avgDistanceCm), 0f)
        assertEquals(200f, requireNotNull(result.avgAmbientLux), 0f)
        assertNull(result.avgNoiseDb)
        assertNull(result.avgBlinkRateBpm)
    }
    @Test fun emptyAggregateAndFallbackDoNotClaimGoodConditions() {
        val aggregate = SessionAggregator().aggregate(1, StudyMode.HANDHELD, null, emptyList(), null)
        val insight = LocalRuleBasedInsightEngine().generate(aggregate)
        assertNull(aggregate.avgDistanceCm)
        assertEquals("Data belum cukup", insight.statusLabel)
        assertFalse(insight.isGeneratedByCloudAi)
    }
    @Test fun dimLightWithDimScreenStillRequiresAttention() {
        val aggregate = SessionAggregateInput(120, StudyMode.HANDHELD, 54,
            15f, 12f, 30f, 0, 10f, 0, 16f, 40, -40f, -30f)
        val insight = LocalRuleBasedInsightEngine().generate(aggregate)
        assertEquals("Perlu perhatian", insight.statusLabel)
        assertTrue(insight.sensorCorrelations.any { it.contains("Cahaya terukur") })
    }
    @Test fun calibrationRejectsTooFewInvalidAndUnstableSamples() {
        val calibrate = CalculateBaselineCalibrationUseCase()
        assertNull(calibrate.computeMedianIpd(listOf(100f, Float.NaN, Float.POSITIVE_INFINITY)))
        assertNull(calibrate.computeMedianIpd(List(19) { 100f }))
        assertNull(calibrate.computeMedianIpd(List(20) { if (it % 2 == 0) 80f else 140f }))
        assertEquals(100f, requireNotNull(calibrate.computeMedianIpd(List(30) { 100f })), 0f)
    }
    @Test fun missingDataResetsDistancePersistence() {
        val manager = AdaptiveInterventionManager().apply { reset(0) }
        val close = SensorSnapshot(timestampMs = 1000, distanceCm = 20f)
        assertEquals(ActiveAlert.None, manager.evaluate(close, prefs))
        assertEquals(ActiveAlert.None, manager.evaluate(close.copy(timestampMs = 5000, distanceCm = null), prefs))
        assertEquals(ActiveAlert.None, manager.evaluate(close.copy(timestampMs = 6100), prefs))
    }
    @Test fun scheduledBreakWorksWithoutCameraAndSnoozes() {
        val manager = AdaptiveInterventionManager().apply { reset(0) }
        assertTrue(manager.evaluate(SensorSnapshot(timestampMs = 1200000), prefs) is ActiveAlert.EyeBreakRequired)
        manager.snoozeEyeBreak(1200000)
        assertEquals(ActiveAlert.None, manager.evaluate(SensorSnapshot(timestampMs = 1499999), prefs))
        assertTrue(manager.evaluate(SensorSnapshot(timestampMs = 1500000), prefs) is ActiveAlert.EyeBreakRequired)
    }
    @OptIn(ExperimentalSerializationApi::class)
    @Test fun cloudMetricsOmitUnavailableMeasurements() {
        val dto = GeminiMetricsSummaryDto(null, null, null, 0, null, 0, 200f, null, null, null)
        val text = Json { explicitNulls = false }.encodeToString(dto)
        assertFalse(text.contains("avg_distance_cm"))
        assertFalse(text.contains("avg_microphone_dbfs"))
        assertTrue(text.contains("avg_ambient_lux"))
    }
}
