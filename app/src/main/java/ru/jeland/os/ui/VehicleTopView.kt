package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
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
            if(ru.jeland.os.BuildConfig.DEMO_FEATURES)Row(Modifier.height(40.dp).clickable(onClick=onDebug).testTag("open-debug"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(vehicle.live)"ГУ"else"DEMO",color=Jeland.colors.muted,fontSize=Jeland.type.footnote)
                Glyph(JIcons.Settings,"Тестовые события",Modifier.size(20.dp))
            }
        }
        VehicleTopView(vehicle,Modifier.fillMaxWidth().weight(1f).padding(horizontal=14.dp).padding(bottom=8.dp),true,onVehicle,onTire,showFuel,onFuel,if(showFuel).60f else .64f,onBattery)
    }
}
/** Car nose points LEFT. Lower edge is the driver's left side; tyres never swap sides. */
@Composable fun VehicleTopView(vehicle:VehicleState,modifier:Modifier=Modifier,showTires:Boolean=true,onVehicle:(()->Unit)?=null,onTire:((Wheel)->Unit)?=null,showFuel:Boolean=false,onFuel:(()->Unit)?=null,widthFraction:Float=.64f,onBattery:(()->Unit)?=null,portrait:Boolean=false) {
    if(portrait){PortraitVehicleLayout(vehicle,modifier,showTires,onVehicle,onTire,showFuel,onFuel,onBattery);return}
    val c=Jeland.colors
    val shadeTarget=shadeOpenFraction(vehicle.shadeUiPosition)
    val shadeAmount by animateFloatAsState(shadeTarget?:0f,tween(Jeland.animation.door),label="vehicle-panorama-shade")
    val progress=VehicleOpening.entries.associateWith {opening->
        val value by animateFloatAsState(if(opening in vehicle.openings)1f else 0f,tween(Jeland.animation.door),label="door-${opening.name}")
        value
    }
    BoxWithConstraints(modifier.clipToBounds().testTag("vehicle-top-view").semantics {
        contentDescription="Автомобиль. Открыты: ${vehicle.openings.joinToString {it.displayName}.ifEmpty {"не обнаружены"}}"
    }) {
        val diagram=VehicleDiagramGeometry.fit(maxWidth.value,maxHeight.value,widthFraction)
        Box(Modifier.align(Alignment.Center).fillMaxWidth(.65f).fillMaxHeight().background(Brush.radialGradient(listOf(c.deep.copy(alpha=if(vehicle.openings.isEmpty()).04f else .16f),c.deep.copy(alpha=0f)))))
        if(showTires) Canvas(Modifier.matchParentSize()) {
            for(front in listOf(true,false))for(left in listOf(true,false)) {
                val g=VehicleDiagramGeometry.fit(size.width,size.height,widthFraction)
                val x=g.px(VehicleArtworkCoordinates.wheelLongitudinal(front));val y=g.py(VehicleArtworkCoordinates.wheelLateral(left))
                val edgeX=if(front).17f else .83f
                val path=Path().apply {moveTo(edgeX*size.width,y);lineTo(x,y)}
                drawPath(path,c.outline,style=Stroke(1.dp.toPx()))
            }
        }
        VehicleArtwork(Modifier.offset(diagram.x.dp,diagram.y.dp).size(diagram.width.dp,diagram.height.dp).testTag("vehicle-artwork"))
        Canvas(Modifier.offset(diagram.px(VehicleArtworkCoordinates.ROOF_LEFT).dp,diagram.py(VehicleArtworkCoordinates.ROOF_TOP).dp)
            .size((diagram.width*(VehicleArtworkCoordinates.ROOF_RIGHT-VehicleArtworkCoordinates.ROOF_LEFT)).dp,
                (diagram.height*(VehicleArtworkCoordinates.ROOF_BOTTOM-VehicleArtworkCoordinates.ROOF_TOP)).dp).testTag("vehicle-shade-state")
            .semantics {
                contentDescription="Шторка панорамной крыши"
                stateDescription=vehicle.shadeUiSummary
                if(shadeTarget!=null)progressBarRangeInfo=ProgressBarRangeInfo(shadeAmount,0f..1f)
            }) {
            drawSunshadeGlass(Rect(Offset.Zero,size),shadeAmount,shadeTarget!=null,c.accent,c.artText)
        }
        Canvas(Modifier.matchParentSize()) {
            val g=VehicleDiagramGeometry.fit(size.width,size.height,widthFraction)
            for(opening in VehicleOpening.entries) {
                val amount=progress.getValue(opening)
                if(amount<.01f)continue
                val isDoor=opening!=VehicleOpening.HOOD&&opening!=VehicleOpening.TRUNK
                if(isDoor) {
                    drawOpenedDoor(g,opening,amount,c.accent)
                    continue
                }
                val path=Path()
                val front=opening==VehicleOpening.HOOD;val x=g.px(if(front).19f else .86f)
                val out=(if(front)-1 else 1)*g.width*.05f*amount
                path.moveTo(x,g.py(.32f));path.lineTo(x+out,g.py(.25f));path.lineTo(x+out,g.py(.75f));path.lineTo(x,g.py(.68f));path.close()
                drawPath(path,c.warm.copy(alpha=.18f),style=Stroke(9.dp.toPx()))
                // Body panels belong to the dark car artwork, not the light/dark UI surface palette.
                drawPath(path,Brush.linearGradient(listOf(c.road,c.artScrim)))
                drawPath(path,c.warm,style=Stroke(1.8.dp.toPx()))
            }
        }
        if(onVehicle!=null)Box(Modifier.offset(diagram.x.dp,diagram.y.dp).size(diagram.width.dp,diagram.height.dp)
            .clickable(role=Role.Button,onClick=onVehicle).testTag("vehicle-body")
            .semantics {contentDescription="Открыть расширенное состояние автомобиля"})
        if(onTire!=null)Box(Modifier.matchParentSize()) {
            Wheel.entries.forEach {wheel->
                val x=diagram.px(VehicleArtworkCoordinates.wheelLongitudinal(wheel.front));val y=diagram.py(VehicleArtworkCoordinates.wheelLateral(wheel.left))
                Box(Modifier.offset(x=x.dp-26.dp,y=y.dp-26.dp).size(52.dp)
                    .clip(Jeland.control).clickable(role=Role.Button){onTire(wheel)}.testTag("wheel-${wheel.name.lowercase()}")
                    .semantics {contentDescription="${wheel.title}: давление шины"})
            }
        }
        if(showTires) {
            val upper=4.dp
            TirePressureIndicator(Wheel.FRONT_RIGHT,vehicle,Modifier.align(Alignment.TopStart).offset(y=upper)){onTire?.invoke(Wheel.FRONT_RIGHT)}
            TirePressureIndicator(Wheel.REAR_RIGHT,vehicle,Modifier.align(Alignment.TopEnd).offset(y=upper)){onTire?.invoke(Wheel.REAR_RIGHT)}
            TirePressureIndicator(Wheel.FRONT_LEFT,vehicle,Modifier.align(Alignment.BottomStart)){onTire?.invoke(Wheel.FRONT_LEFT)}
            TirePressureIndicator(Wheel.REAR_LEFT,vehicle,Modifier.align(Alignment.BottomEnd)){onTire?.invoke(Wheel.REAR_LEFT)}
        }
        if(onBattery!=null)BatteryIndicator(vehicle.battery,Modifier.align(Alignment.CenterStart),onClick=onBattery)
        if(showFuel)VehicleFuelIndicator(vehicle.fuel,Modifier.align(Alignment.CenterEnd)){onFuel?.invoke()}
    }
}
/** Layout adapter only. Artwork, animated body panels and touch targets are the
 * SAME horizontal component, rotated together. Readouts remain upright outside it.
 */
