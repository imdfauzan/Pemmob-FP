package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SensorFusionEngine @Inject constructor(private val scoreCalculator: ErgonomicScoreCalculator) {
    private val smoothed = mutableMapOf<String, Float>()
    fun resetSmoothing() = smoothed.clear()

    fun fuseStream(
        visionFlow: Flow<VisionTelemetry>, environmentFlow: Flow<EnvironmentTelemetry>,
        noiseDbFlow: Flow<NoiseTelemetry>, studyModeFlow: Flow<StudyMode>,
        preferencesFlow: Flow<UserPreferences>, sessionStartTimeMs: () -> Long,
        nowMs: () -> Long = System::currentTimeMillis,
    ): Flow<SensorSnapshot> {
        val inputs = combine(visionFlow, environmentFlow, noiseDbFlow, studyModeFlow, preferencesFlow) { v, e, n, m, p ->
            FusionInputs(v, e, n, m, p)
        }
        val clock = flow { while (true) { emit(Unit); delay(200) } }
        return combine(inputs, clock) { i, _ ->
            // Inputs may arrive between clock ticks; use collection time for freshness.
            fuseSingleTick(i.vision, i.env, i.noise, i.mode, i.prefs, sessionStartTimeMs(), nowMs())
        }
    }

    fun fuseSingleTick(
        vision: VisionTelemetry, env: EnvironmentTelemetry, noiseDb: NoiseTelemetry,
        studyMode: StudyMode, prefs: UserPreferences, sessionStartTimeMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): SensorSnapshot {
        fun status(enabled: Boolean, source: DataStatus, timestamp: Long, ttl: Long = 3000): DataStatus = when {
            !enabled -> DataStatus.DISABLED
            source != DataStatus.AVAILABLE -> source
            timestamp <= 0 || nowMs - timestamp !in 0..ttl -> DataStatus.STALE
            else -> DataStatus.AVAILABLE
        }
        val camera = status(prefs.enableFrontCamera, if (vision.status == DataStatus.AVAILABLE && !vision.faceDetected) DataStatus.NO_FACE else vision.status, vision.timestampMs)
        val face = camera == DataStatus.AVAILABLE && vision.faceDetected
        val blinkState = if (face && !vision.blinkWindowReady) DataStatus.WAITING else camera
        val distanceState = when {
            camera != DataStatus.AVAILABLE -> camera
            !prefs.isCalibrated || !prefs.baselineIpdPx.isFinite() || prefs.baselineIpdPx <= 10 -> DataStatus.NEEDS_CALIBRATION
            !vision.ipdPixels.isFinite() || vision.ipdPixels <= 4 -> DataStatus.FAILED
            else -> DataStatus.AVAILABLE
        }
        val motion = status(prefs.enableAccelerometer || prefs.enableGyroscope, env.motionStatus, env.motionTimestampMs)
        // On-change sensors retain their last value while the registration remains active.
        val light = status(prefs.enableLightSensor, env.lightStatus, env.lightTimestampMs, if (env.lightReportsOnChange) Long.MAX_VALUE else 3000)
        val proximity = status(prefs.enableProximitySensor, env.proximityStatus, env.proximityTimestampMs, if (env.proximityReportsOnChange) Long.MAX_VALUE else 3000)
        val noise = status(prefs.enableMicrophone, noiseDb.status, noiseDb.timestampMs, 5000)
        val posture = if (motion != DataStatus.AVAILABLE) motion else camera
        val near = proximity == DataStatus.AVAILABLE && env.isProximityNear
        fun smooth(key: String, value: Float?, alpha: Float): Float? {
            if (value == null || !value.isFinite()) { smoothed.remove(key); return null }
            return (alpha * value + (1 - alpha) * (smoothed[key] ?: value)).also { smoothed[key] = it }
        }
        val distance = smooth("distance", if (distanceState == DataStatus.AVAILABLE && face)
            (BASELINE_DISTANCE_CM * prefs.baselineIpdPx / vision.ipdPixels).coerceIn(8f, 120f) else null, 0.3f)
        val neck = smooth("posture", if (posture == DataStatus.AVAILABLE && face)
            (env.devicePitchDeg + vision.headPitchDeg).coerceIn(0f, 90f) else null, 0.35f)
        val lux = smooth("lux", env.ambientLux.takeIf { light == DataStatus.AVAILABLE && it >= 0 }, 0.35f)
        val amplitude = smooth("noise", noiseDb.dbfs.takeIf { noise == DataStatus.AVAILABLE }, 0.4f)
        val blink = vision.blinkRateBpm.takeIf { face && blinkState == DataStatus.AVAILABLE && it.isFinite() }
        val lying = motion == DataStatus.AVAILABLE && env.isLyingDown && studyMode == StudyMode.HANDHELD
        val score = scoreCalculator.calculate(blink, vision.eyeSquintAvg, distance, near, neck, lying,
            lux, env.screenBrightnessPct, amplitude, prefs)
        return SensorSnapshot(
            timestampMs = nowMs, elapsedSeconds = ((nowMs - sessionStartTimeMs).coerceAtLeast(0) / 1000).toInt(),
            studyMode = studyMode, faceDetected = face, blinkRateBpm = blink,
            cameraDataStatus = camera, blinkDataStatus = blinkState, distanceDataStatus = distanceState,
            postureDataStatus = posture, lightDataStatus = light, noiseDataStatus = noise, proximityDataStatus = proximity,
            totalBlinks = vision.totalBlinks, eyeSquintAvg = if (face) vision.eyeSquintAvg else 0f,
            distanceCm = distance, isProximityTriggered = near,
            devicePitchDeg = env.devicePitchDeg.takeIf { motion == DataStatus.AVAILABLE },
            headPitchDeg = vision.headPitchDeg.takeIf { face }, neckAngleDeg = neck, isLyingDown = lying,
            ambientLux = lux, screenBrightnessPct = env.screenBrightnessPct, noiseDb = amplitude,
            ipdPixels = if (face) vision.ipdPixels else 0f, inferenceFps = if (face) vision.inferenceFps else 0f,
            ergonomicScore = score.overallScore, blinkSubScore = score.blinkSubScore, distanceSubScore = score.distanceSubScore,
            postureSubScore = score.postureSubScore, lightingSubScore = score.lightingSubScore, noiseSubScore = score.noiseSubScore,
            blinkStatus = score.blinkStatus, distanceStatus = score.distanceStatus, postureStatus = score.postureStatus,
            lightingStatus = score.lightingStatus, noiseStatus = score.noiseStatus,
            landmarkPoints = if (face) vision.landmarkPoints else emptyList(),
            leftIrisPoint = vision.leftIrisPoint.takeIf { face }, rightIrisPoint = vision.rightIrisPoint.takeIf { face },
            frameWidth = vision.frameWidth, frameHeight = vision.frameHeight,
        )
    }
    private data class FusionInputs(val vision: VisionTelemetry, val env: EnvironmentTelemetry,
        val noise: NoiseTelemetry, val mode: StudyMode, val prefs: UserPreferences)
    companion object { const val BASELINE_DISTANCE_CM = 30f }
}
