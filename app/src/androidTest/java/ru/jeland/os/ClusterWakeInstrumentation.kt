package ru.jeland.os.data.cluster

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class ClusterWakeInstrumentation {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrument.runOnMainSync(block)
    private class Preferences:InterfaceRepository {
        override val state=MutableStateFlow(InterfacePreferences(clusterDesign=ClusterDesign.CLASSIC))
        override fun change(value:InterfacePreferences){state.value=value}
    }
    private class Route:ClusterRoute {
        var opens=0;var resets=0;var restores=0;var restoreSuccess=true
        val after=mutableListOf<()->Unit>()
        override fun open(afterArea:()->Unit){opens++;after+=afterArea}
        override fun resumeAfterSleep(){resets++}
        override fun restore(){restores++}
        override fun restore(completed:(Boolean)->Unit){restore();completed(restoreSuccess)}
        override fun closeStockMap(phase:String){}
    }
    private class Host:ClusterOutputHost {
        override var closing=false
        var rebuilt=0;var finished=0
        override fun rebuildOutput(){rebuilt++}
        override fun finishOutput(){finished++;closing=true}
    }
    private class Fixture(context:Context) {
        val name="cluster-wake-${System.nanoTime()}"
        val context=object:ContextWrapper(context) {
            override fun getSharedPreferences(key:String,mode:Int)=super.getSharedPreferences(name,mode)
        }
        val prefs=Preferences();val route=Route();val launches=mutableListOf<Int>();val logs=mutableListOf<String>()
        var ids=listOf(0,2);var ready=true
        val projection=ClusterProjection(this.context,prefs,logs::add,route,{ids},{launches+=it},{ready})
        val host=Host()
        fun show(){projection.start();projection.show();projection.attached(host);projection.resumed(host)}
        fun dispose(){projection.close();context.deleteSharedPreferences(name)}
    }
    @Test fun sleepRebuildsWindowAndReopensRouteOnceWithAppliedDesignNotPreview() {
        val f=Fixture(instrument.targetContext)
        try {
            main {
                f.show();assertEquals(1,f.route.opens)
                f.prefs.change(f.prefs.state.value.copy(clusterDesign=ClusterDesign.SPORT))
                // Theme/lighting display updates and a paused foreground are not sleep transitions.
                repeat(4){f.projection.displayChanged(2);f.projection.dashboardResumed();f.projection.resumed(f.host)}
                f.projection.screenChanged(false);f.projection.stopped(f.host)
            }
            Thread.sleep(750)
            main {assertEquals(1,f.route.opens);assertEquals(0,f.host.rebuilt)
                f.projection.screenChanged(true);f.projection.dashboardResumed();f.projection.resumed(f.host)}
            Thread.sleep(800)
            main {
                assertEquals(2,f.route.opens);assertEquals(1,f.route.resets);assertEquals(1,f.host.rebuilt)
                assertEquals(listOf(2),f.launches)
                assertEquals(ClusterDesign.CLASSIC,f.projection.applied.value!!.design)
                val new=Host();f.projection.stopped(f.host);f.projection.detached(f.host)
                f.projection.dashboardResumed() // Must not cancel an in-flight window recreation.
                f.projection.attached(new);f.projection.resumed(new)
                assertTrue(f.logs.any {it.startsWith("CLUSTER_RECOVERY_COMPLETE")})
                repeat(3){f.projection.displayChanged(2);f.projection.dashboardResumed();f.projection.resumed(new)}
            }
            Thread.sleep(750)
            main {assertEquals(2,f.route.opens)}
        }finally {main {f.dispose()}}
    }
    @Test fun returningToStockCancelsQueuedWakeAndCannotReopenCustom() {
        val f=Fixture(instrument.targetContext)
        try {
            main {f.show();f.projection.screenChanged(false);f.projection.screenChanged(true);f.projection.restoreStock()}
            Thread.sleep(800)
            main {
                assertNull(f.projection.applied.value);assertEquals(1,f.route.opens);assertEquals(1,f.host.finished)
                f.projection.screenChanged(false);f.projection.screenChanged(true);f.projection.dashboardResumed();f.projection.displayAdded(2)
            }
            Thread.sleep(750)
            main {assertEquals(1,f.route.opens);assertEquals(0,f.route.resets)}
        }finally {main {f.dispose()}}
    }
    @Test fun failedReturnToStockDoesNotReclaimCustomOnNextWake() {
        val f=Fixture(instrument.targetContext)
        try {
            main {f.show();f.route.restoreSuccess=false;f.projection.restoreStock();assertNotNull(f.projection.applied.value)
                f.projection.screenChanged(false);f.projection.stopped(f.host);f.projection.screenChanged(true);f.projection.dashboardResumed()}
            Thread.sleep(800)
            main {assertEquals(1,f.route.opens);assertEquals(0,f.host.rebuilt)
                f.projection.show();f.projection.screenChanged(false);f.projection.screenChanged(true)}
            Thread.sleep(800)
            main {assertEquals(2,f.route.opens);assertEquals(1,f.host.rebuilt);f.projection.resumed(f.host)}
        }finally {main {f.dispose()}}
    }
    @Test fun removedDisplayCanReturnWithDifferentIdWithoutLosingAppliedOutput() {
        val f=Fixture(instrument.targetContext)
        try {
            main {f.show();f.ids=listOf(0);f.projection.displayRemoved(2);f.projection.detached(f.host)}
            Thread.sleep(750)
            main {
                assertNotNull(f.projection.applied.value);assertEquals(0,f.route.restores)
                f.ids=listOf(0,8);f.projection.displayAdded(8)
            }
            Thread.sleep(800)
            main {assertEquals(listOf(2,8),f.launches);assertEquals(2,f.route.opens)
                val host=Host();f.projection.attached(host);f.projection.resumed(host)}
        }finally {main {f.dispose()}}
    }
    @Test fun processRecreationRestoresOnlyExplicitlyAppliedSettings() {
        val old=Fixture(instrument.targetContext);val next=Fixture(instrument.targetContext)
        try {
            main {
                old.prefs.change(old.prefs.state.value.copy(clusterAccent=ClusterAccent.custom(0x663399),clusterColorMode=ClusterColorMode.DRIVE_MODE,clusterNavigationMode=ClusterNavigationMode.GUIDANCE));old.show()
                val applied=old.projection.applied.value!!
                val saved=old.context.getSharedPreferences("ignored",0).all
                val editor=next.context.getSharedPreferences("ignored",0).edit()
                saved.forEach {(key,value)->when(value){is Boolean->editor.putBoolean(key,value);is String->editor.putString(key,value)}}
                editor.commit()
                next.prefs.change(next.prefs.state.value.copy(clusterDesign=ClusterDesign.ORIGINAL,clusterAccent=ClusterAccent.RED))
                next.projection.start();assertEquals(applied,next.projection.applied.value)
                assertEquals(ClusterNavigationMode.GUIDANCE,next.projection.applied.value!!.navigationMode)
            }
            Thread.sleep(800)
            main {
                assertEquals(1,next.route.resets);assertEquals(1,next.route.opens);assertEquals(listOf(2),next.launches)
                val host=Host();next.projection.attached(host);next.projection.resumed(host)
            }
        }finally {main {old.dispose();next.dispose()}}
    }
    @Test fun delayedDisplayWakeDoesNotSendCommandsWhileOffAndRearmsAfterWaitBudget() {
        val f=Fixture(instrument.targetContext)
        try {
            main {f.show();f.ready=false;f.projection.displayChanged(2);f.projection.screenChanged(false);f.projection.screenChanged(true)}
            Thread.sleep(5600)
            main {
                assertEquals(1,f.route.opens);assertEquals(0,f.host.rebuilt)
                assertEquals(4,f.logs.count {it.startsWith("CLUSTER_RECOVERY_WAIT_DISPLAY")})
                f.ready=true;f.projection.displayChanged(2)
            }
            Thread.sleep(800)
            main {assertEquals(2,f.route.opens);assertEquals(1,f.host.rebuilt);f.projection.resumed(f.host)}
        }finally {main {f.dispose()}}
    }
}
