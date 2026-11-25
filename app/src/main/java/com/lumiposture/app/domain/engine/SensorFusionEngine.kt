package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.EnvironmentTelemetry
import com.lumiposture.app.domain.model.SensorSnapshot
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.UserPreferences
import com.lumiposture.app.domain.model.VisionTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class SensorFusionEngine @Inject constructor(
    private val scoreCalculator: ErgonomicScoreCalculator,
) {
    private var smoothedDistanceCm: Float? = null
    private var smoothedNeckAngleDeg: Float? = null
    private var smoothedLux: Float? = null
    private var smoothedNoiseDb: Float? = null

    fun resetSmoothing() {
        smoothedDistanceCm = null
        smoothedNeckAngleDeg = null
        smoothedLux = null
        smoothedNoiseDb = null
    }

    fun fuseStream(
        visionFlow: Flow<VisionTelemetry>,
        environmentFlow: Flow<EnvironmentTelemetry>,
        noiseDbFlow: Flow<Float>,
        studyModeFlow: Flow<StudyMode>,
        preferencesFlow: Flow<UserPreferences>,
        sessionStartTimeMs: () -> Long,
    ): Flow<SensorSnapshot> = combine(
        visionFlow,
        environmentFlow,
        noiseDbFlow,
        studyModeFlow,
        preferencesFlow,
    ) { vision, env, noiseDb, studyMode, prefs ->
        fuseSingleTick(
            vision = vision,
            env = env,
            noiseDb = noiseDb,
            studyMode = studyMode,
            prefs = prefs,
            sessionStartTimeMs = sessionStartTimeMs(),
        )
    }

    fun fuseSingleTick(
        vision: VisionTelemetry,
        env: EnvironmentTelemetry,
        noiseDb: Float,
        studyMode: StudyMode,
        prefs: UserPreferences,
        sessionStartTimeMs: Long,
    ): SensorSnapshot {
        val nowMs = maxOf(vision.timestampMs, System.currentTimeMillis())
        val elapsedSec = ((nowMs - sessionStartTimeMs).coerceAtLeast(0L) / 1000L).toInt()

        val camEnabled = prefs.enableFrontCamera
        val effectiveFaceDetected = camEnabled && vision.faceDetected
        val effectiveBlinkBpm = if (camEnabled) vision.blinkRateBpm else 15f
        val effectiveSquintAvg = if (camEnabled) vision.eyeSquintAvg else 0f
        val effectiveTotalBlinks = if (camEnabled) vision.totalBlinks else 0
        val effectiveHeadPitch = if (effectiveFaceDetected) vision.headPitchDeg else 0f
        val effectiveIpd = if (effectiveFaceDetected) vision.ipdPixels else 0f
        val effectiveFps = if (camEnabled) vision.inferenceFps else 0f

        val rawCamDistanceCm = if (effectiveFaceDetected && (effectiveIpd > 4f)) {
            val baselineIpd = if (prefs.baselineIpdPx > 10f) prefs.baselineIpdPx else DEFAULT_BASELINE_IPD_PX
            (BASELINE_DISTANCE_CM * (baselineIpd / effectiveIpd)).coerceIn(8f, 120f)
        } else if (!camEnabled) {
            BASELINE_DISTANCE_CM
        } else {
            smoothedDistanceCm ?: BASELINE_DISTANCE_CM
        }

        val proximityActive = prefs.enableProximitySensor && env.isProximityNear
        val finalDistanceCm = if (proximityActive) {
            5f
        } else if (!camEnabled) {
            BASELINE_DISTANCE_CM
        } else {
            val prev = smoothedDistanceCm ?: rawCamDistanceCm
            val ema = (DISTANCE_EMA_ALPHA * rawCamDistanceCm) + ((1f - DISTANCE_EMA_ALPHA) * prev)
            smoothedDistanceCm = ema
            ema
        }

        val motionEnabled = prefs.enableAccelerometer || prefs.enableGyroscope
        val effectiveDevicePitch = if (motionEnabled) env.devicePitchDeg else 12f
        val effectiveLyingDown = prefs.enableAccelerometer && env.isLyingDown

        val rawNeckAngle = if (effectiveFaceDetected) {
            (effectiveDevicePitch + effectiveHeadPitch).coerceIn(0f, 90f)
        } else {
            effectiveDevicePitch.coerceIn(0f, 90f)
        }
        val prevNeck = smoothedNeckAngleDeg ?: rawNeckAngle
        val finalNeckAngle = ((NECK_EMA_ALPHA * rawNeckAngle) + ((1f - NECK_EMA_ALPHA) * prevNeck))
            .also { smoothedNeckAngleDeg = it }

        val targetLux = if (prefs.enableLightSensor) env.ambientLux else 200f
        val prevLux = smoothedLux ?: targetLux
        val finalLux = (0.35f * targetLux + 0.65f * prevLux).also { smoothedLux = it }

        val targetNoise = if (prefs.enableMicrophone) noiseDb else 36f
        val prevNoise = smoothedNoiseDb ?: targetNoise
        val finalNoise = (0.4f * targetNoise + 0.6f * prevNoise).also { smoothedNoiseDb = it }

        val breakdown = scoreCalculator.calculate(
            blinkRateBpm = effectiveBlinkBpm,
            eyeSquintAvg = effectiveSquintAvg,
            distanceCm = finalDistanceCm,
            isProximityTriggered = proximityActive,
            neckAngleDeg = finalNeckAngle,
            isLyingDown = effectiveLyingDown,
            ambientLux = finalLux,
            screenBrightnessPct = env.screenBrightnessPct,
            noiseDb = finalNoise,
            prefs = prefs,
        )

        return SensorSnapshot(
            timestampMs = nowMs,
            elapsedSeconds = elapsedSec,
            studyMode = studyMode,
            faceDetected = effectiveFaceDetected,
            blinkRateBpm = effectiveBlinkBpm,
            totalBlinks = effectiveTotalBlinks,
            eyeSquintAvg = effectiveSquintAvg,
            isDrowsyEyesClosed = camEnabled && vision.isDrowsyEyesClosed,
            distanceCm = finalDistanceCm,
            isProximityTriggered = proximityActive,
            devicePitchDeg = effectiveDevicePitch,
            headPitchDeg = effectiveHeadPitch,
            neckAngleDeg = finalNeckAngle,
            isLyingDown = effectiveLyingDown,
            ambientLux = finalLux,
            screenBrightnessPct = env.screenBrightnessPct,
            noiseDb = finalNoise,
            ipdPixels = effectiveIpd,
            inferenceFps = effectiveFps,
            ergonomicScore = breakdown.overallScore,
            blinkSubScore = breakdown.blinkSubScore,
            distanceSubScore = breakdown.distanceSubScore,
            postureSubScore = breakdown.postureSubScore,
            lightingSubScore = breakdown.lightingSubScore,
            noiseSubScore = breakdown.noiseSubScore,
            blinkStatus = breakdown.blinkStatus,
            distanceStatus = breakdown.distanceStatus,
            postureStatus = breakdown.postureStatus,
            lightingStatus = breakdown.lightingStatus,
            noiseStatus = breakdown.noiseStatus,
            landmarkPoints = if (camEnabled) vision.landmarkPoints else emptyList(),
            leftIrisPoint = if (camEnabled) vision.leftIrisPoint else null,
            rightIrisPoint = if (camEnabled) vision.rightIrisPoint else null,
            frameWidth = vision.frameWidth,
            frameHeight = vision.frameHeight,
        )
    }

    companion object {
        const val BASELINE_DISTANCE_CM = 30f
        const val DEFAULT_BASELINE_IPD_PX = 115f
        const val DISTANCE_EMA_ALPHA = 0.30f
        const val NECK_EMA_ALPHA = 0.35f
    }
}
