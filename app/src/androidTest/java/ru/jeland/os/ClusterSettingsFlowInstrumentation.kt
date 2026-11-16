package ru.jeland.os

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*

class ClusterSettingsFlowInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun compactSummaryOpensPreviewThenDesignThenColorAndHidesExtrasForStock() {
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(previous.copy(clusterDesign=ClusterDesign.CLASSIC,clusterColorMode=ClusterColorMode.MANUAL))}
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-settings").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("cluster-design-classic").assertDoesNotExist()
            ui.onNodeWithTag("cluster-color-picker-open").assertDoesNotExist()
            ui.onNodeWithTag("open-cluster-preview").assertTextContains("Настроить").performClick()
            ui.onNodeWithTag("cluster-preview-page").assertIsDisplayed()
            val captures=File(ui.activity.getExternalFilesDir(null),"qa-cluster-settings-132").apply {mkdirs()}
            fun capture(name:String) {
                File(captures,"$name.png").outputStream().use {
                    ui.onNodeWithTag("cluster-preview-page").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
                }
            }
            capture("preview")
            val stage=ui.onNodeWithTag("cluster-stage").getUnclippedBoundsInRoot().top
            val designs=ui.onNodeWithTag("cluster-design-classic").getUnclippedBoundsInRoot().top
            val color=ui.onNodeWithTag("cluster-color-picker-open").getUnclippedBoundsInRoot().top
            val statistics=ui.onNodeWithTag("cluster-statistics-settings").getUnclippedBoundsInRoot().top
            assertTrue(stage<designs&&designs<color&&color<statistics)
            ui.onNodeWithTag("cluster-color-picker-open").performScrollTo().assertTextContains("Выбрать цвет")
            ui.onNodeWithTag("cluster-trip-since_reset").performScrollTo().performClick().assertIsSelected()
            capture("options")
            ui.runOnIdle {assertEquals(ClusterTripPeriod.SINCE_RESET,app.preferences.state.value.clusterTripPeriod)}
            for(design in listOf(ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                ui.onNodeWithTag("cluster-design-${design.name.lowercase()}").performScrollTo().performClick()
                ui.onNodeWithTag("cluster-trip-since_reset").performScrollTo().assertIsSelected()
            }
            ui.onNodeWithTag("cluster-design-original").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-color-picker-open").assertDoesNotExist()
            ui.onNodeWithTag("cluster-statistics-settings").assertDoesNotExist()
            ui.onNodeWithTag("show-cluster-output").assertDoesNotExist()
            ui.onNodeWithTag("restore-stock-cluster").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("leave-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().assertIsDisplayed()
        }finally {ui.runOnIdle {app.preferences.change(previous)}}
    }
}
