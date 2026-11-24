package com.basira.os.ui.system.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val BasiraColorScheme = darkColorScheme(
    primary = BasiraColors.Emerald,
    onPrimary = BasiraColors.Night,
    secondary = BasiraColors.Mint,
    onSecondary = BasiraColors.Night,
    background = BasiraColors.Night,
    onBackground = BasiraColors.Text,
    surface = BasiraColors.GlassSolid,
    onSurface = BasiraColors.Text,
    surfaceVariant = BasiraColors.Glass,
    onSurfaceVariant = BasiraColors.TextMuted,
    outline = BasiraColors.GlassBorder,
    error = BasiraColors.Live,
    tertiary = BasiraColors.Warning,
)

/** Radii from docs/DESIGN.md: small panels 22, windows 26, dock 28, home grid 34. */
object BasiraShapes {
    val Panel = RoundedCornerShape(22.dp)
    val Window = RoundedCornerShape(26.dp)
    val Dock = RoundedCornerShape(28.dp)
    val HomeGrid = RoundedCornerShape(34.dp)
    val DockButton = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Minimum gaze/touch target from CLAUDE.md. */
val MinTarget = 52.dp

/**
 * System UI is Arabic-first, so it is always RTL regardless of the device locale.
 */
@Composable
fun BasiraTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = BasiraColorScheme,
            typography = BasiraTypography,
            shapes = Shapes(
                small = BasiraShapes.Panel,
                medium = BasiraShapes.Window,
                large = BasiraShapes.HomeGrid,
            ),
            content = content,
        )
    }
}
