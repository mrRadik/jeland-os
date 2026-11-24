package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.ClimateState
import ru.jeland.os.ui.climateDockTemperature
import ru.jeland.os.ui.climateFanLevel

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
    @Test fun fanScaleUsesReportedSpeedAndDoesNotShowStaleOrUnknownLevels() {
        val s=ClimateState(power=true,fan=5)
        assertEquals(5,climateFanLevel(s))
        assertEquals(10,climateFanLevel(s.copy(fan=10)))
        assertEquals(0,climateFanLevel(s.copy(power=false)))
        assertEquals(0,climateFanLevel(s.copy(fan=0)))
        assertNull(climateFanLevel(s.copy(available=setOf(18))))
        assertNull(climateFanLevel(s.copy(available=setOf(12))))
        assertNull(climateFanLevel(s.copy(fan=11)))
        assertNull(climateFanLevel(s.copy(fan=-1)))
    }
}
