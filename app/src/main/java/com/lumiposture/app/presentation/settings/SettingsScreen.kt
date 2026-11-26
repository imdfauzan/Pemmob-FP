package com.lumiposture.app.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.PillButton
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
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    var apiKeyDraft by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKeySavedNotice by remember { mutableStateOf(false) }

    LaunchedEffect(prefs.geminiApiKey) {
        apiKeyDraft = prefs.geminiApiKey
    }

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

        // In-App Gemini API Key Configuration Card
        item {
            LumiCard {
                Text(
                    text = "Gemini API Key",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Digunakan untuk menganalisis hasil pengecekan Ergonomi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = {
                        apiKeyDraft = it
                        apiKeySavedNotice = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Masukkan API key (AIza...)") },
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (apiKeyVisible) "Sembunyikan" else "Tampilkan",
                                tint = colors.textSecondary,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.textTertiary.copy(alpha = 0.35f),
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary,
                    ),
                )

                if (apiKeySavedNotice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "API key berhasil disimpan ke perangkat.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.optimal,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.isGeminiKeyConfigured) {
                        SecondaryPillButton(
                            text = "Hapus",
                            onClick = {
                                apiKeyDraft = ""
                                apiKeySavedNotice = false
                                viewModel.clearGeminiApiKey()
                            },
                            modifier = Modifier.weight(0.42f),
                        )
                    }
                    PillButton(
                        text = "Simpan API Key",
                        onClick = {
                            viewModel.saveGeminiApiKey(apiKeyDraft)
                            apiKeySavedNotice = apiKeyDraft.isNotBlank()
                        },
                        enabled = apiKeyDraft.isNotBlank() && apiKeyDraft.trim() != prefs.geminiApiKey,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }

        // Calibration Card
        item {
            LumiCard {
                Text(
                    text = "Kalibrasi Baseline Wajah (30 cm)",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TabularMetricText(
                    text = if (prefs.isCalibrated) {
                        String.format(Locale.US, "IPD Baseline aktif: %.1f px", prefs.baselineIpdPx)
                    } else {
                        "Belum dikalibrasi (menggunakan standar 115.0 px)"
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
                    range = 15f..40f,
                ) { viewModel.updateMinDistanceCm(it.roundToInt()) }

                ThresholdSliderRow(
                    label = "Batas Estimasi Orientasi HP/Kepala",
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
            }
        }

        // Toggles: Haptics & Debug Telemetry Overlay (F6)
        item {
            LumiCard {
                Text(
                    text = "Preferensi",
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

        // Per-Sensor Controls & System Status
        item {
            LumiCard {
                SecondaryPillButton(
                    text = "Buka Izin Kamera dan Mikrofon",
                    onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Status Sistem & Sensor",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusLine(
                    "Gemini API Key",
                    if (uiState.isGeminiKeyConfigured) "Tersimpan di Aplikasi" else "Belum Diatur (Fallback Lokal)",
                )
                Spacer(modifier = Modifier.height(12.dp))

                SensorToggleRow(
                    title = "Kamera Depan",
                    hardwareAvailable = caps.hasFrontCamera,
                    description = "Pendeteksi kedipan mata, jarak IPD, dan head pitch",
                    checked = prefs.enableFrontCamera && caps.hasFrontCamera,
                    onCheckedChange = viewModel::setFrontCameraEnabled,
                )
                Spacer(modifier = Modifier.height(10.dp))

                SensorToggleRow(
                    title = "Ambient Light (Lux)",
                    hardwareAvailable = caps.hasLightSensor,
                    description = "Pemantauan intensitas cahaya ruangan",
                    checked = prefs.enableLightSensor && caps.hasLightSensor,
                    onCheckedChange = viewModel::setLightSensorEnabled,
                )
                Spacer(modifier = Modifier.height(10.dp))

                SensorToggleRow(
                    title = "Proximity Sensor",
                    hardwareAvailable = caps.hasProximitySensor,
                    description = "Sinyal objek dekat/jauh, bukan jarak dalam cm",
                    checked = prefs.enableProximitySensor && caps.hasProximitySensor,
                    onCheckedChange = viewModel::setProximitySensorEnabled,
                )
                Spacer(modifier = Modifier.height(10.dp))

                SensorToggleRow(
                    title = "Accelerometer",
                    hardwareAvailable = caps.hasAccelerometer,
                    description = "Kemiringan HP, fallback bila rotation vector tidak dipakai",
                    checked = prefs.enableAccelerometer && caps.hasAccelerometer,
                    onCheckedChange = viewModel::setAccelerometerEnabled,
                )
                Spacer(modifier = Modifier.height(10.dp))

                val hasGyroOrFusion = caps.hasGyroscope || caps.hasRotationVector
                SensorToggleRow(
                    title = "Gyroscope / Rotation Vector",
                    hardwareAvailable = hasGyroOrFusion,
                    description = "Menstabilisasi sudut kemiringan HP",
                    checked = prefs.enableGyroscope && hasGyroOrFusion,
                    onCheckedChange = viewModel::setGyroscopeEnabled,
                )
                Spacer(modifier = Modifier.height(10.dp))

                SensorToggleRow(
                    title = "Amplitudo Mikrofon (dBFS)",
                    hardwareAvailable = caps.hasMicrophone,
                    description = "Amplitudo digital tanpa menyimpan file audio, belum dB SPL",
                    checked = prefs.enableMicrophone && caps.hasMicrophone,
                    onCheckedChange = viewModel::setMicrophoneEnabled,
                )

                Spacer(modifier = Modifier.height(16.dp))
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
private fun SensorToggleRow(
    title: String,
    hardwareAvailable: Boolean,
    description: String,
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
                color = if (hardwareAvailable) colors.textPrimary else colors.textTertiary,
            )
            Text(
                text = if (hardwareAvailable) description else "Sensor tidak tersedia di perangkat",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hardwareAvailable) colors.textSecondary else colors.warning,
            )
        }
        Switch(
            modifier = Modifier.semantics { contentDescription = title },
            checked = checked,
            enabled = hardwareAvailable,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.cardSurface,
                checkedTrackColor = colors.primary,
            ),
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
            modifier = Modifier.semantics { contentDescription = label },
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
            modifier = Modifier.semantics { contentDescription = title },
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
