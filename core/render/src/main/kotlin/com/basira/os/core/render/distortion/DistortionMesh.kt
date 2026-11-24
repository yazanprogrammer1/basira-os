package com.basira.os.core.render.distortion

import com.basira.os.core.render.layout.EyeGeometry
import kotlin.math.sqrt

/**
 * Pre-computed lens pre-warp for one eye.
 * [vertices] = (x, y) in the eye viewport's NDC, then (u, v) into the eye buffer; 4 floats per vertex.
 * u or v outside [0, 1] means "outside the visible field": the shader paints it black.
 */
class DistortionMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
    val columns: Int,
    val rows: Int,
)

object DistortionMeshBuilder {

    const val FLOATS_PER_VERTEX = 4
    const val DEFAULT_RESOLUTION = 40

    /**
     * The grid is uniform on the screen. For each screen point we ask the lens model where the eye sees it,
     * and sample the eye buffer there. No inverse needed, and cheap on the GPU (one texture fetch per pixel).
     */
    fun build(
        eye: EyeGeometry,
        model: DistortionModel,
        columns: Int = DEFAULT_RESOLUTION,
        rows: Int = DEFAULT_RESOLUTION,
    ): DistortionMesh {
        require(columns >= 2 && rows >= 2) { "Mesh needs at least 2x2 vertices" }
        require(columns * rows <= Short.MAX_VALUE) { "Too many vertices for 16-bit indices" }

        val vertices = FloatArray(columns * rows * FLOATS_PER_VERTEX)
        val screen = eye.screenTan
        val fov = eye.eyeFov
        var i = 0
        for (row in 0 until rows) {
            val sy = row / (rows - 1f)
            for (col in 0 until columns) {
                val sx = col / (columns - 1f)
                val tanX = screen.left + sx * screen.width
                val tanY = screen.bottom + sy * screen.height
                val f = model.factor(sqrt(tanX * tanX + tanY * tanY))
                vertices[i++] = sx * 2f - 1f
                vertices[i++] = sy * 2f - 1f
                vertices[i++] = (tanX * f - fov.left) / fov.width
                vertices[i++] = (tanY * f - fov.bottom) / fov.height
            }
        }

        val indices = ShortArray((columns - 1) * (rows - 1) * 6)
        var k = 0
        for (row in 0 until rows - 1) {
            for (col in 0 until columns - 1) {
                val bl = (row * columns + col).toShort()
                val br = (row * columns + col + 1).toShort()
                val tl = ((row + 1) * columns + col).toShort()
                val tr = ((row + 1) * columns + col + 1).toShort()
                indices[k++] = bl; indices[k++] = br; indices[k++] = tl
                indices[k++] = tl; indices[k++] = br; indices[k++] = tr
            }
        }
        return DistortionMesh(vertices, indices, columns, rows)
    }
}
