package com.lumiposture.app.domain.model

data class UserPreferences(
    val baselineIpdPx: Float = 0f,
    val isCalibrated: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val minDistanceThresholdCm: Int = 25,
    val maxNeckAngleThresholdDeg: Int = 30,
    val minLuxThreshold: Int = 80,
    val debugTelemetryEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val geminiApiKey: String = "",
    val enableFrontCamera: Boolean = true,
    val enableLightSensor: Boolean = true,
    val enableProximitySensor: Boolean = true,
    val enableAccelerometer: Boolean = true,
    val enableGyroscope: Boolean = true,
    val enableMicrophone: Boolean = true,
)

data class TelemetrySamplePoint(
    val elapsedSeconds: Int,
    val ergonomicScore: Int?,
    val blinkRateBpm: Float?,
    val distanceCm: Float?,
    val neckAngleDeg: Float?,
    val ambientLux: Float?,
    val noiseDb: Float?,
    val isViolationActive: Boolean,
    val screenBrightnessPct: Int? = null,
)

data class AiInsight(
    val statusLabel: String,
    val executiveSummary: String,
    val primaryRiskFactor: String,
    val sensorCorrelations: List<String>,
    val actionableRecommendations: List<String>,
    val isGeneratedByCloudAi: Boolean,
    val createdAtMillis: Long,
)

data class StudySessionSummary(
    val id: Long,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Int,
    val studyMode: StudyMode,
    val overallScore: Int?,
    val avgBlinkRate: Float?,
    val avgDistanceCm: Float?,
    val avgNeckAngleDeg: Float?,
    val avgLux: Float?,
    val avgNoiseDb: Float?,
    val totalWarningsTriggered: Int,
    val statusLabel: String? = null,
    val isGeneratedByCloudAi: Boolean? = null,
)

data class SessionReport(
    val session: StudySessionSummary,
    val samples: List<TelemetrySamplePoint>,
    val insight: AiInsight?,
)
