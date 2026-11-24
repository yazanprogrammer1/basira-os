package com.basira.os.diagnostics

import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatus
import com.basira.os.core.tracking.ArCoreStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsReportTest {

    private val device = DeviceInfo(
        manufacturer = "samsung",
        model = "SM-S908B",
        device = "b0s",
        androidRelease = "16",
        sdkInt = 36,
        socModel = null,
        screenWidthPx = 3088,
        screenHeightPx = 1440,
        currentRefreshHz = 119.88f,
        maxRefreshHz = 120f,
        glEsVersion = "3.2",
        sustainedPerformanceSupported = false,
    )

    @Test
    fun `report contains the values needed for the phase 0 gate`() {
        val report = DiagnosticsReport.build(
            appVersion = "0.0",
            device = device,
            arCore = ArCoreStatus.SUPPORTED_INSTALLED,
            thermal = ThermalSnapshot(ThermalStatus.LIGHT, 0.456f, 0L),
            quality = QualityLevel.NORMAL,
            cameraGranted = true,
            bindings = listOf(
                ModuleBinding(":core:render", "Compositor", "StubCompositor"),
                ModuleBinding(":core:thermal", "ThermalStatusSource", "PowerManagerThermalStatusSource"),
            ),
        )

        assertTrue(report.contains("device: samsung SM-S908B (b0s)"))
        assertTrue(report.contains("screen: 3088x1440 @ 120 Hz (max 120 Hz)"))
        assertTrue(report.contains("soc: n/a"))
        assertTrue(report.contains("arcore: SUPPORTED_INSTALLED"))
        assertTrue(report.contains("thermal_headroom_10s: 0.46"))
        assertTrue(report.contains("bindings: 2 (1 real, 1 stub)"))
    }

    @Test
    fun `report handles missing readings`() {
        val report = DiagnosticsReport.build("0.0", device, null, null, QualityLevel.NORMAL, false, emptyList())
        assertTrue(report.contains("arcore: checking"))
        assertTrue(report.contains("thermal_status: n/a"))
    }
}
