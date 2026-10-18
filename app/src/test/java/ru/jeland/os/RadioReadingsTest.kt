package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class RadioReadingsTest {
    @Test fun wifiRejectsUnknownRssiInsteadOfDrawingFullSignal() {
        listOf(null,Int.MIN_VALUE,-127,0,127).forEach {assertNull(RadioReadings.wifiLevel(it))}
        assertEquals(0,RadioReadings.wifiLevel(-110));assertEquals(0,RadioReadings.wifiLevel(-100))
        assertEquals(2,RadioReadings.wifiLevel(-75));assertEquals(4,RadioReadings.wifiLevel(-47))
        assertTrue((-126..-1).map {RadioReadings.wifiLevel(it)!!}.zipWithNext().all {(a,b)->a<=b})
    }
    @Test fun mobileDoesNotInterpretRawAsuAsBars() {
        listOf(null,-1,5,26,99,Int.MAX_VALUE).forEach {assertNull(RadioReadings.mobileLevel(it))}
        (0..4).forEach {assertEquals(it,RadioReadings.mobileLevel(it))}
        assertEquals("4G",RadioReadings.mobileType(" 4g "))
        listOf(null,"NoNetwork","Connecting","LTE","garbage").forEach {assertNull(RadioReadings.mobileType(it))}
    }
    @Test fun weatherNeverFormatsMissingForecastAsDemoTemperature() {
        assertEquals("—°",WeatherState(temperature=null,demo=false).temperatureLabel)
        assertEquals("+8°",WeatherState().temperatureLabel)
        assertEquals("0°",WeatherState(temperature=0).temperatureLabel)
        assertEquals("-8°",WeatherState(temperature=-8).temperatureLabel)
    }
}
