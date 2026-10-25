package ru.jeland.os

import android.graphics.Bitmap
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
import ru.jeland.os.data.SystemStartupState
import ru.jeland.os.ui.SystemStartupSettings
import ru.jeland.os.ui.theme.*
import java.io.File

/** Pure UI state: this test never opens the real system permission screen. */
class SystemStartupUiInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun togglePermissionPendingReadyAndOffInBothThemes() {
        ui.waitUntil(15000) {ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        var state by mutableStateOf(SystemStartupState())
        var permissionClicks=0
        for(dark in listOf(true,false)) {
            ui.runOnIdle {state=SystemStartupState()}
            ui.activityRule.scenario.onActivity {activity->activity.setContent {JelandTheme(dark) {
                Box(Modifier.fillMaxWidth().background(Jeland.colors.background).padding(24.dp).testTag("startup-preview")) {
                    SystemStartupSettings(state,{state=state.copy(enabled=it)},{permissionClicks++})
                }
            }}}
            ui.waitUntil(10000) {ui.onAllNodesWithTag("setting-system-startup").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("setting-system-startup").assertIsOff().performClick().assertIsOn()
            ui.onNodeWithTag("startup-permission").assertExists().performClick()
            assertTrue(permissionClicks>0)
            ui.onNodeWithTag("startup-ready").assertDoesNotExist()
            ui.runOnIdle {state=state.copy(permissionScreenUnavailable=true)}
            ui.onNodeWithText("Проверить разрешение").assertExists()
            ui.onNodeWithText("На этом ГУ нет экрана разрешений.",substring=true).assertExists()
            ui.runOnIdle {state=state.copy(permissionGranted=true,permissionScreenUnavailable=false)}
            ui.onNodeWithTag("startup-ready").assertExists()
            ui.onNodeWithTag("startup-permission").assertDoesNotExist()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.9.19").apply {mkdirs()}
            File(folder,"startup-${if(dark)"dark"else"light"}.png").outputStream().use {
                ui.onNodeWithTag("startup-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
            ui.onNodeWithTag("setting-system-startup").performClick().assertIsOff()
            ui.onNodeWithTag("startup-ready").assertDoesNotExist()
        }
    }
}
