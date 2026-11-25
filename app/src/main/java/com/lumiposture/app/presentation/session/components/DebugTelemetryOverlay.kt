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
            DebugRow("Lux / Layar", String.format(Locale.US, "%.0f lx | %d%%", snapshot.ambientLux, snapshot.screenBrightnessPct))
            DebugRow("Kebisingan", String.format(Locale.US, "%.1f dB", snapshot.noiseDb))
            DebugRow("IPD / Jarak", String.format(Locale.US, "%.1f px | %.1f cm", snapshot.ipdPixels, snapshot.distanceCm))
            DebugRow("Pitch HP / Kepala", String.format(Locale.US, "%.1f deg | %.1f deg", snapshot.devicePitchDeg, snapshot.headPitchDeg))
            DebugRow("Sudut Leher", String.format(Locale.US, "%.1f deg", snapshot.neckAngleDeg))
            DebugRow("Vision FPS / Kedipan", String.format(Locale.US, "%.1f fps | %d", snapshot.inferenceFps, snapshot.totalBlinks))
            DebugRow("Proximity", if (snapshot.isProximityTriggered) "DEKAT (<10 cm)" else "AMAN")
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
            style = MaterialTheme.typography.labelSmall,
            color = HudTextSecondary,
        )
        TabularMetricText(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = HudTextPrimary,
        )
    }
}
