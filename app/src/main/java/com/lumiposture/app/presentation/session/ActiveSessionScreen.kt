package com.lumiposture.app.presentation.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.lumiposture.app.presentation.components.metric
import com.lumiposture.app.domain.model.ActiveAlert
import com.lumiposture.app.domain.model.MetricStatus
import com.lumiposture.app.domain.model.StudyMode
import com.lumiposture.app.presentation.components.GlassSurface
import com.lumiposture.app.presentation.components.PillButton
import com.lumiposture.app.presentation.components.PillSegmentedControl
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.session.components.CameraPreviewWithMesh
import com.lumiposture.app.presentation.session.components.DebugTelemetryOverlay
import com.lumiposture.app.presentation.session.components.EyeBreakModalDialog
import com.lumiposture.app.presentation.session.components.LiveErgonomicGauge
import com.lumiposture.app.presentation.theme.DarkCanvas
import com.lumiposture.app.presentation.theme.DarkOptimal
import com.lumiposture.app.presentation.theme.DarkWarning
import com.lumiposture.app.presentation.theme.DarkCritical
import com.lumiposture.app.presentation.theme.HudTextPrimary
import com.lumiposture.app.presentation.theme.HudTextSecondary
import com.lumiposture.app.presentation.theme.InnerTileShape
import com.lumiposture.app.presentation.theme.LumiTheme
import com.lumiposture.app.presentation.theme.PillShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.util.Locale

