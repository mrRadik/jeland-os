package ru.jeland.os.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import ru.jeland.os.ui.theme.Jeland

/** Same glyph layout twice: a thin, crisp theme-background outline under theme text.
 * No shadow, blur, plate or image treatment. Only the filled copy is accessible.
 * Modifier belongs to the foreground Text so its normal text/layout semantics survive.
 */
@Composable fun HeroOutlinedText(
    text:String,modifier:Modifier=Modifier,fontSize:TextUnit,
    fontWeight:FontWeight=FontWeight.Normal,letterSpacing:TextUnit=TextUnit.Unspecified,
    color:Color=Jeland.colors.text,outlined:Boolean=true
) {
    val style=TextStyle(fontSize=fontSize,fontWeight=fontWeight,letterSpacing=letterSpacing)
    if(!outlined) {
        Text(text,modifier=modifier,color=color,style=style)
        return
    }
    // Stroke straddles the glyph boundary; the fill covers its inner half, leaving
    // only 0.65 dp outside. Keep small status copy delicate as well as large digits.
    val width=with(LocalDensity.current){1.3.dp.toPx()}
    Box {
        Text(text,modifier=Modifier.clearAndSetSemantics {},color=Jeland.colors.background,
            style=style.copy(drawStyle=Stroke(width=width,join=StrokeJoin.Round)))
        Text(text,modifier=modifier,color=color,style=style)
    }
}
