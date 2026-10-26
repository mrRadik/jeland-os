package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.disabled
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.NavigationState
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

val LocalFramelessDashboard=staticCompositionLocalOf {false}

@Composable fun Panel(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    if(LocalFramelessDashboard.current){Column(modifier,content=content);return}
    val c=Jeland.colors
    if(Jeland.style==ru.jeland.os.core.DashboardStyle.MINIMAL) {
        Column(modifier.clip(Jeland.card).background(c.panel)
            .background(Brush.linearGradient(listOf(c.raised.copy(alpha=.62f),c.panel,c.raised.copy(alpha=.18f))))
            .border(1.dp,Brush.linearGradient(listOf(c.accent.copy(alpha=.32f),c.outline.copy(alpha=.3f),c.accent.copy(alpha=.22f))),Jeland.card),content=content)
        return
    }
    Column(modifier.clip(Jeland.card).background(c.panel).background(Brush.verticalGradient(listOf(c.raised.copy(alpha=.55f),c.panel)))
        .border(1.dp,c.outline,Jeland.card),content=content)
}
@Composable fun Label(text: String, modifier: Modifier=Modifier, small: Boolean=false, muted: Boolean=false) {
    Text(text,modifier,color=if(muted) Jeland.colors.muted else Jeland.colors.text,style=if(small) Jeland.caption else Jeland.body)
}
@Composable fun Glyph(icon: ImageVector, description: String?=null, modifier: Modifier=Modifier, active: Boolean=false) {
    Icon(icon,description,modifier.size(27.dp),tint=if(active) Jeland.colors.accent else Jeland.colors.muted)
}
@Composable fun Control(text: String, modifier: Modifier=Modifier, selected: Boolean=false, accent:Color=Jeland.colors.accent, enabled:Boolean=true, onClick: ()->Unit) {
    val c=Jeland.colors
    Box(modifier.heightIn(min=50.dp).alpha(if(enabled)1f else .4f).clip(Jeland.control).background(if(selected)c.deep.copy(alpha=.34f) else c.raised.copy(alpha=.45f))
        .border(1.dp,if(selected)accent else c.outline,Jeland.control).semantics {this.selected=selected}.clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(horizontal=Jeland.space.inset,vertical=Jeland.space.pair),contentAlignment=Alignment.Center) {
        Text(text,color=if(selected)accent else c.text,fontSize=Jeland.type.control)
    }
}
/** Whole row is the touch target; native switch semantics work with accessibility/tests. */
@Composable fun SettingsToggle(title:String,detail:String,checked:Boolean,modifier:Modifier=Modifier,onToggle:()->Unit) {
    val c=Jeland.colors
    val position by animateFloatAsState(if(checked)1f else 0f,tween(Jeland.animation.quick),label="toggle-position")
    Row(modifier.fillMaxWidth().heightIn(min=82.dp).toggleable(checked,role=Role.Switch,onValueChange={onToggle()}).padding(vertical=14.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Label(title)
            Label(detail,small=true,muted=true)
        }
        Canvas(Modifier.size(52.dp,30.dp)) {
            drawRoundRect(if(checked)c.deep.copy(alpha=.5f)else c.outline,cornerRadius=androidx.compose.ui.geometry.CornerRadius(size.height/2))
            drawCircle(if(checked)c.accent else c.muted,10.dp.toPx(),Offset(15.dp.toPx()+position*22.dp.toPx(),size.height/2))
        }
    }
}
@Composable fun RoundControl(icon: ImageVector, description: String, modifier: Modifier=Modifier, active: Boolean=false, accent:Color=Jeland.colors.accent, enabled:Boolean=true, spin:Boolean=false, spinSpeed:Int=0, onClick: ()->Unit) {
    val c=Jeland.colors
    val rotation=fanRotation(spin&&enabled,spinSpeed)
    Box(modifier.size(52.dp).clip(CircleShape).background(if(active&&enabled)c.deep.copy(alpha=.45f)else c.raised.copy(alpha=.65f))
        .border(1.dp,if(active&&enabled)accent else c.outline,CircleShape).clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Icon(icon,description,Modifier.size(27.dp).semantics {if(icon==JIcons.Fan)stateDescription=if(spin)"Вращается"else"Остановлен"}.graphicsLayer {rotationZ=rotation},tint=if(!enabled)c.unknown.copy(alpha=.4f)else if(active)accent else c.muted)
    }
}
@Composable fun SectionTitle(title: String, subtitle: String?=null) {
    Text(title,color=Jeland.colors.text,style=Jeland.heading)
    if(subtitle!=null) Text(subtitle,color=Jeland.colors.muted,style=Jeland.caption,modifier=Modifier.padding(top=Jeland.space.compact,bottom=Jeland.space.pair))
}
@Composable fun MockMap(state: NavigationState, modifier: Modifier=Modifier) {
    val c=Jeland.colors
    Canvas(modifier.clipToBounds().background(c.map)) {
        val w=size.width; val h=size.height; val zoom=state.zoom
        fun xy(x: Float,y: Float)=Offset(w/2+(x-.5f)*w*zoom,h/2+(y-.5f)*h*zoom)
        for(i in -3..8) {
            val f=i/6f
            drawLine(c.road.copy(alpha=.55f),xy(f,-.3f),xy(f-.7f,1.3f),8f)
            drawLine(c.road.copy(alpha=.4f),xy(-.2f,f),xy(1.2f,f+.5f),6f)
        }
        val river=Path().apply { moveTo(w*.9f,0f); cubicTo(w*.3f,h*.25f,w*1.2f,h*.7f,w*.65f,h) }
        drawPath(river,c.deep.copy(alpha=.2f),style=androidx.compose.ui.graphics.drawscope.Stroke(w*.09f))
        val route=Path().apply { moveTo(w*.5f,h*.83f); lineTo(w*.63f,h*.53f); lineTo(w*.48f,h*.4f); lineTo(w*.65f,-h*.1f) }
        val routeColor=if(state.active)c.accent else c.road
        drawPath(route,routeColor.copy(alpha=.12f),style=androidx.compose.ui.graphics.drawscope.Stroke(24f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(route,c.deep,style=androidx.compose.ui.graphics.drawscope.Stroke(9f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        drawPath(route,routeColor,style=androidx.compose.ui.graphics.drawscope.Stroke(3f,cap=StrokeCap.Round,join=StrokeJoin.Round))
        val arrow=Path().apply { moveTo(w*.5f,h*.65f);lineTo(w*.46f,h*.82f);lineTo(w*.5f,h*.79f);lineTo(w*.54f,h*.82f);close() }
        drawPath(arrow,routeColor); drawPath(arrow,c.artText,style=androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}
fun timeString(seconds: Int)="${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"

/** Custom native slider with touch, drag and accessibility progress semantics. */
@Composable fun JelandSlider(value:Float,onValueChange:(Float)->Unit,valueRange:ClosedFloatingPointRange<Float>,modifier:Modifier=Modifier,enabled:Boolean=true,onValueChangeFinished:(()->Unit)?=null,gradient:Brush?=null,onValueChangeCanceled:(()->Unit)?=null) {
    val c=Jeland.colors
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val isEnabled by rememberUpdatedState(enabled)
    val cancel by rememberUpdatedState(onValueChangeCanceled)
    val gradientInset=with(LocalDensity.current){18.dp.toPx()}
    val fraction=((value-valueRange.start)/(valueRange.endInclusive-valueRange.start)).coerceIn(0f,1f)
    fun fromX(x:Float,width:Float):Float {
        val fraction=if(gradient==null)x/width else (x-gradientInset)/(width-2*gradientInset).coerceAtLeast(1f)
        return valueRange.start+fraction.coerceIn(0f,1f)*(valueRange.endInclusive-valueRange.start)
    }
    Canvas(modifier.fillMaxWidth().height(48.dp).semantics {
        progressBarRangeInfo=ProgressBarRangeInfo(value,valueRange)
        if(!enabled)disabled()
        setProgress {if(isEnabled){change(it.coerceIn(valueRange.start,valueRange.endInclusive));finish?.invoke();true}else false}
    }.pointerInput(valueRange,enabled) {if(enabled)detectTapGestures {if(isEnabled){change(fromX(it.x,size.width.toFloat()));finish?.invoke()}}}
        .pointerInput(valueRange,enabled) {if(enabled)detectDragGestures(onDragEnd={if(isEnabled)finish?.invoke()},onDragCancel={cancel?.invoke()}) {event,_->if(isEnabled){event.consume();change(fromX(event.position.x,size.width.toFloat()))}}}) {
        val y=size.height/2;val inset=if(gradient==null)7.dp.toPx()else gradientInset;val length=size.width-2*inset
        if(gradient!=null) {
            val center=Offset(inset+length*fraction,y)
            if(enabled)drawLine(gradient,Offset(inset,y),Offset(size.width-inset,y),14.dp.toPx(),cap=StrokeCap.Round)
            else drawLine(c.unknown.copy(alpha=.35f),Offset(inset,y),Offset(size.width-inset,y),14.dp.toPx(),cap=StrokeCap.Round)
            drawCircle(c.accent.copy(alpha=if(enabled).18f else .05f),18.dp.toPx(),center)
            drawCircle(Color(0xFFF0F6FA),12.dp.toPx(),center)
            drawCircle(if(enabled)c.accent else c.unknown,12.dp.toPx(),center,style=androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            return@Canvas
        }
        drawLine(c.outline,Offset(inset,y),Offset(size.width-inset,y),3.dp.toPx(),cap=StrokeCap.Round)
        val tint=if(enabled)c.accent else c.unknown.copy(alpha=.35f)
        drawLine(tint,Offset(inset,y),Offset(inset+length*fraction,y),3.dp.toPx(),cap=StrokeCap.Round)
        drawCircle(tint.copy(alpha=.13f),12.dp.toPx(),Offset(inset+length*fraction,y))
        drawCircle(tint,5.dp.toPx(),Offset(inset+length*fraction,y))
    }
}
