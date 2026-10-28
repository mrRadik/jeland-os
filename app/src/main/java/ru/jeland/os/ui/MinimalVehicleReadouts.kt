package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland
import java.util.Locale

/** Alternative presentation only; original fuel/battery components supply the values. */
@Composable internal fun VehicleSideGauge(title:String,icon:ImageVector,value:String,fraction:Float?,detail:String,
    tint:Color,modifier:Modifier=Modifier,valueTag:String?=null,onClick:()->Unit) {
    val c=Jeland.colors
    val level=fraction?.takeIf {it.isFinite()}?.coerceIn(0f,1f)
    Column(modifier.width(84.dp).clickable(role=Role.Button,onClick=onClick).padding(vertical=8.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=tint)
        Text(title,color=c.muted,fontSize=Jeland.type.footnote,maxLines=1)
        Text(value,color=c.text,fontSize=Jeland.type.clockSmall,maxLines=1,
            modifier=if(valueTag!=null)Modifier.testTag(valueTag)else Modifier)
        Canvas(Modifier.size(42.dp,230.dp)) {
            val top=5.dp.toPx();val bottom=size.height-top;val length=bottom-top
            val x=size.width*.35f
            drawLine(c.outline.copy(alpha=.35f),Offset(x,top),Offset(x,bottom),3.dp.toPx(),StrokeCap.Round)
            level?.let {progress->
                val end=bottom-length*progress
                drawLine(Brush.verticalGradient(listOf(tint,tint.copy(alpha=.35f))),Offset(x,end),Offset(x,bottom),3.dp.toPx(),StrokeCap.Round)
                drawCircle(tint.copy(alpha=.15f),6.dp.toPx(),Offset(x,end));drawCircle(tint,2.6.dp.toPx(),Offset(x,end))
            }
            repeat(21) {i->
                val y=bottom-length*i/20f
                val lit=level!=null&&i/20f<=level
                val start=x+8.dp.toPx();val width=if(i%5==0)12.dp.toPx()else 6.dp.toPx()
                drawLine(if(lit)tint.copy(alpha=.8f)else c.outline.copy(alpha=.5f),Offset(start,y),Offset(start+width,y),1.dp.toPx(),StrokeCap.Round)
            }
        }
        Text(detail,color=c.muted,fontSize=Jeland.type.footnote,textAlign=TextAlign.Center,maxLines=2)
    }
}

/** Four independent pressure targets around a neutral top-view contour. */
@Composable fun TirePressureSummary(vehicle:VehicleState,modifier:Modifier=Modifier,onTire:(Wheel)->Unit,onVehicle:()->Unit) {
    val c=Jeland.colors
    Column(modifier.testTag("pressure-summary"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text("ДАВЛЕНИЕ В ШИНАХ",color=c.muted,fontSize=Jeland.type.footnote,letterSpacing=Jeland.type.trackTight,maxLines=1)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                PressureValue(Wheel.FRONT_LEFT,vehicle){onTire(Wheel.FRONT_LEFT)}
                PressureValue(Wheel.REAR_LEFT,vehicle){onTire(Wheel.REAR_LEFT)}
            }
            Box(Modifier.size(62.dp,164.dp).clickable(role=Role.Button,onClick=onVehicle).testTag("pressure-summary-car")) {
                PressureCarOutline(Modifier.fillMaxSize().padding(horizontal=9.dp,vertical=14.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                PressureValue(Wheel.FRONT_RIGHT,vehicle){onTire(Wheel.FRONT_RIGHT)}
                PressureValue(Wheel.REAR_RIGHT,vehicle){onTire(Wheel.REAR_RIGHT)}
            }
        }
    }
}

@Composable private fun PressureCarOutline(modifier:Modifier=Modifier) {
    val tint=Jeland.colors.warm
    Canvas(modifier.testTag("pressure-car-outline").semantics {contentDescription="Контур автомобиля, вид сверху"}) {
        val w=size.width;val h=size.height
        val stroke=Stroke(1.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
        val body=Path().apply {
            moveTo(w*.25f,h*.05f)
            cubicTo(w*.36f,h*.015f,w*.64f,h*.015f,w*.75f,h*.05f)
            cubicTo(w*.91f,h*.10f,w*.91f,h*.19f,w*.87f,h*.29f)
            lineTo(w*.91f,h*.72f);lineTo(w*.85f,h*.92f)
            cubicTo(w*.80f,h*.99f,w*.20f,h*.99f,w*.15f,h*.92f)
            lineTo(w*.09f,h*.72f);lineTo(w*.13f,h*.29f)
            cubicTo(w*.09f,h*.19f,w*.09f,h*.10f,w*.25f,h*.05f);close()
        }
        drawPath(body,tint.copy(alpha=.9f),style=stroke)
        val windows=Path().apply {
            moveTo(w*.19f,h*.28f);lineTo(w*.27f,h*.19f)
            quadraticBezierTo(w*.5f,h*.16f,w*.73f,h*.19f)
            lineTo(w*.81f,h*.28f);lineTo(w*.72f,h*.34f);lineTo(w*.28f,h*.34f);close()
            moveTo(w*.28f,h*.71f);lineTo(w*.72f,h*.71f)
            lineTo(w*.80f,h*.85f);quadraticBezierTo(w*.5f,h*.89f,w*.20f,h*.85f);close()
            moveTo(w*.22f,h*.36f);lineTo(w*.18f,h*.68f)
            moveTo(w*.78f,h*.36f);lineTo(w*.82f,h*.68f)
            moveTo(w*.24f,h*.51f);lineTo(w*.76f,h*.51f)
        }
        drawPath(windows,tint.copy(alpha=.65f),style=stroke)
        for(left in listOf(true,false))for(front in listOf(true,false)) {
            drawRoundRect(tint.copy(alpha=.6f),Offset(w*(if(left).01f else .90f),h*(if(front).21f else .72f)),
                Size(w*.09f,h*.12f),CornerRadius(1.dp.toPx()),style=stroke)
        }
        for(left in listOf(true,false)) {
            val x=w*(if(left).11f else .89f);val sign=if(left)-1 else 1
            drawLine(tint.copy(alpha=.8f),Offset(x,h*.38f),Offset(x+sign*w*.10f,h*.40f),1.dp.toPx(),StrokeCap.Round)
        }
    }
}

@Composable private fun PressureValue(wheel:Wheel,vehicle:VehicleState,onClick:()->Unit) {
    val c=Jeland.colors;val reading=vehicle.tires[wheel]?:TireReading()
    val color=when(vehicle.tireThresholds.severity(reading)) {
        TireSeverity.NORMAL->c.text;TireSeverity.WARNING->c.warm;TireSeverity.CRITICAL->c.critical;TireSeverity.UNKNOWN->c.unknown
    }
    val value=reading.validBar?.let {(if(vehicle.live)"≈ "else"")+String.format(Locale.US,"%.1f",it)}?:"—"
    Column(Modifier.fillMaxWidth().heightIn(min=76.dp).clickable(role=Role.Button,onClick=onClick)
        .testTag("pressure-${wheel.name.lowercase()}").semantics {contentDescription="${wheel.title}: $value бар"},
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Text(value,color=color,fontSize=Jeland.type.clockSmall,maxLines=1)
        Text("бар",color=c.muted,fontSize=Jeland.type.caption)
    }
}
