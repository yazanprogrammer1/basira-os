package com.basira.os.ui.system.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.basira.os.ui.system.R

val IbmPlexSansArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int = (size * 1.5f).toInt()) = TextStyle(
    fontFamily = IbmPlexSansArabic,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

/** Sizes from docs/DESIGN.md: 30 greeting, 26 window title, 16-17 body, 12-13 captions. */
val BasiraTypography = Typography(
    displaySmall = style(30, FontWeight.Bold),
    headlineMedium = style(26, FontWeight.SemiBold),
    titleLarge = style(20, FontWeight.SemiBold),
    titleMedium = style(17, FontWeight.SemiBold),
    bodyLarge = style(17, FontWeight.Normal),
    bodyMedium = style(16, FontWeight.Normal),
    labelLarge = style(16, FontWeight.Medium),
    labelMedium = style(13, FontWeight.Medium),
    bodySmall = style(13, FontWeight.Normal),
    labelSmall = style(12, FontWeight.Medium),
)
