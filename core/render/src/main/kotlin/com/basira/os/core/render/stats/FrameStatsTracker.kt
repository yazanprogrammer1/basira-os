package com.basira.os.core.render.stats

import com.basira.os.core.common.camera.HeadTracking
import com.basira.os.core.render.FrameStats

/**
 * Rolling frame-time statistics. [onFrame] is allocation-free (GL thread);
 * [snapshot] allocates and is meant for ~2 calls per second.
 */
class FrameStatsTracker(
    private val capacity: Int = 120,
    /** 1.5 vsync at 60 Hz: CPU-side callback times jitter by several ms without a missed vsync. */
    private val jankThresholdMs: Float = 25f,
) {
    private val intervalsMs = FloatArray(capacity)
    private var count = 0
    private var next = 0
    private var lastFrameNs = NO_FRAME
    private var totalFrames = 0L
    private var jankyFrames = 0L
    private var cameraAgeMs = -1f
    private var tracking = false
    private var reprojectionDegrees = -1f
    private var headTracking = HeadTracking.NONE

    fun onFrame(
        nowNs: Long,
        cameraAgeMs: Float,
        tracking: Boolean,
        reprojectionDegrees: Float = -1f,
        headTracking: HeadTracking = HeadTracking.NONE,
    ) {
        if (lastFrameNs != NO_FRAME) {
            val interval = (nowNs - lastFrameNs) / 1_000_000f
            intervalsMs[next] = interval
            next = (next + 1) % capacity
            if (count < capacity) count++
            totalFrames++
            if (interval > jankThresholdMs) jankyFrames++
        }
        lastFrameNs = nowNs
        if (cameraAgeMs >= 0f) this.cameraAgeMs = cameraAgeMs
        this.tracking = tracking
        this.reprojectionDegrees = reprojectionDegrees
        this.headTracking = headTracking
    }

    /** Call when rendering pauses so the gap is not counted as a slow frame. */
    fun reset() {
        lastFrameNs = NO_FRAME
        count = 0
        next = 0
    }

    fun snapshot(): FrameStats {
        if (count == 0) return FrameStats(totalFrames = totalFrames, jankyFrames = jankyFrames, cameraAgeMs = cameraAgeMs, tracking = tracking, reprojectionDegrees = reprojectionDegrees, headTracking = headTracking)
        val window = intervalsMs.copyOf(count)
        val sum = window.sum()
        window.sort()
        val p95Index = ((count - 1) * 0.95f).toInt()
        val avg = sum / count
        return FrameStats(
            fps = if (avg > 0f) 1000f / avg else 0f,
            frameTimeMsAvg = avg,
            frameTimeMsP95 = window[p95Index],
            jankyFrames = jankyFrames,
            totalFrames = totalFrames,
            cameraAgeMs = cameraAgeMs,
            tracking = tracking,
            reprojectionDegrees = reprojectionDegrees,
            headTracking = headTracking,
        )
    }
}

private const val NO_FRAME = Long.MIN_VALUE
