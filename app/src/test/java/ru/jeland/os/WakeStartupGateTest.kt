package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.WakeStartupGate

class WakeStartupGateTest {
    @Test fun initialScreenOnDoesNotStartAnything(){assertFalse(WakeStartupGate().takeWake(true,false))}
    @Test fun oneLaunchPerSleepCycleEvenWithDuplicateScreenOnAndUnlock(){
        val saved=mutableListOf<Boolean>();val gate=WakeStartupGate(save=saved::add)
        gate.screenOff();assertTrue(gate.takeWake(true,false))
        repeat(10){assertFalse(gate.takeWake(true,false))}
        assertEquals(listOf(true,false),saved)
        gate.screenOff();assertTrue(gate.takeWake(true,false))
    }
    @Test fun lockedScreenWaitsForActualUnlock(){
        val gate=WakeStartupGate();gate.screenOff()
        assertFalse(gate.takeWake(true,true));assertTrue(gate.pending)
        assertTrue(gate.takeWake(true,false));assertFalse(gate.takeWake(true,false))
    }
    @Test fun serviceRecreationRetainsAnObservedOffCycle(){
        var saved=false;WakeStartupGate(save={saved=it}).screenOff()
        val recreated=WakeStartupGate(saved){saved=it}
        assertTrue(recreated.takeWake(true,false));assertFalse(saved)
        assertFalse(WakeStartupGate(saved).takeWake(true,false))
    }
    @Test fun shortWakeThenSleepCancelsOpportunityUntilNextRealWake(){
        val gate=WakeStartupGate();gate.screenOff()
        assertFalse(gate.takeWake(false,false));assertTrue(gate.pending)
        gate.screenOff();assertFalse(gate.takeWake(false,false))
        assertTrue(gate.takeWake(true,false))
    }
    @Test fun disablingClearsPendingAndNeitherFailureNorDuplicateCanRetry(){
        val gate=WakeStartupGate(true);gate.clear();assertFalse(gate.takeWake(true,false))
        gate.screenOff();assertTrue(gate.takeWake(true,false)) // consumed before external launch
        assertFalse(gate.pending);assertFalse(gate.takeWake(true,false))
    }
}
