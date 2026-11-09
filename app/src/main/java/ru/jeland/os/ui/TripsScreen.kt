package ru.jeland.os.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.jeland.os.core.TripMeter
import ru.jeland.os.core.TripPeriod
import ru.jeland.os.core.TripSummary
import ru.jeland.os.core.totals
import ru.jeland.os.data.TripJournal
import ru.jeland.os.data.TripJournalState
import ru.jeland.os.data.TripRecordingService
import ru.jeland.os.localization.AppLocale
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import java.text.DateFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(journal: TripJournal, demo: Boolean, onAutoStart: (Boolean) -> Unit) {
    val state by journal.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = AppLocale.current
    var period by rememberSaveable { mutableStateOf(TripPeriod.ALL) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            today = LocalDate.now()
            delay(1_000)
        }
    }
    val zone = ZoneId.systemDefault()
    val visible = remember(state.history, period, today, zone) {
        state.history.filter { period.contains(it.startedAt, today, zone) }
    }

    LazyColumn(
        Modifier.fillMaxSize().testTag("trips-screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SectionTitle("Поездки") }
        item {
            CurrentTripCard(state, demo, now, locale) {
                if (state.recording) TripRecordingService.finish(context) else TripRecordingService.start(context)
            }
        }
        item { TripAutoStartCard(state.autoStart, onAutoStart) }
        item { TripHistorySummary(visible, period, locale) { period = it } }
        if (visible.isEmpty()) item {
            Label(
                if (state.history.isEmpty()) "Сохранённых поездок пока нет." else "За выбранный период поездок нет.",
                modifier = Modifier.padding(horizontal = 6.dp), muted = true
            )
        }
        items(visible, key = { it.id }) { trip ->
            SavedTripCard(trip, locale) { deleteId = trip.id }
        }
    }

    val pending = state.history.firstOrNull { it.id == deleteId }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Удалить поездку?") },
            text = { Text(tripDate(pending, locale)) },
            confirmButton = {
                TextButton(onClick = { journal.delete(pending.id); deleteId = null }) {
                    Text("Удалить", color = Jeland.colors.critical)
                }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun CurrentTripCard(
    state: TripJournalState, demo: Boolean, now: Long, locale: Locale, onAction: () -> Unit
) {
    val speed = state.speed?.takeIf { now - it.atMillis in -1_000..TripMeter.MAX_TRIP_GAP_MS }?.kmh
    val trip = state.current
    val detail = when {
        state.startFailed -> "Не удалось запустить запись."
        demo -> "Данные о поездках доступны при подключении к ГУ."
        state.recording && speed == null -> "Запись включена. Ждём показания скорости."
        state.recording && trip == null -> "Запись включена. Ждём начала движения."
        state.recording -> "Идёт запись поездки"
        state.autoStart -> "Автозапись готова"
        else -> "Запись выключена"
    }
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glyph(JIcons.Trips, modifier = Modifier.size(32.dp), active = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("Текущая поездка")
                    Label(detail, small = true, muted = true)
                }
                if (state.recording) Box(
                    Modifier.size(9.dp).background(Jeland.colors.accent, CircleShape)
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(speed?.let { number(it, 0, locale) } ?: "—", color = Jeland.colors.accent, fontSize = 52.sp)
                Label("км/ч", Modifier.padding(bottom = 9.dp), muted = true)
            }
            if (trip != null) {
                HorizontalDivider(color = Jeland.colors.outline)
                TripDetails(trip, locale)
            }
            Control(
                if (state.recording) "Завершить поездку" else "Начать запись",
                Modifier.fillMaxWidth().testTag(if (state.recording) "trip-stop" else "trip-start"),
                selected = !state.recording && !state.autoStart,
                enabled = state.recording || !demo,
                onClick = onAction
            )
        }
    }
}

