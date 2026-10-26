package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import java.io.File

class AlternativeAppearanceInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=(ui.activity.application as JelandApplication).container
    private var original=InterfacePreferences()
    private var theme=ThemeMode.AUTO
    private var live=false
    @Before fun prepare() {ui.runOnIdle {
        original=app.preferences.state.value;theme=app.theme.mode.value;live=!app.car.link.value.demo
        app.car.useVehicle(false)
    }}
    @After fun restore() {ui.runOnIdle {
        val keep=InstrumentationRegistry.getArguments().getString("keepPreview")=="true"
        app.preferences.change(if(keep)original.copy(style=DashboardStyle.MINIMAL)else original)
        app.theme.setMode(if(keep)ThemeMode.DARK else theme)
        app.car.useVehicle(live)
    }}
    private fun capture(name:String) {
        val image=ui.onRoot().captureToImage().asAndroidBitmap()
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-alternative-appearance").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun alternateLayoutReusesLiveControlsAndSwitchesBackToClassic() {
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithTag("style-classic").performScrollTo().performClick()
        val menuTags=listOf("home","music","navigation","car","automations","apps","settings")
        val classicMenu=menuTags.associateWith {ui.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot}
        ui.onNodeWithTag("style-minimal").performScrollTo().performClick()
        ui.onNodeWithTag("style-minimal").assertIsSelected()
        menuTags.forEach {assertEquals("Menu geometry stays identical for $it",classicMenu.getValue(it),ui.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot)}
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("minimal-home").assertIsDisplayed()
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.waitForIdle();capture(mode.name.lowercase())
            ui.onNodeWithTag("vehicle-body").assertIsDisplayed()
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            ui.onNodeWithTag("fuel-widget").assertIsDisplayed()
            ui.onNodeWithTag("battery-widget").assertIsDisplayed()
            ui.onNodeWithTag("pressure-summary").assertDoesNotExist()
            ui.onNodeWithTag("pressure-car-outline",useUnmergedTree=true).assertDoesNotExist()
            val clock=ui.onNodeWithTag("hero-clock").fetchSemanticsNode().boundsInRoot
            val temperature=ui.onNodeWithTag("outside-temperature",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertEquals("Temperature is level with the time",clock.center.y,temperature.center.y,2f)
            val car=ui.onNodeWithTag("vehicle-body").fetchSemanticsNode().boundsInRoot
            val carBand=ui.onNodeWithTag("vehicle-status-card").fetchSemanticsNode().boundsInRoot
            assertEquals("Main car is centered across the content area",carBand.center.x,car.center.x,1f)
            for(wheel in Wheel.entries) {
                val label=ui.onNodeWithTag("tire-${wheel.name.lowercase()}").fetchSemanticsNode().boundsInRoot
                val target=ui.onNodeWithTag("wheel-${wheel.name.lowercase()}").fetchSemanticsNode().boundsInRoot
                assertEquals("Pressure is level with ${wheel.title}",target.center.y,label.center.y,1f)
                assertTrue(if(wheel.left)label.center.x<target.center.x else label.center.x>target.center.x)
            }
            ui.onNodeWithTag("wheel-sensor-lines").assertDoesNotExist()
            val navigation=ui.onNodeWithTag("minimal-navigation-widget").fetchSemanticsNode().boundsInRoot
            val primary=ui.onNodeWithTag("minimal-primary-content").fetchSemanticsNode().boundsInRoot
            val info=ui.onNodeWithTag("minimal-info-stack").fetchSemanticsNode().boundsInRoot
            assertTrue("Navigation sits to the left of the information stack",navigation.right<=info.left)
            assertEquals(.7f,primary.width/(primary.width+info.width),.005f)
            assertEquals(navigation.top,info.top,1f)
            for(tag in listOf("clock-weather-panel","music-widget")) {
                val bounds=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag stays in the right column",bounds.left>=info.left&&bounds.right<=info.right)
            }
            for(tag in listOf("fuel-widget","battery-widget")) {
                val bounds=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag stays beside the main car",bounds.left>=carBand.left&&bounds.right<=carBand.right)
            }
            val menuEdge=menuTags.maxOf {ui.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot.right}
            for(tag in listOf("minimal-navigation-widget","clock-weather-panel","music-widget","fuel-widget","battery-widget","vehicle-body","quick-actions-widget","favorite-apps-widget")) {
                assertTrue("$tag stays beside the menu",ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left>=menuEdge)
            }
            repeat(4) {ui.onNodeWithTag("favorite-home-card-$it").assertIsDisplayed()}
            ui.onNodeWithTag("favorite-home-card-4").assertDoesNotExist()
        }
        for((tag,popup) in listOf("fuel-widget" to "fuel","battery-widget" to "battery")) {
            ui.onNodeWithTag(tag).performClick()
            ui.onNodeWithTag("popup-$popup").assertIsDisplayed()
            ui.onNodeWithTag("close-popup").performClick()
        }
        val lf=ui.onNodeWithTag("wheel-front_left").fetchSemanticsNode().boundsInRoot.center
        val rf=ui.onNodeWithTag("wheel-front_right").fetchSemanticsNode().boundsInRoot.center
        val lr=ui.onNodeWithTag("wheel-rear_left").fetchSemanticsNode().boundsInRoot.center
        assertTrue("Left side stays left after rotating body and targets together",lf.x<rf.x)
        assertTrue("Front is above rear",lf.y<lr.y)
        for(wheel in Wheel.entries) {
            ui.onNodeWithTag("wheel-${wheel.name.lowercase()}").performClick()
            ui.onNodeWithTag("popup-tire_${wheel.name.lowercase()}").assertIsDisplayed()
            ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("tire-${wheel.name.lowercase()}").performClick()
            ui.onNodeWithTag("popup-tire_${wheel.name.lowercase()}").assertIsDisplayed()
            ui.onNodeWithTag("close-popup").performClick()
        }
        val shadeBefore=app.vehicle.state.value.shadeUiPosition
        ui.onNodeWithTag("quick-shade").performClick()
        ui.onNodeWithTag("popup-shade").assertIsDisplayed()
        ui.onNodeWithTag("shade-preview").assertDoesNotExist()
        val shadeBounds=ui.onNodeWithTag("popup-shade").fetchSemanticsNode().boundsInRoot
        val rootBounds=ui.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Shade selector stays compact",shadeBounds.width<rootBounds.width*.7f&&shadeBounds.height<rootBounds.height*.25f)
        for(shade in listOf(Shade.HALF,Shade.OPEN,Shade.CLOSED)) {
            ui.onNodeWithTag("shade-${shade.name.lowercase()}").performClick()
            ui.waitUntil(5000){app.vehicle.state.value.shadeUiPosition==shade}
            ui.onNodeWithTag("shade-${shade.name.lowercase()}").assertIsSelected()
            ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("vehicle-shade-state").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,shade.title))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(when(shade){Shade.CLOSED->0f;Shade.HALF->.5f;Shade.OPEN->1f},0f..1f)))
            capture("shade-on-car-${shade.name.lowercase()}")
            ui.onNodeWithTag("quick-shade").performClick()
        }
        capture("shade-compact")
        ui.runOnIdle {shadeBefore?.let(app.vehicle::setShade)}
        ui.onNodeWithTag("close-popup").performClick()
        val openingsBefore=app.vehicle.state.value.openings
        try {
            ui.runOnIdle {VehicleOpening.entries.filterNot {it in openingsBefore}.forEach(app.vehicle::simulateOpening)}
            ui.waitForIdle();capture("j7-openings")
        } finally {
            ui.runOnIdle {VehicleOpening.entries.filterNot {it in openingsBefore}.forEach(app.vehicle::simulateOpening)}
        }
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithTag("style-classic").performScrollTo().performClick()
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("hero-banner").assertIsDisplayed()
        ui.onNodeWithTag("minimal-home").assertDoesNotExist()
    }
}
