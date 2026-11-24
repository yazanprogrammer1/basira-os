package com.basira.os.core.common.quality

/**
 * Thermal quality budget, see docs/ARCHITECTURE.md section 6.
 * Every heavy feature reads its limits from here instead of hardcoding them.
 */
enum class QualityLevel(
    /** Target render rate. The pacer rounds down to a divisor of the display rate (45 on 60 Hz becomes 30). */
    val renderFps: Int,
    /** 0 means hand tracking is off. */
    val handTrackingFps: Int,
    val windowTextureScale: Float,
    val maxLiveWindows: Int,
    val wakeWordEnabled: Boolean,
    /**
     * Eye buffer resolution relative to half the screen. Tuned for mid-range GPUs
     * (first test device: Snapdragon 6 Gen 1); revisit after phase 1 measurements.
     */
    val eyeRenderScale: Float,
) {
    NORMAL(renderFps = 60, handTrackingFps = 20, windowTextureScale = 1.0f, maxLiveWindows = 4, wakeWordEnabled = true, eyeRenderScale = 0.85f),
    WARM(renderFps = 60, handTrackingFps = 12, windowTextureScale = 0.8f, maxLiveWindows = 3, wakeWordEnabled = true, eyeRenderScale = 0.75f),
    HOT(renderFps = 45, handTrackingFps = 0, windowTextureScale = 0.6f, maxLiveWindows = 2, wakeWordEnabled = false, eyeRenderScale = 0.65f),
}
