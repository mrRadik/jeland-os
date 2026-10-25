package ru.jeland.os.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.jeland.os.R
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable fun VehicleStatusCard(vehicle:VehicleState,modifier:Modifier=Modifier,onVehicle:()->Unit,onTire:(Wheel)->Unit,onDebug:()->Unit) {
    VehicleStatusWithFuel(vehicle,modifier,onVehicle,onTire,false,{},onDebug=onDebug)
}
@Composable fun VehicleStatusWithFuel(vehicle:VehicleState,modifier:Modifier=Modifier,onVehicle:()->Unit,onTire:(Wheel)->Unit,showFuel:Boolean=false,onFuel:()->Unit={},onBattery:(()->Unit)?=null,onDebug:()->Unit) {
    Panel(modifier.testTag("vehicle-status-card")) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Состояние автомобиля",color=Jeland.colors.text,fontSize=Jeland.type.body,modifier=Modifier.weight(1f))
            Row(Modifier.height(40.dp).clickable(onClick=onDebug).testTag("open-debug"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(vehicle.live)"ГУ"else"DEMO",color=Jeland.colors.muted,fontSize=Jeland.type.footnote)
                Glyph(JIcons.Settings,"Тестовые события",Modifier.size(20.dp))
            }
        }
        VehicleTopView(vehicle,Modifier.fillMaxWidth().weight(1f).padding(horizontal=14.dp).padding(bottom=8.dp),true,onVehicle,onTire,showFuel,onFuel,if(showFuel).60f else .64f,onBattery)
    }
}
/** Car nose points LEFT. Lower edge is the driver's left side; tyres never swap sides. */
@Composable fun VehicleTopView(vehicle:VehicleState,modifier:Modifier=Modifier,showTires:Boolean=true,onVehicle:(()->Unit)?=null,onTire:((Wheel)->Unit)?=null,showFuel:Boolean=false,onFuel:(()->Unit)?=null,widthFraction:Float=.64f,onBattery:(()->Unit)?=null) {
    val c=Jeland.colors
    val progress=VehicleOpening.entries.associateWith {opening->
        val value by animateFloatAsState(if(opening in vehicle.openings)1f else 0f,tween(Jeland.animation.door),label="door-${opening.name}")
        value
    }
    BoxWithConstraints(modifier.clipToBounds().testTag("vehicle-top-view").semantics {
        contentDescription="Jeland J7 строго сверху, передняя часть слева. Открыты: ${vehicle.openings.joinToString {it.title}.ifEmpty {"не обнаружены"}}. ${if(vehicle.live)"ГУ; неизвестных зон: ${VehicleOpening.entries.size-vehicle.knownOpenings.size}"else"DEMO"}"
    }) {
        val diagram=VehicleDiagramGeometry.fit(maxWidth.value,maxHeight.value,widthFraction)
        Box(Modifier.align(Alignment.Center).fillMaxWidth(.65f).fillMaxHeight().background(Brush.radialGradient(listOf(c.deep.copy(alpha=if(vehicle.openings.isEmpty()).04f else .16f),c.deep.copy(alpha=0f)))))
        if(showTires) Canvas(Modifier.matchParentSize()) {
            for(front in listOf(true,false))for(left in listOf(true,false)) {
                val g=VehicleDiagramGeometry.fit(size.width,size.height,widthFraction)
                val x=g.px(if(front).20f else .80f);val y=g.py(if(left).80f else .20f)
                val edgeX=if(front).17f else .83f
                val path=Path().apply {moveTo(edgeX*size.width,y);lineTo(x,y)}
                drawPath(path,c.outline,style=Stroke(1.dp.toPx()))
            }
        }
        Image(painterResource(R.drawable.vehicle_top),null,Modifier.offset(diagram.x.dp,diagram.y.dp).size(diagram.width.dp,diagram.height.dp).testTag("vehicle-artwork"),contentScale=ContentScale.Fit)
        Canvas(Modifier.matchParentSize()) {
            val g=VehicleDiagramGeometry.fit(size.width,size.height,widthFraction)
            for(opening in VehicleOpening.entries) {
                val amount=progress.getValue(opening)
                if(amount<.01f)continue
                val isDoor=opening!=VehicleOpening.HOOD&&opening!=VehicleOpening.TRUNK
                val path=Path()
                if(isDoor) {
                    val front=opening==VehicleOpening.FRONT_LEFT||opening==VehicleOpening.FRONT_RIGHT
                    val left=opening==VehicleOpening.FRONT_LEFT||opening==VehicleOpening.REAR_LEFT
                    val x=g.px(if(front).35f else .55f)
                    val y=g.py(if(left).775f else .245f)
                    val side=if(left)1f else -1f
                    val angle=amount*1.12f;val length=g.width*.145f
                    val dx=length*cos(angle);val dy=length*sin(angle)*side
                    val thickness=g.height*.035f
                    path.moveTo(x,y);path.lineTo(x+dx,y+dy)
                    path.quadraticBezierTo(x+dx+thickness,y+dy-side*thickness,x+dx,y+dy-side*thickness*2)
                    path.lineTo(x,y-side*thickness);path.close()
                } else {
                    val front=opening==VehicleOpening.HOOD;val x=g.px(if(front).19f else .86f)
                    val out=(if(front)-1 else 1)*g.width*.05f*amount
                    path.moveTo(x,g.py(.32f));path.lineTo(x+out,g.py(.25f));path.lineTo(x+out,g.py(.75f));path.lineTo(x,g.py(.68f));path.close()
                }
                drawPath(path,c.warm.copy(alpha=.18f),style=Stroke(9.dp.toPx()))
                // Body panels belong to the dark car artwork, not the light/dark UI surface palette.
                drawPath(path,Brush.linearGradient(listOf(c.road,c.artScrim)))
                drawPath(path,c.warm,style=Stroke(1.8.dp.toPx()))
            }
        }
        if(onVehicle!=null)Box(Modifier.offset(diagram.x.dp,diagram.y.dp).size(diagram.width.dp,diagram.height.dp)
            .clickable(role=Role.Button,onClick=onVehicle).testTag("vehicle-body")
            .semantics {contentDescription="Открыть расширенное состояние автомобиля"})
        if(showTires&&onTire!=null)BoxWithConstraints(Modifier.matchParentSize()) {
            Wheel.entries.forEach {wheel->
                val x=diagram.px(if(wheel.front).20f else .80f);val y=diagram.py(if(wheel.left).80f else .20f)
                Box(Modifier.offset(x=x.dp-26.dp,y=y.dp-26.dp).size(52.dp)
                    .clip(Jeland.control).clickable(role=Role.Button){onTire(wheel)}.testTag("wheel-${wheel.name.lowercase()}")
                    .semantics {contentDescription="${wheel.title}: давление и температура шины"})
            }
        }
        if(showTires) {
            val upper=4.dp
            val lower=(maxHeight.value-56f).coerceAtLeast(0f).dp
            TirePressureIndicator(Wheel.FRONT_RIGHT,vehicle,Modifier.align(Alignment.TopStart).offset(y=upper)){onTire?.invoke(Wheel.FRONT_RIGHT)}
            TirePressureIndicator(Wheel.REAR_RIGHT,vehicle,Modifier.align(Alignment.TopEnd).offset(y=upper)){onTire?.invoke(Wheel.REAR_RIGHT)}
            TirePressureIndicator(Wheel.FRONT_LEFT,vehicle,Modifier.align(Alignment.TopStart).offset(y=lower)){onTire?.invoke(Wheel.FRONT_LEFT)}
            TirePressureIndicator(Wheel.REAR_LEFT,vehicle,Modifier.align(Alignment.TopEnd).offset(y=lower)){onTire?.invoke(Wheel.REAR_LEFT)}
        }
        if(onBattery!=null)BatteryIndicator(vehicle.battery,Modifier.align(Alignment.CenterStart),onBattery)
        if(showFuel)VehicleFuelIndicator(vehicle.fuel,Modifier.align(Alignment.CenterEnd)){onFuel?.invoke()}
    }
}
@Composable private fun VehicleFuelIndicator(fuel:FuelState,modifier:Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Column(modifier.width(122.dp).clip(Jeland.card).background(c.panel.copy(alpha=.94f)).border(1.dp,c.outline,Jeland.card)
        .clickable(onClick=onClick).padding(horizontal=10.dp,vertical=8.dp).testTag("fuel-widget"),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(JIcons.Fuel,null,Modifier.size(18.dp),tint=c.accent)
            Text(fuel.displayLiters?.let {"${if(fuel.estimated)"≈ "else""}$it л"}?:"— л",color=c.text,fontSize=Jeland.type.control,lineHeight=Jeland.type.title,maxLines=1)
        }
        Text(fuel.litersToFull?.let {"До полного $it л"}?:"Топливо",color=c.muted,fontSize=Jeland.type.micro,lineHeight=Jeland.type.caption,maxLines=1)
    }
}
@Composable fun TirePressureIndicator(wheel:Wheel,vehicle:VehicleState,modifier:Modifier=Modifier,onClick:()->Unit) {
    val reading=vehicle.tires[wheel]?:TireReading();val c=Jeland.colors
    val color=when(vehicle.tireThresholds.severity(reading)) {TireSeverity.NORMAL->c.muted;TireSeverity.WARNING->c.warm;TireSeverity.CRITICAL->c.critical;TireSeverity.UNKNOWN->c.unknown}
    Column(modifier.width(122.dp).clip(Jeland.card).background(c.panel.copy(alpha=.94f)).border(1.dp,c.outline,Jeland.card)
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=7.dp).testTag("tire-${wheel.name.lowercase()}")
        .semantics {contentDescription="${wheel.title}, ${reading.validBar?.let {"$it бар"}?:"нет данных"}, ${if(vehicle.live)"ГУ: предварительное сопоставление каналов"else"DEMO"}"},horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Canvas(Modifier.size(7.dp)){drawCircle(color)}
            Text(reading.validBar?.let {(if(vehicle.live)"≈ "else"")+String.format(java.util.Locale.US,"%.1f бар",it)}?:"Нет данных",color=if(reading.validBar!=null)c.text else c.unknown,fontSize=if(reading.validBar!=null)Jeland.type.control else Jeland.type.caption,lineHeight=Jeland.type.title,maxLines=1)
        }
        val position=when(wheel){Wheel.FRONT_LEFT->"ПЛ";Wheel.FRONT_RIGHT->"ПП";Wheel.REAR_LEFT->"ЗЛ";Wheel.REAR_RIGHT->"ЗП"}
        Text("$position  ${reading.temperature?.let {"$it °C"}?:"— °C"}",color=c.muted,fontSize=Jeland.type.footnote,lineHeight=Jeland.type.caption)
    }
}
