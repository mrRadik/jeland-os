package ru.jeland.os

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

class ClusterAllIndicatorsInstrumentation {
    @Test fun fixedFooterIconsHaveNoBackdropWhileAdditionalIconsKeepContrast() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val assets=ClusterOemAssets(app.resources)
        val strip=ClusterWarningStrip(assets)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
        val plain=Bitmap.createBitmap(2048,768,Bitmap.Config.ARGB_8888)
        val overlay=Bitmap.createBitmap(2048,768,Bitmap.Config.ARGB_8888)
        val fixed=ClusterIndicatorCatalog.definitions.filter {it.bit in setOf(38,42,45)}
        val additional=ClusterIndicatorCatalog.definitions.single {it.bit==24}
        try {
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION))for(dark in listOf(true,false)) {
                val tint=if(dark)Color.WHITE else Color.BLACK
                plain.eraseColor(Color.TRANSPARENT)
                overlay.eraseColor(Color.TRANSPARENT)
                val panel=ClusterPanelState(indicators=fixed)
                strip.drawIndicators(Canvas(overlay),panel,tint,labels,design,backdrop=true,dark=dark)
                assertFalse("Footer icons must be drawn",plain.sameAs(overlay))
                strip.drawIndicators(Canvas(plain),panel,tint,labels,design)
                assertTrue("Footer artwork differs with the overlay in $design (dark=$dark)",plain.sameAs(overlay))

                val overflow=ClusterPanelState(indicators=listOf(additional))
                strip.drawIndicators(Canvas(overlay),overflow,tint,labels,design,backdrop=true,dark=dark)
                strip.drawIndicators(Canvas(plain),overflow,tint,labels,design)
                assertFalse("Additional indicators need a contrast backdrop",plain.sameAs(overlay))
            }
        } finally {
            plain.recycle()
            overlay.recycle()
            assets.close()
        }
    }

    @Test fun everyNamedStateHasItsOemAssetAndRendersAcrossAllThreeDesigns() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val assets=ClusterOemAssets(app.resources)
        val folder=File(app.getExternalFilesDir(null),"qa-all-cluster-indicators").apply {mkdirs()}
        val source=app.assets.open("cluster/oem/native-indicators.json").bufferedReader().use {JSONObject(it.readText())}
        assertEquals(130,source.getInt("named_slots"));assertEquals(126,source.getInt("distinct_signals"))
        val provenance=app.assets.open("cluster/oem/provenance.json").bufferedReader().use {JSONObject(it.readText()).getJSONArray("files")}
        val hashes=(0 until provenance.length()).associate {i->provenance.getJSONObject(i).let {"cluster/oem/"+it.getString("file") to it.getString("sha256")}}
        for(icon in ClusterIndicatorCatalog.definitions)if(icon.asset!=null) {
            val raw=app.assets.open(icon.asset).use {it.readBytes()}
            val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it.toInt() and 255)}
            assertEquals(icon.key,hashes[icon.asset],hash)
            val bitmap=assets.bitmap(icon.asset)
            assertTrue(icon.key,bitmap.width>0&&bitmap.height>0)
        }
        val unique=ClusterIndicatorCatalog.definitions.distinctBy {it.key}
        val atlas=Bitmap.createBitmap(1792,1080,Bitmap.Config.ARGB_8888)
        val c=Canvas(atlas);c.drawColor(Color.rgb(5,14,22))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.LTGRAY;textSize=11f;textAlign=Paint.Align.CENTER}
        for((i,icon) in unique.withIndex()) {
            val x=(i%14)*128f;val y=(i/14)*120f
            assets.drawNative(c,icon,x+28,y+7,72f)
            c.drawText("${icon.bit} · "+icon.key.removePrefix("LED_").take(22),x+64,y+102,paint)
        }
        File(folder,"all-126-states.png").outputStream().use {atlas.compress(Bitmap.CompressFormat.PNG,100,it)};atlas.recycle()
        val native=MutableList(49){0}.apply {
            this[39]=255;this[40]=255
            for(icon in unique)this[icon.bit/8]=this[icon.bit/8] or (1 shl(icon.bit%8))
        }
        val s=UiState(car=VehicleLinkState(demo=false,connected=true,
            clusterTelemetry=QnxClusterTelemetry(warnings=RawVehicleSample(native,19_900))))
        val all=ClusterPanelState.from(s,null,null,20_000).copy(speed=0.0,rpm=900.0,gear="P",coolant=12.0,
            odometer=1740.0,rangeKm=377.0,message=ClusterMessageCatalog.find(178))
        val renderer=ClusterRenderer(app.resources)
        val labels=ClusterLabels("км/ч","×1000 об/мин","км","м","л","л/100 км","С момента запуска","Нет маршрута")
        try {
            for(design in listOf(ClusterDesign.CLASSIC,ClusterDesign.SPORT,ClusterDesign.NAVIGATION)) {
                val bitmap=Bitmap.createBitmap(1920,720,Bitmap.Config.ARGB_8888)
                renderer.draw(Canvas(bitmap),all,design,true,"12:36",labels,0f,900f,miniature=true)
                File(folder,"all-${design.name.lowercase()}.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }finally {renderer.close();assets.close()}
    }
}
