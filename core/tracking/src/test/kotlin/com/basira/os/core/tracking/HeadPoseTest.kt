package com.basira.os.core.tracking

import android.view.Surface
import com.basira.os.core.common.camera.HeadTracking
import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.tracking.orientation.ViewFrame
import com.basira.os.core.tracking.pose.HeadPoseFilter
import com.basira.os.core.tracking.pose.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadPoseTest {

    private val ms = 1_000_000L
    private val c = FloatArray(4).also { ViewFrame.deviceToView(Surface.ROTATION_0, it) }

    private fun yaw(deg: Float) = FloatArray(4).also {
        QuatMath.fromAxisAngle(0f, 1f, 0f, Math.toRadians(deg.toDouble()).toFloat(), it)
    }

    private fun pose(x: Float, y: Float, z: Float, yawDeg: Float) =
        FloatArray(7).also { Mat4.setPose(it, x, y, z, yaw(yawDeg)) }

    private fun yawOf(pose: FloatArray): Float {
        val fwd = FloatArray(3)
        QuatMath.rotate(floatArrayOf(pose[3], pose[4], pose[5], pose[6]), 0f, 0f, -1f, fwd)
        return Math.toDegrees(Math.atan2(-fwd[0].toDouble(), -fwd[2].toDouble())).toFloat()
    }

    @Test
    fun `tracking advances the capture rotation by the sensor`() {
        val f = HeadPoseFilter()
        val out = FloatArray(7)
        // Head turned left 5 degrees since capture: capture -> now = yaw(-5) in view space.
        val state = f.update(true, pose(1f, 1.6f, 0f, 30f), yaw(-5f), yaw(0f), c, 0, out)
        assertEquals(HeadTracking.TRACKING, state)
        assertEquals(1f, out[0], 1e-6f)
        assertEquals(35f, yawOf(out), 1e-3f)
    }

    @Test
    fun `no pose before the first tracked frame`() {
        assertEquals(HeadTracking.NONE, HeadPoseFilter().update(false, FloatArray(7), null, null, c, 0, FloatArray(7)))
    }

    @Test
    fun `holding keeps the position and keeps turning with the sensor`() {
        val f = HeadPoseFilter()
        val out = FloatArray(7)
        f.update(true, pose(2f, 1.6f, -1f, 0f), null, yaw(0f), c, 0, out)
        val state = f.update(false, FloatArray(7), null, yaw(20f), c, 100 * ms, out)
        assertEquals(HeadTracking.HOLDING, state)
        assertEquals(2f, out[0], 1e-6f)
        assertEquals(-1f, out[2], 1e-6f)
        assertEquals(20f, yawOf(out), 1e-3f)
    }

    @Test
    fun `recovery fades the jump instead of snapping`() {
        val f = HeadPoseFilter(recoveryNs = 300 * ms)
        val out = FloatArray(7)
        f.update(true, pose(0f, 1.6f, 0f, 0f), null, yaw(0f), c, 0, out)
        f.update(false, FloatArray(7), null, yaw(0f), c, 50 * ms, out) // held at x = 0
        // Tracking returns 0.3 m to the right.
        f.update(true, pose(0.3f, 1.6f, 0f, 0f), null, yaw(0f), c, 100 * ms, out)
        assertEquals(0f, out[0], 1e-5f) // starts exactly where we were
        f.update(true, pose(0.3f, 1.6f, 0f, 0f), null, yaw(0f), c, 250 * ms, out)
        assertEquals(0.15f, out[0], 1e-5f) // half way
        f.update(true, pose(0.3f, 1.6f, 0f, 0f), null, yaw(0f), c, 400 * ms, out)
        assertEquals(0.3f, out[0], 1e-5f) // done
    }

    @Test
    fun `placement is ahead at eye height and faces the viewer`() {
        val out = FloatArray(7)
        // Looking 40 degrees down while turned 90 degrees left: still ahead, level, upright.
        val q = FloatArray(4)
        val pitch = FloatArray(4)
        QuatMath.fromAxisAngle(1f, 0f, 0f, Math.toRadians(-40.0).toFloat(), pitch)
        QuatMath.multiply(yaw(90f), pitch, q)
        val view = FloatArray(7).also { Mat4.setPose(it, 1f, 1.7f, 2f, q) }
        Placement.inFront(view, 1.5f, out)
        assertEquals(1f - 1.5f, out[0], 1e-5f)
        assertEquals(1.7f, out[1], 1e-6f)
        assertEquals(2f, out[2], 1e-5f)
        // Panel normal (+z) points back towards the viewer (+x here).
        val normal = FloatArray(3)
        QuatMath.rotate(floatArrayOf(out[3], out[4], out[5], out[6]), 0f, 0f, 1f, normal)
        assertEquals(1f, normal[0], 1e-5f)
        assertEquals(0f, normal[1], 1e-5f)
    }
}
