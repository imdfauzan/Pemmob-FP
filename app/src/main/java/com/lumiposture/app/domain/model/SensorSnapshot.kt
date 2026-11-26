package com.lumiposture.app.domain.model

enum class StudyMode(val displayName: String, val apiValue: String) {
    HANDHELD("Handheld", "HANDHELD"),
    DESK_STAND("Desk Stand", "DESK_STAND"),
}

enum class MetricStatus {
    UNAVAILABLE,
    OPTIMAL,
    WARNING,
    CRITICAL,
}

enum class DataStatus(val label: String) {
    AVAILABLE("Tersedia"), WAITING("Menunggu data"), DISABLED("Nonaktif"),
    PERMISSION_DENIED("Izin ditolak"), UNSUPPORTED("Tidak didukung"),
    FAILED("Pembacaan gagal"), STALE("Data usang"), NO_FACE("Wajah tidak ditemukan"),
    NEEDS_CALIBRATION("Perlu kalibrasi"),
}

data class NoiseTelemetry(
    val dbfs: Float? = null,
    val status: DataStatus = DataStatus.WAITING,
    val timestampMs: Long = 0L,
)

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
    val blinkRateBpm: Float = 0f,
    val blinkWindowReady: Boolean = false,
    val status: DataStatus = DataStatus.WAITING,
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
    val ambientLux: Float = 0f,
    val lightStatus: DataStatus = DataStatus.WAITING,
    val proximityStatus: DataStatus = DataStatus.WAITING,
    val motionStatus: DataStatus = DataStatus.WAITING,
    val lightTimestampMs: Long = 0L,
    val proximityTimestampMs: Long = 0L,
    val motionTimestampMs: Long = 0L,
    val lightReportsOnChange: Boolean = false,
    val proximityReportsOnChange: Boolean = false,
    val hasLightSensor: Boolean = false,
    val isProximityNear: Boolean = false,
    val proximityCm: Float = 100f,
    val hasProximitySensor: Boolean = false,
    val devicePitchDeg: Float = 0f,
    val isLyingDown: Boolean = false,
    val usingRotationVector: Boolean = false,
    val screenBrightnessPct: Int? = null,
)

data class SensorSnapshot(
    val timestampMs: Long = 0L,
    val elapsedSeconds: Int = 0,
    val studyMode: StudyMode = StudyMode.HANDHELD,
    val faceDetected: Boolean = false,
    val blinkRateBpm: Float? = null,
    val cameraDataStatus: DataStatus = DataStatus.WAITING,
    val blinkDataStatus: DataStatus = DataStatus.WAITING,
    val distanceDataStatus: DataStatus = DataStatus.WAITING,
    val postureDataStatus: DataStatus = DataStatus.WAITING,
    val lightDataStatus: DataStatus = DataStatus.WAITING,
    val noiseDataStatus: DataStatus = DataStatus.WAITING,
    val proximityDataStatus: DataStatus = DataStatus.WAITING,
    val totalBlinks: Int = 0,
    val eyeSquintAvg: Float = 0f,
    val isDrowsyEyesClosed: Boolean = false,
    val distanceCm: Float? = null,
    val isProximityTriggered: Boolean = false,
    val devicePitchDeg: Float? = null,
    val headPitchDeg: Float? = null,
    val neckAngleDeg: Float? = null,
    val isLyingDown: Boolean = false,
    val ambientLux: Float? = null,
    val screenBrightnessPct: Int? = null,
    val noiseDb: Float? = null,
    val ipdPixels: Float = 0f,
    val inferenceFps: Float = 0f,
    val ergonomicScore: Int? = null,
    val blinkSubScore: Int = 100,
    val distanceSubScore: Int = 100,
    val postureSubScore: Int = 100,
    val lightingSubScore: Int = 100,
    val noiseSubScore: Int = 100,
    val blinkStatus: MetricStatus = MetricStatus.UNAVAILABLE,
    val distanceStatus: MetricStatus = MetricStatus.UNAVAILABLE,
    val postureStatus: MetricStatus = MetricStatus.UNAVAILABLE,
    val lightingStatus: MetricStatus = MetricStatus.UNAVAILABLE,
    val noiseStatus: MetricStatus = MetricStatus.UNAVAILABLE,
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
