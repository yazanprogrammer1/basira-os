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
    /**
     * Angular size of the passthrough relative to the camera's true field of view. 1.0 is physically
     * correct for a perfectly calibrated viewer; the user tunes it until the room matches reality.
     */
    val passthroughScale: Float = 1f,
) {
    /** Clamps every value into [Limits], so a bad stored value can never break the view. */
    fun sanitized(): ViewerProfile = copy(
        interLensDistanceMm = interLensDistanceMm.coerceIn(Limits.INTER_LENS_MM),
        screenToLensMm = screenToLensMm.coerceIn(Limits.SCREEN_TO_LENS_MM),
        trayToLensCenterMm = trayToLensCenterMm.coerceIn(Limits.TRAY_TO_LENS_MM),
        k1 = k1.coerceIn(Limits.DISTORTION),
        k2 = k2.coerceIn(Limits.DISTORTION),
        maxFovDeg = maxFovDeg.coerceIn(Limits.FOV_DEG),
        passthroughShiftPx = passthroughShiftPx.coerceIn(Limits.SHIFT_PX),
        passthroughScale = passthroughScale.coerceIn(Limits.PASSTHROUGH_SCALE),
    )

    /** Physically plausible ranges for phone viewers. */
    object Limits {
        val INTER_LENS_MM = 55f..75f
        val SCREEN_TO_LENS_MM = 30f..55f
        val TRAY_TO_LENS_MM = 15f..55f
        val DISTORTION = 0f..1f
        val FOV_DEG = 40f..70f
        val SHIFT_PX = -60f..60f
        val PASSTHROUGH_SCALE = 0.8f..1.6f
    }

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

        /**
         * VR Shinecon (adjustable IPD wheel + focus knob, the project's test viewer).
         * Geometry is the middle of its adjustment ranges with the phone centred in the tray;
         * no published distortion values exist, so k1/k2 start from Cardboard and are tuned on the
         * calibration screen. Approximate: confirm IPD and screen-to-lens with a ruler.
         */
        val VR_SHINECON = ViewerProfile(
            interLensDistanceMm = 63f,
            screenToLensMm = 42f,
            trayToLensCenterMm = 34.5f,
            k1 = 0.34f,
            k2 = 0.55f,
            maxFovDeg = 55f,
            passthroughShiftPx = 0f,
            orientation = ScreenOrientation.LANDSCAPE,
        )

        /** Used until the user saves a calibration. */
        val DEFAULT = VR_SHINECON

        /** Profiles offered as one-tap starting points on the calibration screen. */
        val PRESETS: List<Pair<String, ViewerProfile>> = listOf(
            "VR Shinecon" to VR_SHINECON,
            "Cardboard v2" to CARDBOARD_V2,
        )
    }
}
