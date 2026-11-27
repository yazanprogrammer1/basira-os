package com.basira.os.core.common.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Allocation-free quaternion math on FloatArray(4) in (x, y, z, w) order, for the GL and sensor threads.
 * Hamilton product: multiply(a, b) applies b first, then a.
 * [out] may alias an input only where noted.
 */
object QuatMath {

    const val SIZE = 4

    fun set(out: FloatArray, x: Float, y: Float, z: Float, w: Float) {
        out[0] = x; out[1] = y; out[2] = z; out[3] = w
    }

    fun identity(out: FloatArray) = set(out, 0f, 0f, 0f, 1f)

    fun copy(src: FloatArray, out: FloatArray) = src.copyInto(out, 0, 0, SIZE)

    /** out must not alias a or b. */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray) {
        val ax = a[0]; val ay = a[1]; val az = a[2]; val aw = a[3]
        val bx = b[0]; val by = b[1]; val bz = b[2]; val bw = b[3]
        out[0] = aw * bx + ax * bw + ay * bz - az * by
        out[1] = aw * by - ax * bz + ay * bw + az * bx
        out[2] = aw * bz + ax * by - ay * bx + az * bw
        out[3] = aw * bw - ax * bx - ay * by - az * bz
    }

    /** Safe to alias. */
    fun conjugate(q: FloatArray, out: FloatArray) = set(out, -q[0], -q[1], -q[2], q[3])

    /** Safe to alias. */
    fun normalize(q: FloatArray, out: FloatArray) {
        val n = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (n == 0f) identity(out) else set(out, q[0] / n, q[1] / n, q[2] / n, q[3] / n)
    }

    fun fromAxisAngle(ax: Float, ay: Float, az: Float, radians: Float, out: FloatArray) {
        val n = sqrt(ax * ax + ay * ay + az * az)
        if (n == 0f) return identity(out)
        val s = sin(radians / 2f) / n
        set(out, ax * s, ay * s, az * s, cos(radians / 2f))
    }

    /** Rotation angle in degrees (0..180). */
    fun angleDegrees(q: FloatArray): Float {
        val w = abs(q[3]).coerceAtMost(1f)
        return Math.toDegrees(2.0 * acos(w.toDouble())).toFloat()
    }

    /** Shortest-path spherical interpolation. out may alias a. */
    fun slerp(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        var bx = b[0]; var by = b[1]; var bz = b[2]; var bw = b[3]
        var dot = a[0] * bx + a[1] * by + a[2] * bz + a[3] * bw
        if (dot < 0f) {
            bx = -bx; by = -by; bz = -bz; bw = -bw
            dot = -dot
        }
        val wa: Float
        val wb: Float
        if (dot > 0.9995f) {
            wa = 1f - t
            wb = t
        } else {
            val theta = acos(dot)
            val s = sin(theta)
            wa = sin((1f - t) * theta) / s
            wb = sin(t * theta) / s
        }
        set(out, wa * a[0] + wb * bx, wa * a[1] + wb * by, wa * a[2] + wb * bz, wa * a[3] + wb * bw)
        normalize(out, out)
    }

    /** q^f: the same rotation axis with the angle scaled by [f] (f may exceed 1 for extrapolation). Safe to alias. */
    fun scaleAngle(q: FloatArray, f: Float, out: FloatArray) {
        var x = q[0]; var y = q[1]; var z = q[2]; var w = q[3]
        if (w < 0f) { x = -x; y = -y; z = -z; w = -w }
        val halfAngle = acos(w.coerceAtMost(1f))
        val s = sin(halfAngle)
        if (s < 1e-6f) return identity(out)
        fromAxisAngle(x / s, y / s, z / s, 2f * halfAngle * f, out)
    }

    /** Column-major 4x4 rotation matrix for OpenGL. */
    fun toMatrix4(q: FloatArray, out: FloatArray) {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = 1f - 2f * (yy + zz); out[1] = 2f * (xy + wz); out[2] = 2f * (xz - wy); out[3] = 0f
        out[4] = 2f * (xy - wz); out[5] = 1f - 2f * (xx + zz); out[6] = 2f * (yz + wx); out[7] = 0f
        out[8] = 2f * (xz + wy); out[9] = 2f * (yz - wx); out[10] = 1f - 2f * (xx + yy); out[11] = 0f
        out[12] = 0f; out[13] = 0f; out[14] = 0f; out[15] = 1f
    }

    /** Rotates vector (vx, vy, vz) into out[0..2]. */
    fun rotate(q: FloatArray, vx: Float, vy: Float, vz: Float, out: FloatArray) {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        out[0] = vx + w * tx + (y * tz - z * ty)
        out[1] = vy + w * ty + (z * tx - x * tz)
        out[2] = vz + w * tz + (x * ty - y * tx)
    }
}
