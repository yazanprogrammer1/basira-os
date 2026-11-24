package com.basira.os.core.render.pacing

import android.opengl.GLSurfaceView
import android.view.Choreographer
import com.basira.os.core.render.Compositor

/**
 * Drives a GLSurfaceView at the compositor's target fps.
 * At full rate the view renders continuously (lowest latency); below it, the view renders on demand
 * every Nth vsync so frames stay evenly spaced. Main thread only.
 */
class VsyncRenderDriver(
    private val view: GLSurfaceView,
    private val compositor: Compositor,
    private val displayHz: () -> Float,
) : Choreographer.FrameCallback {

    private var running = false
    private var vsyncIndex = 0L
    private var divisor = 1

    fun start() {
        if (running) return
        running = true
        compositor.onRenderingResumed()
        divisor = 1
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val next = FramePacer.vsyncDivisor(compositor.qualityLevel.renderFps, displayHz())
        if (next != divisor) {
            divisor = next
            view.renderMode = if (divisor == 1) GLSurfaceView.RENDERMODE_CONTINUOUSLY else GLSurfaceView.RENDERMODE_WHEN_DIRTY
            compositor.onRenderingResumed()
        }
        if (divisor > 1 && FramePacer.shouldRender(vsyncIndex, divisor)) view.requestRender()
        vsyncIndex++
        Choreographer.getInstance().postFrameCallback(this)
    }
}
