package ru.jeland.os.data.cluster

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Binder
import android.os.Parcel
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayArea
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDisplayCluster
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterProjectionInstrumentation {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun main(action:()->Unit)=instrumentation.runOnMainSync(action)
    private class Preferences:InterfaceRepository {
        override val state=MutableStateFlow(InterfacePreferences())
        override fun change(value:InterfacePreferences){state.value=value}
        fun choose(design:ClusterDesign)=change(state.value.copy(clusterDesign=design))
    }
    private class Route:ClusterRoute {
        val calls=mutableListOf<String>()
        var callback:(()->Unit)?=null
        override fun open(afterArea:()->Unit){calls+="open";callback=afterArea}
        override fun restore(){calls+="restore"}
        override fun closeStockMap(phase:String){calls+="close:$phase"}
    }
    @Test fun carLanQueuesUntilConnectedAndPreservesBothOemPayloads() {
        val events=CopyOnWriteArrayList<VDEvent>()
        val done=CountDownLatch(1)
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int)=false
        }
        val binder=object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                assertEquals(2,code)
                data.enforceInterface("com.desaysv.ivi.vdb.IVDBus")
                assertEquals(1,data.readInt())
                events+=VDEvent.CREATOR.createFromParcel(data)
                reply!!.writeNoException()
                return true
            }
        }
        main {
            val client=CarLanClient(context,{})
            client.displayAreaOpen(10){done.countDown()}
            assertTrue(events.isEmpty())
            client.onServiceConnected(ComponentName("test","test"),binder)
        }
        assertTrue(done.await(5,TimeUnit.SECONDS))
        assertEquals(listOf(721702,721699),events.map {it.id})
        val area=VDNaviDisplayArea.getValue(events[0])
        assertEquals(10,area.naviDisplayArea);assertEquals("true",area.naviDisplayAreaResult)
        val cluster=VDNaviDisplayCluster.getValue(events[1])
        assertEquals("true",cluster.naviFrontDeskStatus);assertEquals("true",cluster.displayCluster)
        assertEquals(0,cluster.perspective);assertEquals("false",cluster.perspectiveResult)
        assertEquals("false",cluster.requestDisplayNaviArea)
    }

    @Test fun previewChangesNeverProjectUntilExplicitlyApplied() {
        val prefs=Preferences();val route=Route();val launches=mutableListOf<Int>()
        val name="projection-test-${System.nanoTime()}"
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        lateinit var projection:ClusterProjection
        try {
            main {
                projection=ClusterProjection(context,prefs,{},route,{listOf(0,2)},{launches+=it})
                projection.start();prefs.choose(ClusterDesign.CLASSIC)
            }
            instrumentation.waitForIdleSync()
            main {
                assertTrue(route.calls.isEmpty());assertTrue(launches.isEmpty());assertNull(projection.applied.value)
                projection.show()
                assertEquals(listOf(2),launches);assertEquals(listOf("open"),route.calls)
                assertEquals(ClusterDesign.CLASSIC,projection.applied.value!!.design)
                prefs.change(prefs.state.value.copy(clusterDesign=ClusterDesign.SPORT,
                    clusterAccent=ClusterAccent.custom(0x663399),clusterColorMode=ClusterColorMode.DRIVE_MODE,clusterTripPeriod=ClusterTripPeriod.SINCE_RESET,clusterNavigationMode=ClusterNavigationMode.GUIDANCE))
            }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(ClusterOutput(ClusterDesign.CLASSIC,ClusterAccent.CYAN,ClusterColorMode.MANUAL),projection.applied.value)
                assertEquals(1,launches.size)
                projection.restoreStock();route.callback?.invoke()
            }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(listOf("open","restore"),route.calls)
                assertNull(projection.applied.value)
                assertEquals(ClusterDesign.SPORT,prefs.state.value.clusterDesign)
                prefs.choose(ClusterDesign.NAVIGATION)
            }
            instrumentation.waitForIdleSync()
            main {
                assertNull(projection.applied.value);assertEquals(1,launches.size)
                projection.show()
                assertEquals(listOf(2,2),launches)
                assertEquals(ClusterDesign.NAVIGATION,projection.applied.value!!.design)
                assertEquals(ClusterColorMode.DRIVE_MODE,projection.applied.value!!.colorMode)
                assertEquals(ClusterTripPeriod.SINCE_RESET,projection.applied.value!!.tripPeriod)
                assertEquals(ClusterNavigationMode.GUIDANCE,projection.applied.value!!.navigationMode)
                assertEquals(prefs.state.value.clusterAccent,projection.applied.value!!.accent)
                prefs.choose(ClusterDesign.ORIGINAL)
            }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(ClusterDesign.NAVIGATION,projection.applied.value!!.design)
                projection.show()
                assertNull(projection.applied.value)
            }
        }finally {main {projection.close()};context.deleteSharedPreferences(name)}
    }
    @Test fun addingDisplayDoesNotApplyPreviewAndFailedExplicitLaunchRestoresStock() {
        val prefs=Preferences();val route=Route()
        val name="projection-test-${System.nanoTime()}"
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        var ids=listOf(0);var fail=true
        lateinit var projection:ClusterProjection
        try {
            main {
                prefs.choose(ClusterDesign.NAVIGATION)
                projection=ClusterProjection(context,prefs,{},route,{ids},{if(fail)throw IllegalStateException("launch refused")})
                projection.start();projection.show()
                assertTrue(route.calls.isEmpty());assertNull(projection.applied.value)
                ids=listOf(0,7);prefs.choose(ClusterDesign.SPORT)
            }
            instrumentation.waitForIdleSync()
            main {
                assertTrue(route.calls.isEmpty());assertNull(projection.applied.value)
                projection.show()
                assertEquals(listOf("open","restore"),route.calls)
                assertNull(projection.applied.value)
                assertEquals(ClusterDesign.SPORT,prefs.state.value.clusterDesign)
                fail=false;projection.show()
                assertEquals(ClusterDesign.SPORT,projection.applied.value!!.design)
            }
        }finally {main {projection.close()};context.deleteSharedPreferences(name)}
    }
    @Test fun startupRestoresStaleOutputWithoutApplyingTheSavedPreview() {
        val prefs=Preferences();val route=Route();val launches=mutableListOf<Int>()
        val name="projection-test-${System.nanoTime()}"
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        context.getSharedPreferences("cluster-projection",0).edit().putBoolean("owns-cluster",true).commit()
        lateinit var projection:ClusterProjection
        try {
            main {
                prefs.choose(ClusterDesign.CLASSIC)
                projection=ClusterProjection(context,prefs,{},route,{listOf(0,2)},{launches+=it})
                projection.start()
            }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(listOf("restore"),route.calls);assertTrue(launches.isEmpty())
                assertNull(projection.applied.value);assertEquals(ClusterDesign.CLASSIC,prefs.state.value.clusterDesign)
                assertFalse(context.getSharedPreferences("cluster-projection",0).getBoolean("owns-cluster",true))
            }
        }finally {main {projection.close()};context.deleteSharedPreferences(name)}
    }
    @Test fun stockRestoreKeepsCustomSceneUntilNativeWindowIsConfirmedAndCanRetry() {
        val prefs=Preferences();val routeCalls=mutableListOf<String>();var completion:((Boolean)->Unit)?=null
        val route=object:ClusterRoute {
            override fun open(afterArea:()->Unit){routeCalls+="open"}
            override fun restore(){error("Expected acknowledged restore")}
            override fun restore(completed:(Boolean)->Unit){routeCalls+="restore";completion=completed}
            override fun closeStockMap(phase:String){}
        }
        val name="restore-ack-${System.nanoTime()}"
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        lateinit var projection:ClusterProjection;val launches=mutableListOf<Int>()
        try {main {
            prefs.choose(ClusterDesign.CLASSIC)
            projection=ClusterProjection(context,prefs,{},route,{listOf(0,2)},{launches+=it});projection.start();projection.show()
            val opened=projection.openedAtMillis;assertNotNull(opened)
            projection.restoreStock();assertNotNull(projection.applied.value)
            assertTrue(context.getSharedPreferences("cluster-projection",0).getBoolean("owns-cluster",false))
            projection.show();assertEquals(1,launches.size)
            completion!!(false);assertNotNull(projection.applied.value);assertEquals(opened,projection.openedAtMillis)
            projection.restoreStock();completion!!(true)
            assertNull(projection.applied.value);assertNull(projection.openedAtMillis)
            assertFalse(context.getSharedPreferences("cluster-projection",0).getBoolean("owns-cluster",true))
        }}finally {main {projection.close()};context.deleteSharedPreferences(name)}
    }
    @Test fun repeatedShowUpdatesTheDesignWithoutReopeningOrRepeatingTheStartup() {
        val prefs=Preferences();val route=Route();val launches=mutableListOf<Int>()
        val name="in-place-${System.nanoTime()}"
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        lateinit var projection:ClusterProjection
        try {main {
            prefs.choose(ClusterDesign.CLASSIC);projection=ClusterProjection(context,prefs,{},route,{listOf(0,2)},{launches+=it})
            projection.start();projection.show();val started=projection.openedAtMillis
            prefs.choose(ClusterDesign.SPORT);projection.show()
            assertEquals(ClusterDesign.SPORT,projection.applied.value!!.design)
            assertEquals(started,projection.openedAtMillis);assertEquals(listOf(2),launches);assertEquals(listOf("open"),route.calls)
        }}finally {main {projection.close()};context.deleteSharedPreferences(name)}
    }
    @Test fun restoreReleasesNavigationWithOemStopAndFalseOwnershipPayloads() {
        val events=CopyOnWriteArrayList<VDEvent>();val done=CountDownLatch(1)
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int)=false
        }
        val binder=object:Binder() {
            override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
                assertEquals(2,code);data.enforceInterface("com.desaysv.ivi.vdb.IVDBus");assertEquals(1,data.readInt())
                events+=VDEvent.CREATOR.createFromParcel(data);reply!!.writeNoException();return true
            }
        }
        main {val client=CarLanClient(context,{})
            client.releaseCluster {ok->assertTrue(ok);done.countDown()}
            client.onServiceConnected(ComponentName("test","test"),binder)}
        assertTrue(done.await(5,TimeUnit.SECONDS))
        assertEquals(listOf(721701,721702,721699),events.map {it.id})
        val stopped=com.desaysv.ivi.vdb.event.id.carlan.bean.VDNaviDigitalInfo.getValue(events[0])
        assertEquals(4,stopped.naviStatus);assertEquals(3,stopped.cameraType);assertEquals(-1,stopped.warningMessage)
        assertEquals(9,VDNaviDisplayArea.getValue(events[1]).naviDisplayArea)
        val cluster=VDNaviDisplayCluster.getValue(events[2]);assertEquals("false",cluster.displayCluster)
        assertEquals("false",cluster.naviFrontDeskStatus);assertEquals("false",cluster.requestDisplayNaviArea)
    }

}
