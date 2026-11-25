package com.lumiposture.app.domain.model

enum class StudyMode(val displayName: String, val apiValue: String) {
    HANDHELD("Handheld", "HANDHELD"),
    DESK_STAND("Desk Stand", "DESK_STAND"),
}

enum class MetricStatus {
    OPTIMAL,
    WARNING,
    CRITICAL,
}

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

data class SensorCapabilities(
    val hasFrontCamera: Boolean = true,
    val hasLightSensor: Boolean = false,
    val hasProximitySensor: Boolean = false,
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasMicrophone: Boolean = true,
) {
    val availableHardwareCount: Int
        get() = listOf(
            hasFrontCamera,
            hasLightSensor,
            hasProximitySensor,
            hasAccelerometer,
            hasGyroscope || hasRotationVector,
            hasMicrophone,
        ).count { it }
}

data class VisionTelemetry(
    val faceDetected: Boolean = false,
    val blinkRateBpm: Float = 15f,
    val totalBlinks: Int = 0,
    val eyeBlinkAvg: Float = 0f,
    val eyeSquintAvg: Float = 0f,
    val isDrowsyEyesClosed: Boolean = false,
    val ipdPixels: Float = 0f,
    val headPitchDeg: Float = 0f,
    val landmarkPoints: List<NormalizedPoint> = emptyList(),
    val leftIrisPoint: NormalizedPoint? = null,
    val rightIrisPoint: NormalizedPoint? = null,
    val frameWidth: Int = 480,
    val frameHeight: Int = 640,
    val inferenceFps: Float = 0f,
    val timestampMs: Long = 0L,
)

data class EnvironmentTelemetry(
    val ambientLux: Float = 200f,
    val hasLightSensor: Boolean = false,
    val isProximityNear: Boolean = false,
    val proximityCm: Float = 100f,
    val hasProximitySensor: Boolean = false,
    val devicePitchDeg: Float = 10f,
    val isLyingDown: Boolean = false,
    val usingRotationVector: Boolean = false,
    val screenBrightnessPct: Int = 50,
)

data class SensorSnapshot(
    val timestampMs: Long = 0L,
    val elapsedSeconds: Int = 0,
    val studyMode: StudyMode = StudyMode.HANDHELD,
    val faceDetected: Boolean = false,
    val blinkRateBpm: Float = 15f,
    val totalBlinks: Int = 0,
    val eyeSquintAvg: Float = 0f,
    val isDrowsyEyesClosed: Boolean = false,
    val distanceCm: Float = 30f,
    val isProximityTriggered: Boolean = false,
    val devicePitchDeg: Float = 10f,
    val headPitchDeg: Float = 0f,
    val neckAngleDeg: Float = 10f,
    val isLyingDown: Boolean = false,
    val ambientLux: Float = 200f,
    val screenBrightnessPct: Int = 50,
    val noiseDb: Float = 38f,
    val ipdPixels: Float = 0f,
    val inferenceFps: Float = 0f,
    val ergonomicScore: Int = 100,
    val blinkSubScore: Int = 100,
    val distanceSubScore: Int = 100,
    val postureSubScore: Int = 100,
    val lightingSubScore: Int = 100,
    val noiseSubScore: Int = 100,
    val blinkStatus: MetricStatus = MetricStatus.OPTIMAL,
    val distanceStatus: MetricStatus = MetricStatus.OPTIMAL,
    val postureStatus: MetricStatus = MetricStatus.OPTIMAL,
    val lightingStatus: MetricStatus = MetricStatus.OPTIMAL,
    val noiseStatus: MetricStatus = MetricStatus.OPTIMAL,
    val landmarkPoints: List<NormalizedPoint> = emptyList(),
    val leftIrisPoint: NormalizedPoint? = null,
    val rightIrisPoint: NormalizedPoint? = null,
    val frameWidth: Int = 480,
    val frameHeight: Int = 640,
) {
    val hasAnyViolation: Boolean
        get() = (blinkStatus == MetricStatus.CRITICAL) ||
            (distanceStatus == MetricStatus.CRITICAL) ||
            (postureStatus == MetricStatus.CRITICAL) ||
            (lightingStatus == MetricStatus.CRITICAL)
}
