package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*

class AutomationTripGateTest {
    private fun start(g:AutomationTripGate,base:Long=0):String? {
        g.speed(0.0,base,base)
        assertNull(g.speed(2.0,base+100,base+100))
        assertNull(g.speed(3.0,base+1100,base+1100))
        assertNull(g.speed(3.0,base+2100,base+2100))
        return g.speed(3.0,base+2700,base+2700)
    }
    @Test fun appOpeningWakeAndConnectionAreNotMotionInputs() {
        val g=AutomationTripGate(MemorySettingsStore())
        repeat(20){g.interrupted();assertNull(g.tripId);assertEquals(TripStartPhase.WAIT_STOP,g.phase)}
    }
    @Test fun alreadyMovingOnLaunchNeverArms() {
        val g=AutomationTripGate(MemorySettingsStore())
        repeat(20){assertNull(g.speed(5.0,it*1000L,it*1000L))}
        assertEquals(TripStartPhase.WAIT_STOP,g.phase)
    }
    @Test fun sustainedMovementAfterStopStartsExactlyOnce() {
        val g=AutomationTripGate(MemorySettingsStore());val id=start(g)
        assertNotNull(id);assertEquals(TripStartPhase.STARTED,g.phase)
        repeat(20){assertNull(g.speed(4.0,3000L+it*1000,3000L+it*1000));assertEquals(id,g.tripId)}
    }
    @Test fun trafficLightsPauseReconnectAndRecreationCannotRepeat() {
        val store=MemorySettingsStore();val g=AutomationTripGate(store);val id=start(g)
        assertNull(g.speed(0.0,3000,3000));g.interrupted()
        assertNull(start(g,10000));val recreated=AutomationTripGate(store)
        assertEquals(id,recreated.tripId);assertNull(start(recreated,20000))
    }
    @Test fun onlyRealSleepOrChangedBootRearmsNotRepeatedSameBoot() {
        val store=MemorySettingsStore();val g=AutomationTripGate(store);g.boot("42");val id=start(g)
        g.boot("42");assertEquals(id,g.tripId);g.boot(null);assertEquals(id,g.tripId)
        g.boot("43");assertNull(g.tripId);assertNotNull(start(g,10000))
        g.vehicleSlept();assertNull(g.tripId);assertEquals(TripStartPhase.WAIT_STOP,g.phase)
    }
    @Test fun singleSpikeSlowCreepAndStationaryNeverStart() {
        val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0)
        assertNull(g.speed(10.0,100,100));assertNull(g.speed(0.0,200,200))
        repeat(100){assertNull(g.speed(.5,it*100L+300,it*100L+300))};assertNull(g.tripId)
    }
    @Test fun gapResetsAndRequiresANewStoppedBaseline() {
        val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0);g.speed(2.0,100,100)
        repeat(5){assertNull(g.speed(2.0,5000L+it*1000,5000L+it*1000))}
        assertEquals(TripStartPhase.WAIT_STOP,g.phase);assertNotNull(start(g,20000))
    }
    @Test fun staleFutureInvalidAndOutOfRangeSamplesFailClosed() {
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,101.0,-101.0)) {
            val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0)
            assertNull(g.speed(v,10,10));assertEquals(TripStartPhase.WAIT_STOP,g.phase)
        }
        val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0)
        assertNull(g.speed(5.0,100,4000));assertEquals(TripStartPhase.WAIT_STOP,g.phase)
        assertNull(g.speed(5.0,6000,5000));assertNull(g.tripId)
    }
    @Test fun duplicateTimestampCannotManufactureConfirmation() {
        val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0)
        repeat(100){assertNull(g.speed(5.0,100,100))};assertNull(g.tripId)
    }
    @Test fun reverseMotionCanBeARealDeparture() {
        val g=AutomationTripGate(MemorySettingsStore());g.speed(0.0,0,0)
        assertNull(g.speed(-2.0,100,100));assertNull(g.speed(-2.0,1100,1100))
        assertNull(g.speed(-2.0,2100,2100));assertNotNull(g.speed(-2.0,2700,2700))
    }
    @Test fun failedDurableClaimEmitsNoTrip() {
        val store=object:DurableSettingsStore {
            override fun string(key:String,default:String)=default
            override fun put(key:String,value:String){error("unsafe async persistence")}
            override fun putDurable(key:String,value:String){throw java.io.IOException("disk full")}
        }
        val g=AutomationTripGate(store)
        try {start(g);fail("Claim failure must propagate")}catch(_:java.io.IOException){}
        assertNull(g.tripId);assertNotEquals(TripStartPhase.STARTED,g.phase)
    }
}
