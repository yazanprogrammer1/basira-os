package com.basira.os.core.render

import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.render.layout.Projection
import com.basira.os.core.render.layout.TanBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionTest {

    private val bounds = TanBounds(left = -1.7f, right = 1.2f, bottom = -1.4f, top = 1.4f)
    private val p = FloatArray(16).also { Projection.fromTanBounds(bounds, it) }
    private val ndc = FloatArray(2)

    @Test
    fun `frustum corners land on the NDC corners`() {
        Projection.project(p, bounds.left, bounds.bottom, -1f, ndc)
        assertEquals(-1f, ndc[0], 1e-5f)
        assertEquals(-1f, ndc[1], 1e-5f)
        Projection.project(p, bounds.right * 3f, bounds.top * 3f, -3f, ndc)
        assertEquals(1f, ndc[0], 1e-5f)
        assertEquals(1f, ndc[1], 1e-5f)
    }

    @Test
    fun `the optical axis lands off-centre for an asymmetric eye`() {
        Projection.project(p, 0f, 0f, -1f, ndc)
        assertEquals((0f - bounds.left) / bounds.width * 2f - 1f, ndc[0], 1e-5f)
        assertEquals(0f, ndc[1], 1e-5f)
    }

    @Test
    fun `reprojection rotation shifts the camera image across the screen`() {
        // Head turned left by 5 degrees since capture: the captured centre now appears to the right.
        val q = FloatArray(4)
        QuatMath.fromAxisAngle(0f, 1f, 0f, Math.toRadians(-5.0).toFloat(), q)
        val r = FloatArray(16)
        QuatMath.toMatrix4(q, r)
        val mvp = FloatArray(16)
        Projection.multiply(p, r, mvp)
        Projection.project(p, 0f, 0f, -1f, ndc)
        val before = ndc[0]
        Projection.project(mvp, 0f, 0f, -1f, ndc)
        assertTrue(ndc[0] > before)
    }
}
