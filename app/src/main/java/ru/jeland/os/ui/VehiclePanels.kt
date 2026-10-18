package ru.jeland.os.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*
import ru.jeland.os.R
import ru.jeland.os.ui.theme.*
import java.util.Locale

/** One compact event surface; never renders a second car on top of the hero. */
@Composable fun VehicleEventNotifications(state:VehicleNoticeState,live:Boolean=false) {
    val c=Jeland.colors
    AnimatedContent(state,modifier=Modifier.widthIn(max=400.dp),label="vehicle-event",
        contentKey={when {it.persistent->"warning";it.visible->"parked";else->"empty"}},
        transitionSpec={
            (fadeIn(tween(Jeland.animation.panel))+slideInVertically { -it/4 }) togetherWith
                (fadeOut(tween(Jeland.animation.quick))+slideOutVertically { -it/4 })
        }) {notice->
        if(notice.visible) {
            val openings=if(notice.persistent)VehicleOpening.entries.filter {it in notice.warningOpenings}else notice.parked.map {it.opening}
            val icon=if(notice.persistent||openings.size>1)JIcons.Warning else when(openings.single()) {
                VehicleOpening.HOOD->JIcons.Hood;VehicleOpening.TRUNK->JIcons.Trunk;else->JIcons.Door
            }
            val accent=if(notice.persistent)c.critical else c.accent
            Row(Modifier.fillMaxWidth().clip(Jeland.control).background(c.panel.copy(alpha=.97f))
                .border(1.dp,if(notice.persistent)c.critical else c.outline,Jeland.control)
                .testTag(if(notice.persistent)"vehicle-motion-warning"else"vehicle-event-notice")
                .semantics {liveRegion=LiveRegionMode.Polite}.padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(icon,null,Modifier.size(26.dp),tint=accent)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(when {
                        notice.persistent->if(live){if(openings.size==1)openings.single().openEventTitle else"Проверьте двери и багажник"}else"Движение с открытым кузовом · DEMO"
                        openings.size==1->"${openings.single().openEventTitle} · ${if(live)"ГУ"else"DEMO"}"
                        else->"Открыто зон: ${openings.size} · ${if(live)"ГУ"else"DEMO"}"
                    },color=if(notice.persistent)accent else c.text,fontSize=Jeland.type.secondary,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(openings.size>1 || (notice.persistent && !live))Text(openings.joinToString {it.displayName},color=c.muted,
                        fontSize=Jeland.type.caption,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable fun TireDetailsPopup(wheel:Wheel,vehicle:VehicleState) {
    val reading=vehicle.tires[wheel]?:TireReading()
    val severity=vehicle.tireThresholds.severity(reading)
    val c=Jeland.colors
    val status=when(severity) {
        TireSeverity.NORMAL->if(vehicle.live)"Расчётное давление · предварительная калибровка"else"В пределах тестовых порогов"
        TireSeverity.WARNING->"Внимание: давление за тестовым порогом"
        TireSeverity.CRITICAL->"Критическое значение по тестовым порогам"
        TireSeverity.UNKNOWN->"Данные о давлении отсутствуют"
    }
    val color=when(severity) {TireSeverity.NORMAL->c.muted;TireSeverity.WARNING->c.warm;TireSeverity.CRITICAL->c.critical;TireSeverity.UNKNOWN->c.unknown}
    Column(Modifier.fillMaxWidth().testTag("tire-details"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Image(painterResource(R.drawable.wheel_realistic),null,Modifier.size(84.dp).testTag("tire-detail-icon"),contentScale=ContentScale.Fit)
            Column(Modifier.weight(1f)) {
                Label("Давление",small=true,muted=true)
                Text(reading.validBar?.let {(if(vehicle.live)"≈ "else"")+String.format(Locale.US,"%.1f бар",it)}?:"Нет данных",color=c.text,fontSize=Jeland.type.temperature,modifier=Modifier.testTag("tire-detail-pressure"))
            }
            Column(Modifier.weight(1f)) {
                Label("Температура",small=true,muted=true)
                Text(reading.temperature?.let {"$it °C"}?:"Нет данных",color=c.text,fontSize=Jeland.type.temperature,modifier=Modifier.testTag("tire-detail-temperature"))
            }
        }
        Text(status,color=color,fontSize=Jeland.type.secondary)
        Label(if(vehicle.live)"Давление расчётное, сопоставление колёс предварительное. Канал температуры шин пока не подключён. Штатные предупреждения не заменяются."else"DEMO · значения не из датчиков автомобиля. Пороги демонстрационные, не заводские нормы.",small=true,muted=true)
    }
}

@Composable fun VehicleDetailsPopup(s:UiState,model:MainViewModel,show:(Popup)->Unit) {
    val vehicle=s.vehicle;val r=model.dependencies.vehicle
    Column(Modifier.fillMaxWidth().testTag("vehicle-details"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        VehicleTopView(vehicle,Modifier.fillMaxWidth().height(220.dp),onTire={show(Popup.tire(it))})
        if(vehicle.live) {
            Label("Состояния дверей и багажника — только чтение",small=true,muted=true)
            VehicleOpening.entries.forEach {opening->Label("${opening.displayName}: ${when{opening !in vehicle.knownOpenings->"нет свежих данных";opening in vehicle.openings->"открыто";else->"закрыто"}}",small=true)}
            Control("Подключение автомобиля",Modifier.testTag("live-vehicle-debug")){show(Popup.DEBUG)}
            return@Column
        }
        Label("Кузов · нажмите переключатель, чтобы изменить DEMO-состояние",small=true,muted=true)
        VehicleOpening.entries.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            row.forEach {opening->SettingsToggle(opening.displayName,
                if(opening in vehicle.openings)"Открыто · DEMO"else"Закрыто · DEMO",opening in vehicle.openings,
                Modifier.weight(1f).testTag("status-door-${opening.name.lowercase()}")){r.simulateOpening(opening)}}
        }}
        SettingsToggle("Движение · DEMO","Только тест предупреждения, без управления автомобилем",vehicle.mockMoving,Modifier.testTag("status-moving")){r.simulateMoving(!vehicle.mockMoving)}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("Закрыть всё · DEMO",Modifier.weight(1f).testTag("status-close-all")){r.closeAllOpenings()}
            Control("Расширенный симулятор",Modifier.weight(1f).testTag("status-debug")){show(Popup.DEBUG)}
        }
    }
}
