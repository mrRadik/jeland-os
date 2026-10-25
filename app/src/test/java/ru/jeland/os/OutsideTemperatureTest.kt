package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class OutsideTemperatureTest {
    private fun link(values:List<Int>?,time:Long=1000)=VehicleLinkState(demo=false,connected=true,
        raw=mapOf(OutsideTemperature.key to RawVehicleSample(values,time)))
    @Test fun matchesOemFormulaAndBothPhysicalClusterReadings() {
        assertEquals(12.5,OutsideTemperature.celsius(link(listOf(135)),1000)!!,0.0)
        assertEquals(19.5,OutsideTemperature.celsius(link(listOf(149)),1000)!!,0.0)
        assertEquals(20.0,OutsideTemperature.celsius(link(listOf(150)),1000)!!,0.0)
        val reducer=LiveVehicleReducer();reducer.accept(OutsideTemperature.key,listOf(149),1000)
        assertEquals(listOf(149),reducer.snapshot()[OutsideTemperature.key]?.values)
        assertFalse(reducer.climate(1000).known(19)) // not an HVAC toggle or actuator
        assertFalse(VerifiedVehicleProtocol.allowed(VdsWrite(OutsideTemperature.key,1)))
    }
    @Test fun staleMissingMalformedDisconnectedAndWrongModuleNeverProduceAValue() {
        for(raw in listOf(null,emptyList(),listOf(1,2),listOf(-1),listOf(255),listOf(999)))
            assertNull(OutsideTemperature.celsius(link(raw),1000))
        assertNull(OutsideTemperature.celsius(link(listOf(149)),16001))
        assertNull(OutsideTemperature.celsius(link(listOf(149)),999))
        assertNull(OutsideTemperature.celsius(link(listOf(149)).copy(demo=true),1000))
        assertNull(OutsideTemperature.celsius(link(listOf(149)).copy(connected=false),1000))
        assertNull(OutsideTemperature.celsius(link(listOf(149)).copy(raw=mapOf(VdsKey(VerifiedVehicleProtocol.INFO,19) to RawVehicleSample(listOf(149),1000))),1000))
    }
    @Test fun liveFormattingHasHalfDegreesAndNoDemoOrAaosFallback() {
        assertEquals("+19,5°",WeatherState(demo=false,outsideCelsius=19.5).temperatureLabel)
        assertEquals("+11°",WeatherState(demo=false,outsideCelsius=11.0).temperatureLabel)
        assertEquals("0°",WeatherState(demo=false,outsideCelsius=0.0).temperatureLabel)
        assertEquals("-5,5°",WeatherState(demo=false,outsideCelsius=-5.5).temperatureLabel)
        assertEquals("—°",WeatherState(temperature=25,demo=false).temperatureLabel)
        assertEquals("—°",OutsideTemperature.label(Double.NaN))
    }
}
