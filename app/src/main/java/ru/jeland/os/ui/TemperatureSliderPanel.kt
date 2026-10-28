package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*
import kotlin.math.round

/** Local drag preview; exactly one absolute request on release/accessibility action, not per frame. */
@Composable fun TemperatureSliderPanel(driver:Boolean,s:UiState,r:ClimateRepository,onClose:()->Unit) {
    val c=Jeland.colors;val climate=s.climate;val live=!s.car.demo
    val current=(if(driver)climate.driver else climate.passenger).toFloat()
    val range=if(live)15f..31f else 16f..32f
    val known=climate.known(if(driver)10 else 13)
    val enabled=climate.controlsEnabled&&known&&(s.car.demo||(s.car.connected&&s.car.writesEnabled))
    var dragging by remember(driver){mutableStateOf(false)}
    var preview by remember(driver){mutableFloatStateOf(current.coerceIn(range))}
    LaunchedEffect(current,driver,enabled,dragging){if(!dragging||!enabled){preview=current.coerceIn(range);dragging=false}}
    val allowed by rememberUpdatedState(enabled)
    val value=if(dragging)preview else current
    val label=when { !known->"—";live&&value<=15f->"LO";live&&value>=31f->"HI"
        value%1f==0f->"${value.toInt()}°";else->"${value.toString().replace('.',',')}°" }
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=14.dp).testTag("temperature-panel"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(driver)"Температура водителя"else"Температура пассажира",color=c.muted,fontSize=16.sp,modifier=Modifier.weight(1f).testTag("temperature-side"))
            RoundControl(JIcons.Close,"Закрыть температуру",Modifier.size(34.dp).testTag("close-popup"),onClick=onClose)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Icon(JIcons.Thermometer,null,Modifier.size(32.dp),tint=if(enabled)c.accent else c.unknown)
            JelandSlider(preview,{preview=if(live)round(it)else round(it*2)/2;dragging=true},range,
                Modifier.weight(1f).testTag("temperature-slider").semantics {contentDescription=if(driver)"Температура водителя"else"Температура пассажира"},
                enabled=enabled,onValueChangeFinished={if(allowed&&dragging){r.setTemperature(driver,preview.toDouble());dragging=false}},
                gradient=Brush.horizontalGradient(listOf(Color(0xFF289CE4),Color(0xFF8CB4C4),Color(0xFFF4A35F))),
                onValueChangeCanceled={dragging=false;preview=current.coerceIn(range)})
            Text(label,color=if(enabled)c.text else c.unknown,fontSize=26.sp,modifier=Modifier.width(65.dp).testTag("temperature-preview"))
        }
        if(!enabled)Text(when {!climate.controlsEnabled->"Климат выключен или нет данных о питании";!known->"Нет свежих данных температуры";s.car.busy->"Ожидаем ответ автомобиля";else->"Разрешите управление на стоянке"},color=c.muted,fontSize=13.sp)
    }
}
