package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*

class TemperatureSyncTest {
    @Test fun syncInvertsDualFeedbackAndHasExactOemWireValues() {
        val p=VerifiedVehicleProtocol
        assertEquals(VdsWrite(VdsKey(p.HVAC,3),1,expected=0),p.sync(true))
        assertEquals(VdsWrite(VdsKey(p.HVAC,3),2,expected=1),p.sync(false))
        assertTrue(p.allowed(p.sync(true)));assertTrue(p.allowed(p.sync(false)))
        assertTrue(p.requiresClimatePower(p.sync(true)))
        val r=LiveVehicleReducer()
        assertFalse(r.climate(0).known(3));assertFalse(r.climate(0).sync)
        r.accept(VdsKey(p.HVAC,3),listOf(0),100);assertTrue(r.climate(101).sync)
        r.accept(VdsKey(p.HVAC,3),listOf(1),200);assertFalse(r.climate(201).sync)
        r.accept(VdsKey(p.HVAC,3),listOf(2),300);assertFalse(r.climate(301).known(3));assertFalse(r.climate(301).sync)
        r.accept(VdsKey(p.HVAC,3),listOf(0),400);assertFalse(r.climate(16000).known(3));assertFalse(r.climate(16000).sync)
    }
    @Test fun absoluteLiveTemperatureIsOneExactScalarIncludingLoHi() {
        val p=VerifiedVehicleProtocol
        for(driver in listOf(true,false))for(c in 15..31) {
            val write=p.absoluteTemperature(driver,c.toDouble())
            assertEquals(VdsKey(p.HVAC,if(driver)76 else 78),write.key)
            assertEquals(VdsKey(p.HVAC,if(driver)10 else 13),write.feedback)
            assertEquals(c-15,write.value);assertTrue(p.allowed(write));assertEquals(c-15,write.expected)
        }
        for(c in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,14.0,32.0,21.5))
            assertTrue(runCatching {p.absoluteTemperature(true,c)}.isFailure)
        for(level in 1..10)assertEquals(VdsWrite(VdsKey(p.HVAC,77),level,VdsKey(p.HVAC,12),level),p.fan(level))
        for(cmd in listOf(10,12,13,9,19))assertFalse(p.allowed(VdsWrite(VdsKey(p.HVAC,cmd),1,expected=1)))
    }
    @Test fun demoSliderUpdatesSelectedZoneAndRespectsSyncPowerAndPersistence() {
        val store=MemorySettingsStore();val r=MockClimateRepository(store)
        r.setTemperature(true,24.5);assertEquals(24.5,r.state.value.driver,0.0);assertEquals(22.0,r.state.value.passenger,0.0)
        r.toggleSync();r.setTemperature(false,20.5)
        assertEquals(20.5,r.state.value.driver,0.0);assertEquals(20.5,r.state.value.passenger,0.0)
        assertEquals(r.state.value,MockClimateRepository(store).state.value)
        val old=r.state.value
        for(c in listOf(Double.NaN,Double.POSITIVE_INFINITY,15.0,33.0))r.setTemperature(true,c)
        assertEquals(old,r.state.value)
        r.togglePower();val off=r.state.value;r.setTemperature(true,30.0);r.toggleSync();assertEquals(off,r.state.value)
    }
}
