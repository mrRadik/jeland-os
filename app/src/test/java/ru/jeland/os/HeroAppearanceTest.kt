package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.ui.heroBackgroundResource
import ru.jeland.os.ui.radioIndicatorColor
import ru.jeland.os.ui.theme.jelandPalette

class HeroAppearanceTest {
    @Test fun heroCopyAndOutlineUseContrastingThemeTextAndBackground() {
        for(dark in listOf(false,true)) {
            val colors=jelandPalette(dark)
            assertEquals(colors.text,colors.heroText)
            assertTrue(kotlin.math.abs(colors.text.red-colors.background.red)>.7f)
        }
    }
    @Test fun heroForegroundIsPrimaryThemeText() {
        assertEquals(jelandPalette(true).text, jelandPalette(true).heroText)
        assertEquals(jelandPalette(false).text, jelandPalette(false).heroText)
        assertNotEquals(jelandPalette(false).heroText,jelandPalette(true).heroText)
    }
    @Test fun dayAndNightArtworkAreDifferentAndSwitchBack() {
        assertEquals(R.drawable.hero_jeland_night_v2,heroBackgroundResource(true))
        assertEquals(R.drawable.hero_jeland_day_v2,heroBackgroundResource(false))
        assertNotEquals(heroBackgroundResource(true),heroBackgroundResource(false))
        assertEquals(R.drawable.hero_jeland_night_v2,heroBackgroundResource(true))
    }
    @Test fun connectionStatesKeepThemeHueAndRemainDistinguishable() {
        for(dark in listOf(false,true)) {
            val foreground= jelandPalette(dark).heroText
            assertEquals(foreground,radioIndicatorColor(foreground,true))
            val inactive=radioIndicatorColor(foreground,false)
            assertEquals(foreground.red,inactive.red,0f)
            assertEquals(foreground.green,inactive.green,0f)
            assertEquals(foreground.blue,inactive.blue,0f)
            assertEquals(.35f,inactive.alpha,.005f)
            assertTrue(inactive.alpha<foreground.alpha)
        }
    }
    @Test fun artworkTextOutsideHeroStaysLightForDarkImages() {
        assertEquals(jelandPalette(true).artText,jelandPalette(false).artText)
        assertTrue(jelandPalette(false).artText.red>.8f)
    }
}
