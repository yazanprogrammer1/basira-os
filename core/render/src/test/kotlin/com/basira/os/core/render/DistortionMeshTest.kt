package com.basira.os.core.render

import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.distortion.DistortionMeshBuilder
import com.basira.os.core.render.distortion.DistortionModel
import com.basira.os.core.render.layout.EyeLayout
import com.basira.os.core.render.layout.ScreenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistortionMeshTest {

    private val screen = ScreenSpec(2400, 1080, 400f, 400f)
    private val profile = ViewerProfile.CARDBOARD_V2
    private val left = EyeLayout.compute(screen, profile).first

    @Test
    fun `mesh has the expected size and valid indices`() {
        val mesh = DistortionMeshBuilder.build(left, DistortionModel(profile.k1, profile.k2), columns = 10, rows = 8)
        assertEquals(10 * 8 * DistortionMeshBuilder.FLOATS_PER_VERTEX, mesh.vertices.size)
        assertEquals(9 * 7 * 6, mesh.indices.size)
        assertTrue(mesh.indices.all { it in 0 until 80 })
    }

    @Test
    fun `positions span the whole eye viewport`() {
        val mesh = DistortionMeshBuilder.build(left, DistortionModel(0.3f, 0.5f), columns = 5, rows = 5)
        val xs = mesh.vertices.filterIndexed { i, _ -> i % 4 == 0 }
        val ys = mesh.vertices.filterIndexed { i, _ -> i % 4 == 1 }
        assertEquals(-1f, xs.min(), 0f)
        assertEquals(1f, xs.max(), 0f)
        assertEquals(-1f, ys.min(), 0f)
        assertEquals(1f, ys.max(), 0f)
    }

    @Test
    fun `without distortion the screen maps straight onto the eye buffer`() {
        val flat = profile.copy(k1 = 0f, k2 = 0f, maxFovDeg = 89f)
        val eye = EyeLayout.compute(screen, flat).first
        val mesh = DistortionMeshBuilder.build(eye, DistortionModel(0f, 0f), columns = 3, rows = 3)
        for (v in 0 until 9) {
            val x = mesh.vertices[v * 4]
            val y = mesh.vertices[v * 4 + 1]
            val u = mesh.vertices[v * 4 + 2]
            val w = mesh.vertices[v * 4 + 3]
            assertEquals((x + 1f) / 2f, u, 1e-4f)
            assertEquals((y + 1f) / 2f, w, 1e-4f)
        }
    }

    @Test
    fun `the lens centre samples the optical axis`() {
        val model = DistortionModel(profile.k1, profile.k2)
        val fov = left.eyeFov
        // Texture coordinate of tan (0, 0) in the eye buffer.
        val u0 = -fov.left / fov.width
        val v0 = -fov.bottom / fov.height
        val mesh = DistortionMeshBuilder.build(left, model, columns = 401, rows = 3)
        // Middle row, column nearest the lens centre.
        val column = (left.lensCenterXPx / left.viewportWidth * 400).toInt()
        val index = (1 * 401 + column) * 4
        assertEquals(u0, mesh.vertices[index + 2], 0.01f)
        assertTrue(mesh.vertices[index + 3] in 0f..1f)
        assertTrue(v0 in 0f..1f)
    }
}
