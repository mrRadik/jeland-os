package ru.jeland.os

import org.junit.Test
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentThemeRepository

class DayNightSignalTest {
    @Test fun resolvedNightMaskIgnoresCarModeBits() {
        assertEquals(false,DayNightSignal.read(0x13))
        assertEquals(true,DayNightSignal.read(0x23))
        assertNull(DayNightSignal.read(0x03))
        assertNull(DayNightSignal.read(0x33))
    }
    @Test fun autoIsEnabledOnceThenManualChoiceSurvivesRestart() {
        val store=MemorySettingsStore()
        store.put("theme.mode","DARK")
        val repo=PersistentThemeRepository(store)
        assertEquals(ThemeMode.AUTO,repo.mode.value)
        repo.setMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT,PersistentThemeRepository(store).mode.value)
    }
    @Test fun manualThemesOverrideSystemAndAutoFollowsIt() {
        assertFalse(UiState(theme=ThemeMode.AUTO,night=false).dark)
        assertTrue(UiState(theme=ThemeMode.AUTO,night=true).dark)
        assertTrue(UiState(theme=ThemeMode.DARK,night=false).dark)
        assertFalse(UiState(theme=ThemeMode.LIGHT,night=true).dark)
    }
}
