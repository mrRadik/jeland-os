package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

@Composable fun ClimateDashboard(s:ClimateState,r:ClimateRepository) {
    val c=Jeland.colors;val live=s.available!=null;val minimum=if(live)1 else 0
    val maximum=VerifiedVehicleProtocol.FAN_MAX
    val direction=s.reportedAirflow
    val enabled=s.controlsEnabled
    Column(Modifier.testTag("climate-dashboard"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(86.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(76.dp).clip(Jeland.control).background(Brush.radialGradient(listOf(c.deep.copy(alpha=.5f),c.panel))).border(1.dp,c.accent.copy(alpha=.7f),Jeland.control),contentAlignment=Alignment.Center) {
                ClimateFan(s,Modifier.size(48.dp).testTag("climate-fan-icon"))
            }
            Column(Modifier.width(185.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Скорость вентилятора",color=c.muted,fontSize=16.sp)
                Row(Modifier.testTag("fan-value").semantics(mergeDescendants=true){},verticalAlignment=Alignment.Bottom){
                    Text(if(s.known(12))"${s.fan}"else"—",color=if(enabled)c.accent else c.unknown,fontSize=34.sp)
                    Text(" / $maximum",color=c.text,fontSize=25.sp,modifier=Modifier.padding(bottom=3.dp))
                }
            }
            RoundControl(JIcons.Remove,"Убавить вентилятор",Modifier.testTag("fan-minus"),enabled=enabled&&s.known(12)&&s.fan>minimum){r.setFan(s.fan-1)}
            var preview by remember(s.fan){mutableFloatStateOf(s.fan.coerceIn(minimum,maximum).toFloat())}
            JelandSlider(preview,{preview=it},minimum.toFloat()..maximum.toFloat(),Modifier.weight(1f).testTag("fan-slider"),enabled=enabled&&s.known(12),onValueChangeFinished={if(s.controlsEnabled)r.setFan(kotlin.math.round(preview).toInt())})
            RoundControl(JIcons.Add,"Прибавить вентилятор",Modifier.testTag("fan-plus"),enabled=enabled&&s.known(12)&&s.fan<maximum){r.setFan(s.fan+1)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            (1..maximum).forEach {level->ClimateBarButton(text="$level",description="Вентилятор $level",active=s.known(12)&&s.fan==level,
                enabled=enabled,modifier=Modifier.weight(1f).testTag("fan-level-$level")){r.setFan(level)}}
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline.copy(alpha=.6f)))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AirOutlet.entries.forEach {outlet->
                val selected=direction?.outlets?.contains(outlet)==true
                val icon=when(outlet){AirOutlet.FACE->JIcons.AirFace;AirOutlet.FEET->JIcons.AirFeet;AirOutlet.WINDSHIELD->JIcons.Windshield}
                Column(Modifier.weight(1f).height(152.dp).clip(Jeland.control)
                    .background(Brush.verticalGradient(listOf(if(selected&&enabled)c.deep.copy(alpha=.5f)else c.raised.copy(alpha=.3f),c.panel)))
                    .border(if(selected&&enabled)1.5.dp else 1.dp,if(selected&&enabled)c.accent else c.outline,Jeland.control)
                    .testTag("airflow-${outlet.name.lowercase()}").semantics {this.selected=selected;contentDescription=outlet.title}
                    .toggleable(selected,enabled=enabled,role=Role.Checkbox,onValueChange={
                        // There is no all-off airflow enum. Keep the last active outlet instead of inventing raw 7.
                        val target=direction?.toggle(outlet)?:Airflow.fromOutlets(setOf(outlet))!!
                        if(target!=direction)r.setAirflow(target)
                    }),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(icon,null,Modifier.size(65.dp),tint=if(!enabled)c.unknown.copy(alpha=.4f)else if(selected)c.accent else c.muted)
                    Text(outlet.title,color=if(enabled)c.text else c.unknown,fontSize=19.sp,modifier=Modifier.padding(top=12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ClimateAction("Питание",JIcons.Power,s.power&&s.known(18),s.known(18),Modifier.weight(1f).testTag("climate-power"),r::togglePower)
            ClimateAction("Авто",null,s.auto&&s.known(4),enabled&&s.known(4),Modifier.weight(1f).testTag("climate-auto"),r::toggleAuto,"AUTO")
            ClimateAction("A/C",JIcons.Snowflake,s.ac&&s.known(1),enabled&&s.known(1),Modifier.weight(1f).testTag("climate-ac"),r::toggleAc)
            ClimateAction("Рециркуляция",JIcons.Loop,s.recirculation&&s.known(8),enabled&&s.known(8),Modifier.weight(1f).testTag("climate-recirculation"),r::toggleRecirculation)
            ClimateAction("Обдув\nлобового",JIcons.Waves,s.defrost&&s.known(24),enabled&&s.known(24),Modifier.weight(1f).testTag("climate-front-defrost"),r::toggleDefrost)
            ClimateAction("Обогрев\nзаднего",JIcons.RearDefrost,s.rearDefrost&&s.known(80),enabled&&s.known(80),Modifier.weight(1f).testTag("climate-rear-defrost"),r::toggleRearDefrost)
            ClimateAction("Обогрев\nлобового",JIcons.WindshieldHeat,s.windshieldHeat&&s.known(65),enabled&&s.known(65),Modifier.weight(1f).testTag("climate-windshield-heat"),r::toggleWindshieldHeat)
            ClimateAction("Синхр.",null,s.known(3)&&s.sync,enabled&&s.known(3),Modifier.weight(1f).testTag("climate-sync"),r::toggleSync,"SYNC")
        }
        CabinAirflowPreview(direction,s.known(18)&&s.power,Modifier.fillMaxWidth().aspectRatio(4f))
        if(s.known(18)&&!s.power)Label("Климат выключен",small=true,muted=true,modifier=Modifier.testTag("climate-power-off"))
    }
}

@Composable private fun ClimateAction(title:String,icon:ImageVector?,active:Boolean,enabled:Boolean,modifier:Modifier,onClick:()->Unit,word:String?=null) {
    val c=Jeland.colors
    Column(modifier.height(108.dp).clip(Jeland.control).background(if(active&&enabled)c.deep.copy(alpha=.4f)else c.raised.copy(alpha=.35f))
        .border(1.dp,if(active&&enabled)c.accent else c.outline,Jeland.control).semantics {selected=active;if(!enabled)disabled()}
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=3.dp,vertical=12.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterVertically)) {
        if(icon!=null)Icon(icon,null,Modifier.size(34.dp),tint=if(!enabled)c.unknown else if(active)c.accent else c.muted)
        else Text(word.orEmpty(),color=if(!enabled)c.unknown else if(active)c.accent else c.text,fontSize=21.sp)
        Text(title,color=if(enabled)c.text else c.unknown,fontSize=if(title=="Рециркуляция")11.sp else 13.sp,lineHeight=17.sp,textAlign=TextAlign.Center,maxLines=2,softWrap=title!="Рециркуляция")
    }
}

/** Compact square controls in both the fan selector and permanent dock. */
@Composable fun ClimateBarButton(icon:ImageVector?=null,text:String?=null,description:String,active:Boolean=false,enabled:Boolean=true,modifier:Modifier=Modifier,accent:Color=Jeland.colors.accent,onClick:()->Unit) {
    val c=Jeland.colors
    Box(modifier.height(56.dp).widthIn(min=52.dp).clip(Jeland.control).background(if(active&&enabled)c.deep.copy(alpha=.4f)else c.raised.copy(alpha=.35f))
        .border(1.dp,if(active&&enabled)accent else c.outline,Jeland.control).semantics {contentDescription=description;selected=active;if(!enabled)disabled()}
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=9.dp),contentAlignment=Alignment.Center) {
        val tint=if(!enabled)c.unknown else if(active)accent else c.text
        if(icon!=null)Icon(icon,null,Modifier.size(27.dp),tint=tint)else Text(text.orEmpty(),fontSize=16.sp,color=tint)
    }
}

