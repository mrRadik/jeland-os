package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File
import kotlin.math.abs

class HeroThemeInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun nativeThemeSwitchUpdatesArtCopyAndRadiosWithoutChangingOtherArtwork() {
        var night by mutableStateOf(true)
        var weatherClicks=0
        val radios=ConnectivityState(available=true,cellular=true,mobileLevel=4,networkType="4G",
            wifi=true,wifiLevel=4,wifiEnabled=true,bluetoothEnabled=true,bluetoothConnected=true)
        ui.activityRule.scenario.onActivity {activity->activity.setContent {
            val state=UiState(theme=ThemeMode.AUTO,night=night,weather=WeatherState(demo=false,outsideCelsius=12.5))
            JelandTheme(state.dark) {
                Box(Modifier.fillMaxSize().background(Jeland.colors.background)) {
                    JelandHero(state,420.dp,{weatherClicks++},{},Jeland.headerHeight)
                    JelandHeader(radios,overHero=true)
                }
            }
        }}
        for(dark in listOf(true,false,true)) {
            ui.runOnIdle {night=dark};ui.waitForIdle()
            ui.onNodeWithContentDescription("Jeland J7 в горах — ${if(dark)"ночь"else"светлый день"}").assertExists()
            val color=jelandPalette(dark).heroText
            for(tag in listOf("hero-clock","hero-date","outside-temperature","mobile-network-type")) {
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithTag(tag,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
                assertEquals("Theme foreground $tag",color,layouts.single().layoutInput.style.color)
            }
            // Inspect the actual rendered icon pixels, not just the palette helper.
            for(tag in listOf("wifi-indicator","bluetooth-indicator")) {
                val pixels=ui.onNodeWithTag(tag).captureToImage().toPixelMap()
                var matching=0
                for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
                    val p=pixels[x,y]
                    if(abs(p.red-color.red)<.015f&&abs(p.green-color.green)<.015f&&abs(p.blue-color.blue)<.015f)matching++
                }
                assertTrue("Visible opposite-theme $tag pixels",matching>2)
            }
            ui.onNodeWithTag("weather-widget").performClick()
            val image=File(ui.activity.getExternalFilesDir(null),"qa-0.9.11/hero-${if(dark)"dark"else"light"}.png").apply {parentFile!!.mkdirs()}
            image.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(3,weatherClicks)
    }
}
