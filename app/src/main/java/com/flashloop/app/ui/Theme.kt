package com.flashloop.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    background = Color(0xFFFAFAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEEF5)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    onPrimary = Color(0xFF1A1F4D),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121214),
    surface = Color(0xFF1C1C20),
    surfaceVariant = Color(0xFF2A2B33)
)

@Composable
fun FlashLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
