package ru.jeland.os.core

/** Canonical 2:1 stage; the portrait J7 PNG is fitted and rotated inside it. */
data class VehicleDiagramGeometry(val x:Float,val y:Float,val width:Float,val height:Float) {
    fun px(value:Float)=x+value*width
    fun py(value:Float)=y+value*height
    companion object {
        fun fit(width:Float,height:Float,widthFraction:Float=.64f):VehicleDiagramGeometry {
            val w=minOf(width*widthFraction.coerceIn(.4f,1f),height*2f/1.12f).coerceAtLeast(0f)
            val h=w/2f
            return VehicleDiagramGeometry((width-w)/2f,(height-h)/2f,w,h)
        }
    }
}

/** Positions in the shared stage, measured on jeland-j7-top-black-v1 (846x1860).
 * Front points left; the lower edge is the vehicle's left side.
 */
object VehicleArtworkCoordinates {
    const val ROOF_LEFT=.476f
    const val ROOF_TOP=.345f
    const val ROOF_RIGHT=.747f
    const val ROOF_BOTTOM=.655f
    fun wheelLongitudinal(front:Boolean)=if(front).235f else .785f
    fun wheelLateral(left:Boolean)=if(left).85f else .15f
    fun doorHinge(front:Boolean)=if(front).42f else .60f
    const val DOOR_LENGTH=.115f
}
