package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class FieldFixesInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun stablePortrait() {
        ui.runOnIdle {container().car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(450)
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa-0.4.4").apply {mkdirs()}
        val image=ui.onRoot().captureToImage().asAndroidBitmap()
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun liveTyresFullCarDoorsAndCompactWarningInBothThemes() {
        ui.runOnIdle {container().car.useVehicle(false)}
        val reducer=LiveVehicleReducer()
        reducer.accept(VdsKey(VerifiedVehicleProtocol.INFO,140),listOf(182,171,175,169),0)
        val state=reducer.vehicle(1).copy(openings=VehicleOpening.entries.toSet())
        var clicked:Wheel?=null
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
                Column(Modifier.fillMaxSize().background(Jeland.colors.background).padding(16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    VehicleStatusCard(state,Modifier.fillMaxWidth().height(210.dp),{}, {wheel->clicked=wheel},{})
                    VehicleEventNotifications(VehicleNoticeState(warningOpenings=setOf(VehicleOpening.FRONT_LEFT)),true)
                    TireDetailsPopup(Wheel.FRONT_LEFT,state)
                }
            }}}
            ui.onNodeWithTag("tire-front_left").assertTextContains("≈ 2.5 бар",substring=true)
            ui.onNodeWithTag("tire-front_right").assertTextContains("≈ 2.3 бар",substring=true)
            ui.onNodeWithTag("tire-rear_left").assertTextContains("≈ 2.4 бар",substring=true)
            ui.onNodeWithTag("tire-rear_right").assertTextContains("≈ 2.3 бар",substring=true)
            val image=ui.onNodeWithTag("vehicle-artwork",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val view=ui.onNodeWithTag("vehicle-top-view").fetchSemanticsNode().boundsInRoot
            assertEquals(2f,image.width/image.height,.01f)
            assertTrue(view.contains(image.topLeft) && view.contains(image.bottomRight))
            ui.onNodeWithTag("wheel-front_left").performClick();assertEquals(Wheel.FRONT_LEFT,clicked)
            ui.onNodeWithText("Открыта водительская дверь").assertIsDisplayed()
            ui.onNodeWithTag("tire-detail-pressure").assertTextEquals("≈ 2.5 бар")
            ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("Нет данных")
            capture("live-vehicle-${if(dark)"dark"else"light"}")
        }
    }
    @Test fun shadeButtonsHaveNoExtraDialogAndPanelLatchIsNotHvacPower() {
        ui.runOnIdle {container().car.useVehicle(false)}
        val model=MainViewModel(container())
        val state=UiState(car=VehicleLinkState(demo=false),vehicle=VehicleState(live=true,shadeRaw=listOf(5)))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            Column(Modifier.fillMaxSize().background(Jeland.colors.background).padding(24.dp)) {PopupContent(Popup.SHADE,state,model){}}
        }}}
        ui.onNodeWithTag("shade-state").assertTextEquals("Открыта")
        ui.onNodeWithTag("shade-half").performClick()
        ui.onNodeWithText("Отправить").assertDoesNotExist()
        ui.waitUntil(5000){container().vehicle.state.value.shade==Shade.HALF}
        assertEquals(Shade.HALF,container().vehicle.state.value.shade)
        assertFalse(VerifiedVehicleProtocol.allowed(VdsWrite(VdsKey(VerifiedVehicleProtocol.HVAC,9),2)))
        capture("shade-open-no-confirmation")
    }
}
