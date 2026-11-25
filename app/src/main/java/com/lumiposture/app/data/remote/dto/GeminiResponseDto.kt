package com.lumiposture.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
data class GeminiCandidate(
    val content: GeminiCandidateContent? = null,
)

@Serializable
data class GeminiCandidateContent(
    val parts: List<GeminiCandidatePart> = emptyList(),
)

@Serializable
data class GeminiCandidatePart(
    val text: String? = null,
)

@Serializable
data class GeminiInsightOutputDto(
    @SerialName("health_status_label")
    val healthStatusLabel: String,
    @SerialName("executive_summary")
    val executiveSummary: String,
    @SerialName("primary_risk_factor")
    val primaryRiskFactor: String,
    @SerialName("sensor_correlations")
    val sensorCorrelations: List<String> = emptyList(),
    @SerialName("actionable_recommendations")
    val actionableRecommendations: List<String> = emptyList(),
)
