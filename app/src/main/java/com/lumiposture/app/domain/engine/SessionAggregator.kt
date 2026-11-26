package com.lumiposture.app.domain.engine

import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import javax.inject.Inject

fun Iterable<Float?>.validAverage(): Float? = mapNotNull { it?.takeIf(Float::isFinite) }
    .takeIf { it.isNotEmpty() }?.average()?.toFloat()

class SessionAggregator @Inject constructor() {
    fun aggregate(durationSeconds: Int, studyMode: StudyMode, overallScore: Int?,
        samples: List<TelemetrySamplePoint>, avgBrightnessPct: Int?,
        minDistanceCm: Int = 25, maxOrientationDeg: Int = 30): SessionAggregateInput {
        val duration = durationSeconds.coerceAtLeast(0)
        val ordered = samples.sortedBy { it.elapsedSeconds }.distinctBy { it.elapsedSeconds }
        fun violatingSeconds(predicate: (TelemetrySamplePoint) -> Boolean): Int = ordered.mapIndexed { index, point ->
            val start = point.elapsedSeconds.coerceIn(0, duration)
            val end = minOf(ordered.getOrNull(index + 1)?.elapsedSeconds ?: duration, start + 10, duration)
            if (predicate(point)) (end - start).coerceAtLeast(0) else 0
        }.sum().coerceAtMost(duration)
        return SessionAggregateInput(duration, studyMode, overallScore,
            ordered.map { it.blinkRateBpm }.validAverage(), ordered.mapNotNull { it.blinkRateBpm }.minOrNull(),
            ordered.map { it.distanceCm }.validAverage(), violatingSeconds { it.distanceCm?.let { d -> d < minDistanceCm } == true },
            ordered.map { it.neckAngleDeg }.validAverage(), violatingSeconds { it.neckAngleDeg?.let { n -> n >= maxOrientationDeg } == true },
            ordered.map { it.ambientLux }.validAverage(), avgBrightnessPct,
            ordered.map { it.noiseDb }.validAverage(), ordered.mapNotNull { it.noiseDb }.maxOrNull())
    }
}