@Composable private fun PortraitVehicleLayout(vehicle:VehicleState,modifier:Modifier,showTires:Boolean,
    onVehicle:(()->Unit)?,onTire:((Wheel)->Unit)?,showFuel:Boolean,onFuel:(()->Unit)?,onBattery:(()->Unit)?) {
    BoxWithConstraints(modifier.testTag("vehicle-portrait-layout")) {
        val diagram=VehicleDiagramGeometry.fit(maxHeight.value,maxWidth.value,.98f)
        val frontY=(diagram.px(VehicleArtworkCoordinates.wheelLongitudinal(true)).dp-44.dp).coerceAtLeast(0.dp)
        val rearY=(diagram.px(VehicleArtworkCoordinates.wheelLongitudinal(false)).dp-16.dp).coerceAtLeast(0.dp)
        val plain=LocalFramelessDashboard.current
        val c=Jeland.colors
        Layout(modifier=Modifier.fillMaxSize(),content={
            VehicleTopView(vehicle,Modifier.fillMaxSize(),showTires=false,onVehicle=onVehicle,
                onTire=onTire,widthFraction=.98f)
        }) {measurables,constraints->
            val width=constraints.maxWidth;val height=constraints.maxHeight
            val body=measurables.single().measure(Constraints.fixed(height,width))
            layout(width,height) {body.placeWithLayer(width,0){rotationZ=90f;transformOrigin=TransformOrigin(0f,0f)}}
        }
        if(showTires&&!plain)Canvas(Modifier.fillMaxSize().testTag("wheel-sensor-lines")) {
            val geometry=VehicleDiagramGeometry.fit(size.height,size.width,.98f)
            for(wheel in Wheel.entries) {
                val x=size.width-geometry.py(VehicleArtworkCoordinates.wheelLateral(wheel.left))
                val y=geometry.px(VehicleArtworkCoordinates.wheelLongitudinal(wheel.front))
                val edge=if(wheel.left)150.dp.toPx() else size.width-150.dp.toPx()
                val reading=vehicle.tires[wheel]?:TireReading()
                val tint=when(vehicle.tireThresholds.severity(reading)) {
                    TireSeverity.WARNING->c.warm;TireSeverity.CRITICAL->c.critical
                    TireSeverity.UNKNOWN->c.unknown;TireSeverity.NORMAL->c.accent
                }
                val labelY=y+(if(wheel.front)-14 else 14).dp.toPx()
                val direction=if(wheel.left)-1 else 1
                val from=Offset(edge,labelY);val to=Offset(x,y)
                val path=Path().apply {
                    moveTo(edge,labelY);lineTo(x+direction*32.dp.toPx(),labelY)
                    lineTo(x+direction*14.dp.toPx(),y);lineTo(x,y)
                }
                drawPath(path,tint.copy(alpha=.12f),style=Stroke(8.dp.toPx()))
                drawPath(path,Brush.linearGradient(listOf(tint.copy(alpha=.6f),tint),from,to),style=Stroke(1.8.dp.toPx()))
                drawCircle(tint.copy(alpha=.15f),7.dp.toPx(),to)
                drawCircle(tint,2.5.dp.toPx(),to)
            }
        }
        if(showFuel)VehicleFuelIndicator(vehicle.fuel,Modifier.align(Alignment.TopStart),expanded=true){onFuel?.invoke()}
        if(onBattery!=null)BatteryIndicator(vehicle.battery,Modifier.align(Alignment.TopEnd),expanded=true,onClick=onBattery)
        if(showTires&&plain) {
            Wheel.entries.forEach {wheel->
                val x=maxWidth.value-diagram.py(VehicleArtworkCoordinates.wheelLateral(wheel.left))
                val labelX=if(wheel.left)x-124f else x+14f
                val labelY=diagram.px(VehicleArtworkCoordinates.wheelLongitudinal(wheel.front))-26f
                TirePressureIndicator(wheel,vehicle,Modifier.offset(labelX.dp,labelY.dp),frameless=true){onTire?.invoke(wheel)}
            }
        }else if(showTires) {
            TirePressureIndicator(Wheel.FRONT_LEFT,vehicle,Modifier.align(Alignment.TopStart).offset(y=frontY),expanded=true){onTire?.invoke(Wheel.FRONT_LEFT)}
            TirePressureIndicator(Wheel.FRONT_RIGHT,vehicle,Modifier.align(Alignment.TopEnd).offset(y=frontY),expanded=true){onTire?.invoke(Wheel.FRONT_RIGHT)}
            TirePressureIndicator(Wheel.REAR_LEFT,vehicle,Modifier.align(Alignment.TopStart).offset(y=rearY),expanded=true){onTire?.invoke(Wheel.REAR_LEFT)}
            TirePressureIndicator(Wheel.REAR_RIGHT,vehicle,Modifier.align(Alignment.TopEnd).offset(y=rearY),expanded=true){onTire?.invoke(Wheel.REAR_RIGHT)}
        }
    }
}
@Composable internal fun VehicleFuelIndicator(fuel:FuelState,modifier:Modifier,expanded:Boolean=false,wide:Boolean=false,gauge:Boolean=false,onClick:()->Unit) {
    val c=Jeland.colors
    val fuelColor=if(fuel.low)c.critical else c.warm
    if(gauge) {
        VehicleSideGauge("Топливо",JIcons.Fuel,fuel.displayLiters?.let {"${if(fuel.estimated)"≈ "else""}$it л"}?:"— л",
            fuel.validLiters?.div(FuelState.CAPACITY)?.toFloat(),fuel.litersToFull?.let {"До полного\n$it л"}?:"Нет данных",
            fuelColor,modifier.testTag("fuel-widget"),onClick=onClick)
        return
    }
    if(wide) {
        VehicleMetric("ТОПЛИВО",JIcons.Fuel,fuel.displayLiters?.let {"${if(fuel.estimated)"≈ "else""}$it л"}?:"— л",
            fuel.validLiters?.div(FuelState.CAPACITY)?.toFloat(),"До полного",fuel.litersToFull?.let {"$it л"}?:"— л",
            fuelColor,modifier.testTag("fuel-widget"),onClick=onClick)
        return
    }
    if(expanded) {
        Column(modifier.width(178.dp).clip(Jeland.control).clickable(onClick=onClick).padding(12.dp)
            .testTag("fuel-widget"),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(JIcons.Fuel,null,Modifier.size(20.dp),tint=fuelColor)
                Text("ТОПЛИВО",color=c.muted,fontSize=Jeland.type.caption,letterSpacing=Jeland.type.trackTight)
            }
            Text(fuel.displayLiters?.let {"${if(fuel.estimated)"≈ "else""}$it л"}?:"— л",color=c.text,
                fontSize=Jeland.type.weather,fontWeight=FontWeight.Light,maxLines=1)
            VehicleLevelBar(fuel.validLiters?.div(FuelState.CAPACITY)?.toFloat(),fuelColor)
            Text(fuel.litersToFull?.let {"До полного $it л"}?:"Нет данных об остатке",color=c.muted,fontSize=Jeland.type.caption)
        }
        return
    }
    Column(modifier.width(122.dp).clip(Jeland.card).background(c.panel.copy(alpha=.94f)).border(1.dp,c.outline,Jeland.card)
        .clickable(onClick=onClick).padding(horizontal=10.dp,vertical=8.dp).testTag("fuel-widget"),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Icon(JIcons.Fuel,null,Modifier.size(18.dp),tint=if(fuel.low)c.critical else c.accent)
            Text(fuel.displayLiters?.let {"${if(fuel.estimated)"≈ "else""}$it л"}?:"— л",color=c.text,fontSize=Jeland.type.control,lineHeight=Jeland.type.title,maxLines=1)
        }
        Text(fuel.litersToFull?.let {"До полного $it л"}?:"Топливо",color=c.muted,fontSize=Jeland.type.micro,lineHeight=Jeland.type.caption,maxLines=1)
    }
}
@Composable fun TirePressureIndicator(wheel:Wheel,vehicle:VehicleState,modifier:Modifier=Modifier,expanded:Boolean=false,frameless:Boolean=false,onClick:()->Unit) {
    val reading=vehicle.tires[wheel]?:TireReading();val c=Jeland.colors
    val color=when(vehicle.tireThresholds.severity(reading)) {TireSeverity.NORMAL->if(expanded)c.accent else c.muted;TireSeverity.WARNING->c.warm;TireSeverity.CRITICAL->c.critical;TireSeverity.UNKNOWN->c.unknown}
    if(frameless) {
        Box(modifier.width(110.dp).height(52.dp).clickable(onClick=onClick).testTag("tire-${wheel.name.lowercase()}")
            .semantics {contentDescription="${wheel.title}: ${reading.validBar?.let {"$it бар"}?:"нет данных о давлении"}"},contentAlignment=Alignment.Center) {
            Text(reading.validBar?.let {(if(vehicle.live)"≈ "else"")+String.format(java.util.Locale.US,"%.1f бар",it)}?:"—",
                color=if(vehicle.tireThresholds.severity(reading)==TireSeverity.NORMAL)c.text else color,
                fontSize=Jeland.type.control,maxLines=1)
        }
        return
    }
    Column(modifier.width(if(expanded)150.dp else 122.dp).clip(Jeland.card).background(c.panel.copy(alpha=if(expanded).75f else .94f)).border(1.dp,if(expanded)c.accent.copy(alpha=.4f)else c.outline,Jeland.card)
        .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=7.dp).testTag("tire-${wheel.name.lowercase()}")
        .semantics {contentDescription="${wheel.title}: ${reading.validBar?.let {"$it бар"}?:"нет данных"}"},horizontalAlignment=Alignment.CenterHorizontally) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Canvas(Modifier.size(7.dp)){drawCircle(color)}
            Text(reading.validBar?.let {(if(vehicle.live)"≈ "else"")+String.format(java.util.Locale.US,"%.1f бар",it)}?:"Нет данных",color=if(reading.validBar!=null)c.text else c.unknown,fontSize=if(expanded&&reading.validBar!=null)Jeland.type.clockSmall else if(reading.validBar!=null)Jeland.type.control else Jeland.type.caption,lineHeight=Jeland.type.title,maxLines=1)
        }
        val position=when(wheel){Wheel.FRONT_LEFT->"ПЛ";Wheel.FRONT_RIGHT->"ПП";Wheel.REAR_LEFT->"ЗЛ";Wheel.REAR_RIGHT->"ЗП"}
        Text(position,color=c.muted,fontSize=if(expanded)Jeland.type.caption else Jeland.type.footnote,lineHeight=Jeland.type.caption)
    }
}

