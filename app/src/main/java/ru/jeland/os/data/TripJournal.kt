package ru.jeland.os.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.TripSummary
import ru.jeland.os.core.VehicleSpeedSample

data class TripJournalState(
    val recording: Boolean = false,
    val speed: VehicleSpeedSample? = null,
    val current: TripSummary? = null,
    val history: List<TripSummary> = emptyList(),
    val startFailed: Boolean = false
)

/** Main-thread state, with a local checkpoint so an abrupt shutdown leaves a recoverable trip. */
class TripJournal(context: Context) {
    private val preferences = context.getSharedPreferences("jeland_trips", Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(TripJournalState(history = readHistory()))
    val state = stateFlow.asStateFlow()
    private var meter: TripMeter? = null
    private var lastCheckpoint = 0L
    private var recordingStartedAt = 0L

    init {
        val interrupted = preferences.getString("active", null)?.let { raw ->
            runCatching { JSONObject(raw).summary().copy(incomplete = true) }.getOrNull()
        }
        if (interrupted != null) archive(interrupted)
    }

    fun start() {
        if (meter != null) return
        meter = TripMeter()
        recordingStartedAt = SystemClock.elapsedRealtime()
        lastCheckpoint = 0L
        stateFlow.value = stateFlow.value.copy(recording = true, speed = null, current = null, startFailed = false)
    }

    fun failedToStart() {
        stateFlow.value = stateFlow.value.copy(startFailed = true)
    }

    fun speed(sample: VehicleSpeedSample, wallMillis: Long, live: Boolean) {
        val current = stateFlow.value
        if (current.speed?.let { sample.atMillis <= it.atMillis } == true) return
        val recorder = meter
        if (live && sample.atMillis >= recordingStartedAt) recorder?.accept(sample, wallMillis)
        val summary = recorder?.summary
        if (summary != null && (current.current == null ||
                sample.atMillis - lastCheckpoint >= 2_000 ||
                summary.bestZeroToHundredMillis != current.current.bestZeroToHundredMillis)) {
            preferences.edit().putString("active", summary.json().toString()).apply()
            lastCheckpoint = sample.atMillis
        }
        stateFlow.value = current.copy(speed = sample, current = summary)
    }

    fun signalLost() {
        if (stateFlow.value.speed == null) return
        meter?.interrupted()
        val current = meter?.summary
        if (current != null) preferences.edit().putString("active", current.json().toString()).apply()
        stateFlow.value = stateFlow.value.copy(speed = null, current = current)
    }

    fun finish(interrupted: Boolean = false, report: (String) -> Unit = {}) {
        val recorder = meter ?: return
        val stale = stateFlow.value.speed?.let {
            SystemClock.elapsedRealtime() - it.atMillis > TripMeter.MAX_TRIP_GAP_MS
        } ?: true
        meter = null
        recorder.summary?.let { trip ->
            archive(trip.copy(incomplete = trip.incomplete || interrupted || stale))
            report("TRIP_TELEMETRY samples=${recorder.sampleCount} longestIntervalMs=${recorder.largestIntervalMillis}")
        }
        stateFlow.value = stateFlow.value.copy(recording = false, current = null)
    }

    private fun archive(trip: TripSummary) {
        val history = listOf(trip) + stateFlow.value.history
        val array = JSONArray()
        history.forEach { array.put(it.json()) }
        preferences.edit().putString("history", array.toString()).remove("active").apply()
        stateFlow.value = stateFlow.value.copy(history = history)
    }

    private fun readHistory(): List<TripSummary> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]") ?: "[]")
        List(array.length()) { index -> array.getJSONObject(index).summary() }
    }.getOrDefault(emptyList())

    private fun TripSummary.json() = JSONObject()
        .put("startedAt", startedAt).put("elapsed", elapsedMillis).put("moving", movingMillis)
        .put("metres", distanceMetres).put("maxKmh", maxKmh)
        .put("zeroToHundred", bestZeroToHundredMillis ?: JSONObject.NULL).put("incomplete", incomplete)

    private fun JSONObject.summary() = TripSummary(
        getLong("startedAt"), getLong("elapsed"), getLong("moving"),
        getDouble("metres"), getDouble("maxKmh"),
        optLong("zeroToHundred", 0).takeIf { it > 0 }, optBoolean("incomplete")
    )
}
