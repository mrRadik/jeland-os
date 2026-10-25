package ru.jeland.os.navigation

import kotlin.math.*
import ru.jeland.os.core.NavigationState
import ru.jeland.os.core.validCoordinates

private const val METERS_PER_DEGREE=111320.0
private fun longitudeDelta(value:Double)=((value+540.0)%360.0)-180.0
private fun distance(a:GeoPoint,b:GeoPoint):Double = hypot(
    longitudeDelta(b.longitude-a.longitude)*METERS_PER_DEGREE*cos(Math.toRadians((a.latitude+b.latitude)/2)),
    (b.latitude-a.latitude)*METERS_PER_DEGREE)
internal fun courseBetween(a:GeoPoint,b:GeoPoint):Double? {
    if(!validCoordinates(a.latitude,a.longitude)||!validCoordinates(b.latitude,b.longitude)||distance(a,b)<.5)return null
    val lat1=Math.toRadians(a.latitude);val lat2=Math.toRadians(b.latitude)
    val lon=Math.toRadians(b.longitude-a.longitude)
    return navigationBearing(Math.toDegrees(atan2(sin(lon)*cos(lat2),cos(lat1)*sin(lat2)-sin(lat1)*cos(lat2)*cos(lon))))
}

/** Course over ground, not a compass. Jitter/stationary zero bearings must not reset it to north. */
internal class NavigationCourseTracker {
    private var anchor:GeoPoint?=null
    private var anchorAt:Long?=null
    private var lastAt:Long?=null
    private var course:Double?=null
    fun reset(){anchor=null;anchorAt=null;lastAt=null;course=null}
    fun update(point:GeoPoint,at:Long,reported:Double?,speedMps:Double?,accuracy:Double?):Double? {
        if(!validCoordinates(point.latitude,point.longitude)||at<0||lastAt?.let {at<=it}==true)return course
        lastAt=at
        // A bad fix must not become the anchor for the next, accurate position.
        if(accuracy!=null&&(!accuracy.isFinite()||accuracy !in 0.0..50.0))return course
        val old=anchor
        val separation=old?.let {distance(it,point)}?:0.0
        val threshold=(accuracy?.takeIf {it.isFinite()&&it>=0}?.times(.5)?:5.0).coerceIn(5.0,15.0)
        val continuous=anchorAt?.let {at-it in 1..15_000}==true
        val moved=continuous&&separation>=threshold
        val validReported=reported?.takeIf {it.isFinite()}
        val moving=speedMps?.let {it.isFinite()&&it in 1.0..70.0}==true
        if(validReported!=null&&(moving||moved))course=navigationBearing(validReported)
        else if(moved)course=courseBetween(old!!,point)?:course
        if(old==null||!continuous||moved){anchor=point;anchorAt=at}
        return course
    }
}

/** Presentation fallback only: forward along the route, never bearing towards the next turn.
 * Remaining distance disambiguates overlapping/out-and-back legs; distant off-route fixes are rejected.
 */
internal fun routeForwardCourse(geometry:List<Pair<Double,Double>>,position:GeoPoint?,remainingMeters:Double?=null):Double? {
    if(geometry.size<2)return null
    val points=geometry.map {GeoPoint(it.first,it.second)}
    if(points.any {!validCoordinates(it.latitude,it.longitude)})return null
    if(position==null)return points.zipWithNext().firstNotNullOfOrNull {(a,b)->courseBetween(a,b)}
    if(!validCoordinates(position.latitude,position.longitude))return null
    val scale=cos(Math.toRadians(position.latitude)).coerceAtLeast(.01)
    fun xy(p:GeoPoint)=Pair(longitudeDelta(p.longitude-position.longitude)*METERS_PER_DEGREE*scale,(p.latitude-position.latitude)*METERS_PER_DEGREE)
    val lengths=DoubleArray(points.size)
    for(i in 1..points.lastIndex)lengths[i]=lengths[i-1]+distance(points[i-1],points[i])
    var best=Double.POSITIVE_INFINITY;var bestProgress=Double.POSITIVE_INFINITY;var chosen=-1
    for(i in 0 until points.lastIndex) {
        val (ax,ay)=xy(points[i]);val (bx,by)=xy(points[i+1]);val dx=bx-ax;val dy=by-ay
        val square=dx*dx+dy*dy;if(square<.25)continue
        val t=(-(ax*dx+ay*dy)/square).coerceIn(0.0,1.0)
        val separation=hypot(ax+t*dx,ay+t*dy)
        val progress=remainingMeters?.takeIf {it.isFinite()&&it>=0}?.let {abs(lengths.last()-(lengths[i]+t*(lengths[i+1]-lengths[i]))-it)}?:0.0
        if(separation<best-1.0||(abs(separation-best)<=1.0&&progress<bestProgress)) {
            best=separation;bestProgress=progress;chosen=i
        }
    }
    if(chosen<0||best>80.0)return null
    return courseBetween(points[chosen],points[chosen+1])
}

internal fun navigationCourse(state:NavigationState,position:GeoPoint?,gpsCourse:Double?):Double? =
    gpsCourse?.takeIf {it.isFinite()}?.let {navigationBearing(it)}?:
    if(state.active)routeForwardCourse(state.geometry,position,state.kilometers.takeIf {it>0}?.times(1000))else null
