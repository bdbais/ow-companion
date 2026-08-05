package com.bellizia.owcompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Overwatch's own palette: the signature amber-orange against deep navy, with the cyan the
// game uses for interface accents. Kept deliberately close to what players already
// associate with the game, without lifting any Blizzard asset.
val OwOrange = Color(0xFFF99E1A)
val OwOrangeDeep = Color(0xFFE0761B)
val OwCyan = Color(0xFF43C0E0)
val OwNavy = Color(0xFF0B0F17)
val OwNavySurface = Color(0xFF151B26)
val OwNavyRaised = Color(0xFF1E2634)
val OwSteel = Color(0xFF9BA7B8)
val OwOffWhite = Color(0xFFF2F4F7)

private val DarkColors = darkColorScheme(
    primary = OwOrange,
    onPrimary = OwNavy,
    primaryContainer = OwOrangeDeep,
    onPrimaryContainer = OwNavy,
    secondary = OwCyan,
    onSecondary = OwNavy,
    tertiary = OwOrangeDeep,
    background = OwNavy,
    onBackground = OwOffWhite,
    surface = OwNavySurface,
    onSurface = OwOffWhite,
    surfaceVariant = OwNavyRaised,
    onSurfaceVariant = OwSteel,
    outline = Color(0xFF3A4557),
)

private val LightColors = lightColorScheme(
    primary = OwOrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0BC),
    onPrimaryContainer = Color(0xFF3A1F00),
    secondary = Color(0xFF1D7FA0),
    onSecondary = Color.White,
    tertiary = OwOrange,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF14181F),
    surface = Color.White,
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE7EAEF),
    onSurfaceVariant = Color(0xFF515C6B),
    outline = Color(0xFFB4BCC8),
)

@Composable
fun OwCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = OwTypography,
        content = content,
    )
}
