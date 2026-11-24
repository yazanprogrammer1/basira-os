package com.basira.os.core.render

import com.basira.os.core.render.pacing.FramePacer
import com.basira.os.core.render.stats.FrameStatsTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsAndPacingTest {

    private val frameNs = 16_666_667L

    @Test
    fun `steady 60 fps`() {
        val tracker = FrameStatsTracker()
        var t = 0L
        repeat(200) {
            t += frameNs
            tracker.onFrame(t, cameraAgeMs = 40f, tracking = true)
        }
        val stats = tracker.snapshot()
        assertEquals(60f, stats.fps, 0.1f)
        assertEquals(16.67f, stats.frameTimeMsP95, 0.01f)
        assertEquals(0L, stats.jankyFrames)
        assertEquals(199L, stats.totalFrames)
        assertEquals(40f, stats.cameraAgeMs, 0f)
        assertTrue(stats.tracking)
    }

    @Test
    fun `slow frames show up in p95 and jank count`() {
        val tracker = FrameStatsTracker(capacity = 100)
        var t = 0L
        tracker.onFrame(t, -1f, false)
        repeat(100) { i ->
            t += if (i % 10 == 0) 33_333_333L else frameNs
            tracker.onFrame(t, -1f, false)
        }
        val stats = tracker.snapshot()
        assertEquals(10L, stats.jankyFrames)
        assertEquals(33.33f, stats.frameTimeMsP95, 0.01f)
        assertEquals(0.1f, stats.jankRatio, 1e-6f)
    }

    @Test
    fun `reset ignores the pause gap`() {
        val tracker = FrameStatsTracker()
        tracker.onFrame(0L, -1f, false)
        tracker.onFrame(frameNs, -1f, false)
        tracker.reset()
        tracker.onFrame(10_000_000_000L, -1f, false)
        tracker.onFrame(10_000_000_000L + frameNs, -1f, false)
        assertEquals(0L, tracker.snapshot().jankyFrames)
    }

    @Test
    fun `pacer keeps frames evenly spaced`() {
        assertEquals(1, FramePacer.vsyncDivisor(60, 60f))
        assertEquals(1, FramePacer.vsyncDivisor(60, 59.94f))
        assertEquals(2, FramePacer.vsyncDivisor(45, 60f))
        assertEquals(2, FramePacer.vsyncDivisor(45, 90f))
        assertEquals(2, FramePacer.vsyncDivisor(60, 120f))
        assertEquals(30f, FramePacer.effectiveFps(45, 60f), 0f)
        assertTrue(FramePacer.shouldRender(4, 2))
        assertFalse(FramePacer.shouldRender(5, 2))
    }
}
