package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.NavigationState
import ru.jeland.os.navigation.*
import java.io.File

class WidgetHeadingInstrumentation {
    @Test fun nativeSnapshotTurnsRoadWithCourseAndKeepsHeadingWhenCourseMissing()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val maps=OfflineMapManager(context)
        maps.prepare()
        val state=NavigationState(active=true,preview=true,distanceToManeuver=800,
            geometry=listOf(55.16 to 61.400,55.16 to 61.425))
        val point=GeoPoint(55.16,61.402)
        val folder=File(context.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
        // Find only the solid cyan route line, not water, labels or the car arrow.
        fun extent(bitmap:Bitmap):Pair<Int,Int> {
            var left=bitmap.width;var right=0;var top=bitmap.height;var bottom=0;var count=0
            for(y in 0 until bitmap.height)for(x in 0 until bitmap.width) {
                val color=bitmap.getPixel(x,y)
                if(Color.red(color)==32&&Color.green(color)==202&&Color.blue(color)==255) {
                    left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y);count++
                }
            }
            assertTrue("Native cyan route must render",count>100)
            return right-left to bottom-top
        }
        withContext(Dispatchers.Main) {
            val renderer=WidgetMapSnapshot(context,500,260)
            try {
                val north=withTimeout(30000){renderer.render(maps,state,point,0.0,true)}
                val east=withTimeout(30000){renderer.render(maps,state,point,90.0,true)}
                val held=withTimeout(30000){renderer.render(maps,state,point,null,true)}
                assertEquals(0.0,north.bearing,0.0);assertEquals(90.0,east.bearing,0.0);assertEquals(90.0,held.bearing,.02)
                val horizontal=extent(north.bitmap);val vertical=extent(east.bitmap)
                assertTrue("North-up: eastbound road is horizontal $horizontal",horizontal.first>horizontal.second*2)
                assertTrue("Course-up: same road turns vertical $vertical",vertical.second>vertical.first*2)
                for((name,preview) in listOf("widget-north" to north,"widget-east" to east,"widget-course-held" to held))
                    File(folder,"$name.png").outputStream().use {preview.bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }finally {renderer.cancel()}
        }
    }
}
