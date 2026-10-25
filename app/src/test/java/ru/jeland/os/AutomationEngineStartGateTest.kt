package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*

class AutomationEngineStartGateTest {
    @Test fun accessoryAwakeAndMissingDataNeverStartHeaters() {
        val gate=AutomationEngineStartGate(MemorySettingsStore())
        for(i in 0..10){assertNull(gate.sample(0,1,i*5000L,i*5000L));assertNull(gate.sample(null,4,i*5000L,i*5000L))}
        assertNull(gate.cycleId)
    }
    @Test fun twoFreshDistinctRunningReadingsConfirmOneCycle() {
        val gate=AutomationEngineStartGate(MemorySettingsStore())
        assertNull(gate.sample(1,4,0,0));assertNull(gate.sample(1,4,0,1000));assertNull(gate.sample(1,4,500,500))
        val id=gate.sample(1,4,1500,1500);assertNotNull(id)
        repeat(10){assertNull(gate.sample(1,4,5000L+it*5000,5000L+it*5000));assertEquals(id,gate.cycleId)}
    }
    @Test fun resumeReconnectProcessRestartAndStartStopDoNotRepeat() {
        val store=MemorySettingsStore();val gate=AutomationEngineStartGate(store)
        gate.sample(1,4,0,0);val id=gate.sample(1,4,5000,5000)
        gate.interrupted();assertNull(gate.sample(1,4,10000,10000))
        gate.sample(0,4,15000,15000);gate.sample(0,4,20000,20000)
        assertNull(gate.sample(1,4,25000,25000))
        assertEquals(id,AutomationEngineStartGate(store).cycleId)
    }
    @Test fun confirmedEngineAndIgnitionOffOrRealSleepRearm() {
        val gate=AutomationEngineStartGate(MemorySettingsStore())
        gate.sample(1,4,0,0);val first=gate.sample(1,4,5000,5000)
        gate.sample(0,0,10000,10000);assertNotNull(gate.cycleId)
        gate.sample(0,0,15000,15000);assertNull(gate.cycleId)
        gate.sample(1,4,20000,20000);val next=gate.sample(1,4,25000,25000)
        assertNotNull(next);assertNotEquals(first,next)
        gate.vehicleSlept();assertNull(gate.cycleId)
    }
    @Test fun oldFutureUnknownAndSingleSpikeNeverConfirm() {
        for(value in listOf(-1,2,7,255)) {
            val gate=AutomationEngineStartGate(MemorySettingsStore())
            assertNull(gate.sample(value,4,0,0));assertNull(gate.sample(value,4,5000,5000))
        }
        val gate=AutomationEngineStartGate(MemorySettingsStore())
        assertNull(gate.sample(1,4,0,16000));assertNull(gate.sample(1,4,17000,16000))
        gate.sample(1,4,20000,20000);gate.sample(0,4,25000,25000)
        assertNull(gate.sample(1,4,30000,30000));assertNull(gate.cycleId)
    }
    @Test fun disabledAutoNeverClaimsAndPersistenceFailureNeverEmits() {
        val gate=AutomationEngineStartGate(MemorySettingsStore())
        gate.sample(1,4,0,0,false);gate.sample(1,4,5000,5000,false);assertNull(gate.cycleId)
        val store=object:DurableSettingsStore {
            override fun string(key:String,default:String)=default
            override fun put(key:String,value:String)=Unit
            override fun putDurable(key:String,value:String){error("disk full")}
        }
        val failed=AutomationEngineStartGate(store);failed.sample(1,4,0,0)
        assertTrue(runCatching {failed.sample(1,4,5000,5000)}.isFailure);assertNull(failed.cycleId)
    }
}
