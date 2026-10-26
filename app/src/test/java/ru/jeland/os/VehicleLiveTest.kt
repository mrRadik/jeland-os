package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class VehicleLiveTest {
    private val protocol=VerifiedVehicleProtocol
    @Test fun nativeCameraRequestOnlyAllowsExactStockDisplayCommand() {
        val request=protocol.openCameras()
        assertEquals(VdsKey(0x50008,8),request.key);assertEquals(1,request.value)
        assertNull(request.expected);assertTrue(protocol.allowed(request))
        for(cmd in 0..100)for(value in 0..3) {
            assertEquals(cmd==8&&value==1,protocol.allowed(VdsWrite(VdsKey(protocol.AVM,cmd),value)))
        }
        assertFalse(protocol.allowed(request.copy(expected=1)))
        assertFalse(protocol.requiresClimatePower(request))
    }
    @Test fun ownerFormulasAreCentralizedAndExact() {
        assertEquals(2.4,ExperimentalVehicleConversions.pressureBar(175)!!,1e-9)
        assertEquals(2.414285714,ExperimentalVehicleConversions.pressureBar(176)!!,1e-8)
        val f=ExperimentalVehicleConversions.fuel(950)
        assertEquals(95.0,f.percent!!,1e-9);assertEquals(52.25,f.remainingLiters!!,1e-9)
        assertEquals(3,f.litersToFull);assertTrue(f.estimated);assertEquals(950,f.raw)
        assertEquals(3000,ExperimentalVehicleConversions.engineRpm(750))
    }
    @Test fun experimentalFuelDoesNotClampInvalidSensorDataIntoFullTank() {
        listOf(null,-1,1001,65535).forEach {assertNull(ExperimentalVehicleConversions.fuel(it).remainingLiters)}
        assertEquals(55.0,ExperimentalVehicleConversions.fuel(1000).remainingLiters!!,0.0)
        assertEquals(0.0,ExperimentalVehicleConversions.fuel(0).remainingLiters!!,0.0)
        assertNull(ExperimentalVehicleConversions.pressureBar(-1));assertNull(ExperimentalVehicleConversions.pressureBar(null))
        assertNull(ExperimentalVehicleConversions.engineRpm(-1));assertNull(ExperimentalVehicleConversions.engineRpm(Int.MAX_VALUE))
    }
    @Test fun telemetryDecodesScalarOrBigEndianPairWithoutGuessingLargerVectors() {
        assertEquals(1000,protocol.unsigned(listOf(3,232)));assertEquals(950,protocol.unsigned(listOf(950)))
        listOf(null,emptyList(),listOf(-1),listOf(0,256),listOf(1,2,3)).forEach {assertNull(protocol.unsigned(it))}
    }
    @Test fun liveStartsUnknownNeverAtMock22Degrees31LitersOr24Bar() {
        val r=LiveVehicleReducer();assertTrue(r.climate(0).available!!.isEmpty());assertTrue(r.seats(0).available!!.isEmpty())
        val v=r.vehicle(0);assertTrue(v.live);assertNull(v.fuel.displayLiters);assertTrue(v.tires.isEmpty())
        assertTrue(v.knownOpenings.isEmpty());assertFalse(v.steeringKnown);assertNull(v.shadeRaw);assertNull(v.engineRpm)
    }
    @Test fun nullAndExpiredValuesAreNotZeroOrClosed() {
        val r=LiveVehicleReducer();val door=VdsKey(protocol.INFO,15)
        r.accept(door,listOf(1),100);assertTrue(VehicleOpening.FRONT_LEFT in r.vehicle(101).openings)
        r.accept(door,null,102);assertFalse(VehicleOpening.FRONT_LEFT in r.vehicle(103).knownOpenings)
        r.accept(door,listOf(0),104);assertTrue(VehicleOpening.FRONT_LEFT in r.vehicle(105).knownOpenings)
        assertFalse(VehicleOpening.FRONT_LEFT in r.vehicle(15_105).knownOpenings)
        r.clear();assertTrue(r.snapshot().isEmpty())
    }
    @Test fun allFourDoorsAndTrunkRemainIndependentButHoodIsNotClaimedVerified() {
        val r=LiveVehicleReducer()
        for(cmd in 15..19)r.accept(VdsKey(protocol.INFO,cmd),listOf(if(cmd==16||cmd==18)1 else 0),5)
        assertEquals(setOf(VehicleOpening.FRONT_RIGHT,VehicleOpening.REAR_RIGHT),r.vehicle(6).openings)
        assertEquals(5,r.vehicle(6).knownOpenings.size);assertFalse(VehicleOpening.HOOD in r.vehicle(6).knownOpenings)
        r.accept(VdsKey(protocol.INFO,15),listOf(99),7);assertFalse(VehicleOpening.FRONT_LEFT in r.vehicle(8).knownOpenings)
    }
    @Test fun readsDriveSeatIndicatorsWithoutHeatVentExclusionAssumptions() {
        val r=LiveVehicleReducer()
        for(cmd in listOf(93,94,95,96,139,141))r.accept(VdsKey(protocol.CAR,cmd),listOf(if(cmd==141)2 else 3),0)
        val s=r.seats(1);assertEquals(SeatChannel(3,3),s.driver);assertEquals(RearHeat.BOTH,s.rear)
        assertEquals(3,s.rearLeft);assertEquals(2,s.rearRight)
        r.accept(VdsKey(protocol.CAR,95),listOf(4),2);assertFalse(r.seats(3).known(95))
    }
    @Test fun telemetryUsesPhotoCalibrationWithProvisionalWheelOrderAndDoesNotInventRpm() {
        val r=LiveVehicleReducer()
        r.accept(VdsKey(protocol.INFO,68),listOf(3,232),1)
        r.accept(VdsKey(protocol.INFO,140),listOf(175,176,174,172),1)
        r.accept(VdsKey(protocol.INFO,62),listOf(2,238),1)
        val v=r.vehicle(2);assertEquals(55.0,v.fuel.remainingLiters!!,1e-9);assertNull(v.engineRpm)
        assertEquals(4,v.tireChannels.size);assertEquals(2.4,v.tireChannels[0]!!,1e-9)
        assertEquals(2.4,v.tires.getValue(Wheel.FRONT_LEFT).validBar!!,1e-9)
        assertTrue(v.tires.values.all {it.temperature==null})
        r.accept(VdsKey(protocol.INFO,140),null,3);assertTrue(r.vehicle(4).tireChannels.isEmpty())
    }
    @Test fun shadeCommandNeverInventsFinalStateOrStop() {
        val r=LiveVehicleReducer();r.accept(VdsKey(protocol.CAR,10),listOf(7),1)
        val v=r.vehicle(2,Shade.OPEN);assertEquals(listOf(7),v.shadeRaw);assertEquals(Shade.OPEN,v.requestedShade)
        assertEquals(1,protocol.shade(Shade.OPEN).value);assertEquals(2,protocol.shade(Shade.CLOSED).value);assertEquals(3,protocol.shade(Shade.HALF).value)
        assertEquals(VdsKey(protocol.CAR,10),protocol.shade(Shade.OPEN).feedback);assertEquals(5,protocol.shade(Shade.OPEN).expected)
        assertEquals(1,protocol.shade(Shade.CLOSED).expected);assertNull(protocol.shade(Shade.HALF).expected)
    }
    @Test fun shadeFeedbackIsIndependentOfRequestedPositionAndUnknownIsNeverClosed() {
        val r=LiveVehicleReducer()
        for((raw,title) in mapOf(1 to "Закрыта",2 to "Открывается",3 to "Частично открыта",4 to "Закрывается",5 to "Открыта",7 to "Недоступна")) {
            r.accept(VdsKey(protocol.CAR,10),listOf(raw),1)
            val vehicle=r.vehicle(2,Shade.OPEN)
            assertEquals(title,vehicle.shadeSummary)
            assertEquals(when(raw){1->Shade.CLOSED;5->Shade.OPEN;else->null},vehicle.reportedShade)
        }
        r.accept(VdsKey(protocol.CAR,10),null,3);assertNull(r.vehicle(4).reportedShade)
        assertEquals("Нет данных",r.vehicle(4).shadeSummary)
    }
    @Test fun photoPressureMatchesLeftFrontLeftRearButRightOrderIsStillProvisional() {
        val r=LiveVehicleReducer();r.accept(VdsKey(protocol.INFO,140),listOf(182,171,175,169),1)
        val v=r.vehicle(2)
        val rounded=Wheel.entries.map {String.format(java.util.Locale.US,"%.1f",v.tires.getValue(it).validBar)}
        assertEquals(listOf("2.5","2.3","2.4","2.3"),rounded)
        r.accept(VdsKey(protocol.INFO,140),listOf(255,0,175,169),3)
        assertTrue(r.vehicle(4).tires.getValue(Wheel.FRONT_LEFT).validBar!!>3.5)
        assertEquals(0.0,r.vehicle(4).tires.getValue(Wheel.FRONT_RIGHT).validBar!!,0.0)
        assertTrue(r.vehicle(16000).tires.isEmpty())
    }
    @Test fun diagramFitsFullImageWithRoomForOpenDoorsAcrossWidgetShapes() {
        for((w,h) in listOf(850f to 150f,850f to 220f,850f to 400f,500f to 160f)) {
            val g=VehicleDiagramGeometry.fit(w,h)
            assertEquals(2f,g.width/g.height,.001f)
            assertTrue(g.x>=0 && g.y>=0 && g.x+g.width<=w && g.y+g.height<=h)
            assertTrue(g.py(VehicleArtworkCoordinates.wheelLateral(false))-g.width*VehicleArtworkCoordinates.DOOR_LENGTH*.91f>=0)
            assertTrue(g.py(VehicleArtworkCoordinates.wheelLateral(true))+g.width*VehicleArtworkCoordinates.DOOR_LENGTH*.91f<=h)
        }
    }
    @Test fun everyFieldProvenWriteIsAllowlistedAndNoMirrorClusterOrRearVentExists() {
        for(cmd in listOf(93,94,95,96,139,141))for(level in 0..3){
            val w=protocol.seat(cmd,level);assertEquals(level+1,w.value);assertEquals(level,w.expected);assertTrue(protocol.allowed(w))
        }
        for(cmd in listOf(18,1,24,65,8))for(enabled in listOf(true,false))assertTrue(protocol.allowed(protocol.binary(cmd,enabled)))
        for(cmd in listOf(80,160))for(before in 0..1)assertTrue(protocol.allowed(protocol.toggle(cmd,before)))
        for(driver in listOf(true,false))for(raw in 0..16)assertTrue(protocol.allowed(protocol.temperature(driver,raw)))
        for(raw in 1..10)assertTrue(protocol.allowed(protocol.fan(raw)))
        Shade.entries.forEach {assertTrue(protocol.allowed(protocol.shade(it)))}
        for(cmd in listOf(142,144,174,204))assertFalse(protocol.allowed(VdsWrite(VdsKey(protocol.CAR,cmd),1)))
        assertFalse(protocol.allowed(VdsWrite(VdsKey(protocol.HVAC,4),2)))
        assertFalse(protocol.allowed(VdsWrite(VdsKey(protocol.VR,124),0)))
    }
    @Test fun recirculationAndToggleStatusesAreNotTheCommandEnums() {
        assertEquals(0,protocol.binary(8,true).expected);assertEquals(1,protocol.binary(8,false).expected)
        assertEquals(1,protocol.toggle(160,0).value);assertEquals(1,protocol.toggle(160,1).value)
        assertEquals(0,protocol.toggle(160,1).expected)
        assertEquals(6,protocol.airflow(5).expected);assertEquals(7,protocol.airflow(6).expected)
    }
    @Test fun malformedVectorsDoNotCreateScalarStateAndUnknownIdsAreIgnored() {
        val r=LiveVehicleReducer();r.accept(VdsKey(protocol.HVAC,10),listOf(5,6),1)
        assertFalse(r.climate(2).known(10));r.accept(VdsKey(0x9999,1),listOf(1),2)
        assertEquals(1,r.snapshot().size)
    }
    @Test fun climateReadsRawTemperatureAndNoDataExpiry() {
        val r=LiveVehicleReducer()
        for((cmd,value) in mapOf(10 to 6,13 to 7,18 to 1,8 to 0,6 to 6,12 to 3))r.accept(VdsKey(protocol.HVAC,cmd),listOf(value),10)
        val c=r.climate(11);assertEquals(21.0,c.driver,0.0);assertEquals(22.0,c.passenger,0.0)
        assertTrue(c.recirculation);assertTrue(c.power);assertEquals(6,c.airflowRaw);assertEquals(3,c.fan)
        assertTrue(r.climate(15011).available!!.isEmpty())
    }
}
