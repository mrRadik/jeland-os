package ru.jeland.os

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.JelandTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ControlPanelInactivityInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @After fun restoreClock(){ui.mainClock.autoAdvance=true}
    @Test fun onlyControlFormsAreTransientAndFourSecondsClosesWithoutVehicleChange() {
        val transient=setOf(Popup.SHADE,Popup.CLIMATE,Popup.DRIVER,Popup.PASSENGER,Popup.DRIVER_TEMPERATURE,Popup.PASSENGER_TEMPERATURE)
        Popup.entries.forEach {assertEquals(it.name,it in transient,it.isTransientControl)}
        val closed=CountDownLatch(1)
        var visible by mutableStateOf(true)
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            if(visible)Box(Modifier.size(300.dp).then(controlPanelInactivity(Popup.SHADE){visible=false;closed.countDown()}).testTag("idle-panel"))
        }}}
        ui.onNodeWithTag("idle-panel").assertExists()
        // LaunchedEffect uses Compose's virtual scheduler in these tests, not wall time.
        ui.mainClock.autoAdvance=false
        ui.mainClock.advanceTimeBy(3700);assertEquals(1L,closed.count)
        ui.mainClock.advanceTimeBy(500);ui.waitForIdle();assertEquals(0L,closed.count)
        ui.onNodeWithTag("idle-panel").assertDoesNotExist()
    }
    @Test fun holdingControlPreventsTimeoutAndReleaseRestartsFourSeconds() {
        val closed=CountDownLatch(1);var visible by mutableStateOf(true);var clicks=0
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            if(visible)Box(Modifier.size(300.dp).then(controlPanelInactivity(Popup.CLIMATE){visible=false;closed.countDown()}).testTag("idle-panel")) {
                Control("Контроль",Modifier.fillMaxSize().testTag("idle-button")){clicks++}
            }
        }}}
        ui.onNodeWithTag("idle-button").assertExists();ui.mainClock.autoAdvance=false
        ui.onNodeWithTag("idle-button").performTouchInput {down(center)}
        ui.mainClock.advanceTimeBy(4300)
        assertEquals(1L,closed.count);ui.onNodeWithTag("idle-panel").assertExists()
        ui.onNodeWithTag("idle-button").performTouchInput {up()}
        ui.mainClock.advanceTimeBy(2500)
        assertEquals(1L,closed.count);assertEquals(1,clicks)
        ui.mainClock.advanceTimeBy(1800);ui.waitForIdle();assertEquals(0L,closed.count)
    }
    @Test fun switchingControlResetsDeadline() {
        val closed=CountDownLatch(1);var popup by mutableStateOf(Popup.DRIVER)
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            Box(Modifier.size(300.dp).then(controlPanelInactivity(popup){closed.countDown()}).testTag("idle-panel"))
        }}}
        ui.onNodeWithTag("idle-panel").assertExists();ui.mainClock.autoAdvance=false;ui.mainClock.advanceTimeBy(2200)
        ui.runOnIdle {popup=Popup.PASSENGER}
        ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(2200);assertEquals(1L,closed.count)
        ui.mainClock.advanceTimeBy(2100);ui.waitForIdle();assertEquals(0L,closed.count)
    }
}
