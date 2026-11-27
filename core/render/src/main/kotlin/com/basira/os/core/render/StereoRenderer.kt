package com.basira.os.core.render

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import com.basira.os.core.common.camera.CameraFrame
import com.basira.os.core.common.camera.CameraFrameSource
import com.basira.os.core.common.logging.Logger
import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.distortion.DistortionMeshBuilder
import com.basira.os.core.render.distortion.DistortionModel
import com.basira.os.core.render.gl.EyeFramebuffer
import com.basira.os.core.render.gl.GlUtil
import com.basira.os.core.render.layout.EyeGeometry
import com.basira.os.core.render.layout.EyeLayout
import com.basira.os.core.render.layout.Projection
import com.basira.os.core.render.layout.ScreenSpec
import com.basira.os.core.render.pass.CameraPass
import com.basira.os.core.render.pass.DistortionPass
import com.basira.os.core.render.pass.TestPatternPass
import com.basira.os.core.render.stats.FrameStatsTracker
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * The GL thread. Per frame:
 * 1. pull the latest camera frame (non-blocking),
 * 2. draw each eye into its own buffer (camera quad or test pattern),
 * 3. draw both buffers to the screen through the lens distortion mesh,
 * 4. record frame timing.
 * Rebuilds layout, meshes and buffers only when the profile, density or quality level changes.
 */
