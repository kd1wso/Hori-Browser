package dev.hori.browser.browser

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HoriBlue = Color(0xFF1B6EF3)
private val HoriBlueDark = Color(0xFF9CC2FF)

private val Light = lightColorScheme(
    primary = HoriBlue,
    secondary = Color(0xFF4A6285),
    surface = Color(0xFFFBFCFF),
    surfaceVariant = Color(0xFFEEF2FA),
)

private val Dark = darkColorScheme(
    primary = HoriBlueDark,
    secondary = Color(0xFFAFC3E3),
    surface = Color(0xFF12151B),
    surfaceVariant = Color(0xFF232833),
)

@Composable
fun HoriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}