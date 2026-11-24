package com.basira.os.core.render.pacing

import kotlin.math.ceil
import kotlin.math.max

/**
 * Even frame pacing: render on every Nth vsync. Uneven pacing (e.g. 45 fps on a 60 Hz panel) judders in a
 * headset, so a target that does not divide the display rate rounds down: 45 on 60 Hz -> 30.
 */
object FramePacer {

    fun vsyncDivisor(targetFps: Int, displayHz: Float): Int {
        if (targetFps <= 0 || displayHz <= 0f) return 1
        return max(1, ceil(displayHz / targetFps - 0.01f).toInt())
    }

    fun effectiveFps(targetFps: Int, displayHz: Float): Float = displayHz / vsyncDivisor(targetFps, displayHz)

    fun shouldRender(vsyncIndex: Long, divisor: Int): Boolean = vsyncIndex % divisor == 0L
}
