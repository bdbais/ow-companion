package com.bellizia.owcompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The chart is read against a dark backdrop in the original tool; dark is the primary target.
private val Orange = Color(0xFFF58A2E)
private val Cyan = Color(0xFF4CC4E8)
private val Lime = Color(0xFF8FD14F)

private val DarkColors = darkColorScheme(
    primary = Orange,
    secondary = Cyan,
    tertiary = Lime,
    background = Color(0xFF14171F),
    surface = Color(0xFF1B1F2A),
)

private val LightColors = lightColorScheme(
    primary = Orange,
    secondary = Cyan,
    tertiary = Lime,
)

@Composable
fun OwCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
