package com.basira.os.core.common.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Unit quaternion (x, y, z, w), same component order as ARCore's `Pose.getRotationQuaternion`. */
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {

    operator fun times(q: Quaternion) = Quaternion(
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w,
        w * q.w - x * q.x - y * q.y - z * q.z,
    )

    fun conjugate() = Quaternion(-x, -y, -z, w)

    fun normalized(): Quaternion {
        val n = sqrt(x * x + y * y + z * z + w * w)
        return if (n == 0f) IDENTITY else Quaternion(x / n, y / n, z / n, w / n)
    }

    /** Rotates [v] by this quaternion (v' = q v q*). */
    fun rotate(v: Vec3): Vec3 {
        val u = Vec3(x, y, z)
        val t = (u cross v) * 2f
        return v + t * w + (u cross t)
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)

        fun fromAxisAngle(axis: Vec3, radians: Float): Quaternion {
            val a = axis.normalized()
            val s = sin(radians / 2f)
            return Quaternion(a.x * s, a.y * s, a.z * s, cos(radians / 2f))
        }
    }
}
