package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import ru.jeland.os.core.Shade
import ru.jeland.os.core.ThemeMode
import ru.jeland.os.ui.SunshadeRoofPreview
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class SunshadePreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun awaitPortrait() {
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    @Test fun shadePopupKeepsWorkingButtonsAndMatchesCommandImmediately() {
        val container=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {container.car.useVehicle(false);container.theme.setMode(ThemeMode.DARK);container.vehicle.setShade(Shade.CLOSED)}
        ui.onNodeWithTag("quick-shade").performClick()
        ui.onNodeWithTag("shade-closed").assertIsSelected()
        for(shade in listOf(Shade.HALF,Shade.OPEN,Shade.CLOSED)) {
            ui.onNodeWithTag("shade-${shade.name.lowercase()}").performScrollTo().performClick()
            ui.waitUntil(5000){container.vehicle.state.value.shade==shade}
            ui.onNodeWithTag("shade-${shade.name.lowercase()}").assertIsSelected()
            ui.onNodeWithTag("shade-state").assertTextEquals(shade.title)
        }
        ui.onNodeWithTag("shade-half").performClick();ui.waitForIdle()
        val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        val file=File(root,"qa-0.6.1/shade-popup.png").apply {parentFile!!.mkdirs()}
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        val dock=File(root,"qa-0.6.1/climate-dock.png")
        dock.outputStream().use {ui.onNodeWithTag("climate-bar").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun roofPreviewAnimatesThreePositionsAndUnknownIsNotHalfOpen() {
        var position by mutableStateOf<Shade?>(Shade.CLOSED)
        var dark by mutableStateOf(true)
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
            Box(Modifier.fillMaxWidth().padding(24.dp)){SunshadeRoofPreview(position)}
        }}}
        val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        for(shade in Shade.entries) {
            ui.runOnIdle {position=shade};ui.waitForIdle()
            val expected=when(shade){Shade.CLOSED->0f;Shade.HALF->.5f;Shade.OPEN->1f}
            ui.onNodeWithTag("shade-preview").assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(expected,0f..1f)))
            val file=File(root,"qa-0.6.1/shade-${shade.name.lowercase()}.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.runOnIdle {position=Shade.HALF;dark=false};ui.waitForIdle()
        val light=File(root,"qa-0.6.1/shade-half-light.png")
        light.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        ui.runOnIdle {position=null};ui.waitForIdle()
        ui.onNodeWithTag("shade-preview").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
        ui.onNodeWithText("Положение неизвестно").assertIsDisplayed()
    }
}
