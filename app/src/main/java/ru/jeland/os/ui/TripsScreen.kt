package ru.jeland.os.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.TripSummary
import ru.jeland.os.data.TripJournal
import ru.jeland.os.data.TripRecordingService
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.ui.theme.Jeland
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(journal: TripJournal, demo: Boolean) {
    val state by journal.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = ru.jeland.os.localization.AppLocale.current
    val colors = Jeland.colors
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val speed = state.speed?.takeIf {
        now - it.atMillis <= TripMeter.MAX_TRIP_GAP_MS
    }?.kmh
    LazyColumn(
        Modifier.fillMaxSize().testTag("trips-screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Поездки", style = Jeland.heading, color = colors.text)
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("Текущая скорость", muted = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(speed?.let { number(it, 0, locale) } ?: "—", style = Jeland.heading, color = colors.accent)
                        Label("км/ч", muted = true)
                    }
                    Label(when {
                        state.startFailed -> "Не удалось запустить запись."
                        demo -> "Данные о поездках доступны при подключении к ГУ."
                        state.recording && speed == null -> "Запись включена. Ждём показания скорости."
                        state.recording && state.current == null -> "Запись включена. Ждём начала движения."
                        state.recording -> "Идёт запись поездки"
                        speed == null -> "Нет свежих показаний скорости"
                        else -> "Штатная скорость ГУ"
                    }, small = true, muted = true)
                    if (state.recording) {
                        Control("Завершить поездку", Modifier.fillMaxWidth().testTag("trip-stop")) {
                            TripRecordingService.finish(context)
                        }
                    } else {
                        Control("Начать запись", Modifier.fillMaxWidth().testTag("trip-start"), enabled = !demo) {
                            TripRecordingService.start(context)
                        }
                    }
                }
            }
        }
        state.current?.let { trip -> item { TripCard(trip, "Текущая поездка", locale) } }
        item {
            Text("История поездок", style = Jeland.heading, color = colors.text)
            if (state.history.isEmpty()) Label("Сохранённых поездок пока нет.", small = true, muted = true)
        }
        items(state.history) { trip ->
            val title = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(trip.startedAt))
            TripCard(trip, title, locale)
        }
    }
}

@Composable
private fun TripCard(trip: TripSummary, title: String, locale: Locale) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, style = Jeland.heading, color = Jeland.colors.text)
            if (trip.incomplete) Label("Неполные данные", small = true, muted = true)
            TripMetric("Время поездки", duration(trip.elapsedMillis, locale))
            TripMetric("Время в движении", duration(trip.movingMillis, locale))
            TripMetric("Расстояние (расчёт)", number(trip.distanceMetres / 1000, 2, locale), "км")
            TripMetric("Средняя в движении", trip.averageMovingKmh?.let { number(it, 0, locale) } ?: "—", "км/ч")
            TripMetric("Максимальная скорость", number(trip.maxKmh, 0, locale), "км/ч")
            TripMetric("Лучший разгон 0–100", trip.bestZeroToHundredMillis?.let { "≈ " + number(it / 1000.0, 1, locale) } ?: "—", "с")
        }
    }
}

@Composable
private fun TripMetric(title: String, value: String, unit: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title, Modifier.weight(1f), small = true, muted = true)
        Label(value, small = true)
        if (unit != null && value != "—") Label(unit, small = true, muted = true)
    }
}

private fun number(value: Double, digits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = digits }.format(value)

private fun duration(millis: Long, locale: Locale): String {
    val seconds = millis / 1000
    return String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
