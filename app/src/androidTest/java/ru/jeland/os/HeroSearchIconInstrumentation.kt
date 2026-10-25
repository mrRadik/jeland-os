package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import ru.jeland.os.core.*
import ru.jeland.os.navigation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

/** Local rendering and map data only. No native camera, climate or vehicle command. */
class HeroSearchIconInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun stablePortrait() {
        ui.waitUntil(15000) {
            ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true
        }
    }
    private val folder get()=File(ui.activity.getExternalFilesDir(null),"qa-0.9.14").apply {mkdirs()}
    private fun capture(name:String) {
        ui.waitForIdle()
        File(folder,"$name.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun daylightHeroHasOutlinedTextWithoutImageWashOrWeatherCard() {
        var clicked=0
        for(dark in listOf(false,true)) {
            ui.activityRule.scenario.onActivity {it.setContent {
                JelandTheme(dark) {
                    val state=UiState(theme=if(dark)ThemeMode.DARK else ThemeMode.LIGHT,weather=WeatherState(demo=false,outsideCelsius=12.5))
                    Box(Modifier.fillMaxSize().background(Jeland.colors.background)) {
                        JelandHero(state,420.dp,{clicked++},{},Jeland.headerHeight)
                        JelandHeader(ConnectivityState(available=true,cellular=true,mobileLevel=4,networkType="4G",wifi=true,wifiLevel=4,bluetoothEnabled=true,bluetoothConnected=true),overHero=true)
                    }
                }
            }}
            ui.waitUntil(10000) {
                ui.onAllNodesWithContentDescription("Jeland J7 в горах — ${if(dark)"ночь"else"светлый день"}",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
            ui.onNodeWithTag("hero-day-readability",useUnmergedTree=true).assertDoesNotExist()
            capture("hero-${if(dark)"dark"else"light"}")
            ui.onNodeWithTag("weather-widget").performClick()
        }
        assertEquals(2,clicked)
    }
    @Test fun actualOrganizationIndexAndSearchRowsIncludeHouseAddressInBothThemes()=runBlocking<Unit> {
        val app=(ui.activity.application as JelandApplication).container
        val results=app.navigation.maps.search("Магнит Косметик Каслинская 34")
        val target=results.first {it.locality=="Челябинск"&&it.address=="Каслинская улица 34"}
        val other=app.navigation.maps.search("Магнит Челябинск").first {it.address.isNotBlank()&&it.address!=target.address}
        assertEquals(55.187484,target.point.latitude,.000001)
        assertEquals(61.392918,target.point.longitude,.000001)
        var selected:ru.jeland.os.navigation.NavigationPlace?=null
        for(dark in listOf(false,true)) {
            ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
                NavigationSearchPanel(TextFieldValue("Магнит"),{},listOf(target,other),false,null,{selected=it},Modifier.fillMaxSize().background(Jeland.colors.background))
            }}}
            ui.onNodeWithTag("navigation-result-locality-0",useUnmergedTree=true).assertTextEquals("Челябинск · Каслинская улица 34")
            ui.onNodeWithTag("navigation-result-locality-1",useUnmergedTree=true).assertTextEquals(other.searchSubtitle)
            capture("search-${if(dark)"dark"else"light"}")
            ui.onNodeWithTag("navigation-result-0").performClick()
            assertEquals(target,selected)
        }
    }
    @Test fun manifestLoadsNewAdaptiveIconAndRendersSmallSizes() {
        val icon=ui.activity.packageManager.getApplicationIcon(ui.activity.packageName)
        assertTrue(icon is AdaptiveIconDrawable)
        assertEquals(R.mipmap.ic_launcher,ui.activity.applicationInfo.icon)
        for(size in listOf(48,96,256)) {
            val image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
            icon.setBounds(0,0,size,size);icon.draw(Canvas(image))
            var cyan=0
            for(y in 0 until size)for(x in 0 until size) {
                val color=image.getPixel(x,y)
                if(android.graphics.Color.blue(color)>100&&android.graphics.Color.green(color)>90)cyan++
            }
            assertTrue("Visible highlights in ${size}px icon",cyan>size/2)
            File(folder,"icon-$size.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            image.recycle()
        }
    }
}
