package com.basira.os.core.thermal

import com.basira.os.core.common.quality.QualityLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class QualityHysteresisTest {

    @Test
    fun `heating up applies immediately`() {
        val h = QualityHysteresis(coolDownMs = 30_000)
        assertEquals(QualityLevel.HOT, h.update(QualityLevel.HOT, 0))
    }

    @Test
    fun `cooling down waits for a stable period`() {
        val h = QualityHysteresis(coolDownMs = 30_000)
        h.update(QualityLevel.WARM, 0)
        assertEquals(QualityLevel.WARM, h.update(QualityLevel.NORMAL, 1_000))
        assertEquals(QualityLevel.WARM, h.update(QualityLevel.NORMAL, 30_999))
        assertEquals(QualityLevel.NORMAL, h.update(QualityLevel.NORMAL, 31_000))
    }

    @Test
    fun `a hot blip restarts the cool-down timer`() {
        val h = QualityHysteresis(coolDownMs = 30_000)
        h.update(QualityLevel.WARM, 0)
        h.update(QualityLevel.NORMAL, 1_000)
        h.update(QualityLevel.WARM, 20_000)
        assertEquals(QualityLevel.WARM, h.update(QualityLevel.NORMAL, 40_000))
        assertEquals(QualityLevel.NORMAL, h.update(QualityLevel.NORMAL, 70_000))
    }

    @Test
    fun `steps down one level at a time`() {
        val h = QualityHysteresis(coolDownMs = 30_000)
        h.update(QualityLevel.HOT, 0)
        h.update(QualityLevel.NORMAL, 1_000)
        assertEquals(QualityLevel.WARM, h.update(QualityLevel.NORMAL, 31_000))
        assertEquals(QualityLevel.WARM, h.update(QualityLevel.NORMAL, 60_000))
        assertEquals(QualityLevel.NORMAL, h.update(QualityLevel.NORMAL, 61_000))
    }
}
