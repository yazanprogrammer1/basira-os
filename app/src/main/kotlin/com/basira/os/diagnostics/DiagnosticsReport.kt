package com.basira.os.diagnostics

import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.tracking.ArCoreStatus

/**
 * Plain-text device report the user pastes back into the chat or a GitHub issue.
 * Kept in English and machine-friendly on purpose.
 */
object DiagnosticsReport {

    fun build(
        appVersion: String,
        device: DeviceInfo,
        arCore: ArCoreStatus?,
        thermal: ThermalSnapshot?,
        quality: QualityLevel,
        cameraGranted: Boolean,
        bindings: List<ModuleBinding>,
    ): String = buildString {
        appendLine("Basira OS diagnostics v$appVersion")
        appendLine("device: ${device.manufacturer} ${device.model} (${device.device})")
        appendLine("android: ${device.androidRelease} (API ${device.sdkInt})")
        appendLine("soc: ${device.socModel ?: "n/a"}")
        appendLine("screen: ${device.screenWidthPx}x${device.screenHeightPx} @ ${fmt(device.currentRefreshHz)} Hz (max ${fmt(device.maxRefreshHz)} Hz)")
        appendLine("gles: ${device.glEsVersion}")
        appendLine("sustained_performance: ${device.sustainedPerformanceSupported}")
        appendLine("arcore: ${arCore ?: "checking"}")
        appendLine("thermal_status: ${thermal?.status ?: "n/a"}")
        appendLine("thermal_headroom_10s: ${thermal?.headroom?.let { fmt(it, 2) } ?: "n/a"}")
        appendLine("quality_level: $quality")
        appendLine("camera_permission: $cameraGranted")
        val stubs = bindings.count { it.isStub }
        append("bindings: ${bindings.size} (${bindings.size - stubs} real, $stubs stub)")
    }

    private fun fmt(value: Float, decimals: Int = 0): String = "%.${decimals}f".format(java.util.Locale.US, value)
}
