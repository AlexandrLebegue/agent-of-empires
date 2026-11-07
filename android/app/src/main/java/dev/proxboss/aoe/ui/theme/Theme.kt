package dev.proxboss.aoe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD8C5),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0D4B40),
    onPrimaryContainer = Color(0xFFB6F0E1),
    secondary = Color(0xFFA9C7FF),
    onSecondary = Color(0xFF0A2F5C),
    tertiary = Color(0xFFF4C56A),
    onTertiary = Color(0xFF3E2A00),
    tertiaryContainer = Color(0xFF5A4100),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF0C1012),
    surface = Color(0xFF13181B),
    surfaceVariant = Color(0xFF20282C),
    onSurface = Color(0xFFE4EAE8),
    onSurfaceVariant = Color(0xFFB8C4C1),
    outline = Color(0xFF4B5A58),
    outlineVariant = Color(0xFF2D3735),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5A1A16),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF116B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F2DF),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF365E9E),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF5F8F6),
    surface = Color(0xFFFBFDFC),
    surfaceVariant = Color(0xFFE3EAE7),
    onSurface = Color(0xFF171D1B),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun AoETheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
