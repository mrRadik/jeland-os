package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import java.io.File

/** Visual experiment only: existing components, no vehicle commands or update checks. */
class ReadabilityPreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun previewBothLayoutsAndThemesAtHeadUnitSize() {
        val app=(ui.activity.application as JelandApplication).container
        val prefs=app.preferences.state.value;val theme=app.theme.mode.value;val live=!app.car.link.value.demo
        fun capture(name:String) {
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-readability-preview").apply {mkdirs()}
            File(folder,"$name.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        try {
            ui.runOnIdle {app.car.useVehicle(false)}
            for(style in DashboardStyle.entries)for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {app.preferences.change(prefs.copy(style=style));app.theme.setMode(mode)}
                ui.onNodeWithTag("nav-home").performClick();ui.waitForIdle()
                val bar=ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot
                for(tag in listOf("driver-seat","driver-minus","driver-temp","driver-plus","expand-climate","bar-auto","bar-ac",
                    "bar-recirculation","bar-front-defrost","bar-rear-defrost","bar-windshield-heat","passenger-minus","passenger-temp","passenger-plus","passenger-seat")) {
                    val bounds=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                    assertTrue("$tag fits climate bar in $style/$mode",bounds.left>=bar.left&&bounds.right<=bar.right&&bounds.top>=bar.top&&bounds.bottom<=bar.bottom)
                }
                for(tag in listOf("nav-settings","quick-steering","quick-shade","quick-cameras","quick-engineering"))ui.onNodeWithTag(tag).assertIsDisplayed()
                capture("${style.name.lowercase()}-${mode.name.lowercase()}")
                ui.onNodeWithTag("expand-climate").performClick()
                ui.onNodeWithTag("popup-climate").assertIsDisplayed();capture("climate-${style.name.lowercase()}-${mode.name.lowercase()}")
                ui.onNodeWithTag("close-popup").performClick()
            }
        }finally {ui.runOnIdle {app.preferences.change(prefs);app.theme.setMode(theme);app.car.useVehicle(live)}}
    }
}
