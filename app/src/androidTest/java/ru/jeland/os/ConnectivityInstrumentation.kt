package ru.jeland.os

import android.app.Activity
import android.app.Instrumentation
import android.content.*
import android.graphics.Bitmap
import android.os.Parcel
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNetworkStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConnectivityInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun portraitDemo() {
        ui.runOnIdle {container().car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun capture(name:String) {
        ui.waitForIdle();android.os.SystemClock.sleep(350)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.5.0").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun liveHeaderUpdatesWithoutInventingSignalAndFitsBothThemes() {
        var reading by mutableStateOf(ConnectivityState(wifi=true,wifiLevel=4,cellular=true,mobileLevel=3,networkType="4G",available=true,
            wifiEnabled=true,bluetoothEnabled=true,bluetoothConnected=true))
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
                Column(Modifier.fillMaxSize().background(Jeland.colors.background)) {
                    JelandHeader(reading,VehicleLinkState(demo=false,connected=true))
                    WeatherWidget(WeatherState(null,"","Штатная погода",demo=false),Modifier,{})
                }
            }}}
            ui.onNodeWithTag("mobile-network-type").assertTextEquals("4G")
            ui.onNodeWithTag("bluetooth-indicator").assertContentDescriptionEquals("Bluetooth: телефон подключён")
            ui.onNodeWithTag("wifi-indicator").assertContentDescriptionEquals("Wi-Fi подключён, уровень 4")
            ui.onNodeWithText("—°",substring=true).assertIsDisplayed();ui.onNodeWithText("+8°",substring=true).assertDoesNotExist()
            for(tag in listOf("mobile-network-type","bluetooth-indicator","wifi-indicator")) {
                val header=ui.onNodeWithTag("jeland-header").fetchSemanticsNode().boundsInRoot
                val icon=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("Header clips $tag",header.contains(icon.topLeft)&&header.contains(icon.bottomRight))
            }
            capture("native-weather-header-${if(dark)"dark"else"light"}")
        }
        ui.runOnIdle {reading=ConnectivityState(available=true,wifiEnabled=false,bluetoothEnabled=false,bluetoothConnected=false)}
        ui.onNodeWithTag("mobile-network-type").assertTextEquals("—")
        ui.onNodeWithTag("wifi-indicator").assertContentDescriptionEquals("Wi-Fi выключен")
        ui.onNodeWithTag("bluetooth-indicator").assertContentDescriptionEquals("Bluetooth выключен")
        capture("radios-off")
    }
    @Test fun carButtonDispatchesToNativeSettingsInsteadOfDemoCarPage() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val monitor=instrumentation.addMonitor(IntentFilter(Settings.ACTION_SETTINGS),Instrumentation.ActivityResult(Activity.RESULT_OK,null),true)
        try {
            assertFalse("This test targets local BlueStacks, not a head unit",container().nativeApps.settingsAvailable)
            ui.onNodeWithTag("nav-car").performClick()
            ui.waitUntil(5000){monitor.hits==1}
            ui.onNodeWithTag("vehicle-status-card").assertIsDisplayed()
            ui.onNodeWithTag("car-tab-quick").assertDoesNotExist()
            assertFalse(container().nativeApps.engineeringAvailable)
            ui.onNodeWithTag("quick-engineering").performClick()
            ui.onNodeWithText("Инженерное меню недоступно на этом устройстве.").assertIsDisplayed()
            assertEquals("Engineering must not fall back to settings",1,monitor.hits)
            ui.onNodeWithTag("nav-settings").performClick()
            ui.onNodeWithTag("open-demo-car").performScrollTo().performClick()
            ui.onNodeWithTag("car-tab-quick").assertExists()
        } finally {instrumentation.removeMonitor(monitor)}
    }
    @Test fun oemParcelUsesExactReadOrderAndIsReadOnly() {
        val p=Parcel.obtain()
        try {
            p.writeByte(1.toByte());p.writeByte(1.toByte());p.writeString("4G");p.writeByte(0.toByte());p.writeInt(3)
            p.writeString("discarded IP");p.writeString("discarded VPN");p.writeString("READ")
            p.writeInt(0);p.writeInt(0);p.writeInt(10)
            val end=p.dataPosition();p.setDataPosition(0)
            val state=VDNetworkStatus.CREATOR.createFromParcel(p)
            assertTrue(state.connected);assertTrue(state.registered);assertEquals("4G",state.type);assertEquals(3,state.level)
            assertEquals(end,p.dataPosition())
            assertThrows(UnsupportedOperationException::class.java){state.writeToParcel(p,0)}
        } finally {p.recycle()}
    }
    @Test fun weatherModeSwitchRemovesDemoAndNeverLaunchesOrRequestsNetwork() {
        var launches=0
        var weatherGpsAccesses=0
        val context=object:ContextWrapper(ui.activity.applicationContext) {
            override fun getApplicationContext():Context=this
            override fun startActivity(intent:Intent){launches++}
            override fun getSystemService(name:String):Any? {if(name==Context.LOCATION_SERVICE)weatherGpsAccesses++;return super.getSystemService(name)}
        }
        val link=MutableStateFlow(VehicleLinkState(demo=true))
        val native=NativeApps(context)
        assertFalse(native.weatherAvailable)
        val weather=ExistingWeatherRepository(native,MockWeatherRepository(MemorySettingsStore()),link)
        runBlocking {
            assertTrue(withTimeout(3000){weather.state.first {it.demo}}.demo)
            link.value=VehicleLinkState(demo=false)
            val live=withTimeout(3000){weather.state.first {!it.demo}}
            assertNull(live.temperature);assertEquals("",live.city);weather.useCity("Do not fetch")
            assertNull(weather.state.value.temperature)
        }
        assertEquals(0,launches)
        assertFalse(native.openWeather());assertEquals(0,launches)
        assertFalse(native.openEngineering());assertEquals(0,launches)
        val engineering=NativeApps.engineeringIntent()
        assertEquals("com.desaysv.engmode",engineering.component!!.packageName)
        assertEquals("com.desaysv.engmode.MainActivity",engineering.component!!.className)
        assertEquals(Intent.ACTION_MAIN,engineering.action)
        assertTrue(engineering.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertNull(engineering.extras)
        assertTrue(native.openSettings());assertEquals(1,launches)
        assertEquals("com.desaysv.setting.ui.activity.SettingActivity",NativeApps.settingsIntent().component!!.className)
        assertEquals("com.liontech.weather.SplashActivity",NativeApps.weatherIntent().component!!.className)
        @Suppress("DEPRECATION") val permissions=context.packageManager.getPackageInfo(context.packageName,4096).requestedPermissions.orEmpty()
        // Foreground GPS is now needed by our navigation, not by the weather adapter.
        assertEquals("Weather must not request the location service",0,weatherGpsAccesses)
        for(p in listOf("INTERNET","CHANGE_WIFI_STATE","BLUETOOTH_SCAN","ACCESS_BACKGROUND_LOCATION","SYSTEM_ALERT_WINDOW"))assertFalse(p,permissions.contains("android.permission.$p"))
    }
    @Test fun radioObserverSurvivesMissingAutomotiveHardwareAndStops() {
        val journal=mutableListOf<String>()
        val radio=AndroidConnectivityRepository(ui.activity.applicationContext){journal.add(it)}
        ui.runOnIdle {radio.start();radio.start()}
        ui.waitUntil(5000){radio.state.value.available}
        ui.runOnIdle {radio.stop()}
        val snapshot=radio.state.value
        android.os.SystemClock.sleep(500)
        assertEquals(snapshot,radio.state.value)
        assertTrue(journal.none {it.contains("SSID")||it.contains("MAC")})
        ui.runOnIdle {radio.start();radio.stop()}
    }
}
