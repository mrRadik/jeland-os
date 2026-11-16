package ru.jeland.os.data.cluster

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.JelandApplication
import ru.jeland.os.MainActivity
import ru.jeland.os.core.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual secondary Activity/window/Compose lifecycles; only the unavailable OEM route is substituted. */
class ClusterWakeActivityInstrumentation {
    @Test fun secondaryWindowRecreatesAndRendersAfterRepeatedSleepCycles() {
        val instrument=InstrumentationRegistry.getInstrumentation()
        val context=instrument.targetContext
        val app=(context.applicationContext as JelandApplication).container
        val previous=app.preferences.state.value;val setup=app.firstRun.state.value
        val projection=app.clusterProjection
        val field=ClusterProjection::class.java.getDeclaredField("route").apply {isAccessible=true}
        val originalRoute=field.get(projection)
        var opens=0;var resets=0
        val route=object:ClusterRoute {
            override fun open(afterArea:()->Unit){opens++;afterArea()}
            override fun restore(){}
            override fun closeStockMap(phase:String){}
            override fun resumeAfterSleep(){resets++}
        }
        val stored=context.getSharedPreferences("cluster-projection",0)
        val before=stored.all.toMap()
        val reader=ImageReader.newInstance(1920,720,PixelFormat.RGBA_8888,3)
        val handler=Handler(Looper.getMainLooper())
        reader.setOnImageAvailableListener({it.acquireLatestImage()?.close()},handler)
        val display=context.getSystemService(DisplayManager::class.java).createVirtualDisplay("Jeland wake QA",1920,720,160,reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
        instrument.uiAutomation.adoptShellPermissionIdentity("android.permission.ACTIVITY_EMBEDDING")
        var scenario:ActivityScenario<MainActivity>?=null
        fun main(block:()->Unit)=instrument.runOnMainSync(block)
        fun secondary():ClusterDisplayActivity? {
            var found:ClusterDisplayActivity?=null
            main {found=Stage.values().flatMap {ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it)}
                .filterIsInstance<ClusterDisplayActivity>().firstOrNull {!it.isFinishing&&!it.isDestroyed}}
            return found
        }
        fun waitForNew(old:ClusterDisplayActivity?):ClusterDisplayActivity {
            val deadline=System.currentTimeMillis()+12000
            while(System.currentTimeMillis()<deadline) {
                val current=secondary()
                if(current!=null&&current!==old)return current
                Thread.sleep(100)
            }
            error("Secondary Activity was not recreated")
        }
        val folder=File(context.getExternalFilesDir(null),"qa-cluster-124").apply {mkdirs()}
        fun visibleFrame(activity:Activity,name:String) {
            Thread.sleep(1800)
            val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
            val done=CountDownLatch(1);var result=-1
            main {PixelCopy.request(activity.window,bitmap,{result=it;done.countDown()},handler)}
            assertTrue(done.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
            var ink=0
            for(y in 0 until 720 step 3)for(x in 0 until 1920 step 3) {
                val c=bitmap.getPixel(x,y)
                if(android.graphics.Color.red(c)>90||android.graphics.Color.green(c)>90||android.graphics.Color.blue(c)>90)ink++
            }
            assertTrue("Recovered window must contain rendered instruments, not a black surface",ink>2000)
            File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        try {
            main {
                assertNull(projection.applied.value)
                field.set(projection,route)
                app.firstRun.finish();app.preferences.change(previous.copy(clusterDesign=ClusterDesign.CLASSIC))
            }
            scenario=ActivityScenario.launch(MainActivity::class.java)
            main {projection.show()}
            var window=waitForNew(null)
            assertEquals(display.display.displayId,window.display?.displayId)
            visibleFrame(window,"initial")
            repeat(2){cycle->
                main {
                    projection.screenChanged(false)
                    projection.stopped(window)
                }
                Thread.sleep(200)
                main {projection.screenChanged(true);projection.dashboardResumed()}
                val next=waitForNew(window)
                assertNotSame(window,next)
                window=next
                visibleFrame(window,"wake-${cycle+1}")
                main {assertTrue(app.dashboardVisible.value);assertEquals(cycle+2,opens);assertEquals(cycle+1,resets)}
            }
            main {projection.restoreStock();assertNull(projection.applied.value)}
        }finally {
            main {projection.restoreStock();field.set(projection,originalRoute);app.preferences.change(previous)
                if(setup.complete)app.firstRun.finish()else app.firstRun.reopen()}
            scenario?.close();display.release();reader.close();instrument.uiAutomation.dropShellPermissionIdentity()
            val editor=stored.edit().clear()
            before.forEach {(key,value)->when(value){is Boolean->editor.putBoolean(key,value);is String->editor.putString(key,value)}}
            editor.commit()
        }
    }
}
