package com.basira.os.core.thermal

import com.basira.os.core.common.quality.QualityLevel

/**
 * Pure mapping from a thermal reading to a [QualityLevel].
 * Headroom lets us degrade *before* the platform status changes, which is the whole point:
 * we want to drop hand tracking fps, not frames.
 */
object ThermalPolicy {

    const val WARM_HEADROOM = 0.80f
    const val HOT_HEADROOM = 0.95f

    fun qualityFor(snapshot: ThermalSnapshot): QualityLevel {
        val fromStatus = when (snapshot.status) {
            ThermalStatus.NONE, ThermalStatus.LIGHT, ThermalStatus.UNKNOWN -> QualityLevel.NORMAL
            ThermalStatus.MODERATE -> QualityLevel.WARM
            ThermalStatus.SEVERE,
            ThermalStatus.CRITICAL,
            ThermalStatus.EMERGENCY,
            ThermalStatus.SHUTDOWN -> QualityLevel.HOT
        }
        val headroom = snapshot.headroom
        val fromHeadroom = when {
            headroom == null -> QualityLevel.NORMAL
            headroom >= HOT_HEADROOM -> QualityLevel.HOT
            headroom >= WARM_HEADROOM -> QualityLevel.WARM
            else -> QualityLevel.NORMAL
        }
        return maxOf(fromStatus, fromHeadroom)
    }
}
