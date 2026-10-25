package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

/** Focused local preview only. Fake seat states and popup callbacks; no vehicle SET. */
class SeatRingsPreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun levelsUnknownFeedbackAndDockLookCorrectInBothThemes() {
        ui.waitUntil(15000) {
            ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true
        }
        val app=(ui.activity.application as JelandApplication).container
        val model=MainViewModel(app)
        var opened:Popup?=null
        var previewClicks=0
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {activity->activity.setContent {
                JelandTheme(dark) {
                    Column(Modifier.fillMaxSize().background(Jeland.colors.background).testTag("seat-preview"),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp).testTag("seat-gallery"),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                            Text("Сиденья · ${if(dark)"тёмная"else"светлая"} тема",color=Jeland.colors.text,style=Jeland.heading)
                            for(level in 1..3)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.testTag("heat-case-$level"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SeatClimateButton(SeatSide.DRIVER,SeatChannel(heat=level)){previewClicks++}
                                    Text("Подогрев $level / 3",color=Jeland.colors.text,style=Jeland.caption)
                                }
                                Column(Modifier.testTag("vent-case-$level"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SeatClimateButton(SeatSide.PASSENGER,SeatChannel(ventilation=level)){previewClicks++}
                                    Text("Вентиляция $level / 3",color=Jeland.colors.text,style=Jeland.caption)
                                }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                Column(Modifier.testTag("seat-off-case"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SeatClimateButton(SeatSide.DRIVER,SeatChannel()){previewClicks++}
                                    Text("Выключено",color=Jeland.colors.muted,style=Jeland.caption)
                                }
                                Column(Modifier.testTag("seat-unknown-case"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    SeatClimateButton(SeatSide.PASSENGER,SeatChannel(3,3),heatKnown=false,ventilationKnown=false){previewClicks++}
                                    Text("Нет данных",color=Jeland.colors.muted,style=Jeland.caption)
                                }
                            }
                        }
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val density=LocalDensity.current
                            val scale=(maxWidth.value/1000f).coerceIn(.65f,1.8f)
                            CompositionLocalProvider(LocalDensity provides Density(density.density*scale,density.fontScale)) {
                                ClimateBottomBar(UiState(climate=ClimateState(power=true,fan=0),seats=SeatState(driver=SeatChannel(heat=2),passenger=SeatChannel(ventilation=3))),model){opened=it}
                            }
                        }
                    }
                }
            }}
            ui.waitUntil(10000) {ui.onAllNodesWithTag("heat-case-3").fetchSemanticsNodes().isNotEmpty()}
            val heat=ui.onNode(hasTestTag("driver-seat") and hasAnyAncestor(hasTestTag("heat-case-2")))
            heat.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Подогрев: 2 из 3; вентиляция: 0 из 3"))
            ui.onNode(hasTestTag("passenger-seat") and hasAnyAncestor(hasTestTag("seat-unknown-case")))
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Подогрев: нет данных; вентиляция: нет данных"))
            // Let the press ripple settle before taking reference images.
            ui.mainClock.advanceTimeBy(500)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.9.16").apply {mkdirs()}
            for((tag,name) in listOf("seat-gallery" to "seats","climate-bar" to "dock")) {
                File(folder,"$name-${if(dark)"dark"else"light"}.png").outputStream().use {
                    ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
                }
            }
        }
        // Do all press checks after both themes' screenshots: a remembered ripple
        // must not carry from the dark preview into the light preview.
        ui.onNode(hasTestTag("driver-seat") and hasAnyAncestor(hasTestTag("heat-case-2"))).performClick()
        assertTrue(previewClicks>0)
        ui.onNode(hasTestTag("driver-seat") and hasAnyAncestor(hasTestTag("climate-bar"))).performClick()
        assertEquals(Popup.DRIVER,opened)
        ui.onNode(hasTestTag("passenger-seat") and hasAnyAncestor(hasTestTag("climate-bar"))).performClick()
        assertEquals(Popup.PASSENGER,opened)
    }
}
