package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.ActiveAlert
import com.lumiposture.app.domain.model.SensorSnapshot
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.UserPreferences
import javax.inject.Inject
import kotlin.math.roundToInt

class AdaptiveInterventionManager @Inject constructor() {

    private var distanceViolationStartMs: Long? = null
    private var postureViolationStartMs: Long? = null
    private var glareViolationStartMs: Long? = null
    private var lowBlinkViolationStartMs: Long? = null
    private var highSquintBlinkViolationStartMs: Long? = null
    private var lastEyeBreakTriggeredMs: Long? = null
    private var sessionStartMs: Long? = null

    private var previousAlertKey: String? = null
    private var totalWarningsTriggered: Int = 0
    private var eyeBreakActive: Boolean = false

    fun reset(nowMs: Long = System.currentTimeMillis()) {
        distanceViolationStartMs = null
        postureViolationStartMs = null
        glareViolationStartMs = null
        lowBlinkViolationStartMs = null
        highSquintBlinkViolationStartMs = null
        lastEyeBreakTriggeredMs = nowMs
        sessionStartMs = nowMs
        previousAlertKey = null
        totalWarningsTriggered = 0
        eyeBreakActive = false
    }

    fun acknowledgeEyeBreakCompleted(nowMs: Long = System.currentTimeMillis()) {
        eyeBreakActive = false
        lowBlinkViolationStartMs = null
        highSquintBlinkViolationStartMs = null
        lastEyeBreakTriggeredMs = nowMs
        previousAlertKey = null
    }

    fun snoozeEyeBreak(
        nowMs: Long = System.currentTimeMillis(),
    ) {
        eyeBreakActive = false
        lowBlinkViolationStartMs = null
        highSquintBlinkViolationStartMs = null
        // Schedule next scheduled break in 5 minutes from now
        val intervalMs = DEFAULT_EYE_BREAK_MINUTES * 60_000L
        lastEyeBreakTriggeredMs = nowMs - (intervalMs - (5 * 60_000L))
        previousAlertKey = null
    }

    fun getTotalWarningsTriggered(): Int = totalWarningsTriggered

    fun clearContinuousViolations() {
        distanceViolationStartMs = null
        postureViolationStartMs = null
        glareViolationStartMs = null
        lowBlinkViolationStartMs = null
        highSquintBlinkViolationStartMs = null
    }

