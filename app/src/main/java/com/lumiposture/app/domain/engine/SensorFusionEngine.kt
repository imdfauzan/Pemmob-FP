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

        val rawCamDistanceCm = if (vision.faceDetected && (vision.ipdPixels > 4f)) {
            val baselineIpd = if (prefs.baselineIpdPx > 10f) prefs.baselineIpdPx else DEFAULT_BASELINE_IPD_PX
            (BASELINE_DISTANCE_CM * (baselineIpd / vision.ipdPixels)).coerceIn(8f, 120f)
        } else {
            smoothedDistanceCm ?: BASELINE_DISTANCE_CM
        }

        val finalDistanceCm = if (env.isProximityNear) {
            5f
        } else {
            val prev = smoothedDistanceCm ?: rawCamDistanceCm
            val ema = DISTANCE_EMA_ALPHA * rawCamDistanceCm + (1f - DISTANCE_EMA_ALPHA) * prev
            smoothedDistanceCm = ema
            ema
        }

        val rawNeckAngle = if (vision.faceDetected) {
            (env.devicePitchDeg + vision.headPitchDeg).coerceIn(0f, 90f)
        } else {
            env.devicePitchDeg.coerceIn(0f, 90f)
        }
        val prevNeck = smoothedNeckAngleDeg ?: rawNeckAngle
        val finalNeckAngle = (NECK_EMA_ALPHA * rawNeckAngle + (1f - NECK_EMA_ALPHA) * prevNeck)
            .also { smoothedNeckAngleDeg = it }

        val prevLux = smoothedLux ?: env.ambientLux
        val finalLux = (0.35f * env.ambientLux + 0.65f * prevLux).also { smoothedLux = it }

        val prevNoise = smoothedNoiseDb ?: noiseDb
        val finalNoise = (0.4f * noiseDb + 0.6f * prevNoise).also { smoothedNoiseDb = it }

        val breakdown = scoreCalculator.calculate(
            blinkRateBpm = vision.blinkRateBpm,
            eyeSquintAvg = vision.eyeSquintAvg,
            distanceCm = finalDistanceCm,
            isProximityTriggered = env.isProximityNear,
            neckAngleDeg = finalNeckAngle,
            isLyingDown = env.isLyingDown,
            ambientLux = finalLux,
            screenBrightnessPct = env.screenBrightnessPct,
            noiseDb = finalNoise,
            prefs = prefs,
        )

        return SensorSnapshot(
            timestampMs = nowMs,
            elapsedSeconds = elapsedSec,
            studyMode = studyMode,
            faceDetected = vision.faceDetected,
            blinkRateBpm = vision.blinkRateBpm,
            totalBlinks = vision.totalBlinks,
            eyeSquintAvg = vision.eyeSquintAvg,
            isDrowsyEyesClosed = vision.isDrowsyEyesClosed,
            distanceCm = finalDistanceCm,
            isProximityTriggered = env.isProximityNear,
            devicePitchDeg = env.devicePitchDeg,
            headPitchDeg = vision.headPitchDeg,
            neckAngleDeg = finalNeckAngle,
            isLyingDown = env.isLyingDown,
            ambientLux = finalLux,
            screenBrightnessPct = env.screenBrightnessPct,
            noiseDb = finalNoise,
            ipdPixels = vision.ipdPixels,
            inferenceFps = vision.inferenceFps,
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
            landmarkPoints = vision.landmarkPoints,
            leftIrisPoint = vision.leftIrisPoint,
            rightIrisPoint = vision.rightIrisPoint,
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
