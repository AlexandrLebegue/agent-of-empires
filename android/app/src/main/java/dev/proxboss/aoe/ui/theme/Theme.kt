package dev.proxboss.aoe.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AoEPalette {
    val Cyan = Color(0xFF67E8F9)
    val Violet = Color(0xFFA78BFA)
    val Amber = Color(0xFFFACC15)
    val Green = Color(0xFF34D399)
    val Rose = Color(0xFFFB7185)
    val Ink = Color(0xFF050914)
    val Panel = Color(0xFF0B1324)
    val PanelHigh = Color(0xFF131F36)
    val Border = Color(0xFF233452)
    val TextMuted = Color(0xFF8FA3C4)
}

private val DarkColors = darkColorScheme(
    primary = AoEPalette.Cyan,
    onPrimary = Color(0xFF04222A),
    primaryContainer = Color(0xFF0E3D4A),
    onPrimaryContainer = Color(0xFFC8F7FF),
    secondary = AoEPalette.Violet,
    onSecondary = Color(0xFF21133E),
    secondaryContainer = Color(0xFF34245A),
    onSecondaryContainer = Color(0xFFE3D8FF),
    tertiary = AoEPalette.Amber,
    onTertiary = Color(0xFF2D2300),
    tertiaryContainer = Color(0xFF4A3A00),
    onTertiaryContainer = Color(0xFFFFE9A8),
    background = AoEPalette.Ink,
    surface = AoEPalette.Panel,
    surfaceVariant = AoEPalette.PanelHigh,
    onSurface = Color(0xFFE8F1FF),
    onSurfaceVariant = AoEPalette.TextMuted,
    outline = AoEPalette.Border,
    outlineVariant = Color(0xFF17243B),
    error = AoEPalette.Rose,
    onError = Color(0xFF3A0710),
    errorContainer = Color(0xFF3D1420),
    onErrorContainer = Color(0xFFFFD6DC),
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
fun SpaceBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF040710),
                        Color(0xFF071022),
                        Color(0xFF050914),
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(AoEPalette.Violet.copy(alpha = 0.20f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        listOf(AoEPalette.Cyan.copy(alpha = 0.13f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        content()
    }
}

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
