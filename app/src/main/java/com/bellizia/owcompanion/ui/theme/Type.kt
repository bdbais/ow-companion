package com.bellizia.owcompanion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bellizia.owcompanion.R

// Overwatch's own faces (Big Noodle Titling, Koverwatch) are Blizzard's and cannot be
// shipped here. Barlow Condensed carries the same tall, narrow, slightly industrial
// character for headings, and plain Barlow keeps numbers legible in the dense chart rows.
// Both are Open Font Licence.
private val Condensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val Body = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

val OwTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
        displayMedium = displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
        displaySmall = displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Body),
        bodyMedium = bodyMedium.copy(fontFamily = Body),
        bodySmall = bodySmall.copy(fontFamily = Body),
        labelLarge = labelLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.Normal),
    )
}

/** Big numeric readouts - ranks, dps figures - want the condensed face at size. */
val StatNumber = TextStyle(
    fontFamily = Condensed,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
)
