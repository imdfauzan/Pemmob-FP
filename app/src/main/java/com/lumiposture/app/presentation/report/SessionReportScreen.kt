package com.lumiposture.app.presentation.report

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.CircularProgressIndicator
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
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.SecondaryPillButton
import com.lumiposture.app.presentation.components.StatusPillTag
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.home.scoreToStatus
import com.lumiposture.app.presentation.components.metric
import com.lumiposture.app.presentation.home.statusLabelForScore
import com.lumiposture.app.presentation.report.components.CorrelationLineChart
import com.lumiposture.app.presentation.session.components.LiveErgonomicGauge
import com.lumiposture.app.presentation.theme.LumiTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SessionReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LumiTheme.colors

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }
    if (uiState.report == null) {
        Column(Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().padding(24.dp)) {
            Text("Laporan tidak ditemukan atau gagal dimuat.", color = colors.textPrimary)
            SecondaryPillButton(text = "Kembali", onClick = onNavigateBack)
        }
        return
    }

    val report = uiState.report!!
    val session = report.session
    val insight = report.insight
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        .format(Date(session.startTimeMillis))
    val durationMins = (session.durationSeconds / 60f).roundToInt().coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = colors.textPrimary,
                    )
                }
                Column {
                    Text(
                        text = "Hasil Pengecekan",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "$dateStr · ${session.studyMode.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        item {
            Text("Skor memakai metrik valid dan dapat parsial. Estimasi orientasi bukan sudut leher anatomis; dBFS bukan dB SPL. Kedipan bukan diagnosis. Grafik bukan bukti sebab-akibat.",
                style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        item {
            LumiCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatusPillTag(
                            status = scoreToStatus(session.overallScore),
                            label = session.statusLabel ?: statusLabelForScore(session.overallScore),
                        )
                        TabularMetricText(
                            text = "$durationMins menit sesi",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                        )
                        TabularMetricText(
                            text = "${session.totalWarningsTriggered} intervensi terpicu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    LiveErgonomicGauge(
                        score = session.overallScore,
                        isPartial = session.overallScore != null && listOf(session.avgBlinkRate, session.avgDistanceCm, session.avgNeckAngleDeg, session.avgLux).any { it == null },
                        size = 118.dp,
                    )
                }
            }
        }

        // 5-Sensor Averages Card
        item {
            LumiCard {
                Text(
                    text = "Rata-rata Metrik Valid",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ReportMetricStat("Est. kedipan", session.avgBlinkRate.metric("BPM"), Modifier.weight(1f))
                    ReportMetricStat("Est. jarak", session.avgDistanceCm.metric("cm"), Modifier.weight(1f))
                    ReportMetricStat("Est. orientasi", session.avgNeckAngleDeg.metric("deg"), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ReportMetricStat("Cahaya", session.avgLux.metric("Lux", 0), Modifier.weight(1f))
                    ReportMetricStat("Amplitudo mic", session.avgNoiseDb.metric("dBFS"), Modifier.weight(1f))
                    ReportMetricStat("Sampel", "${report.samples.size} titik", Modifier.weight(1f))
                }
            }
        }

        // Vico Correlation Line Chart Card
        item {
            LumiCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timeline,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Grafik Sesi",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                CorrelationLineChart(samples = report.samples)
            }
        }

        // AI Analytical Insight Card
        if (insight != null) {
            item {
                LumiCard {
                    Text(
                        text = if (insight.isGeneratedByCloudAi) {
                            "Analisis Sesi (Gemini)"
                        } else {
                            "Ringkasan Aturan Lokal"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = insight.primaryRiskFactor,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = insight.executiveSummary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )

                    if (insight.sensorCorrelations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Observasi Data Sensor",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        insight.sensorCorrelations.forEachIndexed { index, itemText ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TabularMetricText(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.primary,
                                )
                                Text(
                                    text = itemText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    if (insight.actionableRecommendations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Rekomendasi Perbaikan",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        insight.actionableRecommendations.forEach { rec ->
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = colors.optimal,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                )
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }

                    if (!insight.isGeneratedByCloudAi) {
                        uiState.analysisErrorMessage?.let { reason ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.warning,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        SecondaryPillButton(
                            text = if (uiState.isReanalyzing) "Menganalisis Ulang..." else "Analisis Ulang dengan Gemini",
                            icon = Icons.Rounded.Refresh,
                            enabled = !uiState.isReanalyzing,
                            onClick = viewModel::reanalyzeWithGemini,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        TabularMetricText(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
}
