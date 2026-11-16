package ru.jeland.os

import android.graphics.PointF
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.ui.cluster.ClusterSegments

class ClusterCoolantScaleInstrumentation {
    @Test fun nativeProgressStopsInsideTheFinalBlockRatherThanRoundingUp() {
        // Actual 2026-11-11 native analog values. The installed OEM divides these by 1000.
        for(count in listOf(8,12))for(raw in listOf(0,8,70,183,220)) {
            val paths=ClusterSegments.build(count,10f,3f,raw/1000f){t->PointF(t*1000f,0f)}
            val bounds=paths.filterNot {it.isEmpty}.map {RectF().also {r->it.computeBounds(r,true)}}
            if(raw==0)assertTrue(bounds.isEmpty())else {
                assertFalse(bounds.isEmpty())
                val end=bounds.maxOf {it.right}
                assertTrue("count=$count raw=$raw end=$end",end<=raw+1f)
                assertTrue("The bar must not drop a partial segment",end>=raw-4f)
            }
        }
        val full=ClusterSegments.build(8,10f,3f){t->PointF(t*1000f,0f)}
        assertEquals(8,full.count {!it.isEmpty})
    }
}
