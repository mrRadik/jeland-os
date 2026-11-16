package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import java.io.File

/** Uses the real MainActivity, real native map and normal navigation; no replacement scene. */
class MapStartupInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun normalStartupAndMapPageTransfersStayAlive() {
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-map-startup-109").apply {mkdirs()}
        fun capture(name:String) {
            Thread.sleep(1800)
            ui.onNodeWithTag("comaps-map").assertExists()
            ui.onNodeWithTag("comaps-map").assertIsDisplayed()

        }
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(previous.copy(style=DashboardStyle.MINIMAL))}
            ui.waitUntil(20000) {app.navigation.comaps.ready.value}
            capture("home")
            ui.onNodeWithTag("nav-navigation").performScrollTo().performClick()
            capture("navigation")
            ui.onNodeWithTag("nav-home").performScrollTo().performClick()
            capture("home-return")
        }finally {ui.runOnIdle {app.preferences.change(previous)}}
    }
}
