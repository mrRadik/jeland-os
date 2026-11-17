package ru.jeland.os.ui.cluster

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.hypot

/** Equal arc-length blocks with constant thickness, cut perpendicular to the track. */
internal object ClusterSegments {
    fun build(count:Int,thickness:Float,gap:Float,progress:Float=1f,curve:(Float)->PointF):List<Path> {
        val samples=160
        val points=List(samples+1){curve(it.toFloat()/samples)}
        val lengths=FloatArray(samples+1)
        for(i in 1..samples)lengths[i]=lengths[i-1]+hypot(points[i].x-points[i-1].x,points[i].y-points[i-1].y)
        val total=lengths.last()
        fun edge(distance:Float,side:Float):PointF {
            var lo=1;var hi=samples
            while(lo<hi){val middle=(lo+hi)/2;if(lengths[middle]<distance)lo=middle+1 else hi=middle}
            val a=points[lo-1];val b=points[lo]
            val length=(lengths[lo]-lengths[lo-1]).coerceAtLeast(.0001f)
            val f=((distance-lengths[lo-1])/length).coerceIn(0f,1f)
            val dx=(b.x-a.x)/length;val dy=(b.y-a.y)/length
            return PointF(a.x+(b.x-a.x)*f-dy*thickness*.5f*side,a.y+(b.y-a.y)*f+dx*thickness*.5f*side)
        }
        return List(count){index->
            val start=total*index/count+gap*.5f
            val end=minOf(total*(index+1)/count-gap*.5f,total*progress.coerceIn(0f,1f))
            Path().apply {
                if(end<=start)return@apply
                for(i in 0..8){val p=edge(start+(end-start)*i/8,1f);if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y)}
                for(i in 8 downTo 0){val p=edge(start+(end-start)*i/8,-1f);lineTo(p.x,p.y)}
                close()
            }
        }
    }
    fun sidePoint(fraction:Float,right:Boolean):PointF {
        val t=fraction.coerceIn(0f,1f);val u=1-t
        val x=u*u*111+2*u*t*67+t*t*111
        val y=u*u*236+2*u*t*395+t*t*565
        return PointF(if(right)2048-x else x,y)
    }
}
