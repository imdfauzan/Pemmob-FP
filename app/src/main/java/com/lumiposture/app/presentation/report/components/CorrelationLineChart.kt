package com.lumiposture.app.presentation.report.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumiposture.app.domain.model.TelemetrySamplePoint
import com.lumiposture.app.presentation.components.PillSegmentedControl
import com.lumiposture.app.presentation.theme.LumiTheme
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme

enum class ChartMetricMode(val label: String) {
    SCORE("Skor"),
    BLINK_AND_LUX("Mata"),
    NECK_AND_NOISE("Postur"),
}

@Composable
fun CorrelationLineChart(
    samples: List<TelemetrySamplePoint>,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    var selectedMode by remember { mutableStateOf(ChartMetricMode.BLINK_AND_LUX) }
    val modelProducer = remember { CartesianChartModelProducer() }

    val safeSamples = remember(samples, selectedMode) {
        val runs = mutableListOf<MutableList<TelemetrySamplePoint>>()
        var current = mutableListOf<TelemetrySamplePoint>()
        for (point in samples) {
            val valid = when (selectedMode) {
                ChartMetricMode.SCORE -> point.ergonomicScore != null
                ChartMetricMode.BLINK_AND_LUX -> point.blinkRateBpm != null && point.ambientLux != null
                ChartMetricMode.NECK_AND_NOISE -> point.neckAngleDeg != null && point.noiseDb != null
            }
            if (!valid || (current.isNotEmpty() && point.elapsedSeconds - current.last().elapsedSeconds > 10)) {
                if (current.isNotEmpty()) runs += current
                current = mutableListOf()
            }
            if (valid) current += point
        }
        if (current.isNotEmpty()) runs += current
        runs.maxByOrNull { it.size }?.takeIf { it.size >= 2 }.orEmpty()
    }

    LaunchedEffect(safeSamples, selectedMode) {
        if (safeSamples.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineModel {
                when (selectedMode) {
                    ChartMetricMode.SCORE -> {
                        series(x = safeSamples.map { it.elapsedSeconds }, y = safeSamples.map { requireNotNull(it.ergonomicScore) })
                    }
                    ChartMetricMode.BLINK_AND_LUX -> {
                        series(x = safeSamples.map { it.elapsedSeconds }, y = safeSamples.map { requireNotNull(it.blinkRateBpm) })
                        series(x = safeSamples.map { it.elapsedSeconds }, y = safeSamples.map { requireNotNull(it.ambientLux) / 10f })
                    }
                    ChartMetricMode.NECK_AND_NOISE -> {
                        series(x = safeSamples.map { it.elapsedSeconds }, y = safeSamples.map { requireNotNull(it.neckAngleDeg) })
                        series(x = safeSamples.map { it.elapsedSeconds }, y = safeSamples.map { requireNotNull(it.noiseDb) })
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PillSegmentedControl(
            options = ChartMetricMode.entries,
            selectedOption = selectedMode,
            onOptionSelected = { selectedMode = it },
            labelProvider = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text("Segmen data lengkap terpanjang; waktu dalam detik. Perubahan bersama bukan bukti sebab-akibat.",
            style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)

        if (safeSamples.isEmpty()) {
            Text(
                text = "Belum ada sampel telemetri yang cukup untuk grafik sesi ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return
        }

        val line1Color = when (selectedMode) {
            ChartMetricMode.SCORE -> colors.primary
            ChartMetricMode.BLINK_AND_LUX -> colors.optimal
            ChartMetricMode.NECK_AND_NOISE -> colors.warning
        }
        val line2Color = when (selectedMode) {
            ChartMetricMode.SCORE -> colors.primary
            ChartMetricMode.BLINK_AND_LUX -> colors.primary
            ChartMetricMode.NECK_AND_NOISE -> colors.critical
        }

        ProvideVicoTheme(theme = rememberM3VicoTheme()) {
            val primaryLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(line1Color)),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(line1Color.copy(alpha = 0.14f))),
            )
            val secondaryLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(line2Color)),
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            if (selectedMode == ChartMetricMode.SCORE) {
                                listOf(primaryLine)
                            } else {
                                listOf(primaryLine, secondaryLine)
                            },
                        ),
                    ),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (selectedMode) {
                ChartMetricMode.SCORE -> {
                    ChartLegendItem("Skor Ergonomi (0-100)", colors.primary)
                }
                ChartMetricMode.BLINK_AND_LUX -> {
                    ChartLegendItem("Kedipan (BPM)", colors.optimal)
                    ChartLegendItem("Cahaya (x10 Lux)", colors.primary)
                }
                ChartMetricMode.NECK_AND_NOISE -> {
                    ChartLegendItem("Est. orientasi (deg)", colors.warning)
                    ChartLegendItem("Mic (dBFS)", colors.critical)
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color = color, radius = 4.dp.toPx())
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LumiTheme.colors.textSecondary,
        )
    }
}
