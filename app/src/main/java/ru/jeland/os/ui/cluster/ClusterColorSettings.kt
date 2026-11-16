package ru.jeland.os.ui.cluster

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.jeland.os.core.*
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import ru.jeland.os.ui.Control
import ru.jeland.os.ui.Panel
import ru.jeland.os.ui.theme.Jeland
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ClusterColorSettings(prefs:InterfacePreferences,driveMode:ClusterDriveMode?,onChange:(InterfacePreferences)->Unit) {
    val c=Jeland.colors
    var picker by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Цвет подсветки",color=c.text,fontSize=Jeland.type.body)
        Row(Modifier.widthIn(max=520.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            for((mode,label,tag) in listOf(
                Triple(ClusterColorMode.MANUAL,"Вручную","cluster-color-manual"),
                Triple(ClusterColorMode.DRIVE_MODE,"По режиму авто","cluster-color-drive"))) {
                Control(label,Modifier.weight(1f).testTag(tag),selected=prefs.clusterColorMode==mode) {
                    onChange(prefs.copy(clusterColorMode=mode))
                }
            }
        }
        if(prefs.clusterColorMode==ClusterColorMode.MANUAL) {
            Box(Modifier.fillMaxWidth()) {
                Control("Выбрать цвет",Modifier.fillMaxWidth().testTag("cluster-color-picker-open")){picker=true}
                Box(Modifier.align(Alignment.CenterEnd).padding(end=18.dp).size(24.dp)
                    .clip(RoundedCornerShape(7.dp)).background(Color(prefs.clusterAccent.darkArgb))
                    .border(1.dp,c.text.copy(alpha=.2f),RoundedCornerShape(7.dp)))
            }
        }else {
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                for(mode in ClusterDriveMode.entries) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp).background(Color(mode.accent.darkArgb),CircleShape))
                        Text(mode.title,color=c.text,fontSize=Jeland.type.footnote)
                    }
                }
            }
            Text(if(driveMode==null)"Режим автомобиля пока не получен. Используется выбранный вручную цвет."
                else localized("Текущий режим")+": "+localized(driveMode.title),
                color=c.muted,fontSize=Jeland.type.footnote,modifier=Modifier.testTag("cluster-drive-mode-status"))
        }
    }
    if(picker)ClusterColorPicker(prefs.clusterAccent.darkArgb,onDismiss={picker=false}) {color->
        onChange(prefs.copy(clusterAccent=ClusterAccent.custom(color)));picker=false
    }
}

@Composable private fun ClusterColorPicker(initial:Int,onDismiss:()->Unit,onApply:(Int)->Unit) {
    val c=Jeland.colors
    var hsv by remember {mutableStateOf(FloatArray(3).also {AndroidColor.colorToHSV(initial,it)})}
    var hex by remember {mutableStateOf(String.format(Locale.ROOT,"%06X",initial and 0xffffff))}
    fun update(next:FloatArray) {hsv=next;hex=String.format(Locale.ROOT,"%06X",AndroidColor.HSVToColor(next) and 0xffffff)}
    val current=Color(AndroidColor.HSVToColor(hsv))
    val hue=Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0],1f,1f)))
    val paletteDescription=localized("Насыщенность и яркость")
    val hueDescription=localized("Оттенок")
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Panel(Modifier.padding(18.dp).widthIn(max=620.dp).fillMaxWidth().testTag("cluster-color-picker")) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Цвет приборной панели",color=c.text,fontSize=Jeland.type.heading)
                val latestHsv by rememberUpdatedState(hsv)
                val change by rememberUpdatedState<(Offset,Float,Float)->Unit>({point,width,height->
                    update(floatArrayOf(latestHsv[0],(point.x/width.coerceAtLeast(1f)).coerceIn(0f,1f),
                        (1f-point.y/height.coerceAtLeast(1f)).coerceIn(0f,1f)))
                })
                Canvas(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)).testTag("cluster-color-saturation")
                    .semantics {contentDescription=paletteDescription}
                    .pointerInput(Unit){detectTapGestures {change(it,size.width.toFloat(),size.height.toFloat())}}
                    .pointerInput(Unit){detectDragGestures(onDragStart={change(it,size.width.toFloat(),size.height.toFloat())}) {event,_->
                        event.consume();change(event.position,size.width.toFloat(),size.height.toFloat())
                    }}) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White,hue)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
                    val point=Offset(size.width*hsv[1],size.height*(1f-hsv[2]))
                    drawCircle(Color.Black,9.dp.toPx(),point)
                    drawCircle(Color.White,7.dp.toPx(),point)
                    drawCircle(current,5.dp.toPx(),point)
                }
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).align(Alignment.Center).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                    Slider(hsv[0],{update(floatArrayOf(it,hsv[1],hsv[2]))},valueRange=0f..359.999f,
                        modifier=Modifier.fillMaxWidth().testTag("cluster-color-hue").semantics {contentDescription=hueDescription},
                        colors=SliderDefaults.colors(thumbColor=current,activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent))
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(current).border(1.dp,c.outline,RoundedCornerShape(12.dp)))
                    OutlinedTextField(hex,{value->
                        hex=value.removePrefix("#").filter {it in "0123456789abcdefABCDEF"}.take(6).uppercase(Locale.ROOT)
                        if(hex.length==6)hsv=FloatArray(3).also {AndroidColor.colorToHSV(hex.toInt(16) or (0xff shl 24),it)}
                    },Modifier.weight(1f).testTag("cluster-color-hex"),singleLine=true,
                        label={Text("HEX")},prefix={Text("#")},isError=hex.length!=6)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Control("Отмена",Modifier.weight(1f)){onDismiss()}
                    Control("Применить",Modifier.weight(1f).testTag("cluster-color-apply"),selected=true,enabled=hex.length==6) {
                        onApply(hex.toInt(16) or (0xff shl 24))
                    }
                }
            }
        }
    }
}
