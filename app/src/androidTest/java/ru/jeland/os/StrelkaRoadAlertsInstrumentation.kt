package ru.jeland.os.data.roadalerts

import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.MainActivity
import ru.jeland.os.core.StrelkaNoticePolicy
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.navigation.StrelkaRoadAlertCard
import ru.jeland.os.navigation.StrelkaRoadAlertsSettings
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

class StrelkaRoadAlertsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun officialEnvelopeReplayFiltersPrivateNotificationsAndHandlesCancelRepostWithoutFlicker() {
        val name="road-alerts-test-${System.nanoTime()}"
        val context=object:ContextWrapper(ui.activity) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        val repository=StrelkaRoadAlerts(context)
        var dark by mutableStateOf(true)
        var compact by mutableStateOf(false)
        fun notice(pkg:String=StrelkaNoticePolicy.PACKAGE,id:Int=10017,channel:String="1038",tag:String="Alert",title:String="Камера 60 км/ч",detail:String="ул. Ленина",posted:Long=System.currentTimeMillis(),flags:Int=0):StatusBarNotification {
            val n=Notification.Builder(ui.activity,channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(detail).build()
            n.flags=n.flags or flags
            return StatusBarNotification(pkg,pkg,id,tag,ui.activity.applicationInfo.uid,0,0,n,android.os.Process.myUserHandle(),posted)
        }
        try {
            ui.runOnIdle {
                repository.setEnabled(true)
                assertTrue("Enable the listener through setup-strelka before this integration test",repository.state.value.permission)
                repository.attached(StrelkaNotificationListener())
                ui.activity.setContent {AppLocalization("ru") {JelandTheme(dark) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {StrelkaRoadAlertCard(repository,compact=compact)}
                }}}
            }
            ui.onNodeWithTag("strelka-road-alert").assertDoesNotExist()
            ui.runOnIdle {repository.posted(notice(pkg="ru.private.chat",title="private-secret"));repository.posted(notice(id=1039,title="Update available"))}
            ui.onNodeWithTag("strelka-road-alert").assertDoesNotExist()
            val first=notice()
            ui.runOnIdle {repository.posted(first)}
            ui.onNodeWithText("Камера 60 км/ч").assertIsDisplayed()
            ui.onNodeWithText("ул. Ленина").assertIsDisplayed()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-road-alerts-125").apply {mkdirs()}
            for(night in listOf(true,false)) {
                ui.runOnIdle {dark=night};ui.waitForIdle()
                val image=ui.onNodeWithTag("strelka-road-alert").captureToImage().asAndroidBitmap()
                File(folder,"alert-${if(night)"dark"else"light"}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            val second=notice(title="Пешеходный переход",posted=first.postTime)
            ui.runOnIdle {repository.removed(first);repository.posted(second)}
            Thread.sleep(250)
            ui.onNodeWithText("Пешеходный переход").assertIsDisplayed()
            ui.runOnIdle {compact=true}
            ui.onNodeWithText("ул. Ленина").assertDoesNotExist()
            ui.runOnIdle {repository.removed(second)}
            Thread.sleep(250)
            ui.onNodeWithTag("strelka-road-alert").assertDoesNotExist()
            ui.runOnIdle {
                repository.posted(notice(posted=System.currentTimeMillis()-61_000),replay=true)
                assertNull(repository.state.value.alert)
                repository.posted(notice());repository.setEnabled(false);assertNull(repository.state.value.alert)
            }
            ui.onNodeWithTag("strelka-road-alert").assertDoesNotExist()
            ui.runOnIdle {ui.activity.setContent {AppLocalization("en") {JelandTheme(false) {Column(Modifier.fillMaxSize().padding(20.dp)){StrelkaRoadAlertsSettings(repository)}}}}}
            ui.onNodeWithText("Speed cameras and road hazards").assertIsDisplayed()
            ui.onNodeWithTag("strelka-enabled").performClick()
            ui.onNodeWithTag("strelka-permission").assertIsOn()
        }finally {ui.runOnIdle {repository.setEnabled(false)};context.deleteSharedPreferences(name)}
    }
}
