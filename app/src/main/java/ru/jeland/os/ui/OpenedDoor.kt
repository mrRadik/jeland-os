package ru.jeland.os.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import ru.jeland.os.core.*
import kotlin.math.cos
import kotlin.math.sin

/** Physical hinge, curved painted outer skin, glass, trim and handle in the car's stage. */
internal fun DrawScope.drawOpenedDoor(g:VehicleDiagramGeometry,opening:VehicleOpening,amount:Float,accent:Color) {
    val front=opening==VehicleOpening.FRONT_LEFT||opening==VehicleOpening.FRONT_RIGHT
    val left=opening==VehicleOpening.FRONT_LEFT||opening==VehicleOpening.REAR_LEFT
    val side=if(left)1f else -1f
    val hinge=Offset(g.px(VehicleArtworkCoordinates.doorHinge(front)),g.py(if(left).80f else .20f))
    val angle=amount*1.10f
    val length=g.width*(if(front).175f else .155f)
    val axis=Offset(cos(angle),sin(angle)*side)
    val normal=Offset(-axis.y,axis.x)
    fun p(x:Float,y:Float)=hinge+axis*(x*length)+normal*(y*length*side)
    fun Path.to(v:Offset)=lineTo(v.x,v.y)
    val a=p(0f,0f);val b=p(.91f,0f);val tip=p(1f,-.065f);val edge=p(.87f,-.18f);val inner=p(.03f,-.14f)
    val skin=Path().apply {
        moveTo(a.x,a.y);to(b);quadraticBezierTo(tip.x,tip.y,edge.x,edge.y);to(inner);close()
    }
    drawPath(skin,Color.Black.copy(alpha=.6f),style=Stroke(g.height*.07f))
    drawPath(skin,Brush.linearGradient(listOf(Color(0xFF7B8E9C),Color(0xFF17222D),Color(0xFF060D14)),a,p(0f,-.22f)))
    drawPath(skin,Color(0xFFB8C6D1).copy(alpha=.85f),style=Stroke((g.width*.0025f).coerceAtLeast(1f)))
    val glass=Path().apply {val start=p(.13f,-.04f);moveTo(start.x,start.y);to(p(.85f,-.035f));to(p(.78f,-.115f));to(p(.16f,-.11f));close()}
    drawPath(glass,Brush.linearGradient(listOf(Color(0xFF466478),Color(0xFF07131E)),p(.15f,0f),p(.8f,-.15f)))
    drawPath(glass,Color(0xFF92ABB9).copy(alpha=.7f),style=Stroke((g.width*.0015f).coerceAtLeast(.7f)))
    drawLine(Color(0xFFD6E0E5),p(.66f,-.16f),p(.79f,-.16f),(g.width*.004f).coerceAtLeast(1.4f),StrokeCap.Round)
    drawLine(accent.copy(alpha=.7f),p(.12f,0f),p(.88f,0f),(g.width*.002f).coerceAtLeast(1f),StrokeCap.Round)
    drawCircle(Color(0xFFB4C7D1),g.width*.004f,hinge)
}
