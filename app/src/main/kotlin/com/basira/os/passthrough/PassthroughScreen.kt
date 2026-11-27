package com.basira.os.passthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basira.os.R
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.tracking.arcore.ArSessionError
import com.basira.os.core.tracking.arcore.ArSessionState
import com.basira.os.stereo.PerEyeOverlay
import com.basira.os.stereo.StereoSurface
import com.basira.os.ui.system.theme.BasiraColors
import com.basira.os.ui.system.theme.BasiraShapes
import java.util.Locale

/**
 * Stereo passthrough. A tap (or the viewer button) or volume down toggles the developer overlay;
 * volume up toggles camera reprojection for side-by-side comparison in the viewer.
 */
@Composable
fun PassthroughScreen(viewModel: PassthroughViewModel) {
    LaunchedEffect(Unit) { viewModel.runWhileVisible() }
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StereoSurface(
            compositor = viewModel.compositor,
            sessions = viewModel.sessions,
            frameSource = viewModel.frameSource,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::toggleOverlay,
                ),
        )
        val failure = overlay.session as? ArSessionState.Failed
        if (failure != null) {
            PerEyeOverlay { EyeLabel(stringResource(sessionErrorText(failure.error)), BasiraColors.Live) }
        } else if (overlay.visible) {
            PerEyeOverlay(alignment = Alignment.Center) {
                DevStats(overlay, Modifier.offset(y = (-110).dp))
            }
        }
    }
}

@Composable
private fun DevStats(state: OverlayState, modifier: Modifier = Modifier) {
    val s = state.stats
    val t = state.thermal
    val line1 = String.format(
        Locale.US,
        "%.0f fps · p95 %.1f ms · jank %.1f%%",
        s.fps,
        s.frameTimeMsP95,
        s.jankRatio * 100f,
    )
    val camera = if (s.cameraAgeMs >= 0f) String.format(Locale.US, "cam %.0f ms", s.cameraAgeMs) else "cam --"
    val battery = t?.batteryTempC?.let { String.format(Locale.US, "%.1f°", it) } ?: "--°"
    val headroom = t?.headroom?.let { String.format(Locale.US, "h %.2f", it) } ?: "h --"
    val reprojection = when {
        !state.reprojectionEnabled -> "reproj OFF"
        s.reprojectionDegrees < 0f -> "reproj n/a"
        else -> String.format(Locale.US, "reproj %.1f°", s.reprojectionDegrees)
    }
    val line2 = "$camera · $reprojection · ${if (s.tracking) "6DoF" else "no track"} · $battery · $headroom · ${state.quality}"
    val color = when (state.quality) {
        QualityLevel.NORMAL -> BasiraColors.Mint
        QualityLevel.WARM -> BasiraColors.Warning
        QualityLevel.HOT -> BasiraColors.Live
    }
    Column(
        modifier = modifier
            .background(BasiraColors.Glass, BasiraShapes.Panel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(line1, color = color, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
        Text(line2, color = BasiraColors.TextMuted, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
        (state.session as? ArSessionState.Running)?.let {
            Text(it.cameraConfig, color = BasiraColors.TextSubtle, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr))
        }
    }
}

@Composable
fun EyeLabel(text: String, color: Color = BasiraColors.Text, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .background(BasiraColors.Glass, BasiraShapes.Panel)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun sessionErrorText(error: ArSessionError): Int = when (error) {
    ArSessionError.NOT_INSTALLED -> R.string.session_error_not_installed
    ArSessionError.UNSUPPORTED -> R.string.session_error_unsupported
    ArSessionError.NO_CAMERA_PERMISSION -> R.string.session_error_permission
    ArSessionError.CAMERA_UNAVAILABLE -> R.string.session_error_camera
    ArSessionError.UNKNOWN -> R.string.session_error_unknown
}
