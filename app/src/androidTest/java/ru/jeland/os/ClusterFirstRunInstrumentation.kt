package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*
import java.io.File

class ClusterFirstRunInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun firstRunPersistsClusterChoicesAcrossBackNavigationAndCompletionWithoutProjecting() {
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value;val setup=app.firstRun.state.value
        assumeTrue(app.clusterProjection.applied.value==null)
        try {
            ui.runOnIdle {app.preferences.change(previous.copy(clusterDesign=ClusterDesign.ORIGINAL));app.firstRun.reopen()}
            ui.onNodeWithTag("first-run").assertIsDisplayed()
            ui.onNodeWithTag("language-en").performClick()
            ui.onNodeWithTag("setup-next").performClick() // appearance
            ui.onNodeWithTag("setup-next").performClick() // cluster
            ui.onNodeWithTag("setup-cluster").assertExists()
            ui.onNodeWithText("Instrument cluster").assertExists()
            ui.onNodeWithTag("cluster-design-original").assertIsSelected()
            ui.onNodeWithTag("cluster-color-picker-open").assertDoesNotExist()
            ui.onNodeWithTag("cluster-design-classic").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-drive").performScrollTo().performClick()
            ui.runOnIdle {
                assertEquals(ClusterDesign.CLASSIC,app.preferences.state.value.clusterDesign)
                assertEquals(ClusterColorMode.DRIVE_MODE,app.preferences.state.value.clusterColorMode)
                assertNull(app.clusterProjection.applied.value)
            }
            ui.onNodeWithTag("setup-back").performClick()
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithTag("cluster-design-classic").performScrollTo().assertIsSelected()
            ui.onNodeWithTag("cluster-color-manual").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-picker-open").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-hex").performTextReplacement("A435F0")
            ui.onNodeWithTag("cluster-color-apply").performClick()
            ui.onNodeWithTag("cluster-design-original").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-picker-open").assertDoesNotExist()
            ui.onNodeWithTag("cluster-design-sport").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-drive").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-design-original").performScrollTo()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-122").apply {mkdirs()}
            val image=ui.onNodeWithTag("first-run").captureToImage().asAndroidBitmap()
            File(folder,"setup-cluster-en.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            repeat(4){ui.onNodeWithTag("setup-next").performClick()}
            ui.onNodeWithText("You're ready").assertExists()
            ui.onNodeWithTag("setup-next").performClick()
            ui.onNodeWithTag("first-run").assertDoesNotExist()
            ui.runOnIdle {
                assertTrue(app.firstRun.state.value.complete);assertNull(app.clusterProjection.applied.value)
                val stored=PersistentInterfaceRepository(AndroidSettingsStore(ui.activity)).state.value
                assertEquals(ClusterDesign.SPORT,stored.clusterDesign)
                assertEquals(ClusterColorMode.DRIVE_MODE,stored.clusterColorMode)
                assertEquals(0xFFA435F0.toInt(),stored.clusterAccent.darkArgb)
            }
        }finally {ui.runOnIdle {
            app.preferences.change(previous);app.firstRun.language(setup.language)
            if(setup.complete)app.firstRun.finish()else app.firstRun.reopen()
        }}
    }
}
