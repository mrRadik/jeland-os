package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland
import kotlin.math.round

@Composable fun DashboardDebugPanel(s:UiState,model:MainViewModel) {
    val r=model.dependencies.vehicle
    var selectedName by rememberSaveable {mutableStateOf(Wheel.FRONT_LEFT.name)}
    val wheel=Wheel.valueOf(selectedName)
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Label("Только локальные события. Двери, шины и движение автомобиля не управляются.",small=true,muted=true)
        Label("Кузов · DEMO")
        VehicleOpening.entries.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach {opening->Control(opening.title,Modifier.weight(1f).testTag("debug-door-${opening.name.lowercase()}"),opening in s.vehicle.openings){r.simulateOpening(opening)}}
        }}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Control("Закрыть всё · DEMO",Modifier.testTag("debug-close-all")){r.closeAllOpenings()}
            Control("Движение · DEMO",Modifier.testTag("debug-moving"),s.vehicle.mockMoving,accent=Jeland.colors.warm){r.simulateMoving(!s.vehicle.mockMoving)}
        }
        Label("Топливо · бак 55 л")
        FuelPopup(s.vehicle.fuel,model)
        Label("Давление в шинах · DEMO")
        Wheel.entries.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            row.forEach {item->Control(item.title,Modifier.weight(1f),wheel==item){selectedName=item.name}}
        }}
        val reading=s.vehicle.tires[wheel]?:TireReading()
        Text("${wheel.title}: ${reading.validBar?.let {String.format(java.util.Locale.US,"%.1f бар",it)}?:"нет данных"}",color=Jeland.colors.text,fontSize=Jeland.type.body)
        if(reading.validBar!=null)JelandSlider(reading.validBar!!.toFloat(),{r.simulateTire(wheel,reading.copy(bar=round(it*10)/10.0))},0f..4f,Modifier.testTag("debug-tire-slider"))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Control("Норма"){r.simulateTire(wheel,TireReading(2.4,18))}
            Control("Внимание"){r.simulateTire(wheel,TireReading(1.9,18))}
            Control("Критично"){r.simulateTire(wheel,TireReading(1.5,18))}
            Control("Нет данных",Modifier.testTag("debug-tire-unknown")){r.simulateTire(wheel,TireReading())}
        }
        Label("Тестовые пороги — НЕ заводские нормы",small=true,muted=true)
        val t=s.vehicle.tireThresholds
        ThresholdSlider("Критично ниже",t.criticalLow,0.0..(t.warningLow-.1)){r.setTireThresholds(t.copy(criticalLow=it))}
        ThresholdSlider("Предупреждение ниже",t.warningLow,(t.criticalLow+.1)..(t.warningHigh-.1)){r.setTireThresholds(t.copy(warningLow=it))}
        ThresholdSlider("Предупреждение выше",t.warningHigh,(t.warningLow+.1)..(t.criticalHigh-.1)){r.setTireThresholds(t.copy(warningHigh=it))}
        ThresholdSlider("Критично выше",t.criticalHigh,(t.warningHigh+.1)..5.0){r.setTireThresholds(t.copy(criticalHigh=it))}
        Control("Сбросить тестовые пороги"){r.setTireThresholds(TireThresholds())}
    }
}
@Composable private fun ThresholdSlider(title:String,value:Double,range:ClosedFloatingPointRange<Double>,change:(Double)->Unit) {
    Label("$title: ${String.format(java.util.Locale.US,"%.1f",value)} бар",small=true)
    JelandSlider(value.toFloat(),{change((round(it*10)/10.0).coerceIn(range))},range.start.toFloat()..range.endInclusive.toFloat())
}
