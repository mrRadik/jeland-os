package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.VehicleSpeedSample

class TripMeterTest {
    @Test fun carInfoGaugeSpeedRecordsDistanceAndEstimatedAcceleration() {
        val meter = TripMeter()
        for (i in 0..30) {
            val sample = requireNotNull(VehicleSpeedSample.fromCarInfo(intArrayOf(0, i * 4), i * 100L))
            assertEquals(i * 4.0, sample.kmh, .001)
            meter.accept(sample, 10_000L + sample.atMillis)
        }
        val trip = requireNotNull(meter.summary)
        assertEquals(120.0, trip.maxKmh, .001)
        assertTrue(trip.distanceMetres > 40)
        assertEquals(2_482L, trip.bestZeroToHundredMillis!!)
        assertEquals(260.0, requireNotNull(VehicleSpeedSample.fromCarInfo(intArrayOf(1, 4), 100L)).kmh, .001)
        assertNull(VehicleSpeedSample.fromCarInfo(intArrayOf(255, 255), 100L))
        assertNull(VehicleSpeedSample.fromCarInfo(intArrayOf(7), 100L))
    }

    @Test fun freshVdsSamplesUseElapsedTimeAndStartATrip() {
        val wallAtBoot = 1_799_000_000_000L
        val meter = TripMeter()
        for ((at, speed) in listOf(100_000L to 0.0, 100_100L to 5.0, 100_200L to 10.0)) {
            val sample = requireNotNull(VehicleSpeedSample.fromVds(speed, at, at + 25, wallAtBoot + at + 25))
            assertEquals(at, sample.atMillis)
            meter.accept(sample, wallAtBoot + sample.atMillis)
        }
        val trip = requireNotNull(meter.summary)
        assertEquals(wallAtBoot + 100_010L, trip.startedAt)
        assertEquals(36.0, trip.maxKmh, .001)
        assertTrue(trip.distanceMetres > 0)
    }

    @Test fun speedTimestampValidationAcceptsFreshEpochAndRejectsMissingOrStaleTimes() {
        val elapsed = 100_000L
        val wall = 1_799_000_100_000L
        assertEquals(VehicleSpeedSample(4.0, elapsed - 20), VehicleSpeedSample.fromVds(4.0, wall - 20, elapsed, wall))
        for (invalid in listOf(0L, elapsed - 3_001, elapsed + 1, wall - 3_001, wall + 1)) {
            assertNull(VehicleSpeedSample.fromVds(4.0, invalid, elapsed, wall))
        }
    }

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
