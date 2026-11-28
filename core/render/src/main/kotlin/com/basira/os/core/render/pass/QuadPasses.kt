package com.basira.os.core.render.pass

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.basira.os.core.render.distortion.DistortionMesh
import com.basira.os.core.render.distortion.DistortionMeshBuilder
import com.basira.os.core.render.gl.GlUtil
import com.basira.os.core.render.gl.Shaders
import com.basira.os.core.render.layout.TanBounds
import com.basira.os.core.render.panel.PanelEntry

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
 * Passthrough drawn straight to the screen through each eye's distortion mesh, sampling the
 * camera texture at full resolution (see [DirectCameraMath] for the per-pixel mapping).
 */
internal class DirectCameraPass(private val mesh: DistortionPass) {
    private val program = GlUtil.compileProgram(Shaders.QUAD_VERTEX, Shaders.DIRECT_CAMERA_FRAGMENT)
    private val camera = GLES30.glGetUniformLocation(program, "uCamera")
    private val fovMin = GLES30.glGetUniformLocation(program, "uFovMin")
    private val fovSize = GLES30.glGetUniformLocation(program, "uFovSize")
    private val captureFromNow = GLES30.glGetUniformLocation(program, "uCaptureFromNow")
    private val halfTan = GLES30.glGetUniformLocation(program, "uHalfTan")
    private val shift = GLES30.glGetUniformLocation(program, "uShift")
    private val uv00 = GLES30.glGetUniformLocation(program, "uUv00")
    private val uvX = GLES30.glGetUniformLocation(program, "uUvX")
    private val uvY = GLES30.glGetUniformLocation(program, "uUvY")

    fun draw(
        eye: Int,
        oesTexture: Int,
        fov: TanBounds,
        captureFromNow3: FloatArray,
        halfTanX: Float,
        halfTanY: Float,
        shiftTan: Float,
        cornerUv: FloatArray,
    ) {
        GLES30.glUseProgram(program)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES30.glUniform1i(camera, 0)
        GLES30.glUniform2f(fovMin, fov.left, fov.bottom)
        GLES30.glUniform2f(fovSize, fov.width, fov.height)
        GLES30.glUniformMatrix3fv(captureFromNow, 1, false, captureFromNow3, 0)
        GLES30.glUniform2f(halfTan, halfTanX, halfTanY)
        GLES30.glUniform1f(shift, shiftTan)
        // Corner order: bottom-left, bottom-right, top-left (top-right follows: the map is affine).
        GLES30.glUniform2f(uv00, cornerUv[0], cornerUv[1])
        GLES30.glUniform2f(uvX, cornerUv[2] - cornerUv[0], cornerUv[3] - cornerUv[1])
        GLES30.glUniform2f(uvY, cornerUv[4] - cornerUv[0], cornerUv[5] - cornerUv[1])
        mesh.drawMesh(eye)
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
    private val compositeProgram = GlUtil.compileProgram(Shaders.QUAD_VERTEX, Shaders.DISTORTION_COMPOSITE_FRAGMENT)
    private val compositeSampler = GLES30.glGetUniformLocation(compositeProgram, "uEye")
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
        drawMesh(eye)
    }

    /** Blends an eye buffer (premultiplied alpha) over what is already on screen. */
    fun drawComposite(eye: Int, eyeTexture: Int) {
        GLES30.glUseProgram(compositeProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, eyeTexture)
        GLES30.glUniform1i(compositeSampler, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        drawMesh(eye)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /** Draws the eye's mesh with whatever program is bound (shared by the direct camera pass). */
    fun drawMesh(eye: Int) {
        if (indexCounts[eye] == 0) return
        GLES30.glBindVertexArray(vaos[eye])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCounts[eye], GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }
}

/** A window quad in the world: rounded glass frame, page texture from its SurfaceTexture. */
internal class WindowPass {
    private val program = GlUtil.compileProgram(Shaders.WINDOW_VERTEX, Shaders.WINDOW_FRAGMENT)
    private val mvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
    private val sizeLocation = GLES30.glGetUniformLocation(program, "uSize")
    private val panelLocation = GLES30.glGetUniformLocation(program, "uPanel")
    private val texMatrixLocation = GLES30.glGetUniformLocation(program, "uTexMatrix")
    private val radiusLocation = GLES30.glGetUniformLocation(program, "uRadius")
    private val borderLocation = GLES30.glGetUniformLocation(program, "uBorder")
    private val brightnessLocation = GLES30.glGetUniformLocation(program, "uBrightness")
    private val vao = GlUtil.genVertexArray()
    private val vbo = GlUtil.genBuffer()

    init {
        val quad = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * Float.SIZE_BYTES, GlUtil.floatBuffer(quad), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * Float.SIZE_BYTES, 0)
        GLES30.glBindVertexArray(0)
    }

    fun draw(mvp: FloatArray, panel: PanelEntry, brightness: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES30.glUniform2f(sizeLocation, panel.widthMeters, panel.heightMeters)
        GLES30.glUniformMatrix4fv(texMatrixLocation, 1, false, panel.texMatrix, 0)
        GLES30.glUniform1f(radiusLocation, CORNER_RADIUS_M)
        GLES30.glUniform1f(borderLocation, BORDER_M)
        GLES30.glUniform1f(brightnessLocation, brightness)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, panel.texture)
        GLES30.glUniform1i(panelLocation, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    private companion object {
        /** About 26 CSS px on a 1.4 m, 853 CSS-px-wide page (DESIGN.md window radius). */
        const val CORNER_RADIUS_M = 0.042f
        const val BORDER_M = 0.003f
    }
}
