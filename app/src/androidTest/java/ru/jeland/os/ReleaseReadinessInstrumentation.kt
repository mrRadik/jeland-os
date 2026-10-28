package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.JelandTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.AppLanguage
import java.io.File

class ReleaseReadinessInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String) {
        ui.waitForIdle()
        ui.waitUntil(5_000){ui.activity.hasWindowFocus()}
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(400) // Let the platform window transition finish before capturing pixels.
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-1.0.0").apply {mkdirs()}
        val bitmap=checkNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun openedDoorsAndLowFuelAreReadable() {
        val fuel=FuelState(4.0)
        ui.runOnUiThread {ui.activity.setContent {
            JelandTheme(true,DashboardStyle.CLASSIC) {
                Panel(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().padding(24.dp)) {
                    LowFuelNotification(fuel)
                    VehicleTopView(VehicleState(live=true,fuel=fuel,openings=setOf(VehicleOpening.FRONT_LEFT,VehicleOpening.REAR_RIGHT)),
                        Modifier.fillMaxWidth().weight(1f),portrait=true)
                    VehicleFuelIndicator(fuel,Modifier.fillMaxWidth(),gauge=true){}
                }}
            }
        }}
        ui.onNodeWithTag("low-fuel-warning").assertIsDisplayed();ui.waitForIdle();capture("opened-doors-and-low-fuel")
    }
    @Test fun productionFirstRunEnglishSettingsAndAutomaticVehicleConnection() {
        assertFalse("Run this check on the RELEASE variant",BuildConfig.DEMO_FEATURES)
        assertFalse(BuildConfig.DEBUG)
        val app=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {app.firstRun.reopen()}
        ui.onNodeWithTag("first-run").assertIsDisplayed()
        ui.onNodeWithTag("language-en").performClick()
        ui.onNodeWithText("App language").assertIsDisplayed();capture("setup-1-language-en")
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithTag("setup-style-MINIMAL").performClick()
        ui.onNodeWithTag("setup-theme-LIGHT").performClick();capture("setup-2-appearance-en")
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithText("Permissions").assertIsDisplayed();capture("setup-3-permissions-en")
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithText("Start with the system").assertIsDisplayed();capture("setup-4-startup-en")
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithText("Download maps").assertIsDisplayed();capture("setup-5-navigation-en")
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithText("You're ready").assertIsDisplayed()
        ui.onNodeWithTag("setup-next").performClick()
        ui.onNodeWithTag("first-run").assertDoesNotExist()
        assertTrue(app.firstRun.state.value.complete)
        ui.onNodeWithTag("media-title").assertTextEquals("Music")
        capture("home-release-en")
        assertFalse("No fake vehicle readings in release",app.car.link.value.demo)
        ui.runOnIdle {app.car.useVehicle(false)}
        assertFalse("Release cannot switch to DEMO",app.car.link.value.demo)
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithTag("settings-debug").assertDoesNotExist()
        ui.onNodeWithTag("open-demo-car").assertDoesNotExist()
        ui.onNodeWithTag("radio-permissions").assertDoesNotExist()
        ui.onNodeWithTag("map-catalog-source").assertDoesNotExist()
        capture("settings-en")
        ui.runOnIdle {app.firstRun.language(AppLanguage.RUSSIAN)}
        ui.onNodeWithTag("nav-home").performClick();capture("home-release-ru")
    }
}
