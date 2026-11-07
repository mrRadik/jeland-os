package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.VehicleSpeedSample

class TripMeterTest {
    @Test fun stopsStayInTheTripAndMissingIntervalsDoNotAddDistance() {
        val meter = TripMeter()
        fun sample(ms: Long, speed: Double) = meter.accept(VehicleSpeedSample(speed, ms), 10_000 + ms)
        sample(0, 0.0)
        sample(1_000, 0.0)
        assertNull(meter.summary)
        sample(2_000, 10.0)
        sample(3_000, 10.0)
        sample(4_000, 0.0)
        sample(5_000, 0.0)
        val trip = requireNotNull(meter.summary)
        assertEquals(3_950L, trip.elapsedMillis)
        assertEquals(2_900L, trip.movingMillis)
        assertEquals(19.975, trip.distanceMetres, .001)
        assertEquals(36.0, trip.maxKmh, .001)
        sample(9_000, 20.0)
        val afterGap = requireNotNull(meter.summary)
        assertEquals(trip.distanceMetres, afterGap.distanceMetres, .001)
        assertEquals(trip.movingMillis, afterGap.movingMillis)
        assertTrue(afterGap.incomplete)
        assertEquals(72.0, afterGap.maxKmh, .001)
    }

    @Test fun accelerationRequiresFreshStopAndContinuousSamples() {
        fun drive(skipAtMillis: Long? = null, firstMillis: Long = 0): Long? {
            val meter = TripMeter()
            for (ms in firstMillis..6_000 step 100) {
                if (skipAtMillis != null && ms in skipAtMillis..skipAtMillis + 200) continue
                meter.accept(VehicleSpeedSample(ms / 200.0, ms), 10_000 + ms)
            }
            return meter.summary?.bestZeroToHundredMillis
        }
        assertEquals(5_516L, drive()!!)
        assertNull(drive(skipAtMillis = 2_000))
        assertNull(drive(firstMillis = 1_000))
    }
}
