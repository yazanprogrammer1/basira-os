package com.basira.os.core.render

import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.render.pass.DirectCameraMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectCameraMathTest {

    private val identity3 = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val q = FloatArray(2)

    @Test
    fun `optical axis hits the image centre`() {
        assertTrue(DirectCameraMath.cameraCoordinate(0f, 0f, identity3, 0.65f, 0.37f, 0f, q))
        assertEquals(0.5f, q[0], 1e-6f)
        assertEquals(0.5f, q[1], 1e-6f)
    }

    @Test
    fun `the camera edges map to 0 and 1`() {
        DirectCameraMath.cameraCoordinate(0.65f, -0.37f, identity3, 0.65f, 0.37f, 0f, q)
        assertEquals(1f, q[0], 1e-6f)
        assertEquals(0f, q[1], 1e-6f)
    }

    @Test
    fun `a larger passthrough scale shows a larger image`() {
        DirectCameraMath.cameraCoordinate(0.65f, 0f, identity3, 0.65f * 1.3f, 0.37f * 1.3f, 0f, q)
        // At the same screen angle we are now well inside the image: it looks bigger.
        assertTrue(q[0] < 1f)
        assertEquals(0.5f + 0.5f / 1.3f, q[0], 1e-5f)
    }

    @Test
    fun `a head turn left since capture moves the image right on screen`() {
        // Head turned left by 5 degrees: capture -> now = yaw(-5); the shader needs its inverse.
        val m = FloatArray(4)
        QuatMath.fromAxisAngle(0f, 1f, 0f, Math.toRadians(-5.0).toFloat(), m)
        val r4 = FloatArray(16)
        QuatMath.toMatrix4(m, r4)
        val inv = FloatArray(9)
        DirectCameraMath.inverseRotation3(r4, inv)
        // The captured centre should now be found slightly to the right of the optical axis.
        val tan5 = Math.tan(Math.toRadians(5.0)).toFloat()
        DirectCameraMath.cameraCoordinate(tan5, 0f, inv, 0.65f, 0.37f, 0f, q)
        assertEquals(0.5f, q[0], 1e-4f)
    }

    @Test
    fun `directions behind the viewer are rejected`() {
        val flip = floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f)
        assertFalse(DirectCameraMath.cameraCoordinate(0f, 0f, flip, 0.65f, 0.37f, 0f, q))
    }
}
