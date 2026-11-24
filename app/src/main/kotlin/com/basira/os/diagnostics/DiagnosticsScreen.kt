package com.basira.os.diagnostics

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basira.os.R
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatus
import com.basira.os.core.tracking.ArCoreStatus
import com.basira.os.ui.system.components.GlassPanel
import com.basira.os.ui.system.components.PillButton
import com.basira.os.ui.system.components.StatusPill
import com.basira.os.ui.system.theme.BasiraColors
import com.basira.os.ui.system.theme.BasiraShapes
import com.basira.os.ui.system.theme.BasiraTheme
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onRequestCamera: () -> Unit,
    onInstallArCore: () -> Unit,
    onRecheckArCore: () -> Unit,
    onCopyReport: () -> Unit,
    onStartPassthrough: () -> Unit,
    onOpenCalibration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BasiraColors.Night)
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Header(
            state = state,
            onCopyReport = onCopyReport,
            onStartPassthrough = onStartPassthrough,
            onOpenCalibration = onOpenCalibration,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                DeviceSection(state.device)
                ArCoreSection(state.arCore, onInstallArCore, onRecheckArCore)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ThermalSection(state.thermal, state.quality)
                CameraSection(state.cameraGranted, onRequestCamera)
                ModulesSection(state)
            }
        }
    }
}

@Composable
private fun Header(
    state: DiagnosticsUiState,
    onCopyReport: () -> Unit,
    onStartPassthrough: () -> Unit,
    onOpenCalibration: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(BasiraColors.Emerald, BasiraShapes.DockButton),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.headlineMedium, color = BasiraColors.Text)
            Text(stringResource(R.string.diag_subtitle), style = MaterialTheme.typography.bodySmall, color = BasiraColors.TextSubtle)
        }
        StatusPill(
            text = qualityLabel(state.quality),
            dotColor = qualityColor(state.quality),
        )
        Spacer(Modifier.width(12.dp))
        TextButton(onClick = onCopyReport) {
            Text(stringResource(R.string.diag_copy_report), color = BasiraColors.Mint)
        }
        TextButton(onClick = onOpenCalibration) {
            Text(stringResource(R.string.diag_open_calibration), color = BasiraColors.Mint)
        }
        Spacer(Modifier.width(8.dp))
        PillButton(text = stringResource(R.string.diag_start_passthrough), onClick = onStartPassthrough)
    }
}

@Composable
private fun DeviceSection(device: DeviceInfo) {
    Section(R.string.diag_section_device) {
        InfoRow(R.string.diag_model, "${device.manufacturer} ${device.model}", ltr = true)
        InfoRow(R.string.diag_android, "${device.androidRelease} (API ${device.sdkInt})", ltr = true)
        InfoRow(R.string.diag_soc, device.socModel ?: stringResource(R.string.diag_not_available), ltr = device.socModel != null)
        InfoRow(R.string.diag_screen, "${device.screenWidthPx}×${device.screenHeightPx}", ltr = true)
        InfoRow(
            R.string.diag_refresh,
            "${device.currentRefreshHz.format()} Hz (max ${device.maxRefreshHz.format()})",
            ltr = true,
        )
        InfoRow(R.string.diag_gles, device.glEsVersion, ltr = true)
        InfoRow(R.string.diag_sustained, yesNo(device.sustainedPerformanceSupported))
    }
}

@Composable
private fun ArCoreSection(status: ArCoreStatus?, onInstall: () -> Unit, onRecheck: () -> Unit) {
    Section(R.string.diag_section_arcore) {
        if (status == null) {
            Text(stringResource(R.string.diag_loading), style = MaterialTheme.typography.bodyMedium, color = BasiraColors.TextMuted)
            return@Section
        }
        InfoRow(R.string.diag_arcore_supported, yesNo(status.isSupported), dot = if (status.isSupported) BasiraColors.Emerald else BasiraColors.Live)
        InfoRow(
            R.string.diag_arcore_installed,
            yesNo(status == ArCoreStatus.SUPPORTED_INSTALLED),
            dot = if (status == ArCoreStatus.SUPPORTED_INSTALLED) BasiraColors.Emerald else BasiraColors.Warning,
        )
        InfoRow(R.string.diag_arcore_status, stringResource(arCoreLabel(status)))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (status == ArCoreStatus.SUPPORTED_NOT_INSTALLED || status == ArCoreStatus.SUPPORTED_APK_TOO_OLD) {
                PillButton(stringResource(R.string.diag_arcore_install), onClick = onInstall)
            }
            TextButton(onClick = onRecheck) {
                Text(stringResource(R.string.diag_arcore_recheck), color = BasiraColors.Mint)
            }
        }
    }
}

@Composable
private fun ThermalSection(thermal: ThermalSnapshot?, quality: QualityLevel) {
    Section(R.string.diag_section_thermal) {
        if (thermal == null) {
            Text(stringResource(R.string.diag_loading), style = MaterialTheme.typography.bodyMedium, color = BasiraColors.TextMuted)
            return@Section
        }
        InfoRow(R.string.diag_thermal_status, stringResource(thermalLabel(thermal.status)), dot = thermalColor(thermal.status))
        InfoRow(
            R.string.diag_thermal_headroom,
            thermal.headroom?.let { String.format(Locale.US, "%.2f", it) } ?: stringResource(R.string.diag_not_available),
            ltr = thermal.headroom != null,
        )
        InfoRow(R.string.diag_thermal_quality, qualityLabel(quality), dot = qualityColor(quality))
    }
}

