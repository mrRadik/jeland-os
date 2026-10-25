package ru.jeland.os

import android.content.*
import android.app.Notification
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.*
import ru.jeland.os.data.navigation.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class InstalledNavigationInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun portrait() {
        ui.runOnIdle {container().car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    @Test fun routeIntentsRespectProviderCoordinateOrderAndDoNotUseBrowserFallback() {
        val place=NavigationPlace("home","Дом",55.16,61.4)
        val gis=NavigatorIntents.route(NavigationProvider.DGIS,place)
        assertEquals("ru.dublgis.dgismobile",gis.`package`)
        assertEquals("dgis://2gis.ru/routeSearch/rsType/car/to/61.4,55.16",gis.dataString)
        val ya=NavigatorIntents.route(NavigationProvider.YANDEX,place)
        assertEquals("ru.yandex.yandexnavi",ya.`package`)
        assertEquals("55.16",ya.data!!.getQueryParameter("lat_to"));assertEquals("61.4",ya.data!!.getQueryParameter("lon_to"))
        assertNull(ya.data!!.getQueryParameter("lat_from"))
        for(intent in listOf(gis,ya)) {assertEquals(Intent.ACTION_VIEW,intent.action);assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK!=0);assertNull(intent.extras)}
    }
    @Test fun shellBridgeRegistrationIsNotAvailableEvenToNormalAppUid() {
        val data=android.os.Bundle().apply {putBinder("host",android.os.Binder())}
        assertThrows(SecurityException::class.java) {
            ui.activity.contentResolver.call(android.net.Uri.parse("content://ru.jeland.os.navhost"),"register",null,data)
        }
    }
    @Test fun installedGisLaunchAndPlacePersistenceWithSafeErrors() {
        val calls=mutableListOf<Intent>()
        val context=object:ContextWrapper(ui.activity.applicationContext) {
            override fun getApplicationContext():Context=this
            override fun startActivity(intent:Intent) {calls.add(Intent(intent))}
        }
        val store=MemorySettingsStore();val repo=InstalledNavigationRepository(context,store)
        assertNull(repo.state.value.external!!.selected)
        assertNotNull(repo.openNavigator());assertTrue(calls.isEmpty())
        repo.selectProvider(NavigationProvider.DGIS)
        assertTrue("Install official 2GIS on local emulator before running this test",repo.state.value.external!!.selectedInstalled)
        assertNull(repo.openNavigator());assertEquals(NavigationProvider.DGIS.packageName,calls.single().component!!.packageName)
        assertNotNull(repo.savePlace("home","Дом","bad"))
        assertNull(repo.savePlace("home","Дом","55.16,61.40"))
        assertNull(repo.openPlace("home"));assertEquals("dgis://2gis.ru/routeSearch/rsType/car/to/61.4,55.16",calls.last().dataString)
        assertNotNull(context.packageManager.resolveActivity(calls.last(),0))
        val restored=InstalledNavigationRepository(context,store)
        assertEquals(NavigationProvider.DGIS,restored.state.value.external!!.selected)
        assertEquals("Дом",restored.state.value.external!!.places.single().title)
        restored.removePlace("home");assertTrue(restored.state.value.external!!.places.isEmpty())
        repo.selectProvider(NavigationProvider.YANDEX)
        assertTrue("Install official Yandex Navigator on local emulator before running this test",repo.state.value.external!!.selectedInstalled)
        assertNull(repo.openNavigator());assertEquals(NavigationProvider.YANDEX.packageName,calls.last().component!!.packageName)
        assertNull(repo.openPlace("home"));assertEquals("yandexnavi",calls.last().data!!.scheme)
        assertNotNull(context.packageManager.resolveActivity(calls.last(),0))
    }
    @Test fun providerScreenAndEditorAreInteractiveAndNoDemoRouteIsShown() {
        val model=MainViewModel(container());val r=container().navigation
        ui.runOnIdle {r.selectProvider(NavigationProvider.DGIS)}
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {it.setContent {
                val nav by r.state.collectAsState()
                JelandTheme(dark) {InstalledNavigationScreen(model,nav.external!!)}
            }}
            ui.onNodeWithTag("navigator-dgis").assertDoesNotExist()
            ui.onNodeWithTag("navigator-yandex").assertDoesNotExist()
            ui.onNodeWithTag("navigation-open").assertIsEnabled()
            ui.onNodeWithTag("navigation-screen-instruction").assertTextEquals("Куда поедем?")
            ui.onNodeWithText("Следующий манёвр").assertDoesNotExist()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.8.0").apply {mkdirs()}
            File(folder,"navigation-${if(dark)"dark"else"light"}.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            ui.onNodeWithTag("navigation-add-place").performScrollTo().performClick()
            ui.onNodeWithTag("navigation-place-name").performScrollTo().performTextInput("Тестовое место")
            ui.onNodeWithTag("navigation-place-coordinates").performScrollTo().performTextInput("55.16, 61.40")
            ui.onNodeWithTag("navigation-place-save").performScrollTo().performClick()
            ui.onNodeWithTag("navigation-place-editor").assertDoesNotExist()
            assertTrue(r.state.value.external!!.places.any {it.title=="Тестовое место"})
            ui.onNodeWithTag("navigation-notification-access").assertDoesNotExist()
            ui.onNodeWithTag("navigation-settings").assertIsDisplayed()
        }
    }
    @Test fun navigatorSelectionLivesInSettingsAndSettingsShortcutWorks() {
        val model=MainViewModel(container());val r=container().navigation
        ui.activityRule.scenario.onActivity {it.setContent {
            val nav by r.state.collectAsState()
            JelandTheme(true) {SettingsScreen(UiState(navigation=nav),model,{})}
        }}
        ui.onNodeWithTag("navigator-yandex").performScrollTo().performClick()
        ui.onNodeWithTag("navigator-yandex").assertIsSelected()
        assertEquals(NavigationProvider.YANDEX,r.state.value.external!!.selected)
        ui.onNodeWithTag("navigator-dgis").performScrollTo().performClick()
        ui.onNodeWithTag("navigator-dgis").assertIsSelected()
        assertEquals(NavigationProvider.DGIS,r.state.value.external!!.selected)
        ui.onNodeWithTag("navigation-notification-access").performScrollTo().assertIsDisplayed()
        var opened=false
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {InstalledNavigationScreen(model,r.state.value.external!!){opened=true}}}}
        ui.onNodeWithTag("navigation-settings").performClick()
        ui.runOnIdle {assertTrue(opened)}
    }
    @Test fun notificationAdapterReadsOnlyNavigatorAndClearsOnRemovalDisconnect() {
        fun notification(pkg:String,id:Int):StatusBarNotification {
            val n=Notification.Builder(ui.activity,"test-navigation").setSmallIcon(android.R.drawable.ic_dialog_map)
                .setCategory(Notification.CATEGORY_NAVIGATION).setOngoing(true)
                .setContentTitle("Через 200 м").setContentText("Поверните направо").build()
            return StatusBarNotification(pkg,pkg,id,null,10001,0,0,n,UserHandle.getUserHandleForUid(10001),System.currentTimeMillis())
        }
        NavigationNotices.connection(true)
        try {
            assertFalse(NavigationNotices.posted(notification("other.app",1)))
            val sbn=notification(NavigationProvider.DGIS.packageName,2)
            assertTrue(NavigationNotices.posted(sbn))
            assertEquals("Через 200 м",NavigationNotices.current(NavigationProvider.DGIS)!!.title)
            assertNull(NavigationNotices.current(NavigationProvider.YANDEX))
            NavigationNotices.removed(sbn.key);assertNull(NavigationNotices.current(NavigationProvider.DGIS))
            NavigationNotices.posted(sbn);NavigationNotices.connection(false)
            assertNull(NavigationNotices.current(NavigationProvider.DGIS))
        } finally {NavigationNotices.connection(false)}
    }
}
