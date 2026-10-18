package ru.jeland.os.core

/** The PNG is 1800x900. Artwork, moving panels and touch targets share this fitted rectangle. */
data class VehicleDiagramGeometry(val x:Float,val y:Float,val width:Float,val height:Float) {
    fun px(value:Float)=x+value*width
    fun py(value:Float)=y+value*height
    companion object {
        fun fit(width:Float,height:Float):VehicleDiagramGeometry {
            val w=minOf(width*.64f,height*2f/1.12f).coerceAtLeast(0f)
            val h=w/2f
            return VehicleDiagramGeometry((width-w)/2f,(height-h)/2f,w,h)
        }
    }
}
