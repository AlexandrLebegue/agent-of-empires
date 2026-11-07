package dev.proxboss.aoe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DD3C0),
    secondary = Color(0xFF9EC5FE),
    tertiary = Color(0xFFF2C46D),
    background = Color(0xFF0F1214),
    surface = Color(0xFF171B1E),
    surfaceVariant = Color(0xFF232A2E),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    secondary = Color(0xFF365E9E),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7ECEA),
    error = Color(0xFFB3261E),
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
