package com.basira.os.core.render

import com.basira.os.core.render.distortion.DistortionModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistortionModelTest {

    private val cardboard = DistortionModel(k1 = 0.34f, k2 = 0.55f)

    @Test
    fun `centre is not distorted`() {
        assertEquals(0f, cardboard.distort(0f), 0f)
        assertEquals(1f, cardboard.factor(0f), 0f)
    }

    @Test
    fun `positive coefficients push points outward (pincushion lens)`() {
        assertTrue(cardboard.distort(0.5f) > 0.5f)
        assertEquals(0.5f * (1f + 0.34f * 0.25f + 0.55f * 0.0625f), cardboard.distort(0.5f), 1e-6f)
    }

    @Test
    fun `inverse undoes distort across the visible range`() {
        var r = 0.05f
        while (r <= 1.2f) {
            assertEquals(r, cardboard.distortInverse(cardboard.distort(r)), 1e-4f)
            r += 0.05f
        }
    }

    @Test
    fun `distort is monotonic so the mesh never folds`() {
        var previous = 0f
        var r = 0.01f
        while (r <= 1.5f) {
            val d = cardboard.distort(r)
            assertTrue("not monotonic at r=$r", d > previous)
            previous = d
            r += 0.01f
        }
    }

    @Test
    fun `zero coefficients are the identity`() {
        val none = DistortionModel(0f, 0f)
        assertEquals(0.7f, none.distort(0.7f), 0f)
        assertEquals(0.7f, none.distortInverse(0.7f), 1e-6f)
    }
}
