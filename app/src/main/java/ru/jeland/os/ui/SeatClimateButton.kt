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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.SeatChannel
import ru.jeland.os.core.SeatSide
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

/** The existing seat-popup button, with independent 0..3 heat/vent arcs on its rim.
 * Display-only feedback: never cycles modes or sends a seat command on its own.
 */
@Composable fun SeatClimateButton(
    side:SeatSide,seat:SeatChannel,modifier:Modifier=Modifier,
    heatKnown:Boolean=true,ventilationKnown:Boolean=true,onClick:()->Unit
) {
    val c=Jeland.colors
    val heat=seat.heat.takeIf {heatKnown&&it in 0..3}
    val vent=seat.ventilation.takeIf {ventilationKnown&&it in 0..3}
    val active=(heat?:0)>0||(vent?:0)>0
    val heatColor=Color(0xFFFF963F)
    val ventColor=Color(0xFF35B9FF)
    val icon=when {
        (heat?:0)>0->JIcons.SeatHeating
        (vent?:0)>0->JIcons.SeatVentilation
        else->JIcons.Seat
    }
    val tag=if(side==SeatSide.DRIVER)"driver-seat"else"passenger-seat"
    Box(modifier.size(62.dp).testTag(tag).clip(CircleShape)
        .background(Brush.radialGradient(listOf(if(active)c.deep.copy(alpha=.26f)else c.raised.copy(alpha=.55f),c.rail)))
        .semantics {
            contentDescription=side.title
            selected=active
            stateDescription="Подогрев: ${heat?.let {"$it из 3"}?:"нет данных"}; вентиляция: ${vent?.let {"$it из 3"}?:"нет данных"}"
        }.clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter=size.minDimension
            val ring=diameter/2-1.2.dp.toPx()
            drawCircle(c.outline,ring,style=Stroke(1.1.dp.toPx()))
            val radius=diameter/2-2.5.dp.toPx()
            val offset=Offset(center.x-radius,center.y-radius)
            val arcSize=Size(radius*2,radius*2)
            fun lamps(level:Int?,color:Color,angles:List<Float>) {
                angles.forEachIndexed {index,angle->
                    val lit=level!=null&&index<level
                    if(lit)drawArc(color.copy(alpha=.16f),angle-8f,16f,false,offset,arcSize,
                        style=Stroke(5.3.dp.toPx(),cap=StrokeCap.Round))
                    drawArc(if(lit)color else c.outline,angle-8f,16f,false,offset,arcSize,
                        style=Stroke(3.1.dp.toPx(),cap=StrokeCap.Round))
                }
            }
            // Bottom-to-top progress on both sides. Geometry stays on the circle,
            // instead of dots under the button or bars outside the dock.
            lamps(heat,heatColor,listOf(150f,180f,210f))
            lamps(vent,ventColor,listOf(30f,0f,-30f))
        }
        Icon(icon,null,Modifier.size(32.dp).scale(scaleX=if(side==SeatSide.PASSENGER)-1f else 1f,scaleY=1f),tint=c.text)
    }
}
