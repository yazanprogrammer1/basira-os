package com.basira.os.core.common.quality

/**
 * Thermal quality budget, see docs/ARCHITECTURE.md section 6.
 * Every heavy feature reads its limits from here instead of hardcoding them.
 */
enum class QualityLevel(
    val renderFps: Int,
    /** 0 means hand tracking is off. */
    val handTrackingFps: Int,
    val windowTextureScale: Float,
    val maxLiveWindows: Int,
    val wakeWordEnabled: Boolean,
) {
    NORMAL(renderFps = 60, handTrackingFps = 20, windowTextureScale = 1.0f, maxLiveWindows = 4, wakeWordEnabled = true),
    WARM(renderFps = 60, handTrackingFps = 12, windowTextureScale = 0.8f, maxLiveWindows = 3, wakeWordEnabled = true),
    HOT(renderFps = 45, handTrackingFps = 0, windowTextureScale = 0.6f, maxLiveWindows = 2, wakeWordEnabled = false),
}
