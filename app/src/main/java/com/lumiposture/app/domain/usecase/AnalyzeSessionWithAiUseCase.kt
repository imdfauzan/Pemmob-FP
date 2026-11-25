package com.lumiposture.app.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lumiposture.app.BuildConfig
import com.lumiposture.app.data.remote.GeminiApiService
import com.lumiposture.app.data.remote.dto.GeminiContent
import com.lumiposture.app.data.remote.dto.GeminiGenerateContentRequest
import com.lumiposture.app.data.remote.dto.GeminiGenerationConfig
import com.lumiposture.app.data.remote.dto.GeminiInsightOutputDto
import com.lumiposture.app.data.remote.dto.GeminiMetricsSummaryDto
import com.lumiposture.app.data.remote.dto.GeminiPart
import com.lumiposture.app.data.remote.dto.GeminiSchema
import com.lumiposture.app.data.remote.dto.GeminiTelemetryPayloadDto
import com.lumiposture.app.data.remote.dto.GeminiTimelineSegmentDto
import com.lumiposture.app.domain.engine.LocalRuleBasedInsightEngine
import com.lumiposture.app.domain.engine.SessionAggregateInput
import com.lumiposture.app.domain.model.AiInsight
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.roundToInt

class AnalyzeSessionWithAiUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geminiApiService: GeminiApiService,
    private val localFallbackEngine: LocalRuleBasedInsightEngine,
    private val json: Json,
) {

    suspend fun analyze(
        aggregate: SessionAggregateInput,
        samples: List<TelemetrySamplePoint>,
    ): AiInsight = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || !isNetworkAvailable()) {
            return@withContext localFallbackEngine.generate(aggregate)
        }

        val payload = buildTelemetryPayload(aggregate, samples)
        val payloadJson = json.encodeToString(payload)

        val request = GeminiGenerateContentRequest(
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(SYSTEM_INSTRUCTION)),
            ),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(payloadJson)),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = RESPONSE_SCHEMA,
                temperature = 0.2f,
            ),
        )

        runCatching {
            val response = geminiApiService.generateErgonomicInsight(
                apiKey = apiKey,
                request = request,
            )
            val rawText = response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim()
                ?: error("Empty Gemini response")

            val parsed = json.decodeFromString<GeminiInsightOutputDto>(rawText)
            AiInsight(
                statusLabel = sanitizeText(parsed.healthStatusLabel).ifBlank { "Perlu Perhatian" },
                executiveSummary = sanitizeText(parsed.executiveSummary),
                primaryRiskFactor = sanitizeText(parsed.primaryRiskFactor),
                sensorCorrelations = parsed.sensorCorrelations
                    .asSequence()
                    .map(::sanitizeText)
                    .filter { it.isNotBlank() }
                    .take(4)
                    .toList(),
                actionableRecommendations = parsed.actionableRecommendations
                    .asSequence()
                    .map(::sanitizeText)
                    .filter { it.isNotBlank() }
                    .take(3)
                    .toList(),
                isGeneratedByCloudAi = true,
                createdAtMillis = System.currentTimeMillis(),
            )
        }.getOrElse {
            localFallbackEngine.generate(aggregate)
        }
    }

    fun buildAggregateInput(
        durationSeconds: Int,
        studyMode: StudyMode,
        overallScore: Int,
        samples: List<TelemetrySamplePoint>,
        avgBrightnessPct: Int,
    ): SessionAggregateInput {
        if (samples.isEmpty()) {
            return SessionAggregateInput(
                durationSeconds = durationSeconds.coerceAtLeast(1),
                studyMode = studyMode,
                overallScore = overallScore,
                avgBlinkRateBpm = 15f,
                minBlinkRateBpm = 15f,
                avgDistanceCm = 40f,
                tooCloseDurationSec = 0,
                avgNeckAngleDeg = 12f,
                techNeckDurationSec = 0,
                avgAmbientLux = 200f,
                avgScreenBrightnessPct = avgBrightnessPct,
                avgNoiseDb = 40f,
                peakNoiseDb = 45f,
            )
        }
        val avgBlink = samples.map { it.blinkRateBpm }.average().toFloat()
        val minBlink = samples.minOf { it.blinkRateBpm }
        val avgDist = samples.map { it.distanceCm }.average().toFloat()
        val tooCloseSec = samples.count { it.distanceCm < 30f } * SAMPLE_STEP_SEC
        val avgNeck = samples.map { it.neckAngleDeg }.average().toFloat()
        val techNeckSec = samples.count { it.neckAngleDeg >= 30f } * SAMPLE_STEP_SEC
        val avgLux = samples.map { it.ambientLux }.average().toFloat()
        val avgNoise = samples.map { it.noiseDb }.average().toFloat()
        val peakNoise = samples.maxOf { it.noiseDb }

        return SessionAggregateInput(
            durationSeconds = durationSeconds.coerceAtLeast(1),
            studyMode = studyMode,
            overallScore = overallScore,
            avgBlinkRateBpm = roundOneDecimal(avgBlink),
            minBlinkRateBpm = roundOneDecimal(minBlink),
            avgDistanceCm = roundOneDecimal(avgDist),
            tooCloseDurationSec = tooCloseSec,
            avgNeckAngleDeg = roundOneDecimal(avgNeck),
            techNeckDurationSec = techNeckSec,
            avgAmbientLux = roundOneDecimal(avgLux),
            avgScreenBrightnessPct = avgBrightnessPct,
            avgNoiseDb = roundOneDecimal(avgNoise),
            peakNoiseDb = roundOneDecimal(peakNoise),
        )
    }

    private fun buildTelemetryPayload(
        aggregate: SessionAggregateInput,
        samples: List<TelemetrySamplePoint>,
    ): GeminiTelemetryPayloadDto {
        val segments = buildTimelineSegments(aggregate.durationSeconds, samples)
        return GeminiTelemetryPayloadDto(
            sessionDurationMinutes = (aggregate.durationSeconds / 60f).roundToInt().coerceAtLeast(1),
            studyMode = aggregate.studyMode.apiValue,
            overallErgonomicScore = aggregate.overallScore,
            metricsSummary = GeminiMetricsSummaryDto(
                avgBlinkRateBpm = aggregate.avgBlinkRateBpm,
                minBlinkRateBpm = aggregate.minBlinkRateBpm,
                avgDistanceCm = aggregate.avgDistanceCm,
                tooCloseDurationSec = aggregate.tooCloseDurationSec,
                avgNeckAngleDeg = aggregate.avgNeckAngleDeg,
                techNeckDurationSec = aggregate.techNeckDurationSec,
                avgAmbientLux = aggregate.avgAmbientLux,
                avgScreenBrightnessPct = aggregate.avgScreenBrightnessPct,
                avgNoiseDb = aggregate.avgNoiseDb,
                peakNoiseDb = aggregate.peakNoiseDb,
            ),
            timelineSegments = segments,
        )
    }

    private fun buildTimelineSegments(
        durationSeconds: Int,
        samples: List<TelemetrySamplePoint>,
    ): List<GeminiTimelineSegmentDto> {
        if (samples.isEmpty()) return emptyList()
        val bucketCount = 3
        val chunkSize = (samples.size / bucketCount).coerceAtLeast(1)
        val chunks = samples.chunked(chunkSize).take(4)
        val totalMinutes = (durationSeconds / 60f).coerceAtLeast(1f)
        val stepMin = totalMinutes / chunks.size.coerceAtLeast(1)

        return chunks.mapIndexed { idx, group ->
            val startMin = (idx * stepMin).roundToInt()
            val endMin = (((idx + 1) * stepMin).roundToInt()).coerceAtLeast(startMin + 1)
            GeminiTimelineSegmentDto(
                minuteRange = "$startMin-$endMin",
                score = group.map { it.ergonomicScore }.average().roundToInt(),
                lux = roundOneDecimal(group.map { it.ambientLux }.average().toFloat()),
                bpm = roundOneDecimal(group.map { it.blinkRateBpm }.average().toFloat()),
                neckDeg = roundOneDecimal(group.map { it.neckAngleDeg }.average().toFloat()),
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun sanitizeText(raw: String): String {
        return raw.replace(EMOJI_REGEX, "").replace(Regex("\\s+"), " ").trim()
    }

    private fun roundOneDecimal(value: Float): Float = (value * 10f).roundToInt() / 10f

    companion object {
        private const val SAMPLE_STEP_SEC = 10
        private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cs}]")

        private const val SYSTEM_INSTRUCTION =
            "Anda adalah asisten analis ergonomi dan kesehatan mata untuk aplikasi LumiPosture. " +
                "Tugas Anda adalah menganalisis data telemetri sensor sesi belajar pengguna dalam format JSON, " +
                "menemukan korelasi sebab-akibat antar sensor (cahaya, jarak, kedipan, postur leher, kebisingan), " +
                "dan memberikan rekomendasi praktis dalam Bahasa Indonesia yang ringkas, objektif, dan berbasis data. " +
                "JANGAN gunakan emoji, tanda seru berlebihan, atau bahasa promosi. " +
                "JANGAN mendiagnosis penyakit medis klinis. Kembalikan output HANYA sesuai skema JSON yang ditentukan."

        private val RESPONSE_SCHEMA = GeminiSchema(
            type = "OBJECT",
            properties = mapOf(
                "health_status_label" to GeminiSchema(type = "STRING"),
                "executive_summary" to GeminiSchema(type = "STRING"),
                "primary_risk_factor" to GeminiSchema(type = "STRING"),
                "sensor_correlations" to GeminiSchema(
                    type = "ARRAY",
                    items = GeminiSchema(type = "STRING"),
                ),
                "actionable_recommendations" to GeminiSchema(
                    type = "ARRAY",
                    items = GeminiSchema(type = "STRING"),
                ),
            ),
            required = listOf(
                "health_status_label",
                "executive_summary",
                "primary_risk_factor",
                "sensor_correlations",
                "actionable_recommendations",
            ),
        )
    }
}
