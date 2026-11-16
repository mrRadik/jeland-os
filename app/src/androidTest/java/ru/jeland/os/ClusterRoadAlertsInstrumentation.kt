package ru.jeland.os.data.roadalerts

import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.*
import ru.jeland.os.core.*
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.ui.MainViewModel
import ru.jeland.os.ui.cluster.*
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

class ClusterRoadAlertsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val designs=listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)
    private fun fieldPanel():ClusterPanelState {
        val text=InstrumentationRegistry.getInstrumentation().context.assets.open("cluster-pps/field-warm.txt").bufferedReader().use {it.readText()}
        val native=ClusterPpsBatch.parse(text).telemetry(19_900)
        assertNull(native.error)
        return ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=native)),null,null,20_000)
            .copy(message=null,lamps=emptySet(),navigation=NavigationState(active=true,distanceToManeuver=350,street="ул. Лесная"))
    }
    @Test fun cameraOverlayPreservesInstrumentPixelsAndFactoryMessagePriorityInAllDesigns() {
        val p=fieldPanel().copy(speed=130.0)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
        val road=RoadAlert("official-envelope","СТРЕЛКА 80км/ч","Тест 1 из 5",0,60000)
        assertTrue(clusterIslandNotice(p,labels,road)!!.road)
        val critical=p.copy(message=ClusterOemMessage(1,"Неисправность тормозной системы","Brake system fault",true))
        assertEquals(clusterIslandNotice(critical,labels),clusterIslandNotice(critical,labels,road))
        val renderer=ClusterRenderer(ui.activity.resources)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-road-128").apply {mkdirs()}
        try {
            for(design in designs)for(dark in listOf(true,false)) {
                fun render(alert:RoadAlert?):Bitmap {
                    val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                    renderer.draw(Canvas(bitmap),p,design,dark,"20:35",labels,p.speed?.toFloat(),p.rpm?.toFloat(),roadAlert=alert)
                    return bitmap
                }
                val normal=render(null);val alert=render(road)
                assertFalse(normal.sameAs(alert))
                for(y in 0 until normal.height)for(x in 0 until normal.width) {
                    val ax=x*2048f/1920f;val ay=y*768f/720f
                    val sign=clusterSpeedLimitCircle(design)!!
                    val insideSign=(ax-sign.x)*(ax-sign.x)+(ay-sign.y)*(ay-sign.y)<=(sign.radius+2f)*(sign.radius+2f)
                    if((y>175||x<545||x>1375)&&!insideSign&&normal.getPixel(x,y)!=alert.getPixel(x,y))fail("Instrument pixel changed: $design/$dark at $x,$y")
                }
                File(folder,"${design.name.lowercase()}-${if(dark)"dark"else"light"}-native.png").outputStream().use {alert.compress(Bitmap.CompressFormat.PNG,100,it)}
                normal.recycle();alert.recycle()
            }
        }finally {renderer.close()}
    }
    @Test fun productionStageObservesSharedAlertsAcrossDesignChangesAndRemoval() {
        val app=(ui.activity.application as JelandApplication).container
        val repo=app.navigation.roadAlerts
        val previous=repo.state.value
        var design by mutableStateOf(ClusterDesign.CLASSIC)
        var dark by mutableStateOf(true)
        var panel by mutableStateOf(fieldPanel())
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-road-128").apply {mkdirs()}
        fun notice():StatusBarNotification {
            val n=Notification.Builder(ui.activity,"1038").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("СТРЕЛКА 80км/ч").setContentText("Тест 1 из 5").build()
            return StatusBarNotification("com.ivolk.StrelkaGPS","com.ivolk.StrelkaGPS",10017,"Alert",ui.activity.applicationInfo.uid,0,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis())
        }
        try {
            ui.runOnIdle {
                app.firstRun.finish();repo.setAutoStart(false);repo.setEnabled(true)
                assertTrue("Run setup-strelka first",repo.state.value.permission)
                NotificationListenerService.requestRebind(ComponentName(ui.activity,StrelkaNotificationListener::class.java))
            }
            ui.waitUntil(20000){repo.state.value.connected}
            ui.runOnIdle {ui.activity.setContent {AppLocalization("ru"){JelandTheme(dark){
                val model:MainViewModel=viewModel(factory=MainViewModel.Factory(app))
                ClusterStage(panel,MediaState(),design,dark,"20:35",model,Modifier.fillMaxWidth().aspectRatio(1920f/720f),ClusterAccent.CYAN)
            }}}}
            val incoming=notice()
            ui.runOnIdle {repo.posted(incoming)}
            for(d in designs)for(night in listOf(true,false)) {
                ui.runOnIdle {design=d;dark=night}
                ui.onNodeWithTag("cluster-road-alert").assertIsDisplayed()
                ui.onNodeWithTag("cluster-camera-speed-limit").assertIsDisplayed()
                ui.onNodeWithContentDescription("Ограничение скорости камеры: 80 км/ч").assertExists()
                ui.onNodeWithContentDescription("СТРЕЛКА 80км/ч. Тест 1 из 5").assertExists()
                ui.onNodeWithTag("cluster-header-gear").assertTextEquals(panel.gear?:"—")
                val image=ui.onNodeWithTag("cluster-stage").captureToImage().asAndroidBitmap()
                File(folder,"${d.name.lowercase()}-${if(night)"dark"else"light"}-stage.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            ui.runOnIdle {panel=panel.copy(message=ClusterOemMessage(1,"Проверьте тормозную систему","Check brakes",true))}
            ui.onNodeWithTag("cluster-road-alert").assertDoesNotExist()
            ui.onNodeWithTag("cluster-camera-speed-limit").assertIsDisplayed()
            ui.runOnIdle {panel=panel.copy(message=null)}
            ui.onNodeWithTag("cluster-road-alert").assertIsDisplayed()
            ui.runOnIdle {
                val emptyLimit=Notification.Builder(ui.activity,"1038").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("КАМЕРА 0км/ч").build()
                repo.posted(StatusBarNotification("com.ivolk.StrelkaGPS","com.ivolk.StrelkaGPS",10017,"Alert",ui.activity.applicationInfo.uid,0,0,emptyLimit,android.os.Process.myUserHandle(),System.currentTimeMillis()))
            }
            ui.onNodeWithTag("cluster-road-alert").assertIsDisplayed()
            ui.onNodeWithTag("cluster-camera-speed-limit").assertDoesNotExist()
            ui.runOnIdle {repo.posted(notice());design=ClusterDesign.ORIGINAL}
            ui.onNodeWithTag("cluster-road-alert").assertDoesNotExist()
            ui.runOnIdle {design=ClusterDesign.CLASSIC;repo.removed(incoming)}
            Thread.sleep(250)
            ui.onNodeWithTag("cluster-road-alert").assertDoesNotExist()
            ui.onNodeWithTag("cluster-camera-speed-limit").assertDoesNotExist()
            ui.runOnIdle {repo.posted(notice());repo.setEnabled(false)}
            ui.onNodeWithTag("cluster-road-alert").assertDoesNotExist()
        }finally {ui.runOnIdle {repo.setAutoStart(previous.autoStart);repo.setEnabled(previous.enabled)}}
    }
}
