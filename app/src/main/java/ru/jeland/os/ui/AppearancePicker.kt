package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.jeland.os.R
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*

/** Shared by first-run setup and Settings; thumbnails have no map engine or vehicle connection. */
@Composable fun AppearancePicker(state:UiState,model:MainViewModel,setup:Boolean=false) {
    Label("Оформление")
    Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        DashboardStyle.entries.forEach {style->
            AppearanceChoice(style.title,state.preferences.style==style,Modifier.weight(1f)
                .testTag(if(setup)"setup-style-${style.name}"else"style-${style.name.lowercase()}"),
                {model.dependencies.preferences.change(state.preferences.copy(style=style))}) {
                HomeLayoutThumbnail(style,state.dark,Modifier.height(240.dp).width(178.dp))
            }
        }
    }
    Label("Цветовая тема")
    Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        ThemeMode.entries.forEach {theme->
            AppearanceChoice(theme.title,state.theme==theme,Modifier.weight(1f)
                .testTag(if(setup)"setup-theme-${theme.name}"else"theme-${theme.name.lowercase()}"),
                {model.dependencies.theme.setMode(theme)}) {
                if(theme==ThemeMode.AUTO)Row(Modifier.height(180.dp).width(150.dp)) {
                    HomeLayoutThumbnail(state.preferences.style,false,Modifier.weight(1f).fillMaxHeight())
                    HomeLayoutThumbnail(state.preferences.style,true,Modifier.weight(1f).fillMaxHeight())
                }else HomeLayoutThumbnail(state.preferences.style,theme==ThemeMode.DARK,Modifier.height(180.dp).width(132.dp))
            }
        }
    }
    Label("Авто — день и ночь по настройкам автомобиля",small=true,muted=true)
}

@Composable private fun AppearanceChoice(title:String,selected:Boolean,modifier:Modifier,onClick:()->Unit,preview:@Composable ()->Unit) {
    val c=Jeland.colors
    Column(modifier.clip(Jeland.control).background(c.raised.copy(alpha=if(selected).65f else .3f))
        .border(if(selected)2.dp else 1.dp,if(selected)c.accent else c.outline,Jeland.control)
        .selectable(selected,role=Role.RadioButton,onClick=onClick).padding(12.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        preview()
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            RadioButton(selected,onClick=null,colors=RadioButtonDefaults.colors(selectedColor=c.accent,unselectedColor=c.muted))
            Spacer(Modifier.width(8.dp));Label(title,small=true)
        }
    }
}

/** The same artwork, palette and layout proportions as Home, drawn without interactive controls. */
@Composable private fun HomeLayoutThumbnail(style:DashboardStyle,dark:Boolean,modifier:Modifier) {
    val c=jelandPalette(dark,style)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(8.dp)).border(1.dp,c.outline,RoundedCornerShape(8.dp)).background(c.background)) {
        Canvas(Modifier.fillMaxSize()) {
            fun panel(x:Float,y:Float,w:Float,h:Float,fill:Color=c.panel) {
                drawRoundRect(fill,Offset(size.width*x,size.height*y),Size(size.width*w,size.height*h),CornerRadius(size.width*.015f))
            }
            panel(0f,0f,.13f,1f,c.rail)
            for(i in 0..5) {
                val y=size.height*(.09f+i*.125f)
                drawCircle(if(i==0)c.accent else c.muted,size.width*.015f,Offset(size.width*.062f,y))
                drawLine(c.muted.copy(alpha=.6f),Offset(size.width*.03f,y+size.height*.025f),Offset(size.width*.095f,y+size.height*.025f),size.height*.004f)
            }
            if(style==DashboardStyle.CLASSIC) {
                panel(.15f,.36f,.405f,.19f);panel(.57f,.36f,.405f,.19f)
                miniatureMap(Rect(size.width*.16f,size.height*.37f,size.width*.54f,size.height*.54f),c,dark)
                panel(.15f,.57f,.825f,.20f)
                for(i in 0..3) {panel(.16f+i*.205f,.79f,.19f,.07f);panel(.16f+i*.205f,.88f,.19f,.055f)}
            }else {
                miniatureMap(Rect(size.width*.16f,size.height*.06f,size.width*.72f,size.height*.31f),c,dark)
                panel(.77f,.15f,.20f,.12f)
                for(i in 0..3) {panel(.16f+i*.205f,.79f,.19f,.06f,c.raised);panel(.16f+i*.205f,.89f,.19f,.045f,c.raised)}
                for(y in listOf(.44f,.68f))for(x in listOf(.20f,.81f))drawLine(c.text,Offset(size.width*x,size.height*y),Offset(size.width*(x+.10f),size.height*y),size.height*.007f)
            }
            panel(.02f,.955f,.96f,.035f,c.raised)
            repeat(10){i->drawCircle(if(i==3)c.accent else c.muted,size.width*.013f,Offset(size.width*(.06f+i*.098f),size.height*.973f))}
        }
        if(style==DashboardStyle.CLASSIC) {
            Image(painterResource(heroBackgroundResource(dark)),null,Modifier.offset(maxWidth*.14f,0.dp).size(maxWidth*.86f,maxHeight*.34f),contentScale=ContentScale.Crop)
            VehicleArtwork(Modifier.offset(maxWidth*.34f,maxHeight*.60f).size(maxWidth*.46f,maxHeight*.14f))
        }else Image(painterResource(R.drawable.vehicle_top),null,
            Modifier.offset(maxWidth*.39f,maxHeight*.34f).size(maxWidth*.35f,maxHeight*.42f),contentScale=ContentScale.Fit)
    }
}

private fun DrawScope.miniatureMap(r:Rect,c:JelandColors,dark:Boolean) {
    drawRect(if(dark)c.map else Color(0xFFE4E2D9),r.topLeft,r.size)
    repeat(5) {i->
        val fraction=(i+1)/6f
        drawLine(if(dark)c.road else Color.White,Offset(r.left+r.width*fraction,r.top),Offset(r.left+r.width*fraction,r.bottom),size.width*.007f)
        drawLine(if(dark)c.road else Color.White,Offset(r.left,r.top+r.height*fraction),Offset(r.right,r.top+r.height*fraction),size.width*.007f)
    }
    val route=Path().apply {moveTo(r.left+r.width*.3f,r.bottom);lineTo(r.left+r.width*.3f,r.center.y);lineTo(r.left+r.width*.7f,r.center.y);lineTo(r.left+r.width*.7f,r.top)}
    drawPath(route,c.accent,style=androidx.compose.ui.graphics.drawscope.Stroke(size.width*.012f))
}
