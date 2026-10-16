package com.example.drivelog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Always light, never dynamic: pure white background, jet black text and borders. A fixed
 * palette guarantees the contrast regardless of wallpaper or system theme.
 */
private val HighContrastScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Ink,
    onSecondary = Paper,
    tertiary = Ink,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Ink,
    outline = Ink,
    outlineVariant = Ink,
    error = AlertRed,
    onError = Paper,
)

@Composable
fun DrivelogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastScheme,
        typography = Typography,
        content = content,
    )
}
