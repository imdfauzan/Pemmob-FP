package com.lumiposture.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.SecondaryPillButton
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.theme.LumiTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    hazeState: HazeState,
    onRecalibrateClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = uiState.preferences
    val caps = uiState.capabilities
    val colors = LumiTheme.colors
    var showClearConfirm by remember { mutableStateOf(value = false) }

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
                    text = "Setelan",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
            }
        }

        // Calibration Card
        item {
            LumiCard {
                Text(
                    text = "Kalibrasi Baseline Wajah",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TabularMetricText(
                    text = if (prefs.isCalibrated) {
                        String.format(Locale.US, "IPD Baseline aktif: %.1f px", prefs.baselineIpdPx)
                    } else {
                        "Belum dikalibrasi (menggunakan standar 86.0 px)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))
                SecondaryPillButton(
                    text = "Kalibrasi Ulang Jarak Wajah",
                    onClick = onRecalibrateClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Ergonomic Thresholds Card
        item {
            LumiCard {
                Text(
                    text = "Ambang Batas Peringatan",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(14.dp))

                ThresholdSliderRow(
                    label = "Jarak Pandang Minimum",
                    valueText = "${prefs.minDistanceThresholdCm} cm",
                    value = prefs.minDistanceThresholdCm.toFloat(),
                    range = 20f..45f,
                ) { viewModel.updateMinDistanceCm(it.roundToInt()) }

                ThresholdSliderRow(
                    label = "Batas Sudut Leher (Tech-Neck)",
                    valueText = "${prefs.maxNeckAngleThresholdDeg} deg",
                    value = prefs.maxNeckAngleThresholdDeg.toFloat(),
                    range = 20f..45f,
                ) { viewModel.updateMaxNeckAngleDeg(it.roundToInt()) }

                ThresholdSliderRow(
                    label = "Cahaya Ruangan Minimum",
                    valueText = "${prefs.minLuxThreshold} Lux",
                    value = prefs.minLuxThreshold.toFloat(),
                    range = 40f..200f,
                ) { viewModel.updateMinLuxThreshold(it.roundToInt()) }

                ThresholdSliderRow(
                    label = "Interval Istirahat 20-20-20",
                    valueText = "${prefs.eyeBreakIntervalMinutes} mnt",
                    value = prefs.eyeBreakIntervalMinutes.toFloat(),
                    range = 5f..45f,
                ) { viewModel.updateEyeBreakMinutes(it.roundToInt()) }
            }
        }

        // Toggles: Haptics & Debug Telemetry Overlay (F6)
        item {
            LumiCard {
                Text(
                    text = "Preferensi & Mode Presentasi",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))

                ToggleSettingRow(
                    title = "Getaran Peringatan",
                    subtitle = "Getaran saat jarak terlalu dekat atau leher menunduk",
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                )

                Spacer(modifier = Modifier.height(10.dp))

                ToggleSettingRow(
                    title = "Telemetry Debug",
                    subtitle = "Tampilkan angka telemetri di layar",
                    checked = prefs.debugTelemetryEnabled,
                    onCheckedChange = viewModel::setDebugTelemetryEnabled,
                )
            }
        }

        // Hardware & API Diagnostics
        item {
            LumiCard {
                Text(
                    text = "Status Sistem & Sensor",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                StatusLine("Sensor Aktif", "${caps.availableHardwareCount} dari 6 modul")
                StatusLine(
                    "Sensor Gerak",
                    if (caps.hasRotationVector) "Rotation Vector (Gyro+Accel)" else "Accelerometer Low-Pass",
                )
                StatusLine(
                    "Gemini API Key",
                    if (uiState.isGeminiKeyConfigured) "Terkonfigurasi" else "Tidak Ada (Pakai Fallback Lokal)",
                )
                Spacer(modifier = Modifier.height(14.dp))
                SecondaryPillButton(
                    text = "Hapus Semua Riwayat Sesi",
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus Riwayat?") },
            text = { Text("Seluruh data sesi belajar dan insight yang tersimpan di perangkat akan dihapus permanen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirm = false
                    },
                ) {
                    Text("Hapus", color = colors.critical)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Batal")
                }
            },
        )
    }
}

@Composable
private fun ThresholdSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val colors = LumiTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            TabularMetricText(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
            ),
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LumiTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.cardSurface,
                checkedTrackColor = colors.primary,
            ),
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    val colors = LumiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
        )
    }
}
