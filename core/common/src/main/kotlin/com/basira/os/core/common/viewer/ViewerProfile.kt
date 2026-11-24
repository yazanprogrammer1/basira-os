package com.basira.os.core.common.viewer

/** Which way the phone sits in the viewer. The back camera must stay uncovered. */
enum class ScreenOrientation { LANDSCAPE, REVERSE_LANDSCAPE }

/**
 * Physical parameters of the phone VR viewer, edited on the calibration screen and stored in DataStore.
 * Distortion follows the Cardboard convention: a point at screen tan-angle r is seen at r * (1 + k1 r^2 + k2 r^4).
 */
data class ViewerProfile(
    val interLensDistanceMm: Float,
    val screenToLensMm: Float,
    /** Distance from the bottom edge of the phone (the tray) to the lens centres, in landscape. */
    val trayToLensCenterMm: Float,
    val k1: Float,
    val k2: Float,
    /** Lens-limited half field of view per side, in degrees. */
    val maxFovDeg: Float,
    /** Horizontal passthrough offset per eye, in eye-buffer pixels. Positive moves the images inward. */
    val passthroughShiftPx: Float,
    val orientation: ScreenOrientation,
) {
    companion object {
        /** Google Cardboard v2 published values. A starting point only: every viewer differs. */
        val CARDBOARD_V2 = ViewerProfile(
            interLensDistanceMm = 64f,
            screenToLensMm = 39.3f,
            trayToLensCenterMm = 35f,
            k1 = 0.34f,
            k2 = 0.55f,
            maxFovDeg = 60f,
            passthroughShiftPx = 0f,
            orientation = ScreenOrientation.LANDSCAPE,
        )
    }
}
