package com.basira.os.core.render.pass

/**
 * The mapping the direct passthrough shader performs per pixel, in plain Kotlin so it can be tested.
 * Keep it in step with [com.basira.os.core.render.gl.Shaders.DIRECT_CAMERA_FRAGMENT].
 *
 * 1. eye tan-angle now (from the distortion mesh),
 * 2. rotated back to the capture-time view direction ([captureFromNow], column-major 3x3),
 * 3. projected onto the camera image plane and shifted,
 * 4. normalised to q in [0, 1]^2 across the camera image (outside = no camera there).
 */
object DirectCameraMath {

    /** Column-major 3x3 transpose of the rotation in a column-major 4x4 (the inverse rotation). */
    fun inverseRotation3(rotation4: FloatArray, out: FloatArray) {
        for (col in 0 until 3) {
            for (row in 0 until 3) {
                out[col * 3 + row] = rotation4[row * 4 + col]
            }
        }
    }

    /** Returns false when the direction points backwards; q is written to out[0..1]. */
    fun cameraCoordinate(
        tanX: Float,
        tanY: Float,
        captureFromNow: FloatArray,
        halfTanX: Float,
        halfTanY: Float,
        shiftTan: Float,
        out: FloatArray,
    ): Boolean {
        val m = captureFromNow
        val dx = m[0] * tanX + m[3] * tanY - m[6]
        val dy = m[1] * tanX + m[4] * tanY - m[7]
        val dz = m[2] * tanX + m[5] * tanY - m[8]
        if (dz > -1e-4f) return false
        val tx = dx / -dz - shiftTan
        val ty = dy / -dz
        out[0] = tx / halfTanX * 0.5f + 0.5f
        out[1] = ty / halfTanY * 0.5f + 0.5f
        return true
    }
}
