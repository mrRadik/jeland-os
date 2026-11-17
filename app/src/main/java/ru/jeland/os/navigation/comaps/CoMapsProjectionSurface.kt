package ru.jeland.os.navigation.comaps

import app.organicmaps.sdk.JelandMapView
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import ru.jeland.os.ui.theme.Jeland

/** CoMaps owns one GL engine. The GU keeps touch ownership; the PP shares its frames. */
internal var mainMapSurface by mutableStateOf<JelandMapView?>(null)

@Composable internal fun CoMapsProjectionMirror(source:JelandMapView,modifier:Modifier) {
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
    if(current!=null)Image(current.asImageBitmap(),null,modifier,contentScale=ContentScale.Crop)
    else Box(modifier.background(Jeland.colors.map))
}
