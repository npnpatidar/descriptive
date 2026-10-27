package com.paperpractice.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `system mode follows the device setting`() {
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = false))
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemInDarkTheme = true))
    }

    @Test
    fun `fixed modes ignore the device setting`() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemInDarkTheme = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemInDarkTheme = false))
    }

    @Test
    fun `stored values restore the matching mode`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStoredValue("unknown"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStoredValue("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStoredValue("DARK"))
    }
}
