package com.lumiposture.app.presentation.session.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.theme.LumiTheme
import com.lumiposture.app.presentation.theme.DarkOptimal
import com.lumiposture.app.presentation.theme.DarkWarning
import com.lumiposture.app.presentation.theme.DarkCritical

@Composable
fun LiveErgonomicGauge(
    score: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 152.dp,
    onDarkSurface: Boolean = false,
    isPartial: Boolean = false,
) {
    val colors = LumiTheme.colors
    val targetProgress = ((score ?: 0).coerceIn(0, 100) / 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 350),
        label = "gaugeSweep",
    )

    val targetAccent = when {
        score == null -> colors.textSecondary
        score >= 80 -> if (onDarkSurface) DarkOptimal else colors.optimal
        score >= 60 -> if (onDarkSurface) DarkWarning else colors.warning
        else -> if (onDarkSurface) DarkCritical else colors.critical
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
        score == null -> "Belum ada data"
        isPartial -> "Data parsial"
        score >= 85 -> "Indeks tinggi"
        score >= 70 -> "Indeks sedang"
        score >= 50 -> "Perlu perhatian"
        else -> "Indeks rendah"
    }

    val isCompact = size <= 124.dp
    val strokeDp = (size * 0.08f).coerceIn(8.dp, 12.dp)
    val scoreFontSize = if (isCompact) 30.sp else 36.sp
    val statusFontSize = if (isCompact) 11.sp else 12.sp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokePx = strokeDp.toPx()
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
                    text = score?.toString() ?: "N/A",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = scoreFontSize,
                        lineHeight = scoreFontSize,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = textColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = statusFontSize,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = animatedAccent,
                )
            }
        }

        Text(
            text = "SKOR ERGONOMI",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = subTextColor,
        )
    }
}
