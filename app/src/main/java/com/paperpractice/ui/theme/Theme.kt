package com.paperpractice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D4D87),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF101A43),
    secondary = Color(0xFF665E3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE3BC),
    onSecondaryContainer = Color(0xFF211B02),
    tertiary = Color(0xFF7C5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFF7F5EF),
    onBackground = Color(0xFF1E2433),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1E2433),
    surfaceVariant = Color(0xFFE7E2D6),
    onSurfaceVariant = Color(0xFF4A4E59),
    outline = Color(0xFF7A766B),
    outlineVariant = Color(0xFFCAC5B9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF202C5B),
    primaryContainer = Color(0xFF344272),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFD1C89B),
    onSecondary = Color(0xFF373114),
    secondaryContainer = Color(0xFF4F482A),
    onSecondaryContainer = Color(0xFFEDE3BC),
    tertiary = Color(0xFFEDB8C8),
    onTertiary = Color(0xFF482534),
    tertiaryContainer = Color(0xFF623B4B),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF14161D),
    onBackground = Color(0xFFE9EBF1),
    surface = Color(0xFF1D2029),
    onSurface = Color(0xFFE9EBF1),
    surfaceVariant = Color(0xFF353A48),
    onSurfaceVariant = Color(0xFFC3C7D3),
    outline = Color(0xFF8D929F),
    outlineVariant = Color(0xFF444955),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun PaperPracticeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
