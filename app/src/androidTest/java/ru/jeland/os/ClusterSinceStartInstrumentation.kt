package ru.jeland.os
import android.graphics.Bitmap
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

class ClusterSinceStartInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun oemSinceStartAndCompactManeuverRenderWithoutMusicSourceOrEmptyRouteCaption() {
        val ctx=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val raw=ctx.assets.open("cluster-pps/field-warm.txt").bufferedReader().use {it.readText()}+
            "__JOS_TRIP__\n"+ctx.assets.open("cluster-pps/oem-a001-since-start.pps").bufferedReader().use {it.readText()}+"__JOS_TRIP_EXIT__:0\n"
        val panel=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=ClusterPpsBatch.parse(raw).telemetry(19900))),null,null,20000)
        assertEquals(120.6,panel.tripKm!!,.001);assertEquals(6840000L,panel.tripMillis);assertEquals(7.2,panel.consumption!!,.001)
        val design=mutableStateOf(ClusterDesign.CLASSIC);val dark=mutableStateOf(true);val route=mutableStateOf(true)
        val artist=mutableStateOf("GorillaGun")
        ui.runOnIdle {ui.activity.setContent {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1920f/720f).testTag("since-start-qa")) {
                ClusterArtwork(panel.copy(speed=90.0,rpm=1600.0,gear="D",navigation=if(route.value)NavigationState(demo=false,maneuver=Maneuver.RIGHT,distanceToManeuver=350,street="ул. Лесная")else null),design.value,dark.value,"20:35",Modifier.matchParentSize(),miniature=design.value==ClusterDesign.NAVIGATION) {
                    ClusterMusicOverlay(MediaState(live=true,liveTrack=Track("Больше никогда",artist.value,227),position=138,source="CarPlay"),design.value,dark.value,ClusterAccent.CYAN)
                }
            }
        }}
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-121").apply {mkdirs()}
        for(d in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION))for(night in listOf(true,false))for(active in listOf(true,false)) {
            ui.runOnIdle {design.value=d;dark.value=night;route.value=active};ui.waitForIdle()
            ui.onAllNodesWithText("CarPlay",substring=true).assertCountEquals(0)
            val image=ui.onNodeWithTag("since-start-qa").captureToImage().asAndroidBitmap()
            File(folder,"${d.name.lowercase()}-${if(night)"dark"else"light"}-${if(active)"route"else"empty"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.runOnIdle {artist.value=""};ui.waitForIdle()
        ui.onAllNodesWithText("CarPlay",substring=true).assertCountEquals(0)
        ui.onNodeWithTag("cluster-music-artist").assertDoesNotExist()
    }
}
