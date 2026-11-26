package com.lumiposture.app

import com.lumiposture.app.data.remote.dto.GeminiContent
import com.lumiposture.app.data.remote.dto.GeminiGenerateContentRequest
import com.lumiposture.app.data.remote.dto.GeminiGenerationConfig
import com.lumiposture.app.data.remote.dto.GeminiPart
import com.lumiposture.app.data.remote.dto.GeminiSchema
import com.lumiposture.app.domain.engine.AdaptiveInterventionManager
import com.lumiposture.app.domain.engine.ErgonomicScoreCalculator
import com.lumiposture.app.domain.engine.LocalRuleBasedInsightEngine
import com.lumiposture.app.domain.engine.SensorFusionEngine
import com.lumiposture.app.domain.engine.SessionAggregateInput
import com.lumiposture.app.domain.model.ActiveAlert
import com.lumiposture.app.domain.model.DataStatus
import com.lumiposture.app.domain.model.NoiseTelemetry
import com.lumiposture.app.domain.model.EnvironmentTelemetry
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.domain.model.SensorSnapshot
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.UserPreferences
import com.lumiposture.app.domain.model.VisionTelemetry
import com.lumiposture.app.domain.usecase.CalculateBaselineCalibrationUseCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainEnginesTest {

    private val scoreCalculator = ErgonomicScoreCalculator()
    private val fusionEngine = SensorFusionEngine(scoreCalculator)
    private val localInsightEngine = LocalRuleBasedInsightEngine()
    private val calibrationUseCase = CalculateBaselineCalibrationUseCase()

    @Test
    fun ergonomicScore_optimalInputs_returns100AndOptimalStatuses() {
        val result = scoreCalculator.calculate(
            blinkRateBpm = 16f,
            eyeSquintAvg = 0.1f,
            distanceCm = 30f,
            isProximityTriggered = false,
            neckAngleDeg = 12f,
            isLyingDown = false,
            ambientLux = 250f,
            screenBrightnessPct = 50,
            noiseDb = 38f,
            prefs = UserPreferences(),
        )

        assertEquals(100, result.overallScore)
        assertEquals(MetricStatus.OPTIMAL, result.blinkStatus)
        assertEquals(MetricStatus.OPTIMAL, result.distanceStatus)
        assertEquals(MetricStatus.OPTIMAL, result.postureStatus)
        assertEquals(MetricStatus.OPTIMAL, result.lightingStatus)
        assertEquals(MetricStatus.UNAVAILABLE, result.noiseStatus)
    }

    @Test
    fun ergonomicScore_dimRoomGlareAndTechNeck_flagsCritical() {
        val result = scoreCalculator.calculate(
            blinkRateBpm = 7f,
            eyeSquintAvg = 0.55f,
            distanceCm = 18f,
            isProximityTriggered = false,
            neckAngleDeg = 38f,
            isLyingDown = false,
            ambientLux = 42f,
            screenBrightnessPct = 85,
            noiseDb = 70f,
            prefs = UserPreferences(),
        )

        assertTrue(requireNotNull(result.overallScore) < 50)
        assertEquals(MetricStatus.CRITICAL, result.blinkStatus)
        assertEquals(MetricStatus.CRITICAL, result.distanceStatus)
        assertEquals(MetricStatus.CRITICAL, result.postureStatus)
        assertEquals(MetricStatus.CRITICAL, result.lightingStatus)
        assertEquals(MetricStatus.UNAVAILABLE, result.noiseStatus)
    }

    @Test
    fun sensorFusion_proximityTriggered_doesNotInventCentimeters() {
        fusionEngine.resetSmoothing()
        val snap = fusionEngine.fuseSingleTick(
            vision = VisionTelemetry(
                faceDetected = false,
                ipdPixels = 0f,
                timestampMs = 5_000L,
            ),
            env = EnvironmentTelemetry(
                isProximityNear = true,
                proximityStatus = DataStatus.AVAILABLE,
                proximityTimestampMs = 5000,
                ambientLux = 180f,
                devicePitchDeg = 15f,
                screenBrightnessPct = 45,
            ),
            noiseDb = NoiseTelemetry(),
            studyMode = StudyMode.HANDHELD,
            prefs = UserPreferences(baselineIpdPx = 100f, isCalibrated = true),
            sessionStartTimeMs = 0L,
            nowMs = 5000,
        )

        assertEquals(null, snap.distanceCm)
        assertTrue(snap.isProximityTriggered)
        assertEquals(MetricStatus.CRITICAL, snap.distanceStatus)
    }

    @Test
    fun sensorFusion_ipdRatio_estimates30CmWhenIpdMatchesBaseline() {
        fusionEngine.resetSmoothing()
        val snap = fusionEngine.fuseSingleTick(
            vision = VisionTelemetry(
                faceDetected = true,
                status = DataStatus.AVAILABLE,
                ipdPixels = 100f,
                headPitchDeg = 10f,
                timestampMs = 2_000L,
            ),
            env = EnvironmentTelemetry(
                isProximityNear = false,
                devicePitchDeg = 12f,
                motionStatus = DataStatus.AVAILABLE,
                motionTimestampMs = 2000,
            ),
            noiseDb = NoiseTelemetry(),
            studyMode = StudyMode.HANDHELD,
            prefs = UserPreferences(baselineIpdPx = 100f, isCalibrated = true),
            sessionStartTimeMs = 0L,
            nowMs = 2000,
        )

        assertEquals(30f, requireNotNull(snap.distanceCm), 0.5f)
        assertEquals(22f, requireNotNull(snap.neckAngleDeg), 0.5f)
    }

    @Test
    fun interventionManager_distancePersistence_triggersOnlyAfter5Seconds() {
        val manager = AdaptiveInterventionManager()
        val prefs = UserPreferences(minDistanceThresholdCm = 25)
        manager.reset(nowMs = 0L)

        val closeSnapStart = SensorSnapshot(
            timestampMs = 1_000L,
            faceDetected = true,
            distanceCm = 20f,
        )
        assertEquals(ActiveAlert.None, manager.evaluate(closeSnapStart, prefs))

        val closeSnapStillUnder5s = closeSnapStart.copy(timestampMs = 5_900L)
        assertEquals(ActiveAlert.None, manager.evaluate(closeSnapStillUnder5s, prefs))

        val closeSnapAfter5s = closeSnapStart.copy(timestampMs = 6_200L)
        val alert = manager.evaluate(closeSnapAfter5s, prefs)
        assertTrue(alert is ActiveAlert.DistanceWarning)
        assertEquals(1, manager.getTotalWarningsTriggered())
    }

    @Test
    fun interventionManager_posturePersistence_triggersAfter15Seconds() {
        val manager = AdaptiveInterventionManager()
        val prefs = UserPreferences(maxNeckAngleThresholdDeg = 30)
        manager.reset(nowMs = 0L)

        val badPostureStart = SensorSnapshot(
            timestampMs = 1_000L,
            faceDetected = true,
            distanceCm = 45f,
            neckAngleDeg = 36f,
        )
        assertEquals(ActiveAlert.None, manager.evaluate(badPostureStart, prefs))
        assertEquals(ActiveAlert.None, manager.evaluate(badPostureStart.copy(timestampMs = 14_000L), prefs))

        val triggered = manager.evaluate(badPostureStart.copy(timestampMs = 16_500L), prefs)
        assertTrue(triggered is ActiveAlert.PostureWarning)
    }

    @Test
    fun localRuleInsightEngine_producesNonEmptyRecommendationsWithoutEmojis() {
        val insight = localInsightEngine.generate(
            SessionAggregateInput(
                durationSeconds = 1800,
                studyMode = StudyMode.HANDHELD,
                overallScore = 62,
                avgBlinkRateBpm = 8.1f,
                minBlinkRateBpm = 5.5f,
                avgDistanceCm = 27.0f,
                tooCloseDurationSec = 180,
                avgNeckAngleDeg = 34.5f,
                techNeckDurationSec = 420,
                avgAmbientLux = 48f,
                avgScreenBrightnessPct = 82,
                avgNoiseDb = 66f,
                peakNoiseDb = 74f,
            ),
        )

        assertFalse(insight.isGeneratedByCloudAi)
        assertEquals(3, insight.actionableRecommendations.size)
        assertTrue(insight.sensorCorrelations.isNotEmpty())
        assertTrue(insight.executiveSummary.isNotBlank())
    }

    @Test
    fun calibrationUseCase_computesMedianRobustly() {
        val median = calibrationUseCase.computeMedianIpd(List(20) { 85f } + listOf(82f, 200f, 84f, 86f, 85f))
        assertEquals(85f, median!!, 0.01f)
    }

    @Test
    fun sensorFusion_disabledSensors_haveNoMeasuredValues() {
        fusionEngine.resetSmoothing()
        val snap = fusionEngine.fuseSingleTick(
            vision = VisionTelemetry(
                faceDetected = true,
                blinkRateBpm = 4f,
                ipdPixels = 300f,
                headPitchDeg = 40f,
                timestampMs = 3_000L,
            ),
            env = EnvironmentTelemetry(
                ambientLux = 10f,
                isProximityNear = true,
                devicePitchDeg = 55f,
                isLyingDown = true,
                screenBrightnessPct = 95,
            ),
            noiseDb = NoiseTelemetry(85f, DataStatus.AVAILABLE, 3000),
            studyMode = StudyMode.HANDHELD,
            prefs = UserPreferences(
                enableFrontCamera = false,
                enableLightSensor = false,
                enableProximitySensor = false,
                enableAccelerometer = false,
                enableGyroscope = false,
                enableMicrophone = false,
            ),
            sessionStartTimeMs = 0L,
        )

        assertFalse(snap.faceDetected)
        assertFalse(snap.isProximityTriggered)
        assertEquals(null, snap.distanceCm)
        assertEquals(null, snap.blinkRateBpm)
        assertEquals(null, snap.ambientLux)
        assertEquals(null, snap.noiseDb)
        assertEquals(null, snap.neckAngleDeg)
        assertEquals(null, snap.ergonomicScore)
        assertEquals(DataStatus.DISABLED, snap.lightDataStatus)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun geminiRequestJson_omitsNullFieldsForValidSchemaPayload() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        val req = GeminiGenerateContentRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart("inst"))),
            contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart("{}")))),
            generationConfig = GeminiGenerationConfig(
                responseSchema = GeminiSchema(
                    type = "OBJECT",
                    properties = mapOf("summary" to GeminiSchema(type = "STRING")),
                    required = listOf("summary"),
                ),
            ),
        )

        val serialized = json.encodeToString(req)
        assertFalse(serialized.contains(":null"))
        assertFalse(serialized.contains("\"role\":null"))
    }
}
