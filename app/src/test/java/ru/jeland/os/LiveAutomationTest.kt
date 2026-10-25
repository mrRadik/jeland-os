package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.core.automation.*

class LiveAutomationTest {
    private val now=100_000L
    private fun link(vararg values:Pair<Int,Int>)=VehicleLinkState(demo=false,connected=true,writesEnabled=true,
        raw=values.associate {(cmd,value)->VdsKey(VerifiedVehicleProtocol.CAR,cmd) to RawVehicleSample(listOf(value),now)})
    @Test fun knownCommandsMatchExistingVerifiedManualProtocol() {
        val actions=listOf(SeatAction(ComponentType.DRIVER_SEAT,SeatLevel.LEVEL_2,SeatLevel.OFF),
            SeatAction(ComponentType.PASSENGER_SEAT,SeatLevel.OFF,SeatLevel.LEVEL_1),
            SeatAction(ComponentType.REAR_LEFT_SEAT,SeatLevel.LEVEL_3),SeatAction(ComponentType.REAR_RIGHT_SEAT,SeatLevel.LEVEL_1),SteeringWheelAction.HEAT_ON)
        val plan=LiveAutomationPlanner.plan(actions,link(95 to 0,93 to 1,96 to 2,94 to 0,139 to 0,141 to 0,160 to 0),now)
        assertNull(plan.failure);assertEquals(7,plan.writes.size)
        assertTrue(plan.writes.all {VerifiedVehicleProtocol.allowed(it.write)})
        assertEquals(listOf(0,0),plan.writes.take(2).map {(it.command as AutomationCommand.Seat).level})
        assertEquals(VerifiedVehicleProtocol.seat(95,2),plan.writes.single {it.write.key.cmd==95}.write)
        assertEquals(VerifiedVehicleProtocol.toggle(160,0),plan.writes.last().write)
    }
    @Test fun demoDisconnectedAndWriteLockedNeverProduceRequests() {
        val rule=listOf(SteeringWheelAction.HEAT_ON)
        val valid=link(160 to 0)
        for(state in listOf(valid.copy(demo=true),valid.copy(connected=false),valid.copy(writesEnabled=false))) {
            val plan=LiveAutomationPlanner.plan(rule,state,now)
            assertTrue(plan.writes.isEmpty());assertEquals(AutomationRunStatus.NO_CONNECTION,plan.failure!!.status)
        }
    }
    @Test fun staleNullMalformedAndOutOfRangeFeedbackCannotTurnSteeringOn() {
        for(sample in listOf(RawVehicleSample(listOf(0),now-15_001),RawVehicleSample(listOf(0),now+1),RawVehicleSample(null,now),RawVehicleSample(listOf(0,1),now),RawVehicleSample(listOf(7),now))) {
            val state=link().copy(raw=mapOf(VdsKey(VerifiedVehicleProtocol.CAR,160) to sample))
            assertEquals(AutomationRunStatus.UNKNOWN_STATE,LiveAutomationPlanner.plan(listOf(SteeringWheelAction.HEAT_ON),state,now).failure!!.status)
        }
    }
    @Test fun onIsIdempotentAndDoesNotToggleTheWheelOff() {
        val plan=LiveAutomationPlanner.plan(listOf(SteeringWheelAction.HEAT_ON),link(160 to 1),now)
        assertNull(plan.failure);assertTrue(plan.writes.isEmpty())
    }
    @Test fun keepDoesNotDisableAnotherModeOrAllowConflictingHeat() {
        val plan=LiveAutomationPlanner.plan(listOf(SeatAction(ComponentType.DRIVER_SEAT,SeatLevel.LEVEL_1)),link(95 to 0,93 to 2),now)
        assertEquals(AutomationRunStatus.STATE_CONFLICT,plan.failure!!.status);assertTrue(plan.writes.isEmpty())
    }
    @Test fun preflightRejectsTheEntireScenarioBeforeAnyWrite() {
        val plan=LiveAutomationPlanner.plan(listOf(SteeringWheelAction.HEAT_ON,SeatAction(ComponentType.REAR_RIGHT_SEAT,SeatLevel.LEVEL_1)),link(160 to 0),now)
        assertTrue(plan.writes.isEmpty());assertEquals(AutomationRunStatus.UNKNOWN_STATE,plan.failure!!.status)
    }
    @Test fun rearSeatDoesNotRequireOrTouchOtherSeatChannels() {
        val plan=LiveAutomationPlanner.plan(listOf(SeatAction(ComponentType.REAR_LEFT_SEAT,SeatLevel.LEVEL_2)),link(139 to 0),now)
        assertNull(plan.failure);assertEquals(listOf(139),plan.writes.map {it.write.key.cmd})
    }
    @Test fun shadeDoesNotRequireSpeedAndDoesNotBlockOtherDevices() {
        val actions=listOf(SunshadeAction.OPEN_HALF,SteeringWheelAction.HEAT_ON)
        // No speed or parked flag is supplied: neither preflight nor transport accepts one.
        val plan=LiveAutomationPlanner.plan(actions,link(10 to 1,160 to 0),now)
        assertNull(plan.failure);assertEquals(2,plan.writes.size)
        assertEquals(VerifiedVehicleProtocol.shade(Shade.HALF),plan.writes.first().write)
        assertNull(plan.writes.first().write.expected) // HALF is not a measured 50% confirmation.
    }
    @Test fun movingOrUnavailableShadeStillBlocksCommandsButCarMotionDoesNot() {
        for(raw in listOf(2,4,7)) {
            val plan=LiveAutomationPlanner.plan(listOf(SunshadeAction.OPEN_HALF,SteeringWheelAction.HEAT_ON),link(10 to raw,160 to 0),now)
            assertEquals(AutomationRunStatus.UNSAFE,plan.failure!!.status);assertTrue(plan.writes.isEmpty())
        }
    }
    @Test fun shadeStillRequiresFreshKnownStateAndConnection() {
        val actions=listOf(SunshadeAction.OPEN_FULL)
        val stale=link(10 to 1).copy(raw=mapOf(VdsKey(VerifiedVehicleProtocol.CAR,10) to RawVehicleSample(listOf(1),now-15_001)))
        assertEquals(AutomationRunStatus.UNKNOWN_STATE,LiveAutomationPlanner.plan(actions,stale,now).failure!!.status)
        assertEquals(AutomationRunStatus.UNKNOWN_STATE,LiveAutomationPlanner.plan(actions,link(),now).failure!!.status)
        assertEquals(AutomationRunStatus.NO_CONNECTION,LiveAutomationPlanner.plan(actions,link(10 to 1).copy(connected=false),now).failure!!.status)
    }
    @Test fun alreadyFullyOpenShadeDoesNotGetAnotherCommand() {
        val plan=LiveAutomationPlanner.plan(listOf(SunshadeAction.OPEN_FULL),link(10 to 5),now)
        assertNull(plan.failure);assertTrue(plan.writes.isEmpty())
    }
    @Test fun realOutsideTemperatureCannotBecomeFreshByRepeatedReading() {
        val reading=ru.jeland.os.core.automation.OutsideTemperature(5.0,now,TemperatureSource.DESAY_VDS)
        assertTrue(reading.fresh(now+15_000));assertFalse(reading.fresh(now+15_001))
    }
}