    fun evaluate(
        snapshot: SensorSnapshot,
        prefs: UserPreferences,
    ): ActiveAlert {
        val nowMs = snapshot.timestampMs
        if (snapshot.distanceCm == null) distanceViolationStartMs = null
        if (snapshot.neckAngleDeg == null) postureViolationStartMs = null
        if (snapshot.ambientLux == null) glareViolationStartMs = null
        if (snapshot.blinkRateBpm == null) {
            lowBlinkViolationStartMs = null
            highSquintBlinkViolationStartMs = null
        }
        if (sessionStartMs == null) {
            sessionStartMs = nowMs
            lastEyeBreakTriggeredMs = nowMs
        }

        if (eyeBreakActive) {
            return ActiveAlert.EyeBreakRequired(
                blinkRateBpm = snapshot.blinkRateBpm,
                squintLevel = snapshot.eyeSquintAvg,
                reason = "Pertahankan istirahat 20 detik sambil menatap objek jauh.",
            )
        }

        // 1. Proximity / Critical Distance (> 5s for camera distance, immediate for proximity)
        val isTooClose = snapshot.distanceCm?.let { it < prefs.minDistanceThresholdCm } == true
        distanceViolationStartMs = if (isTooClose) {
            distanceViolationStartMs ?: nowMs
        } else {
            null
        }
        val tooCloseElapsedMs = distanceViolationStartMs?.let { nowMs - it } ?: 0L

        if (snapshot.isProximityTriggered) {
            val alert = ActiveAlert.DistanceWarning(
                distanceCm = snapshot.distanceCm,
                isCriticalProximity = true,
                message = "Objek terlalu dekat dengan sensor. Jauhkan layar minimal ${prefs.minDistanceThresholdCm} cm.",
            )
            recordTransition("PROXIMITY")
            return alert
        }

        if (tooCloseElapsedMs >= DISTANCE_PERSIST_MS) {
            val alert = ActiveAlert.DistanceWarning(
                distanceCm = snapshot.distanceCm,
                isCriticalProximity = false,
                message = "Estimasi jarak ${snapshot.distanceCm?.roundToInt()} cm. Jauhkan layar dari mata.",
            )
            recordTransition("DISTANCE")
            return alert
        }

        // 2. Tech-Neck Posture (> 15s)
        val motionEnabled = prefs.enableAccelerometer || prefs.enableGyroscope
        val isPostureViolation = motionEnabled && (
            snapshot.neckAngleDeg?.let { it >= prefs.maxNeckAngleThresholdDeg } == true
            )
        postureViolationStartMs = if (isPostureViolation) {
            postureViolationStartMs ?: nowMs
        } else {
            null
        }
        val postureElapsedMs = postureViolationStartMs?.let { nowMs - it } ?: 0L

        if (postureElapsedMs >= POSTURE_PERSIST_MS) {
            val message = if (snapshot.isLyingDown && snapshot.studyMode == StudyMode.HANDHELD) {
                "Kemiringan HP melewati ambang. Sesuaikan posisi layar."
            } else {
                "Estimasi orientasi ${snapshot.neckAngleDeg?.roundToInt()} derajat. Sesuaikan posisi layar."
            }
            val alert = ActiveAlert.PostureWarning(
                neckAngleDeg = requireNotNull(snapshot.neckAngleDeg),
                isLyingDown = snapshot.isLyingDown,
                message = message,
            )
            recordTransition("POSTURE")
            return alert
        }

        // 3. Dynamic 20-20-20 Eye Break
        val sessionElapsedMs = nowMs - (sessionStartMs ?: nowMs)
        val sinceLastBreakMs = nowMs - (lastEyeBreakTriggeredMs ?: nowMs)
        val scheduledBreakIntervalMs = DEFAULT_EYE_BREAK_MINUTES * 60_000L

        val lowBlinkActive = snapshot.faceDetected && sessionElapsedMs >= 60_000L && snapshot.blinkRateBpm?.let { it < 10f } == true
        lowBlinkViolationStartMs = if (lowBlinkActive) {
            lowBlinkViolationStartMs ?: nowMs
        } else {
            null
        }
        val highSquintWithLowBlink = lowBlinkActive && snapshot.eyeSquintAvg > 0.35f
        highSquintBlinkViolationStartMs = if (highSquintWithLowBlink) {
            highSquintBlinkViolationStartMs ?: nowMs
        } else {
            null
        }

        val lowBlinkElapsed = lowBlinkViolationStartMs?.let { nowMs - it } ?: 0L
        val squintElapsed = highSquintBlinkViolationStartMs?.let { nowMs - it } ?: 0L

        val eyeBreakReason = when {
            sinceLastBreakMs >= scheduledBreakIntervalMs ->
                "Sudah $DEFAULT_EYE_BREAK_MINUTES menit menatap layar. Saatnya jeda 20-20-20."
            squintElapsed >= ADAPTIVE_SQUINT_BREAK_MS ->
                "Estimasi kedipan rendah (${snapshot.blinkRateBpm?.roundToInt()} BPM) disertai mata menyipit. Ini bukan diagnosis."
            lowBlinkElapsed >= LOW_BLINK_BREAK_MS ->
                "Frekuensi kedipan di bawah 10 BPM selama 2 menit berturut-turut."
            else -> null
        }

        if (eyeBreakReason != null) {
            eyeBreakActive = true
            val alert = ActiveAlert.EyeBreakRequired(
                blinkRateBpm = snapshot.blinkRateBpm,
                squintLevel = snapshot.eyeSquintAvg,
                reason = eyeBreakReason,
            )
            recordTransition("EYE_BREAK")
            return alert
        }

        // 4. Dim-Room Glare Warning (> 8s)
        val glareActive = prefs.enableLightSensor &&
            snapshot.ambientLux?.let { it < prefs.minLuxThreshold } == true &&
            snapshot.screenBrightnessPct?.let { it > 65 } == true
        glareViolationStartMs = if (glareActive) {
            glareViolationStartMs ?: nowMs
        } else {
            null
        }
        val glareElapsedMs = glareViolationStartMs?.let { nowMs - it } ?: 0L
        if (glareElapsedMs >= GLARE_PERSIST_MS) {
            val alert = ActiveAlert.GlareWarning(
                ambientLux = requireNotNull(snapshot.ambientLux),
                brightnessPct = requireNotNull(snapshot.screenBrightnessPct),
                message = "Ruangan redup (${snapshot.ambientLux?.roundToInt()} Lux) dengan layar terang (${snapshot.screenBrightnessPct}%). Nyalakan lampu atau redupkan layar.",
            )
            recordTransition("GLARE")
            return alert
        }

        previousAlertKey = null
        return ActiveAlert.None
    }

    private fun recordTransition(key: String) {
        if (previousAlertKey != key) {
            totalWarningsTriggered += 1
            previousAlertKey = key
        }
    }

    companion object {
        const val DEFAULT_EYE_BREAK_MINUTES = 20
        const val DISTANCE_PERSIST_MS = 5_000L
        const val POSTURE_PERSIST_MS = 15_000L
        const val GLARE_PERSIST_MS = 8_000L
        const val LOW_BLINK_BREAK_MS = 120_000L
        const val ADAPTIVE_SQUINT_BREAK_MS = 45_000L
    }
}
