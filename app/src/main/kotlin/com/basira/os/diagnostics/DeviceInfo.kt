package com.basira.os.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    /** Build.SOC_MODEL, API 31+. */
    val socModel: String?,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val currentRefreshHz: Float,
    val maxRefreshHz: Float,
    val glEsVersion: String,
    val sustainedPerformanceSupported: Boolean,
)

class DeviceInfoReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(): DeviceInfo {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                    .filter { it.isNotBlank() && it != Build.UNKNOWN }
                    .joinToString(" ")
                    .ifBlank { null }
            } else {
                null
            },
            // Landscape app: report the long edge as width.
            screenWidthPx = maxOf(mode.physicalWidth, mode.physicalHeight),
            screenHeightPx = minOf(mode.physicalWidth, mode.physicalHeight),
            currentRefreshHz = display.refreshRate,
            maxRefreshHz = display.supportedModes.maxOf { it.refreshRate },
            glEsVersion = activityManager.deviceConfigurationInfo.glEsVersion,
            sustainedPerformanceSupported = powerManager.isSustainedPerformanceModeSupported,
        )
    }
}
