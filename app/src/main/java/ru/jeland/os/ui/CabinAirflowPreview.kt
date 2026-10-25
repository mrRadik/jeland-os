package ru.jeland.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.jeland.os.R
import ru.jeland.os.core.AirOutlet
import ru.jeland.os.core.Airflow
import ru.jeland.os.ui.theme.Jeland
import kotlin.math.max

/** Decorative cabin render; only the separate flow layers represent current climate feedback. */
@Composable internal fun CabinAirflowPreview(mode:Airflow?,powered:Boolean,modifier:Modifier=Modifier) {
    val c=Jeland.colors
    val artwork=painterResource(R.drawable.climate_cabin_047)
    val source=artwork.intrinsicSize
    val active=if(powered)mode?.outlets.orEmpty()else emptySet()
    Box(modifier.clip(Jeland.control).background(Color(0xFF07131E))
        .border(1.dp,c.outline.copy(alpha=.65f),Jeland.control).testTag("climate-cabin")
        .semantics {
            contentDescription="Иллюстрация салона и распределение воздуха"
            stateDescription=when {mode==null->"Направление: нет данных";!powered->"Потоки выключены";else->mode.title}
        }) {
        Image(artwork,null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        // A subtle scrim seats the bitmap in the panel; do not recolor the real state indicators.
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF06121F).copy(alpha=.08f),Color.Transparent,Color(0xFF020C15).copy(alpha=.23f))))
        }
        active.forEach {outlet->
            Canvas(Modifier.matchParentSize().testTag("airflow-streams-${outlet.name.lowercase()}")) {
                // Use the same center-crop transform as Image. Anchors stay on vents at any size.
                val scale=max(size.width/source.width,size.height/source.height)
                val width=source.width*scale;val height=source.height*scale
                val origin=Offset((size.width-width)/2f,(size.height-height)/2f)
                fun at(x:Float,y:Float)=origin+Offset(width*x,height*y)
                val blue=Color(0xFF66DAFF)
                fun ribbon(x:Float,y:Float,endX:Float,endY:Float,spread:Float=.009f) {
                    val p=at(x,y);val end=at(endX,endY)
                    val ctrl1=at(x,y+(endY-y)*.40f)
                    val ctrl2=at(endX,y+(endY-y)*.72f)
                    val startWidth=width*.0018f;val endWidth=width*spread
                    val shape=Path().apply {
                        moveTo(p.x-startWidth,p.y)
                        cubicTo(ctrl1.x-startWidth,ctrl1.y,ctrl2.x-endWidth,ctrl2.y,end.x-endWidth,end.y)
                        lineTo(end.x+endWidth,end.y)
                        cubicTo(ctrl2.x+endWidth,ctrl2.y,ctrl1.x+startWidth,ctrl1.y,p.x+startWidth,p.y)
                        close()
                    }
                    drawPath(shape,Brush.linearGradient(listOf(blue.copy(alpha=.05f),blue.copy(alpha=.22f),blue.copy(alpha=0f)),p,end))
                    // Fine, fading strands rather than thick neon pipes or looping smoke.
                    for(strand in -1..1) {
                        val line=Path().apply {
                            moveTo(p.x+strand*startWidth,p.y)
                            cubicTo(ctrl1.x+strand*startWidth,ctrl1.y,ctrl2.x+strand*endWidth*.7f,ctrl2.y,end.x+strand*endWidth*.7f,end.y)
                        }
                        drawPath(line,Brush.linearGradient(listOf(blue.copy(alpha=.65f),blue.copy(alpha=.35f),Color.Transparent),p,end),
                            style=Stroke(.8.dp.toPx(),cap=StrokeCap.Round))
                    }
                }
                when(outlet) {
                    AirOutlet.FACE->{
                        ribbon(.169f,.380f,.116f,.820f)
                        ribbon(.440f,.478f,.400f,.845f)
                        ribbon(.565f,.478f,.650f,.845f)
                        ribbon(.828f,.380f,.884f,.820f)
                    }
                    AirOutlet.FEET->{
                        ribbon(.352f,.680f,.355f,.865f,.018f)
                        ribbon(.695f,.680f,.710f,.865f,.018f)
                    }
                    AirOutlet.WINDSHIELD->{
                        ribbon(.385f,.255f,.365f,.145f,.012f)
                        ribbon(.645f,.255f,.650f,.145f,.012f)
                        ribbon(.740f,.265f,.770f,.155f,.012f)
                    }
                }
            }
        }
    }
}
