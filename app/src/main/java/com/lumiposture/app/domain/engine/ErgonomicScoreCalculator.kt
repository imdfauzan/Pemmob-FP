package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.domain.model.UserPreferences
import javax.inject.Inject
import kotlin.math.roundToInt

data class ScoreBreakdown(
    val overallScore: Int,
    val blinkSubScore: Int,
    val distanceSubScore: Int,
    val postureSubScore: Int,
    val lightingSubScore: Int,
    val noiseSubScore: Int,
    val blinkStatus: MetricStatus,
    val distanceStatus: MetricStatus,
    val postureStatus: MetricStatus,
    val lightingStatus: MetricStatus,
    val noiseStatus: MetricStatus,
)

class ErgonomicScoreCalculator @Inject constructor() {

    fun calculate(
        blinkRateBpm: Float,
        eyeSquintAvg: Float,
        distanceCm: Float,
        isProximityTriggered: Boolean,
        neckAngleDeg: Float,
        isLyingDown: Boolean,
        ambientLux: Float,
        screenBrightnessPct: Int,
        noiseDb: Float,
        prefs: UserPreferences,
    ): ScoreBreakdown {
        val (blinkScore, blinkStatus) = scoreBlink(blinkRateBpm, eyeSquintAvg)
        val (distScore, distStatus) = scoreDistance(
            distanceCm = distanceCm,
            isProximityTriggered = isProximityTriggered,
            minDistanceCm = prefs.minDistanceThresholdCm.toFloat(),
        )
        val (postureScore, postureStatus) = scorePosture(
            neckAngleDeg = neckAngleDeg,
            isLyingDown = isLyingDown,
            maxNeckAngleDeg = prefs.maxNeckAngleThresholdDeg.toFloat(),
        )
        val (lightingScore, lightingStatus) = scoreLighting(
            ambientLux = ambientLux,
            screenBrightnessPct = screenBrightnessPct,
            minLux = prefs.minLuxThreshold.toFloat(),
        )
        val (noiseScore, noiseStatus) = scoreNoise(noiseDb)

        val weighted = ((0.25f * blinkScore) +
            (0.25f * distScore) +
            (0.25f * postureScore) +
            (0.15f * lightingScore) +
            (0.10f * noiseScore)).roundToInt().coerceIn(0, 100)

        return ScoreBreakdown(
            overallScore = weighted,
            blinkSubScore = blinkScore,
            distanceSubScore = distScore,
            postureSubScore = postureScore,
            lightingSubScore = lightingScore,
            noiseSubScore = noiseScore,
            blinkStatus = blinkStatus,
            distanceStatus = distStatus,
            postureStatus = postureStatus,
            lightingStatus = lightingStatus,
            noiseStatus = noiseStatus,
        )
    }

    private fun scoreBlink(bpm: Float, squintAvg: Float): Pair<Int, MetricStatus> {
        val baseScore = when {
            (bpm >= 12f) && (bpm <= 25f) -> 100f
            (bpm >= 10f) && (bpm < 12f) -> 75f + ((bpm - 10f) * 12.5f)
            (bpm > 25f) && (bpm <= 32f) -> 100f - ((bpm - 25f) * 3.5f)
            bpm < 10f -> (20f + ((bpm / 10f) * 50f)).coerceIn(15f, 70f)
            else -> 72f
        }
        val squintPenalty = if (squintAvg > 0.35f) ((squintAvg - 0.35f) * 40f).coerceAtMost(25f) else 0f
        val finalScore = (baseScore - squintPenalty).roundToInt().coerceIn(0, 100)
        val status = when {
            bpm < 10f || finalScore < 60 -> MetricStatus.CRITICAL
            bpm < 12f || squintAvg > 0.35f -> MetricStatus.WARNING
            else -> MetricStatus.OPTIMAL
        }
        return finalScore to status
    }

