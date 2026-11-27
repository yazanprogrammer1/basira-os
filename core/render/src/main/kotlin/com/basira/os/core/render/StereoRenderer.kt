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
import com.basira.os.core.render.layout.ScreenSpec
import com.basira.os.core.render.pass.DirectCameraMath
import com.basira.os.core.render.pass.DirectCameraPass
import com.basira.os.core.render.pass.DistortionPass
import com.basira.os.core.render.pass.TestPatternPass
import com.basira.os.core.render.stats.FrameStatsTracker
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/**
 * The GL thread. Per frame:
 * 1. pull the latest camera frame (non-blocking),
 * 2. passthrough: draw each eye straight to the screen through its distortion mesh, sampling the
 *    full-resolution camera texture with the reprojection rotation applied per pixel;
 *    test pattern: draw the pattern into each eye buffer, then through the distortion mesh,
 * 3. record frame timing.
 * Layout, meshes and eye buffers are rebuilt only when the profile, density or quality level changes.
 */
internal class StereoRenderer(
    private val compositor: StereoCompositor,
    private val frameSource: CameraFrameSource,
    private val logger: Logger,
) : GLSurfaceView.Renderer {

    private lateinit var patternPass: TestPatternPass
    private lateinit var distortionPass: DistortionPass
    private lateinit var directCameraPass: DirectCameraPass
    private var oesTexture = 0

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var eyes: Array<EyeGeometry>? = null
    private val eyeBuffers = arrayOfNulls<EyeFramebuffer>(2)

    private var appliedProfile: ViewerProfile? = null
    private var appliedXdpi = 0f
    private var appliedYdpi = 0f
    private var appliedScale = 0f
    private var loggedHalfTanX = 0f

    private val rotation4 = FloatArray(16)
    private val captureFromNow3 = FloatArray(9)
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)

    private val stats = FrameStatsTracker()
    private var lastPublishNs = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // New EGL context: every GL object from before is gone.
        oesTexture = GlUtil.createOesTexture()
        frameSource.onGlSurfaceCreated(oesTexture)
        patternPass = TestPatternPass()
        distortionPass = DistortionPass()
        directCameraPass = DirectCameraPass(distortionPass)
        eyeBuffers.fill(null)
        eyes = null
        appliedProfile = null
        appliedScale = 0f
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
        val frame = frameSource.update()

        val reprojectionDegrees = when (compositor.renderMode) {
            RenderMode.PASSTHROUGH -> drawPassthrough(frame, geometry, profile)
            RenderMode.TEST_PATTERN -> {
                drawTestPattern(geometry)
                -1f
            }
        }
        recordFrame(frame, reprojectionDegrees)
    }

    /** Returns the reprojection angle applied this frame, or -1 when none was. */
    private fun drawPassthrough(frame: CameraFrame?, geometry: Array<EyeGeometry>, profile: ViewerProfile): Float {
        bindScreen()
        if (frame == null || !frame.hasIntrinsics || frame.geometryVersion <= 0) return -1f

        val reprojecting = frame.reprojectionValid && compositor.reprojectionEnabled
        QuatMath.toMatrix4(if (reprojecting) frame.reprojection else identity, rotation4)
        DirectCameraMath.inverseRotation3(rotation4, captureFromNow3)

        val scale = profile.passthroughScale
        val halfTanX = frame.halfTanX * scale
        val halfTanY = frame.halfTanY * scale
        if (frame.halfTanX != loggedHalfTanX) {
            loggedHalfTanX = frame.halfTanX
            logger.d(TAG, "Camera ${frame.imageWidth}x${frame.imageHeight}, half-tan ${frame.halfTanX} x ${frame.halfTanY}")
        }

        for (eye in 0..1) {
            val g = geometry[eye]
            GLES30.glViewport(g.viewportX, g.viewportY, g.viewportWidth, g.viewportHeight)
            // passthroughShiftPx is in full-resolution eye pixels; convert to this eye's tan units.
            val shiftTan = profile.passthroughShiftPx * g.eyeFov.width / g.viewportWidth
            directCameraPass.draw(
                eye = eye,
                oesTexture = oesTexture,
                fov = g.eyeFov,
                captureFromNow3 = captureFromNow3,
                halfTanX = halfTanX,
                halfTanY = halfTanY,
                shiftTan = if (eye == 0) shiftTan else -shiftTan,
                cornerUv = frame.uv,
            )
        }
        return if (reprojecting) frame.reprojectionDegrees else -1f
    }

    private fun drawTestPattern(geometry: Array<EyeGeometry>) {
        val scale = compositor.qualityLevel.eyeRenderScale
        if (scale != appliedScale) rebuildEyeBuffers(geometry, scale)
        for (eye in 0..1) {
            val buffer = eyeBuffers[eye] ?: continue
            buffer.bind()
            GLES30.glClearColor(NIGHT_R, NIGHT_G, NIGHT_B, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            patternPass.draw(eye)
        }
        bindScreen()
        for (eye in 0..1) {
            val buffer = eyeBuffers[eye] ?: continue
            val g = geometry[eye]
            GLES30.glViewport(g.viewportX, g.viewportY, g.viewportWidth, g.viewportHeight)
            distortionPass.draw(eye, buffer.texture)
        }
    }

    private fun bindScreen() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    private fun rebuildLayout(profile: ViewerProfile) {
        val screen = ScreenSpec(surfaceWidth, surfaceHeight, compositor.xdpi, compositor.ydpi)
        val (left, right) = EyeLayout.compute(screen, profile)
        val geometry = arrayOf(left, right)
        val model = DistortionModel(profile.k1, profile.k2)
        for (eye in 0..1) {
            distortionPass.upload(eye, DistortionMeshBuilder.build(geometry[eye], model))
            patternPass.update(eye, geometry[eye].eyeFov)
        }
        eyes = geometry
        appliedProfile = profile
        appliedXdpi = compositor.xdpi
        appliedYdpi = compositor.ydpi
        appliedScale = 0f // eye buffer size depends on the viewport
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
        logger.d(TAG, "Eye buffers ${eyeBuffers[0]?.width}x${eyeBuffers[0]?.height} (scale $scale)")
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
