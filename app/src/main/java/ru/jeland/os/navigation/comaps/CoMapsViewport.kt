package ru.jeland.os.navigation.comaps

internal data class CoMapsViewport(val top:Int,val right:Int,val bottom:Int,val followOffset:Int)

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
