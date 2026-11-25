package com.lumiposture.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.StatusPillTag
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.home.scoreToStatus
import com.lumiposture.app.presentation.home.statusLabelForScore
import com.lumiposture.app.presentation.theme.LumiTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    hazeState: HazeState,
    onOpenReport: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(modifier = Modifier.statusBarsPadding()) {
                Text(
                    text = "Riwayat Sesi",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
            }
        }

        item {
            LumiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Total Sesi",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        TabularMetricText(
                            text = uiState.sessions.size.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                    }
                    Column {
                        Text(
                            text = "Total Durasi",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        TabularMetricText(
                            text = "${uiState.totalMinutesAllTime} mnt",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                        )
                    }
                    Column {
                        Text(
                            text = "Skor Rata-Rata",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        TabularMetricText(
                            text = if (uiState.sessions.isEmpty()) "-" else "${uiState.averageScoreAllTime}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                        )
                    }
                }
            }
        }

        if (uiState.sessions.isEmpty()) {
            item {
                LumiCard {
                    Text(
                        text = "Belum Ada Riwayat Sesi",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mulai sesi belajar pertama dari tab Beranda untuk melihat analisis ergonomi dan grafik tren.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        } else {
            items(uiState.sessions, key = { it.id }) { session ->
                val dateStr = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("id-ID"))
                    .format(Date(session.startTimeMillis))
                val durationMin = (session.durationSeconds / 60).coerceAtLeast(1)
                val status = scoreToStatus(session.overallScore)

                LumiCard(onClick = { onOpenReport(session.id) }) {
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
                                text = "$dateStr · ${session.studyMode.displayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                            TabularMetricText(
                                text = "$durationMin mnt · ${String.format(Locale.US, "%.1f", session.avgBlinkRate)} BPM · ${String.format(Locale.US, "%.0f", session.avgDistanceCm)} cm · ${String.format(Locale.US, "%.0f", session.avgNeckAngleDeg)} deg",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            StatusPillTag(
                                status = status,
                                label = session.statusLabel ?: statusLabelForScore(session.overallScore),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TabularMetricText(
                                text = session.overallScore.toString(),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = when (status) {
                                    MetricStatus.OPTIMAL -> colors.optimal
                                    MetricStatus.WARNING -> colors.warning
                                    MetricStatus.CRITICAL -> colors.critical
                                },
                            )
                            IconButton(
                                onClick = { viewModel.deleteSession(session.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Hapus sesi",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
