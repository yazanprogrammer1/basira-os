package com.basira.os.core.tracking

import android.view.Surface
import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.tracking.orientation.OrientationHistory
import com.basira.os.core.tracking.orientation.ViewFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationTest {

    private val ms = 1_000_000L

    /** Yaw (about the device's y axis) in degrees. */
    private fun yaw(deg: Float) = FloatArray(4).also {
        QuatMath.fromAxisAngle(0f, 1f, 0f, Math.toRadians(deg.toDouble()).toFloat(), it)
    }

    private fun OrientationHistory.add(tNs: Long, q: FloatArray) = add(tNs, q[0], q[1], q[2], q[3])

    /** Head turning at 100 deg/s, one sample every 5 ms. */
    private fun turningHistory(): OrientationHistory = OrientationHistory(capacity = 64).apply {
        for (i in 0..40) add(i * 5 * ms, yaw(i * 0.5f))
    }

    @Test
    fun `interpolates between samples`() {
        val out = FloatArray(4)
        assertTrue(turningHistory().sampleAt(52 * ms, out))
        assertEquals(5.2f, QuatMath.angleDegrees(out), 1e-3f)
    }

    @Test
    fun `extrapolates a constant turn into the future`() {
        val out = FloatArray(4)
        // Newest sample: t = 200 ms, 20 degrees. 16 ms later at 100 deg/s -> 21.6 degrees.
        assertTrue(turningHistory().predicted(16 * ms, out))
        assertEquals(21.6f, QuatMath.angleDegrees(out), 1e-2f)
    }

    @Test
    fun `refuses times it cannot cover`() {
        val h = turningHistory()
        val out = FloatArray(4)
        assertFalse(h.sampleAt(-1 * ms, out))
        assertFalse(h.sampleAt(400 * ms, out))
        assertFalse(OrientationHistory().sampleAt(0, out))
    }

    @Test
    fun `ring buffer keeps only the newest samples`() {
        val h = OrientationHistory(capacity = 8)
        for (i in 0..20) h.add(i * ms, yaw(i.toFloat()))
        val out = FloatArray(4)
        assertFalse(h.sampleAt(5 * ms, out))
        assertTrue(h.sampleAt(15 * ms, out))
        assertEquals(15f, QuatMath.angleDegrees(out), 1e-3f)
    }

    @Test
    fun `out-of-order samples are dropped`() {
        val h = OrientationHistory(capacity = 8)
        h.add(10 * ms, yaw(1f))
        h.add(5 * ms, yaw(50f))
        assertEquals(1, h.size)
    }

    @Test
    fun `a head turn after capture moves the image the other way in view space`() {
        val capture = yaw(0f)
        val now = yaw(10f) // turned left (counter-clockwise seen from above) by 10 degrees
        val c = FloatArray(4)
        ViewFrame.deviceToView(Surface.ROTATION_0, c)
        val out = FloatArray(4)
        ViewFrame.captureToNowInView(capture, now, c, FloatArray(4), FloatArray(4), out)
        val v = FloatArray(3)
        // What was straight ahead at capture is now 10 degrees to the right.
        QuatMath.rotate(out, 0f, 0f, -1f, v)
        assertTrue(v[0] > 0f)
        assertEquals(10f, QuatMath.angleDegrees(out), 1e-3f)
    }

    @Test
    fun `landscape maps device axes onto screen axes`() {
        val c = FloatArray(4)
        val v = FloatArray(3)
        ViewFrame.deviceToView(Surface.ROTATION_90, c)
        // Device x (portrait right) points up on a ROTATION_90 landscape screen.
        QuatMath.rotate(c, 1f, 0f, 0f, v)
        assertEquals(0f, v[0], 1e-6f)
        assertEquals(1f, v[1], 1e-6f)
        // Device y (portrait up) points left.
        QuatMath.rotate(c, 0f, 1f, 0f, v)
        assertEquals(-1f, v[0], 1e-6f)
    }
}
