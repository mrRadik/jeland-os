package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.BatteryReading
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import java.util.Locale

private fun number(value:Double?,unit:String,digits:Int=1)=value?.let {String.format(Locale.forLanguageTag("ru"),"%.${digits}f %s",it,unit)}?:"Нет данных"

@Composable fun BatteryIndicator(reading:BatteryReading,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Column(modifier.width(122.dp).clip(Jeland.card).background(c.panel.copy(alpha=.94f)).border(1.dp,c.outline,Jeland.card)
        .clickable(role=Role.Button,onClick=onClick).padding(horizontal=10.dp,vertical=8.dp).testTag("battery-widget"),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(JIcons.Battery,"Аккумулятор",Modifier.size(22.dp),tint=c.accent)
            Text(reading.voltage?.let {number(it,"В")}?:"— В",color=c.text,fontSize=Jeland.type.control,maxLines=1,modifier=Modifier.testTag("battery-voltage"))
        }
        Text("Аккумулятор",color=c.muted,fontSize=Jeland.type.micro,lineHeight=Jeland.type.caption,maxLines=1)
    }
}

@Composable fun BatteryDetails(reading:BatteryReading,live:Boolean) {
    val c=Jeland.colors
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(JIcons.Battery,null,Modifier.size(64.dp).align(Alignment.CenterHorizontally),tint=c.accent)
        Text(number(reading.voltage,"В"),color=c.text,fontSize=Jeland.type.clockSmall,modifier=Modifier.align(Alignment.CenterHorizontally))
        val rows=listOf(
            "Напряжение бортсети" to number(reading.voltage,"В"),
            "Напряжение IBS" to number(reading.ibsVoltage,"В",2),
            "Заряд АКБ" to (reading.chargePercent?.let {"$it %"}?:"Нет данных"),
            "Оценка состояния · SOH" to (reading.healthPercent?.let {"$it %"}?:"Нет данных"),
            "Температура АКБ" to (reading.temperatureCelsius?.let {"$it °C"}?:"Нет данных"),
            "Ток АКБ" to number(reading.currentAmps,"А",2),
            "Пусковое напряжение · SOF" to number(reading.startingVoltage,"В",2),
            "Ток покоя" to number(reading.quiescentAmps,"А",3),
        )
        rows.forEach {(title,value)->
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Text(title,color=c.muted,fontSize=Jeland.type.secondary,modifier=Modifier.weight(1f))
                Text(value,color=c.text,fontSize=Jeland.type.secondary)
            }
        }
        Label(if(live)"Данные ГУ · пересчёт по штатному EngMode. Сопоставление каналов требует проверки; SOH — оценка датчика, не диагностика аккумулятора."
            else "DEMO · пример показаний аккумулятора",small=true,muted=true)
    }
}
