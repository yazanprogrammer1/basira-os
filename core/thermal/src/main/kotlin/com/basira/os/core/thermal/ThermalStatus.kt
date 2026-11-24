package com.basira.os.core.thermal

import android.os.PowerManager

/** Mirror of `PowerManager.THERMAL_STATUS_*`, ordered from coolest to hottest. */
enum class ThermalStatus {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN, UNKNOWN;

    companion object {
        fun fromPlatform(value: Int): ThermalStatus = when (value) {
            PowerManager.THERMAL_STATUS_NONE -> NONE
            PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
            else -> UNKNOWN
        }
    }
}

/**
 * One thermal reading.
 * [headroom] is `PowerManager.getThermalHeadroom(10)`: 1.0 means throttling starts within ~10 s.
 * It is null below API 30 or when the device does not report it.
 */
data class ThermalSnapshot(
    val status: ThermalStatus,
    val headroom: Float?,
    val timestampMs: Long,
    /** Battery temperature in degrees C (the closest public proxy for how hot the phone feels). */
    val batteryTempC: Float? = null,
)
