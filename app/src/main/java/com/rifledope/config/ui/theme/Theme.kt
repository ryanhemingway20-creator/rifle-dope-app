package com.rifledope.config.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFF2FD1C0),
    onPrimary = Color(0xFF00201C),
    primaryContainer = Color(0xFF123A36),
    onPrimaryContainer = Color(0xFFB8F5EC),
    secondary = Color(0xFFFFC24B),
    onSecondary = Color(0xFF2A1B00),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE6EAEC),
    surface = Color(0xFF161A1D),
    onSurface = Color(0xFFE6EAEC),
    surfaceVariant = Color(0xFF232A2E),
    onSurfaceVariant = Color(0xFFB8C2C6),
    error = Color(0xFFFF5A52),
    outline = Color(0xFF3A434A),
)

@Composable
fun RifleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
