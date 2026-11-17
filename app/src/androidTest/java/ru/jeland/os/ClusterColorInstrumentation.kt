package ru.jeland.os

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.PersistentInterfaceRepository
import ru.jeland.os.ui.cluster.*
import ru.jeland.os.ui.theme.*
import java.io.File

class ClusterColorInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun pickerAcceptsExactHexAndAutoColoursChangeWithDriveMode() {
        val store=MemorySettingsStore()
        val repository=PersistentInterfaceRepository(store)
        var prefs by mutableStateOf(repository.state.value)
        var mode by mutableStateOf<ClusterDriveMode?>(null)
        var dark by mutableStateOf(true)
        ui.runOnUiThread {ui.activity.setContent {JelandTheme(dark) {
            Column(Modifier.fillMaxSize().background(Jeland.colors.background).padding(20.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1920f/720f).testTag("colour-stage")) {
                    ClusterArtwork(ClusterPanelState(speed=90.0,rpm=2100.0,gear="D",rangeKm=442.0,fuel=.7,driveMode=mode),
                        ClusterDesign.CLASSIC,dark,"12:36",Modifier.fillMaxSize(),accent=prefs.clusterAccentFor(mode))
                }
                ClusterColorSettings(prefs,mode){repository.change(it);prefs=repository.state.value}
            }
        }}}
        val folder=File(ui.activity.getExternalFilesDir(null),"cluster-colour-preview").apply {mkdirs()}
        fun capture(name:String) {
            val image=(if(name=="picker")ui.onNodeWithTag("cluster-color-picker")else ui.onRoot()).captureToImage().asAndroidBitmap()
            File(folder,"$name.png").outputStream().use {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.onNodeWithTag("cluster-color-picker-open").performClick()
        ui.onNodeWithTag("cluster-color-hue").performTouchInput {swipeLeft()}
        ui.onNodeWithTag("cluster-color-saturation").performTouchInput {click(center)}
        ui.onNodeWithTag("cluster-color-hex").performTextReplacement("A435F0")
        capture("picker")
        ui.onNodeWithTag("cluster-color-apply").performClick()
        ui.runOnIdle {
            assertEquals(0xFFA435F0.toInt(),prefs.clusterAccent.darkArgb)
            assertEquals(prefs,PersistentInterfaceRepository(store).state.value)
        }
        capture("custom-dark")
        ui.onNodeWithTag("cluster-color-drive").performClick()
        ui.runOnIdle {assertEquals(ClusterColorMode.DRIVE_MODE,prefs.clusterColorMode)}
        ClusterDriveMode.entries.forEach {ui.onNodeWithText(it.title,useUnmergedTree=true).assertIsDisplayed()}
        ui.onNodeWithTag("cluster-drive-mode-status").assertTextContains("Режим автомобиля пока не получен",substring=true)
        for(value in ClusterDriveMode.entries) {
            ui.runOnIdle {mode=value}
            ui.onNodeWithTag("cluster-drive-mode-status").assertTextContains(value.title,substring=true)
            ui.runOnIdle {assertEquals(value.accent,prefs.clusterAccentFor(mode))}
            capture("auto-${value.name.lowercase()}")
        }
        ui.onNodeWithTag("cluster-color-manual").performClick()
        ui.runOnIdle {assertEquals(0xFFA435F0.toInt(),prefs.clusterAccentFor(mode).darkArgb);dark=false}
        capture("custom-light")
        ui.onNodeWithTag("cluster-color-picker-open").performClick()
        ui.onNodeWithTag("cluster-color-hex").performTextReplacement("FF")
        ui.onNodeWithTag("cluster-color-apply").assertIsNotEnabled()
    }
}
