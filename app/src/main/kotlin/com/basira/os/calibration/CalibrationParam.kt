package com.basira.os.calibration

import androidx.annotation.StringRes
import com.basira.os.R
import com.basira.os.core.common.viewer.ViewerProfile
import java.util.Locale

/**
 * One adjustable viewer parameter. Order = the order to calibrate in:
 * IPD until the two crosshairs fuse, then k1 until grid lines look straight, then k2 for the edges,
 * then the passthrough scale until a door seen through the viewer matches the door seen past it.
 */
enum class CalibrationParam(
    @StringRes val label: Int,
    val unit: String,
    val step: Float,
    private val range: ClosedFloatingPointRange<Float>,
    val decimals: Int,
) {
    IPD(R.string.calib_ipd, "mm", 0.5f, ViewerProfile.Limits.INTER_LENS_MM, 1),
    K1(R.string.calib_k1, "", 0.01f, ViewerProfile.Limits.DISTORTION, 2),
    K2(R.string.calib_k2, "", 0.01f, ViewerProfile.Limits.DISTORTION, 2),
    SCALE(R.string.calib_scale, "×", 0.02f, ViewerProfile.Limits.PASSTHROUGH_SCALE, 2),
    SCREEN_TO_LENS(R.string.calib_screen_to_lens, "mm", 0.5f, ViewerProfile.Limits.SCREEN_TO_LENS_MM, 1),
    TRAY_TO_LENS(R.string.calib_tray_to_lens, "mm", 0.5f, ViewerProfile.Limits.TRAY_TO_LENS_MM, 1),
    FOV(R.string.calib_fov, "°", 1f, ViewerProfile.Limits.FOV_DEG, 0),
    SHIFT(R.string.calib_shift, "px", 2f, ViewerProfile.Limits.SHIFT_PX, 0),
    ;

    val min: Float get() = range.start
    val max: Float get() = range.endInclusive

    fun get(p: ViewerProfile): Float = when (this) {
        IPD -> p.interLensDistanceMm
        K1 -> p.k1
        K2 -> p.k2
        SCREEN_TO_LENS -> p.screenToLensMm
        TRAY_TO_LENS -> p.trayToLensCenterMm
        FOV -> p.maxFovDeg
        SHIFT -> p.passthroughShiftPx
        SCALE -> p.passthroughScale
    }

    fun set(p: ViewerProfile, value: Float): ViewerProfile {
        val v = snap(value.coerceIn(min, max))
        return when (this) {
            IPD -> p.copy(interLensDistanceMm = v)
            K1 -> p.copy(k1 = v)
            K2 -> p.copy(k2 = v)
            SCREEN_TO_LENS -> p.copy(screenToLensMm = v)
            TRAY_TO_LENS -> p.copy(trayToLensCenterMm = v)
            FOV -> p.copy(maxFovDeg = v)
            SHIFT -> p.copy(passthroughShiftPx = v)
            SCALE -> p.copy(passthroughScale = v)
        }
    }

    fun adjust(p: ViewerProfile, steps: Int): ViewerProfile = set(p, get(p) + steps * step)

    fun format(p: ViewerProfile): String {
        val number = "%.${decimals}f".format(Locale.US, get(p))
        return if (unit.isEmpty()) number else "$number $unit"
    }

    /** Snap to the step grid so repeated presses do not accumulate float error. */
    private fun snap(value: Float): Float {
        val snapped = Math.round(value / step) * step
        val scale = Math.pow(10.0, decimals.toDouble()).toFloat()
        return Math.round(snapped * scale) / scale
    }

    fun next(): CalibrationParam = entries[(ordinal + 1) % entries.size]
}
