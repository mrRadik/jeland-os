package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class ClusterFieldVisualInstrumentation {
    @Test fun actualFieldPacketRendersInBothThemesWithCompactDoorNotification() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val text=instrumentation.context.assets.open("cluster-pps/field-warm.txt").bufferedReader().use {it.readText()}
        val native=ClusterPpsBatch.parse(text).telemetry(19_900)
        assertNull(native.error)
        val panel=ClusterPanelState.from(UiState(car=VehicleLinkState(demo=false,connected=true,clusterTelemetry=native)),null,null,20_000)
        assertEquals(800.0,panel.rpm!!,.001)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута",
            warnings=mapOf(ClusterLamp.DOOR to "Открыта дверь"))
        val folder=File(context.getExternalFilesDir(null),"qa-field-6184").apply {mkdirs()}
        val renderer=ClusterRenderer(context.resources)
        try {
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION))for(dark in listOf(true,false)) {
                val p=panel.copy(gear=ClusterGear.native(14),lamps=panel.lamps+ClusterLamp.DOOR,openings=setOf(VehicleOpening.FRONT_LEFT))
                val b=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(b),p,design,dark,"15:40",labels,p.speed?.toFloat(),p.rpm?.toFloat())
                File(folder,"${design.name.lowercase()}-${if(dark)"dark"else"light"}.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
                b.recycle()
            }
        }finally {renderer.close()}
    }
}
