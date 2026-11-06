package com.paperpractice.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme catalog is data, so it is tested as data: every palette must be
 * reachable, Paper must stay identical to the app's previous look, and every
 * foreground/background pair the app renders text in must clear WCAG AA in
 * both modes. The derivation can adjust a colour, so an unreadable pair here
 * means a real screen is unreadable, not just an aesthetically dim palette.
 */
class ThemeCatalogTest {

    private val expectedKeys = listOf(
        "paper", "material", "nord", "dracula", "tokyo_night", "catppuccin",
        "gruvbox", "one_dark", "monokai", "solarized", "everforest",
        "rose_pine", "ayu", "night_owl", "kanagawa", "github"
    )

    @Test
    fun `the stored keys are stable`() {
        // Keys live in SharedPreferences; renaming one silently drops an
        // imported or selected theme back to Paper.
        assertEquals(expectedKeys, AppTheme.entries.map { it.key })
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `unknown keys fall back to paper`() {
        assertEquals(AppTheme.PAPER, AppTheme.fromKey(null))
        assertEquals(AppTheme.PAPER, AppTheme.fromKey(""))
        assertEquals(AppTheme.PAPER, AppTheme.fromKey("no-such-theme"))
        assertEquals(AppTheme.PAPER, AppTheme.fromKey("custom-deleted"))
    }

    @Test
    fun `paper is still the original palette`() {
        assertSameScheme(PaperLightColors, AppTheme.PAPER.scheme(false))
        assertSameScheme(PaperDarkColors, AppTheme.PAPER.scheme(true))
    }

    @Test
    fun `material is still the stock scheme`() {
        assertSameScheme(lightColorScheme(), AppTheme.MATERIAL.scheme(false))
        assertSameScheme(darkColorScheme(), AppTheme.MATERIAL.scheme(true))
    }

    @Test
    fun `every theme has its own dark and light surface`() {
        for (theme in AppTheme.entries) {
            val light = theme.scheme(false)
            val dark = theme.scheme(true)
            assertNotEquals(
                "${theme.key}: light and dark backgrounds are identical",
                light.background,
                dark.background
            )
        }
    }

    @Test
    fun `every theme meets text contrast in both modes`() {
        for (theme in AppTheme.entries) {
            for (dark in listOf(false, true)) {
                val s = theme.scheme(dark)
                val mode = if (dark) "dark" else "light"
                fun check(name: String, foreground: Color, background: Color) {
                    val ratio = contrastRatio(foreground, background)
                    assertTrue(
                        "${theme.key} $mode: $name is only %.2f:1".format(ratio),
                        ratio >= 4.5
                    )
                }
                check("onBackground/background", s.onBackground, s.background)
                check("onSurface/surface", s.onSurface, s.surface)
                check("onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
                check("onPrimary/primary", s.onPrimary, s.primary)
                check("onSecondary/secondary", s.onSecondary, s.secondary)
                check("onTertiary/tertiary", s.onTertiary, s.tertiary)
                check("onError/error", s.onError, s.error)
                check("primary/surface", s.primary, s.surface)
                check("secondary/surface", s.secondary, s.surface)
                check("tertiary/surface", s.tertiary, s.surface)
                check("error/surface", s.error, s.surface)
                check("onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer)
                check("onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
                check("onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
                check("onErrorContainer", s.onErrorContainer, s.errorContainer)
                check("inverseOnSurface", s.inverseOnSurface, s.inverseSurface)
            }
        }
    }

    @Test
    fun `every theme has a visible outline`() {
        for (theme in AppTheme.entries) {
            for (dark in listOf(false, true)) {
                val s = theme.scheme(dark)
                val ratio = contrastRatio(s.outline, s.surface)
                assertTrue(
                    "${theme.key} ${if (dark) "dark" else "light"}: outline is only " +
                        "%.2f:1".format(ratio),
                    ratio >= 3.0
                )
            }
        }
    }

    @Test
    fun `an imported theme round-trips through the stored format`() {
        val parsed = CustomThemeCodec.parse(SAMPLE_THEME)
        val theme = CustomTheme("custom-test", parsed.name, parsed.spec)

        val decoded = CustomThemeCodec.decodeAll(CustomThemeCodec.encodeAll(listOf(theme)))

        assertEquals(1, decoded.size)
        assertEquals("custom-test", decoded[0].key)
        assertEquals("Midnight", decoded[0].name)
        // The scheme is derived, so comparing it proves every colour survived
        // the round trip, not just the name.
        assertSameScheme(theme.scheme(false), decoded[0].scheme(false))
        assertSameScheme(theme.scheme(true), decoded[0].scheme(true))
    }

    @Test
    fun `an imported theme is used when selected and paper otherwise`() {
        val parsed = CustomThemeCodec.parse(SAMPLE_THEME)
        val theme = CustomTheme("custom-test", parsed.name, parsed.spec)
        val themes = listOf(theme)

        assertEquals(
            theme.scheme(true).background,
            resolveScheme("custom-test", themes, true).background
        )
        assertEquals(
            AppTheme.PAPER.scheme(true).background,
            resolveScheme("custom-deleted", themes, true).background
        )
    }

    @Test
    fun `an imported theme meets the same contrast bar`() {
        val parsed = CustomThemeCodec.parse(SAMPLE_THEME)
        val theme = CustomTheme("custom-test", parsed.name, parsed.spec)
        for (dark in listOf(false, true)) {
            val s = theme.scheme(dark)
            assertTrue(contrastRatio(s.onSurface, s.surface) >= 4.5)
            assertTrue(contrastRatio(s.onSurfaceVariant, s.surfaceVariant) >= 4.5)
            assertTrue(contrastRatio(s.primary, s.surface) >= 4.5)
        }
    }

    @Test
    fun `imports accept optional hash and both case`() {
        val parsed = CustomThemeCodec.parse(
            SAMPLE_THEME
                .replace("\"#1a1b26\"", "\"1A1B26\"")
                .replace("\"#7aa2f7\"", "\"#7AA2F7\"")
        )
        assertEquals("Midnight", parsed.name)
    }

    @Test
    fun `malformed imports are rejected with a reason`() {
        assertThrows(InvalidThemeException::class.java) {
            CustomThemeCodec.parse("not json")
        }
        assertThrows(InvalidThemeException::class.java) {
            // Six-digit hex only: alpha belongs to the scheme, not the file.
            CustomThemeCodec.parse(SAMPLE_THEME.replace("\"#7aa2f7\"", "\"#7AA2F7FF\""))
        }
        assertThrows(InvalidThemeException::class.java) {
            CustomThemeCodec.parse(SAMPLE_THEME.replace("\"Midnight\"", "\"\""))
        }
        assertThrows(InvalidThemeException::class.java) {
            CustomThemeCodec.parse(SAMPLE_THEME.replace("\"#1a1b26\"", "\"blue\""))
        }
        assertThrows(InvalidThemeException::class.java) {
            // A missing mode section cannot fall back to a half-defined theme.
            CustomThemeCodec.parse(SAMPLE_THEME.substringBefore("\"light\"") + "}")
        }
    }

    @Test
    fun `a corrupt stored blob does not take the list down`() {
        assertEquals(emptyList<CustomTheme>(), CustomThemeCodec.decodeAll(null))
        assertEquals(emptyList<CustomTheme>(), CustomThemeCodec.decodeAll(""))
        assertEquals(emptyList<CustomTheme>(), CustomThemeCodec.decodeAll("{not json"))
        // One bad entry is skipped; a good neighbour survives.
        val good = CustomThemeCodec.encodeAll(
            listOf(CustomTheme("custom-a", "A", CustomThemeCodec.parse(SAMPLE_THEME).spec))
        )
        val mixed = good.dropLast(1) + ",{\"key\":\"broken\"}]"
        assertEquals(listOf("A"), CustomThemeCodec.decodeAll(mixed).map { it.name })
    }

    private fun assertSameScheme(expected: ColorScheme, actual: ColorScheme) {
        // ColorScheme has no structural equals, so compare every role the app
        // can render. Keeping the list complete means adding a role later
        // cannot slip past this assertion.
        val roles: (ColorScheme) -> List<Pair<String, Color>> = { s ->
            listOf(
                "primary" to s.primary,
                "onPrimary" to s.onPrimary,
                "primaryContainer" to s.primaryContainer,
                "onPrimaryContainer" to s.onPrimaryContainer,
                "inversePrimary" to s.inversePrimary,
                "secondary" to s.secondary,
                "onSecondary" to s.onSecondary,
                "secondaryContainer" to s.secondaryContainer,
                "onSecondaryContainer" to s.onSecondaryContainer,
                "tertiary" to s.tertiary,
                "onTertiary" to s.onTertiary,
                "tertiaryContainer" to s.tertiaryContainer,
                "onTertiaryContainer" to s.onTertiaryContainer,
                "background" to s.background,
                "onBackground" to s.onBackground,
                "surface" to s.surface,
                "onSurface" to s.onSurface,
                "surfaceVariant" to s.surfaceVariant,
                "onSurfaceVariant" to s.onSurfaceVariant,
                "surfaceTint" to s.surfaceTint,
                "inverseSurface" to s.inverseSurface,
                "inverseOnSurface" to s.inverseOnSurface,
                "error" to s.error,
                "onError" to s.onError,
                "errorContainer" to s.errorContainer,
                "onErrorContainer" to s.onErrorContainer,
                "outline" to s.outline,
                "outlineVariant" to s.outlineVariant,
                "scrim" to s.scrim,
                "surfaceBright" to s.surfaceBright,
                "surfaceDim" to s.surfaceDim,
                "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh,
                "surfaceContainerHighest" to s.surfaceContainerHighest,
                "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainerLowest" to s.surfaceContainerLowest,
                "primaryFixed" to s.primaryFixed,
                "primaryFixedDim" to s.primaryFixedDim,
                "onPrimaryFixed" to s.onPrimaryFixed,
                "onPrimaryFixedVariant" to s.onPrimaryFixedVariant,
                "secondaryFixed" to s.secondaryFixed,
                "secondaryFixedDim" to s.secondaryFixedDim,
                "onSecondaryFixed" to s.onSecondaryFixed,
                "onSecondaryFixedVariant" to s.onSecondaryFixedVariant,
                "tertiaryFixed" to s.tertiaryFixed,
                "tertiaryFixedDim" to s.tertiaryFixedDim,
                "onTertiaryFixed" to s.onTertiaryFixed,
                "onTertiaryFixedVariant" to s.onTertiaryFixedVariant
            )
        }
        assertEquals(roles(expected), roles(actual))
    }

    private companion object {
        val SAMPLE_THEME = """
            {
              "name": "Midnight",
              "primary": "#7aa2f7",
              "secondary": "#bb9af7",
              "tertiary": "#7dcfff",
              "error": "#f7768e",
              "light": {
                "background": "#e6e7ed",
                "surface": "#ffffff",
                "surfaceVariant": "#c4c8da",
                "onBackground": "#3760bf"
              },
              "dark": {
                "background": "#1a1b26",
                "surface": "#16161e",
                "surfaceVariant": "#292e42",
                "onBackground": "#c0caf5"
              }
            }
        """.trimIndent()
    }
}
