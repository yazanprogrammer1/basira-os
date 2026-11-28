package com.basira.os.core.common

import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.math.QuatMath
import org.junit.Assert.assertEquals
import org.junit.Test

class Mat4Test {

    private fun pose(tx: Float, ty: Float, tz: Float, yawDeg: Float): FloatArray {
        val q = FloatArray(4)
        QuatMath.fromAxisAngle(0f, 1f, 0f, Math.toRadians(yawDeg.toDouble()).toFloat(), q)
        return FloatArray(7).also { Mat4.setPose(it, tx, ty, tz, q) }
    }

    @Test
    fun `pose matrix rotates then translates`() {
        val m = FloatArray(16)
        Mat4.fromPose(pose(1f, 2f, 3f, 90f), m)
        val p = FloatArray(3)
        Mat4.transformPoint(m, 0f, 0f, -1f, p)
        // Forward yawed 90 deg left is -x, then translated.
        assertEquals(0f, p[0], 1e-5f)
        assertEquals(2f, p[1], 1e-5f)
        assertEquals(3f, p[2], 1e-5f)
    }

    @Test
    fun `rigid inverse undoes the pose`() {
        val m = FloatArray(16)
        val inv = FloatArray(16)
        val id = FloatArray(16)
        Mat4.fromPose(pose(-0.4f, 1.6f, 2.2f, 37f), m)
        Mat4.rigidInverse(m, inv)
        Mat4.multiply(inv, m, id)
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, id[i], 1e-5f)
    }

    @Test
    fun `view matrix puts a panel 1_5 m ahead at -z`() {
        val head = FloatArray(16)
        val view = FloatArray(16)
        Mat4.fromPose(pose(5f, 1.7f, -2f, 0f), head)
        Mat4.rigidInverse(head, view)
        val p = FloatArray(3)
        Mat4.transformPoint(view, 5f, 1.7f, -3.5f, p)
        assertEquals(0f, p[0], 1e-5f)
        assertEquals(0f, p[1], 1e-5f)
        assertEquals(-1.5f, p[2], 1e-5f)
    }
}
