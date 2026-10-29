package ru.jeland.os.navigation.comaps

import ru.jeland.os.navigation.GeoPoint
import ru.jeland.os.navigation.courseBetween
import kotlin.math.*

/** Debug playback of an engine-built route, never a routing engine or a source of real GPS. */
internal class CoMapsDemoRoute(geometry:List<Pair<Double,Double>>) {
    private val points=geometry.map {GeoPoint(it.second,it.first)} // native geometry is lon/lat
    private val ends=DoubleArray(points.size)
    init {
        for(i in 1..points.lastIndex) {
            val a=points[i-1];val b=points[i]
            val north=Math.toRadians(b.latitude-a.latitude)
            val east=Math.toRadians(b.longitude-a.longitude)
            val h=sin(north/2).pow(2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(east/2).pow(2)
            ends[i]=ends[i-1]+6371000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
        }
    }
    val length get()=ends.lastOrNull()?:0.0
    fun sample(meters:Double):Pair<GeoPoint,Double>? {
        if(points.size<2||length<=0)return null
        val progress=meters.coerceIn(0.0,length)
        val index=(ends.indexOfFirst {it>progress}.takeIf {it>0}?:points.lastIndex)
        val a=points[index-1];val b=points[index]
        val fraction=((progress-ends[index-1])/(ends[index]-ends[index-1]).coerceAtLeast(.01)).coerceIn(0.0,1.0)
        return GeoPoint(a.latitude+(b.latitude-a.latitude)*fraction,a.longitude+(b.longitude-a.longitude)*fraction) to (courseBetween(a,b)?:0.0)
    }
}
