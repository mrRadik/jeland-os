package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.MockSeatRepository
import ru.jeland.os.ui.RearSeatsControls
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class RearSeatsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun localPortrait() {
        ui.runOnIdle {container().car.useVehicle(false);container().theme.setMode(ThemeMode.DARK)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    @Test fun passengerPopupHasTwoWorkingIndependentRearSeatsInBothThemes() {
        val r=container().seats;val before=r.state.value
        try {
            ui.runOnIdle {r.setRearLevel(RearSeatSide.LEFT,0);r.setRearLevel(RearSeatSide.RIGHT,0)}
            ui.onNodeWithTag("passenger-seat").performClick()
            ui.onNodeWithTag("rear-left-level-2").performScrollTo().performClick()
            ui.waitUntil(5000){r.state.value.rearLeft==2};assertEquals(0,r.state.value.rearRight)
            ui.onNodeWithTag("rear-right-level-3").performClick()
            ui.waitUntil(5000){r.state.value.rearRight==3};assertEquals(2,r.state.value.rearLeft)
            ui.onNodeWithTag("rear-left-level-2").assertIsSelected();ui.onNodeWithTag("rear-right-level-3").assertIsSelected()
            assertEquals(before.driver,r.state.value.driver);assertEquals(before.passenger,r.state.value.passenger)
            assertEquals(0,ui.onAllNodesWithTag("rear-seat-vent").fetchSemanticsNodes().size)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.6.5").apply {mkdirs()}
            for(dark in listOf(true,false)) {
                ui.runOnIdle {container().theme.setMode(if(dark)ThemeMode.DARK else ThemeMode.LIGHT)}
                ui.onNodeWithTag("rear-seat-left").assertIsDisplayed();ui.onNodeWithTag("rear-seat-right").assertIsDisplayed()
                File(folder,"rear-seats-${if(dark)"dark"else"light"}.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            ui.onNodeWithTag("rear-left-level-0").performClick();ui.waitUntil(5000){r.state.value.rearLeft==0}
            assertEquals(3,r.state.value.rearRight);ui.onNodeWithTag("rear-left-value").assertTextEquals("Выключен")
        } finally {ui.runOnIdle {r.setRearLevel(RearSeatSide.LEFT,before.rearLeft?:0);r.setRearLevel(RearSeatSide.RIGHT,before.rearRight?:0);container().theme.setMode(ThemeMode.DARK)}}
    }
    @Test fun missingRightTelemetryDoesNotDisableKnownLeftOrSendRightCommand() {
        val feedback=MutableStateFlow(SeatState(available=setOf(139),rearLeft=2,rearRight=null))
        val sent=mutableListOf<Pair<RearSeatSide,Int>>()
        val r=object:SeatRepository by MockSeatRepository(MemorySettingsStore()) {
            override val state=feedback
            override fun setRearLevel(side:RearSeatSide,value:Int){sent+=side to value}
        }
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {Box(Modifier.fillMaxWidth().padding(20.dp)){RearSeatsControls(feedback.value,r)}}}}
        ui.onNodeWithTag("rear-left-level-3").assertIsEnabled().performClick()
        ui.onNodeWithTag("rear-right-level-3").assertIsNotEnabled().performTouchInput {click()}
        assertEquals(listOf(RearSeatSide.LEFT to 3),sent)
        ui.onNodeWithTag("rear-right-value").assertTextEquals("Нет данных")
        ui.onNodeWithTag("rear-left-level-2").assertIsSelected()
    }
}
