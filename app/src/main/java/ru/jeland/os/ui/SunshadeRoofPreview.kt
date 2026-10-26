package ru.jeland.os.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.jeland.os.core.Shade
import ru.jeland.os.core.VehicleArtworkCoordinates
import ru.jeland.os.ui.theme.Jeland

/** Fraction revealed from the LEFT; fabric retracts to the RIGHT. Not measured position. */
internal fun shadeOpenFraction(shade:Shade?):Float?=when(shade){Shade.CLOSED->0f;Shade.HALF->.5f;Shade.OPEN->1f;null->null}
internal fun shadeFabricRange(openFraction:Float):ClosedFloatingPointRange<Float> = openFraction.coerceIn(0f,1f)..1f

@Composable fun SunshadeRoofPreview(shade:Shade?,modifier:Modifier=Modifier) {
    val c=Jeland.colors
    val target=shadeOpenFraction(shade)
    val amount by animateFloatAsState(target?:0f,tween(Jeland.animation.door),label="shade-opening-left-to-right")
    Column(modifier.fillMaxWidth().clip(Jeland.card).background(Brush.verticalGradient(listOf(c.raised.copy(alpha=.45f),c.panel)))) {
        Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,top=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("ПАНОРАМНАЯ КРЫША",color=c.muted,fontSize=12.sp,letterSpacing=1.3.sp)
            Text(if(target==null)"Положение неизвестно"else shade!!.title,Modifier.testTag("shade-state"),color=if(target==null)c.muted else c.accent,fontSize=14.sp)
        }
        // Reuse the same J7 artwork as the status card; no separate generic vehicle drawing.
        Box(Modifier.fillMaxWidth().aspectRatio(2f).testTag("shade-preview").semantics {
            contentDescription="Крыша Jeland J7. Шторка открывается слева направо"
            stateDescription=shade?.title?:"Положение неизвестно"
            if(target!=null)progressBarRangeInfo=ProgressBarRangeInfo(amount,0f..1f)
        }) {
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(c.accent.copy(alpha=.10f),Color.Transparent))))
            VehicleArtwork(Modifier.matchParentSize())
            Canvas(Modifier.matchParentSize()) {
                val roof=Rect(size.width*VehicleArtworkCoordinates.ROOF_LEFT,size.height*VehicleArtworkCoordinates.ROOF_TOP,
                    size.width*VehicleArtworkCoordinates.ROOF_RIGHT,size.height*VehicleArtworkCoordinates.ROOF_BOTTOM)
                drawSunshadeGlass(roof,amount,target!=null,c.accent,c.artText)
            }
        }
        Text("Открытие слева направо  →",Modifier.align(Alignment.CenterHorizontally).padding(bottom=14.dp),color=c.muted,fontSize=13.sp)
    }
}

/** The fabric and opening share the same roof coordinates in every vehicle view. */
internal fun DrawScope.drawSunshadeGlass(roof:Rect,openFraction:Float,known:Boolean,accent:Color,artText:Color) {
    if(!known)return // No response must not look like a closed or half-open shade.
    val amount=openFraction.coerceIn(0f,1f)
    val radius=CornerRadius(roof.height*.098f)
    val aperture=Path().apply {addRoundRect(RoundRect(roof,radius))}
    clipPath(aperture) {
        drawRect(Brush.linearGradient(listOf(accent.copy(alpha=.12f),Color.Transparent),roof.topLeft,roof.bottomRight),roof.topLeft,roof.size)
        if(amount<1f) {
            val edge=roof.left+roof.width*shadeFabricRange(amount).start
            drawRect(Brush.verticalGradient(listOf(Color(0xFF455564),Color(0xFF1D303E),Color(0xFF394D5C)),roof.top,roof.bottom),Offset(edge,roof.top),Size(roof.right-edge,roof.height))
            for(i in 0..64) {
                val x=edge+i*roof.width*.0167f
                if(x<roof.right)drawLine(Color.White.copy(alpha=.045f),Offset(x,roof.top),Offset(x,roof.bottom),1.dp.toPx())
            }
            drawLine(Color.Black.copy(alpha=.35f),Offset(edge+3.dp.toPx(),roof.top),Offset(edge+3.dp.toPx(),roof.bottom),5.dp.toPx())
            if(amount>.001f)drawLine(accent.copy(alpha=.9f),Offset(edge,roof.top),Offset(edge,roof.bottom),1.4.dp.toPx())
            drawRoundRect(artText.copy(alpha=.6f),Offset(edge+5.dp.toPx(),roof.center.y-7.dp.toPx()),Size(2.dp.toPx(),14.dp.toPx()),CornerRadius(1.dp.toPx()))
        }
    }
    drawRoundRect(accent.copy(alpha=.35f),roof.topLeft,roof.size,radius,style=Stroke(1.dp.toPx()))
}
