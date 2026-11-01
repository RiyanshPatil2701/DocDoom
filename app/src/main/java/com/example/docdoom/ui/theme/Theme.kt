package com.example.docdoom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DocDoomLightColorScheme = lightColorScheme(
    primary = DocDoomEmerald,
    onPrimary = DocDoomWhite,
    secondary = DocDoomEmerald,
    onSecondary = DocDoomWhite,
    tertiary = DocDoomMint,
    background = DocDoomCanvas,
    onBackground = DocDoomDark,
    surface = DocDoomWhite,
    onSurface = DocDoomDark,
    surfaceVariant = DocDoomMint,
    onSurfaceVariant = DocDoomMuted,
    outline = DocDoomBorder
)

private val DocDoomDarkColorScheme = darkColorScheme(
    primary = Color(0xFF75D6B8),
    onPrimary = Color(0xFF00382B),
    secondary = Color(0xFF9BDCC8),
    onSecondary = Color(0xFF123D31),
    tertiary = Color(0xFFB5DCCF),
    background = Color(0xFF101714),
    onBackground = Color(0xFFE1EAE5),
    surface = Color(0xFF18211D),
    onSurface = Color(0xFFE1EAE5),
    surfaceVariant = Color(0xFF293630),
    onSurfaceVariant = Color(0xFFB8C9C1),
    outline = Color(0xFF52645B)
)

@Composable
fun DocDoomTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DocDoomDarkColorScheme
    } else {
        DocDoomLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}