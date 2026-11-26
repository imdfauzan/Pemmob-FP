package com.lumiposture.app.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.lumiposture.app.data.local.prefs.UserCalibrationDataStore
import com.lumiposture.app.data.remote.GeminiInsightClient
import com.lumiposture.app.data.remote.dto.*
import com.lumiposture.app.domain.engine.*
import com.lumiposture.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import kotlin.math.roundToInt

data class AiAnalysisOutcome(val insight: AiInsight, val fallbackReason: String? = null)

class AnalyzeSessionWithAiUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geminiInsightClient: GeminiInsightClient,
    private val localFallbackEngine: LocalRuleBasedInsightEngine,
    private val userDataStore: UserCalibrationDataStore,
    private val json: Json,
) {
    suspend fun analyze(aggregate: SessionAggregateInput, samples: List<TelemetrySamplePoint>): AiInsight =
        analyzeWithOutcome(aggregate, samples).insight

    suspend fun analyzeWithOutcome(aggregate: SessionAggregateInput, samples: List<TelemetrySamplePoint>): AiAnalysisOutcome = withContext(Dispatchers.IO) {
        fun fallback(reason: String) = AiAnalysisOutcome(localFallbackEngine.generate(aggregate).let {
            it.copy(executiveSummary = "$reason ${it.executiveSummary}")
        }, reason)
        if (aggregate.overallScore == null) return@withContext fallback("Data sesi belum cukup untuk analisis Gemini.")
        val key = userDataStore.preferencesFlow.first().geminiApiKey.trim()
        if (key.isBlank()) return@withContext fallback("Gemini API Key belum diatur di Setelan.")
        if (!hasInternet()) return@withContext fallback("Perangkat offline.")
        try {
            val request = GeminiGenerateContentRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(SYSTEM_INSTRUCTION))),
                contents = listOf(GeminiContent("user", listOf(GeminiPart(json.encodeToString(buildTelemetryPayload(aggregate, samples)))))),
                generationConfig = GeminiGenerationConfig(responseSchema = RESPONSE_SCHEMA),
            )
            val response = withTimeout(20_000) { geminiInsightClient.generate(key, request) }
            val raw = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?.trim()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim() ?: error("Empty response")
            val output = json.decodeFromString<GeminiInsightOutputDto>(raw)
            check(output.executiveSummary.isNotBlank() && output.actionableRecommendations.isNotEmpty())
            AiAnalysisOutcome(AiInsight(clean(output.healthStatusLabel), clean(output.executiveSummary), clean(output.primaryRiskFactor),
                output.sensorCorrelations.map(::clean).filter(String::isNotBlank).take(4),
                output.actionableRecommendations.map(::clean).filter(String::isNotBlank).take(3), true, System.currentTimeMillis()))
        } catch (_: TimeoutCancellationException) {
            fallback("Gemini melewati batas waktu 20 detik.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Do not log exceptions containing request URLs or API credentials.
            val reason = when (error) {
                is HttpException -> when (error.code()) {
                    404 -> "Model Gemini tidak tersedia untuk proyek ini (HTTP 404). Gunakan versi aplikasi terbaru atau coba lagi nanti."
                    503 -> "Layanan Gemini sedang sibuk (HTTP 503). Coba analisis ulang nanti."
                    401, 403 -> "Akses Gemini ditolak (HTTP ${error.code()}). Periksa API key dan izin proyek."
                    429 -> "Batas kuota Gemini tercapai (HTTP 429). Coba lagi setelah kuota tersedia."
                    else -> "Gemini gagal (HTTP ${error.code()})."
                }
                else -> "Analisis Gemini gagal; periksa koneksi dan API key."
            }
            fallback(reason)
        }
    }

    fun buildAggregateInput(durationSeconds: Int, studyMode: StudyMode, overallScore: Int?,
        samples: List<TelemetrySamplePoint>, avgBrightnessPct: Int?,
        minDistanceCm: Int = 25, maxOrientationDeg: Int = 30): SessionAggregateInput =
        SessionAggregator().aggregate(durationSeconds, studyMode, overallScore, samples, avgBrightnessPct, minDistanceCm, maxOrientationDeg)

    internal fun buildTelemetryPayload(aggregate: SessionAggregateInput, samples: List<TelemetrySamplePoint>): GeminiTelemetryPayloadDto =
        GeminiTelemetryPayloadDto(
            sessionDurationMinutes = (aggregate.durationSeconds / 60f).roundToInt().coerceAtLeast(1),
            studyMode = aggregate.studyMode.apiValue, overallErgonomicScore = aggregate.overallScore,
            metricsSummary = GeminiMetricsSummaryDto(aggregate.avgBlinkRateBpm, aggregate.minBlinkRateBpm,
                aggregate.avgDistanceCm, aggregate.tooCloseDurationSec, aggregate.avgNeckAngleDeg,
                aggregate.techNeckDurationSec, aggregate.avgAmbientLux, aggregate.avgScreenBrightnessPct,
                aggregate.avgNoiseDb, aggregate.peakNoiseDb),
            timelineSegments = samples.chunked((samples.size / 3).coerceAtLeast(1)).map { group ->
                GeminiTimelineSegmentDto("${group.first().elapsedSeconds / 60}-${group.last().elapsedSeconds / 60}",
                    group.mapNotNull { it.ergonomicScore }.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                    group.map { it.ambientLux }.validAverage(), group.map { it.blinkRateBpm }.validAverage(), group.map { it.neckAngleDeg }.validAverage())
            },
        )

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    private fun clean(text: String) = text.replace(Regex("[\\p{So}\\p{Cs}]"), "").replace('—', ',').trim()

    companion object {
        private const val SYSTEM_INSTRUCTION = "Analisis sesi LumiPosture dalam Bahasa Indonesia. Nilai hilang berarti tidak tersedia, jangan ditebak. " +
            "Skor hanya indeks aturan dari metrik valid dan dapat parsial. Jarak kamera adalah estimasi hasil kalibrasi. " +
            "Orientasi gabungan HP/kepala bukan sudut leher anatomis. Amplitudo mikrofon dBFS bukan dB SPL terkalibrasi, jangan dinilai sebagai tingkat kebisingan. " +
            "Kedipan atau mata menyipit bukan diagnosis kelelahan. Grafik bukan bukti sebab-akibat; jangan simpulkan korelasi statistik tanpa analisis. " +
            "Jangan mendiagnosis penyakit, gunakan emoji, atau membuat klaim medis. Kembalikan JSON sesuai skema."
        private val RESPONSE_SCHEMA = GeminiSchema("OBJECT", properties = mapOf(
            "health_status_label" to GeminiSchema("STRING"), "executive_summary" to GeminiSchema("STRING"),
            "primary_risk_factor" to GeminiSchema("STRING"),
            "sensor_correlations" to GeminiSchema("ARRAY", items = GeminiSchema("STRING")),
            "actionable_recommendations" to GeminiSchema("ARRAY", items = GeminiSchema("STRING")),
        ), required = listOf("health_status_label", "executive_summary", "primary_risk_factor", "sensor_correlations", "actionable_recommendations"))
    }
}
