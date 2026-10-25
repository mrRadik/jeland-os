package ru.jeland.os.navigation

import kotlin.math.*

data class RoutePreviewWindow(val points:List<GeoPoint>,val center:GeoPoint,val zoom:Double,val aheadMeters:Double)

/** A bounded, actual piece of the route near the current position, not the whole trip. */
fun routePreviewWindow(geometry:List<Pair<Double,Double>>,position:GeoPoint?,distanceToManeuver:Int,width:Int,height:Int,bearing:Double=0.0):RoutePreviewWindow? {
    if(geometry.size<2)return null
    val points=geometry.map {GeoPoint(it.first,it.second)}
    val origin=position?:points.first()
    val scale=cos(Math.toRadians(origin.latitude)).coerceAtLeast(.01)
    fun xy(p:GeoPoint)=Pair((p.longitude-origin.longitude)*111320*scale,(p.latitude-origin.latitude)*111320)
    var best=Double.POSITIVE_INFINITY;var segment=0;var fraction=0.0
    for(i in 0 until points.lastIndex) {
        val (ax,ay)=xy(points[i]);val (bx,by)=xy(points[i+1]);val dx=bx-ax;val dy=by-ay
        val t=if(dx*dx+dy*dy>0)(-(ax*dx+ay*dy)/(dx*dx+dy*dy)).coerceIn(0.0,1.0)else 0.0
        val d=(ax+t*dx).pow(2)+(ay+t*dy).pow(2)
        if(d<best){best=d;segment=i;fraction=t}
    }
    fun interpolate(a:GeoPoint,b:GeoPoint,t:Double)=GeoPoint(a.latitude+(b.latitude-a.latitude)*t,a.longitude+(b.longitude-a.longitude)*t)
    val start=interpolate(points[segment],points[segment+1],fraction)
    val ahead=(distanceToManeuver.coerceAtLeast(0)+160.0).coerceIn(320.0,850.0)
    val selected=mutableListOf(start)
    var remaining=ahead
    for(i in segment+1..points.lastIndex) {
        val a=selected.last();val b=points[i]
        val (dx,dy)=xy(b);val (ax,ay)=xy(a);val length=hypot(dx-ax,dy-ay)
        if(length>remaining){selected+=interpolate(a,b,remaining/length);break}
        selected+=b;remaining-=length
        if(remaining<=0)break
    }
    // At the destination keep a real short segment instead of an empty line.
    if(selected.size<2)selected.add(0,points[segment])
    val all=if(position!=null&&best<250.0*250.0)selected+position else selected
    // Fit in the rotated camera's axes, not north-up bounds: on a wide widget an
    // eastbound road becomes vertical after turning the camera and needs more height.
    val angle=Math.toRadians(navigationBearing(bearing));val c=cos(angle);val s=sin(angle)
    val rotated=all.map {val (x,y)=xy(it);Pair(x*c-y*s,x*s+y*c)}
    val minX=rotated.minOf {it.first};val maxX=rotated.maxOf {it.first}
    val minY=rotated.minOf {it.second};val maxY=rotated.maxOf {it.second}
    val cx=(minX+maxX)/2;val cy=(minY+maxY)/2
    val center=GeoPoint(origin.latitude+(-cx*s+cy*c)/111320,origin.longitude+(cx*c+cy*s)/(111320*scale))
    val metersX=max(220.0,maxX-minX+140)
    val metersY=max(220.0,maxY-minY+140)
    val metersPerPixel=max(metersX/max(80,width-48),metersY/max(80,height-90))
    val zoom=log2(78271.51696*scale/metersPerPixel).coerceIn(10.0,17.5)
    return RoutePreviewWindow(selected,center,zoom,ahead)
}

/** Polygon containment including courtyards. Only use an address from the tapped footprint. */
internal fun insideFootprint(point:GeoPoint,rings:List<List<GeoPoint>>):Boolean {
    fun inside(ring:List<GeoPoint>):Boolean {
        var result=false
        for(i in ring.indices) {
            val a=ring[i];val b=ring[(i+1)%ring.size]
            if((a.latitude>point.latitude)!=(b.latitude>point.latitude)&&
                point.longitude<(b.longitude-a.longitude)*(point.latitude-a.latitude)/(b.latitude-a.latitude)+a.longitude)result=!result
        }
        return result
    }
    return rings.isNotEmpty()&&inside(rings.first())&&rings.drop(1).none(::inside)
}
