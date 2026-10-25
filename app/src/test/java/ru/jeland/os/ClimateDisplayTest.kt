package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.ClimateState
import ru.jeland.os.ui.climateDockTemperature
import ru.jeland.os.ui.fanCycleMillis

class ClimateDisplayTest {
    @Test fun dockShowsCenteredIntegerLabelsWithoutChangingSetpoints() {
        val s=ClimateState(driver=22.0,passenger=23.5)
        assertEquals("22",climateDockTemperature(s,true));assertEquals("24",climateDockTemperature(s,false))
        assertEquals(23.5,s.passenger,0.0)
        assertEquals("—",climateDockTemperature(s.copy(driver=Double.NaN),true))
    }
    @Test fun liveUnknownAndLowHighStillHaveTheirActualMeaning() {
        val s=ClimateState(available=setOf(10,13),driverRaw=0,passengerRaw=16)
        assertEquals("LO",climateDockTemperature(s,true));assertEquals("HI",climateDockTemperature(s,false))
        assertEquals("—",climateDockTemperature(s.copy(available=emptySet()),true))
        assertEquals("22",climateDockTemperature(s.copy(driverRaw=7),true))
    }
    @Test fun higherFanLevelsRotateFasterWithBoundedDuration() {
        for(level in 1..10)assertTrue(fanCycleMillis(level) in 1000..2440)
        for(level in 2..10)assertTrue(fanCycleMillis(level)<fanCycleMillis(level-1))
        assertEquals(fanCycleMillis(1),fanCycleMillis(-1));assertEquals(fanCycleMillis(10),fanCycleMillis(100))
    }
}
