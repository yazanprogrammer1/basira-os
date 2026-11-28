package com.basira.os.core.tracking.pose

import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.math.QuatMath
import kotlin.math.atan2
import kotlin.math.sqrt

/** Where a new window goes: in front of the gaze, at eye height, upright, facing the viewer. Pure. */
object Placement {

    private val forward = FloatArray(3)
    private val q = FloatArray(QuatMath.SIZE)
    private val yaw = FloatArray(QuatMath.SIZE)

    /**
     * @param viewPose world_from_view (packed), y up (ARCore world).
     * @param out packed world_from_panel; the panel's +z points back at the viewer.
     */
    fun inFront(viewPose: FloatArray, distanceMeters: Float, out: FloatArray) {
        q[0] = viewPose[3]; q[1] = viewPose[4]; q[2] = viewPose[5]; q[3] = viewPose[6]
        QuatMath.rotate(q, 0f, 0f, -1f, forward)
        // Flatten onto the floor plane: looking down at the floor still puts the window ahead, not below.
        var fx = forward[0]
        var fz = forward[2]
        val len = sqrt(fx * fx + fz * fz)
        if (len < 1e-3f) {
            fx = 0f; fz = -1f
        } else {
            fx /= len; fz /= len
        }
        // Rotation about +y taking +z to -forward.
        QuatMath.fromAxisAngle(0f, 1f, 0f, atan2(-fx, -fz), yaw)
        Mat4.setPose(
            out,
            viewPose[0] + fx * distanceMeters,
            viewPose[1],
            viewPose[2] + fz * distanceMeters,
            yaw,
        )
    }

    /**
     * On a surface point the viewer is looking at: [offsetMeters] in front of it (towards the viewer),
     * upright, facing the viewer. Returns the viewer-to-surface distance.
     */
    fun onSurface(viewPose: FloatArray, px: Float, py: Float, pz: Float, offsetMeters: Float, out: FloatArray): Float {
        val dx = px - viewPose[0]
        val dy = py - viewPose[1]
        val dz = pz - viewPose[2]
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        val k = if (distance > 1e-4f) offsetMeters / distance else 0f
        var fx = dx
        var fz = dz
        val flat = sqrt(fx * fx + fz * fz)
        if (flat < 1e-3f) {
            fx = 0f; fz = -1f
        } else {
            fx /= flat; fz /= flat
        }
        QuatMath.fromAxisAngle(0f, 1f, 0f, atan2(-fx, -fz), yaw)
        Mat4.setPose(out, px - dx * k, py - dy * k, pz - dz * k, yaw)
        return distance
    }
}
