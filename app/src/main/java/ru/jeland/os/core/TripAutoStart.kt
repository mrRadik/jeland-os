package ru.jeland.os.core

/** A manual finish stays final until the next confirmed ignition cycle. */
internal class TripAutoStart {
    private var engineAt: Long? = null
    private var offSince: Long? = null
    private var lastEngineSample = -1L
    private var used = false
    private var sawRunning = false

    fun engine(engine: Int?, ignition: Int?, at: Long, now: Long): Boolean {
        if (at <= lastEngineSample || now - at !in 0..15_000) return false
        lastEngineSample = at
        engineAt = at.takeIf { engine == 1 }
        if (engine == 1) sawRunning = true
        if (engine != 0 || ignition != 0) {
            offSince = null
            return false
        }
        val since = offSince ?: at.also { offSince = it }
        if (at - since < 1_500) return false
        val ended = sawRunning
        reset()
        lastEngineSample = at
        return ended
    }

    fun claim(speed: VehicleSpeedSample, now: Long): Boolean {
        if (used || speed.kmh <= 0 || now - speed.atMillis !in 0..TripMeter.MAX_TRIP_GAP_MS ||
            engineAt?.let { now - it in 0..15_000 } != true) return false
        used = true
        return true
    }

    fun consume() { used = true }

    fun interrupted() {
        engineAt = null
        offSince = null
    }

    fun reset() {
        interrupted()
        lastEngineSample = -1
        used = false
        sawRunning = false
    }
}
