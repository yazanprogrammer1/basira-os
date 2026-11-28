package com.basira.os.core.common.math

/**
 * Allocation-free 4x4 column-major matrices for OpenGL, plus rigid poses packed as FloatArray(7):
 * (tx, ty, tz, qx, qy, qz, qw) = rotate by q, then translate by t (world_from_local).
 */
object Mat4 {

    const val SIZE = 16
    const val POSE_SIZE = 7

    fun identity(out: FloatArray) {
        out.fill(0f, 0, SIZE)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
    }

    /** out = a * b. out must not alias a or b. */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
    }

    fun translation(x: Float, y: Float, z: Float, out: FloatArray) {
        identity(out)
        out[12] = x; out[13] = y; out[14] = z
    }

    /** Matrix of a packed pose (world_from_local). */
    fun fromPose(pose: FloatArray, out: FloatArray) {
        val q = SCRATCH_Q.get()!!
        q[0] = pose[3]; q[1] = pose[4]; q[2] = pose[5]; q[3] = pose[6]
        QuatMath.toMatrix4(q, out)
        out[12] = pose[0]; out[13] = pose[1]; out[14] = pose[2]
    }

    /** Inverse of a rigid transform (rotation + translation only). out must not alias m. */
    fun rigidInverse(m: FloatArray, out: FloatArray) {
        // R^T
        out[0] = m[0]; out[1] = m[4]; out[2] = m[8]; out[3] = 0f
        out[4] = m[1]; out[5] = m[5]; out[6] = m[9]; out[7] = 0f
        out[8] = m[2]; out[9] = m[6]; out[10] = m[10]; out[11] = 0f
        // -R^T t
        val tx = m[12]; val ty = m[13]; val tz = m[14]
        out[12] = -(out[0] * tx + out[4] * ty + out[8] * tz)
        out[13] = -(out[1] * tx + out[5] * ty + out[9] * tz)
        out[14] = -(out[2] * tx + out[6] * ty + out[10] * tz)
        out[15] = 1f
    }

    /** Applies m to point (x, y, z, 1); writes (x, y, z) into out[0..2]. */
    fun transformPoint(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    fun setPose(out: FloatArray, tx: Float, ty: Float, tz: Float, q: FloatArray) {
        out[0] = tx; out[1] = ty; out[2] = tz
        out[3] = q[0]; out[4] = q[1]; out[5] = q[2]; out[6] = q[3]
    }

    private val SCRATCH_Q = ThreadLocal.withInitial { FloatArray(4) }
}
