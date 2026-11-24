package com.basira.os.ui.system.theme

import androidx.compose.ui.graphics.Color

/** Design tokens from docs/DESIGN.md. */
object BasiraColors {
    val Emerald = Color(0xFF10B981)
    val Mint = Color(0xFF6EE7B7)
    val Night = Color(0xFF0B0F0D)
    val GlassSolid = Color(0xFF141A17)

    /** rgba(14,18,16,0.78) */
    val Glass = Color(red = 14, green = 18, blue = 16, alpha = 199)

    /** rgba(255,255,255,0.12) */
    val GlassBorder = Color(red = 255, green = 255, blue = 255, alpha = 31)

    val Text = Color(0xFFF4F7F5)
    val TextMuted = Color(0xFFB8C4BE)
    val TextSubtle = Color(0xFF9FB0A7)
    val Warning = Color(0xFFFBBF24)
    val Live = Color(0xFFF87171)

    /** Emerald tint 14% for the page-understanding chip. */
    val EmeraldTint = Emerald.copy(alpha = 0.14f)
    val EmeraldBorder = Emerald.copy(alpha = 0.35f)
}
