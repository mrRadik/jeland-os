package ru.jeland.os

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.SurfaceView
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.NavigationProvider
import ru.jeland.os.navhost.NavHostConnection
import java.io.File

/** Local-only test emulates the user's explicit ADB activation, never grants app shell identity. */
@RunWith(AndroidJUnit4::class)
class EmbeddedNavigationInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun surface(view:View):SurfaceView? {
        if(view is SurfaceView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)surface(view.getChildAt(i))?.let {return it}
        return null
    }
    @Test fun restoreInterruptedTestPreferencesWhenExplicitlyRequested() {
        val args=InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.containsKey("restoreCarLive")&&args.containsKey("restoreNavigator"))
        val live=requireNotNull(args.getString("restoreCarLive"));require(live=="true"||live=="false")
        val provider=NavigationProvider.valueOf(requireNotNull(args.getString("restoreNavigator")))
        assertTrue(ui.activity.getSharedPreferences("jelandos_mock_only",0).edit().putString("car.live",live).putString("navigation.provider",provider.name).commit())
    }
    @Test fun bridgeHostsNavigatorAndForwardsTouchInsideJeland() {
        val c=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {c.car.useVehicle(false);c.navigation.selectProvider(NavigationProvider.DGIS)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        val apk=ui.activity.applicationInfo.sourceDir
        require(apk.matches(Regex("/data/app/[-A-Za-z0-9_/~.=]+\\.apk")))
        val command="/system/bin/env CLASSPATH=$apk /system/bin/app_process /system/bin ru.jeland.os.navhost.NavHostShell"
        val helperOutput=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        try {
            ui.waitUntil(10000){NavHostConnection.state.value.connected}
            ui.onNodeWithTag("nav-navigation").performClick()
            ui.waitUntil(10000){NavHostConnection.state.value.attached}
            assertTrue(NavHostConnection.state.value.error,NavHostConnection.state.value.displayId>0)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-embedded-navigation").apply {mkdirs()}
            if(InstrumentationRegistry.getArguments().getString("renderProbe")=="true") {
                ui.waitUntil(30000) {
                    var bitmap:Bitmap?=null
                    val completed=java.util.concurrent.CountDownLatch(1)
                    ui.runOnUiThread {surface(ui.activity.window.decorView)?.let {view->
                        val b=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
                        PixelCopy.request(view,b,{result->if(result==PixelCopy.SUCCESS)bitmap=b else b.recycle();completed.countDown()},Handler(Looper.getMainLooper()))
                    }?:completed.countDown()}
                    completed.await(2,java.util.concurrent.TimeUnit.SECONDS)
                    bitmap?.let {b->
                        val colors=mutableSetOf<Int>();for(x in 1..20)for(y in 1..20)colors.add(b.getPixel((b.width-1)*x/21,(b.height-1)*y/21))
                        val varied=colors.size>16
                        if(varied)File(folder,"gis-surface.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
                        b.recycle();varied
                    }?:false
                }
            }
            File(folder,"gis-inside-jeland.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
            ui.onNodeWithTag("nav-home").assertIsDisplayed()
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            val before=NavHostConnection.state.value.inputs
            ui.onNodeWithTag("navigation-map-surface").performTouchInput {click(center)}
            ui.waitUntil(5000){NavHostConnection.state.value.inputs>=before+2}
            ui.onNodeWithTag("expand-climate").performClick()
            ui.onNodeWithTag("popup-climate").assertIsDisplayed()
            ui.waitUntil(5000){!NavHostConnection.state.value.attached}
            ui.onNodeWithTag("close-popup").performClick()
            ui.waitUntil(5000){NavHostConnection.state.value.attached}
            ui.onNodeWithTag("nav-home").performClick()
            ui.waitUntil(5000){!NavHostConnection.state.value.attached}
            ui.onNodeWithTag("nav-navigation").performClick()
            ui.waitUntil(5000){NavHostConnection.state.value.attached}
            assertNull(NavHostConnection.state.value.error)
        } finally {NavHostConnection.stop();ui.waitUntil(5000){!NavHostConnection.state.value.connected};helperOutput.close()}
    }
}
