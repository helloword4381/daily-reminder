package com.dailyreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF43A047),
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFE53935),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF1B5E20),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E2E),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF601410),
)

@Composable
fun DailyReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}