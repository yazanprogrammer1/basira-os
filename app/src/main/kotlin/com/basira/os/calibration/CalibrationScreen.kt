package com.basira.os.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basira.os.R
import com.basira.os.passthrough.EyeLabel
import com.basira.os.stereo.PerEyeOverlay
import com.basira.os.stereo.StereoSurface
import com.basira.os.ui.system.components.GlassPanel
import com.basira.os.ui.system.theme.BasiraColors
import com.basira.os.ui.system.theme.BasiraShapes
import com.basira.os.ui.system.theme.MinTarget

/**
 * Two ways to calibrate:
 * - outside the viewer: the panel's chips and slider;
 * - inside the viewer: volume keys change the selected value, a tap (viewer button) selects the next one.
 */
@Composable
fun CalibrationScreen(viewModel: CalibrationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onEnter()
        onDispose { viewModel.onExit() }
    }
    LaunchedEffect(state.loaded) { if (state.loaded) viewModel.onEnter() }

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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    viewModel.selectNext()
                },
        )
        PerEyeOverlay {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-120).dp)) {
                EyeLabel("${stringResource(state.selected.label)} · ${state.selected.format(state.profile)}", BasiraColors.Mint)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.calib_hint_in_viewer),
                    color = BasiraColors.TextSubtle,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (state.panelVisible) {
            ControlPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .widthIn(max = 720.dp),
            )
        } else {
            TextButton(onClick = viewModel::togglePanel, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Text(stringResource(R.string.calib_show_panel), color = BasiraColors.TextSubtle)
            }
        }
    }
}

@Composable
private fun ControlPanel(state: CalibrationUiState, viewModel: CalibrationViewModel, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            CalibrationParam.entries.forEach { param ->
                ParamChip(
                    text = stringResource(param.label),
                    selected = param == state.selected,
                    onClick = { viewModel.select(param) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.selected.format(state.profile),
                color = BasiraColors.Text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.widthIn(min = 96.dp),
            )
            Slider(
                value = state.selected.get(state.profile),
                onValueChange = { viewModel.setValue(state.selected, it) },
                valueRange = state.selected.min..state.selected.max,
                colors = SliderDefaults.colors(thumbColor = BasiraColors.Emerald, activeTrackColor = BasiraColors.Emerald),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            PanelButton(stringResource(if (state.showCamera) R.string.calib_show_grid else R.string.calib_show_camera), viewModel::toggleCamera)
            PanelButton(stringResource(R.string.calib_flip), viewModel::toggleOrientation)
            PanelButton(stringResource(R.string.calib_reset), viewModel::reset)
            Spacer(Modifier.weight(1f))
            PanelButton(stringResource(R.string.calib_hide_panel), viewModel::togglePanel)
        }
    }
}

@Composable
private fun ParamChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .background(if (selected) BasiraColors.Emerald else Color.Transparent, BasiraShapes.Pill)
            .border(1.dp, if (selected) BasiraColors.Emerald else BasiraColors.GlassBorder, BasiraShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text,
            color = if (selected) BasiraColors.Night else BasiraColors.TextMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PanelButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(MinTarget)) {
        Text(text, color = BasiraColors.Mint, style = MaterialTheme.typography.labelLarge)
    }
}
