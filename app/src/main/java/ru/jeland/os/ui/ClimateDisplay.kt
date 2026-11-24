package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.ClimateState
import ru.jeland.os.core.VerifiedVehicleProtocol
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import kotlin.math.roundToInt

internal fun climateDockTemperature(s:ClimateState,driver:Boolean):String {
    val raw=if(driver)s.driverRaw else s.passengerRaw
    val value=if(driver)s.driver else s.passenger
    return when {
        !s.known(if(driver)10 else 13)->"—"
        s.available!=null&&raw==0->"LO"
        s.available!=null&&raw==16->"HI"
        !value.isFinite()->"—"
        else->value.roundToInt().toString()
    }
}
internal fun climateFanLevel(s:ClimateState):Int?=when {
    !s.known(18)->null
    !s.power->0
    !s.known(12)||s.fan !in 0..VerifiedVehicleProtocol.FAN_MAX->null
    else->s.fan
}

/** Static fan with one rim segment per reported speed, matching the seat controls. */
@Composable fun ClimateFan(s:ClimateState,modifier:Modifier=Modifier) {
    val c=Jeland.colors
    val level=climateFanLevel(s)
    val maximum=VerifiedVehicleProtocol.FAN_MAX
    Box(modifier.semantics {
        stateDescription=when(level){null->"Скорость вентилятора: нет данных";0->"Вентилятор выключен";else->"Скорость вентилятора: $level из $maximum"}
    },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius=size.minDimension/2-2.5.dp.toPx()
            val offset=Offset(center.x-radius,center.y-radius)
            val arcSize=Size(radius*2,radius*2)
            drawCircle(c.outline,size.minDimension/2-1.2.dp.toPx(),style=Stroke(1.1.dp.toPx()))
            repeat(maximum) {index->
                val lit=level!=null&&index<level
                val angle=135f+index*27f
                if(lit)drawArc(c.accent.copy(alpha=.16f),angle,18f,false,offset,arcSize,
                    style=Stroke(5.3.dp.toPx(),cap=StrokeCap.Round))
                drawArc(if(lit)c.accent else c.outline,angle,18f,false,offset,arcSize,
                    style=Stroke(3.1.dp.toPx(),cap=StrokeCap.Round))
            }
        }
        Icon(JIcons.Fan,null,Modifier.fillMaxSize(.54f),tint=if(level==null)c.unknown else c.text)
    }
}

@Composable fun ClimateFanButton(s:ClimateState,modifier:Modifier=Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    val active=(climateFanLevel(s)?:0)>0
    Box(modifier.size(64.dp).clip(CircleShape)
        .background(Brush.radialGradient(listOf(if(active)c.deep.copy(alpha=.26f)else c.raised.copy(alpha=.55f),c.rail)))
        .semantics(mergeDescendants=true) {contentDescription="Расширенный климат";selected=active}
        .clickable(role=Role.Button,onClick=onClick)) {
        ClimateFan(s,Modifier.fillMaxSize())
    }
}
