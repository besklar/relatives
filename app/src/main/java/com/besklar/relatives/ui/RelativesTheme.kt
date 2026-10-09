package com.besklar.relatives.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightPalette = lightColorScheme(
    primary = Color(0xFF66508D), onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF), onPrimaryContainer = Color(0xFF281444),
    secondary = Color(0xFF665C75), secondaryContainer = Color(0xFFE8DFF1),
    onSecondaryContainer = Color(0xFF30273D),
    background = Color(0xFFFAF6FF), onBackground = Color(0xFF211D27),
    surface = Color(0xFFFAF6FF), onSurface = Color(0xFF211D27),
    surfaceContainerLow = Color(0xFFF2ECF7), surfaceContainer = Color(0xFFEEE7F3),
    surfaceContainerHigh = Color(0xFFE8E0EE),
    onSurfaceVariant = Color(0xFF62596C), outlineVariant = Color(0xFFD3C9DD),
)
private val DarkPalette = darkColorScheme(
    primary = Color(0xFFD4BCF8), onPrimary = Color(0xFF382550),
    primaryContainer = Color(0xFF503B6B), onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCEC0DD), secondaryContainer = Color(0xFF493E56),
    onSecondaryContainer = Color(0xFFE8DFF1),
    background = Color(0xFF17131D), onBackground = Color(0xFFECE4F1),
    surface = Color(0xFF17131D), onSurface = Color(0xFFECE4F1),
    surfaceContainerLow = Color(0xFF211C28), surfaceContainer = Color(0xFF282231),
    surfaceContainerHigh = Color(0xFF302939),
    onSurfaceVariant = Color(0xFFC9BDD2), outlineVariant = Color(0xFF4D4259),
)

@Composable
fun RelativesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkPalette else LightPalette, content = content)
}
