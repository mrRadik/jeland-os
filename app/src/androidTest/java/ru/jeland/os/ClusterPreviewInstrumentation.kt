package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Canvas
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.downloader.CountryItem
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File
import kotlin.math.roundToInt

class ClusterPreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun previewHasExplicitOutputButtonsAndDoesNotApplySelection() {
        assumeTrue(BuildConfig.LOCAL_DEMO)
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value
        assumeTrue(app.clusterProjection.applied.value==null)
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(previous.copy(clusterDesign=ClusterDesign.CLASSIC))}
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("show-cluster-output").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("restore-stock-cluster").assertIsDisplayed()
            ui.runOnIdle {assertNull(app.clusterProjection.applied.value)}
            ui.onNodeWithTag("cluster-design-sport").performScrollTo().performClick()
            ui.runOnIdle {assertNull(app.clusterProjection.applied.value)}
            ui.onNodeWithTag("leave-cluster-preview").performScrollTo()
            ui.onNodeWithTag("cluster-preview-page").captureToImage().asAndroidBitmap().let {bitmap->
                File(ui.activity.getExternalFilesDir(null),"cluster-header-110.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            ui.onNodeWithTag("expand-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("show-cluster-output").assertIsDisplayed()
            ui.onNodeWithTag("restore-stock-cluster").assertIsDisplayed()
            ui.onNodeWithTag("close-cluster-preview").performClick()
            ui.onNodeWithTag("leave-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().assertIsDisplayed().performClick()
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-preview-page").assertDoesNotExist()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().assertIsDisplayed()
        }finally {ui.runOnIdle {app.preferences.change(previous)}}
    }
    @Test fun navigationWidgetModeSwitchesAcrossDesignsAndSurvivesReopening() {
        assumeTrue(BuildConfig.LOCAL_DEMO)
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-navigation-widget").apply {mkdirs()}
        fun capture(name:String) {
            ui.onNodeWithTag("cluster-artwork").performScrollTo()
            Thread.sleep(900)
            val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            val bitmap=automation.executeShellCommand("screencap -p").use {fd->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {checkNotNull(android.graphics.BitmapFactory.decodeStream(it))}
            }
            val b=ui.onNodeWithTag("cluster-stage").fetchSemanticsNode().boundsInWindow
            val crop=Bitmap.createBitmap(bitmap,b.left.roundToInt(),b.top.roundToInt(),b.width.roundToInt(),b.height.roundToInt())
            File(folder,"$name.png").outputStream().use {crop.compress(Bitmap.CompressFormat.PNG,100,it)}
            crop.recycle();bitmap.recycle()
        }
        try {
            ui.runOnIdle {
                app.firstRun.finish();app.navigation.comaps.stop()
                app.preferences.change(previous.copy(clusterDesign=ClusterDesign.CLASSIC,clusterNavigationMode=ClusterNavigationMode.MAP))
            }
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().performClick()
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                ui.onNodeWithTag("cluster-design-${design.name.lowercase()}").performScrollTo().performClick()
                for(mode in listOf(ClusterNavigationMode.GUIDANCE,ClusterNavigationMode.MAP)) {
                    ui.onNodeWithTag("cluster-navigation-${mode.name.lowercase()}").performScrollTo().performClick()
                    ui.runOnIdle {assertEquals(mode,app.preferences.state.value.clusterNavigationMode)}
                    ui.onNodeWithTag("cluster-artwork").performScrollTo()
                    if(mode==ClusterNavigationMode.MAP)ui.onNodeWithTag("comaps-map").assertExists()
                    else ui.onNodeWithTag("comaps-map").assertDoesNotExist()
                    ui.onNodeWithTag("cluster-route-guidance").assertDoesNotExist()
                    capture("${design.name.lowercase()}-${mode.name.lowercase()}-idle")
                }
            }
            ui.onNodeWithTag("cluster-navigation-guidance").performScrollTo().performClick()
            ui.onNodeWithTag("leave-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-artwork").performScrollTo()
            ui.onNodeWithTag("comaps-map").assertDoesNotExist()
            ui.runOnIdle {assertEquals(ClusterNavigationMode.GUIDANCE,app.preferences.state.value.clusterNavigationMode)}
        }finally {ui.runOnIdle {app.preferences.change(previous)}}
    }
    @Test fun fourDesignsUseSameSessionAndNavigationSurfaceAndRestoreSelection() {
        assumeTrue(BuildConfig.LOCAL_DEMO)
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value;val theme=app.theme.mode.value
        val car=app.car;val navigation=app.navigation.comaps;val media=app.media
        val previousMedia=media.state.value
        var startedDemoRoute=false
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster").apply {mkdirs()}
        fun capture(name:String){
            val bitmap=if(!name.contains("original")) {
                Thread.sleep(900)
                val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.executeShellCommand("screencap -p").use {fd->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {checkNotNull(android.graphics.BitmapFactory.decodeStream(it))}
                }
            }else if(name.startsWith("expanded-"))ui.onNodeWithTag("cluster-preview-expanded").captureToImage().asAndroidBitmap()
            else ui.onRoot().captureToImage().asAndroidBitmap()
            File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            if(!name.contains("original")) {
                val bounds=ui.onNodeWithTag("cluster-stage").fetchSemanticsNode().boundsInWindow
                val crop=Bitmap.createBitmap(bitmap,bounds.left.roundToInt(),bounds.top.roundToInt(),bounds.width.roundToInt(),bounds.height.roundToInt())
                File(folder,"stage-$name.png").outputStream().use {crop.compress(Bitmap.CompressFormat.PNG,100,it)}
                crop.recycle()
            }
        }
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(previous.copy(clusterAccent=ClusterAccent.CYAN,clusterColorMode=ClusterColorMode.MANUAL,clusterNavigationMode=ClusterNavigationMode.MAP))}
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-preview-page").assertIsDisplayed()
            for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT))for(design in ClusterDesign.entries) {
                ui.runOnIdle {app.theme.setMode(mode)}
                ui.onNodeWithTag("cluster-design-${design.name.lowercase()}").performScrollTo().performClick()
                ui.waitUntil {app.preferences.state.value.clusterDesign==design}
                ui.onNodeWithTag("cluster-artwork").performScrollTo().assertIsDisplayed()
                assertSame(car,app.car);assertSame(navigation,app.navigation.comaps)
                if(design!=ClusterDesign.ORIGINAL){
                    ui.waitUntil(30000){navigation.ready.value||navigation.error.value!=null}
                    assertNull(navigation.error.value)
                    if(design==ClusterDesign.CLASSIC&&mode==ThemeMode.DARK&&!navigation.state.value.active) {
                        ui.runOnIdle {
                            if(MapManager.nativeGetStatus("Andorra")==CountryItem.STATUS_DONE) {
                                navigation.startDemoTrip();startedDemoRoute=true
                            }
                        }
                    }
                    if(startedDemoRoute&&navigation.state.value.geometry.isEmpty()) {
                        ui.waitUntil(30000){navigation.state.value.geometry.size>1}
                        assertTrue("Downloaded map builds a real demo route",navigation.state.value.geometry.size>1)
                    }
                    if(startedDemoRoute) {
                        ui.waitUntil(30000){navigation.state.value.active&&!navigation.state.value.preview}
                        ui.runOnIdle {
                            val panel=ClusterPanelState.from(UiState(car=app.car.link.value,navigation=navigation.state.value),null,null,android.os.SystemClock.elapsedRealtime())
                            assertNotNull("The simulator must show the same route guidance as the map",panel.navigation)
                        }
                    }
                    if(startedDemoRoute) {
                        ui.onNodeWithTag("cluster-route-guidance").assertExists()
                        ui.runOnIdle {app.preferences.change(app.preferences.state.value.copy(clusterNavigationMode=ClusterNavigationMode.GUIDANCE))}
                        ui.onNodeWithTag("comaps-map").assertDoesNotExist()
                        ui.onNodeWithTag("cluster-route-guidance").assertExists()
                        capture("guidance-${design.name.lowercase()}-${mode.name.lowercase()}")
                        ui.runOnIdle {app.preferences.change(app.preferences.state.value.copy(clusterNavigationMode=ClusterNavigationMode.MAP))}
                    }
                    ui.onNodeWithTag("comaps-map").assertExists()
                    val stage=ui.onNodeWithTag("cluster-stage").fetchSemanticsNode().boundsInRoot
                    val map=ui.onNodeWithTag("comaps-map").fetchSemanticsNode().boundsInRoot
                    if(design==ClusterDesign.NAVIGATION) {
                        assertTrue("Map fills the cluster width",map.width>stage.width*.95f)
                        assertTrue("Map fills most of the cluster height",map.height>stage.height*.75f)
                    }else {
                        ui.onNodeWithTag("cluster-minimap").assertExists()
                        assertTrue("Mini-map stays inside the cluster",map.left>=stage.left&&map.right<=stage.right&&map.top>=stage.top&&map.bottom<=stage.bottom)
                        assertTrue("Mini-map leaves space for gauges",map.width<stage.width*.3f)
                        val music=ui.onNodeWithTag("cluster-music").fetchSemanticsNode().boundsInRoot
                        assertTrue("Music and map do not overlap",music.top>=map.bottom||music.bottom<=map.top||music.right<=map.left||music.left>=map.right)
                    }
                }
                assertSame(media,app.media)
                if(design==ClusterDesign.ORIGINAL)ui.onNodeWithTag("cluster-music").assertDoesNotExist()
                else {
                    ui.onNodeWithTag("cluster-music").assertIsDisplayed()
                    val inPlayer=hasAnyAncestor(hasTestTag("cluster-music"))
                    ui.onNode(inPlayer and hasTestTag("cluster-music-title")).assertExists()
                    ui.onNode(inPlayer and hasTestTag("minimal-music-progress")).assertExists()
                    ui.onAllNodes(inPlayer and hasClickAction()).assertCountEquals(0)
                    if(mode==ThemeMode.DARK) {
                        val before=media.state.value
                        ui.runOnIdle {media.next()}
                        ui.waitUntil {media.state.value.track!=before.track}
                        ui.onNode(inPlayer and hasTestTag("cluster-music-title")).assertTextContains(media.state.value.track.title,substring=true)
                        ui.runOnIdle {media.previous()}
                        ui.waitUntil {media.state.value.track==before.track}
                    }
                }
                capture("page-${design.name.lowercase()}-${mode.name.lowercase()}")
                if(design!=ClusterDesign.ORIGINAL&&mode==ThemeMode.DARK) {
                    ui.onNodeWithTag("expand-cluster-preview").performScrollTo().performClick()
                    ui.onNodeWithTag("cluster-music").assertIsDisplayed()
                    capture("expanded-${design.name.lowercase()}")
                    ui.onNodeWithTag("close-cluster-preview").performClick()
                }
            }
            ui.runOnIdle {app.theme.setMode(ThemeMode.DARK)}
            ui.onNodeWithTag("cluster-design-classic").performScrollTo().performClick()
            for(accent in ClusterAccent.entries) {
                ui.runOnIdle {app.preferences.change(app.preferences.state.value.copy(clusterAccent=accent,clusterColorMode=ClusterColorMode.MANUAL))}
                ui.waitUntil {app.preferences.state.value.clusterAccent==accent}
                ui.onNodeWithTag("cluster-artwork").performScrollTo().assertIsDisplayed()
                capture("native-${accent.name.lowercase()}")
            }
            ui.onNodeWithTag("expand-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-preview-expanded").assertIsDisplayed()
            ui.onNodeWithTag("cluster-music").assertIsDisplayed()
            capture("expanded-classic-music")
            ui.onNodeWithTag("close-cluster-preview").performClick()
            ui.onNodeWithTag("nav-home").performScrollTo().performClick()
            ui.onNodeWithTag("nav-navigation").performScrollTo().performClick()
            ui.onNodeWithTag("comaps-map").assertExists()
            assertSame(navigation,app.navigation.comaps)

            // Only a QA render fixture uses illustrative values. No fixture is in product sources.
            val frame=ClusterPanelState(speed=78.0,rpm=2150.0,gear="D",fuel=.43,fuelLiters=23.7,
                outside=12.5,voltage=14.4,tripKm=12.5,tripMillis=540000,
                navigation=NavigationState(demo=false,distanceToManeuver=240,street="улица Лесная"))
            val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
            val renderer=ClusterRenderer(ui.activity.resources)
            try {for(dark in listOf(true,false))for(design in ClusterDesign.entries) {
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap),frame,design,dark,"16:42",labels,78f,2150f,miniature=true)
                File(folder,"fixture-${design.name.lowercase()}-${if(dark)"dark"else"light"}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
            for(dark in listOf(true,false)) {
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap),ClusterPanelState(),ClusterDesign.CLASSIC,dark,"16:42",labels,null,null,miniature=true)
                File(folder,"fixture-classic-empty-${if(dark)"dark"else"light"}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
            for(accent in ClusterAccent.entries) {
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap),frame,ClusterDesign.CLASSIC,true,"16:42",labels,78f,2150f,
                    miniature=true,accent=accent)
                File(folder,"fixture-native-${accent.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }}finally {renderer.close()}
        }finally {ui.runOnIdle {
            if(startedDemoRoute)navigation.stop(false)
            if(!previousMedia.live) {
                media.selectTrack(previousMedia.index)
                if(media.state.value.playing!=previousMedia.playing)media.togglePlay()
                media.seek(previousMedia.position)
            }
            app.preferences.change(previous);app.theme.setMode(theme)
        }}
    }
}
