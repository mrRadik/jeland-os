package ru.jeland.os

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.data.roadalerts.*
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.navigation.StrelkaRoadAlertCard
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.ClusterStage
import ru.jeland.os.ui.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap

/** Opt-in device test: requires official Strelka with its notification output configured. */
class StrelkaOfficialIntegration {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun actualOfficialDemoReachesOurNotificationListenerAndCard() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("officialStrelka")=="true")
        val i=InstrumentationRegistry.getInstrumentation()
        val repo=RoadAlertsRuntime.get(ui.activity)
        val previous=repo.state.value
        val preferences=(ui.activity.application as JelandApplication).container.preferences
        val previousPreferences=preferences.state.value
        fun main(action:()->Unit)=i.runOnMainSync(action)
        fun await(label:String,timeout:Long=20000,condition:()->Boolean) {
            val until=SystemClock.elapsedRealtime()+timeout
            while(!condition()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100)
            assertTrue("$label: ${repo.state.value}",condition())
        }
        fun node(id:String):AccessibilityNodeInfo? {
            fun find(n:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                if(n.viewIdResourceName==if(id.contains(":"))id else "com.ivolk.StrelkaGPS:id/$id")return n
                for(x in 0 until n.childCount)n.getChild(x)?.let {find(it)}?.let {return it}
                return null
            }
            val automation=i.uiAutomation
            automation.serviceInfo=automation.serviceInfo.apply {flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS}
            for(w in automation.windows)w.root?.let(::find)?.let {return it}
            return null
        }
        fun stopVendorDemo() {
            main {ui.activity.startActivity(Intent(Intent.ACTION_VIEW).setClassName("com.ivolk.StrelkaGPS","com.ivolk.StrelkaGPS.MainActivity").putExtra("scut_stop",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            Thread.sleep(700)
        }
        try {
            main {
                (ui.activity.application as JelandApplication).container.firstRun.finish()
                repo.setAutoStart(false);repo.setEnabled(true);repo.resume()
                NotificationListenerService.requestRebind(ComponentName(ui.activity,StrelkaNotificationListener::class.java))
            }
            await("Real Android listener is connected") {repo.state.value.connected}
            stopVendorDemo()
            await("Previous vendor demo stopped") {!repo.state.value.running}
            main {repo.start()}
            fun dismissVideoTip() {
                if(node("android:id/alertTitle")?.text?.toString()=="Видео о настройке") {
                    assertTrue(node("android:id/button2")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(600)
                }
            }
            Thread.sleep(1200)
            dismissVideoTip()
            await("Real service notification confirms manual start") {repo.state.value.running}
            stopVendorDemo()
            await("Service stopped before entering the official demo") {!repo.state.value.running}
            main {repo.openApp()};Thread.sleep(600)
            dismissVideoTip()
            await("Official demo start control is visible",10000) {node("buttonStart")!=null}
            val start=node("buttonStart")?:error("Official Start button not found")
            assertTrue(start.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK))
            await("Actual vendor demo warning reached the production listener",30000) {repo.state.value.alert!=null}
            val alert=repo.state.value.alert!!
            assertTrue(alert.key.contains("com.ivolk.StrelkaGPS|10017|Alert"))
            File(ui.activity.getExternalFilesDir(null),"official-strelka-result.txt").writeText("Actual vendor warning: ${alert.title}\n${alert.detail}\nService running: ${repo.state.value.running}")
            main {
                ui.activity.startActivity(Intent(ui.activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                preferences.change(previousPreferences.copy(style=DashboardStyle.MINIMAL))
            }
            ui.onNodeWithTag("nav-navigation").performScrollTo().performClick()
            ui.onNodeWithTag("strelka-road-alert").assertIsDisplayed()
            ui.onNodeWithText(alert.title).assertIsDisplayed()
            ui.onNodeWithTag("comaps-map").assertIsDisplayed()
            fun capturePage(name:String) {
                val image=ui.onRoot().captureToImage().asAndroidBitmap()
                val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-road-127").apply {mkdirs()}
                File(folder,"app-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            capturePage("navigation")
            val bitmap=ui.onNodeWithTag("strelka-road-alert").captureToImage().asAndroidBitmap()
            File(ui.activity.getExternalFilesDir(null),"official-strelka-card.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            ui.onNodeWithTag("nav-home").performScrollTo().performClick()
            ui.onNodeWithTag("strelka-road-alert").assertIsDisplayed()
            ui.onNodeWithTag("nav-home").performTouchInput {swipeDown()}
            capturePage("home")
            if(InstrumentationRegistry.getArguments().getString("clusterStrelka")=="true") {
                var design by mutableStateOf(ClusterDesign.CLASSIC)
                main {ui.activity.setContent {AppLocalization("ru"){JelandTheme(true){
                    val model:MainViewModel=viewModel(factory=MainViewModel.Factory((ui.activity.application as JelandApplication).container))
                    ClusterStage(ClusterPanelState(),MediaState(),design,true,"20:35",model,Modifier.fillMaxWidth().aspectRatio(1920f/720f),ClusterAccent.CYAN)
                }}}}
                val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-road-127").apply {mkdirs()}
                for(d in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                    main {design=d}
                    ui.onNodeWithTag("cluster-road-alert").assertIsDisplayed()
                    ui.onNodeWithTag("cluster-camera-speed-limit").assertIsDisplayed()
                    val bitmap=ui.onNodeWithTag("cluster-stage").captureToImage().asAndroidBitmap()
                    File(folder,"${d.name.lowercase()}-official-demo.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                }
            }
        }finally {stopVendorDemo();main {repo.setAutoStart(previous.autoStart);repo.setEnabled(previous.enabled);preferences.change(previousPreferences)}}
    }
}
