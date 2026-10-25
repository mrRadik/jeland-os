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
        dependencies=AppContainer(context,db,MemorySettingsStore());dependencies.theme.setMode(ThemeMode.DARK);model=MainViewModel(dependencies)
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
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.9.32").apply {mkdirs()}
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
        ui.onNodeWithTag("automation-mode-temperature").performScrollTo().performClick()
        ui.onNodeWithTag("automation-component-driver_seat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-driver_heating-level_2").performScrollTo().performClick()
        ui.onNodeWithTag("automation-component-passenger_seat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-passenger_heating-level_2").performScrollTo().performClick()
        ui.onNodeWithTag("automation-component-rear_left_seat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-rear_left_heating-level_1").performScrollTo().performClick()
        ui.onNodeWithTag("automation-rear_right_heating-keep").assertDoesNotExist()
        capture("seats-editor-rear-dark");assertPanels()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==1&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single();assertEquals(3,rule.actions.size);val action=AutomationDraft.from(rule).seats
        assertEquals(SeatLevel.LEVEL_2,action.driverHeating);assertEquals(SeatLevel.OFF,action.driverVentilation)
        assertEquals(SeatLevel.LEVEL_2,action.passengerHeating);assertEquals(SeatLevel.LEVEL_1,action.rearLeftHeating);assertEquals(SeatLevel.KEEP,action.rearRightHeating)
        val other=runBlocking {dependencies.automations.save(AutomationRule(name="Шторка при запуске",triggerType=TriggerType.SESSION_START,actions=listOf(SunshadeAction.OPEN_HALF)),true)}
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
        assertEquals(SeatLevel.LEVEL_2,AutomationDraft.from(rules().single {it.id==rule.id}).seats.passengerHeating)
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)}
        ui.onNodeWithTag("automation-delete-${rule.id}").performScrollTo().performClick();capture("delete-light")
        ui.onNodeWithTag("automation-delete-cancel").performClick();assertEquals(2,rules().size)
        ui.onNodeWithTag("automation-delete-${rule.id}").performClick();ui.onNodeWithTag("automation-delete-confirm").performClick()
        ui.waitUntil(5000){rules().size==1};assertEquals(other,rules().single());capture("rules-light")
        ui.onNodeWithTag("automation-delete-${other.id}").performScrollTo().performClick();ui.onNodeWithTag("automation-delete-confirm").performClick()
        ui.waitUntil(5000){ui.onAllNodesWithTag("automations-empty").fetchSemanticsNodes().isNotEmpty()};assertPanels()
    }
    @Test fun shadeEditorHasOnlyTwoPositionsAndNoSimulationButtons() {
        val carBefore=dependencies.vehicle.state.value;val seatsBefore=dependencies.seats.state.value
        ui.onNodeWithTag("automation-create").performClick()
        ui.onNodeWithTag("automation-name").performTextReplacement("Шторка при запуске")
        ui.onNodeWithTag("automation-mode-auto").performScrollTo().performClick()
        ui.onNodeWithTag("automation-event-engine_start").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("automation-component-sunshade").performScrollTo().performClick()
        ui.onNodeWithTag("automation-shade-open_full").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithTag("automation-shade-open_half").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==1&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single()
        assertEquals(listOf(SunshadeAction.OPEN_HALF),rule.actions);assertEquals(LaunchEvent.ENGINE_START,rule.launchEvent)
        ui.onNodeWithTag("automation-run-${rule.id}").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("automation-debug-toggle").assertDoesNotExist()
        assertEquals(carBefore,dependencies.vehicle.state.value);assertEquals(seatsBefore,dependencies.seats.state.value)
    }
    @Test fun multiDeviceScenarioCanBeEditedSavedAndRunFromItsCard() {
        val carBefore=dependencies.vehicle.state.value;val seatsBefore=dependencies.seats.state.value
        ui.onNodeWithTag("automation-create").performClick()
        ui.onNodeWithTag("automation-name").performTextReplacement("Комфорт одним нажатием")
        ui.onNodeWithTag("automation-mode-manual").assertIsSelected()
        ui.onNodeWithTag("automation-component-steering_wheel").performScrollTo().performClick()
        ui.onNodeWithTag("automation-steering-actions").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("automation-component-sunshade").performScrollTo().performClick()
        ui.onNodeWithTag("automation-component-driver_seat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-driver_heating-level_1").performScrollTo().performClick()
        ui.onNodeWithTag("automation-component-steering_wheel").performScrollTo().assertIsSelected().performClick().assertIsNotSelected()
        ui.onNodeWithTag("automation-steering-actions").assertDoesNotExist()
        ui.onNodeWithTag("automation-driver_heating-level_1").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("automation-component-steering_wheel").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithTag("automation-steering-actions").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("automation-mode-temperature").performScrollTo().performClick()
        ui.onNodeWithTag("automation-temperature-trigger").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("automation-mode-auto").performScrollTo().performClick()
        ui.onNodeWithTag("automation-temperature-trigger").assertDoesNotExist()
        capture("multi-device-auto-editor-dark")
        ui.onNodeWithTag("automation-mode-manual").performScrollTo().performClick()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==1&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single()
        assertEquals(TriggerType.MANUAL,rule.triggerType);assertNull(rule.temperatureThreshold)
        assertEquals(setOf(ComponentType.DRIVER_SEAT,ComponentType.SUNSHADE,ComponentType.STEERING_WHEEL),rule.actions.map {it.component}.toSet())
        ui.onNodeWithTag("automation-condition-${rule.id}").performScrollTo().assertTextContains("Вручную",substring=true)
        ui.onNodeWithTag("automation-run-${rule.id}").performScrollTo().assertIsNotEnabled()
        capture("multi-device-card-dark")
        ui.onNodeWithTag("automation-edit-${rule.id}").performScrollTo().performClick()
        ui.onNodeWithTag("automation-remove-driver_seat").performScrollTo().performClick()
        ui.onNodeWithTag("automation-driver_seat-actions").assertDoesNotExist()
        ui.onNodeWithTag("automation-mode-auto").performScrollTo().performClick()
        ui.onNodeWithTag("automation-event-movement").performScrollTo().performClick()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().singleOrNull()?.let {it.actions.size==2&&it.triggerType==TriggerType.SESSION_START}==true&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("automation-condition-${rule.id}").performScrollTo().assertTextContains("после начала движения",substring=true)
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)}
        capture("multi-device-card-light")
        assertEquals(carBefore,dependencies.vehicle.state.value);assertEquals(seatsBefore,dependencies.seats.state.value)
    }
    @Test fun temperatureSliderSupportsTouchLimitsSignedValuesAndSavedThreshold() {
        ui.onNodeWithTag("automation-create").performClick()
        ui.onNodeWithTag("automation-name").performTextReplacement("Подогрев в мороз")
        ui.onNodeWithTag("automation-mode-temperature").performScrollTo().performClick()
        ui.onNodeWithTag("automation-threshold-value").assertTextEquals("+5 °C")
        ui.onNodeWithTag("automation-threshold").performScrollTo().performTouchInput {click(center)}
        ui.onNodeWithTag("automation-threshold-value").assertTextEquals("0 °C")
        ui.onNodeWithTag("automation-threshold").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(-60f)}
        ui.onNodeWithTag("automation-threshold-minus").assertIsNotEnabled()
        ui.onNodeWithTag("automation-threshold-plus").performClick()
        ui.onNodeWithTag("automation-threshold-value").assertTextEquals("-59 °C")
        ui.onNodeWithTag("automation-threshold").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(60f)}
        ui.onNodeWithTag("automation-threshold-plus").assertIsNotEnabled()
        ui.onNodeWithTag("automation-threshold").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(-12f)}
        ui.onNodeWithTag("automation-above").performClick().assertIsSelected()
        ui.onNodeWithTag("automation-threshold-value").assertTextEquals("-12 °C")
        ui.onNodeWithTag("automation-below").performClick().assertIsSelected()
        capture("threshold-slider-dark")
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)}
        capture("threshold-slider-light")
        ui.onNodeWithTag("automation-component-steering_wheel").performScrollTo().performClick()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().singleOrNull()?.temperatureThreshold== -12.0&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val rule=rules().single();assertEquals(TriggerType.BELOW,rule.triggerType)
        ui.onNodeWithTag("automation-edit-${rule.id}").performScrollTo().performClick()
        ui.onNodeWithTag("automation-threshold-value").performScrollTo().assertTextEquals("-12 °C")
    }
    @Test fun deviceToggleAddsRemovesAndRestoresSelectionWithoutTouchingOtherActions() {
        val handle=SavedStateHandle();val editor=AutomationEditorViewModel("new",dependencies.automations,handle)
        try {
            for(device in AutomationDevices.available) {
                val before=editor.state.value.draft
                editor.toggleDevice(device)
                assertEquals(before.actions+AutomationDevices.defaultAction(device),editor.state.value.draft.actions)
                editor.toggleDevice(device)
                assertEquals(before,editor.state.value.draft)
                assertEquals(before,AutomationDraft.restore(checkNotNull(handle.get<Bundle>("automation-draft"))))
                editor.toggleDevice(device)
            }
            editor.seat(SeatParameter.DRIVER_HEATING,SeatLevel.LEVEL_2)
            val seats=editor.state.value.draft.seats
            editor.toggleDevice(ComponentType.STEERING_WHEEL)
            assertEquals(seats,editor.state.value.draft.seats)
            assertTrue(editor.state.value.draft.actions.none {it.component==ComponentType.STEERING_WHEEL})
            assertTrue(rules().isEmpty())
        }finally {editor.viewModelScope.cancel()}
    }
    @Test fun editorDraftRoundtripsSavedStateAndCancelDoesNotCreateRule() {
        val handle=SavedStateHandle();val editor=AutomationEditorViewModel("new",dependencies.automations,handle)
        editor.name("x".repeat(60));editor.seat(SeatParameter.DRIVER_HEATING,SeatLevel.LEVEL_2)
        editor.addDevice(ComponentType.STEERING_WHEEL);editor.addDevice(ComponentType.SUNSHADE);editor.trigger(TriggerType.SESSION_START)
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
    @Test fun fourSeatsAreSeparateAndRemovingOneKeepsTheOthersAndDraftRestore() {
        val handle=SavedStateHandle();val editor=AutomationEditorViewModel("new",dependencies.automations,handle)
        try {
            editor.name("Каждому своё")
            editor.seat(SeatParameter.DRIVER_HEATING,SeatLevel.LEVEL_1)
            editor.seat(SeatParameter.PASSENGER_VENTILATION,SeatLevel.LEVEL_2)
            editor.seat(SeatParameter.REAR_LEFT_HEATING,SeatLevel.LEVEL_3)
            editor.seat(SeatParameter.REAR_RIGHT_HEATING,SeatLevel.OFF)
            val before=editor.state.value.draft
            assertEquals(4,before.actions.size);assertTrue(AutomationValidator.errors(before.rule()).isEmpty())
            editor.toggleDevice(ComponentType.PASSENGER_SEAT)
            val after=editor.state.value.draft
            assertEquals(before.actions.filterNot {it.component==ComponentType.PASSENGER_SEAT},after.actions)
            assertEquals(after,AutomationDraft.restore(checkNotNull(handle.get<Bundle>("automation-draft"))))
            // Old in-progress editor bundles also convert once, without inventing actions for untouched seats.
            val legacy=before.bundle().apply {putStringArrayList("devices",arrayListOf("SEATS"))}
            assertEquals(before,AutomationDraft.restore(legacy))
            assertTrue(rules().isEmpty())
        }finally {editor.viewModelScope.cancel()}
    }
    @Test fun launchEventIsIndependentOfTemperatureAndPersistsInEachRule() {
        val other=runBlocking {dependencies.automations.save(AutomationRule(name="В движении",triggerType=TriggerType.SESSION_START,
            actions=listOf(SteeringWheelAction.HEAT_ON),launchEvent=LaunchEvent.MOVEMENT),true)}
        ui.onNodeWithTag("automation-create").performClick()
        ui.onNodeWithTag("automation-name").performTextReplacement("Прогрев до поездки")
        ui.onNodeWithTag("automation-mode-temperature").performScrollTo().performClick()
        ui.onNodeWithTag("automation-event-engine_start").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("automation-event-movement").performClick().assertIsSelected()
        ui.onNodeWithTag("automation-event-engine_start").performClick().assertIsSelected()
        capture("live-launch-event-dark")
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.LIGHT)};capture("live-launch-event-light")
        ui.runOnIdle {dependencies.theme.setMode(ThemeMode.DARK)}
        ui.onNodeWithTag("automation-component-steering_wheel").performScrollTo().performClick()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().size==2&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        val saved=rules().single {it.id!=other.id}
        assertEquals(LaunchEvent.ENGINE_START,saved.launchEvent);assertEquals(TriggerType.BELOW,saved.triggerType)
        assertEquals(5.0,saved.temperatureThreshold!!,0.0);assertEquals(other,rules().single {it.id==other.id})
        ui.onNodeWithTag("automation-condition-${saved.id}").performScrollTo().assertTextContains("при запуске двигателя",substring=true)
        ui.onNodeWithTag("automation-edit-${saved.id}").performScrollTo().performClick()
        ui.onNodeWithTag("automation-event-engine_start").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("automation-event-movement").performClick()
        ui.onNodeWithTag("automation-mode-auto").performScrollTo().performClick()
        ui.onNodeWithTag("automation-save").performClick()
        ui.waitUntil(5000){rules().single {it.id==saved.id}.launchEvent==LaunchEvent.MOVEMENT&&ui.onAllNodesWithTag("screen-automations").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(other,rules().single {it.id==other.id})
    }
}
