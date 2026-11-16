package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class ClusterLiveFrameInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun callbackBetweenTimerTicksRemainsVisibleAndAllLayoutsRenderOemWarnings() {
        val tick=SystemClock.elapsedRealtime()
        val incoming=mutableStateOf(UiState(car=VehicleLinkState(demo=false,connected=true)))
        val observed=AtomicReference<ClusterPanelState>()
        ui.runOnIdle {ui.activity.setContent {
            val s by incoming
            val p=rememberClusterPanelSnapshot(s,null,null,tick)
            SideEffect {observed.set(p)}
            ClusterArtwork(p,ClusterDesign.CLASSIC,true,"11:46")
        }}
        ui.waitForIdle()
        Thread.sleep(30)
        ui.runOnIdle {
            val now=SystemClock.elapsedRealtime()
            assertTrue(now>tick)
            val raw=mapOf(119 to listOf(0,0),26 to listOf(1),72 to listOf(1,121),71 to listOf(0,0,6,204),1 to listOf(1))
                .mapKeys {VdsKey(VerifiedVehicleProtocol.INFO,it.key)}.mapValues {RawVehicleSample(it.value,now)}
            incoming.value=incoming.value.copy(car=incoming.value.car.copy(raw=raw))
        }
        ui.waitForIdle()
        val p=observed.get()
        assertEquals(0.0,p.speed!!,.001);assertEquals("P",p.gear)
        assertEquals(377.0,p.rangeKm!!,.001);assertEquals(1740.0,p.odometer!!,.001)
        assertTrue(ClusterLamp.POSITION in p.lamps)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-field-fix").apply {mkdirs()}
        val renderer=ClusterRenderer(ui.activity.resources)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
        try {
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                val frame=p.copy(rpm=920.0,coolant=12.0,coolantCondition=ClusterCoolantCondition.COLD,
                    lamps=p.lamps+setOf(ClusterLamp.EPB,ClusterLamp.START_STOP_UNAVAILABLE,ClusterLamp.BELT,ClusterLamp.ESP_OFF),
                    message=ClusterMessageCatalog.find(178))
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap),frame,design,true,"11:46",labels,0f,920f,miniature=true)
                File(folder,"${design.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }finally {renderer.close()}
    }
}
