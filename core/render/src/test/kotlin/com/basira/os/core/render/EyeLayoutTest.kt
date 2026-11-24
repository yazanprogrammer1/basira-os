package com.basira.os.core.render

import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.layout.Eye
import com.basira.os.core.render.layout.EyeLayout
import com.basira.os.core.render.layout.ScreenSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class EyeLayoutTest {

    /** First test device: OPPO CPH2699, 2400x1080. 400 dpi keeps the arithmetic readable. */
    private val screen = ScreenSpec(widthPx = 2400, heightPx = 1080, xdpi = 400f, ydpi = 400f)
    private val profile = ViewerProfile.CARDBOARD_V2

    @Test
    fun `each eye gets half the screen`() {
        val (left, right) = EyeLayout.compute(screen, profile)
        assertEquals(Eye.LEFT, left.eye)
        assertEquals(0, left.viewportX)
        assertEquals(1200, left.viewportWidth)
        assertEquals(1200, right.viewportX)
        assertEquals(1200, right.viewportWidth)
        assertEquals(1080, right.viewportHeight)
    }

    @Test
    fun `lens centres are one IPD apart on the physical screen`() {
        val (left, right) = EyeLayout.compute(screen, profile)
        val leftAbsolute = left.viewportX + left.lensCenterXPx
        val rightAbsolute = right.viewportX + right.lensCenterXPx
        val ipdPx = 64f / 1000f * screen.pxPerMeterX
        assertEquals(ipdPx, rightAbsolute - leftAbsolute, 0.01f)
        assertEquals(1200f, (leftAbsolute + rightAbsolute) / 2f, 0.01f)
    }

    @Test
    fun `eyes are mirror images`() {
        val (left, right) = EyeLayout.compute(screen, profile)
        assertEquals(-left.screenTan.left, right.screenTan.right, 1e-5f)
        assertEquals(-left.screenTan.right, right.screenTan.left, 1e-5f)
        assertEquals(left.eyeFov.top, right.eyeFov.top, 1e-6f)
    }

    @Test
    fun `lens height comes from the tray distance`() {
        val (left, _) = EyeLayout.compute(screen, profile)
        assertEquals(35f / 1000f * screen.pxPerMeterY, left.lensCenterYPx, 0.01f)
    }

    @Test
    fun `visible field is limited by the lens fov`() {
        val wide = ScreenSpec(widthPx = 6000, heightPx = 4000, xdpi = 400f, ydpi = 400f)
        val (left, _) = EyeLayout.compute(wide, profile.copy(trayToLensCenterMm = 120f))
        val maxTan = tan(Math.toRadians(60.0)).toFloat()
        assertEquals(-maxTan, left.eyeFov.left, 1e-5f)
        assertEquals(maxTan, left.eyeFov.top, 1e-5f)
    }

    @Test
    fun `lens magnifies the screen edges`() {
        val (left, _) = EyeLayout.compute(screen, profile)
        assertTrue(left.eyeFov.right > left.screenTan.right)
        assertTrue(left.eyeFov.bottom < left.screenTan.bottom)
    }
}