    private fun scoreDistance(
        distanceCm: Float,
        isProximityTriggered: Boolean,
        minDistanceCm: Float,
    ): Pair<Int, MetricStatus> {
        if (isProximityTriggered || distanceCm <= 10f) {
            return 10 to MetricStatus.CRITICAL
        }
        val optimalFloor = (minDistanceCm + 5f).coerceAtLeast(30f)
        val score = when {
            distanceCm >= optimalFloor -> 100
            distanceCm >= minDistanceCm -> {
                val ratio = (distanceCm - minDistanceCm) / (optimalFloor - minDistanceCm)
                (70f + ratio * 28f).roundToInt()
            }
            else -> {
                val ratio = ((distanceCm - 10f) / (minDistanceCm - 10f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                (15f + ratio * 50f).roundToInt()
            }
        }
        val status = when {
            distanceCm < minDistanceCm -> MetricStatus.CRITICAL
            distanceCm < optimalFloor -> MetricStatus.WARNING
            else -> MetricStatus.OPTIMAL
        }
        return score.coerceIn(0, 100) to status
    }

    private fun scorePosture(
        neckAngleDeg: Float,
        isLyingDown: Boolean,
        maxNeckAngleDeg: Float,
    ): Pair<Int, MetricStatus> {
        if (isLyingDown) {
            return 25 to MetricStatus.CRITICAL
        }
        val warningFloor = (maxNeckAngleDeg - 10f).coerceAtLeast(15f)
        val score = when {
            neckAngleDeg <= warningFloor -> 100
            neckAngleDeg < maxNeckAngleDeg -> {
                val ratio = (neckAngleDeg - warningFloor) / (maxNeckAngleDeg - warningFloor).coerceAtLeast(1f)
                (96f - ratio * 26f).roundToInt()
            }
            else -> {
                val excess = (neckAngleDeg - maxNeckAngleDeg).coerceIn(0f, 45f)
                (65f - (excess / 45f) * 50f).roundToInt()
            }
        }
        val status = when {
            neckAngleDeg >= maxNeckAngleDeg -> MetricStatus.CRITICAL
            neckAngleDeg >= warningFloor -> MetricStatus.WARNING
            else -> MetricStatus.OPTIMAL
        }
        return score.coerceIn(0, 100) to status
    }

    private fun scoreLighting(
        ambientLux: Float,
        screenBrightnessPct: Int,
        minLux: Float,
    ): Pair<Int, MetricStatus> {
        val glareRisk = ambientLux < minLux && screenBrightnessPct > 65
        val tooDark = ambientLux < 40f
        val score = when {
            tooDark -> if (screenBrightnessPct > 65) 25 else 38
            glareRisk -> 42
            ambientLux < minLux -> 62
            ambientLux in 150f..500f -> 100
            ambientLux in minLux..150f -> {
                val ratio = (ambientLux - minLux) / (150f - minLux).coerceAtLeast(1f)
                (76f + ratio * 22f).roundToInt()
            }
            else -> 94
        }
        val status = when {
            tooDark || glareRisk -> MetricStatus.CRITICAL
            ambientLux < 120f -> MetricStatus.WARNING
            else -> MetricStatus.OPTIMAL
        }
        return score.coerceIn(0, 100) to status
    }

    private fun scoreNoise(noiseDb: Float): Pair<Int, MetricStatus> {
        val score = when {
            noiseDb <= 50f -> 100
            noiseDb <= 65f -> (100f - ((noiseDb - 50f) / 15f) * 28f).roundToInt()
            else -> (68f - ((noiseDb - 65f) / 25f).coerceIn(0f, 1f) * 48f).roundToInt()
        }
        val status = when {
            noiseDb > 65f -> MetricStatus.CRITICAL
            noiseDb > 55f -> MetricStatus.WARNING
            else -> MetricStatus.OPTIMAL
        }
        return score.coerceIn(0, 100) to status
    }
}
