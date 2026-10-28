package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import ru.jeland.os.ui.theme.Jeland

/** Same glyph layout twice: a thin, crisp theme-background outline under theme text.
 * No shadow, blur, plate or image treatment. Only the filled copy is accessible.
 * Modifier belongs to the foreground Text so its normal text/layout semantics survive.
 */
@Composable fun HeroOutlinedText(
    text:String,modifier:Modifier=Modifier,fontSize:TextUnit,
    fontWeight:FontWeight=FontWeight.Normal,letterSpacing:TextUnit=TextUnit.Unspecified,
    color:Color=Jeland.colors.text,outlined:Boolean=true,maxLines:Int=Int.MAX_VALUE,softWrap:Boolean=true,
    textAlign:TextAlign=TextAlign.Start
) {
    val style=TextStyle(fontSize=fontSize,lineHeight=fontSize*1.12f,fontWeight=fontWeight,letterSpacing=letterSpacing,textAlign=textAlign)
    if(!outlined) {
        Text(text,modifier=modifier,color=color,style=style,maxLines=maxLines,softWrap=softWrap,overflow=TextOverflow.Clip)
        return
    }
    // Stroke straddles the glyph boundary; the fill covers its inner half, leaving
    // only 0.65 dp outside. Keep small status copy delicate as well as large digits.
    val width=with(LocalDensity.current){1.3.dp.toPx()}
    Box {
        Text(text,modifier=Modifier.matchParentSize().clearAndSetSemantics {},color=Jeland.colors.background,
            style=style.copy(drawStyle=Stroke(width=width,join=StrokeJoin.Round)),maxLines=maxLines,softWrap=softWrap)
        Text(text,modifier=modifier,color=color,style=style,maxLines=maxLines,softWrap=softWrap,overflow=TextOverflow.Clip)
    }
}

/** Fit the entire value into its real slot, including the head unit's font scale. */
@Composable fun FittedHeroText(text:String,modifier:Modifier=Modifier,fontSize:TextUnit,
    fontWeight:FontWeight=FontWeight.Normal,letterSpacing:TextUnit=TextUnit.Unspecified,
    outlined:Boolean=true,alignment:Alignment=Alignment.CenterStart) {
    val measurer=rememberTextMeasurer();val density=LocalDensity.current
    val baseStyle=LocalTextStyle.current.merge(TextStyle(fontSize=fontSize,lineHeight=fontSize*1.12f,fontWeight=fontWeight,letterSpacing=letterSpacing))
    BoxWithConstraints(modifier.semantics(mergeDescendants=true) {}) {
        val width=constraints.maxWidth;val height=constraints.maxHeight
        val fitted=remember(text,baseStyle,width,height,density) {
            val limits=Constraints(maxWidth=(width-2).coerceAtLeast(1),maxHeight=(height-2).coerceAtLeast(1))
            var low=fontSize.value*.1f;var high=fontSize.value
            repeat(12) {
                val value=(low+high)/2
                val measured=measurer.measure(text,style=baseStyle.copy(fontSize=value.sp,lineHeight=value.sp*1.12f),
                    maxLines=1,softWrap=false,constraints=limits)
                if(measured.hasVisualOverflow)high=value else low=value
            }
            low.sp
        }
        Box(Modifier.align(alignment).width(with(density){width.toDp()})) {
            HeroOutlinedText(text,Modifier.fillMaxWidth(),fontSize=fitted,fontWeight=fontWeight,letterSpacing=letterSpacing,
                outlined=outlined,maxLines=1,softWrap=false,textAlign=if(alignment==Alignment.Center)TextAlign.Center else TextAlign.Start)
        }
    }
}
