package com.basira.os.core.common

import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.common.math.Quaternion
import com.basira.os.core.common.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class QuatMathTest {

    private fun q(axisX: Float, axisY: Float, axisZ: Float, degrees: Float) =
        FloatArray(4).also { QuatMath.fromAxisAngle(axisX, axisY, axisZ, Math.toRadians(degrees.toDouble()).toFloat(), it) }

    private fun assertQuat(expected: FloatArray, actual: FloatArray, eps: Float = 1e-5f) {
        // q and -q are the same rotation.
        val sign = if (expected[3] * actual[3] + expected[0] * actual[0] + expected[1] * actual[1] + expected[2] * actual[2] < 0) -1f else 1f
        for (i in 0..3) assertEquals(expected[i], sign * actual[i], eps)
    }

    @Test
    fun `multiply matches the Quaternion class`() {
        val a = q(0f, 1f, 0f, 30f)
        val b = q(1f, 0f, 0f, 50f)
        val out = FloatArray(4)
        QuatMath.multiply(a, b, out)
        val expected = Quaternion(a[0], a[1], a[2], a[3]) * Quaternion(b[0], b[1], b[2], b[3])
        assertQuat(floatArrayOf(expected.x, expected.y, expected.z, expected.w), out)
    }

    @Test
    fun `rotate agrees with the Quaternion class`() {
        val a = q(1f, 2f, 3f, 70f)
        val out = FloatArray(3)
        QuatMath.rotate(a, 0.3f, -1f, 2f, out)
        val expected = Quaternion(a[0], a[1], a[2], a[3]).rotate(Vec3(0.3f, -1f, 2f))
        assertEquals(expected.x, out[0], 1e-5f)
        assertEquals(expected.y, out[1], 1e-5f)
        assertEquals(expected.z, out[2], 1e-5f)
    }

    @Test
    fun `conjugate undoes the rotation`() {
        val a = q(0.2f, 1f, -0.4f, 42f)
        val inv = FloatArray(4)
        val out = FloatArray(4)
        QuatMath.conjugate(a, inv)
        QuatMath.multiply(a, inv, out)
        assertQuat(floatArrayOf(0f, 0f, 0f, 1f), out)
    }

    @Test
    fun `slerp halfway is half the angle`() {
        val a = q(0f, 1f, 0f, 0f)
        val b = q(0f, 1f, 0f, 40f)
        val out = FloatArray(4)
        QuatMath.slerp(a, b, 0.5f, out)
        assertQuat(q(0f, 1f, 0f, 20f), out)
    }

    @Test
    fun `scaleAngle extrapolates along the same axis`() {
        val out = FloatArray(4)
        QuatMath.scaleAngle(q(0f, 0f, 1f, 10f), 2.5f, out)
        assertQuat(q(0f, 0f, 1f, 25f), out)
        assertEquals(25f, QuatMath.angleDegrees(out), 1e-3f)
    }

    @Test
    fun `matrix rotates like the quaternion`() {
        val a = q(0f, 1f, 0f, 90f)
        val m = FloatArray(16)
        QuatMath.toMatrix4(a, m)
        // Column-major: forward (0,0,-1) -> -column 2.
        assertEquals(-1f, -m[8], 1e-6f)
        assertEquals(0f, -m[10], 1e-6f)
        val v = FloatArray(3)
        QuatMath.rotate(a, 0f, 0f, -1f, v)
        assertEquals(v[0], -m[8], 1e-6f)
        assertEquals(PI.toFloat() / 2f, Math.toRadians(QuatMath.angleDegrees(a).toDouble()).toFloat(), 1e-5f)
    }
}
