package com.lumiposture.app.presentation.session.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumiposture.app.domain.model.SensorSnapshot
import com.lumiposture.app.presentation.components.GlassSurface
import com.lumiposture.app.presentation.components.TabularMetricText
import com.lumiposture.app.presentation.components.metric
import com.lumiposture.app.domain.model.DataStatus
import com.lumiposture.app.presentation.theme.HudTextPrimary
import com.lumiposture.app.presentation.theme.HudTextSecondary
import dev.chrisbanes.haze.HazeState
import java.util.Locale

@Composable
fun DebugTelemetryOverlay(
    snapshot: SensorSnapshot,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier,
        hazeState = hazeState,
        shape = RoundedCornerShape(16.dp),
        isDarkOverlay = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "TELEMETRI SENSOR MENTAH",
                style = MaterialTheme.typography.labelSmall,
                color = HudTextSecondary,
            )
            DebugRow("Lux / Layar", "${snapshot.ambientLux.metric("lx", 0)} | ${snapshot.screenBrightnessPct}%")
            DebugRow("Amplitudo mic", snapshot.noiseDb?.metric("dBFS") ?: snapshot.noiseDataStatus.label)
            DebugRow("Est. jarak", snapshot.distanceCm?.metric("cm") ?: snapshot.distanceDataStatus.label)
            DebugRow("Pitch HP / Kepala", "${snapshot.devicePitchDeg.metric("deg")} | ${snapshot.headPitchDeg.metric("deg")}")
            DebugRow("Est. orientasi", snapshot.neckAngleDeg?.metric("deg") ?: snapshot.postureDataStatus.label)
            DebugRow("Kamera / Kedipan", "${snapshot.cameraDataStatus.label} | ${snapshot.blinkDataStatus.label}")
            DebugRow("Vision FPS / Kedipan", String.format(Locale.US, "%.1f fps | %d", snapshot.inferenceFps, snapshot.totalBlinks))
            DebugRow("Proximity", if (snapshot.proximityDataStatus != DataStatus.AVAILABLE) snapshot.proximityDataStatus.label else if (snapshot.isProximityTriggered) "DEKAT" else "JAUH")
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = HudTextSecondary,
        )
        TabularMetricText(
            text = value,
            modifier = Modifier.weight(1.2f),
            maxLines = 2,
            style = MaterialTheme.typography.labelSmall,
            color = HudTextPrimary,
        )
    }
}
