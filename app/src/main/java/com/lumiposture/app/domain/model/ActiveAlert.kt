package com.lumiposture.app.domain.model

sealed interface ActiveAlert {
    data object None : ActiveAlert

    data class DistanceWarning(
        val distanceCm: Float,
        val isCriticalProximity: Boolean,
        val message: String,
    ) : ActiveAlert

    data class PostureWarning(
        val neckAngleDeg: Float,
        val isLyingDown: Boolean,
        val message: String,
    ) : ActiveAlert

    data class GlareWarning(
        val ambientLux: Float,
        val brightnessPct: Int,
        val message: String,
    ) : ActiveAlert

    data class EyeBreakRequired(
        val blinkRateBpm: Float,
        val squintLevel: Float,
        val reason: String,
    ) : ActiveAlert
}
