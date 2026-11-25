package com.lumiposture.app.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.domain.model.StudySessionSummary
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.PillButton
import com.lumiposture.app.presentation.components.PillSegmentedControl
import com.lumiposture.app.presentation.components.StatusPillTag
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.session.components.LiveErgonomicGauge
import com.lumiposture.app.presentation.theme.LumiTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    hazeState: HazeState,
    onStartSession: (StudyMode) -> Unit,
    onOpenReport: (Long) -> Unit,
    onOpenCalibration: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LumiTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .hazeSource(hazeState),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(modifier = Modifier.statusBarsPadding()) {
                Text(
                    text = "LumiPosture",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Pemantauan Ergonomi Penggunaan HP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        if (!uiState.preferences.isCalibrated) {
            item {
                LumiCard(onClick = onOpenCalibration) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kalibrasi Jarak Belum Selesai",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lakukan kalibrasi 10 detik pada jarak 30 cm untuk akurasi estimasi jarak.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.primary,
                        )
                    }
                }
            }
        }

        // Hero Session Launch Card
        item {
            LumiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = if (uiState.latestSession != null) "Skor Terakhir" else "Siap Belajar Sehat",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                        val latest = uiState.latestSession
                        if (latest != null) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Kedipan %.1f BPM · Jarak %.0f cm · Leher %.0f deg",
                                    latest.avgBlinkRate,
                                    latest.avgDistanceCm,
                                    latest.avgNeckAngleDeg,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            StatusPillTag(
                                status = scoreToStatus(latest.overallScore),
                                label = latest.statusLabel ?: statusLabelForScore(latest.overallScore),
                            )
                        } else {
                            Text(
                                text = "Pantau kedipan, jarak layar, leher, cahaya, dan kebisingan secara real-time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    LiveErgonomicGauge(
                        score = uiState.latestSession?.overallScore ?: 100,
                        size = 112.dp,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Posisi Perangkat",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                PillSegmentedControl(
                    options = StudyMode.entries,
                    selectedOption = uiState.selectedMode,
                    onOptionSelected = viewModel::selectStudyMode,
                    labelProvider = { it.displayName },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(14.dp))

                PillButton(
                    text = "Mulai",
                    icon = Icons.Rounded.PlayArrow,
                    onClick = { onStartSession(uiState.selectedMode) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Weekly Ergonomic Trends
        item {
            LumiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Tren Mingguan",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        TabularMetricText(
                            text = "${uiState.weeklyTotalMinutes} mnt total",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                WeeklyScoreBarChart(
                    trends = uiState.weeklyTrends,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(126.dp),
                )
            }
        }

        if (uiState.recentSessions.isNotEmpty()) {
            item {
                Text(
                    text = "Skor Terbaru",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(uiState.recentSessions, key = { it.id }) { session ->
                SessionCompactItem(session = session) {
                    onOpenReport(session.id)
                }
            }
        }
    }
}

@Composable
private fun WeeklyScoreBarChart(
    trends: List<DailyTrendPoint>,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (trends.isEmpty()) return@Canvas
            val slotWidth = size.width / trends.size
            val barWidth = (slotWidth * 0.42f).coerceAtMost(22.dp.toPx())
            val maxHeight = size.height - 8.dp.toPx()

            trends.forEachIndexed { idx, point ->
                val centerX = (slotWidth * idx) + (slotWidth * 0.5f)
                val left = centerX - (barWidth * 0.5f)

                // Track background
                drawRoundRect(
                    color = colors.textTertiary.copy(alpha = 0.12f),
                    topLeft = Offset(left, 4.dp.toPx()),
                    size = Size(barWidth, maxHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )

                if (point.avgScore > 0) {
                    val barHeight = (maxHeight * (point.avgScore / 100f)).coerceAtLeast(8.dp.toPx())
                    val topY = 4.dp.toPx() + (maxHeight - barHeight)
                    val barColor = when {
                        point.avgScore >= 80 -> colors.optimal
                        point.avgScore >= 60 -> colors.warning
                        else -> colors.critical
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            trends.forEach { point ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = point.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun SessionCompactItem(
    session: StudySessionSummary,
    onClick: () -> Unit,
) {
    val colors = LumiTheme.colors
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("id-ID"))
    val dateStr = formatter.format(Date(session.startTimeMillis))
    val durationMin = (session.durationSeconds / 60).coerceAtLeast(1)

    LumiCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "$dateStr · ${session.studyMode.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                TabularMetricText(
                    text = "$durationMin mnt · ${String.format(Locale.US, "%.1f", session.avgBlinkRate)} BPM · ${String.format(Locale.US, "%.0f", session.avgDistanceCm)} cm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabularMetricText(
                    text = session.overallScore.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = when (scoreToStatus(session.overallScore)) {
                        MetricStatus.OPTIMAL -> colors.optimal
                        MetricStatus.WARNING -> colors.warning
                        MetricStatus.CRITICAL -> colors.critical
                    },
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Buka laporan",
                    tint = colors.textTertiary,
                )
            }
        }
    }
}

fun scoreToStatus(score: Int): MetricStatus = when {
    score >= 80 -> MetricStatus.OPTIMAL
    score >= 60 -> MetricStatus.WARNING
    else -> MetricStatus.CRITICAL
}

fun statusLabelForScore(score: Int): String = when {
    score >= 85 -> "Optimal"
    score >= 70 -> "Cukup Baik"
    score >= 50 -> "Perlu Perhatian"
    else -> "Berisiko"
}
