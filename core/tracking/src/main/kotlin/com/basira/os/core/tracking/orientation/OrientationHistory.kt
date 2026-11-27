package com.basira.os.core.tracking.orientation

import com.basira.os.core.common.math.QuatMath

/**
 * Ring buffer of timestamped device orientations (device -> world quaternions).
 * Not thread-safe: [OrientationTracker] guards it. Allocation-free after construction.
 */
class OrientationHistory(private val capacity: Int = 512) {

    private val times = LongArray(capacity)
    private val quats = FloatArray(capacity * QuatMath.SIZE)
    private var count = 0
    private var newest = -1

    private val a = FloatArray(QuatMath.SIZE)
    private val b = FloatArray(QuatMath.SIZE)
    private val delta = FloatArray(QuatMath.SIZE)
    private val inverse = FloatArray(QuatMath.SIZE)

    val size: Int get() = count

    val newestTimestampNs: Long get() = if (count == 0) Long.MIN_VALUE else times[newest]

    fun clear() {
        count = 0
        newest = -1
    }

    fun add(timestampNs: Long, x: Float, y: Float, z: Float, w: Float) {
        if (count > 0 && timestampNs <= times[newest]) return // out of order or duplicate
        newest = (newest + 1) % capacity
        times[newest] = timestampNs
        val o = newest * QuatMath.SIZE
        quats[o] = x; quats[o + 1] = y; quats[o + 2] = z; quats[o + 3] = w
        if (count < capacity) count++
    }

    /**
     * Orientation at [timestampNs]: interpolated between the samples around it, or extrapolated
     * (at most [maxExtrapolationNs]) past the newest one. False when the time is not covered.
     */
    fun sampleAt(timestampNs: Long, out: FloatArray, maxExtrapolationNs: Long = DEFAULT_MAX_EXTRAPOLATION_NS): Boolean {
        if (count == 0) return false
        val newestTime = times[newest]
        if (timestampNs >= newestTime) {
            return extrapolate(timestampNs - newestTime, maxExtrapolationNs, out)
        }
        // Walk back from the newest sample (the camera image is usually < 150 ms old).
        var later = newest
        for (step in 1 until count) {
            val earlier = (newest - step + capacity) % capacity
            val tEarlier = times[earlier]
            if (tEarlier <= timestampNs) {
                val tLater = times[later]
                val t = (timestampNs - tEarlier).toFloat() / (tLater - tEarlier).toFloat()
                load(earlier, a)
                load(later, b)
                QuatMath.slerp(a, b, t, out)
                return true
            }
            later = earlier
        }
        return false // older than the whole buffer
    }

    /** The newest orientation predicted [aheadNs] into the future. */
    fun predicted(aheadNs: Long, out: FloatArray, maxExtrapolationNs: Long = DEFAULT_MAX_EXTRAPOLATION_NS): Boolean =
        count > 0 && extrapolate(aheadNs, maxExtrapolationNs, out)

    private fun extrapolate(aheadNs: Long, maxNs: Long, out: FloatArray): Boolean {
        load(newest, b)
        if (aheadNs <= 0L || count < 2) {
            QuatMath.copy(b, out)
            return true
        }
        if (aheadNs > maxNs) return false
        val previous = (newest - 1 + capacity) % capacity
        val dt = times[newest] - times[previous]
        if (dt <= 0L) {
            QuatMath.copy(b, out)
            return true
        }
        load(previous, a)
        // Angular velocity in the device frame: delta = a^-1 * b, applied (aheadNs / dt) more times.
        QuatMath.conjugate(a, inverse)
        QuatMath.multiply(inverse, b, delta)
        QuatMath.scaleAngle(delta, aheadNs.toFloat() / dt.toFloat(), delta)
        QuatMath.multiply(b, delta, out)
        return true
    }

    private fun load(index: Int, out: FloatArray) {
        quats.copyInto(out, 0, index * QuatMath.SIZE, index * QuatMath.SIZE + QuatMath.SIZE)
    }

    companion object {
        const val DEFAULT_MAX_EXTRAPOLATION_NS = 50_000_000L
    }
}
