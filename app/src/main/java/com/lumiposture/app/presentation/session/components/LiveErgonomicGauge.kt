package com.lumiposture.app.presentation.session.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.theme.LumiTheme

@Composable
fun LiveErgonomicGauge(
    score: Int,
    modifier: Modifier = Modifier,
    size: Dp = 152.dp,
    onDarkSurface: Boolean = false,
) {
    val colors = LumiTheme.colors
    val targetProgress = (score.coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 350),
        label = "gaugeSweep",
    )

    val targetAccent = when {
        score >= 80 -> colors.optimal
        score >= 60 -> colors.warning
        else -> colors.critical
    }
    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        label = "gaugeColor",
    )

    val trackColor = if (onDarkSurface) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colors.textTertiary.copy(alpha = 0.16f)
    }
    val textColor = if (onDarkSurface) Color.White else colors.textPrimary
    val subTextColor = if (onDarkSurface) Color.White.copy(alpha = 0.72f) else colors.textSecondary

    val statusText = when {
        score >= 85 -> "Optimal"
        score >= 70 -> "Stabil"
        score >= 50 -> "Waspada"
        else -> "Berisiko"
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = 11.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            drawArc(
                color = animatedAccent,
                startAngle = 140f,
                sweepAngle = 260f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TabularMetricText(
                text = score.toString(),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                color = textColor,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = animatedAccent,
            )
            Text(
                text = "SKOR ERGONOMI",
                style = MaterialTheme.typography.labelSmall,
                color = subTextColor,
            )
        }
    }
}
