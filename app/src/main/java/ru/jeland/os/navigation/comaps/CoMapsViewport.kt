package ru.jeland.os.navigation.comaps

import kotlin.math.roundToInt

internal data class CoMapsViewport(val top:Int,val right:Int,val bottom:Int,val followOffset:Int)

internal data class CoMapsFrameCrop(val left:Int,val top:Int,val width:Int,val height:Int)

/** Crop the unobscured map, keeping its lower guidance/vehicle area in a small cluster mirror. */
internal fun coMapsMirrorCrop(width:Int,height:Int,targetWidth:Int,targetHeight:Int,viewport:CoMapsViewport?):CoMapsFrameCrop? {
    if(width<=0||height<=0||targetWidth<=0||targetHeight<=0)return null
    val top=(viewport?.top?:0).coerceIn(0,height-1)
    val bottom=(viewport?.bottom?:height).coerceIn(top+1,height)
    val visibleWidth=(viewport?.right?:width).coerceIn(1,width)
    val visibleHeight=bottom-top
    val ratio=targetWidth.toDouble()/targetHeight
    val cropWidth=minOf(visibleWidth,(visibleHeight*ratio).roundToInt().coerceAtLeast(1))
    val cropHeight=minOf(visibleHeight,(cropWidth/ratio).roundToInt().coerceAtLeast(1))
    return CoMapsFrameCrop((visibleWidth-cropWidth)/2,bottom-cropHeight,cropWidth,cropHeight)
}

/** Reserve actual overlay bounds and keep the vehicle in the lower part of the remaining map. */
internal fun coMapsViewport(width:Int,height:Int,topOverlay:Int,bottomOverlay:Int,guidance:Boolean):CoMapsViewport? {
    if(width<=0||height<=0)return null
    var top=topOverlay.coerceIn(0,height)
    var coveredBottom=bottomOverlay.coerceIn(0,height)
    val budget=(height*.8).toInt()
    if(top+coveredBottom>budget) {
        val factor=budget.toDouble()/(top+coveredBottom)
        top=(top*factor).toInt();coveredBottom=(coveredBottom*factor).toInt()
    }
    val bottom=height-coveredBottom
    return CoMapsViewport(top,width,bottom,if(guidance)((bottom-top)*.10).toInt()else 0)
}
