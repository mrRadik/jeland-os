package ru.jeland.os

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.ThemeMode
import java.io.File

class NavigationSearchInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=(ui.activity.application as JelandApplication).container
    @Before fun prepare(){
        ui.runOnIdle {app.car.useVehicle(false);app.navigation.stop();app.navigation.provider.voice.setEnabled(false)}
        ui.waitUntil(20000){!app.navigation.preparing.value}
    }
    @Test fun partialAddressesKeepLocalitiesAndRankExactHouseFirst()=runBlocking<Unit> {
        val results=app.navigation.maps.search("Мира 1")
        assertTrue(results.size>=5)
        assertTrue(results.all {it.locality.isNotBlank()})
        assertTrue(results.map {it.locality}.distinct().size>=5)
        for(house in listOf(10,11,12))assertTrue("Missing house prefix result $house",results.any {Regex("(?<!\\d)$house(?!\\d)").containsMatchIn(it.name)})
        assertTrue(app.navigation.maps.search("Мир 1").any {it.name.contains("Мира 10")})
        val chebarkul=app.navigation.maps.search("Мира 1 Чебаркуль")
        assertTrue(chebarkul.size>1)
        assertEquals("Чебаркуль",chebarkul.first().locality)
        assertEquals("улица Мира 1",chebarkul.first().name)
        assertEquals(54.982041,chebarkul.first().point.latitude,0.000001)
        assertEquals(60.369130,chebarkul.first().point.longitude,0.000001)
        val zlatoust=app.navigation.maps.search("проспект Мира 1 Златоуст")
        assertTrue(zlatoust.any {it.name=="проспект Мира 1"&&it.locality=="Златоуст"})
        assertTrue(app.navigation.maps.search("55.165,61.437").isEmpty())
    }
    @Test fun searchShowsCityInsteadOfCoordinatesAndSelectionKeepsCityInBothThemes() {
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.onNodeWithTag("navigation-search-open").performClick()
        ui.onNodeWithTag("navigation-address-keyboard").assertIsDisplayed()
        ui.onNodeWithTag("navigation-keyboard-toggle").performClick()
        ui.onNodeWithTag("navigation-destination").performTextInput("Мира 1")
        ui.waitUntil(15000){ui.onAllNodesWithTag("navigation-result-locality-4",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            for(index in 0..4) {
                val node=ui.onNodeWithTag("navigation-result-locality-$index",useUnmergedTree=true).fetchSemanticsNode()
                val text=node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString {it.text}
                assertTrue(text.isNotBlank());assertFalse(Regex("\\d+\\.\\d+").containsMatchIn(text))
            }
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
            val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/search-${mode.name.lowercase()}.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {while(it.read()!=-1){}}
            assertTrue(File(folder,"search-${mode.name.lowercase()}.png").length()>10000)
        }
        ui.onNodeWithTag("navigation-destination").performTextClearance()
        ui.onNodeWithTag("navigation-destination").performTextInput("Мира 1 Чебаркуль")
        ui.waitUntil(15000){
            val config=ui.onAllNodesWithTag("navigation-result-locality-0",useUnmergedTree=true).fetchSemanticsNodes().singleOrNull()?.config
            val key=androidx.compose.ui.semantics.SemanticsProperties.Text
            config!=null&&config.contains(key)&&config[key].joinToString {it.text}=="Чебаркуль"
        }
        ui.onNodeWithTag("navigation-result-0").performClick()
        ui.onNodeWithTag("navigation-selected").assertTextContains("Чебаркуль",substring=true)
        ui.runOnIdle {assertEquals("Чебаркуль",app.navigation.selected.value?.locality)}
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
    }
    @Test fun builtInKeyboardTypesAddressSwitchesLanguageAndCanBeReopened() {
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.onNodeWithTag("navigation-search-open").performClick()
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.onNodeWithTag("navigation-address-keyboard").assertIsDisplayed()
            for(key in listOf("м","и","р","Пробел","1"))ui.onNodeWithTag("navigation-key-$key").performClick()
            ui.onNodeWithTag("navigation-destination").assertTextContains("мир 1")
            ui.waitUntil(15000){ui.onAllNodesWithTag("navigation-result-0").fetchSemanticsNodes().isNotEmpty()}
            ui.waitForIdle()
            // Wait for the compositor's frame as well as Compose semantics before screencap.
            Thread.sleep(250)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
            val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/keyboard-${mode.name.lowercase()}.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {while(it.read()!=-1){}}
            ui.onNodeWithTag("navigation-key-⌫").performClick()
            ui.onNodeWithTag("navigation-key-2").performClick()
            ui.onNodeWithTag("navigation-destination").assertTextContains("мир 2")
            ui.onNodeWithTag("navigation-key-РУС / EN").performClick()
            ui.onNodeWithTag("navigation-key-q").assertIsDisplayed().performClick()
            ui.onNodeWithTag("navigation-key-EN / РУС").performClick()
            ui.onNodeWithTag("navigation-search-clear").performClick()
            ui.onNodeWithTag("navigation-destination").assertTextEquals("Адрес или организация","")
            ui.onNodeWithTag("navigation-keyboard-toggle").performClick()
            ui.onNodeWithTag("navigation-address-keyboard").assertDoesNotExist()
            ui.onNodeWithTag("navigation-keyboard-toggle").performClick()
        }
        ui.onNodeWithTag("navigation-sheet-close").performClick()
        ui.onNodeWithTag("navigation-address-keyboard").assertDoesNotExist()
    }
}
