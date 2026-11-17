package ru.jeland.os.navigation.comaps

import app.organicmaps.sdk.routing.ResultCodes
import ru.jeland.os.navigation.GeoPoint
import kotlin.math.*

data class CoMapsApproach(val requested:GeoPoint,val point:GeoPoint,val distanceMeters:Double)

/** Only native, successful vehicle routes supply approach points. Probe coordinates are never
 * accepted as a final destination: the nearest point on an internal road part is rebuilt first.
 * CoMaps index_router represents off-road connectors only at the head/tail of these routes.
 */
internal class CoMapsDestinationRecovery(private val target:GeoPoint,origin:GeoPoint) {
    sealed interface Step {
        data class Build(val point:GeoPoint):Step
        data class Accepted(val approach:CoMapsApproach):Step
        data object Exhausted:Step
    }
    private data class Choice(val point:GeoPoint,val offset:Double,val length:Double)
    private val radii=listOf(250.0,750.0,1500.0,3000.0)
    private val angles=listOf(0.0,45.0,-45.0,90.0,-90.0,135.0,-135.0,180.0)
    private val direction=atan2((origin.longitude-target.longitude)*cos(Math.toRadians(target.latitude)),origin.latitude-target.latitude)
    private var ring=0
    private var spoke=0
    private var verifying=false
    private var current:Choice?=null
    private val choices=mutableListOf<Choice>()
    private var verifyIndex=0
    private var finished=false
    var attempts=0;private set

    val isProbing:Boolean get()=!verifying&&!finished
    fun cancel(){finished=true}
    fun start():Step=next()
    fun failed():Step=if(finished)Step.Exhausted else next()
    fun built(points:List<GeoPoint>):Step {
        if(finished)return Step.Exhausted
        if(verifying) {
            val end=points.lastOrNull()
            val chosen=current
            // A late/foreign result must not confirm a different target.
            if(end!=null&&chosen!=null&&distance(end,chosen.point)<20.0) {
                finished=true
                return Step.Accepted(CoMapsApproach(target,chosen.point,chosen.offset))
            }
            return next()
        }
        closestRoadPoint(target,points)?.let {point->
            val offset=distance(target,point)
            if(offset in 1.0..MAX_OFFSET&&choices.none {distance(it.point,point)<10.0}) {
                val length=points.zipWithNext().sumOf {(a,b)->distance(a,b)}
                choices+=Choice(point,offset,length)
            }
        }
        return next()
    }
    private fun next():Step {
        if(finished)return Step.Exhausted
        if(verifying) {
            if(verifyIndex>=choices.size){finished=true;return Step.Exhausted}
            current=choices[verifyIndex++]
            attempts++
            return Step.Build(current!!.point)
        }
        if(spoke==angles.size){spoke=0;ring++}
        if(ring>=radii.size) {
            if(choices.isEmpty()){finished=true;return Step.Exhausted}
            choices.sortWith(compareBy<Choice>{it.offset}.thenBy {it.length})
            while(choices.size>8)choices.removeAt(choices.lastIndex)
            verifying=true
            return next()
        }
        val bearing=direction+Math.toRadians(angles[spoke++])
        val north=radii[ring]*cos(bearing);val east=radii[ring]*sin(bearing)
        val point=GeoPoint(target.latitude+Math.toDegrees(north/EARTH),
            target.longitude+Math.toDegrees(east/(EARTH*cos(Math.toRadians(target.latitude)).coerceAtLeast(.01))))
        if(!valid(point))return next()
        attempts++
        return Step.Build(point)
    }
    companion object {
        private const val EARTH=6_371_000.0
        const val MAX_OFFSET=5000.0
        fun eligible(code:Int,missingMaps:Boolean,guidanceStarted:Boolean,origin:GeoPoint?,target:GeoPoint?):Boolean =
            !missingMaps&&!guidanceStarted&&origin!=null&&target!=null&&valid(origin)&&valid(target)&&distance(origin,target)>50&&
                code in setOf(ResultCodes.END_POINT_NOT_FOUND,ResultCodes.ROUTE_NOT_FOUND)
        private fun valid(p:GeoPoint)=p.latitude.isFinite()&&p.longitude.isFinite()&&p.latitude in -85.0..85.0&&p.longitude in -180.0..180.0
        fun distance(a:GeoPoint,b:GeoPoint):Double {
            val dlat=Math.toRadians(b.latitude-a.latitude);val dlon=Math.toRadians(b.longitude-a.longitude)
            val h=(sin(dlat/2).pow(2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(dlon/2).pow(2)).coerceIn(0.0,1.0)
            return 2*EARTH*asin(sqrt(h))
        }
        internal fun closestRoadPoint(target:GeoPoint,geometry:List<GeoPoint>):GeoPoint? {
            if(geometry.size<3||geometry.any {!valid(it)})return null
            val points=geometry.fold(mutableListOf<GeoPoint>()){result,p->
                if(result.isEmpty()||distance(result.last(),p)>.25)result.add(p)
                result
            }
            // Endpoints and their off-road connectors are omitted. Every remaining piece
            // comes from the route's vehicle graph, not a guessed straight line to a POI.
            val road=points.drop(1).dropLast(1)
            if(road.isEmpty())return null
            val scale=cos(Math.toRadians(target.latitude)).coerceAtLeast(.01)
            val candidates=road.toMutableList()
            road.zipWithNext().forEach {(a,b)->
                val ax=(a.longitude-target.longitude)*scale;val ay=a.latitude-target.latitude
                val dx=(b.longitude-a.longitude)*scale;val dy=b.latitude-a.latitude
                val t=(-(ax*dx+ay*dy)/(dx*dx+dy*dy).coerceAtLeast(1e-20)).coerceIn(0.0,1.0)
                candidates+=GeoPoint(a.latitude+t*(b.latitude-a.latitude),a.longitude+t*(b.longitude-a.longitude))
            }
            return candidates.minByOrNull {distance(target,it)}
        }
    }
}
