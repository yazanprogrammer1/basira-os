package com.basira.os.core.common.math

/** Half-line used for gaze and hand picking. [direction] is always normalised. */
class Ray(val origin: Vec3, direction: Vec3) {
    val direction: Vec3 = direction.normalized()

    fun pointAt(t: Float): Vec3 = origin + direction * t

    companion object {
        /** Gaze ray through the centre of the view for a head pose. */
        fun gaze(head: Pose) = Ray(head.position, head.forward)
    }
}
