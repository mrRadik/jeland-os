package ru.jeland.os

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import java.io.File

class FreeWidgetResizeInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun allCornersResizeSquaresWrapAndOverlappingLayersPersist() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val emulator=instrumentation.uiAutomation.executeShellCommand("getprop init.svc.bstsvcmgrtest").use {fd->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use {it.readText().trim()}
        }
        assumeTrue(emulator=="running")
        val app=(ui.activity.application as JelandApplication).container
        val original=app.preferences.state.value
        val initial=FreeDashboardLayout(listOf(
            DashboardWidget(DashboardWidgetKind.ACTIONS,0,0,columns=6,rows=6),
            DashboardWidget(DashboardWidgetKind.FAVORITES,6,0,columns=6,rows=6),
            DashboardWidget(DashboardWidgetKind.MUSIC,0,7,DashboardWidgetSize.COMPACT),
            DashboardWidget(DashboardWidgetKind.CLOCK,2,7,columns=5,rows=4)))
        try {
            ui.runOnIdle {app.firstRun.finish();app.preferences.change(original.copy(style=DashboardStyle.FREE,freeLayout=initial))}
            ui.onNodeWithTag("nav-home").performClick()
            val steering=ui.onNodeWithTag("quick-steering").fetchSemanticsNode().boundsInRoot
            val cameras=ui.onNodeWithTag("quick-cameras").fetchSemanticsNode().boundsInRoot
            val firstApp=ui.onNodeWithTag("favorite-home-card-0").fetchSemanticsNode().boundsInRoot
            val thirdApp=ui.onNodeWithTag("favorite-home-card-2").fetchSemanticsNode().boundsInRoot
            assertTrue(cameras.top>=steering.bottom)
            assertTrue(thirdApp.top>=firstApp.bottom)
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-CLOCK").performClick()
            val cell=ui.onNodeWithTag("free-canvas").fetchSemanticsNode().boundsInRoot.width/12f
            fun resize(corner:WidgetCorner,dx:Int,dy:Int) {
                val handle=ui.onNodeWithTag("free-resize-CLOCK-${corner.name}",useUnmergedTree=true)
                handle.performScrollTo()
                android.util.Log.i("FreeResizeTest","${corner.name}: ${handle.fetchSemanticsNode().boundsInRoot}")
                val folder=File(ui.activity.getExternalFilesDir(null),"qa-free-resize").apply {mkdirs()}
                File(folder,"resize-${corner.name}.png").outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
                handle.assertIsDisplayed().performTouchInput {
                    swipe(center,center+Offset(cell*dx,cell*dy),600)
                }
            }
            resize(WidgetCorner.BOTTOM_RIGHT,1,1)
            resize(WidgetCorner.TOP_LEFT,1,1)
            resize(WidgetCorner.TOP_RIGHT,1,-1)
            resize(WidgetCorner.BOTTOM_LEFT,-1,1)
            assertEquals(initial,app.preferences.state.value.freeLayout)
            ui.onNodeWithTag("free-save").performClick()
            val saved=app.preferences.state.value.freeLayout
            val clock=saved.widgets.first {it.kind==DashboardWidgetKind.CLOCK}
            assertEquals(2,clock.x);assertEquals(7,clock.y)
            assertEquals(WidgetFootprint(7,6),clock.footprint)
            assertTrue(clock.overlaps(saved.widgets.first {it.kind==DashboardWidgetKind.MUSIC}))
            assertEquals(saved,FreeDashboardLayout.decode(saved.encode()))
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-CLOCK").performClick()
            ui.onNodeWithTag("free-lower").performClick()
            ui.onNodeWithTag("free-add").performClick()
            ui.onNodeWithTag("free-add-CLOCK").performClick()
            ui.onNodeWithTag("free-resize-CLOCK-TOP_LEFT",useUnmergedTree=true).assertExists()
            ui.onNodeWithTag("free-cancel").performClick()
            assertEquals(saved,app.preferences.state.value.freeLayout)
            ui.onNodeWithTag("free-edit").performClick()
            ui.onNodeWithTag("free-select-ACTIONS").performClick()
            ui.onNodeWithTag("free-square").performClick()
            ui.onNodeWithTag("free-save").performClick()
            ui.waitForIdle();Thread.sleep(400)
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-free-resize").apply {mkdirs()}
            File(folder,"squares-and-overlap.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
        }finally {ui.runOnIdle {app.preferences.change(original)}}
    }
}
