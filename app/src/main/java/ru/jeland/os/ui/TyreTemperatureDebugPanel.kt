package ru.jeland.os.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.jeland.os.core.*

/** Cached transport state only. Opening this panel never starts or retries a QNX request. */
@Composable fun TyreTemperatureDebugPanel(wheel:Wheel,state:TyreTemperatureDiagnostics,onSave:()->Unit,onClear:(()->Unit)?=null,busy:Boolean=false) {
    var now by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
    LaunchedEffect(Unit){while(true){now=SystemClock.elapsedRealtime();delay(1000)}}
    fun age(at:Long?)=at?.let {"${((now-it).coerceAtLeast(0)/1000)} с назад"}?:"не было"
    Column(Modifier.fillMaxWidth().testTag("tyre-temperature-debug"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Label("Диагностика температуры · только чтение")
        SelectionContainer {
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Label("Этап: ${state.phase.title} [${state.phase}]",Modifier.testTag("tyre-debug-stage"),small=true)
                Label("Чтение: ${if(state.active)"активно"else"остановлено"} · TCP: ${if(state.tcpConnected)"подключён"else"нет соединения"} · попыток: ${state.attempts}",small=true,muted=true)
                Label("Интерфейс: ${state.interfaceName.ifBlank {"не найден"}} · 192.168.8.2 → 192.168.8.1:8000",small=true,muted=true)
                Label("Файл: ${QnxTyreTemperatureDecoder.PPS_PATH}",small=true,muted=true)
                Label("RX ${state.trace.receivedBytes} байт · TX ${state.trace.sentBytes} байт (текущее/последнее соединение)",small=true)
                Label("Последний запрос: ${state.trace.command.ifBlank {"ещё не отправлялся"}}",small=true)
                if(state.lastError.isNotBlank()) {
                    Label("Ошибка на этапе ${state.failedPhase?.title?:"неизвестно"} · ${age(state.errorAtMillis)}",small=true)
                    Label(state.lastError,Modifier.testTag("tyre-debug-error"),small=true)
                }
                state.retryAtMillis?.takeIf {state.active}?.let {
                    Label("Повтор через ${((it-now).coerceAtLeast(0)+999)/1000} с",small=true,muted=true)
                }
                Label("Ответ, хвост: ${state.trace.textTail.ifBlank {"байты не получены на этом этапе"}}",small=true)
                Label("HEX: ${state.trace.hexTail.ifBlank {"—"}}",small=true,muted=true)
                Label("Разобрано пакетов: ${state.packetCount} · последний: ${age(state.lastPacketAtMillis)}",small=true)
                Label("Последний a005: ${state.payload.ifBlank {"не получен"}.map {if(it.isISOControl()&&it!='\n'&&it!='\t')'.'else it}.joinToString("")}",small=true,muted=true)
                Wheel.entries.forEach {item->
                    val sample=state.samples[item]
                    Label("${if(item==wheel)"• "else""}${item.title}: ${sample?.let {"raw=${it.raw}, status=${it.status}, ${it.celsius?.let {t->"$t °C"}?:"значение невалидно"} · ${age(it.atMillis)}${if(now-it.atMillis>30_000)" · устарело"else""}"}?:"пакет не получен"}",small=true)
                }
            }
        }
        Label("Это диагностика последнего обмена, не подтверждение исправности датчика. DEMO-значения не подставляются.",small=true,muted=true)
        Control("Сохранить журнал OS",Modifier.fillMaxWidth().testTag("tyre-debug-save"),enabled=!busy,onClick=onSave)
        onClear?.let {LogCleanupButton(busy,it)}
    }
}
