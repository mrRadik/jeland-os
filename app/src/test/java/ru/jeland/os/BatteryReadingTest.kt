package ru.jeland.os

import org.junit.Test
import org.junit.Assert.*
import ru.jeland.os.core.*

class BatteryReadingTest {
    private val fixture=mapOf(76 to listOf(0,146),96 to listOf(84),97 to listOf(100),109 to listOf(0,1,128,230),110 to listOf(44,131),111 to listOf(53),113 to listOf(139),115 to listOf(5))
    @Test fun decodesLoggedBatteryUsingEngModeFormulas() {
        val b=BatteryDecoder.decode {fixture[it]}
        assertEquals(14.6,b.voltage!!,1e-8);assertEquals(84,b.chargePercent);assertEquals(100,b.healthPercent)
        assertEquals(14.1279296875,b.ibsVoltage!!,1e-8);assertEquals(13,b.temperatureCelsius)
        assertEquals(1.796875,b.currentAmps!!,1e-8);assertEquals(8.6875,b.startingVoltage!!,1e-8)
        assertEquals(.01953125,b.quiescentAmps!!,1e-8)
    }
    @Test fun missingMalformedSentinelsNeverBecomeSyntheticZero() {
        assertEquals(BatteryReading(),BatteryDecoder.decode {null})
        assertEquals(BatteryReading(),BatteryDecoder.decode {emptyList()})
        val b=BatteryDecoder.decode {when(it){76,110->listOf(255,255);109->listOf(0,9,128,230);else->listOf(255)}}
        assertEquals(BatteryReading(),b)
        assertEquals(BatteryReading(),BatteryDecoder.decode {listOf(-1)})
        assertEquals(BatteryReading(),BatteryDecoder.decode {listOf(256)})
    }
    @Test fun liveReadingsExpireClearAndCannotMixWithSeatCommands() {
        val r=LiveVehicleReducer()
        fixture.forEach {(cmd,value)->r.accept(VdsKey(VerifiedVehicleProtocol.INFO,cmd),value,100)}
        assertEquals(14.6,r.vehicle(100).battery.voltage!!,1e-8)
        assertEquals(BatteryReading(),r.vehicle(15101).battery)
        r.accept(VdsKey(VerifiedVehicleProtocol.INFO,76),null,200)
        assertNull(r.vehicle(200).battery.voltage)
        r.clear();r.accept(VdsKey(VerifiedVehicleProtocol.CAR,96),listOf(3),300)
        assertNull(r.vehicle(300).battery.chargePercent)
        assertEquals(BatteryReading(),r.vehicle(300).battery)
    }
    @Test fun subscriptionsAreReadOnlyAndRealZeroIsPreserved() {
        assertTrue(VerifiedVehicleProtocol.subscriptions.getValue(VerifiedVehicleProtocol.INFO).containsAll(BatteryDecoder.commands))
        BatteryDecoder.commands.forEach {assertFalse(VerifiedVehicleProtocol.allowed(VdsWrite(VdsKey(VerifiedVehicleProtocol.INFO,it),1)))}
        assertEquals(0,BatteryDecoder.decode {if(it==96)listOf(0)else null}.chargePercent)
    }
}
