package ru.jeland.os

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*
import ru.jeland.os.data.*
import ru.jeland.os.data.automation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.automation.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class AutomationUiInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var db:AutomationDatabase
    private lateinit var dependencies:AppContainer
    private lateinit var model:MainViewModel
    @Before fun isolatedGraph() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse("QA targets local BlueStacks, not the physical HU",NativeApps(context).settingsAvailable)
        db=Room.inMemoryDatabaseBuilder(context,AutomationDatabase::class.java).build()
        dependencies=AppContainer(context,db,MemorySettingsStore());model=MainViewModel(dependencies)
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        ui.activityRule.scenario.onActivity {it.setContent {JelandApp(model)}}
        ui.onNodeWithTag("nav-automations").performClick()
        ui.waitUntil(5000){ui.onAllNodesWithTag("automations-empty").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun close() {
        ui.activityRule.scenario.onActivity {it.setContent {}}
        if(::model.isInitialized){model.pause();model.viewModelScope.cancel()}
        if(::db.isInitialized)db.close()
    }
    private fun rules()=runBlocking {dependencies.automations.rules.first()}
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(450)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.6.0").apply {mkdirs()}
        val node=if(ui.onAllNodesWithTag("automation-delete-dialog").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("automation-delete-dialog")else ui.onRoot()
        File(folder,"$name.png").outputStream().use {node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun assertPanels() {
        for(tag in listOf("jeland-header","nav-automations","nav-settings","climate-bar"))ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    @Test fun createEditToggleAndConfirmedDeleteKeepOtherRuleUntouched() {
        capture("empty-dark");assertPanels()
        ui.onNodeWithTag("automation-create").performClick()
        ui.onNodeWithTag("automation-save").performClick();ui.onNodeWithTag("automation-errors").assertIsDisplayed()
        ui.onNodeWithTag("automation-name").performTextReplacement("Зимний комфорт")
        ui.onNodeWithTag("automation-driver_heating-level_2").performScrollTo().performClick()
        ui.onNodeWithTag("automation-passenger_heating-level_2").performScrollTo().performClick()
        ui.onNodeWithTag("automation-rear_left_heating-level_1").performScrollTo().performClick()
        ui.onNodeWithTag("automation-rear_right_heating-keep").performScrollTo().assertIsSelected()
        capture("seats-editor-rear-dark");assertPanels()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==1&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single();val action=rule.action as SeatsAction
        assertEquals(SeatLevel.LEVEL_2,action.driverHeating);assertEquals(SeatLevel.OFF,action.driverVentilation)
        assertEquals(SeatLevel.LEVEL_2,action.passengerHeating);assertEquals(SeatLevel.LEVEL_1,action.rearLeftHeating);assertEquals(SeatLevel.KEEP,action.rearRightHeating)
        val other=runBlocking {dependencies.automations.save(AutomationRule(name="Шторка при запуске",componentType=ComponentType.SUNSHADE,triggerType=TriggerType.SESSION_START,action=SunshadeAction.OPEN_HALF),true)}
        ui.waitUntil(5000){ui.onAllNodesWithTag("automation-card-${other.id}").fetchSemanticsNodes().isNotEmpty()}
        capture("rules-dark")
        ui.onNodeWithTag("automation-enabled-${rule.id}").performScrollTo().performClick()
        ui.waitUntil(5000){rules().single {it.id==rule.id}.enabled==false}
        ui.onNodeWithTag("automation-edit-${rule.id}").performScrollTo().performClick()
        ui.waitUntil(5000){runCatching {ui.onNodeWithTag("automation-name").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text=="Зимний комфорт"}.getOrDefault(false)}
        ui.onNodeWithTag("automation-name").performTextReplacement("Зима · обновлено")
        ui.onNodeWithTag("automation-driver_heating-level_3").performScrollTo().performClick()
        capture("seats-editor-front-dark")
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.AUTO);dependencies.dayNight.toggle()}
        capture("seats-editor-auto-day")
        ui.runOnIdle {dependencies.dayNight.toggle()}
        capture("seats-editor-auto-night")
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.DARK)}
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().any {it.name=="Зима · обновлено"}&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(other,rules().single {it.id==other.id})
        assertEquals(SeatLevel.LEVEL_2,(rules().single {it.id==rule.id}.action as SeatsAction).passengerHeating)
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)}
        ui.onNodeWithTag("automation-delete-${rule.id}").performScrollTo().performClick();capture("delete-light")
        ui.onNodeWithTag("automation-delete-cancel").performClick();assertEquals(2,rules().size)
        ui.onNodeWithTag("automation-delete-${rule.id}").performClick();ui.onNodeWithTag("automation-delete-confirm").performClick()
        ui.waitUntil(5000){rules().size==1};assertEquals(other,rules().single());capture("rules-light")
        ui.onNodeWithTag("automation-delete-${other.id}").performScrollTo().performClick();ui.onNodeWithTag("automation-delete-confirm").performClick()
        ui.waitUntil(5000){ui.onAllNodesWithTag("automations-empty").fetchSemanticsNodes().isNotEmpty()};assertPanels()
    }
    @Test fun shadeEditorHasOnlyTwoPositionsAndDebugSessionDoesNotRepeatOrTouchCarState() {
        val carBefore=dependencies.vehicle.state.value;val seatsBefore=dependencies.seats.state.value
        ui.onNodeWithTag("automation-create").performClick();ui.onNodeWithTag("automation-name").performTextReplacement("Шторка при запуске")
        ui.onNodeWithTag("automation-component-sunshade").performClick()
        ui.onNodeWithTag("automation-temperature-trigger").assertDoesNotExist();ui.onNodeWithTag("automation-seats-actions").assertDoesNotExist()
        ui.onNodeWithTag("automation-shade-open_full").performClick().assertIsSelected()
        ui.onNodeWithTag("automation-shade-open_half").performClick().assertIsSelected()
        capture("shade-editor-dark");assertPanels()
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)};capture("shade-editor-light")
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==1&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single();assertEquals(SunshadeAction.OPEN_HALF,rule.action);assertNull(rule.temperatureThreshold);assertEquals(TriggerType.SESSION_START,rule.triggerType)
        ui.onNodeWithTag("automation-debug-toggle").performClick()
        ui.onNodeWithTag("automation-debug-new").performScrollTo().performClick()
        ui.waitUntil(5000){dependencies.automationComfort.state.value.shade==SunshadeAction.OPEN_HALF}
        ui.onNodeWithTag("automation-debug-manual").performScrollTo().performClick()
        assertEquals(1,dependencies.automationComfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        ui.onNodeWithTag("automation-debug-repeat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-debug-summary").assertTextContains("Повторных команд нет",substring=true)
        assertEquals(1,dependencies.automationComfort.state.value.seats[SeatParameter.DRIVER_HEATING])
        ui.onNodeWithTag("automation-debug-parked").performScrollTo().performClick()
        ui.onNodeWithTag("automation-debug-new").performClick()
        ui.waitUntil(5000){runBlocking {dependencies.automations.latestRuns.first()}.singleOrNull()?.status==AutomationRunStatus.UNSAFE}
        ui.onNodeWithTag("automation-result-${rule.id}").performScrollTo();capture("debug-unsafe-light")
        assertEquals(carBefore,dependencies.vehicle.state.value);assertEquals(seatsBefore,dependencies.seats.state.value);assertPanels()
    }
    @Test fun editorDraftRoundtripsSavedStateAndCancelDoesNotCreateRule() {
        val handle=SavedStateHandle();val editor=AutomationEditorViewModel("new",dependencies.automations,handle)
        editor.name("x".repeat(60));editor.seat(SeatParameter.DRIVER_HEATING,SeatLevel.LEVEL_2)
        assertEquals(50,editor.state.value.draft.name.length);assertEquals(SeatLevel.OFF,editor.state.value.draft.seats.driverVentilation)
        val parcel=Parcel.obtain()
        try {
            parcel.writeBundle(handle.get<Bundle>("automation-draft"));parcel.setDataPosition(0)
            val restoredHandle=SavedStateHandle(mapOf("automation-draft" to parcel.readBundle(javaClass.classLoader)))
            val recreated=AutomationEditorViewModel("new",dependencies.automations,restoredHandle)
            assertEquals(editor.state.value.draft,recreated.state.value.draft);recreated.viewModelScope.cancel()
        }finally {parcel.recycle();editor.viewModelScope.cancel()}
        ui.onNodeWithTag("automation-create").performClick();ui.onNodeWithTag("automation-name").performTextReplacement("Отменённое правило")
        ui.onNodeWithTag("automation-cancel").performClick();assertTrue(rules().isEmpty())
        ui.onNodeWithTag("automations-empty").assertIsDisplayed()
    }
}
