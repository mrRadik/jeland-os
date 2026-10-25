package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.MockClimateRepository
import ru.jeland.os.ui.ClimateDashboard
import ru.jeland.os.ui.CabinAirflowPreview
import ru.jeland.os.ui.theme.JelandTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

@RunWith(AndroidJUnit4::class)
class ClimateControlsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun demoOnly() {
        ui.runOnIdle {
            container().car.useVehicle(false);container().theme.setMode(ThemeMode.DARK)
            if(!container().climate.state.value.power)container().climate.togglePower()
            container().climate.setAirflow(Airflow.FACE)
        }
        ui.waitUntil(5000){container().climate.state.value.reportedAirflow==Airflow.FACE}
        // Wait for the manifest portrait recreation before replacing Activity content in isolated tests.
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(450)
        val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val file=File(root,"qa-0.4.8/$name.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun direction(raw:Int) {
        ui.waitUntil(5000){container().climate.state.value.reportedAirflow?.commandRaw==raw}
        val selected=Airflow.fromCommand(raw)!!.outlets
        AirOutlet.entries.forEach {outlet->
            val node=ui.onNodeWithTag("airflow-${outlet.name.lowercase()}").assertIsDisplayed()
            if(outlet in selected)node.assertIsSelected()else node.assertIsNotSelected()
        }
    }
    @Test fun threeSwitchesReachEveryCombinationAndNeverAllOff() {
        ui.onNodeWithTag("expand-climate").performClick();direction(0)
        ui.onNodeWithTag("airflow-face").performClick();direction(0)
        ui.onNodeWithTag("airflow-feet").performClick();direction(1)
        ui.onNodeWithTag("airflow-face").performClick();direction(2)
        ui.onNodeWithTag("airflow-windshield").performClick();direction(3)
        ui.onNodeWithTag("airflow-feet").performClick();direction(4)
        ui.onNodeWithTag("airflow-face").performClick();direction(5)
        ui.onNodeWithTag("airflow-feet").performClick();direction(6)
        capture("climate-all-outlets-dark")
        ui.runOnIdle {container().theme.setMode(ThemeMode.LIGHT)}
        direction(6);capture("climate-all-outlets-light")
        ui.runOnIdle {container().theme.setMode(ThemeMode.DARK)}
        ui.onNodeWithTag("close-popup").performClick();capture("home-new-climate-dock")
    }
    @Test fun glassHeatButtonsInDockAndPopupShareStateButDoNotSetAirflowDefrost() {
        val r=container().climate
        ui.runOnIdle {
            if(r.state.value.rearDefrost)r.toggleRearDefrost()
            if(r.state.value.windshieldHeat)r.toggleWindshieldHeat()
            if(r.state.value.defrost)r.toggleDefrost()
        }
        ui.onNodeWithTag("bar-rear-defrost").assertIsDisplayed().performClick()
        ui.waitUntil(5000){r.state.value.rearDefrost}
        ui.onNodeWithTag("bar-windshield-heat").assertIsDisplayed().performClick()
        ui.waitUntil(5000){r.state.value.windshieldHeat};assertFalse(r.state.value.defrost)
        ui.onNodeWithTag("expand-climate").performClick()
        ui.onNodeWithTag("climate-rear-defrost").performScrollTo().assertIsSelected().performClick()
        ui.waitUntil(5000){!r.state.value.rearDefrost}
        ui.onNodeWithTag("climate-windshield-heat").assertIsSelected().performClick()
        ui.waitUntil(5000){!r.state.value.windshieldHeat};assertFalse(r.state.value.defrost)
        val power=r.state.value.power
        ui.onNodeWithTag("climate-power").performClick();ui.waitUntil(5000){r.state.value.power!=power}
        ui.onNodeWithTag("climate-sync").assertIsNotEnabled()
        ui.onNodeWithTag("climate-power").performClick();ui.waitUntil(5000){r.state.value.power==power}
    }
    @Test fun frontDefrostIsAnIndependentWorkingButtonInPopupAndDock() {
        val r=container().climate
        ui.runOnIdle {
            if(r.state.value.defrost)r.toggleDefrost()
            if(r.state.value.windshieldHeat)r.toggleWindshieldHeat()
        }
        ui.onNodeWithTag("bar-front-defrost").assertIsDisplayed().assertIsNotSelected().performClick()
        ui.waitUntil(5000){r.state.value.defrost}
        assertFalse(r.state.value.windshieldHeat)
        ui.onNodeWithTag("expand-climate").performClick()
        ui.onNodeWithTag("climate-front-defrost").performScrollTo().assertIsDisplayed().assertIsSelected()
        capture("climate-restored-front-defrost")
        ui.onNodeWithTag("climate-front-defrost").performClick()
        ui.waitUntil(5000){!r.state.value.defrost}
        ui.onNodeWithTag("bar-front-defrost").assertIsNotSelected()
        assertFalse(r.state.value.windshieldHeat)
    }
    @Test fun liveUiWaitsForFeedbackAndNeverMistakesGetSixForAllOutlets() {
        // Isolated fake port: exercises live-state UI without binding to any OEM service.
        val feedback=MutableStateFlow(ClimateState(available=setOf(6,12,18),power=true,airflowRaw=6,fan=5))
        val commands=mutableListOf<Airflow>()
        val r=object:ClimateRepository by MockClimateRepository(MemorySettingsStore()) {
            override val state=feedback
            override fun setAirflow(value:Airflow) {commands+=value}
        }
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
                ClimateDashboard(feedback.collectAsState().value,r)
            }
        }}}
        ui.onNodeWithTag("airflow-face").assertIsSelected()
        ui.onNodeWithTag("airflow-feet").assertIsNotSelected().performClick()
        assertEquals(listOf(Airflow.ALL),commands)
        ui.onNodeWithTag("airflow-feet").assertIsNotSelected()
        ui.runOnIdle {feedback.value=feedback.value.copy(airflowRaw=7)}
        ui.onNodeWithTag("airflow-feet").assertIsSelected()
        ui.onNodeWithTag("climate-sync").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("climate-power").assertIsEnabled()
        ui.runOnIdle {feedback.value=feedback.value.copy(available=emptySet(),airflowRaw=null)}
        AirOutlet.entries.forEach {ui.onNodeWithTag("airflow-${it.name.lowercase()}").performScrollTo().assertIsNotSelected()}
    }
    @Test fun cabinStreamsMatchEveryModeAndDisappearWhenOffOrUnknown() {
        val state=MutableStateFlow(ClimateState(power=true,airflow=Airflow.ALL))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            val s=state.collectAsState().value
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                CabinAirflowPreview(s.reportedAirflow,s.power,Modifier.fillMaxWidth().padding(vertical=8.dp).aspectRatio(4f))
            }
        }}}
        Airflow.entries.forEach {mode->
            ui.runOnIdle {state.value=state.value.copy(airflow=mode)}
            AirOutlet.entries.forEach {outlet->
                val node=ui.onNodeWithTag("airflow-streams-${outlet.name.lowercase()}")
                if(outlet in mode.outlets)node.assertExists()else node.assertDoesNotExist()
            }
        }
        val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val image=File(root,"qa-0.4.7/cabin-detail.png").apply {parentFile!!.mkdirs()}
        ui.onNodeWithTag("climate-cabin").captureToImage().asAndroidBitmap().let {bitmap->image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        ui.runOnIdle {state.value=state.value.copy(power=false)}
        AirOutlet.entries.forEach {ui.onNodeWithTag("airflow-streams-${it.name.lowercase()}").assertDoesNotExist()}
        ui.onNodeWithTag("climate-cabin").assertExists()
        ui.runOnIdle {state.value=ClimateState(power=true,available=emptySet())}
        AirOutlet.entries.forEach {ui.onNodeWithTag("airflow-streams-${it.name.lowercase()}").assertDoesNotExist()}
    }
    @Test fun climateOffDisablesPopupDockAndSliderUntilPowerIsOn() {
        val r=container().climate
        ui.onNodeWithTag("expand-climate").performClick()
        ui.onNodeWithTag("climate-power").performClick()
        ui.waitUntil(5000){!r.state.value.controlsEnabled}
        try {
            val before=r.state.value
            val blocked=listOf("fan-minus","fan-plus","fan-slider","climate-auto","climate-ac","climate-recirculation",
                "climate-front-defrost","climate-rear-defrost","climate-windshield-heat","climate-sync","airflow-face","airflow-feet","airflow-windshield")+(1..10).map {"fan-level-$it"}
            blocked.forEach {tag->ui.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput {click()}}
            ui.onNodeWithTag("fan-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){assertFalse(it(10f))}
            listOf("bar-auto","bar-ac","bar-recirculation","bar-front-defrost","bar-rear-defrost","bar-windshield-heat","driver-plus","driver-minus","passenger-plus","passenger-minus")
                .forEach {ui.onNodeWithTag(it).assertIsNotEnabled().performTouchInput {click()}}
            assertEquals(before,r.state.value)
            ui.onNodeWithTag("climate-power").assertIsEnabled()
            capture("climate-power-off")
            ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("expand-climate").assertIsEnabled().performClick()
            ui.onNodeWithTag("climate-power").performClick()
            ui.waitUntil(5000){r.state.value.controlsEnabled}
            ui.onNodeWithTag("fan-level-10").assertIsEnabled().performClick()
            ui.waitUntil(5000){r.state.value.fan==10}
            ui.onNodeWithTag("bar-ac").assertIsEnabled()
        } finally {ui.runOnIdle {if(!r.state.value.power)r.togglePower()}}
    }
    @Test fun recirculationUsesSameNewIconInDockAndPopupWithoutChangingItsCommand() {
        val r=container().climate;val original=r.state.value.recirculation
        fun saveNode(tag:String,name:String) {
            ui.waitForIdle();android.os.SystemClock.sleep(350)
            val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            val file=File(root,"qa-0.4.9/$name.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        try {
            ui.runOnIdle {if(r.state.value.recirculation)r.toggleRecirculation()}
            ui.onNodeWithTag("bar-recirculation").assertIsEnabled().assertIsNotSelected()
            saveNode("bar-recirculation","recirculation-dock-off")
            ui.onNodeWithTag("bar-recirculation").performClick()
            ui.waitUntil(5000){r.state.value.recirculation}
            ui.onNodeWithTag("bar-recirculation").assertIsSelected()
            saveNode("bar-recirculation","recirculation-dock-on")
            ui.onNodeWithTag("expand-climate").performClick()
            ui.onNodeWithTag("climate-recirculation").assertIsSelected()
            saveNode("climate-recirculation","recirculation-popup-on")
            ui.onNodeWithTag("climate-recirculation").performClick()
            ui.waitUntil(5000){!r.state.value.recirculation}
            ui.onNodeWithTag("bar-recirculation").assertIsNotSelected()
            ui.onNodeWithTag("climate-power").performClick()
            ui.waitUntil(5000){!r.state.value.power}
            ui.onNodeWithTag("climate-recirculation").assertIsNotEnabled()
            ui.onNodeWithTag("bar-recirculation").assertIsNotEnabled()
        } finally {ui.runOnIdle {
            if(!r.state.value.power)r.togglePower()
            if(r.state.value.recirculation!=original)r.toggleRecirculation()
        }}
    }
}
