package ru.jeland.os.core

data class IconBox(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    val width get()=right-left
    val height get()=bottom-top
}
/** Uniform visible size; never stretch a rectangular icon to a square. */
object LauncherIconGeometry {
    fun fit(width: Int,height: Int,canvas: Int,occupancy: Float=.88f): IconBox {
        require(width>0 && height>0 && canvas>0 && occupancy>0f && occupancy<=1f)
        val scale=canvas*occupancy/maxOf(width,height)
        val w=width*scale;val h=height*scale
        return IconBox((canvas-w)/2,(canvas-h)/2,(canvas+w)/2,(canvas+h)/2)
    }
}
