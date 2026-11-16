package ru.jeland.os

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ClusterPpsFrameInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun typedNativePacketsUpdateAllThreeRenderedPanels() {
        val testContext=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val text=testContext.assets.open("cluster-pps/field-warm.txt").bufferedReader().use {it.readText()}
        val tick=SystemClock.elapsedRealtime()
        val incoming=mutableStateOf(UiState(car=VehicleLinkState(demo=false,connected=true)))
        val design=mutableStateOf(ClusterDesign.CLASSIC)
        val observed=AtomicReference<ClusterPanelState>()
        ui.runOnIdle {ui.activity.setContent {
            val s by incoming
            val p=rememberClusterPanelSnapshot(s,null,null,tick)
            SideEffect {observed.set(p)}
            ClusterArtwork(p,design.value,true,"13:55",Modifier.fillMaxWidth().aspectRatio(1920f/720f).testTag("pps-live-artwork"))
        }}
        ui.waitForIdle();Thread.sleep(30)
        ui.runOnIdle {
            val at=SystemClock.elapsedRealtime()
            assertTrue(at>tick)
            val parsed=ClusterPpsBatch.parse(text).telemetry(at)
            assertNull(parsed.error)
            val raw=mapOf(26 to listOf(1),72 to listOf(1,121),71 to listOf(0,0,6,204))
                .mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}.mapValues {RawVehicleSample(it.value,at)}
            incoming.value=incoming.value.copy(car=incoming.value.car.copy(raw=raw,clusterTelemetry=parsed))
        }
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-pps-json-v2").apply {mkdirs()}
        for(d in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
            ui.runOnIdle {design.value=d};ui.waitForIdle()
            val p=observed.get()
            assertEquals(800.0,p.rpm!!,.001);assertEquals(0.0,p.speed!!,.001);assertNull(p.coolant)
            assertEquals("P",p.gear);assertNull(p.message);assertEquals(11,p.indicators!!.size)
            assertTrue(p.lamps.containsAll(setOf(ClusterLamp.EPB,ClusterLamp.BELT,ClusterLamp.START_STOP_UNAVAILABLE)))
            ui.onNodeWithTag("pps-live-artwork").captureToImage().asAndroidBitmap().let {bitmap->
                File(folder,"${d.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        ui.runOnIdle {
            val old=SystemClock.elapsedRealtime()-4000
            incoming.value=incoming.value.copy(car=incoming.value.car.copy(clusterTelemetry=ClusterPpsBatch.parse(text).telemetry(old)))
        }
        ui.waitForIdle()
        assertNull(observed.get().rpm);assertNull(observed.get().indicators);assertNull(observed.get().message)
    }
}
