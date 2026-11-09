package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.TripAutoStart
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.TripPeriod
import ru.jeland.os.core.TripSummary
import ru.jeland.os.core.VehicleSpeedSample
import ru.jeland.os.core.totals
import java.time.LocalDate
import java.time.ZoneId

class TripRecordingPolicyTest {
    @Test fun automaticRecordingRequiresRunningEngineAndPositiveLiveSpeedOnlyOncePerCycle() {
        val start = TripAutoStart()
        fun speed(kmh: Double, at: Long) = VehicleSpeedSample(kmh / 3.6, at)
        assertFalse(start.claim(speed(5.0, 1000), 1000))
        assertFalse(start.engine(1, 1, 2000, 2000))
        assertFalse(start.claim(speed(0.0, 2000), 2000))
        assertTrue(start.claim(speed(1.0, 2100), 2100))
        assertFalse(start.engine(0, 1, 3000, 3000))
        assertFalse(start.engine(1, 1, 5000, 5000))
        assertFalse(start.claim(speed(10.0, 5100), 5100))
        assertFalse(start.engine(0, 0, 6000, 6000))
        assertTrue(start.engine(0, 0, 7500, 7500))
        assertFalse(start.claim(speed(5.0, 7600), 7600))
        start.engine(1, 1, 8000, 8000)
        assertTrue(start.claim(speed(1.0, 8100), 8100))
    }

    @Test fun cachedEngineAndSpeedCannotArmRecording() {
        val start = TripAutoStart()
        start.engine(1, 1, 1000, 1000)
        assertFalse(start.claim(VehicleSpeedSample(4.0, 17_000), 17_000))
        start.engine(1, 1, 17_500, 17_500)
        assertFalse(start.claim(VehicleSpeedSample(4.0, 14_499), 17_500))
        start.interrupted()
        assertFalse(start.claim(VehicleSpeedSample(4.0, 17_600), 17_600))
    }

    @Test fun automaticMeterStartsBelowTheManualMovementThreshold() {
        val meter = TripMeter(startAtAnyMovement = true)
        meter.accept(VehicleSpeedSample(0.0, 1000), 10_000)
        meter.accept(VehicleSpeedSample(1.0 / 3.6, 1500), 10_500)
        val trip = requireNotNull(meter.summary)
        assertEquals(500L, trip.movingMillis)
        assertEquals(1.0, trip.maxKmh, .001)
        assertFalse(trip.incomplete)
    }

    @Test fun dateFilterUsesLocalCalendarAndSummaryOnlyIncludesMatchedTrips() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 10, 25)
        fun start(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val included = listOf(
            TripSummary(start(today), 2000, 1000, 80.0, 100.0, 7400),
            TripSummary(start(today.minusDays(6)), 5000, 3000, 120.0, 120.0, 6500)
        )
        val outside = listOf(TripSummary(start(today.minusDays(6)) - 1), TripSummary(start(today.plusDays(1))))
        val totals = (included + outside).filter { TripPeriod.WEEK.contains(it.startedAt, today, zone) }.totals()
        assertEquals(2, totals.count)
        assertEquals(200.0, totals.distanceMetres, .001)
        assertEquals(4000L, totals.movingMillis)
        assertEquals(120.0, totals.maxKmh!!, .001)
        assertEquals(6500L, totals.bestZeroToHundredMillis!!)
    }
}
