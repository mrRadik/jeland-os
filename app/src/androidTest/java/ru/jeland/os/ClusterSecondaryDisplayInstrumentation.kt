package ru.jeland.os

import android.app.Activity
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.cluster.ClusterDisplayActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.lifecycle.Lifecycle
import ru.jeland.os.navigation.comaps.mainMapSurface

/** Real ActivityOptions routing and Compose rendering on an app-owned Android secondary display. */
class ClusterSecondaryDisplayInstrumentation {
    @Test fun allDesignsRenderOnSecondaryAndOriginalRestoresMainApp() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val context=instrument.targetContext
        val app=(context.applicationContext as JelandApplication).container
        val previous=app.preferences.state.value
        val reader=ImageReader.newInstance(1920,720,PixelFormat.RGBA_8888,3)
        val handler=Handler(Looper.getMainLooper())
        reader.setOnImageAvailableListener({it.acquireLatestImage()?.close()},handler)
        val display=context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
            "Jeland PP QA",1920,720,160,reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
        val folder=File(context.getExternalFilesDir(null),"cluster-secondary-display").apply {mkdirs()}
        // Android's app-owned virtual displays require embedding permission, unlike
        // the system-owned physical PP. Adopt it only in this test process.
        instrument.uiAutomation.adoptShellPermissionIdentity("android.permission.ACTIVITY_EMBEDDING")
        var scenario:ActivityScenario<MainActivity>?=null
        fun secondary():ClusterDisplayActivity? {
            var found:ClusterDisplayActivity?=null
            instrument.runOnMainSync {
                found=Stage.values().flatMap {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)}
                    .filterIsInstance<ClusterDisplayActivity>().firstOrNull {!it.isFinishing&&!it.isDestroyed}
            }
            return found
        }
        try {
            instrument.runOnMainSync {app.preferences.change(previous.copy(clusterDesign=ClusterDesign.ORIGINAL));app.firstRun.finish()}
            scenario=ActivityScenario.launch(MainActivity::class.java)
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                val previousOutput=app.clusterProjection.applied.value
                instrument.runOnMainSync {app.preferences.change(app.preferences.state.value.copy(clusterDesign=design))}
                Thread.sleep(150)
                instrument.runOnMainSync {
                    assertEquals(previousOutput,app.clusterProjection.applied.value)
                    app.clusterProjection.show()
                }
                val deadline=System.currentTimeMillis()+15000
                while(secondary()==null&&System.currentTimeMillis()<deadline)Thread.sleep(100)
                val activity=checkNotNull(secondary()){"PP Activity was not launched"}
                assertEquals(display.display.displayId,activity.display?.displayId)
                Thread.sleep(1500)
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                val done=CountDownLatch(1);var result=-1
                instrument.runOnMainSync {PixelCopy.request(activity.window,bitmap,{result=it;done.countDown()},handler)}
                assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
                // Header must survive every renderer/background switch, not just the first design.
                for((left,right) in listOf(680 to 825,910 to 1010,1070 to 1225)) {
                    var ink=0
                    for(y in 15..52)for(x in left..right) {
                        val c=bitmap.getPixel(x,y)
                        if(android.graphics.Color.red(c)>170&&android.graphics.Color.green(c)>170&&android.graphics.Color.blue(c)>170)ink++
                    }
                    assertTrue("$design header text visible at $left",ink>35)
                }
                File(folder,"${design.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                instrument.runOnMainSync {assertTrue(app.dashboardVisible.value)}
            }
            // Pausing GU must keep PP telemetry alive and transfer the single map engine.
            scenario.moveToState(Lifecycle.State.STARTED)
            Thread.sleep(1000)
            instrument.runOnMainSync {assertTrue(app.dashboardVisible.value);assertNull(mainMapSurface)}
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(1000)
            instrument.runOnMainSync {assertTrue(app.dashboardVisible.value);assertNotNull(mainMapSurface)}
            instrument.runOnMainSync {app.preferences.change(app.preferences.state.value.copy(clusterDesign=ClusterDesign.ORIGINAL))}
            Thread.sleep(150)
            assertNotNull(secondary())
            instrument.runOnMainSync {app.clusterProjection.restoreStock()}
            val deadline=System.currentTimeMillis()+5000
            while(secondary()!=null&&System.currentTimeMillis()<deadline)Thread.sleep(100)
            assertNull(secondary())
            scenario.onActivity {assertFalse(it.isFinishing);assertTrue(app.dashboardVisible.value)}
        }finally {
            instrument.runOnMainSync {
                if(app.clusterProjection.applied.value!=null)app.clusterProjection.restoreStock()
                app.preferences.change(previous.copy(clusterDesign=ClusterDesign.ORIGINAL))
            }
            scenario?.close();display.release();reader.close()
            instrument.uiAutomation.dropShellPermissionIdentity()
            instrument.runOnMainSync {app.preferences.change(previous)}
        }
    }
}
