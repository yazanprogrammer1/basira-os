package com.basira.os.core.tracking.pose

import com.basira.os.core.common.camera.HeadTracking
import com.basira.os.core.common.math.Mat4
import com.basira.os.core.common.math.QuatMath
import com.basira.os.core.tracking.orientation.ViewFrame

/**
 * world_from_view at display time, from three sources (pure logic, GL thread, allocation-free):
 * - TRACKING: ARCore position at capture; rotation = ARCore rotation at capture, advanced to display
 *   time by the sensor (the same correction the camera image gets, so windows and room move together).
 * - HOLDING: ARCore lost tracking. Keep the last good position, keep turning with the sensor,
 *   for at most [maxHoldNs]; then NONE until ARCore tracks again.
 * - Recovery: when ARCore returns, its pose can differ from what we held (we did not follow your
 *   steps). Instead of jumping, the difference fades out over [recoveryNs].
 *
 * Poses are packed (tx, ty, tz, qx, qy, qz, qw).
 */
class HeadPoseFilter(
    private val recoveryNs: Long = DEFAULT_RECOVERY_NS,
    private val maxHoldNs: Long = DEFAULT_MAX_HOLD_NS,
) {

    private val lastGood = FloatArray(Mat4.POSE_SIZE)
    private val lastGoodSensor = FloatArray(QuatMath.SIZE)
    private var hasLastGood = false
    private var hasLastGoodSensor = false
    private var holding = false
    private var holdStartNs = Long.MIN_VALUE

    /** The pose produced on the last HOLDING frame: where the recovery fade starts from. */
    private val lastHeld = FloatArray(Mat4.POSE_SIZE)

    private val correctionRot = FloatArray(QuatMath.SIZE)
    private val correctionPos = FloatArray(3)
    private var recoveryStartNs = Long.MIN_VALUE

    private val q = FloatArray(QuatMath.SIZE)
    private val r = FloatArray(QuatMath.SIZE)
    private val s1 = FloatArray(QuatMath.SIZE)
    private val s2 = FloatArray(QuatMath.SIZE)
    private val delta = FloatArray(QuatMath.SIZE)
    private val identity = floatArrayOf(0f, 0f, 0f, 1f)

    fun reset() {
        hasLastGood = false
        hasLastGoodSensor = false
        holding = false
        recoveryStartNs = Long.MIN_VALUE
    }

    /**
     * @param capturePose ARCore world_from_view at capture (valid when [tracking]).
     * @param captureToNow view-frame rotation capture -> display, or null when unavailable.
     * @param sensorNow device -> world orientation at display time, or null when unavailable.
     */
    fun update(
        tracking: Boolean,
        capturePose: FloatArray,
        captureToNow: FloatArray?,
        sensorNow: FloatArray?,
        deviceToView: FloatArray,
        nowNs: Long,
        out: FloatArray,
    ): HeadTracking {
        if (tracking) {
            // R_now = R_capture * (capture -> now)^-1, expressed in the view frame.
            loadRotation(capturePose, r)
            if (captureToNow != null) {
                QuatMath.conjugate(captureToNow, s1)
                QuatMath.multiply(r, s1, q)
            } else {
                QuatMath.copy(r, q)
            }
            Mat4.setPose(out, capturePose[0], capturePose[1], capturePose[2], q)

            if (holding) startRecovery(out, nowNs)
            holding = false
            applyRecovery(out, nowNs)

            out.copyInto(lastGood, 0, 0, Mat4.POSE_SIZE)
            hasLastGood = true
            if (sensorNow != null) QuatMath.copy(sensorNow, lastGoodSensor)
            hasLastGoodSensor = sensorNow != null
            return HeadTracking.TRACKING
        }

        if (!hasLastGood) return HeadTracking.NONE
        if (!holding) holdStartNs = nowNs
        if (nowNs - holdStartNs > maxHoldNs) {
            // Holding only bridges short gaps. Past that, the frozen position is a lie (the user walks on):
            // report no pose, so windows hide until ARCore relocalises and anchors put them back.
            holding = false
            hasLastGood = false
            recoveryStartNs = Long.MIN_VALUE
            return HeadTracking.NONE
        }
        holding = true
        recoveryStartNs = Long.MIN_VALUE
        loadRotation(lastGood, r)
        if (sensorNow != null && hasLastGoodSensor) {
            ViewFrame.captureToNowInView(lastGoodSensor, sensorNow, deviceToView, s1, s2, delta)
            QuatMath.conjugate(delta, s1)
            QuatMath.multiply(r, s1, q)
        } else {
            QuatMath.copy(r, q)
        }
        Mat4.setPose(out, lastGood[0], lastGood[1], lastGood[2], q)
        out.copyInto(lastHeld, 0, 0, Mat4.POSE_SIZE)
        return HeadTracking.HOLDING
    }

    /** Remember held - tracked, so it can be faded out. [tracked] is in [out] (pre-recovery). */
    private fun startRecovery(tracked: FloatArray, nowNs: Long) {
        // Rotation correction in the world frame: held * tracked^-1.
        loadRotation(lastHeld, s1)
        loadRotation(tracked, s2)
        QuatMath.conjugate(s2, s2)
        QuatMath.multiply(s1, s2, correctionRot)
        correctionPos[0] = lastHeld[0] - tracked[0]
        correctionPos[1] = lastHeld[1] - tracked[1]
        correctionPos[2] = lastHeld[2] - tracked[2]
        recoveryStartNs = nowNs
    }

    private fun applyRecovery(out: FloatArray, nowNs: Long) {
        if (recoveryStartNs == Long.MIN_VALUE) return
        val t = (nowNs - recoveryStartNs).toFloat() / recoveryNs
        if (t >= 1f) {
            recoveryStartNs = Long.MIN_VALUE
            return
        }
        val remaining = 1f - t.coerceAtLeast(0f)
        QuatMath.slerp(identity, correctionRot, remaining, s1)
        loadRotation(out, s2)
        QuatMath.multiply(s1, s2, q)
        Mat4.setPose(
            out,
            out[0] + correctionPos[0] * remaining,
            out[1] + correctionPos[1] * remaining,
            out[2] + correctionPos[2] * remaining,
            q,
        )
    }

    private fun loadRotation(pose: FloatArray, outQ: FloatArray) {
        outQ[0] = pose[3]; outQ[1] = pose[4]; outQ[2] = pose[5]; outQ[3] = pose[6]
    }

    companion object {
        const val DEFAULT_RECOVERY_NS = 300_000_000L
        const val DEFAULT_MAX_HOLD_NS = 3_000_000_000L
    }
}
