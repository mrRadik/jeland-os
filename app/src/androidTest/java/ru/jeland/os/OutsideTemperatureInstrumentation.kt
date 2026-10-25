package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.NativeApps
import ru.jeland.os.data.ExistingWeatherRepository
import ru.jeland.os.data.MockWeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

class OutsideTemperatureInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun demo(){
        ui.runOnIdle {(ui.activity.application as JelandApplication).container.car.useVehicle(false)}
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
        ui.waitForIdle()
    }
    @Test fun silentRefreshPreservesThePreviousRealReading() {
        val link=MutableStateFlow(VehicleLinkState(demo=false,connected=true,raw=mapOf(
            OutsideTemperature.key to RawVehicleSample(listOf(149),android.os.SystemClock.elapsedRealtime()))))
        val repo=ExistingWeatherRepository(NativeApps(ui.activity),MockWeatherRepository(MemorySettingsStore()),link)
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true){val value by repo.state.collectAsState();WeatherWidget(value,onClick={})}}}
        ui.waitUntil(5000){ui.onAllNodesWithText("+19,5°").fetchSemanticsNodes().isNotEmpty()}
        ui.runOnIdle {link.value=link.value.copy(raw=emptyMap())}
        android.os.SystemClock.sleep(1200);ui.onNodeWithTag("outside-temperature",useUnmergedTree=true).assertTextEquals("+19,5°")
        ui.runOnIdle {link.value=link.value.copy(raw=mapOf(OutsideTemperature.key to RawVehicleSample(listOf(135),android.os.SystemClock.elapsedRealtime())))}
        ui.waitUntil(5000){ui.onAllNodesWithText("+12,5°").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun onlyLargeTemperatureNoWeatherCaptionOrIconAndClickWorksInBothThemes() {
        var dark by mutableStateOf(true);var clicks=0
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
            Box(Modifier.fillMaxWidth().padding(24.dp)){WeatherWidget(WeatherState(demo=false,outsideCelsius=19.5),onClick={clicks++})}
        }}}
        for(theme in listOf(true,false)) {
            ui.runOnIdle {dark=theme}
            ui.onNodeWithTag("outside-temperature",useUnmergedTree=true).assertTextEquals("+19,5°")
            ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).assertCountEquals(1)
            ui.onNodeWithText("Штатная погода").assertDoesNotExist();ui.onNodeWithText("Облачно").assertDoesNotExist()
            ui.onNodeWithTag("weather-widget").performClick()
            val file=File(ui.activity.getExternalFilesDir(null),"qa-0.6.9/temperature-${if(theme)"dark"else"light"}.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(2,clicks)
        assertEquals("com.liontech.weather",NativeApps.weatherIntent().component!!.packageName)
    }
    @Test fun missingLiveSensorShowsOnlyDashNotDemoTemperature() {
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true){WeatherWidget(WeatherState(demo=false),onClick={})}}}
        ui.onNodeWithTag("outside-temperature",useUnmergedTree=true).assertTextEquals("—°")
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).assertCountEquals(1)
    }
}
