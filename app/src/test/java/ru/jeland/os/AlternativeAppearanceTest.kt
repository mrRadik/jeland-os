package ru.jeland.os

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository
import ru.jeland.os.ui.theme.jelandPalette

class AlternativeAppearanceTest {
    @Test fun existingSettingsKeepClassicAndUnknownStyleIsSafe() {
        val store=MemorySettingsStore()
        assertEquals(DashboardStyle.CLASSIC,PersistentInterfaceRepository(store).state.value.style)
        store.put("ui.style","unsupported-future-style")
        assertEquals(DashboardStyle.CLASSIC,PersistentInterfaceRepository(store).state.value.style)
    }
    @Test fun styleAndVisibilitySurviveRepositoryRecreation() {
        val store=MemorySettingsStore();val repo=PersistentInterfaceRepository(store)
        repo.change(repo.state.value.copy(style=DashboardStyle.MINIMAL,showFuel=false,showFavorites=false))
        val restored=PersistentInterfaceRepository(store).state.value
        assertEquals(DashboardStyle.MINIMAL,restored.style)
        assertFalse(restored.showFuel);assertFalse(restored.showFavorites);assertTrue(restored.showTires)
        repo.change(restored.copy(showFuel=true))
        assertEquals(DashboardStyle.MINIMAL,PersistentInterfaceRepository(store).state.value.style)
    }
    @Test fun bothAlternativePalettesHaveReadablePrimaryAndSecondaryCopy() {
        fun contrast(a:Color,b:Color):Float {
            val x=a.luminance();val y=b.luminance()
            return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
        }
        for(dark in listOf(false,true)) {
            val c=jelandPalette(dark,DashboardStyle.MINIMAL)
            assertTrue(contrast(c.text,c.background)>=7f)
            assertTrue(contrast(c.muted,c.background)>=4.5f)
            assertTrue(contrast(c.accent,c.panel)>=4.5f)
            assertNotEquals(jelandPalette(dark).background,c.background)
        }
    }
}