@Composable
private fun TripAutoStartCard(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Jeland.space.large)) {
            SettingsToggle(
                title = "Автозапись поездок",
                detail = "После запуска двигателя и начала движения.",
                checked = checked,
                modifier = Modifier.testTag("trip-auto-start")
            ) { onCheckedChange(!checked) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TripHistorySummary(
    visible: List<TripSummary>, selected: TripPeriod, locale: Locale, onPeriod: (TripPeriod) -> Unit
) {
    val totals = remember(visible) { visible.totals() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("История поездок")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TripPeriod.entries.forEach { period ->
                val label = when (period) {
                    TripPeriod.TODAY -> "Сегодня"
                    TripPeriod.WEEK -> "7 дней"
                    TripPeriod.MONTH -> "30 дней"
                    TripPeriod.ALL -> "Всё время"
                }
                Control(label, selected = selected == period) { onPeriod(period) }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Label("Сводка за период")
                MetricRow(
                    Metric("Количество поездок", number(totals.count.toDouble(), 0, locale)),
                    Metric("Расстояние", distance(totals.distanceMetres, locale))
                )
                MetricRow(
                    Metric("Время поездок", duration(totals.elapsedMillis, locale)),
                    Metric("Время в движении", duration(totals.movingMillis, locale))
                )
                MetricRow(
                    Metric("Средняя в движении", speed(totals.averageMovingKmh, locale)),
                    Metric("Максимальная скорость", speed(totals.maxKmh, locale))
                )
                totals.bestZeroToHundredMillis?.let {
                    MetricCell(Metric("Лучший разгон 0–100", acceleration(it, locale)), Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SavedTripCard(trip: TripSummary, locale: Locale, onDelete: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(distance(trip.distanceMetres, locale), style = Jeland.heading, color = Jeland.colors.accent)
                    Label(tripDate(trip, locale), small = true, muted = true)
                    if (trip.incomplete) Label("Неполные данные", small = true, muted = true)
                }
                RoundControl(JIcons.Delete, "Удалить поездку", Modifier.testTag("trip-delete"), onClick = onDelete)
            }
            HorizontalDivider(color = Jeland.colors.outline)
            TripDetails(trip, locale, showDistance = false)
        }
    }
}

@Composable
private fun TripDetails(trip: TripSummary, locale: Locale, showDistance: Boolean = true) {
    MetricRow(
        Metric("Время поездки", duration(trip.elapsedMillis, locale)),
        Metric("Время в движении", duration(trip.movingMillis, locale))
    )
    MetricRow(
        Metric("Средняя в движении", speed(trip.averageMovingKmh, locale)),
        Metric("Максимальная скорость", speed(trip.maxKmh, locale))
    )
    val launch = Metric("Лучший разгон 0–100", acceleration(trip.bestZeroToHundredMillis, locale))
    if (showDistance) MetricRow(Metric("Расстояние", distance(trip.distanceMetres, locale)), launch)
    else MetricCell(launch, Modifier.fillMaxWidth())
}

private data class Metric(val label: String, val value: String)

@Composable
private fun MetricRow(first: Metric, second: Metric) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCell(first, Modifier.weight(1f))
        MetricCell(second, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(metric: Metric, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(metric.value, style = Jeland.body, color = Jeland.colors.text)
        Label(metric.label, small = true, muted = true)
    }
}

private fun tripDate(trip: TripSummary, locale: Locale): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(trip.startedAt))

private fun distance(metres: Double, locale: Locale) = "${number(metres / 1000, 2, locale)} км"
private fun speed(kmh: Double?, locale: Locale) = kmh?.let { "${number(it, 0, locale)} км/ч" } ?: "—"
private fun acceleration(millis: Long?, locale: Locale) = millis?.let { "≈ ${number(it / 1000.0, 1, locale)} с" } ?: "—"

private fun number(value: Double, digits: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = digits }.format(value)

private fun duration(millis: Long, locale: Locale): String {
    val seconds = millis / 1000
    return String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
