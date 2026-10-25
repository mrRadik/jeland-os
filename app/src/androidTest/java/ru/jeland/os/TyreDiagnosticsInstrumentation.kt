package ru.jeland.os

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.DiagnosticsExporter
import ru.jeland.os.ui.TireDetailsPopup
import ru.jeland.os.ui.theme.*
import java.io.File

/** Cached synthetic debug state only. No network, QNX, permission changes or real file export. */
class TyreDiagnosticsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun failure()=TyreTemperatureDiagnostics(active=true,phase=TyreReadPhase.GREETING,attempts=3,
        interfaceName="eth0",lastError="SocketTimeoutException: Read timed out",failedPhase=TyreReadPhase.GREETING,
        errorAtMillis=SystemClock.elapsedRealtime(),retryAtMillis=SystemClock.elapsedRealtime()+30_000,
        trace=TyreProtocolTrace(TyreReadPhase.GREETING,"<CRLF>",13,2,"QCONN\\r\\n<qconn-","51 43 4f 4e 4e"))
    @Test fun failedTransportIsReadableAndCollapsibleInBothThemes() {
        ui.waitUntil(15000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        var saved=0
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {activity->activity.setContent {JelandTheme(dark) {
                Column(Modifier.fillMaxSize().background(Jeland.colors.background).testTag("tyre-preview")
                    .verticalScroll(rememberScrollState()).padding(22.dp)) {
                    TireDetailsPopup(Wheel.FRONT_LEFT,VehicleState(live=true,tires=mapOf(Wheel.FRONT_LEFT to TireReading(2.4,null))),failure(),onSaveLogs={saved++},onClearLogs={})
                }
            }}}
            ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("Нет данных")
            ui.onNodeWithTag("tyre-debug-stage").assertTextContains("GREETING",substring=true)
            ui.onNodeWithTag("tyre-debug-error").assertTextEquals("SocketTimeoutException: Read timed out")
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.9.21").apply {mkdirs()}
            File(folder,"tyre-debug-${if(dark)"dark"else"light"}.png").outputStream().use {
                ui.onNodeWithTag("tyre-preview").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
            ui.onNodeWithTag("tyre-debug-save").performScrollTo().performClick()
            ui.onNodeWithTag("tyre-debug-toggle").performScrollTo().performClick()
            ui.onNodeWithTag("tyre-temperature-debug").assertDoesNotExist()
            ui.onNodeWithTag("tyre-debug-toggle").performClick()
            ui.onNodeWithTag("tyre-debug-error").assertExists()
        }
        assertEquals(2,saved)
    }
    @Test fun exportContainsBoundedTraceAndRawSampleWithoutInventingTemperature() {
        val state=failure().copy(payload="@a005\ndata:json:[0,0,0,255,0,0,0,0]",
            samples=mapOf(Wheel.FRONT_LEFT to TyreDebugSample(255,6,null,100)))
        val json=DiagnosticsExporter.tyreDiagnosticsJson(state)
        assertEquals("GREETING",json.getString("failedPhase"))
        assertEquals(13,json.getJSONObject("trace").getLong("rxBytes"))
        assertTrue(json.getJSONArray("samples").getJSONObject(0).isNull("celsius"))
        assertEquals(255,json.getJSONArray("samples").getJSONObject(0).getInt("raw"))
        assertEquals("elapsedRealtimeMillis",json.getString("clock"))
        assertEquals(2048,DiagnosticsExporter.tyreDiagnosticsJson(state.copy(payload="x".repeat(4096))).getString("lastPayload").length)
    }
}
