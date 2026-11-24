package com.basira.os.core.render.layout

import com.basira.os.core.common.viewer.ViewerProfile
import com.basira.os.core.render.distortion.DistortionModel
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.tan

/** Physical screen: size in pixels (landscape) and density in pixels per inch. */
data class ScreenSpec(val widthPx: Int, val heightPx: Int, val xdpi: Float, val ydpi: Float) {
    val pxPerMeterX: Float get() = xdpi / METERS_PER_INCH
    val pxPerMeterY: Float get() = ydpi / METERS_PER_INCH

    private companion object {
        const val METERS_PER_INCH = 0.0254f
    }
}

/** Tan-angle rectangle. left and bottom are negative for a lens centred inside it. */
data class TanBounds(val left: Float, val right: Float, val bottom: Float, val top: Float) {
    val width: Float get() = right - left
    val height: Float get() = top - bottom
}

enum class Eye { LEFT, RIGHT }

/**
 * Where one eye lives on the screen.
 * Viewport is in GL window coordinates (origin bottom-left).
 * [screenTan]: tan-angles of the viewport edges as seen from the lens centre, before the lens.
 * [eyeFov]: what the eye actually sees through the lens, i.e. the render frustum.
 */
data class EyeGeometry(
    val eye: Eye,
    val viewportX: Int,
    val viewportY: Int,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val lensCenterXPx: Float,
    val lensCenterYPx: Float,
    val screenTan: TanBounds,
    val eyeFov: TanBounds,
)

object EyeLayout {

    fun compute(screen: ScreenSpec, profile: ViewerProfile): Pair<EyeGeometry, EyeGeometry> =
        eye(Eye.LEFT, screen, profile) to eye(Eye.RIGHT, screen, profile)

    private fun eye(eye: Eye, screen: ScreenSpec, profile: ViewerProfile): EyeGeometry {
        val halfWidth = screen.widthPx / 2
        val viewportX = if (eye == Eye.LEFT) 0 else halfWidth
        val viewportWidth = if (eye == Eye.LEFT) halfWidth else screen.widthPx - halfWidth
        val viewportHeight = screen.heightPx

        val halfIpdPx = profile.interLensDistanceMm / 1000f / 2f * screen.pxPerMeterX
        // Lens centre relative to this eye's viewport.
        val lensX = if (eye == Eye.LEFT) halfWidth - halfIpdPx else halfIpdPx
        val lensY = (profile.trayToLensCenterMm / 1000f * screen.pxPerMeterY).coerceIn(0f, viewportHeight.toFloat())

        val metersToLens = profile.screenToLensMm / 1000f
        val toTanX = 1f / (screen.pxPerMeterX * metersToLens)
        val toTanY = 1f / (screen.pxPerMeterY * metersToLens)
        val screenTan = TanBounds(
            left = (0f - lensX) * toTanX,
            right = (viewportWidth - lensX) * toTanX,
            bottom = (0f - lensY) * toTanY,
            top = (viewportHeight - lensY) * toTanY,
        )

        // Visible field = the smaller of what the lens allows and what the screen edge allows (after the lens).
        val model = DistortionModel(profile.k1, profile.k2)
        val maxTan = tan(profile.maxFovDeg * PI.toFloat() / 180f)
        fun limit(screenEdge: Float): Float {
            val seen = model.distort(kotlin.math.abs(screenEdge))
            return min(seen, maxTan) * kotlin.math.sign(screenEdge)
        }
        val eyeFov = TanBounds(
            left = limit(screenTan.left),
            right = limit(screenTan.right),
            bottom = limit(screenTan.bottom),
            top = limit(screenTan.top),
        )

        return EyeGeometry(
            eye = eye,
            viewportX = viewportX,
            viewportY = 0,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            lensCenterXPx = lensX,
            lensCenterYPx = lensY,
            screenTan = screenTan,
            eyeFov = eyeFov,
        )
    }
}
