package com.basira.os.core.thermal

import com.basira.os.core.common.quality.QualityLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalPolicyTest {

    private fun snap(status: ThermalStatus, headroom: Float? = null) =
        ThermalSnapshot(status, headroom, timestampMs = 0L)

    @Test
    fun `cool device without headroom stays normal`() {
        assertEquals(QualityLevel.NORMAL, ThermalPolicy.qualityFor(snap(ThermalStatus.NONE)))
        assertEquals(QualityLevel.NORMAL, ThermalPolicy.qualityFor(snap(ThermalStatus.LIGHT)))
    }

    @Test
    fun `platform status maps to levels`() {
        assertEquals(QualityLevel.WARM, ThermalPolicy.qualityFor(snap(ThermalStatus.MODERATE)))
        assertEquals(QualityLevel.HOT, ThermalPolicy.qualityFor(snap(ThermalStatus.SEVERE)))
        assertEquals(QualityLevel.HOT, ThermalPolicy.qualityFor(snap(ThermalStatus.CRITICAL)))
    }

    @Test
    fun `headroom degrades before the platform status does`() {
        assertEquals(QualityLevel.WARM, ThermalPolicy.qualityFor(snap(ThermalStatus.NONE, 0.85f)))
        assertEquals(QualityLevel.HOT, ThermalPolicy.qualityFor(snap(ThermalStatus.LIGHT, 0.97f)))
    }

    @Test
    fun `the hotter signal wins`() {
        assertEquals(QualityLevel.HOT, ThermalPolicy.qualityFor(snap(ThermalStatus.SEVERE, 0.2f)))
    }

    @Test
    fun `hot level drops render to 45 fps and disables hands`() {
        assertEquals(45, QualityLevel.HOT.renderFps)
        assertEquals(0, QualityLevel.HOT.handTrackingFps)
    }
}
