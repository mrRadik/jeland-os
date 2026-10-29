package ru.jeland.os.navigation

import kotlin.math.*
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
