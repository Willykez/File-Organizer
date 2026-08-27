package com.willykez.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OnPrimaryDark = Color(0xFF0A1F17)

private val FileOrganizerColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimaryDark,
    secondary = Aurora2,
    tertiary = Accent,
    background = BgSpace,
    surface = BgSpace,
    surfaceVariant = Glass,
    onBackground = TextMain,
    onSurface = TextMain,
    error = ErrorRed
)

@Composable
fun FileOrganizerTheme(
    // The app is dark-mode-first by design (glass/aurora aesthetic); kept as a parameter rather
    // than hardcoded so a future light theme can be added without touching call sites.
    darkTheme: Boolean = isSystemInDarkTheme() || true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FileOrganizerColorScheme,
        typography = FileOrganizerTypography,
        content = content
    )
}
