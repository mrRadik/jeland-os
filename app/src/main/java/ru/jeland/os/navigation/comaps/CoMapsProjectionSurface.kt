package ru.jeland.os.navigation.comaps

import app.organicmaps.sdk.JelandMapView
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import ru.jeland.os.ui.theme.Jeland

/** CoMaps owns one GL engine. The GU keeps touch ownership; the PP shares its frames. */
internal var mainMapSurface by mutableStateOf<JelandMapView?>(null)
internal var mainMapViewport by mutableStateOf<CoMapsViewport?>(null)

@Composable internal fun CoMapsProjectionMirror(source:JelandMapView,modifier:Modifier,compact:Boolean=false) {
    var frame by remember(source){mutableStateOf<Bitmap?>(null)}
    LaunchedEffect(source) {
        while(true) {
            if(source.isAvailable&&source.width>0&&source.height>0) {
                val width=minOf(source.width,1024)
                val height=(source.height.toLong()*width/source.width).toInt().coerceAtLeast(1)
                runCatching {source.getBitmap(width,height)}.getOrNull()?.let {frame=it}
            }
            delay(150)
        }
    }
    val current=frame
    if(current!=null&&compact) {
        val viewport=mainMapViewport
        val brush=remember {Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)}
        Canvas(modifier) {
            coMapsMirrorCrop(source.width,source.height,size.width.toInt(),size.height.toInt(),viewport)?.let {crop->
                val sx=current.width.toFloat()/source.width;val sy=current.height.toFloat()/source.height
                val rect=Rect((crop.left*sx).toInt(),(crop.top*sy).toInt(),((crop.left+crop.width)*sx).toInt(),((crop.top+crop.height)*sy).toInt())
                drawIntoCanvas {it.nativeCanvas.drawBitmap(current,rect,RectF(0f,0f,size.width,size.height),brush)}
            }
        }
    }
    else if(current!=null)Image(current.asImageBitmap(),null,modifier,contentScale=ContentScale.Crop)
    else Box(modifier.background(Jeland.colors.map))
}
