package ru.jeland.os

import android.content.*
import android.os.*
import androidx.test.platform.app.InstrumentationRegistry
import com.desaysv.ivi.vdb.*
import com.desaysv.ivi.vdb.event.VDEvent
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.OemCameraLauncher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Fake bindings only: never queries configuration or opens an actual camera. */
class NativeCameraInstrumentation {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=instrumentation.runOnMainSync(block)
    private fun waitFor(check:()->Boolean){val end=SystemClock.elapsedRealtime()+4000;while(!check()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(10);assertTrue(check())}
    private class FakeBus:IVDBus.Stub() {
        val sets=CopyOnWriteArrayList<VDEvent>();var active=false;var fail=false
        override fun get(event:VDEvent?)=event?.let {VDEvent(it.id,Bundle().apply {putInt("status",if(active)2 else 1)})}
        override fun set(event:VDEvent?) {if(fail)throw RemoteException("test");sets+=checkNotNull(event)}
        override fun subscribe(ids:IntArray?,pid:Int,pkg:String?,callback:IVDBusCallback?) {}
        override fun subscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
        override fun unsubscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
    }
    private inner class Fixture(route:NativeCameraRoute=NativeCameraRoute.RVC) {
        val bus=FakeBus();val legacy=AtomicInteger();val allowed=AtomicBoolean(true)
        val errors=CopyOnWriteArrayList<String>();val binds=CopyOnWriteArrayList<Intent>();val reports=CopyOnWriteArrayList<String>()
        val unbound=AtomicInteger();var accepted=true;var hold=false;var binding:ServiceConnection?=null
        private val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun getApplicationContext():Context=this
            override fun startService(service:Intent)=throw AssertionError("No external service starts")
            override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean {
                binds+=Intent(intent);binding=connection
                if(accepted&&!hold)Handler(Looper.getMainLooper()).post {connection.onServiceConnected(checkNotNull(intent.component),bus)}
                return accepted
            }
            override fun unbindService(connection:ServiceConnection){unbound.incrementAndGet()}
        }
        val launcher=OemCameraLauncher(context,{reports+=it},{allowed.get()},{legacy.incrementAndGet()}) {
            NativeCameraConfiguration("4.0",if(route==NativeCameraRoute.CARINFO)1 else 0,0)
        }
        fun open()=main {launcher.open {errors+=it}}
        fun close()=main {launcher.cancel()}
    }
    @Test fun rvcUsesNativeServiceAndEmptyIconEventOnlyOnce() {
        val f=Fixture();try {
            f.open();f.open();waitFor {f.bus.sets.size==1&&f.unbound.get()==1}
            assertEquals(0,f.legacy.get());assertEquals(1,f.binds.size)
            val intent=f.binds.single();assertEquals(OemCameraLauncher.RVC_PACKAGE,intent.component!!.packageName)
            assertEquals(OemCameraLauncher.RVC_SERVICE,intent.component!!.className)
            assertEquals("action.desaysv.ivi.vds.rvc.SERVICE",intent.action)
            assertEquals(OemCameraLauncher.ICON_PRESS,f.bus.sets.single().id)
            assertTrue(f.bus.sets.single().payload.isEmpty);assertTrue(f.errors.isEmpty())
        }finally {f.close()}
    }
    @Test fun knownCarInfoConfigurationUsesOnlyOriginalPath() {
        val f=Fixture(NativeCameraRoute.CARINFO);try {f.open();waitFor {f.legacy.get()==1};assertTrue(f.binds.isEmpty());assertTrue(f.bus.sets.isEmpty())}finally {f.close()}
    }
    @Test fun alreadyActiveVideoNeverGetsToggledOff() {
        val f=Fixture();try {f.bus.active=true;f.open();waitFor {f.unbound.get()==1};assertTrue(f.bus.sets.isEmpty());assertEquals(0,f.legacy.get())}finally {f.close()}
    }
    @Test fun bindingAndRequestFailuresNeverTrySecondRouteOrRetry() {
        for(bindFailure in listOf(true,false)) {
            val f=Fixture();try {
                f.accepted=!bindFailure;f.bus.fail=!bindFailure;f.open();waitFor {f.errors.isNotEmpty()}
                assertEquals(1,f.binds.size);assertEquals(0,f.legacy.get());assertTrue(f.bus.sets.isEmpty())
            }finally {f.close()}
        }
    }
    @Test fun cancellationAndDemoBlockLateCameraCommands() {
        val f=Fixture();try {
            f.allowed.set(false);f.open();assertTrue(f.binds.isEmpty());assertTrue(f.bus.sets.isEmpty())
            f.allowed.set(true);f.hold=true;f.open();waitFor {f.binds.size==1};f.close()
            main {f.binding!!.onServiceConnected(f.binds.single().component!!,f.bus)}
            SystemClock.sleep(100);assertTrue(f.bus.sets.isEmpty());assertEquals(0,f.legacy.get())
        }finally {f.close()}
    }
}
