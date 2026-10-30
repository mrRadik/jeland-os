package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.location.LocationState
import app.organicmaps.sdk.downloader.MapManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.navigation.*
import ru.jeland.os.navigation.comaps.*
import java.io.File

/** Explicit local BlueStacks integration check; never runs downloads or test fixes on a car. */
class CoMapsIntegrationInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun shell(command:String)=instrumentation.uiAutomation.executeShellCommand(command).use {fd->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use {it.readText().trim()}
    }
    private fun capture(name:String) {
        ui.waitForIdle();Thread.sleep(500)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-comaps").apply {mkdirs()}
        val image=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun nativePoiLabelsCanBeReadOnTheNativeWorkerWithoutADebugToast() {
        assumeTrue(shell("getprop init.svc.bstsvcmgrtest")=="running")
        val context=instrumentation.targetContext
        assertNotEquals(0,context.resources.getIdentifier("stars_4","string",context.packageName))
        assertNotEquals("stars_4",app.organicmaps.sdk.util.Utils.getStringValueByKey(context,"stars_4"))
    }
    @Test fun downloadedRegionSupportsSearchLocalRouteAndSharedHomeWidget() {
        assumeTrue("This fixture is restricted to BlueStacks",shell("getprop init.svc.bstsvcmgrtest")=="running")
        val container=(ui.activity.application as JelandApplication).container
        val host=container.navigation
        val engine=host.comaps
        ui.runOnIdle {
            container.firstRun.finish()
        }
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.waitUntil(30_000){engine.ready.value||engine.error.value!=null}
        assertNull(engine.error.value);assertTrue(engine.ready.value)
        ui.onNodeWithTag("comaps-map").assertExists()
        ui.runOnIdle {
            engine.openFolder("Countries")
            if(engine.regions.value.none {it.id=="Andorra"&&it.status==CountryItem.STATUS_DONE})engine.download("Andorra",true)
        }
        ui.waitUntil(180_000){engine.regions.value.any {it.id=="Andorra"&&it.status==CountryItem.STATUS_DONE}}
        ui.runOnIdle {engine.search("Andorra la Vella ")}
        ui.waitUntil(30_000){!engine.searching.value&&engine.results.value.isNotEmpty()}
        assertTrue(engine.results.value.any {it.place.name.contains("Andorra",true)})
        capture("map-online")
        try {
            ui.runOnIdle {
                engine.select(NavigationPlace("Encamp",GeoPoint(42.5367,1.5824)))
                engine.build(GeoPoint(42.5078,1.5220))
            }
            ui.waitUntil(60_000){!engine.state.value.calculating&&(engine.state.value.geometry.size>1||engine.message.value.isNotBlank())}
            assertTrue("Route failed: ${engine.message.value}; missing=${engine.missingMaps.value}",engine.state.value.geometry.size>1)
            assertTrue(engine.state.value.preview)
            assertTrue("Kilometer units must be converted to meters",engine.state.value.kilometers>1)
            val geometry=engine.state.value.geometry
            capture("route-local")
            repeat(3) {
                ui.onNodeWithTag("nav-home").performClick();Thread.sleep(600)
                ui.onNodeWithTag("nav-navigation").performClick();Thread.sleep(600)
            }
            assertEquals(geometry,engine.state.value.geometry)
            capture("route-after-screen-switches")
            ui.runOnIdle {engine.stop();engine.search("Andorra la Vella ")}
            ui.waitUntil(20_000){!engine.searching.value&&engine.results.value.isNotEmpty()}
            ui.runOnIdle {assertFalse(MapManager.nativeIsDownloading())}
        }finally {
            ui.runOnIdle {engine.stop()}
        }
    }
    @Test fun normalNavigationButtonsPreserveManualZoomAndExposeFollowing() {
        assumeTrue(shell("getprop init.svc.bstsvcmgrtest")=="running")
        val app=(ui.activity.application as JelandApplication).container
        val engine=app.navigation.comaps
        ui.runOnIdle {app.firstRun.finish();app.firstRun.language(ru.jeland.os.data.AppLanguage.RUSSIAN)}
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.waitUntil(30_000){engine.ready.value||engine.error.value!=null}
        assertNull(engine.error.value)
        ui.onNodeWithTag("navigation-options").performClick()
        ui.onNodeWithText("Следовать за автомобилем").assertExists()
        ui.onNodeWithContentDescription("Закрыть настройки карты").performClick()
        ui.runOnIdle {engine.setFollowing(false);Framework.nativeZoomToPoint(42.5078,1.5220,17,false)}
        ui.waitUntil(5_000){var scale=0;ui.runOnIdle {scale=Framework.nativeGetDrawScale()};scale>=15}
        var previous=0
        ui.runOnIdle {previous=Framework.nativeGetDrawScale()}
        repeat(6) {
            ui.onNodeWithTag("navigation-zoom-out").performClick();Thread.sleep(400)
            var scale=0;ui.runOnIdle {scale=Framework.nativeGetDrawScale()}
            assertTrue("Zoom out unexpectedly increased scale: $previous -> $scale",scale<=previous)
            previous=scale
        }
        repeat(3){ui.onNodeWithTag("navigation-zoom-out").performClick()}
        Thread.sleep(700)
        ui.runOnIdle {assertFalse(Framework.nativeGetAutoZoomEnabled())}
        repeat(3) {
            ui.onNodeWithTag("nav-home").performClick();Thread.sleep(600)
            ui.onNodeWithTag("nav-navigation").performClick();Thread.sleep(600)
        }
        capture("real-navigation-after-zoom-and-switches")
        ui.onNodeWithTag("navigation-recenter").performClick()
        ui.runOnIdle {assertTrue(engine.following.value)}
        Thread.sleep(400)
        // A follow request made before the first fix must later enter the
        // native FOLLOW_AND_ROTATE mode, not merely change the button color.
        ui.runOnIdle {
            app.navigation.gps.pause()
            // Use the provider already selected by the SDK. Its provider
            // quality filter deliberately rejects a one-off competing fix.
            engine.locationHelper.onLocationChanged(android.location.Location(engine.locationHelper.savedLocation?.provider?:"gps").apply {
                latitude=42.5078;longitude=1.5220;accuracy=5f;bearing=90f;speed=8f
                time=System.currentTimeMillis();elapsedRealtimeNanos=android.os.SystemClock.elapsedRealtimeNanos()
            })
        }
        Thread.sleep(150)
        ui.runOnIdle {
            assertTrue("Moving fix was rejected: ${engine.locationHelper.savedLocation}",engine.locationHelper.savedLocation?.hasBearing()==true)
            engine.surfaceReady()
        }
        ui.waitUntil(5_000){var mode=0;ui.runOnIdle {mode=LocationState.getMode()};mode==LocationState.FOLLOW_AND_ROTATE}
        Thread.sleep(2500)
        ui.runOnIdle {
            val center=Framework.nativeGetScreenRectCenter()
            assertEquals(42.5078,center[0],.01)
            assertEquals(1.5220,center[1],.01)
            assertTrue("Recenter must restore a driving scale",Framework.nativeGetDrawScale()>=14)
        }
        capture("real-navigation-follow-requested")
    }
    @Test fun startingAfterADestinationSelectionKeepsOneCameraAndCompactGuidancePanels() {
        assumeTrue(shell("getprop init.svc.bstsvcmgrtest")=="running")
        val app=(ui.activity.application as JelandApplication).container
        val engine=app.navigation.comaps
        val wasEnabled=app.navigation.voice.enabled.value
        ui.runOnIdle {app.firstRun.finish();app.firstRun.language(ru.jeland.os.data.AppLanguage.RUSSIAN);app.navigation.voice.setEnabled(false)}
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.waitUntil(30_000){engine.ready.value}
        ui.runOnIdle {
            engine.openFolder("Countries")
            if(engine.regions.value.none {it.id=="Andorra"&&it.status==CountryItem.STATUS_DONE})engine.download("Andorra",true)
        }
        ui.waitUntil(180_000){engine.regions.value.any {it.id=="Andorra"&&it.status==CountryItem.STATUS_DONE}}
        assumeTrue("Guidance playback uses the explicit debug DEMO feature",BuildConfig.DEMO_FEATURES)
        try {
            ui.runOnIdle {engine.select(NavigationPlace("Encamp",GeoPoint(42.5367,1.5824)))}
            ui.onNodeWithTag("navigation-options").performClick()
            ui.onNodeWithTag("navigation-demo-drive").performScrollTo().performClick()
            ui.waitUntil(30_000){
                var following=false
                ui.runOnIdle {following=LocationState.getMode()==LocationState.FOLLOW_AND_ROTATE&&
                    Framework.nativeGetRouteFollowingInfo()?.routingSessionState?.name=="OnRoute"}
                following
            }
            Thread.sleep(1500)
            repeat(6) {
                Thread.sleep(600)
                ui.runOnIdle {
                    assertEquals(LocationState.FOLLOW_AND_ROTATE,LocationState.getMode())
                    assertNotEquals("Native guidance must really be active", "RouteNoFollowing",Framework.nativeGetRouteFollowingInfo()?.routingSessionState?.name)
                    val params=Framework.Params3dMode();Framework.nativeGet3dMode(params)
                    assertTrue("Guidance must use a tilted camera",params.enabled)
                    assertTrue("Camera must stay near street level",Framework.nativeGetDrawScale()>=16)
                    val center=Framework.nativeGetScreenRectCenter()
                    assertEquals("Camera drifted back towards the selected destination",42.5078,center[0],.02)
                    assertEquals(1.5220,center[1],.02)
                }
            }
            val map=ui.onNodeWithTag("comaps-map").fetchSemanticsNode().boundsInRoot
            val summary=ui.onNodeWithTag("navigation-trip-summary").fetchSemanticsNode().boundsInRoot
            assertTrue(summary.width<map.width*.8f)
            assertTrue(summary.height<map.height*.25f)
            ui.onNodeWithTag("navigation-maneuver").assertIsDisplayed()
            capture("guidance-compact-and-following")
        }finally {
            ui.runOnIdle {engine.stop(false);app.navigation.voice.setEnabled(wasEnabled);app.navigation.gps.start()}
        }
    }
}