@Composable
fun ActiveSessionScreen(
    onSessionFinished: (Long) -> Unit,
    onMissingCameraPermission: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LumiTheme.colors
    val hazeState = rememberHazeState()

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    LifecycleResumeEffect(Unit) {
        viewModel.setForeground(true, ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        onPauseOrDispose { viewModel.setForeground(false, false) }
    }

    LaunchedEffect(uiState.completedSessionId) {
        uiState.completedSessionId?.let { id ->
            onSessionFinished(id)
        }
    }

    BackHandler(enabled = !uiState.isEndingSession) {
        viewModel.endSession()
    }

    val snapshot = uiState.snapshot
    val prefs = uiState.preferences
    val scoredMetricCount = listOf(snapshot.blinkStatus, snapshot.distanceStatus, snapshot.postureStatus, snapshot.lightingStatus)
        .count { it != MetricStatus.UNAVAILABLE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas),
    ) {
        // Layer 1: Camera Feed + Minimal Mesh Overlay (hazeSource for real frosted glass HUD)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            if (hasCameraPermission && prefs.enableFrontCamera) {
                CameraPreviewWithMesh(
                    faceLandmarkerHelper = viewModel.faceLandmarkerHelper,
                    landmarks = snapshot.landmarkPoints,
                    leftIris = snapshot.leftIrisPoint,
                    rightIris = snapshot.rightIrisPoint,
                    frameWidth = snapshot.frameWidth,
                    frameHeight = snapshot.frameHeight,
                    showMeshOverlay = uiState.showMeshOverlay && uiState.showCameraPreview,
                    isActive = uiState.isMonitoring && !uiState.isEndingSession && prefs.enableFrontCamera,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!prefs.enableFrontCamera) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkCanvas),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "Kamera Depan dinonaktifkan di Setelan",
                        modifier = Modifier.padding(top = 136.dp, start = 16.dp, end = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary,
                    )
                }
            } else if (!uiState.showCameraPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkCanvas),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "Pratinjau kamera disembunyikan · Pemantauan aktif",
                        modifier = Modifier.padding(top = 136.dp, start = 16.dp, end = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary,
                    )
                }
            } else {
                // Flat subtle translucent scrim for HUD readability (zero gradients)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.24f)),
                )
            }
        }

        // Layer 2: Foreground Glass HUD
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Controls & Floating Alert Banner
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassSurface(
                        hazeState = hazeState,
                        shape = PillShape,
                        isDarkOverlay = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(PillShape)
                                    .background(
                                        if (snapshot.faceDetected) colors.optimal else colors.warning,
                                    ),
                            )
                            TabularMetricText(
                                text = formatElapsed(snapshot.elapsedSeconds),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = HudTextPrimary,
                            )
                        }
                    }

                    PillSegmentedControl(
                        options = StudyMode.entries,
                        selectedOption = uiState.studyMode,
                        onOptionSelected = viewModel::setStudyMode,
                        labelProvider = { it.displayName },
                        hazeState = hazeState,
                        isDarkOverlay = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )

                    GlassSurface(
                        hazeState = hazeState,
                        shape = PillShape,
                        isDarkOverlay = true,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = viewModel::toggleMeshOverlay,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GridOn,
                                    contentDescription = "Toggle mesh",
                                    tint = if (uiState.showMeshOverlay) colors.primary else HudTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = viewModel::toggleCameraPreview,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (uiState.showCameraPreview) {
                                        Icons.Rounded.Visibility
                                    } else {
                                        Icons.Rounded.VisibilityOff
                                    },
                                    contentDescription = "Toggle preview",
                                    tint = HudTextPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // Floating Pill Alert Banner
                val bannerAlert = uiState.activeAlert.takeUnless {
                    (it is ActiveAlert.None) || (it is ActiveAlert.EyeBreakRequired)
                }
                AnimatedVisibility(
                    visible = bannerAlert != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    if (bannerAlert != null) {
                        FloatingAlertPill(
                            alert = bannerAlert,
                            hazeState = hazeState,
                        )
                    }
                }

                if (uiState.debugTelemetryEnabled) {
                    DebugTelemetryOverlay(
                        snapshot = snapshot,
                        hazeState = hazeState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Bottom Ergonomic Gauge + 5 Sensor Cards + End Session CTA
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassSurface(
                    hazeState = hazeState,
                    isDarkOverlay = true,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    LiveErgonomicGauge(
                        score = snapshot.ergonomicScore,
                        size = 136.dp,
                        onDarkSurface = true,
                        isPartial = scoredMetricCount in 1..3,
                    )
                }

                Text(
                    text = uiState.saveError ?: when (scoredMetricCount) {
                        0 -> "Belum ada metrik valid untuk menghitung skor."
                        in 1..3 -> "Skor parsial: $scoredMetricCount/4 metrik valid. Estimasi, bukan diagnosis."
                        else -> "4/4 metrik valid. Indeks aturan, bukan diagnosis."
                    },
                    color = HudTextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HudMetricTile(
                        icon = Icons.Rounded.Straighten,
                        label = "Est. jarak",
                        value = if (snapshot.isProximityTriggered) "Objek dekat" else snapshot.distanceCm?.metric("cm", 0) ?: snapshot.distanceDataStatus.label,
                        status = snapshot.distanceStatus,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                    )
                    HudMetricTile(
                        icon = Icons.Rounded.RemoveRedEye,
                        label = "Kedipan",
                        value = snapshot.blinkRateBpm?.metric("BPM") ?: snapshot.blinkDataStatus.label,
                        status = snapshot.blinkStatus,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                    )
                    val motionEnabled = prefs.enableAccelerometer || prefs.enableGyroscope
                    HudMetricTile(
                        icon = Icons.Rounded.AccessibilityNew,
                        label = "Est. orientasi",
                        value = snapshot.neckAngleDeg?.metric("deg", 0) ?: snapshot.postureDataStatus.label,
                        status = snapshot.postureStatus,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HudMetricTile(
                        icon = Icons.Rounded.LightMode,
                        label = "Cahaya",
                        value = snapshot.ambientLux?.metric("lx", 0) ?: snapshot.lightDataStatus.label,
                        status = snapshot.lightingStatus,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                    )
                    HudMetricTile(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Amplitudo mic",
                        value = snapshot.noiseDb?.metric("dBFS") ?: snapshot.noiseDataStatus.label,
                        status = snapshot.noiseStatus,
                        hazeState = hazeState,
                        modifier = Modifier.weight(1f),
                    )
                }

                PillButton(
                    text = "Akhiri Sesi",
                    icon = Icons.Rounded.Stop,
                    onClick = viewModel::endSession,
                    enabled = !uiState.isEndingSession,
                    containerColor = colors.critical,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Dynamic 20-20-20 Eye Break Modal
        val eyeBreakAlert = uiState.activeAlert as? ActiveAlert.EyeBreakRequired
        if (eyeBreakAlert != null && !uiState.isEndingSession) {
            EyeBreakModalDialog(
                reason = eyeBreakAlert.reason,
                onCompleted = viewModel::completeEyeBreak,
                onDismissSnooze = viewModel::snoozeEyeBreak,
            )
        }

        // Post-session aggregation & Gemini analysis overlay
        if (uiState.isEndingSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkCanvas.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = colors.primary)
                    Text(
                        text = "Menyimpan telemetri dan menyusun analisis...",
                        style = MaterialTheme.typography.titleMedium,
                        color = HudTextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingAlertPill(
    alert: ActiveAlert,
    hazeState: HazeState,
) {
    val colors = LumiTheme.colors
    val (accent, message) = when (alert) {
        is ActiveAlert.DistanceWarning -> {
            val c = if (alert.isCriticalProximity) DarkCritical else DarkWarning
            c to alert.message
        }
        is ActiveAlert.PostureWarning -> DarkWarning to alert.message
        is ActiveAlert.GlareWarning -> DarkWarning to alert.message
        else -> DarkWarning to ""
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        shape = PillShape,
        isDarkOverlay = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = HudTextPrimary,
            )
        }
    }
}

@Composable
private fun HudMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    status: MetricStatus,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = LumiTheme.colors
    val dotColor = when (status) {
        MetricStatus.UNAVAILABLE -> HudTextSecondary
        MetricStatus.OPTIMAL -> DarkOptimal
        MetricStatus.WARNING -> DarkWarning
        MetricStatus.CRITICAL -> DarkCritical
    }

    GlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = InnerTileShape,
        isDarkOverlay = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = HudTextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(PillShape)
                        .background(dotColor),
                )
            }
            TabularMetricText(
                text = value,
                maxLines = 2,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = HudTextPrimary,
            )
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
