package ru.jeland.os

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.cluster.ClusterDisplayActivity
import ru.jeland.os.ui.cluster.*
import java.io.File
import kotlin.math.roundToInt

class ClusterStartupInstrumentation {
    @Test fun startupMovesNeedlesButKeepsDigitalReadingsAndHandlesThemeInPlace() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val info=context.packageManager.getActivityInfo(ComponentName(context,ClusterDisplayActivity::class.java),0)
        assertTrue(info.configChanges and ActivityInfo.CONFIG_UI_MODE!=0)
        assertTrue(info.configChanges and ActivityInfo.CONFIG_SCREEN_SIZE!=0)
        val renderer=ClusterRenderer(context.resources)
        val panel=ClusterPanelState(speed=90.0,rpm=1600.0,gear="D",rangeKm=375.0,fuel=.67)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
        val folder=File(context.getExternalFilesDir(null),"qa-cluster-118-startup").apply {mkdirs()}
        fun frame(design:ClusterDesign,age:Long):Bitmap {
            val image=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
            renderer.draw(Canvas(image),panel,design,true,"20:35",labels,90f,1600f,
                startup=ClusterStartupSweep.sample(age,90f,1600f))
            File(folder,"${design.name.lowercase()}-$age.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            return image
        }
        try {
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                val normal=frame(design,1600)
                for(age in listOf(0L,650L,1200L)) {
                    val image=frame(design,age)
                    var changed=0
                    for(y in 0 until 720 step 2)for(x in 0 until 1920 step 2)if(image.getPixel(x,y)!=normal.getPixel(x,y))changed++
                    assertTrue("$design $age must move needles",changed>200)
                    for(dial in ClusterGeometry.dials(design)) {
                        val cx=(dial.x*1920/2048).roundToInt();val cy=(dial.y*720/768).roundToInt()
                        for(y in cy+5..cy+22)for(x in cx-25..cx+25)
                            assertEquals("Digital values must stay live: $design $age ($x,$y)",normal.getPixel(x,y),image.getPixel(x,y))
                    }
                    image.recycle()
                }
                normal.recycle()
            }
        }finally {renderer.close()}
    }
}
