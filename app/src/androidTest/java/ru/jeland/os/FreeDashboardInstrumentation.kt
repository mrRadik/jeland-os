package ru.jeland.os

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import ru.jeland.os.core.*
import java.io.File

class FreeDashboardInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun onlyLocalEmulator() {
        val emulator=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop init.svc.bstsvcmgrtest").use {fd->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use {it.readText().trim()}
        }
        assumeTrue(emulator=="running")
    }
    @Test fun freeAppearanceCanBeSelectedAndExistingWidgetsRenderInBothThemes() {
        val app=(ui.activity.application as JelandApplication).container
        val original=app.preferences.state.value
        val theme=app.theme.mode.value
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        try {
            android.util.Log.i("FreeDashboardTest","Initialize minimal appearance")
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(original.copy(style=DashboardStyle.MINIMAL,freeLayout=FreeDashboardLayout.default()))}
            android.util.Log.i("FreeDashboardTest","Open settings")
            ui.onNodeWithTag("nav-settings").performClick()
            android.util.Log.i("FreeDashboardTest","Choose free appearance")
            ui.onNodeWithTag("style-free").performScrollTo().performClick()
            assertEquals(DashboardStyle.FREE,app.preferences.state.value.style)
            ui.onNodeWithTag("nav-home").performClick()
            android.util.Log.i("FreeDashboardTest","Free home opened")
            for(mode in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {app.theme.setMode(mode)}
                ui.onNodeWithTag("free-widget-NAVIGATION").assertIsDisplayed()
                ui.onNodeWithTag("free-widget-MUSIC").assertIsDisplayed()
                ui.onNodeWithTag("free-widget-VEHICLE").assertIsDisplayed()
                ui.onNodeWithTag("free-edit").assertIsDisplayed()
                val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
                val dock=ui.onNodeWithTag("climate-bar").fetchSemanticsNode().boundsInRoot
                val canvas=ui.onNodeWithTag("free-viewport").fetchSemanticsNode().boundsInRoot
                assertTrue(canvas.bottom<=dock.top)
                assertTrue(dock.bottom<=root.bottom)
                ui.waitForIdle();Thread.sleep(800)
                val folder=File(ui.activity.getExternalFilesDir(null),"qa-free-dashboard").apply {mkdirs()}
                File(folder,"home-${mode.name.lowercase()}.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            }
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-NAVIGATION").performClick()
            ui.onNodeWithTag("free-size-COMPACT").performClick()
            ui.onNodeWithTag("free-move-2").performClick()
            val resizeCell=ui.onNodeWithTag("free-canvas").fetchSemanticsNode().boundsInRoot.width/12f
            ui.onNodeWithTag("free-resize-NAVIGATION-BOTTOM_RIGHT",useUnmergedTree=true).performScrollTo().performTouchInput {
                swipe(center,center+Offset(resizeCell,resizeCell),600)
            }
            ui.onNodeWithTag("free-save").performClick()
            val map=app.preferences.state.value.freeLayout.widgets.first {it.kind==DashboardWidgetKind.NAVIGATION}
            assertEquals(DashboardWidgetSize.COMPACT,map.size);assertEquals(3,map.y)
            assertEquals(WidgetFootprint(5,5),map.footprint)
            ui.onNodeWithTag("free-widget-NAVIGATION").performTouchInput {click()}
            ui.onNodeWithTag("comaps-navigation-screen").assertIsDisplayed()
            ui.onNodeWithTag("nav-home").performClick()
            ui.onNodeWithTag("free-widget-NAVIGATION").assertIsDisplayed()
        }finally {ui.runOnIdle {app.preferences.change(original);app.theme.setMode(theme)}}
    }
    @Test fun editorMovesResizesAddsAndPersistsExistingWidgetsAndCancelDoesNotApply() {
        val app=(ui.activity.application as JelandApplication).container
        val original=app.preferences.state.value
        val originalPlaying=app.media.state.value.playing
        val initial=FreeDashboardLayout(listOf(DashboardWidget(DashboardWidgetKind.MUSIC,0,0),DashboardWidget(DashboardWidgetKind.CLOCK,8,0)))
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(original.copy(style=DashboardStyle.FREE,freeLayout=initial))}
            ui.onNodeWithTag("nav-home").performClick()
            ui.onNodeWithTag("free-home").assertIsDisplayed()
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-MUSIC").performClick()
            ui.onNodeWithTag("media-play").assertDoesNotExist()
            assertEquals(originalPlaying,app.media.state.value.playing)
            val cell=ui.onNodeWithTag("free-canvas").fetchSemanticsNode().boundsInRoot.width/12f
            ui.onNodeWithTag("free-select-MUSIC").performTouchInput {
                swipe(Offset(50f,50f),Offset(50f+2*cell,50f+3*cell),600)
            }
            ui.onNodeWithTag("free-size-COMPACT").performClick()
            ui.waitForIdle()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-free-dashboard").apply {mkdirs()}
            File(folder,"editor.png").outputStream().use {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            assertEquals("Draft must not modify saved preferences",initial,app.preferences.state.value.freeLayout)
            ui.onNodeWithTag("free-save").performClick()
            val saved=app.preferences.state.value.freeLayout
            val music=saved.widgets.first {it.kind==DashboardWidgetKind.MUSIC}
            assertEquals(2,music.x);assertEquals(3,music.y);assertEquals(DashboardWidgetSize.COMPACT,music.size)
            ui.onNodeWithTag("media-play").assertIsDisplayed().performClick()
            ui.waitUntil(5_000){app.media.state.value.playing!=originalPlaying}
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-MUSIC").performClick()
            ui.onNodeWithTag("free-move-3").performClick()
            ui.onNodeWithTag("free-cancel").performClick()
            assertEquals(saved,app.preferences.state.value.freeLayout)
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-MUSIC").performClick()
            ui.onNodeWithTag("free-remove").performClick()
            ui.onNodeWithTag("free-add").performClick()
            ui.onNodeWithTag("free-add-MUSIC").performClick()
            ui.onNodeWithTag("free-save").performClick()
            assertEquals(DashboardWidgetSize.MEDIUM,app.preferences.state.value.freeLayout.widgets.first {it.kind==DashboardWidgetKind.MUSIC}.size)
            val added=app.preferences.state.value.freeLayout
            ui.onNodeWithTag("nav-music").performClick()
            ui.onNodeWithTag("nav-home").performClick()
            ui.onNodeWithTag("free-widget-MUSIC").assertIsDisplayed()
            assertEquals(added,app.preferences.state.value.freeLayout)
        }finally {
            ui.runOnIdle {
                app.preferences.change(original)
                if(app.media.state.value.playing!=originalPlaying)app.media.togglePlay()
            }
        }
    }
}
