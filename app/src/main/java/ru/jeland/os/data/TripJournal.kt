package ru.jeland.os.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.core.TripAutoStart
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.TripSummary
import ru.jeland.os.core.VehicleSpeedSample

data class TripJournalState(
    val recording: Boolean = false,
    val speed: VehicleSpeedSample? = null,
    val current: TripSummary? = null,
    val history: List<TripSummary> = emptyList(),
    val startFailed: Boolean = false,
    val autoStart: Boolean = false
)

/** Main-thread state, with a local checkpoint so an abrupt shutdown leaves a recoverable trip. */
class TripJournal(context: Context) {
    private val preferences = context.getSharedPreferences("jeland_trips", Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(TripJournalState(
        history = readHistory(), autoStart = preferences.getBoolean("autoStart", false)
    ))
    private val auto = TripAutoStart()
    private data class Reading(val sample: VehicleSpeedSample, val wallMillis: Long)
    private var lastLive: Reading? = null
    private var pendingAuto: List<Reading> = emptyList()
    val state = stateFlow.asStateFlow()
    private var meter: TripMeter? = null
    private var lastCheckpoint = 0L
    private var recordingStartedAt = 0L

    init {
        val interrupted = preferences.getString("active", null)?.let { raw ->
            runCatching { JSONObject(raw).summary("active").copy(incomplete = true) }.getOrNull()
        }
        if (interrupted != null) archive(interrupted)
    }

    fun setAutoStart(value: Boolean) {
        preferences.edit().putBoolean("autoStart", value).apply()
        if (!value) pendingAuto = emptyList()
        stateFlow.value = stateFlow.value.copy(autoStart = value, startFailed = false)
    }

    fun engine(engine: Int?, ignition: Int?, at: Long): Boolean {
        val ended = auto.engine(engine, ignition, at, SystemClock.elapsedRealtime())
        if (engine == 1 && meter != null) auto.consume()
        if (ended) pendingAuto = emptyList()
        return ended
    }

    fun engineUnavailable() = auto.interrupted()

    fun vehicleSlept() {
        auto.reset()
        pendingAuto = emptyList()
        lastLive = null
    }

    fun start(automatic: Boolean): Boolean {
        if (meter != null) return true
        val seed = pendingAuto
        if (automatic && seed.isEmpty()) return false
        pendingAuto = emptyList()
        auto.consume()
        meter = TripMeter(startAtAnyMovement = automatic)
        recordingStartedAt = if (automatic) seed.first().sample.atMillis else SystemClock.elapsedRealtime()
        lastCheckpoint = 0L
        stateFlow.value = stateFlow.value.copy(recording = true, speed = null, current = null, startFailed = false)
        if (automatic) seed.forEach { speed(it.sample, it.wallMillis, live = true) }
        return true
    }

    fun failedToStart() {
        pendingAuto = emptyList()
        stateFlow.value = stateFlow.value.copy(startFailed = true)
    }

    fun speed(sample: VehicleSpeedSample, wallMillis: Long, live: Boolean): Boolean {
        val current = stateFlow.value
        if (current.speed?.let { sample.atMillis <= it.atMillis } == true) return false
        val reading = Reading(sample, wallMillis)
        val recorder = meter
        val trigger = live && recorder == null && current.autoStart && auto.claim(sample, SystemClock.elapsedRealtime())
        if (trigger) {
            val stopped = lastLive?.takeIf { it.sample.kmh == 0.0 &&
                sample.atMillis - it.sample.atMillis in 1..TripMeter.MAX_TRIP_GAP_MS }
            pendingAuto = listOfNotNull(stopped, reading)
        }
        if (live) lastLive = reading
        if (live && sample.atMillis >= recordingStartedAt) recorder?.accept(sample, wallMillis)
        val summary = recorder?.summary
        if (summary != null && (current.current == null ||
                sample.atMillis - lastCheckpoint >= 2_000 ||
                summary.bestZeroToHundredMillis != current.current.bestZeroToHundredMillis)) {
            preferences.edit().putString("active", summary.json().toString()).apply()
            lastCheckpoint = sample.atMillis
        }
        stateFlow.value = current.copy(speed = sample, current = summary, startFailed = if (trigger) false else current.startFailed)
        return trigger
    }

    fun signalLost() {
        lastLive = null
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

    fun delete(id: String) {
        val previous = stateFlow.value.history
        val history = previous.filterNot { it.id == id }
        if (history.size != previous.size) saveHistory(history, clearCheckpoint = false)
    }

    private fun archive(trip: TripSummary) =
        saveHistory(listOf(trip) + stateFlow.value.history, clearCheckpoint = true)

    private fun saveHistory(history: List<TripSummary>, clearCheckpoint: Boolean) {
        val array = JSONArray()
        history.forEach { array.put(it.json()) }
        preferences.edit().putString("history", array.toString()).apply {
            if (clearCheckpoint) remove("active")
        }.apply()
        stateFlow.value = stateFlow.value.copy(history = history)
    }

    private fun readHistory(): List<TripSummary> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]") ?: "[]")
        List(array.length()) { index -> array.getJSONObject(index).summary(index.toString()) }
    }.getOrDefault(emptyList())

    private fun TripSummary.json() = JSONObject().put("id", id)
        .put("startedAt", startedAt).put("elapsed", elapsedMillis).put("moving", movingMillis)
        .put("metres", distanceMetres).put("maxKmh", maxKmh)
        .put("zeroToHundred", bestZeroToHundredMillis ?: JSONObject.NULL).put("incomplete", incomplete)

    private fun JSONObject.summary(legacyKey: String) = TripSummary(
        getLong("startedAt"), getLong("elapsed"), getLong("moving"),
        getDouble("metres"), getDouble("maxKmh"),
        optLong("zeroToHundred", 0).takeIf { it > 0 }, optBoolean("incomplete"),
        optString("id").ifBlank { "legacy-$legacyKey-${getLong("startedAt")}" }
    )
}
