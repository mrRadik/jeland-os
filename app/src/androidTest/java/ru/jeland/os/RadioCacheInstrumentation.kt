package ru.jeland.os

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.desaysv.ivi.vdb.*
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNetworkStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.data.OemMobileCache
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** In-memory cache Binder. Never binds an external service or issues a radio/vehicle SET. */
@RunWith(AndroidJUnit4::class)
class RadioCacheInstrumentation {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private class Bus:IVDBus.Stub() {
        val calls=AtomicInteger();val entered=CountDownLatch(1);var gate:CountDownLatch?=null
        override fun get(event:VDEvent?):VDEvent {
            assertEquals(720899,event!!.id);assertTrue(event.payload.isEmpty)
            calls.incrementAndGet();entered.countDown();gate?.await(2,TimeUnit.SECONDS)
            val p=Parcel.obtain()
            try {
                p.writeByte(1.toByte());p.writeByte(1.toByte());p.writeString("4G");p.writeByte(0.toByte());p.writeInt(4)
                repeat(3){p.writeString("")};repeat(3){p.writeInt(0)};p.setDataPosition(0)
                val dto=VDNetworkStatus.CREATOR.createFromParcel(p)
                return VDEvent(720899,Bundle().apply {putParcelable("data",dto)})
            } finally {p.recycle()}
        }
        override fun set(event:VDEvent?) {throw AssertionError("Cache cannot SET")}
        override fun subscribe(ids:IntArray?,pid:Int,pkg:String?,callback:IVDBusCallback?) {throw AssertionError("GET only")}
        override fun subscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {throw AssertionError("GET only")}
        override fun unsubscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {throw AssertionError("GET only")}
    }
    private fun field(name:String)=OemMobileCache::class.java.getDeclaredField(name).apply {isAccessible=true}
    private fun withCache(test:(OemMobileCache,Bus,Runnable)->Unit) {
        val context=object:ContextWrapper(instrumentation.targetContext) {
            override fun bindService(i:Intent,c:ServiceConnection,flags:Int):Boolean=throw AssertionError("No external binding in this test")
            override fun unbindService(c:ServiceConnection) {}
        }
        val cache=OemMobileCache(context,{},{})
        val bus=Bus()
        val connection=object:ServiceConnection {
            override fun onServiceConnected(n:ComponentName,b:IBinder) {}
            override fun onServiceDisconnected(n:ComponentName) {}
        }
        instrumentation.runOnMainSync {field("bus").set(cache,bus);field("connection").set(cache,connection)}
        try {test(cache,bus,field("poll").get(cache) as Runnable)}
        finally {
            bus.gate?.countDown();instrumentation.runOnMainSync {cache.stop()}
            (field("worker").get(cache) as ExecutorService).shutdownNow()
        }
    }
    @Test fun cacheReadsOnlyTheVerifiedNetworkGetter()=withCache {cache,bus,poll->
        instrumentation.runOnMainSync {poll.run()}
        val deadline=SystemClock.elapsedRealtime()+3000
        while(cache.value==null&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(10)
        val result=checkNotNull(cache.value)
        assertTrue(result.connected&&result.registered);assertEquals("4G",result.type);assertEquals(4,result.level)
        assertEquals(1,bus.calls.get())
    }
    @Test fun hungReadCannotQueueMoreReadsOrPublishAfterStop()=withCache {cache,bus,poll->
        bus.gate=CountDownLatch(1)
        instrumentation.runOnMainSync {poll.run()}
        assertTrue(bus.entered.await(1,TimeUnit.SECONDS))
        instrumentation.runOnMainSync {repeat(3){poll.run()};cache.stop()}
        bus.gate!!.countDown();SystemClock.sleep(250)
        assertEquals(1,bus.calls.get());assertNull(cache.value)
    }
}
