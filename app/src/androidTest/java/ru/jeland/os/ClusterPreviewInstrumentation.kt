package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Canvas
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
    @Test fun fourDesignsUseSameSessionAndNavigationSurfaceAndRestoreSelection() {
        assumeTrue(BuildConfig.LOCAL_DEMO)
        val app=(ui.activity.application as JelandApplication).container
        val previous=app.preferences.state.value;val theme=app.theme.mode.value
        val car=app.car;val navigation=app.navigation.comaps;val media=app.media
        val previousMedia=media.state.value
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster").apply {mkdirs()}
        fun capture(name:String){
            val bitmap=if(name.contains("navigation")) {
                Thread.sleep(900)
                val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                automation.executeShellCommand("screencap -p").use {fd->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {checkNotNull(android.graphics.BitmapFactory.decodeStream(it))}
                }
            }else if(name.startsWith("expanded-"))ui.onNodeWithTag("cluster-preview-expanded").captureToImage().asAndroidBitmap()
            else ui.onRoot().captureToImage().asAndroidBitmap()
            File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            if(name.contains("navigation")) {
                val bounds=ui.onNodeWithTag("cluster-stage").fetchSemanticsNode().boundsInRoot
                val crop=Bitmap.createBitmap(bitmap,bounds.left.roundToInt(),bounds.top.roundToInt(),bounds.width.roundToInt(),bounds.height.roundToInt())
                File(folder,"stage-$name.png").outputStream().use {crop.compress(Bitmap.CompressFormat.PNG,100,it)}
                crop.recycle()
            }
        }
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(previous)}
            ui.onNodeWithTag("nav-settings").performScrollTo().performClick()
            ui.onNodeWithTag("open-cluster-preview").performScrollTo().performClick()
            ui.onNodeWithTag("cluster-preview-page").assertIsDisplayed()
            for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT))for(design in ClusterDesign.entries) {
                ui.runOnIdle {app.theme.setMode(mode)}
                ui.onNodeWithTag("cluster-design-${design.name.lowercase()}").performScrollTo().performClick()
                ui.waitUntil {app.preferences.state.value.clusterDesign==design}
                ui.onNodeWithTag("cluster-artwork").performScrollTo().assertIsDisplayed()
                assertSame(car,app.car);assertSame(navigation,app.navigation.comaps)
                if(design==ClusterDesign.NAVIGATION){
                    ui.waitUntil(30000){navigation.ready.value||navigation.error.value!=null}
                    assertNull(navigation.error.value)
                    ui.onNodeWithTag("comaps-map").assertExists()
                    val stage=ui.onNodeWithTag("cluster-stage").fetchSemanticsNode().boundsInRoot
                    val map=ui.onNodeWithTag("comaps-map").fetchSemanticsNode().boundsInRoot
                    assertTrue("Map fills the cluster width",map.width>stage.width*.95f)
                    assertTrue("Map fills most of the cluster height",map.height>stage.height*.75f)
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
                        ui.onNode(inPlayer and hasTestTag("cluster-music-title")).assertTextContains(media.state.value.track.title)
                        ui.runOnIdle {media.previous()}
                        ui.waitUntil {media.state.value.track==before.track}
                    }
                }
                capture("page-${design.name.lowercase()}-${mode.name.lowercase()}")
                if(design==ClusterDesign.NAVIGATION&&mode==ThemeMode.DARK) {
                    ui.onNodeWithTag("expand-cluster-preview").performScrollTo().performClick()
                    ui.onNodeWithTag("cluster-music").assertIsDisplayed()
                    capture("expanded-navigation")
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
            if(!previousMedia.live) {
                media.selectTrack(previousMedia.index)
                if(media.state.value.playing!=previousMedia.playing)media.togglePlay()
                media.seek(previousMedia.position)
            }
            app.preferences.change(previous);app.theme.setMode(theme)
        }}
    }
}
