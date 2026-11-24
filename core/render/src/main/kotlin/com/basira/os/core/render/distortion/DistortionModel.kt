package com.basira.os.core.render.distortion

import kotlin.math.abs

/**
 * Polynomial radial lens model (Cardboard convention), in tan-angle units.
 * A point on the screen at radius r (relative to the lens centre) is seen at [distort] (r).
 */
class DistortionModel(val k1: Float, val k2: Float) {

    fun factor(r: Float): Float {
        val r2 = r * r
        return 1f + k1 * r2 + k2 * r2 * r2
    }

    /** Screen-space radius -> eye-space radius. */
    fun distort(r: Float): Float = r * factor(r)

    /** Eye-space radius -> screen-space radius (secant method, like the Cardboard SDK). */
    fun distortInverse(radius: Float): Float {
        if (radius == 0f) return 0f
        var r0 = radius / 0.9f
        var r1 = radius * 0.9f
        var dr0 = radius - distort(r0)
        repeat(MAX_ITERATIONS) {
            if (abs(r1 - r0) <= EPSILON) return r1
            val dr1 = radius - distort(r1)
            if (dr1 == dr0) return r1
            val r2 = r1 - dr1 * ((r1 - r0) / (dr1 - dr0))
            r0 = r1
            r1 = r2
            dr0 = dr1
        }
        return r1
    }

    private companion object {
        const val MAX_ITERATIONS = 20
        const val EPSILON = 1e-6f
    }
}