/** Shared readout presentation; fuel/battery keep their own data and callbacks. */
@Composable internal fun VehicleMetric(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,
    value:String,fraction:Float?,detailTitle:String,detail:String,tint:Color,modifier:Modifier=Modifier,
    valueTag:String?=null,onClick:()->Unit) {
    val c=Jeland.colors
    Row(modifier.clip(Jeland.control).clickable(onClick=onClick).padding(horizontal=12.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(icon,null,Modifier.size(22.dp),tint=tint)
                Text(title,color=c.muted,fontSize=Jeland.type.footnote,letterSpacing=Jeland.type.trackTight,maxLines=1)
            }
            Text(value,color=c.text,fontSize=Jeland.type.weather,fontWeight=FontWeight.Light,maxLines=1,
                modifier=if(valueTag!=null)Modifier.testTag(valueTag)else Modifier)
            VehicleLevelBar(fraction,tint)
        }
        Spacer(Modifier.width(1.dp).height(50.dp).background(c.outline.copy(alpha=.35f)))
        Column(Modifier.width(90.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(detailTitle,color=c.muted,fontSize=Jeland.type.caption)
            Text(detail,color=c.text,fontSize=Jeland.type.clockSmall,maxLines=1)
        }
    }
}

@Composable internal fun VehicleLevelBar(fraction:Float?,tint:Color,modifier:Modifier=Modifier) {
    val track=Jeland.colors.outline.copy(alpha=.45f)
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val corner=androidx.compose.ui.geometry.CornerRadius(size.height/2)
        drawRoundRect(track,cornerRadius=corner)
        fraction?.takeIf {it.isFinite()}?.coerceIn(0f,1f)?.let {
            drawRoundRect(Brush.horizontalGradient(listOf(tint.copy(alpha=.55f),tint)),
                size=androidx.compose.ui.geometry.Size(size.width*it,size.height),cornerRadius=corner)
        }
    }
}
