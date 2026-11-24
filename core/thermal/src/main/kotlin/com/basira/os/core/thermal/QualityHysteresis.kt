package com.basira.os.core.thermal

import com.basira.os.core.common.quality.QualityLevel

/**
 * Hot fast, cool slow: a hotter level applies immediately, a cooler one only after the readings have
 * asked for it continuously for [coolDownMs]. Stops the quality from flickering around a threshold.
 */
class QualityHysteresis(private val coolDownMs: Long = DEFAULT_COOL_DOWN_MS) {

    var current: QualityLevel = QualityLevel.NORMAL
        private set

    private var coolerSinceMs: Long = NO_TIME

    fun update(target: QualityLevel, nowMs: Long): QualityLevel {
        when {
            target > current -> {
                current = target
                coolerSinceMs = NO_TIME
            }
            target < current -> {
                if (coolerSinceMs == NO_TIME) coolerSinceMs = nowMs
                if (nowMs - coolerSinceMs >= coolDownMs) {
                    // Step down one level at a time.
                    current = QualityLevel.entries[current.ordinal - 1]
                    coolerSinceMs = if (target < current) nowMs else NO_TIME
                }
            }
            else -> coolerSinceMs = NO_TIME
        }
        return current
    }

    companion object {
        const val DEFAULT_COOL_DOWN_MS = 30_000L
        private const val NO_TIME = Long.MIN_VALUE
    }
}
