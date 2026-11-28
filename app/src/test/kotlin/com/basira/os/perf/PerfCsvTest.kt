package com.basira.os.perf

import com.basira.os.core.common.quality.QualityLevel
import com.basira.os.core.render.FrameStats
import com.basira.os.core.thermal.ThermalSnapshot
import com.basira.os.core.thermal.ThermalStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PerfCsvTest {

    @Test
    fun `row matches the header column count`() {
        val row = PerfCsv.row(
            tSeconds = 12,
            stats = FrameStats(59.94f, 16.68f, 17.9f, 3, 700, 48.25f, true, reprojectionDegrees = 1.234f),
            thermal = ThermalSnapshot(ThermalStatus.LIGHT, 0.4567f, 0L, batteryTempC = 33.4f),
            quality = QualityLevel.WARM,
        )
        assertEquals("12,59.9,16.68,17.90,3,700,48.3,true,LIGHT,0.457,33.4,WARM,1.23,NONE,", row)
        assertEquals(PerfCsv.HEADER.split(",").size, row.split(",").size)
    }

    @Test
    fun `missing readings leave empty cells`() {
        val row = PerfCsv.row(0, FrameStats(), null, QualityLevel.NORMAL)
        assertEquals("0,0.0,0.00,0.00,0,0,,false,,,,NORMAL,,NONE,", row)
    }
}
