package ru.jeland.os.data

import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import ru.jeland.os.core.LauncherIconGeometry

/** Draw the whole Android drawable, including an adaptive icon's official background/mask.
 * Only transparent margins are normalized. White/coloured app backgrounds are never erased. */
object LauncherIconRenderer {
    const val SIZE=192
    fun render(drawable: Drawable,size: Int=SIZE): Bitmap {
        require(size in 32..512)
        val source=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        source.density=DisplayMetrics.DENSITY_DEFAULT
        val oldBounds=Rect(drawable.bounds)
        try {
            val box=LauncherIconGeometry.fit(drawable.intrinsicWidth.takeIf {it>0}?:size,
                drawable.intrinsicHeight.takeIf {it>0}?:size,size,1f)
            drawable.setBounds(box.left.toInt(),box.top.toInt(),box.right.toInt(),box.bottom.toInt())
            drawable.draw(Canvas(source))
        }finally{drawable.bounds=oldBounds}
        val pixels=IntArray(size*size);source.getPixels(pixels,0,size,0,0,size,size)
        var minX=size;var minY=size;var maxX=-1;var maxY=-1
        for(y in 0 until size)for(x in 0 until size)if((pixels[y*size+x] ushr 24)>8){
            minX=minOf(minX,x);minY=minOf(minY,y);maxX=maxOf(maxX,x);maxY=maxOf(maxY,y)
        }
        if(maxX<minX || maxY<minY){source.recycle();error("Drawable contains no visible pixels")}
        // Preserve the antialias fringe; never cut opaque corners or legitimate backgrounds.
        val crop=Rect((minX-1).coerceAtLeast(0),(minY-1).coerceAtLeast(0),(maxX+2).coerceAtMost(size),(maxY+2).coerceAtMost(size))
        val target=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        target.density=DisplayMetrics.DENSITY_DEFAULT
        val box=LauncherIconGeometry.fit(crop.width(),crop.height(),size)
        Canvas(target).drawBitmap(source,crop,RectF(box.left,box.top,box.right,box.bottom),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        source.recycle()
        return target
    }
}
