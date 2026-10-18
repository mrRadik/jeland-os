package ru.jeland.os

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.desaysv.ivi.vdb.*
import com.desaysv.ivi.vdb.event.VDEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.DesayCarSession
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** In-memory IVDBus only. Never binds an OEM service or connects to a physical vehicle. */
@RunWith(AndroidJUnit4::class)
class VehicleCommandInstrumentation {
    private class FakeBus:IVDBus.Stub() {
        val sets=CopyOnWriteArrayList<Pair<Int,Int>>()
        @Volatile var power=0
        @Volatile var fail=false
        var gate:CountDownLatch?=null
        val entered=CountDownLatch(1)
        override fun get(event:VDEvent?):VDEvent?=event?.let {
            val cmd=it.payload.getInt("CMD_ID")
            VDEvent(it.id,Bundle().apply {putInt("CMD_ID",cmd);putIntArray("VALUE",intArrayOf(if(cmd==18)power else if(it.id==VerifiedVehicleProtocol.CAR)1 else 5))})
        }
        override fun set(event:VDEvent?) {
            entered.countDown();gate?.await(3,TimeUnit.SECONDS)
            if(fail)throw RemoteException("Synthetic failure")
            val e=checkNotNull(event);val cmd=e.payload.getInt("CMD_ID");val value=e.payload.getIntArray("VALUE")!!.single()
            sets+=cmd to value
            if(e.id==VerifiedVehicleProtocol.HVAC&&cmd==18)power=if(value==1)1 else 0
        }
        override fun subscribe(ids:IntArray?,pid:Int,pkg:String?,callback:IVDBusCallback?) {}
        override fun subscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
        override fun unsubscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
    }
    private fun field(session:DesayCarSession,name:String)=DesayCarSession::class.java.getDeclaredField(name).apply {isAccessible=true}
    private fun waitFor(condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+2000
        while(!condition()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(10)
        assertTrue("Timed out; command must not wait for feedback",condition())
    }
    private fun withSession(test:(DesayCarSession,FakeBus,LiveVehicleReducer)->Unit) {
        val context=object:ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext():Context=this
            override fun startService(service:Intent)=throw AssertionError("Test cannot start an external service")
        }
        val session=DesayCarSession(context,MemorySettingsStore());val bus=FakeBus()
        field(session,"bus").set(session,bus)
        for(name in listOf("foreground","vehicleMode","writes"))field(session,name).setBoolean(session,true)
        val reducer=field(session,"reducer").get(session) as LiveVehicleReducer
        try {test(session,bus,reducer)} finally {
            bus.gate?.countDown();session.pause()
            (field(session,"worker").get(session) as ScheduledExecutorService).shutdownNow()
        }
    }
    @Test fun offOrUnknownPowerPreventsHvacSetButPowerCommandIsAllowed()=withSession {session,bus,reducer->
        session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
        waitFor {!session.link.value.busy};assertTrue(bus.sets.isEmpty())
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(0),SystemClock.elapsedRealtime())
        session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
        waitFor {!session.link.value.busy};assertTrue(bus.sets.isEmpty())
        session.binary(18)
        // This command deliberately has normal readback; wait only for its SET, then cleanup cancels reads.
        waitFor {bus.sets.isNotEmpty()};assertEquals(listOf(18 to 1),bus.sets.toList())
    }
    @Test fun rearLevelsSendExactlyOneSelectedSeatCommandWithoutOtherSeatData() {
        for(side in RearSeatSide.entries)for(level in 0..3)withSession {session,bus,reducer->
            reducer.accept(VdsKey(VerifiedVehicleProtocol.CAR,side.commandId),listOf(1),SystemClock.elapsedRealtime())
            session.rearSeat(side,level)
            waitFor {bus.sets.size==1}
            assertEquals(listOf(side.commandId to level+1),bus.sets.toList())
        }
    }
    @Test fun rearLevelsRespectWriteGuardAndUnknownTargetState()=withSession {session,bus,reducer->
        session.rearSeat(RearSeatSide.LEFT,2);waitFor {!session.link.value.busy};assertTrue(bus.sets.isEmpty())
        reducer.accept(VdsKey(VerifiedVehicleProtocol.CAR,139),listOf(0),SystemClock.elapsedRealtime())
        session.rearSeat(RearSeatSide.LEFT,4);waitFor {!session.link.value.busy};assertTrue(bus.sets.isEmpty())
        field(session,"writes").setBoolean(session,false)
        session.rearSeat(RearSeatSide.LEFT,2);assertTrue(bus.sets.isEmpty())
    }
    @Test fun shadeAnimatesBeforeBinderReturnsAndDoesNotWaitForReadback()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.CAR,10),listOf(1),SystemClock.elapsedRealtime())
        bus.gate=CountDownLatch(1)
        session.shade(Shade.OPEN)
        assertEquals(Shade.OPEN,session.vehicle.value.shadeUiPosition)
        assertTrue(bus.entered.await(1,TimeUnit.SECONDS))
        bus.gate!!.countDown();waitFor {!session.link.value.busy}
        assertEquals(listOf(124 to 1),bus.sets.toList())
        session.shade(Shade.HALF);waitFor {!session.link.value.busy}
        assertEquals(Shade.HALF,session.vehicle.value.shadeUiPosition)
        session.shade(Shade.OPEN);waitFor {!session.link.value.busy}
        assertEquals(listOf(124 to 1,124 to 3,124 to 1),bus.sets.toList())
        SystemClock.sleep(500)
        assertEquals(Shade.CLOSED,session.vehicle.value.reportedShade)
        assertEquals(Shade.OPEN,session.vehicle.value.shadeUiPosition)
        bus.fail=true;session.shade(Shade.CLOSED);waitFor {!session.link.value.busy}
        assertEquals(Shade.OPEN,session.vehicle.value.shadeUiPosition)
        assertNotNull(session.link.value.userMessage)
        field(session,"writes").setBoolean(session,false)
        session.shade(Shade.HALF);assertEquals(Shade.OPEN,session.vehicle.value.shadeUiPosition)
        assertEquals(3,bus.sets.size)
    }
}
