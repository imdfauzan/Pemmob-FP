package com.lumiposture.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateContentRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
data class GeminiPart(
    val text: String,
)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
    val responseSchema: GeminiSchema,
    val temperature: Float = 0.2f,
)

@Serializable
data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val items: GeminiSchema? = null,
    val required: List<String>? = null,
)

@Serializable
data class GeminiTelemetryPayloadDto(
    @SerialName("session_duration_minutes")
    val sessionDurationMinutes: Int,
    @SerialName("study_mode")
    val studyMode: String,
    @SerialName("overall_ergonomic_score")
    val overallErgonomicScore: Int,
    @SerialName("metrics_summary")
    val metricsSummary: GeminiMetricsSummaryDto,
    @SerialName("timeline_segments")
    val timelineSegments: List<GeminiTimelineSegmentDto>,
)

@Serializable
data class GeminiMetricsSummaryDto(
    @SerialName("avg_blink_rate_bpm")
    val avgBlinkRateBpm: Float,
    @SerialName("min_blink_rate_bpm")
    val minBlinkRateBpm: Float,
    @SerialName("avg_distance_cm")
    val avgDistanceCm: Float,
    @SerialName("too_close_duration_sec")
    val tooCloseDurationSec: Int,
    @SerialName("avg_neck_angle_deg")
    val avgNeckAngleDeg: Float,
    @SerialName("tech_neck_duration_sec")
    val techNeckDurationSec: Int,
    @SerialName("avg_ambient_lux")
    val avgAmbientLux: Float,
    @SerialName("avg_screen_brightness_pct")
    val avgScreenBrightnessPct: Int,
    @SerialName("avg_noise_db")
    val avgNoiseDb: Float,
    @SerialName("peak_noise_db")
    val peakNoiseDb: Float,
)

@Serializable
data class GeminiTimelineSegmentDto(
    @SerialName("minute_range")
    val minuteRange: String,
    val score: Int,
    val lux: Float,
    val bpm: Float,
    @SerialName("neck_deg")
    val neckDeg: Float,
)
