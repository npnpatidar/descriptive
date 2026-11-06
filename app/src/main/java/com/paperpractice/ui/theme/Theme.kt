package com.paperpractice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

enum class ThemeMode(
    val label: String,
    val description: String
) {
    SYSTEM(
        label = "System default",
        description = "Follow your device's light or dark setting"
    ),
    LIGHT(
        label = "Light",
        description = "Always use the light theme"
    ),
    DARK(
        label = "Dark",
        description = "Always use the dark theme"
    );

    fun resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Applies the user's mode (system/light/dark) and colour theme.
 *
 * [themePalette] is a key from [AppTheme] or an imported [CustomTheme]. A key
 * that no longer resolves — a deleted imported theme restored from a backup,
 * say — renders the original Paper palette rather than leaving the screen
 * unstyled.
 */
@Composable
internal fun PaperPracticeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePalette: String = AppTheme.PAPER.key,
    customThemes: List<CustomTheme> = emptyList(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = resolveScheme(themePalette, customThemes, darkTheme),
        content = content
    )
}
