package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.ThemeMode
import java.io.File

class MusicDesignInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun compactWidgetAndReferencePlayerFitBothThemes() {
        val app=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {app.car.useVehicle(false)}
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.onNodeWithTag("nav-home").performClick()
            val navigation=ui.onNodeWithTag("navigation-widget").fetchSemanticsNode().boundsInRoot
            val music=ui.onNodeWithTag("music-widget").fetchSemanticsNode().boundsInRoot
            assertEquals("Navigation matches music height",music.height,navigation.height,1f)
            assertEquals("Navigation aligns with music top",music.top,navigation.top,1f)
            val previous=ui.onNodeWithTag("media-previous").fetchSemanticsNode().boundsInRoot
            val play=ui.onNodeWithTag("media-play").fetchSemanticsNode().boundsInRoot
            assertTrue("Primary action larger than previous/next",play.height>previous.height)
            capture("home-${mode.name.lowercase()}")
            ui.onNodeWithTag("nav-music").performClick()
            ui.onNodeWithTag("music-main-artwork").assertIsDisplayed()
            ui.onNodeWithTag("media-play").assertIsDisplayed()
            ui.onNodeWithTag("music-active-source").assertIsDisplayed()
            ui.onNodeWithTag("media-volume").assertIsDisplayed()
            val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
            val player=ui.onNodeWithTag("full-music-player").fetchSemanticsNode().boundsInRoot
            assertTrue("Player fits above climate dock",player.bottom<root.bottom)
            capture("music-${mode.name.lowercase()}")
        }
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val file=File(ui.activity.getExternalFilesDir(null),"qa-0.7.4/$name.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
