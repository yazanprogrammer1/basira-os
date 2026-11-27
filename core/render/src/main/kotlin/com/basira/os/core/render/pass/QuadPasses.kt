package com.basira.os.core.render.pass

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.basira.os.core.render.distortion.DistortionMesh
import com.basira.os.core.render.distortion.DistortionMeshBuilder
import com.basira.os.core.render.gl.GlUtil
import com.basira.os.core.render.gl.Shaders
import com.basira.os.core.render.layout.TanBounds

private const val EYES = 2
private const val QUAD_VERTICES = 4
private const val FLOATS_PER_VERTEX = 4
private const val STRIDE = FLOATS_PER_VERTEX * Float.SIZE_BYTES

/** Binds the (position, coord) interleaved layout shared by every pass. */
private fun setupInterleavedAttributes() {
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, STRIDE, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, STRIDE, 2 * Float.SIZE_BYTES)
}

/**
 * Per-eye quad with a dynamic vertex buffer. Subclasses decide what the coord attribute means.
 * Vertex order is a triangle strip: bottom-left, bottom-right, top-left, top-right.
 */
internal abstract class EyeQuadPass(fragmentSource: String) {
    protected val program = GlUtil.compileProgram(Shaders.QUAD_VERTEX, fragmentSource)
    private val vaos = IntArray(EYES) { GlUtil.genVertexArray() }
    private val vbos = IntArray(EYES) { GlUtil.genBuffer() }
    private val scratch = GlUtil.floatBuffer(QUAD_VERTICES * FLOATS_PER_VERTEX)
    private val values = FloatArray(QUAD_VERTICES * FLOATS_PER_VERTEX)
    private val ready = BooleanArray(EYES)

    init {
        for (eye in 0 until EYES) {
            GLES30.glBindVertexArray(vaos[eye])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[eye])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, values.size * Float.SIZE_BYTES, null, GLES30.GL_DYNAMIC_DRAW)
            setupInterleavedAttributes()
        }
        GLES30.glBindVertexArray(0)
    }

    /** positions: NDC rect; coords: 8 floats in strip order. */
    protected fun upload(eye: Int, left: Float, right: Float, bottom: Float, top: Float, coords: FloatArray) {
        val px = floatArrayOf(left, right, left, right)
        val py = floatArrayOf(bottom, bottom, top, top)
        for (v in 0 until QUAD_VERTICES) {
            values[v * 4] = px[v]
            values[v * 4 + 1] = py[v]
            values[v * 4 + 2] = coords[v * 2]
            values[v * 4 + 3] = coords[v * 2 + 1]
        }
        scratch.position(0)
        scratch.put(values)
        scratch.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[eye])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, values.size * Float.SIZE_BYTES, scratch)
        ready[eye] = true
    }

    fun isReady(eye: Int): Boolean = ready[eye]

    protected fun drawQuad(eye: Int) {
        GLES30.glBindVertexArray(vaos[eye])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
        GLES30.glBindVertexArray(0)
    }
}

/**
 * The camera image as a quad of view-space directions at its true angular size (z = -1).
 * Mono passthrough: both eyes get the same image, with an optional inward shift (in tan units).
 * The per-eye MVP = projection(eye fov) * reprojection rotation, set every frame.
 */
internal class CameraPass {
    private val program = GlUtil.compileProgram(Shaders.CAMERA_VERTEX, Shaders.CAMERA_OES_FRAGMENT)
    private val samplerLocation = GLES30.glGetUniformLocation(program, "uCamera")
    private val mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
    private val vaos = IntArray(EYES) { GlUtil.genVertexArray() }
    private val vbos = IntArray(EYES) { GlUtil.genBuffer() }
    private val values = FloatArray(QUAD_VERTICES * CAMERA_FLOATS)
    private val scratch = GlUtil.floatBuffer(values.size)
    private val ready = BooleanArray(EYES)

