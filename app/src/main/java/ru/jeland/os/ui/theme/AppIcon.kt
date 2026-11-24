package ru.jeland.os.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** Original HU drawable plus its visible bounds; the source image bytes stay untouched. */
data class AppIcon(@param:DrawableRes val resource:Int,val source:IconSource?=null,val preserveColors:Boolean=false)
data class IconSource(val left:Int,val top:Int,val right:Int,val bottom:Int)

@Composable fun appIconPainter(icon:AppIcon):Painter {
    val source=icon.source?:return painterResource(icon.resource)
    val bitmap=ImageBitmap.imageResource(icon.resource)
    return remember(bitmap,source) {BitmapPainter(bitmap,IntOffset(source.left,source.top),IntSize(source.right-source.left,source.bottom-source.top))}
}
