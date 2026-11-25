package com.lumiposture.app.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.presentation.components.GlassSurface
import com.lumiposture.app.presentation.components.LumiCard
import com.lumiposture.app.presentation.components.PillButton
import com.lumiposture.app.presentation.components.SecondaryPillButton
import com.lumiposture.app.presentation.components.StatusPillTag
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.session.components.CameraPreviewWithMesh
import com.lumiposture.app.presentation.theme.GlassCardShape
import com.lumiposture.app.presentation.theme.HudTextPrimary
import com.lumiposture.app.presentation.theme.HudTextSecondary
import com.lumiposture.app.presentation.theme.LumiTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale

@Composable
fun CalibrationScreen(
    fromSettings: Boolean,
    onCalibrationFinished: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CalibrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LumiTheme.colors

    fun checkPermissions(): Boolean {
        val camGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        // Camera is mandatory; Mic is requested alongside for full 6-sensor fusion
        return camGranted
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.updatePermissionState(checkPermissions())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updatePermissionState(checkPermissions())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.updatePermissionState(checkPermissions())
    }

    LaunchedEffect(uiState.calibrationCompleted) {
        if (uiState.calibrationCompleted) {
            onCalibrationFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (fromSettings) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = colors.textPrimary,
                    )
                }
            }
            Column {
                Text(
                    text = if (fromSettings) "Kalibrasi Ulang Wajah" else "Persiapan LumiPosture",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Edge AI & 6-Sensor Ergonomic Calibration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        if (!uiState.permissionsGranted) {
            PrivacyPermissionStep(
                onRequestPermissions = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                        ),
                    )
                },
            ) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            }
        } else {
            val previewHazeState = rememberHazeState()
            val vision = uiState.visionTelemetry

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(GlassCardShape)
                    .background(Color(0xFF10131A)),
            ) {
                CameraPreviewWithMesh(
                    faceLandmarkerHelper = viewModel.faceLandmarkerHelper,
                    landmarks = vision.landmarkPoints,
                    leftIris = vision.leftIrisPoint,
                    rightIris = vision.rightIrisPoint,
                    frameWidth = vision.frameWidth,
                    frameHeight = vision.frameHeight,
                    showMeshOverlay = true,
                    isActive = uiState.permissionsGranted,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(previewHazeState),
                )

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(14.dp),
                    hazeState = previewHazeState,
                    isDarkOverlay = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = if (vision.faceDetected) "Wajah Terdeteksi" else "Posisikan Wajah",
                                style = MaterialTheme.typography.titleMedium,
                                color = HudTextPrimary,
                            )
                            Text(
                                text = "Pegang ponsel pada jarak nyaman (~30 cm)",
                                style = MaterialTheme.typography.labelMedium,
                                color = HudTextSecondary,
                            )
                        }
                        TabularMetricText(
                            text = String.format(Locale.US, "%.1f px", vision.ipdPixels),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = HudTextPrimary,
                        )
                    }
                }

                if (uiState.isCalibrating) {
                    GlassSurface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(14.dp),
                        hazeState = previewHazeState,
                        isDarkOverlay = true,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Merekam Baseline IPD...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HudTextPrimary,
                                )
                                TabularMetricText(
                                    text = "${uiState.collectedSamplesCount} sampel iris terkumpul",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HudTextSecondary,
                                )
                            }
                            TabularMetricText(
                                text = "${uiState.countdownSeconds}s",
                                style = MaterialTheme.typography.headlineLarge,
                                color = colors.primary,
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                LumiCard(innerPadding = 16.dp) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.critical,
                    )
                }
            }

            if (uiState.isCalibrating) {
                SecondaryPillButton(
                    text = "Batalkan Kalibrasi",
                    onClick = viewModel::cancelCalibration,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                PillButton(
                    text = "Mulai Kalibrasi 10 Detik",
                    onClick = viewModel::startCalibration,
                    enabled = vision.faceDetected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SensorDiagnosticsCard(uiState)
        }
    }
}

@Composable
private fun PrivacyPermissionStep(
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val colors = LumiTheme.colors

    LumiCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = colors.primary,
            )
            Text(
                text = "Privasi Terjamin (100% On-Device)",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Seluruh pemrosesan frame kamera dan amplitudo mikrofon dijalankan langsung di RAM perangkat. Tidak ada foto, video, atau file audio yang pernah disimpan maupun dikirim ke server.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(18.dp))

        PermissionDetailRow(
            icon = Icons.Rounded.CameraFront,
            title = "Kamera Depan (MediaPipe Face Landmarker)",
            description = "Mengukur jarak iris ke layar, frekuensi kedipan mata, dan sudut anggukan kepala secara offline.",
        )
        Spacer(modifier = Modifier.height(14.dp))
        PermissionDetailRow(
            icon = Icons.Rounded.GraphicEq,
            title = "Mikrofon (Pengukur Desibel Ambien)",
            description = "Membaca intensitas kebisingan ruangan dalam dB tanpa merekam atau menyimpan suara.",
        )
    }

    PillButton(
        text = "Berikan Izin Sensor",
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth(),
    )

    SecondaryPillButton(
        text = "Buka Pengaturan Izin",
        onClick = onOpenAppSettings,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PermissionDetailRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val colors = LumiTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun SensorDiagnosticsCard(uiState: CalibrationUiState) {
    val caps = uiState.capabilities
    val colors = LumiTheme.colors

    LumiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Diagnostik Hardware Sensor",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            StatusPillTag(
                status = if (caps.availableHardwareCount >= 5) MetricStatus.OPTIMAL else MetricStatus.WARNING,
                label = "${caps.availableHardwareCount}/6 Aktif",
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        DiagnosticRow(name = "Kamera Depan", available = caps.hasFrontCamera, fallbackLabel = "Tidak Ada")
        DiagnosticRow(name = "Ambient Light (Lux)", available = caps.hasLightSensor, fallbackLabel = "Fallback Default")
        DiagnosticRow(name = "Proximity Sensor", available = caps.hasProximitySensor, fallbackLabel = "Tidak Ada")
        DiagnosticRow(name = "Accelerometer", available = caps.hasAccelerometer, fallbackLabel = "Tidak Ada")
        DiagnosticRow(name = "Gyroscope / Rotation", available = caps.hasGyroscope || caps.hasRotationVector, fallbackLabel = "Fallback Accel")
        DiagnosticRow(name = "Mikrofon", available = caps.hasMicrophone, fallbackLabel = "Tidak Ada")
    }
}

@Composable
private fun DiagnosticRow(
    name: String,
    available: Boolean,
    okLabel: String = "Tersedia",
    fallbackLabel: String,
) {
    val colors = LumiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (available) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                contentDescription = null,
                tint = if (available) colors.optimal else colors.warning,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (available) okLabel else fallbackLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (available) colors.optimal else colors.warning,
            )
        }
    }
}
