package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

/** Screenshot-only preview of the hero. No suite, search, native launch or vehicle commands. */
class HeroOutlinePreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun previewHeroOnlyInBothThemes() {
        ui.waitUntil(15000) {
            ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true
        }
        for(dark in listOf(false,true)) {
            ui.activityRule.scenario.onActivity {activity->activity.setContent {
                JelandTheme(dark) {
                    val state=UiState(theme=if(dark)ThemeMode.DARK else ThemeMode.LIGHT,
                        weather=WeatherState(demo=false,outsideCelsius=12.5))
                    Box(Modifier.fillMaxWidth().height(420.dp).background(Jeland.colors.background).testTag("hero-preview")) {
                        JelandHero(state,420.dp,{},{},Jeland.headerHeight)
                        JelandHeader(ConnectivityState(available=true,cellular=true,mobileLevel=4,networkType="4G",
                            wifi=true,wifiLevel=4,bluetoothEnabled=true,bluetoothConnected=true),overHero=true)
                    }
                }
            }}
            ui.waitUntil(10000) {
                ui.onAllNodesWithContentDescription("Jeland J7 в горах — ${if(dark)"ночь"else"светлый день"}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("hero-day-readability",useUnmergedTree=true).assertDoesNotExist()
            val hero=ui.onNodeWithTag("hero-preview").fetchSemanticsNode().boundsInRoot
            val weather=ui.onNodeWithTag("weather-widget").fetchSemanticsNode().boundsInRoot
            assertTrue("Weather stays in the right half of the hero",weather.left>=hero.center.x)
            assertTrue("Weather stays above the car",weather.bottom<hero.top+hero.height*.45f)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.9.15").apply {mkdirs()}
            File(folder,"hero-${if(dark)"dark"else"light"}.png").outputStream().use {
                ui.onNodeWithTag("hero-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }
}
