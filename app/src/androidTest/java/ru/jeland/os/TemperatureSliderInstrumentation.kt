package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.MockClimateRepository
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

class TemperatureSliderInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun demoOnly() {
        ui.runOnIdle {
            container().car.useVehicle(false);container().theme.setMode(ThemeMode.DARK)
            val r=container().climate;if(!r.state.value.power)r.togglePower();if(r.state.value.sync)r.toggleSync()
            r.setTemperature(true,22.0);r.setTemperature(false,22.0)
        }
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun progress(value:Float)=ui.onNodeWithTag("temperature-slider").performSemanticsAction(SemanticsActions.SetProgress){assertTrue(it(value))}
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(300)
        val file=File(ui.activity.getExternalFilesDir(null),"qa-0.6.7/$name.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun dockTapOpensCompactSliderForCorrectSideInBothThemes() {
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {container().theme.setMode(mode)}
            ui.onNodeWithTag("driver-temp").performClick()
            ui.onNodeWithTag("temperature-side").assertTextEquals("Температура водителя")
            progress(26f);ui.waitUntil(5000){container().climate.state.value.driver==26.0}
            assertEquals(22.0,container().climate.state.value.passenger,0.0)
            val panel=ui.onNodeWithTag("temperature-panel").fetchSemanticsNode().boundsInRoot
            val bar=ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.bottom<bar.top);assertTrue(panel.height<panel.width*.35f)
            capture("driver-${mode.name.lowercase()}")
            ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("passenger-temp").performClick()
            ui.onNodeWithTag("temperature-side").assertTextEquals("Температура пассажира")
            progress(20f);ui.waitUntil(5000){container().climate.state.value.passenger==20.0}
            assertEquals(26.0,container().climate.state.value.driver,0.0)
            capture("passenger-${mode.name.lowercase()}")
            ui.onNodeWithTag("close-popup").performClick()
            ui.runOnIdle {container().climate.setTemperature(true,22.0);container().climate.setTemperature(false,22.0)}
        }
    }
    @Test fun dragSendsOneAbsoluteValueAndCancellationSendsNone() {
        val delegate=MockClimateRepository(MemorySettingsStore());val calls=mutableListOf<Double>()
        val r=object:ClimateRepository by delegate {override fun setTemperature(driver:Boolean,value:Double){calls+=value;delegate.setTemperature(driver,value)}}
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            val climate by r.state.collectAsState()
            Box(Modifier.fillMaxWidth().padding(24.dp)){TemperatureSliderPanel(true,UiState(climate=climate),r){}}
        }}}
        ui.onNodeWithTag("temperature-slider").performTouchInput {down(centerLeft);moveTo(center);moveTo(centerRight);up()}
        ui.waitForIdle();assertEquals(1,calls.size);assertTrue(calls.single() in 16.0..32.0)
        calls.clear()
        ui.onNodeWithTag("temperature-slider").performTouchInput {down(centerLeft);moveTo(center);cancel()}
        ui.waitForIdle();assertTrue(calls.isEmpty())
    }
    @Test fun liveLimitsAndMissingPermissionPowerOrFeedbackCannotSend() {
        val calls=mutableListOf<Double>();val delegate=MockClimateRepository(MemorySettingsStore())
        val r=object:ClimateRepository by delegate {override fun setTemperature(driver:Boolean,value:Double){calls+=value}}
        var s by mutableStateOf(UiState(car=VehicleLinkState(demo=false,connected=true,writesEnabled=true),
            climate=ClimateState(power=true,available=setOf(18,10),driver=15.0,driverRaw=0)))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true){TemperatureSliderPanel(true,s,r){}}}}
        ui.onNodeWithTag("temperature-preview").assertTextEquals("LO")
        progress(31f);assertEquals(listOf(31.0),calls)
        ui.runOnIdle {s=s.copy(climate=s.climate.copy(driver=31.0,driverRaw=16))}
        ui.onNodeWithTag("temperature-preview").assertTextEquals("HI")
        for(block in listOf("power","unknown","lock")) {
            ui.runOnIdle {s=s.copy(climate=s.climate.copy(power=block!="power",available=if(block=="unknown")setOf(18)else setOf(18,10)),car=s.car.copy(writesEnabled=block!="lock",busy=block=="busy"))}
            ui.onNodeWithTag("temperature-slider").assertIsNotEnabled()
            ui.onNodeWithTag("temperature-slider").performSemanticsAction(SemanticsActions.SetProgress){assertFalse(it(22f))}
        }
        assertEquals(1,calls.size)
        ui.runOnIdle {s=s.copy(climate=s.climate.copy(power=true,available=setOf(18,10)),car=s.car.copy(writesEnabled=true,busy=true))}
        ui.onNodeWithTag("temperature-slider").assertIsEnabled()
    }
    @Test fun liveSyncButtonUsesFreshFeedbackNotOptimisticToggle() {
        val delegate=MockClimateRepository(MemorySettingsStore());var requests=0
        val r=object:ClimateRepository by delegate {override fun toggleSync(){requests++}}
        var feedback by mutableStateOf(ClimateState(power=true,available=setOf(18,3),sync=false))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            Box(Modifier.fillMaxWidth().padding(16.dp)){ClimateDashboard(feedback,r)}
        }}}
        ui.onNodeWithTag("climate-sync").assertIsEnabled().assertIsNotSelected().performClick()
        assertEquals(1,requests);ui.onNodeWithTag("climate-sync").assertIsNotSelected()
        ui.runOnIdle {feedback=feedback.copy(sync=true)}
        ui.onNodeWithTag("climate-sync").assertIsSelected()
        ui.runOnIdle {feedback=feedback.copy(available=setOf(18))}
        ui.onNodeWithTag("climate-sync").assertIsNotEnabled().assertIsNotSelected()
    }
}
