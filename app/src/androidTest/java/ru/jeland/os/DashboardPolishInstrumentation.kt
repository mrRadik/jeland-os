package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import java.io.File

class DashboardPolishInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Test fun centredVehicleFuelPlacementCompactCardsAndLargerDock() {
        val app=container();val preferences=app.preferences.state.value;val theme=app.theme.mode.value
        ui.runOnIdle {app.car.useVehicle(false);app.preferences.change(preferences.copy(showTires=true,showFuel=true,showFavorites=true))}
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
        try {
            for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {app.theme.setMode(mode)};ui.waitForIdle()
                val content=ui.onNodeWithTag("home-scroll").fetchSemanticsNode().boundsInRoot
                val hero=ui.onNodeWithTag("hero-banner").fetchSemanticsNode().boundsInRoot
                val header=ui.onNodeWithTag("jeland-header").fetchSemanticsNode().boundsInRoot
                assertEquals("Hero begins at screen top, behind the header",0f,hero.top,1f)
                assertEquals("Header overlays the hero, without a separate strip",hero.top,header.top,1f)
                assertTrue("Header fits inside hero",header.bottom<hero.bottom)
                val homeButton=ui.onNodeWithTag("nav-home").fetchSemanticsNode().boundsInRoot
                assertTrue("Sidebar buttons do not cover the logo",homeButton.top>=header.bottom)
                val menu=listOf("home","music","navigation","car","automations","apps","settings")
                    .map {ui.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot}
                menu.zipWithNext().forEach {(previous,next)->
                    assertEquals("Fixed 108dp menu item height",homeButton.height,next.height,1f)
                    assertEquals("Fixed 12dp gap, independent of screen height",homeButton.height/9f,next.top-previous.bottom,1f)
                }
                val dock=ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot
                val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue("Unused rail space stays below the menu",menu.last().bottom<root.bottom-homeButton.height)
                assertTrue("Compact climate dock stays on screen",dock.bottom<=root.bottom&&dock.height/root.height<.085f)
                val car=ui.onNodeWithTag("vehicle-status-card").fetchSemanticsNode().boundsInRoot
                assertTrue("Vehicle card absorbs spare height without taking over the screen",car.height/content.height in .18f.. .5f)
                val favorites=ui.onNodeWithTag("favorite-apps-widget").fetchSemanticsNode().boundsInRoot
                assertEquals("No unused space below the last home card",content.bottom,favorites.bottom,1f)
                val fuel=ui.onNodeWithTag("fuel-widget").fetchSemanticsNode().boundsInRoot
                val rearTop=ui.onNodeWithTag("tire-rear_right").fetchSemanticsNode().boundsInRoot
                val rearBottom=ui.onNodeWithTag("tire-rear_left").fetchSemanticsNode().boundsInRoot
                assertTrue("Fuel $fuel must fit between rear tyres $rearTop / $rearBottom",fuel.top>=rearTop.bottom&&fuel.bottom<=rearBottom.top)
                val battery=ui.onNodeWithTag("battery-widget").fetchSemanticsNode().boundsInRoot
                val frontTop=ui.onNodeWithTag("tire-front_right").fetchSemanticsNode().boundsInRoot
                val frontBottom=ui.onNodeWithTag("tire-front_left").fetchSemanticsNode().boundsInRoot
                assertTrue("Battery fits between front tyres",battery.top>=frontTop.bottom&&battery.bottom<=frontBottom.top)
                ui.onNodeWithTag("battery-voltage",useUnmergedTree=true).assertTextEquals("14,6 В")
                ui.onNodeWithTag("quick-shade").assertIsDisplayed();ui.onNodeWithTag("passenger-plus").assertIsDisplayed()
                val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.7.7").apply {mkdirs()}
                File(folder,"home-${mode.name.lowercase()}.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            ui.onNodeWithTag("fuel-widget").performClick();ui.onNodeWithTag("popup-fuel").assertIsDisplayed();ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("battery-widget").performClick();ui.onNodeWithTag("popup-battery").assertIsDisplayed()
            ui.onNodeWithText("Температура АКБ").assertIsDisplayed()
            ui.onNodeWithText("13 °C").assertIsDisplayed()
            ui.onNodeWithTag("close-popup").performClick()
            ui.onNodeWithTag("nav-settings").performClick()
            ui.onNodeWithTag("vehicle-write-unlock").assertDoesNotExist()
        }finally {ui.runOnIdle {app.preferences.change(preferences);app.theme.setMode(theme)}}
    }
}
