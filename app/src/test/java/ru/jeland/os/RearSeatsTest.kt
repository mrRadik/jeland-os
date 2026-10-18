package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.MockSeatRepository

class RearSeatsTest {
    @Test fun separateRearLevelsPersistAndDoNotChangeFrontSeats() {
        val store=MemorySettingsStore();val r=MockSeatRepository(store)
        r.setLevel(SeatSide.DRIVER,SeatMode.HEAT,2);r.setLevel(SeatSide.PASSENGER,SeatMode.VENTILATE,3)
        val before=r.state.value
        r.setRearLevel(RearSeatSide.LEFT,2);r.setRearLevel(RearSeatSide.RIGHT,3)
        assertEquals(2,r.state.value.rearLeft);assertEquals(3,r.state.value.rearRight);assertEquals(RearHeat.BOTH,r.state.value.rear)
        r.setRearLevel(RearSeatSide.LEFT,0)
        assertEquals(3,r.state.value.rearRight);assertEquals(RearHeat.RIGHT,r.state.value.rear)
        val restored=MockSeatRepository(store).state.value
        assertEquals(r.state.value,restored);assertEquals(before.driver,restored.driver);assertEquals(before.passenger,restored.passenger)
    }
    @Test fun legacyRearSwitchMigratesAndRemainsConsistentWithLevels() {
        val store=MemorySettingsStore();store.put("seat.rear","BOTH")
        val r=MockSeatRepository(store);assertEquals(1,r.state.value.rearLeft);assertEquals(1,r.state.value.rearRight)
        r.setRearLevel(RearSeatSide.LEFT,3);r.setRear(RearHeat.LEFT)
        assertEquals(3,r.state.value.rearLeft);assertEquals(0,r.state.value.rearRight)
        r.setRear(RearHeat.OFF);assertEquals(0,r.state.value.rearLeft);assertEquals(0,r.state.value.rearRight)
    }
    @Test fun independentCommandsUseExistingRearHeatIdsAndOneBasedWireLevels() {
        for(side in RearSeatSide.entries)for(level in 0..3) {
            val w=VerifiedVehicleProtocol.rearSeat(side,level)
            assertEquals(VdsKey(VerifiedVehicleProtocol.CAR,if(side==RearSeatSide.LEFT)139 else 141),w.key)
            assertEquals(level+1,w.value);assertEquals(level,w.expected)
            assertTrue(VerifiedVehicleProtocol.allowed(w));assertFalse(VerifiedVehicleProtocol.requiresClimatePower(w))
        }
        assertTrue(runCatching {VerifiedVehicleProtocol.rearSeat(RearSeatSide.LEFT,4)}.isFailure)
        assertTrue(runCatching {VerifiedVehicleProtocol.rearSeat(RearSeatSide.RIGHT,-1)}.isFailure)
    }
    @Test fun eachRearChannelCanBeKnownWithoutTheOtherAndExpiresIndependently() {
        val r=LiveVehicleReducer();r.accept(VdsKey(VerifiedVehicleProtocol.CAR,139),listOf(2),100)
        assertEquals(2,r.seats(101).rearLevel(RearSeatSide.LEFT));assertNull(r.seats(101).rearLevel(RearSeatSide.RIGHT))
        r.accept(VdsKey(VerifiedVehicleProtocol.CAR,141),listOf(3),10000)
        assertEquals(2,r.seats(10001).rearLevel(RearSeatSide.LEFT));assertEquals(3,r.seats(10001).rearLevel(RearSeatSide.RIGHT))
        assertNull(r.seats(16000).rearLevel(RearSeatSide.LEFT));assertEquals(3,r.seats(16000).rearLevel(RearSeatSide.RIGHT))
    }
}
