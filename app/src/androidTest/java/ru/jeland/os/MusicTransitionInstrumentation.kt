package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import ru.jeland.os.core.ThemeMode
import java.io.File

class MusicTransitionInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @After fun restoreClock(){ui.mainClock.autoAdvance=true}

    @Test fun pageInsetsAnimateWhileHeaderSidebarAndClimateStayInPlace() {
        val app=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {app.car.useVehicle(false);app.navigation.provider.voice.setEnabled(false)}
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.mainClock.autoAdvance=true
            ui.runOnIdle {app.theme.setMode(theme)}
            ui.onNodeWithTag("nav-home").performClick();ui.waitForIdle()
            val start=ui.onNodeWithTag("screen-content-frame").fetchSemanticsNode().boundsInRoot
            val sidebar=ui.onNodeWithTag("nav-music").fetchSemanticsNode().boundsInRoot
            val dock=ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot
            val header=ui.onNodeWithTag("jeland-header").fetchSemanticsNode().boundsInRoot
            ui.mainClock.autoAdvance=false
            ui.onNodeWithTag("nav-music").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.mainClock.advanceTimeBy(128)
            val middle=ui.onNodeWithTag("screen-content-frame").fetchSemanticsNode().boundsInRoot
            capture("${theme.name.lowercase()}-during")
            ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
            val end=ui.onNodeWithTag("screen-content-frame").fetchSemanticsNode().boundsInRoot
            assertTrue("Header space is interpolated, not inserted instantly: $start → $middle → $end",middle.top>start.top&&middle.top<end.top)
            assertTrue("Horizontal margins interpolate",middle.left>start.left&&middle.left<end.left)
            assertEquals(sidebar,ui.onNodeWithTag("nav-music").fetchSemanticsNode().boundsInRoot)
            assertEquals(dock,ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot)
            assertEquals(header,ui.onNodeWithTag("jeland-header").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithTag("full-music-player").assertIsDisplayed()
            ui.onNodeWithTag("media-play").assertIsDisplayed()
            capture("${theme.name.lowercase()}-music")
            ui.onNodeWithTag("nav-home").performClick()
            ui.mainClock.advanceTimeBy(450);ui.waitForIdle()
            assertEquals(start,ui.onNodeWithTag("screen-content-frame").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithTag("hero-banner").assertIsDisplayed()
            ui.onAllNodesWithTag("full-music-player").assertCountEquals(0)
            // Interrupted transitions must settle on the final destination without a stuck layer.
            ui.onNodeWithTag("nav-music").performClick();ui.mainClock.advanceTimeBy(64)
            ui.onNodeWithTag("nav-home").performClick();ui.mainClock.advanceTimeBy(64)
            ui.onNodeWithTag("nav-music").performClick();ui.mainClock.advanceTimeBy(500)
            ui.onAllNodesWithTag("full-music-player").assertCountEquals(1)
            assertEquals(dock,ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot)
        }
        ui.mainClock.autoAdvance=true
    }

    private fun capture(name:String) {
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
        File(folder,"transition-$name.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
