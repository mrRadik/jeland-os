package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.R
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.JIcons
import ru.jeland.os.ui.theme.Jeland

@Composable fun PopupContent(p:Popup,s:UiState,model:MainViewModel,show:(Popup)->Unit) {
    when(p) {
        Popup.DRIVER->SeatControlPopup(SeatSide.DRIVER,s,model)
        Popup.PASSENGER->SeatControlPopup(SeatSide.PASSENGER,s,model)
        Popup.CLIMATE->ClimatePopup(s.climate,model)
        Popup.APPS->AppsPicker(model,s.apps)
        Popup.DEBUG->if(s.car.demo)DashboardDebugPanel(s,model)else VehicleConnectionPanel(s,model)
        Popup.FUEL->FuelPopup(s.vehicle.fuel,model)
        Popup.VEHICLE->VehicleDetailsPopup(s,model,show)
        Popup.TIRE_FRONT_LEFT,Popup.TIRE_FRONT_RIGHT,Popup.TIRE_REAR_LEFT,Popup.TIRE_REAR_RIGHT->TireDetailsPopup(p.wheel!!,s.vehicle)
        Popup.SHADE->{
            SunshadeRoofPreview(s.vehicle.shadeUiPosition)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
                Shade.entries.forEach {v->Control(v.title,Modifier.weight(1f).testTag("shade-${v.name.lowercase()}"),s.vehicle.shadeUiPosition==v){model.dependencies.vehicle.setShade(v)}}
            }
            if(!s.car.demo){Spacer(Modifier.height(18.dp));Label("Не перекрывайте проём шторки.",small=true,muted=true)}
        }
        Popup.CAMERA->CameraPanel()
        Popup.WEATHER->{
            SectionTitle("${s.weather.temperatureLabel} · ${s.weather.condition}","${s.weather.city} · данные не из сети")
            Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
                listOf("Челябинск","Екатеринбург","Москва").forEach {city->Control(city,selected=s.weather.city==city){model.dependencies.weather.useCity(city)}}
            }
            Spacer(Modifier.height(18.dp));Label("Демо погоды. Провайдер, координаты и прогноз появятся отдельным этапом.",small=true,muted=true)
        }
    }
}
@Composable fun SeatControlPopup(side:SeatSide,s:UiState,model:MainViewModel) {
    var tab by rememberSaveable(side) {mutableStateOf(if(s.seats.channel(side).ventilation>0)SeatMode.VENTILATE.name else SeatMode.HEAT.name)}
    val mode=SeatMode.valueOf(tab);val seat=s.seats.channel(side)
    val level=if(mode==SeatMode.HEAT)seat.heat else seat.ventilation
    val cmd=if(mode==SeatMode.HEAT){if(side==SeatSide.DRIVER)95 else 96}else if(side==SeatSide.DRIVER)93 else 94
    val c=Jeland.colors;val glow=if(mode==SeatMode.HEAT)c.warm else c.accent
    Row(Modifier.fillMaxWidth().heightIn(min=300.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.page)) {
        Box(Modifier.width(225.dp).height(300.dp),contentAlignment=Alignment.Center) {
            if(level>0) {
                Box(Modifier.size(210.dp).background(Brush.radialGradient(listOf(glow.copy(alpha=.15f*level),glow.copy(alpha=0f)))))
            }
            Image(painterResource(R.drawable.seat_jeland),"Иллюстрация сиденья",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            // Native heat/air indication is separate from the neutral transparent artwork.
            if(level>0) Canvas(Modifier.fillMaxSize()) {
                for(i in 0 until level) {
                    val x=size.width*(.53f+i*.07f)
                    val wave=androidx.compose.ui.graphics.Path().apply {
                        moveTo(x,size.height*.35f)
                        cubicTo(x-8.dp.toPx(),size.height*.42f,x+8.dp.toPx(),size.height*.49f,x,size.height*.56f)
                    }
                    drawPath(wave,glow.copy(alpha=.14f),style=Stroke(12.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
                    drawPath(wave,glow.copy(alpha=.75f),style=Stroke(2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(Jeland.space.card)) {
            Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.controlGap)) {
                Control("Подогрев",Modifier.weight(1f).testTag("seat-heat"),mode==SeatMode.HEAT,accent=c.warm){tab=SeatMode.HEAT.name}
                Control("Вентиляция",Modifier.weight(1f).testTag("seat-vent"),mode==SeatMode.VENTILATE){tab=SeatMode.VENTILATE.name}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.controlGap)) {
                (0..3).forEach {v->Control(v.toString(),Modifier.weight(1f).testTag("seat-level-$v"),s.seats.known(cmd) && level==v,accent=glow){model.dependencies.seats.setLevel(side,mode,v)}}
            }
            Label("${if(mode==SeatMode.HEAT)"Подогрев"else"Вентиляция"}: ${if(s.seats.known(cmd))"$level / 3"else"нет данных"} · ${if(s.car.demo)"DEMO"else"ГУ"}",modifier=Modifier.testTag("seat-value"),small=true,muted=true)
            if(side==SeatSide.PASSENGER) {
                RearSeatsControls(s.seats,model.dependencies.seats)
            }
            Label(if(s.car.demo)"Только локальная модель. В демо нагрев и вентиляция взаимно исключаются."else"Ступени 0…3 → OEM 1…4. Показания обновляются только по GET/событиям, без имитации успеха. Задний ряд — только подогрев.",small=true,muted=true)
        }
    }
}
@Composable fun RearSeatsControls(state:SeatState,repository:SeatRepository) {
    val c=Jeland.colors
    Column(Modifier.fillMaxWidth().testTag("rear-seats-controls"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Label("Задние сиденья · подогрев",small=true,muted=true)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            RearSeatSide.entries.forEach {side->
                val tag=side.name.lowercase();val level=state.rearLevel(side)
                Panel(Modifier.weight(1f).testTag("rear-seat-$tag")) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(54.dp,76.dp),contentAlignment=Alignment.Center) {
                                if(level!=null&&level>0)Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(c.warm.copy(alpha=.09f*level),c.warm.copy(alpha=0f)))))
                                Image(painterResource(R.drawable.seat_jeland),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(side.title,color=c.text,fontSize=14.sp,maxLines=2)
                                Text(when(level){null->"Нет данных";0->"Выключен";else->"Подогрев $level / 3"},color=if(level!=null&&level>0)c.warm else c.muted,fontSize=12.sp,modifier=Modifier.testTag("rear-$tag-value"))
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            (0..3).forEach {value->
                                ClimateBarButton(text="$value",description="${side.title}: ${if(value==0)"выключить подогрев"else"подогрев $value"}",active=level==value,enabled=level!=null,
                                    modifier=Modifier.weight(1f).testTag("rear-$tag-level-$value"),accent=c.warm){repository.setRearLevel(side,value)}
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable fun ClimatePopup(s:ClimateState,model:MainViewModel) {
    ClimateDashboard(s,model.dependencies.climate)
}
@Composable private fun CameraPanel() {
    var view by rememberSaveable {mutableStateOf("Сверху")}
    val c=Jeland.colors
    Box(Modifier.fillMaxWidth().height(240.dp).background(c.map),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(25.dp)) {
            val w=size.width;val h=size.height
            drawRoundRect(c.deep.copy(alpha=.3f),Offset(w*.42f,h*.1f),Size(w*.16f,h*.8f),androidx.compose.ui.geometry.CornerRadius(30f,30f))
            drawRoundRect(c.artMuted,Offset(w*.42f,h*.1f),Size(w*.16f,h*.8f),androidx.compose.ui.geometry.CornerRadius(30f,30f),style=Stroke(3f))
            if(view=="Сверху") {
                drawLine(c.success,Offset(w*.28f,h*.9f),Offset(w*.3f,h*.1f),3f)
                drawLine(c.success,Offset(w*.72f,h*.9f),Offset(w*.7f,h*.1f),3f)
            }
        }
        Text("НЕТ ВИДЕОСИГНАЛА · ДЕМО",color=c.artText,fontSize=Jeland.type.secondary,modifier=Modifier.align(Alignment.BottomCenter).padding(Jeland.space.inset))
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {listOf("Сверху","Спереди","Сзади").forEach {v->Control(v,Modifier.weight(1f),view==v){view=v}}}
    Spacer(Modifier.height(14.dp));Label("Выбрано: $view. Это иллюстрация, не камера и не парковочный ассистент.",small=true,muted=true)
}
