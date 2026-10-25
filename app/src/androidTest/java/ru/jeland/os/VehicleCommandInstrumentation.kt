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
import java.util.concurrent.atomic.AtomicInteger

/** In-memory IVDBus only. Never binds an OEM service or connects to a physical vehicle. */
@RunWith(AndroidJUnit4::class)
class VehicleCommandInstrumentation {
    @Test fun rapidTemperatureTapsAreOrderedWithoutReadbackPause()=withSession {session,bus,reducer->
        val time=SystemClock.elapsedRealtime()
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),time)
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,10),listOf(5),time)
        repeat(3){session.temperature(true,.5)}
        waitFor {bus.sets.size==3}
        assertEquals(listOf(76 to 6,76 to 7,76 to 8),bus.sets.toList())
        assertTrue("Readback must not block the next SET",SystemClock.elapsedRealtime()-time<1500)
        assertEquals(listOf(5),reducer.values(VdsKey(VerifiedVehicleProtocol.HVAC,10),SystemClock.elapsedRealtime()))
    }
    @Test fun activeConnectionEnablesCommandsWithoutManualCheckbox()=withSession {session,_,_->
        session.allowWrites(false)
        assertTrue(session.link.value.writesEnabled)
        session.pause();assertFalse(session.link.value.writesEnabled)
    }
    private class FakeBus:IVDBus.Stub() {
        val sets=CopyOnWriteArrayList<Pair<Int,Int>>()
        val modules=CopyOnWriteArrayList<Int>()
        @Volatile var power=0
        @Volatile var fail=false
        @Volatile var display=5
        @Volatile var dual=1
        @Volatile var driver=5
        @Volatile var passenger=8
        @Volatile var fan=5
        var gate:CountDownLatch?=null
        val entered=CountDownLatch(1)
        override fun get(event:VDEvent?):VDEvent?=event?.let {
            val cmd=it.payload.getInt("CMD_ID")
            VDEvent(it.id,Bundle().apply {putInt("CMD_ID",cmd);putIntArray("VALUE",intArrayOf(if(it.id==VerifiedVehicleProtocol.HVAC)when(cmd){18->power;9->display;3->dual;10->driver;13->passenger;12->fan;else->5}else if(it.id==VerifiedVehicleProtocol.CAR)1 else 5))})
        }
        override fun set(event:VDEvent?) {
            entered.countDown();gate?.await(3,TimeUnit.SECONDS)
            if(fail)throw RemoteException("Synthetic failure")
            val e=checkNotNull(event);val cmd=e.payload.getInt("CMD_ID");val value=e.payload.getIntArray("VALUE")!!.single()
            sets+=cmd to value;modules+=e.id
            if(e.id==VerifiedVehicleProtocol.HVAC&&cmd==18)power=if(value==1)1 else 0
            if(e.id==VerifiedVehicleProtocol.HVAC)when(cmd){3->{dual=if(value==1)0 else 1;if(dual==0)passenger=driver};76->{driver=value;if(dual==0)passenger=value};78->{passenger=value;if(dual==0)driver=value};77->fan=value}
        }
        override fun subscribe(ids:IntArray?,pid:Int,pkg:String?,callback:IVDBusCallback?) {}
        override fun subscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
        override fun unsubscribeCustomizedEvent(event:VDEvent?,notify:IVDBusNotify?) {}
    }
    private fun field(session:DesayCarSession,name:String)=DesayCarSession::class.java.getDeclaredField(name).apply {isAccessible=true}
    private fun waitFor(condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+4000
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
    @Test fun nativeCamerasSendOnlyOneStockDisplayRequestWithoutHvacOrRetries()=withSession {session,bus,_->
        session.openCameras();waitFor {!session.link.value.busy}
        assertEquals(listOf(8 to 1),bus.sets.toList());assertEquals(listOf(0x50008),bus.modules.toList())
        SystemClock.sleep(2700)
        assertEquals(listOf(8 to 1),bus.sets.toList())
    }
    @Test fun disconnectedDemoOrBackgroundCannotOpenNativeCameras() {
        for(flag in listOf("foreground","vehicleMode","writes"))withSession {session,bus,_->
            field(session,flag).setBoolean(session,false);session.openCameras()
            assertTrue(bus.sets.isEmpty())
        }
    }
    @Test fun failedCameraRequestNeverRetriesOrFallsBackToAnotherChannel()=withSession {session,bus,_->
        bus.fail=true;session.openCameras();waitFor {!session.link.value.busy}
        SystemClock.sleep(700);assertTrue(bus.sets.isEmpty());assertNotNull(session.link.value.userMessage)
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
    @Test fun fanUsesDockSetAndOnlyReadsFeedbackWithoutDisplayOrPowerWrites()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
        waitFor {!session.link.value.busy}
        assertEquals(listOf(77 to 5),bus.sets.toList())
        SystemClock.sleep(1000)
        assertEquals(listOf(77 to 5),bus.sets.toList())
        assertFalse(bus.sets.any {it.first in listOf(9,18,51,64)})
    }
    @Test fun pauseOrWriteLockCannotScheduleAnyExtraHvacWrite() {
        for(pause in listOf(false,true))withSession {session,bus,reducer->
            reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
            session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
            waitFor {bus.sets.isNotEmpty()}
            if(pause)session.pause()else session.allowWrites(false)
            SystemClock.sleep(650)
            assertEquals(listOf(77 to 5),bus.sets.toList())
        }
    }
    @Test fun failedDockCommandNeverRetriesLegacySetterOrUiSuppression()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        bus.fail=true
        session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
        waitFor {!session.link.value.busy}
        assertTrue(bus.sets.isEmpty())
        assertNotNull(session.link.value.userMessage)
    }
    @Test fun syncSendsOnlyDualCommandAndReadsBothZonesBack()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,3),listOf(1),SystemClock.elapsedRealtime())
        session.sync();waitFor {session.climate.value.sync}
        assertEquals(listOf(3 to 1),bus.sets.toList());assertTrue(session.climate.value.sync)
        assertEquals(20.0,session.climate.value.driver,0.0);assertEquals(20.0,session.climate.value.passenger,0.0)
        session.sync();waitFor {!session.climate.value.sync}
        assertEquals(listOf(3 to 1,3 to 2),bus.sets.toList());assertFalse(session.climate.value.sync)
    }
    @Test fun noisyActionClosesOnlyDisplayImmediatelyAfterActualOpenAndOnlyOnce()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        session.command("AUTO",{listOf(VerifiedVehicleProtocol.autoOn())})
        waitFor {!session.link.value.busy}
        assertEquals(listOf(4 to 1),bus.sets.toList())
        val start=SystemClock.elapsedRealtime()
        session.onOemClimatePanelOpened()
        waitFor {bus.sets.size==2}
        assertTrue("No fixed close timer/readback wait",SystemClock.elapsedRealtime()-start<350)
        assertEquals(listOf(4 to 1,9 to 2),bus.sets.toList())
        repeat(3){session.onOemClimatePanelOpened()};SystemClock.sleep(300)
        assertEquals(2,bus.sets.size)
        assertFalse(bus.sets.any {it.first in listOf(18,51,64)})
    }
    @Test fun quietActionAndUnrelatedOemOpeningNeverSendDisplayClose()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        session.onOemClimatePanelOpened();session.command("fan",{listOf(VerifiedVehicleProtocol.fan(5))})
        waitFor {!session.link.value.busy};session.onOemClimatePanelOpened();SystemClock.sleep(200)
        assertEquals(listOf(77 to 5),bus.sets.toList())
    }
    @Test fun failedOrPausedNoisyActionNeverClosesAnOemPanel() {
        for(fail in listOf(true,false))withSession {session,bus,reducer->
            reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
            bus.fail=fail
            session.command("AUTO",{listOf(VerifiedVehicleProtocol.autoOn())});waitFor {!session.link.value.busy}
            if(!fail)session.pause()
            session.onOemClimatePanelOpened();SystemClock.sleep(250)
            assertFalse(bus.sets.any {it.first==9})
        }
    }
    @Test fun absoluteSliderRequestDoesNotReplayIntermediateTemperatures()=withSession {session,bus,reducer->
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,13),listOf(8),SystemClock.elapsedRealtime())
        session.setTemperature(false,26.0);waitFor {session.climate.value.passenger==26.0}
        assertEquals(listOf(78 to 11),bus.sets.toList());assertEquals(26.0,session.climate.value.passenger,0.0)
    }
    @Test fun temperatureUsesSystemUiDockSetterAndNeverTouchesAnAlreadyOpenOemPanel()=withSession {session,bus,reducer->
        bus.power=1;bus.display=1
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,18),listOf(1),SystemClock.elapsedRealtime())
        reducer.accept(VdsKey(VerifiedVehicleProtocol.HVAC,10),listOf(5),SystemClock.elapsedRealtime())
        session.temperature(true,1.0);waitFor {session.climate.value.driver==21.0}
        assertEquals(listOf(76 to 6),bus.sets.toList());assertEquals(21.0,session.climate.value.driver,0.0)
        assertEquals(1,bus.display)
    }
}
