package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.*

class ClimateControlsTest {
    @Test fun allSevenCommandEnumsMatchOwnerTableAndOemFeedback() {
        val f=AirOutlet.FACE;val n=AirOutlet.FEET;val g=AirOutlet.WINDSHIELD
        val combinations=listOf(setOf(f),setOf(f,n),setOf(n),setOf(n,g),setOf(g),setOf(f,g),setOf(f,n,g))
        combinations.forEachIndexed {raw,outlets->
            val mode=Airflow.fromCommand(raw)!!
            assertEquals(outlets,mode.outlets);assertEquals(mode,Airflow.fromOutlets(outlets))
            val write=VerifiedVehicleProtocol.airflow(raw)
            assertEquals(raw,write.value);assertTrue(VerifiedVehicleProtocol.allowed(write))
            assertEquals(mode,Airflow.fromFeedback(write.expected))
        }
        assertEquals(Airflow.FACE_GLASS,Airflow.fromFeedback(6))
        assertEquals(Airflow.ALL,Airflow.fromFeedback(7));assertNull(Airflow.fromFeedback(5))
        for(raw in listOf(-1,7,8,255))assertTrue(runCatching {VerifiedVehicleProtocol.airflow(raw)}.isFailure)
    }
    @Test fun togglesNeverEmitEmptyOrUnrelatedDirections() {
        Airflow.entries.forEach {mode->AirOutlet.entries.forEach {outlet->
            val next=mode.toggle(outlet)
            if(mode.outlets==setOf(outlet))assertEquals(mode,next)
            else assertEquals(if(outlet in mode.outlets)mode.outlets-outlet else mode.outlets+outlet,next.outlets)
        }}
    }
    @Test fun liveHighlightsOnlyFreshDecodedFeedbackNotDefaultDemo() {
        val r=LiveVehicleReducer()
        assertNull(r.climate(0).reportedAirflow)
        Airflow.entries.forEach {mode->
            r.accept(VdsKey(VerifiedVehicleProtocol.HVAC,6),listOf(mode.feedbackRaw),100)
            assertEquals(mode,r.climate(101).reportedAirflow)
            assertNull(r.climate(16000).reportedAirflow)
        }
    }
    @Test fun demoNewControlsWorkAndPersistWithoutAnyVehicleSession() {
        val store=MemorySettingsStore();val r=MockClimateRepository(store)
        val power=r.state.value.power;r.toggleRearDefrost();r.toggleWindshieldHeat()
        r.setRawAirflow(2);assertEquals(Airflow.FEET,r.state.value.reportedAirflow)
        r.setRawAirflow(6);assertEquals(Airflow.ALL,r.state.value.reportedAirflow)
        r.togglePower();val saved=MockClimateRepository(store).state.value
        assertEquals(!power,saved.power);assertTrue(saved.rearDefrost);assertTrue(saved.windshieldHeat)
        assertEquals(Airflow.ALL,saved.reportedAirflow)
    }
    @Test fun panelCloseIsBoundedRateLimitedAndCancellable() {
        val w=HvacPanelCloseWindow();assertFalse(w.claim(0));w.arm(100)
        assertTrue(w.claim(100));assertFalse(w.claim(449));assertTrue(w.claim(450))
        assertTrue(w.claim(800));assertTrue(w.claim(1150));assertTrue(w.claim(1500))
        assertFalse(w.claim(1900));w.arm(2000);assertFalse(w.claim(5200))
        w.arm(6000);w.cancel();assertFalse(w.claim(6001))
    }
    @Test fun powerOffBlocksEveryClimateMutationButCanAlwaysBeSwitchedBackOn() {
        val store=MemorySettingsStore();val r=MockClimateRepository(store)
        r.togglePower();val before=r.state.value
        r.setFan(10);r.setAirflow(Airflow.ALL);r.setRawAirflow(2)
        r.adjustTemperature(true,1.0);r.adjustTemperature(false,-1.0)
        r.toggleAuto();r.toggleAc();r.toggleRecirculation();r.toggleDefrost();r.toggleSync()
        r.toggleRearDefrost();r.toggleWindshieldHeat()
        assertEquals(before,r.state.value);assertFalse(r.state.value.controlsEnabled)
        assertEquals(before,MockClimateRepository(store).state.value)
        r.togglePower();assertTrue(r.state.value.controlsEnabled)
        r.setFan(10);assertEquals(10,r.state.value.fan)
        assertFalse(ClimateState(power=true,available=emptySet()).controlsEnabled)
    }
    @Test fun livePowerGuardCoversHvacNotShadeOrIndependentSeats() {
        val p=VerifiedVehicleProtocol
        listOf(p.fan(5),p.airflow(2),p.temperature(true,5),p.binary(1,true),p.autoOn(),p.toggle(80,0),p.binary(65,true))
            .forEach {assertTrue(p.requiresClimatePower(it))}
        listOf(p.binary(18,true),p.binary(18,false),p.seat(95,2),p.shade(Shade.OPEN),p.toggle(160,0))
            .forEach {assertFalse(p.requiresClimatePower(it))}
    }
    @Test fun shadePresentationFollowsCommandButRawPhysicalFeedbackStaysSeparate() {
        val r=LiveVehicleReducer();r.accept(VdsKey(VerifiedVehicleProtocol.CAR,10),listOf(1),100)
        Shade.entries.forEach {target->
            val state=r.vehicle(101,target)
            assertEquals(target,state.shadeUiPosition);assertEquals(target.title,state.shadeUiSummary)
            assertEquals(Shade.CLOSED,state.reportedShade);assertEquals(listOf(1),state.shadeRaw)
        }
        assertEquals(Shade.CLOSED,r.vehicle(101).shadeUiPosition)
        assertNull(r.vehicle(20000).shadeUiPosition)
    }
}
