package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File

class ClusterStable118VisualInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun permanentIndicatorsAndMessageOverlayInEveryDesignAndTheme() {
        val ctx=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val native=ClusterPpsBatch.parse(ctx.assets.open("cluster-pps/field-warm.txt").bufferedReader().use {it.readText()}).telemetry(19_900)
        val actual=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=native)),null,null,20_000)
        assertNull(actual.coolant);assertEquals(.070,actual.coolantLevel!!,.00001)
        // Rendering-only examples for layout; actual incoming telemetry assertions remain above and in replay tests.
        val panel=actual.copy(speed=90.0,rpm=1600.0,gear="D",tripKm=120.6,tripMillis=6840000,consumption=7.2,
            rangeKm=375.0,odometer=17740.0,fuel=.67,outside=5.0,driveMode=ClusterDriveMode.COMFORT,
            limiter=ClusterLimiter(1,90,false),navigation=NavigationState(demo=false,maneuver=Maneuver.LEFT,distanceToManeuver=51,street="ул. Лесная"))
        val design=mutableStateOf(ClusterDesign.SPORT);val dark=mutableStateOf(true)
        val popup=mutableStateOf(false)
        ui.runOnIdle {ui.activity.setContent {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1920f/720f).background(Color.Black).testTag("reference-cluster")) {
                ClusterArtwork(panel.copy(message=if(popup.value)ClusterOemMessage(1,"Открыта передняя правая дверь","Front right door open",false) else null),design.value,dark.value,"20:35",Modifier.matchParentSize(),miniature=design.value==ClusterDesign.NAVIGATION) {
                    ClusterMusicOverlay(MediaState(live=true,liveTrack=Track("Больше никогда","GorillaGun",227),position=138,source="CarPlay"),design.value,dark.value,ClusterAccent.CYAN)
                }
            }
        }}
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-118").apply {mkdirs()}
        for(d in listOf(ClusterDesign.SPORT,ClusterDesign.CLASSIC,ClusterDesign.NAVIGATION))for(night in listOf(true,false))for(message in listOf(false,true)) {
            ui.runOnIdle {design.value=d;dark.value=night;popup.value=message};ui.waitForIdle()
            ui.onNodeWithTag("cluster-header-gear").assertTextEquals("D")
            ui.onNodeWithTag("cluster-header-clock").assertTextEquals("20:35")
            val image=ui.onNodeWithTag("reference-cluster").captureToImage().asAndroidBitmap()
            File(folder,"${d.name.lowercase()}-${if(night)"dark"else"light"}-${if(message)"message"else"normal"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
}
