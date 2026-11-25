package com.lumiposture.app.presentation.session.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lumiposture.app.presentation.components.GlassSurface
import com.lumiposture.app.presentation.components.PillButton
import com.lumiposture.app.presentation.components.SecondaryPillButton
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.theme.GlassCardShape
import com.lumiposture.app.presentation.theme.LumiTheme
import kotlinx.coroutines.delay

@Composable
fun EyeBreakModalDialog(
    reason: String,
    onCompleted: () -> Unit,
    onDismissSnooze: () -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(20) }
    val colors = LumiTheme.colors

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
    }

    val progress by animateFloatAsState(
        targetValue = (20 - secondsLeft) / 20f,
        animationSpec = tween(durationMillis = 400),
        label = "eyeBreakProgress",
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = GlassCardShape,
            contentPadding = PaddingValues(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Istirahat Mata 20-20-20",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier.size(124.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(124.dp)) {
                        val stroke = 8.dp.toPx()
                        drawArc(
                            color = colors.textTertiary.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = if (secondsLeft == 0) colors.optimal else colors.primary,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    TabularMetricText(
                        text = "${secondsLeft}s",
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Arahkan pandangan ke objek berjarak sekitar 6 meter dan berkedip secara sadar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (secondsLeft > 0) {
                        SecondaryPillButton(
                            text = "Tunda",
                            onClick = onDismissSnooze,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PillButton(
                        text = if (secondsLeft == 0) "Lanjutkan Sesi" else "Selesai",
                        onClick = onCompleted,
                        enabled = secondsLeft == 0,
                        containerColor = if (secondsLeft == 0) colors.optimal else colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
