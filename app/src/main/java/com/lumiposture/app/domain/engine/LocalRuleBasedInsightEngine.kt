package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.*
import javax.inject.Inject

data class SessionAggregateInput(
    val durationSeconds: Int, val studyMode: StudyMode, val overallScore: Int?,
    val avgBlinkRateBpm: Float?, val minBlinkRateBpm: Float?, val avgDistanceCm: Float?,
    val tooCloseDurationSec: Int, val avgNeckAngleDeg: Float?, val techNeckDurationSec: Int,
    val avgAmbientLux: Float?, val avgScreenBrightnessPct: Int?, val avgNoiseDb: Float?, val peakNoiseDb: Float?,
)

class LocalRuleBasedInsightEngine @Inject constructor() {
    fun generate(input: SessionAggregateInput, nowMillis: Long = System.currentTimeMillis()): AiInsight {
        val observations = mutableListOf<String>()
        val actions = mutableListOf<String>()
        if (input.avgAmbientLux?.let { it < 80 } == true) {
            observations += if (input.avgScreenBrightnessPct?.let { it > 65 } == true)
                "Cahaya terukur rendah saat setelan layar terang." else "Cahaya terukur berada di bawah ambang aturan aplikasi."
            actions += "Sesuaikan lampu dan kecerahan layar agar nyaman dibaca."
        }
        if (input.avgDistanceCm?.let { it < 25 } == true || input.tooCloseDurationSec > 0) {
            observations += "Estimasi jarak kamera sempat di bawah ambang sesi."
            actions += "Jauhkan layar dan perbesar teks bila perlu."
        }
        if (input.avgNeckAngleDeg?.let { it >= 30 } == true || input.techNeckDurationSec > 0) {
            observations += "Estimasi orientasi gabungan HP dan kepala melewati ambang sesi, bukan pengukuran sudut leher anatomis."
            actions += "Sesuaikan ketinggian layar dan posisi duduk agar nyaman."
        }
        if (input.avgBlinkRateBpm?.let { it < 10 } == true) {
            observations += "Estimasi kedipan rendah selama wajah terlacak, bukan diagnosis kelelahan mata."
            actions += "Ambil jeda melihat jauh dan berkediplah secara wajar."
        }
        val count = listOf(input.avgBlinkRateBpm, input.avgDistanceCm, input.avgNeckAngleDeg, input.avgAmbientLux).count { it != null }
        val label = when {
            input.overallScore == null -> "Data belum cukup"
            count < 4 -> "Data parsial"
            observations.isNotEmpty() || input.overallScore < 80 -> "Perlu perhatian"
            else -> "Dalam ambang aplikasi"
        }
        if (observations.isEmpty()) observations += if (input.overallScore == null)
            "Belum ada metrik valid untuk menilai sesi." else if (input.overallScore < 80)
            "Indeks aturan berada di bawah 80; tinjau metrik yang tersedia." else "Metrik yang tersedia tidak melewati ambang aturan. Sensor yang hilang tidak dinilai."
        if (input.avgNoiseDb != null) observations += "Amplitudo mikrofon ditampilkan dalam dBFS, tanpa kalibrasi dB SPL dan tanpa penilaian kebisingan."
        actions += "Gunakan jeda melihat jauh secara berkala selama belajar."
        if (count < 4) actions += "Periksa izin, sensor, dan kalibrasi untuk melengkapi data sesi berikutnya."
        actions += "Pastikan posisi layar dan pencahayaan terasa nyaman."
        return AiInsight(label,
            "Ringkasan aturan lokal untuk sesi ${input.durationSeconds} detik. $count dari 4 metrik skor tersedia. Skor ini indeks aturan aplikasi, bukan penilaian medis.",
            if (observations.size > 1 || count == 4) observations.first() else "Data terbatas",
            observations.take(4) + "Grafik menunjukkan perubahan bersama; tidak membuktikan sebab-akibat.",
            actions.distinct().take(3), false, nowMillis)
    }
}
