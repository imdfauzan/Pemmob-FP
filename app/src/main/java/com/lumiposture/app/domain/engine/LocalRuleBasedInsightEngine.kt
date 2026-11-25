package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.AiInsight
import com.lumiposture.app.domain.model.StudyMode
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

data class SessionAggregateInput(
    val durationSeconds: Int,
    val studyMode: StudyMode,
    val overallScore: Int,
    val avgBlinkRateBpm: Float,
    val minBlinkRateBpm: Float,
    val avgDistanceCm: Float,
    val tooCloseDurationSec: Int,
    val avgNeckAngleDeg: Float,
    val techNeckDurationSec: Int,
    val avgAmbientLux: Float,
    val avgScreenBrightnessPct: Int,
    val avgNoiseDb: Float,
    val peakNoiseDb: Float,
)

class LocalRuleBasedInsightEngine @Inject constructor() {

    fun generate(input: SessionAggregateInput, nowMillis: Long = System.currentTimeMillis()): AiInsight {
        val statusLabel = when {
            input.overallScore >= 85 -> "Optimal"
            input.overallScore >= 70 -> "Cukup Baik"
            input.overallScore >= 50 -> "Perlu Perhatian"
            else -> "Berisiko Tinggi"
        }

        val correlations = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val hasGlare = (input.avgAmbientLux < 80f) && (input.avgScreenBrightnessPct > 65)
        val hasLowBlink = (input.avgBlinkRateBpm < 10f) || (input.minBlinkRateBpm < 8f)
        val hasCloseDistance = (input.avgDistanceCm < 25f) || (input.tooCloseDurationSec >= 30)
        val hasTechNeck = (input.avgNeckAngleDeg >= 30f) || (input.techNeckDurationSec >= 60)
        val hasHighNoise = (input.peakNoiseDb > 65f) || (input.avgNoiseDb > 60f)

        if (hasGlare && hasLowBlink) {
            correlations += String.format(
                Locale.US,
                "Pencahayaan redup rata-rata %.0f Lux dengan kecerahan layar %d%% berkorelasi dengan penurunan kedipan ke %.1f BPM.",
                input.avgAmbientLux,
                input.avgScreenBrightnessPct,
                input.avgBlinkRateBpm,
            )
        } else if (hasGlare) {
            correlations += String.format(
                Locale.US,
                "Kontras cahaya tinggi terjadi pada ruangan %.0f Lux dengan layar %d%%, meningkatkan risiko ketegangan mata.",
                input.avgAmbientLux,
                input.avgScreenBrightnessPct,
            )
        } else if (hasLowBlink) {
            correlations += String.format(
                Locale.US,
                "Frekuensi kedipan rata-rata %.1f BPM (terendah %.1f BPM) berada di bawah ambang sehat 12-25 BPM.",
                input.avgBlinkRateBpm,
                input.minBlinkRateBpm,
            )
        }

        if (hasTechNeck) {
            val mins = (input.techNeckDurationSec / 60f)
            correlations += String.format(
                Locale.US,
                "Sudut menunduk rata-rata %.1f derajat selama %.1f menit memberi beban tambahan pada tulang leher.",
                input.avgNeckAngleDeg,
                mins,
            )
        }

        if (hasCloseDistance) {
            correlations += String.format(
                Locale.US,
                "Jarak pandang rata-rata %.1f cm dengan durasi terlalu dekat %d detik memicu akomodasi berlebih pada mata.",
                input.avgDistanceCm,
                input.tooCloseDurationSec,
            )
        }

        if (hasHighNoise) {
            correlations += String.format(
                Locale.US,
                "Kebisingan puncak %.0f dB (rata-rata %.0f dB) berpotensi mengganggu fokus dan memperpanjang durasi tatap layar.",
                input.peakNoiseDb,
                input.avgNoiseDb,
            )
        }

        if (correlations.isEmpty()) {
            correlations += String.format(
                Locale.US,
                "Keseimbangan cahaya (%.0f Lux), jarak pandang (%.1f cm), dan kedipan (%.1f BPM) terjaga dalam batas ergonomis.",
                input.avgAmbientLux,
                input.avgDistanceCm,
                input.avgBlinkRateBpm,
            )
        }

        if (hasGlare || (input.avgAmbientLux < 120f)) {
            recommendations += "Tingkatkan pencahayaan meja belajar minimal 150 Lux atau turunkan kecerahan layar ke kisaran 40-55%."
        }
        if (hasTechNeck) {
            recommendations += if (input.studyMode == StudyMode.HANDHELD) {
                "Gunakan penyangga ponsel setinggi pandangan mata atau naikkan posisi genggam agar sudut leher di bawah 20 derajat."
            } else {
                "Tinggikan posisi penyangga layar sehingga bagian atas layar mendekati sejajar mata."
            }
        }
        if (hasCloseDistance) {
            recommendations += "Pertahankan jarak layar minimal 30-40 cm dari wajah selama sesi membaca."
        }
        if (hasLowBlink) {
            recommendations += "Terapkan jeda 20-20-20 secara berkala: lihat objek sejauh 6 meter selama 20 detik dan berkedip sadar."
        }
        if (hasHighNoise) {
            recommendations += "Pertimbangkan lokasi belajar yang lebih tenang di bawah 55 dB untuk mengurangi ketegangan kognitif."
        }
        while (recommendations.size < 3) {
            val filler = when (recommendations.size) {
                0 -> "Pertahankan jarak pandang minimal 30 cm dan sudut leher di bawah 20 derajat pada sesi berikutnya."
                1 -> "Lakukan jeda 20-20-20 setiap 20 menit untuk menjaga kelembapan permukaan mata."
                else -> "Pastikan pencahayaan ruang tetap merata di kisaran 150-350 Lux tanpa silau langsung."
            }
            recommendations += filler
        }

        val primaryRiskFactor = when {
            hasGlare && hasTechNeck -> "Kontras Cahaya dan Postur Menunduk"
            hasGlare -> "Kontras Cahaya Layar dan Ruangan"
            hasTechNeck -> "Sudut Fleksi Leher (Tech-Neck)"
            hasCloseDistance -> "Jarak Pandang Terlalu Dekat"
            hasLowBlink -> "Penurunan Frekuensi Kedipan Mata"
            hasHighNoise -> "Kebisingan Lingkungan Belajar"
            else -> "Tidak Ada Risiko Signifikan"
        }

        val durationMin = (input.durationSeconds / 60f).roundToInt().coerceAtLeast(1)
        val executiveSummary = when {
            input.overallScore >= 85 ->
                "Selama sesi $durationMin menit, ergonomi mata dan postur Anda berada pada kondisi optimal dengan skor ${input.overallScore}/100."
            else ->
                "Skor ergonomi sesi $durationMin menit mencapai ${input.overallScore}/100 dengan faktor utama pada ${primaryRiskFactor.lowercase(Locale.ROOT)}."
        }

        return AiInsight(
            statusLabel = statusLabel,
            executiveSummary = executiveSummary,
            primaryRiskFactor = primaryRiskFactor,
            sensorCorrelations = correlations.take(3),
            actionableRecommendations = recommendations.take(3),
            isGeneratedByCloudAi = false,
            createdAtMillis = nowMillis,
        )
    }
}