internal class StereoRenderer(
    private val compositor: StereoCompositor,
    private val frameSource: CameraFrameSource,
    private val logger: Logger,
) : GLSurfaceView.Renderer {

    private lateinit var cameraPass: CameraPass
    private lateinit var patternPass: TestPatternPass
    private lateinit var distortionPass: DistortionPass
    private var oesTexture = 0

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var eyes: Array<EyeGeometry>? = null
    private val eyeBuffers = arrayOfNulls<EyeFramebuffer>(2)

    private var appliedProfile: ViewerProfile? = null
    private var appliedXdpi = 0f
    private var appliedYdpi = 0f
    private var appliedScale = 0f
    private var layoutVersion = 0
    private var cameraLayoutVersion = -1
    private var cameraGeometryVersion = -1
    private var cameraHalfTanX = 0f

    private val projections = Array(2) { FloatArray(16) }
    private val rotation = FloatArray(16)
    private val mvp = FloatArray(16)
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)

    private val stats = FrameStatsTracker()
    private var lastPublishNs = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // New EGL context: every GL object from before is gone.
        oesTexture = GlUtil.createOesTexture()
        frameSource.onGlSurfaceCreated(oesTexture)
        cameraPass = CameraPass()
        patternPass = TestPatternPass()
        distortionPass = DistortionPass()
        eyeBuffers.fill(null)
        eyes = null
        appliedProfile = null
        appliedScale = 0f
        cameraLayoutVersion = -1
        cameraGeometryVersion = -1
        stats.reset()
        GlUtil.checkError("onSurfaceCreated")
        logger.d(TAG, "GL surface created: ${GLES30.glGetString(GLES30.GL_RENDERER)}")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        appliedProfile = null // force a layout rebuild
        logger.d(TAG, "Surface ${width}x$height")
    }

    override fun onDrawFrame(gl: GL10?) {
        if (compositor.consumeStatsReset()) stats.reset()
        if (surfaceWidth == 0 || surfaceHeight == 0) return

        val profile = compositor.currentProfile()
        if (profile != appliedProfile || compositor.xdpi != appliedXdpi || compositor.ydpi != appliedYdpi) {
            rebuildLayout(profile)
        }
        val geometry = eyes ?: return
        val scale = compositor.qualityLevel.eyeRenderScale
        if (scale != appliedScale) rebuildEyeBuffers(geometry, scale)

        val frame = frameSource.update()
        if (frame != null && frame.hasIntrinsics && frame.geometryVersion > 0 &&
            (frame.geometryVersion != cameraGeometryVersion || layoutVersion != cameraLayoutVersion)
        ) {
            updateCameraQuads(frame, geometry, profile)
        }

        val reprojecting = frame != null && frame.reprojectionValid && compositor.reprojectionEnabled
        QuatMath.toMatrix4(if (reprojecting) frame.reprojection else identity, rotation)

        val mode = compositor.renderMode
        for (eye in 0..1) {
            val buffer = eyeBuffers[eye] ?: continue
            buffer.bind()
            GLES30.glClearColor(NIGHT_R, NIGHT_G, NIGHT_B, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            when (mode) {
                RenderMode.TEST_PATTERN -> patternPass.draw(eye)
                RenderMode.PASSTHROUGH -> if (frame != null && cameraGeometryVersion >= 0) {
                    Projection.multiply(projections[eye], rotation, mvp)
                    cameraPass.draw(eye, oesTexture, mvp)
                }
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        for (eye in 0..1) {
            val buffer = eyeBuffers[eye] ?: continue
            val g = geometry[eye]
            GLES30.glViewport(g.viewportX, g.viewportY, g.viewportWidth, g.viewportHeight)
            distortionPass.draw(eye, buffer.texture)
        }

        recordFrame(frame, if (reprojecting) frame.reprojectionDegrees else -1f)
    }

    private fun rebuildLayout(profile: ViewerProfile) {
        val screen = ScreenSpec(surfaceWidth, surfaceHeight, compositor.xdpi, compositor.ydpi)
        val (left, right) = EyeLayout.compute(screen, profile)
        val geometry = arrayOf(left, right)
        val model = DistortionModel(profile.k1, profile.k2)
        for (eye in 0..1) {
            distortionPass.upload(eye, DistortionMeshBuilder.build(geometry[eye], model))
            patternPass.update(eye, geometry[eye].eyeFov)
            Projection.fromTanBounds(geometry[eye].eyeFov, projections[eye])
        }
        eyes = geometry
        appliedProfile = profile
        appliedXdpi = compositor.xdpi
        appliedYdpi = compositor.ydpi
        appliedScale = 0f // eye buffer size depends on the viewport
        layoutVersion++
        logger.d(TAG, "Layout rebuilt: left fov=${left.eyeFov}, lens=(${left.lensCenterXPx}, ${left.lensCenterYPx})")
    }

    private fun rebuildEyeBuffers(geometry: Array<EyeGeometry>, scale: Float) {
        for (eye in 0..1) {
            eyeBuffers[eye]?.release()
            val g = geometry[eye]
            eyeBuffers[eye] = EyeFramebuffer(
                (g.viewportWidth * scale).roundToInt().coerceAtLeast(1),
                (g.viewportHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
        appliedScale = scale
        cameraLayoutVersion = -1 // shift is in eye-buffer pixels
        logger.d(TAG, "Eye buffers ${eyeBuffers[0]?.width}x${eyeBuffers[0]?.height} (scale $scale)")
    }

    private fun updateCameraQuads(frame: CameraFrame, geometry: Array<EyeGeometry>, profile: ViewerProfile) {
        val bufferWidth = eyeBuffers[0]?.width ?: return
        val shiftNdc = 2f * profile.passthroughShiftPx * appliedScale / bufferWidth
        for (eye in 0..1) {
            val inwardNdc = if (eye == 0) shiftNdc else -shiftNdc
            val inwardTan = inwardNdc * geometry[eye].eyeFov.width / 2f
            cameraPass.update(eye, frame.halfTanX, frame.halfTanY, inwardTan, frame.uv)
        }
        cameraGeometryVersion = frame.geometryVersion
        cameraLayoutVersion = layoutVersion
        if (frame.halfTanX != cameraHalfTanX) {
            cameraHalfTanX = frame.halfTanX
            logger.d(TAG, "Camera ${frame.imageWidth}x${frame.imageHeight}, half-tan ${frame.halfTanX} x ${frame.halfTanY}")
        }
    }

    private fun recordFrame(frame: CameraFrame?, reprojectionDegrees: Float) {
        val now = System.nanoTime()
        val cameraAgeMs = if (frame != null && frame.timestampNs > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - frame.timestampNs) / 1_000_000f).takeIf { it in 0f..1000f } ?: -1f
        } else {
            -1f
        }
        stats.onFrame(now, cameraAgeMs, frame?.isTracking == true, reprojectionDegrees)
        if (now - lastPublishNs >= PUBLISH_INTERVAL_NS) {
            lastPublishNs = now
            compositor.publish(stats.snapshot())
        }
    }

    private companion object {
        const val TAG = "Render"
        const val PUBLISH_INTERVAL_NS = 500_000_000L
        const val NIGHT_R = 0.043f
        const val NIGHT_G = 0.059f
        const val NIGHT_B = 0.051f
    }
}
