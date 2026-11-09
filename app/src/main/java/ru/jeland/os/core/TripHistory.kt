package ru.jeland.os.core

import java.time.LocalDate
import java.time.ZoneId

internal enum class TripPeriod(val days: Long?) {
    TODAY(1), WEEK(7), MONTH(30), ALL(null);

    fun contains(startedAt: Long, today: LocalDate, zone: ZoneId): Boolean {
        val count = days ?: return true
        val first = today.minusDays(count - 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return startedAt in first until end
    }
}

internal data class TripTotals(
    val count: Int, val distanceMetres: Double, val elapsedMillis: Long, val movingMillis: Long,
    val maxKmh: Double?, val bestZeroToHundredMillis: Long?
) {
    val averageMovingKmh get() = if (movingMillis > 0) distanceMetres * 3600 / movingMillis else null
}

internal fun List<TripSummary>.totals() = TripTotals(
    size, sumOf { it.distanceMetres }, sumOf { it.elapsedMillis }, sumOf { it.movingMillis },
    maxOfOrNull { it.maxKmh }, mapNotNull { it.bestZeroToHundredMillis }.minOrNull()
)
