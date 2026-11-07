package ru.jeland.os.core

import kotlin.math.abs
import kotlin.math.roundToLong

data class VehicleSpeedSample(val metresPerSecond: Double, val atMillis: Long) {
    val kmh get() = abs(metresPerSecond) * 3.6
}

data class TripSummary(
    val startedAt: Long,
    val elapsedMillis: Long = 0,
    val movingMillis: Long = 0,
    val distanceMetres: Double = 0.0,
    val maxKmh: Double = 0.0,
    val bestZeroToHundredMillis: Long? = null,
    val incomplete: Boolean = false
) {
    val averageMovingKmh get() = if (movingMillis > 0) distanceMetres * 3600 / movingMillis else null
}

/** Integrates only adjacent, fresh speed samples. A stopped car can remain in the same trip. */
class TripMeter {
    private var previous: VehicleSpeedSample? = null
    private var startAt = 0L
    private var launchAt: Long? = null
    private var armedForLaunch = false
    var summary: TripSummary? = null
        private set
    var largestIntervalMillis = 0L
        private set
    var sampleCount = 0L
        private set

    fun accept(sample: VehicleSpeedSample, wallMillis: Long) {
        if (!sample.metresPerSecond.isFinite() || sample.kmh > 360 || sample.atMillis < 0) return
        val old = previous
        if (sample.atMillis < startAt || (old != null && sample.atMillis <= old.atMillis)) return
        sampleCount++
        val interval = old?.let { sample.atMillis - it.atMillis }
        if (interval != null) largestIntervalMillis = maxOf(largestIntervalMillis, interval)
        val adjacent = old?.takeIf { interval != null && interval <= MAX_TRIP_GAP_MS }
        val acceleration = measureAcceleration(old, sample, interval)
        previous = sample

        if (summary == null && abs(sample.metresPerSecond) >= MOVING_MPS) {
            startAt = if (adjacent != null && abs(adjacent.metresPerSecond) < MOVING_MPS) {
                crossing(adjacent, sample, MOVING_MPS, magnitude = true)
            } else sample.atMillis
            summary = TripSummary(
                startedAt = wallMillis - (sample.atMillis - startAt),
                incomplete = adjacent == null
            )
        }
        val trip = summary ?: return
        val covered = if (adjacent != null) sample.atMillis - maxOf(startAt, adjacent.atMillis) else 0L
        val moving = if (adjacent != null) movingTime(adjacent, sample, covered) else 0L
        val oldSpeed = adjacent?.let { abs(it.metresPerSecond) } ?: 0.0
        val speed = abs(sample.metresPerSecond)
        val entrySpeed = if (adjacent != null && adjacent.atMillis < startAt) MOVING_MPS else oldSpeed
        val distance = if (moving > 0) {
            val entry = if (moving < covered) maxOf(entrySpeed, MOVING_MPS) else entrySpeed
            val exit = if (moving < covered) maxOf(speed, MOVING_MPS) else speed
            (entry + exit) * moving / 2000
        } else 0.0
        summary = trip.copy(
            elapsedMillis = sample.atMillis - startAt,
            movingMillis = trip.movingMillis + moving,
            distanceMetres = trip.distanceMetres + distance,
            maxKmh = maxOf(trip.maxKmh, sample.kmh),
            bestZeroToHundredMillis = if (acceleration == null) trip.bestZeroToHundredMillis
                else minOf(acceleration, trip.bestZeroToHundredMillis ?: acceleration),
            incomplete = trip.incomplete || (adjacent == null && sample.atMillis > startAt)
        )
    }

    fun interrupted() {
        previous = null
        launchAt = null
        armedForLaunch = false
        summary = summary?.copy(incomplete = true)
    }

    private fun movingTime(old: VehicleSpeedSample, sample: VehicleSpeedSample, covered: Long): Long {
        val wasMoving = abs(old.metresPerSecond) >= MOVING_MPS
        val moving = abs(sample.metresPerSecond) >= MOVING_MPS
        if (wasMoving == moving) return if (moving) covered else 0L
        val crossing = crossing(old, sample, MOVING_MPS, magnitude = true)
        val from = maxOf(old.atMillis, startAt)
        return (if (moving) sample.atMillis - maxOf(from, crossing) else crossing - from).coerceIn(0L, covered)
    }

    private fun measureAcceleration(old: VehicleSpeedSample?, sample: VehicleSpeedSample, interval: Long?): Long? {
        val speed = sample.metresPerSecond
        if (abs(speed) <= STOPPED_MPS) {
            armedForLaunch = true
            launchAt = null
            return null
        }
        if (old == null || interval == null || interval > MAX_ACCELERATION_GAP_MS || speed < 0) {
            armedForLaunch = false
            launchAt = null
            return null
        }
        if (armedForLaunch) {
            launchAt = crossing(old, sample, STOPPED_MPS, magnitude = false)
            armedForLaunch = false
        }
        val launch = launchAt ?: return null
        if (sample.atMillis - launch > 30_000) {
            launchAt = null
            return null
        }
        val target = 100 / 3.6
        if (speed < target || old.metresPerSecond >= target) return null
        launchAt = null
        return (crossing(old, sample, target, magnitude = false) - launch).takeIf { it > 0 }
    }

    private fun crossing(old: VehicleSpeedSample, next: VehicleSpeedSample, threshold: Double, magnitude: Boolean): Long {
        val from = if (magnitude) abs(old.metresPerSecond) else old.metresPerSecond
        val to = if (magnitude) abs(next.metresPerSecond) else next.metresPerSecond
        val fraction = ((threshold - from) / (to - from)).coerceIn(0.0, 1.0)
        return old.atMillis + ((next.atMillis - old.atMillis) * fraction).roundToLong()
    }

    companion object {
        const val MAX_TRIP_GAP_MS = 3_000L
        private const val MAX_ACCELERATION_GAP_MS = 250L
        private const val STOPPED_MPS = 0.2
        private const val MOVING_MPS = 0.5
    }
}
