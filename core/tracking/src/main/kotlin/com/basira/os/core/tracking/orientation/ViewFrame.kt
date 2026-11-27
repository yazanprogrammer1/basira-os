package com.basira.os.core.tracking.orientation

import android.view.Surface
import com.basira.os.core.common.math.QuatMath
import kotlin.math.PI

/**
 * Android sensors report in the device's natural (portrait) frame; the renderer works in the
 * view frame of the rotated display (x right, y up on screen). This is the rotation about z
 * that maps device axes to view axes for each `Surface.ROTATION_*`.
 */
object ViewFrame {

    fun deviceToView(displayRotation: Int, out: FloatArray) {
        val degrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> -90f
            else -> 0f
        }
        QuatMath.fromAxisAngle(0f, 0f, 1f, degrees * PI.toFloat() / 180f, out)
    }

    /**
     * Rotation that takes directions seen by the device at capture time to directions seen now,
     * expressed in the view frame: C * (now^-1 * capture) * C^-1.
     * [capture] and [now] are device -> world orientations from the same sensor.
     */
    fun captureToNowInView(
        capture: FloatArray,
        now: FloatArray,
        deviceToView: FloatArray,
        scratchA: FloatArray,
        scratchB: FloatArray,
        out: FloatArray,
    ) {
        QuatMath.conjugate(now, scratchA)
        QuatMath.multiply(scratchA, capture, scratchB) // device frame: capture -> now
        QuatMath.multiply(deviceToView, scratchB, scratchA)
        QuatMath.conjugate(deviceToView, scratchB)
        QuatMath.multiply(scratchA, scratchB, out)
    }
}
