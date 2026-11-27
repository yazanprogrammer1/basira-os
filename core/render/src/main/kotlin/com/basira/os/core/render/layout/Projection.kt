package com.basira.os.core.render.layout

/**
 * Off-axis projection straight from tan-angle bounds (near plane at distance 1, depth unused).
 * A view-space direction (x, y, z) with z < 0 lands at NDC ((x/-z - left) / width * 2 - 1, ...),
 * and the GPU interpolates texture coordinates perspective-correctly.
 */
object Projection {

    /** Column-major 4x4 for OpenGL. */
    fun fromTanBounds(b: TanBounds, out: FloatArray) {
        out.fill(0f)
        out[0] = 2f / b.width
        out[5] = 2f / b.height
        out[8] = (b.right + b.left) / b.width
        out[9] = (b.top + b.bottom) / b.height
        out[11] = -1f
    }

    /** out = a * b, column-major 4x4. out must not alias a or b. */
    fun multiply(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
    }

    /** Applies a column-major matrix to (x, y, z, 1) and returns NDC (x, y) in out[0..1]. Test helper. */
    fun project(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        out[0] = cx / cw
        out[1] = cy / cw
    }
}
