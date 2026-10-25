package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.ThemeMode
import ru.jeland.os.navigation.*
import org.maplibre.android.maps.MapView
import android.view.View
import android.view.ViewGroup
import java.io.File

class OfflineNavigationInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=(ui.activity.application as JelandApplication).container
    private var originalTheme=ThemeMode.AUTO
    private var originalLive=false
    private var originalVoice=false
    @Before fun localDemo() {
        ui.runOnIdle {
            originalTheme=app.theme.mode.value;originalLive=!app.car.link.value.demo;originalVoice=app.navigation.provider.voice.enabled.value
            app.car.useVehicle(false);app.theme.setMode(ThemeMode.DARK);app.navigation.stop();app.navigation.provider.voice.setEnabled(false)
            // Emulator GPS may be enabled by the user. Use a declared preview source,
            // never an assumed absence of real fixes or a moving default emulator point.
            app.navigation.gps.replay(GeoPoint(55.160,61.402))
        }
        ui.waitUntil(15000){!app.navigation.preparing.value}
        assertNotNull("Bundled map available without import",app.navigation.maps.region.value)
    }
    private fun openMap() {
        ui.waitUntil(10000){ui.onAllNodesWithTag("nav-navigation").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.waitUntil(30000){ui.onAllNodesWithTag("navigation-map-rendered").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun stopTestRoute(){ui.runOnIdle {
        app.navigation.stop();app.navigation.message.value=""
        app.theme.setMode(originalTheme);app.car.useVehicle(originalLive);app.navigation.provider.voice.setEnabled(originalVoice)
    }}
    private fun mapView(root:View):MapView? {
        if(root is MapView)return root
        if(root is ViewGroup)for(i in 0 until root.childCount)mapView(root.getChildAt(i))?.let {return it}
        return null
    }
    private fun captureMap(name:String,minColors:Int=40) {
        val captured=CompletableDeferred<Bitmap>()
        ui.runOnIdle {mapView(ui.activity.window.decorView)!!.getMapAsync {it.snapshot {bitmap->captured.complete(bitmap)}}}
        val bitmap=runBlocking {withTimeout(30000){captured.await()}}
        val colors=mutableSetOf<Int>()
        for(y in 0 until bitmap.height step maxOf(1,bitmap.height/40))for(x in 0 until bitmap.width step maxOf(1,bitmap.width/40))colors+=bitmap.getPixel(x,y)
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
        File(folder,"$name-map.png").outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        val capture=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/$name-screen.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(capture).use {while(it.read()!=-1){}}
        assertTrue(File(folder,"$name-screen.png").length()>10000)
        assertTrue("$name: real map must contain roads, labels and land, not a blank surface (${colors.size} sampled colors)",colors.size>minColors)
    }
    @Test fun fullSizeRealMapInBothThemesAndClimateStillWorks() {
        openMap()
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.waitForIdle()
            val screen=ui.onNodeWithTag("offline-navigation-screen").fetchSemanticsNode().boundsInRoot
            val map=ui.onNodeWithTag("offline-map").fetchSemanticsNode().boundsInRoot
            assertEquals("Map fills tab width",screen.width,map.width,1f)
            assertEquals("Map fills tab height",screen.height,map.height,1f)
            assertTrue("Full-height map, not old 480dp thumbnail",map.height>map.width)
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            ui.onNodeWithTag("nav-music").assertIsDisplayed()
            captureMap(mode.name.lowercase())
        }
        ui.onNodeWithTag("driver-temp").performClick()
        ui.onNodeWithTag("temperature-panel").assertIsDisplayed()
        ui.onNodeWithTag("close-popup").performClick()
        ui.onNodeWithTag("offline-map").assertIsDisplayed()
        ui.onNodeWithTag("navigation-zoom-in").performClick()
        ui.onNodeWithTag("nav-home").performClick()
        ui.onNodeWithTag("navigation-widget").assertIsDisplayed()
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.waitUntil(30000){ui.onAllNodesWithTag("navigation-map-rendered").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun selectedDestinationCanBeClearedWithoutInventingAnotherPoint() {
        openMap()
        val place=NavigationPlace("Выбранный дом",GeoPoint(55.1605,61.402))
        ui.runOnIdle {app.navigation.select(place)}
        ui.onNodeWithTag("navigation-selected").assertIsDisplayed()
        ui.onNodeWithTag("navigation-clear-selection").performClick()
        ui.onNodeWithTag("navigation-selected").assertDoesNotExist()
        ui.onNodeWithTag("navigation-clear-selection").assertDoesNotExist()
        ui.runOnIdle {assertNull(app.navigation.selected.value);assertFalse(app.navigation.state.value.active);assertTrue(app.navigation.state.value.geometry.isEmpty())}
        ui.onNodeWithText("Выберите место на карте").assertIsDisplayed()
    }
    @Test fun navigationStartChangesCameraWithoutWaitingForAnotherGpsFix() {
        openMap()
        val origin=GeoPoint(55.160,61.402)
        lateinit var map:org.maplibre.android.maps.MapLibreMap
        ui.runOnIdle {app.navigation.gps.replay(origin);mapView(ui.activity.window.decorView)!!.getMapAsync {map=it}}
        ui.waitForIdle()
        ui.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(7.0))}
        ui.runOnIdle {app.navigation.build(from=origin,to=GeoPoint(55.170,61.410))}
        ui.waitUntil(30000){app.navigation.state.value.active}
        ui.waitUntil(10000){app.navigation.camera?.zoom?.let {it>=16.49}==true}
        ui.runOnIdle {assertEquals(origin,app.navigation.gps.position.value);assertTrue(map.cameraPosition.zoom>=16.49)}
    }
    @Test fun courseUpReturnsAfterNorthModeAndManualRotationWithoutAnotherGpsFix() {
        openMap()
        val origin=GeoPoint(55.160,61.402)
        lateinit var map:org.maplibre.android.maps.MapLibreMap
        ui.runOnIdle {
            mapView(ui.activity.window.decorView)!!.getMapAsync {map=it}
            app.navigation.build(from=origin,to=GeoPoint(55.170,61.410))
        }
        ui.waitUntil(30000){app.navigation.state.value.active&&app.navigation.course.value!=null}
        val course=app.navigation.course.value!!
        fun aligned():Boolean {
            var matches=false
            ui.runOnIdle {matches=kotlin.math.abs(navigationMarkerRotation(course,map.cameraPosition.bearing))<1f}
            return matches
        }
        ui.waitUntil(10000){aligned()}
        ui.runOnIdle {
            assertNull("Explicit preview has no GPS bearing: route must supply it",app.navigation.gps.bearing.value)
            val marker=map.style!!.getLayerAs<org.maplibre.android.style.layers.SymbolLayer>("position-arrow")!!
            assertEquals(course.toFloat(),marker.iconRotate.value!!,.05f)
        }
        ui.onNodeWithTag("navigation-options").performClick()
        ui.onNodeWithText("Карта на север").performClick()
        ui.waitUntil(10000){var north=false;ui.runOnIdle {north=kotlin.math.abs(navigationMarkerRotation(0.0,map.cameraPosition.bearing))<1f};north}
        // Moving the camera does not change the GPS point or generate a new course.
        ui.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.bearingTo(navigationBearing(course+130)))}
        ui.onNodeWithTag("navigation-recenter").performClick()
        ui.waitUntil(10000){aligned()}
        ui.runOnIdle {assertEquals(origin,app.navigation.gps.position.value)}
    }
    @Test fun realHouseNumbersRenderInBothThemesOnlyAtCloseZoom() {
        assertTrue("New region must declare real source addresses",app.navigation.maps.region.value!!.houseNumberCount>1000)
        ui.runOnIdle {app.navigation.camera=NavigationCamera(GeoPoint(55.1605,61.402),17.5)}
        openMap()
        lateinit var map:org.maplibre.android.maps.MapLibreMap
        ui.runOnIdle {mapView(ui.activity.window.decorView)!!.getMapAsync {map=it}}
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.waitUntil(15000) {ui.runOnIdle {
                val view=mapView(ui.activity.window.decorView)!!
                map.queryRenderedFeatures(android.graphics.RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),"house-numbers").size>5
            }}
            ui.runOnIdle {
                val layer=map.style!!.getLayerAs<org.maplibre.android.style.layers.SymbolLayer>("house-numbers")!!
                assertEquals("addresses",layer.sourceLayer)
                assertEquals(16f,layer.minZoom,0f)
                val view=mapView(ui.activity.window.decorView)!!
                val labels=map.queryRenderedFeatures(android.graphics.RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),"house-numbers")
                assertTrue(labels.all {it.hasProperty("house_number")&&it.getStringProperty("house_number").isNotBlank()})
                assertFalse("Address node inside the same numbered building must not create a second label",
                    labels.any {it.getStringProperty("osm_ref")=="n513557986"})
            }
            captureMap("houses-${mode.name.lowercase()}")
        }
        ui.runOnIdle {map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(14.0))}
        ui.waitUntil(10000) {ui.runOnIdle {
            val view=mapView(ui.activity.window.decorView)!!
            map.queryRenderedFeatures(android.graphics.RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),"house-numbers").isEmpty()
        }}
    }
    @Test fun searchPreviewSharedRouteAndStopAreFunctional() {
        openMap()
        ui.onNodeWithTag("navigation-search-open").performClick()
        ui.onNodeWithTag("navigation-destination").performTextInput("Мира 1 Челябинск")
        ui.waitUntil(15000){ui.onAllNodesWithTag("navigation-result-0").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("navigation-result-0").performClick()
        ui.onNodeWithTag("navigation-selected").assertIsDisplayed()
        ui.runOnIdle {assertNull("Manual preview must not be used as a real GPS origin",app.navigation.gps.routeOrigin())}
        ui.onNodeWithTag("navigation-build").performClick()
        ui.onNodeWithTag("navigation-message").assertIsDisplayed()
        assertFalse(app.navigation.state.value.active)
        ui.onNodeWithTag("navigation-preview-toggle").performClick()
        ui.onNodeWithTag("navigation-origin-center").performClick()
        ui.onNodeWithTag("navigation-preview-build").performClick()
        ui.waitUntil(30000){app.navigation.state.value.active&&app.navigation.state.value.geometry.size>2}
        ui.onNodeWithTag("navigation-preview-indicator").assertIsDisplayed()
        assertTrue(app.navigation.state.value.preview)
        assertTrue(app.navigation.state.value.kilometers>0)
        ui.onNodeWithTag("navigation-overview").performClick()
        captureMap("route")
        ui.onNodeWithTag("nav-home").performClick()
        assertTrue(app.navigation.state.value.active)
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.onNodeWithTag("navigation-stop").performClick()
        ui.runOnIdle {assertFalse(app.navigation.state.value.active);assertTrue(app.navigation.state.value.geometry.isEmpty())}
        ui.onNodeWithTag("navigation-maneuver").assertDoesNotExist()
    }
    @Test fun searchAndNativeRoutesWorkWithoutHttpAndRejectOutOfRegion()=runBlocking<Unit> {
        val repository=app.navigation
        assertTrue(repository.maps.search("Ленина").isNotEmpty())
        assertTrue(repository.maps.search("42.5,1.5").isEmpty())
        val origin=GeoPoint(55.160,61.402)
        for(to in listOf(GeoPoint(55.165,61.437),GeoPoint(55.297053,61.510928),GeoPoint(55.045,60.108),GeoPoint(55.172,59.672))) {
            repository.provider.stop()
            repository.provider.route(origin,to)
            val route=withTimeout(30000){repository.state.first {it.active&&it.geometry.size>2}}
            assertTrue(route.kilometers>0);assertTrue(route.instruction.isNotBlank())
        }
        repository.provider.stop()
        assertTrue(runCatching {repository.provider.route(origin,GeoPoint(42.5,1.5))}.isFailure)
        assertFalse(repository.state.value.active)
        assertTrue(repository.state.value.geometry.isEmpty())
        assertTrue("All routes were produced by the local Valhalla actor",repository.provider.routeRequests>=4)
        assertTrue(repository.maps.graphFile.isFile)
    }
    @Test fun repeatedRouteStopsAreSafeDuringLocationUpdates()=runBlocking<Unit> {
        val repository=app.navigation
        val origin=GeoPoint(55.160,61.402)
        val updates=launch(Dispatchers.Default) {
            while(isActive){repository.gps.replay(origin);delay(2)}
        }
        try {
            repeat(12) {
                repository.provider.route(origin,GeoPoint(55.165,61.437))
                withTimeout(10000){repository.state.first {it.active&&it.geometry.size>2}}
                delay(5)
                repository.provider.stop()
            }
            delay(100)
            assertFalse(repository.state.value.active)
            assertTrue(repository.state.value.geometry.isEmpty())
        }finally {updates.cancelAndJoin();repository.provider.stop()}
    }
    @Test fun ordinaryTapSelectsHouseAndBuildsRouteWithoutTyping() {
        val house=GeoPoint(55.16083795,61.4024727)
        ui.runOnIdle {app.navigation.camera=NavigationCamera(house,17.5)}
        openMap()
        // Native camera animations are not part of Compose's idle clock. Freeze this
        // hit-test frame before projecting the touch, otherwise follow can move the house.
        ui.runOnIdle {mapView(ui.activity.window.decorView)!!.getMapAsync {map->
            map.cancelTransitions()
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(org.maplibre.android.geometry.LatLng(house.latitude,house.longitude),17.5))
        }}
        ui.waitForIdle()
        var tap=androidx.compose.ui.geometry.Offset.Zero
        ui.runOnIdle {mapView(ui.activity.window.decorView)!!.getMapAsync {map->
            val p=map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(house.latitude,house.longitude))
            tap=androidx.compose.ui.geometry.Offset(p.x,p.y)
            assertTrue(map.queryRenderedFeatures(p,"buildings").isNotEmpty())
        }}
        ui.onNodeWithTag("offline-map").performTouchInput {click(tap)}
        try {ui.waitUntil(10000){app.navigation.selected.value?.kind=="building"}}
        catch(e:Exception){captureMap("tap-failure");throw AssertionError("Tap at $tap selected ${app.navigation.selected.value}",e)}
        assertTrue("Correct house address, not a neighboring label",app.navigation.selected.value!!.name.contains("54"))
        ui.onNodeWithTag("navigation-build").assertIsEnabled()
        captureMap("house-selected")
        // No pretend GPS: build through the explicit preview controls in the emulator.
        ui.onNodeWithTag("navigation-preview-toggle").performClick()
        ui.onNodeWithTag("navigation-origin-center").performClick()
        ui.onNodeWithTag("navigation-preview-build").performClick()
        ui.waitUntil(30000){app.navigation.state.value.active&&app.navigation.state.value.destination.contains("54")}
        assertTrue(app.navigation.state.value.geometry.size>2)
    }
    @Test fun homeShowsIndependentCloseRouteAndLargeManeuver() {
        openMap()
        ui.runOnIdle {app.navigation.select(NavigationPlace("Златоуст",GeoPoint(55.172,59.672)));app.navigation.build(from=GeoPoint(55.160,61.402))}
        ui.waitUntil(30000){app.navigation.state.value.active&&app.navigation.state.value.geometry.size>2}
        ui.onNodeWithTag("navigation-overview").performClick()
        ui.onNodeWithTag("nav-home").performClick()
        ui.waitUntil(30000){app.navigation.previewImage.value?.let {it.routeKey==app.navigation.state.value.geometry.hashCode()&&it.focus!=null}==true}
        ui.onNodeWithTag("navigation-widget-map").assertIsDisplayed()
        val arrow=ui.onNodeWithTag("navigation-widget-maneuver").fetchSemanticsNode().boundsInRoot
        assertTrue("Prominent maneuver arrow, not old 26dp position icon",arrow.width>=60&&arrow.height>=60)
        val preview=app.navigation.previewImage.value!!
        val colors=mutableSetOf<Int>();var routePixels=0
        for(y in 0 until preview.bitmap.height step 2)for(x in 0 until preview.bitmap.width step 2) {
            val color=preview.bitmap.getPixel(x,y);colors+=color
            if(android.graphics.Color.red(color)<100&&android.graphics.Color.green(color)>150&&android.graphics.Color.blue(color)>190)routePixels++
        }
        assertTrue("Real local roads and labels, not a blank image",colors.size>40)
        assertTrue("Actual route line is rendered in the snapshot",routePixels>40)
        assertTrue("A local segment, not the entire 90+ km route",preview.focus!!.aheadMeters<=850)
        assertTrue("Overview camera must not leak into home",preview.focus!!.zoom>12)
        assertTrue(preview.focus!!.points.maxOf {it.longitude}-preview.focus!!.points.minOf {it.longitude}<.03)
        val guidance=ui.onNodeWithTag("navigation-widget-guidance").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertFalse(guidance.contains("Налево · Поверните"))
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
        File(folder,"home-focused-map.png").outputStream().use {preview.bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val capture=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/home-focused-screen.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(capture).use {while(it.read()!=-1){}}
        // A different theme gets its own snapshot, without retaining a dark screenshot.
        ui.runOnIdle {app.theme.setMode(ThemeMode.LIGHT)}
        ui.waitUntil(30000){app.navigation.previewImage.value?.dark==false}
        ui.onNodeWithTag("navigation-widget-map").assertIsDisplayed()
        File(folder,"home-focused-light-map.png").outputStream().use {app.navigation.previewImage.value!!.bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val lightCapture=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/home-focused-light-screen.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(lightCapture).use {while(it.read()!=-1){}}
    }
    @Test fun russiaOverviewIsRealLocalMapAndDownloadsRequireExplicitConsent() {
        openMap()
        ui.onNodeWithTag("navigation-options").performClick()
        ui.onNodeWithTag("navigation-maps").performScrollTo().performClick()
        ui.onNodeWithTag("map-download-url").performScrollTo().performTextInput("https://example.org/region.jelandnav.zip")
        ui.onNodeWithTag("map-download-start").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("navigation-russia-overview").performScrollTo().performClick()
        lateinit var map:org.maplibre.android.maps.MapLibreMap
        ui.runOnIdle {mapView(ui.activity.window.decorView)!!.getMapAsync {map=it}}
        ui.waitUntil(20000){ui.runOnIdle {
            val view=mapView(ui.activity.window.decorView)!!
            map.cameraPosition.zoom in 3.9..4.1&&map.queryRenderedFeatures(android.graphics.RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),"overview-roads").isNotEmpty()
        }}
        assertTrue(app.navigation.maps.overviewFile().length()>1000000)
        for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(mode)}
            ui.waitUntil(15000){ui.runOnIdle {
                val view=mapView(ui.activity.window.decorView)!!
                map.style?.isFullyLoaded==true&&map.style?.getLayerAs<org.maplibre.android.style.layers.FillLayer>("overview-earth")?.fillColorAsInt==android.graphics.Color.parseColor(if(mode==ThemeMode.DARK)"#102b3a"else"#e6e5de")&&
                    map.queryRenderedFeatures(android.graphics.RectF(0f,0f,view.width.toFloat(),view.height.toFloat()),"overview-places").isNotEmpty()
            }}
            // A low-zoom base map has fewer sampled colors than a dense city. Actual
            // roads and labels are separately verified above, not inferred from colors.
            captureMap("russia-${mode.name.lowercase()}",12)
        }
        assertFalse("Overview is not a routing graph for all of Russia",app.navigation.maps.region.value!!.contains(GeoPoint(55.75,37.61)))
    }
}