    init {
        for (eye in 0 until EYES) {
            GLES30.glBindVertexArray(vaos[eye])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[eye])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, values.size * Float.SIZE_BYTES, null, GLES30.GL_DYNAMIC_DRAW)
            val stride = CAMERA_FLOATS * Float.SIZE_BYTES
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * Float.SIZE_BYTES)
        }
        GLES30.glBindVertexArray(0)
    }

    /** [uv]: texture coordinates in strip order (bottom-left, bottom-right, top-left, top-right). */
    fun update(eye: Int, halfTanX: Float, halfTanY: Float, shiftTan: Float, uv: FloatArray) {
        val xs = floatArrayOf(-halfTanX, halfTanX, -halfTanX, halfTanX)
        val ys = floatArrayOf(-halfTanY, -halfTanY, halfTanY, halfTanY)
        for (v in 0 until QUAD_VERTICES) {
            val o = v * CAMERA_FLOATS
            values[o] = xs[v] + shiftTan
            values[o + 1] = ys[v]
            values[o + 2] = -1f
            values[o + 3] = uv[v * 2]
            values[o + 4] = uv[v * 2 + 1]
        }
        scratch.position(0)
        scratch.put(values)
        scratch.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[eye])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, values.size * Float.SIZE_BYTES, scratch)
        ready[eye] = true
    }

    fun draw(eye: Int, oesTexture: Int, mvp: FloatArray) {
        if (!ready[eye]) return
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES30.glUniform1i(samplerLocation, 0)
        GLES30.glBindVertexArray(vaos[eye])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        /** direction (3) + uv (2) */
        const val CAMERA_FLOATS = 5
    }
}

/** Full-buffer calibration pattern; coord = tan angle at each corner of the eye's field of view. */
internal class TestPatternPass : EyeQuadPass(Shaders.TEST_PATTERN_FRAGMENT) {

    fun update(eye: Int, fov: TanBounds) {
        val tan = floatArrayOf(
            fov.left, fov.bottom,
            fov.right, fov.bottom,
            fov.left, fov.top,
            fov.right, fov.top,
        )
        upload(eye, -1f, 1f, -1f, 1f, tan)
    }

    fun draw(eye: Int) {
        if (!isReady(eye)) return
        GLES30.glUseProgram(program)
        drawQuad(eye)
    }
}

/** Draws each eye buffer onto its half of the screen through the pre-computed distortion mesh. */
internal class DistortionPass {
    private val program = GlUtil.compileProgram(Shaders.QUAD_VERTEX, Shaders.DISTORTION_FRAGMENT)
    private val samplerLocation = GLES30.glGetUniformLocation(program, "uEye")
    private val vaos = IntArray(EYES) { GlUtil.genVertexArray() }
    private val vbos = IntArray(EYES) { GlUtil.genBuffer() }
    private val ibos = IntArray(EYES) { GlUtil.genBuffer() }
    private val indexCounts = IntArray(EYES)

    fun upload(eye: Int, mesh: DistortionMesh) {
        GLES30.glBindVertexArray(vaos[eye])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[eye])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            mesh.vertices.size * Float.SIZE_BYTES,
            GlUtil.floatBuffer(mesh.vertices),
            GLES30.GL_STATIC_DRAW,
        )
        check(DistortionMeshBuilder.FLOATS_PER_VERTEX == FLOATS_PER_VERTEX)
        setupInterleavedAttributes()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[eye])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            mesh.indices.size * Short.SIZE_BYTES,
            GlUtil.shortBuffer(mesh.indices),
            GLES30.GL_STATIC_DRAW,
        )
        GLES30.glBindVertexArray(0)
        indexCounts[eye] = mesh.indices.size
    }

    fun draw(eye: Int, eyeTexture: Int) {
        if (indexCounts[eye] == 0) return
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, eyeTexture)
        GLES30.glUniform1i(samplerLocation, 0)
        GLES30.glBindVertexArray(vaos[eye])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCounts[eye], GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }
}
