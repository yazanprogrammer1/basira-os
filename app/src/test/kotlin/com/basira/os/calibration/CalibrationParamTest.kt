package com.basira.os.calibration

import com.basira.os.core.common.viewer.ViewerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationParamTest {

    private val base = ViewerProfile.CARDBOARD_V2

    @Test
    fun `volume steps move by one step`() {
        assertEquals(64.5f, CalibrationParam.IPD.adjust(base, 1).interLensDistanceMm, 0f)
        assertEquals(0.33f, CalibrationParam.K1.adjust(base, -1).k1, 1e-6f)
    }

    @Test
    fun `repeated steps do not accumulate float error`() {
        var p = base
        repeat(37) { p = CalibrationParam.K2.adjust(p, 1) }
        assertEquals(0.92f, p.k2, 0f)
    }

    @Test
    fun `values are clamped to a sane range`() {
        assertEquals(75f, CalibrationParam.IPD.set(base, 500f).interLensDistanceMm, 0f)
        assertEquals(0f, CalibrationParam.K1.set(base, -3f).k1, 0f)
    }

    @Test
    fun `stored extreme values are pulled back into range`() {
        val wild = base.copy(interLensDistanceMm = 78f, k1 = 1.29f, k2 = 0.91f, maxFovDeg = 80f, screenToLensMm = 54.5f)
        val safe = wild.sanitized()
        assertEquals(75f, safe.interLensDistanceMm, 0f)
        assertEquals(1f, safe.k1, 0f)
        assertEquals(0.91f, safe.k2, 0f)
        assertEquals(70f, safe.maxFovDeg, 0f)
        assertEquals(54.5f, safe.screenToLensMm, 0f)
        assertEquals(base, base.sanitized())
    }

    @Test
    fun `only the selected field changes`() {
        val p = CalibrationParam.SHIFT.adjust(base, 3)
        assertEquals(6f, p.passthroughShiftPx, 0f)
        assertEquals(base.copy(passthroughShiftPx = 6f), p)
    }

    @Test
    fun `passthrough scale defaults to true size and is clamped`() {
        assertEquals(1f, base.passthroughScale, 0f)
        assertEquals(1.1f, CalibrationParam.SCALE.adjust(base, 5).passthroughScale, 1e-6f)
        assertEquals(1.6f, CalibrationParam.SCALE.set(base, 3f).passthroughScale, 0f)
    }

    @Test
    fun `presets are already inside the plausible ranges`() {
        ViewerProfile.PRESETS.forEach { (name, preset) -> assertEquals(name, preset, preset.sanitized()) }
        assertEquals(ViewerProfile.VR_SHINECON, ViewerProfile.DEFAULT)
    }

    @Test
    fun `labels are formatted with units`() {
        assertEquals("64.0 mm", CalibrationParam.IPD.format(base))
        assertEquals("0.34", CalibrationParam.K1.format(base))
    }

    @Test
    fun `next cycles through every parameter`() {
        var p = CalibrationParam.IPD
        repeat(CalibrationParam.entries.size) { p = p.next() }
        assertEquals(CalibrationParam.IPD, p)
    }
}