@Composable
private fun CameraSection(granted: Boolean, onRequest: () -> Unit) {
    Section(R.string.diag_section_camera) {
        InfoRow(
            R.string.diag_camera_permission,
            stringResource(if (granted) R.string.diag_camera_granted else R.string.diag_camera_denied),
            dot = if (granted) BasiraColors.Emerald else BasiraColors.Live,
        )
        if (!granted) {
            Spacer(Modifier.height(8.dp))
            PillButton(stringResource(R.string.diag_camera_request), onClick = onRequest)
        }
    }
}

@Composable
private fun ModulesSection(state: DiagnosticsUiState) {
    Section(R.string.diag_section_modules) {
        state.bindings.forEach { binding ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Dot(if (binding.isStub) BasiraColors.TextSubtle else BasiraColors.Emerald)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${binding.module} · ${binding.contract}",
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = BasiraColors.TextMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (binding.isStub) R.string.diag_module_stub else R.string.diag_module_real),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (binding.isStub) BasiraColors.TextSubtle else BasiraColors.Mint,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoRow(
            R.string.diag_keys,
            stringResource(
                R.string.diag_keys_value,
                yesNo(state.llmConfigured),
                yesNo(state.sttConfigured),
            ),
        )
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = BasiraColors.Mint)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun InfoRow(@StringRes label: Int, value: String, ltr: Boolean = false, dot: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = BasiraColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (dot != null) {
            Dot(dot)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            ),
            color = BasiraColors.Text,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun yesNo(value: Boolean) = stringResource(if (value) R.string.diag_yes else R.string.diag_no)

@StringRes
private fun arCoreLabel(status: ArCoreStatus): Int = when (status) {
    ArCoreStatus.SUPPORTED_INSTALLED -> R.string.diag_arcore_installed_ok
    ArCoreStatus.SUPPORTED_NOT_INSTALLED -> R.string.diag_arcore_not_installed
    ArCoreStatus.SUPPORTED_APK_TOO_OLD -> R.string.diag_arcore_too_old
    ArCoreStatus.UNSUPPORTED -> R.string.diag_arcore_unsupported
    ArCoreStatus.UNKNOWN -> R.string.diag_arcore_unknown
}

@StringRes
private fun thermalLabel(status: ThermalStatus): Int = when (status) {
    ThermalStatus.NONE -> R.string.thermal_none
    ThermalStatus.LIGHT -> R.string.thermal_light
    ThermalStatus.MODERATE -> R.string.thermal_moderate
    ThermalStatus.SEVERE -> R.string.thermal_severe
    ThermalStatus.CRITICAL -> R.string.thermal_critical
    ThermalStatus.EMERGENCY -> R.string.thermal_emergency
    ThermalStatus.SHUTDOWN -> R.string.thermal_shutdown
    ThermalStatus.UNKNOWN -> R.string.thermal_unknown
}

private fun thermalColor(status: ThermalStatus): Color = when (status) {
    ThermalStatus.NONE, ThermalStatus.LIGHT, ThermalStatus.UNKNOWN -> BasiraColors.Emerald
    ThermalStatus.MODERATE -> BasiraColors.Warning
    else -> BasiraColors.Live
}

@Composable
private fun qualityLabel(level: QualityLevel): String = stringResource(
    when (level) {
        QualityLevel.NORMAL -> R.string.quality_normal
        QualityLevel.WARM -> R.string.quality_warm
        QualityLevel.HOT -> R.string.quality_hot
    },
)

private fun qualityColor(level: QualityLevel): Color = when (level) {
    QualityLevel.NORMAL -> BasiraColors.Emerald
    QualityLevel.WARM -> BasiraColors.Warning
    QualityLevel.HOT -> BasiraColors.Live
}

private fun Float.format(): String = String.format(Locale.US, "%.0f", this)

@Preview(widthDp = 960, heightDp = 540, locale = "ar")
@Composable
private fun DiagnosticsScreenPreview() {
    BasiraTheme {
        DiagnosticsScreen(
            state = DiagnosticsUiState(
                device = DeviceInfo(
                    manufacturer = "samsung",
                    model = "SM-S908B",
                    device = "b0s",
                    androidRelease = "16",
                    sdkInt = 36,
                    socModel = "QTI SM8450",
                    screenWidthPx = 3088,
                    screenHeightPx = 1440,
                    currentRefreshHz = 120f,
                    maxRefreshHz = 120f,
                    glEsVersion = "3.2",
                    sustainedPerformanceSupported = false,
                ),
                arCore = ArCoreStatus.SUPPORTED_INSTALLED,
                thermal = ThermalSnapshot(ThermalStatus.LIGHT, 0.42f, 0L),
                cameraGranted = false,
                bindings = listOf(
                    ModuleBinding(":core:render", "Compositor", "StubCompositor"),
                    ModuleBinding(":core:thermal", "ThermalStatusSource", "PowerManagerThermalStatusSource"),
                ),
            ),
            onRequestCamera = {},
            onInstallArCore = {},
            onRecheckArCore = {},
            onCopyReport = {},
            onStartPassthrough = {},
            onOpenCalibration = {},
        )
    }
}
