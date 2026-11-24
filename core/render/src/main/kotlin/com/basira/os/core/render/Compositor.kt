package com.basira.os.core.render

import android.opengl.GLSurfaceView
import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.common.viewer.ViewerProfile
import kotlinx.coroutines.flow.StateFlow

/** Rolling frame statistics, read by the developer overlay and the perf CSV logger. */
data class FrameStats(
    val fps: Float = 0f,
    val frameTimeMsAvg: Float = 0f,
    val frameTimeMsP95: Float = 0f,
    /** Frames slower than 20 ms since rendering started. */
    val jankyFrames: Long = 0L,
    val totalFrames: Long = 0L,
    /** Age of the camera image when it was drawn; -1 when unknown. Approximate motion-to-photon floor. */
    val cameraAgeMs: Float = -1f,
    val tracking: Boolean = false,
) {
    val jankRatio: Float get() = if (totalFrames == 0L) 0f else jankyFrames.toFloat() / totalFrames
}

enum class RenderMode {
    /** Camera image in both eyes. */
    PASSTHROUGH,

    /** Grid + crosshair per eye, for lens calibration. */
    TEST_PATTERN,
}

/**
 * Draws both eyes every frame. It knows nothing about ARCore or WebView:
 * it receives textures and poses only (see CLAUDE.md, module rules).
 * All setters are thread-safe; the GL thread picks changes up on the next frame.
 */
interface Compositor {
    val frameStats: StateFlow<FrameStats>
    val qualityLevel: QualityLevel

    fun setQualityLevel(level: QualityLevel)

    fun setRenderMode(mode: RenderMode)

    /** Live preview while calibrating; null goes back to the stored profile. */
    fun setViewerProfilePreview(profile: ViewerProfile?)

    /** Physical pixel density of the panel (DisplayMetrics.xdpi / ydpi). */
    fun setScreenDensity(xdpi: Float, ydpi: Float)

    /** Called when the host resumes rendering, so the pause gap is not counted as a slow frame. */
    fun onRenderingResumed()

    /** A new renderer for a GLSurfaceView (EGL context version 3). */
    fun createRenderer(): GLSurfaceView.Renderer
}
