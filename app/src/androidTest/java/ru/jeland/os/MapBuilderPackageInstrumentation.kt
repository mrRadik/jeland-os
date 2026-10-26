package ru.jeland.os

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.valhalla.valhalla.Valhalla
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.NavigationState
import ru.jeland.os.navigation.*
import java.io.File

/** Opt-in acceptance check for an externally generated package. Never changes the user's map. */
class MapBuilderPackageInstrumentation {
    @Test fun generatedPackageImportsRoutesAndRenders()=runBlocking<Unit> {
        val args=InstrumentationRegistry.getArguments()
        val packagePath=args.getString("mapPackage")
        assumeTrue("Pass mapPackage, mapFrom and mapTo to test a generated region",!packagePath.isNullOrBlank())
        val start=parseCoordinates(args.getString("mapFrom").orEmpty())!!
        val end=parseCoordinates(args.getString("mapTo").orEmpty())!!
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-map-builder-${java.util.UUID.randomUUID()}"
        val folder=File(base.cacheDir,name).apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(key:String,mode:Int)=base.getSharedPreferences("$name-$key",mode)
        }
        try {
            val maps=OfflineMapManager(context)
            val region=maps.importRegion(Uri.fromFile(File(packagePath!!)))
            assertTrue(region.contains(start));assertTrue(region.contains(end));assertTrue(region.houseNumberCount>0)
            val config=JSONObject(base.assets.open("navigation/default.json").bufferedReader().use {it.readText()})
            config.getJSONObject("mjolnir").apply {put("tile_extract",maps.graphFile.absolutePath);put("tile_dir",maps.directory.absolutePath);remove("tile_url")}
            val configFile=File(folder,"valhalla.json").apply {writeText(config.toString())}
            val actor=Valhalla(configFile.absolutePath)
            try {
                val request=JSONObject().put("costing","auto").put("format","osrm").put("language","ru-RU")
                    .put("locations",org.json.JSONArray().put(JSONObject().put("lat",start.latitude).put("lon",start.longitude))
                        .put(JSONObject().put("lat",end.latitude).put("lon",end.longitude)))
                val response=JSONObject(actor.routeRaw(request.toString()))
                assertEquals("Ok",response.getString("code"))
                assertTrue(response.getJSONArray("routes").getJSONObject(0).getDouble("distance")>10)
            }finally {actor.close()}
            val evidence=File(base.getExternalFilesDir(null),"qa-map-builder").apply {mkdirs()}
            withContext(Dispatchers.Main) {
                val renderer=WidgetMapSnapshot(base,720,480)
                try {
                    val preview=withTimeout(30000){renderer.render(maps,NavigationState(active=false,demo=false),start,0.0,true)}
                    val bitmap=preview.bitmap
                    val colors=mutableSetOf<Int>()
                    for(y in 0 until bitmap.height step 8)for(x in 0 until bitmap.width step 8)colors+=bitmap.getPixel(x,y)
                    assertTrue("Generated map is visibly rendered",colors.size>40)
                    File(evidence,"generated-region.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                }finally {renderer.cancel()}
            }
            assertTrue(maps.search(" ").isEmpty())
            File(evidence,"accepted.txt").writeText("${region.id}\nroute=$start → $end\nmapBytes=${maps.mapFile().length()}\nhouseNumbers=${region.houseNumberCount}\n")
        }finally {
            check(folder.canonicalFile.parentFile==base.cacheDir.canonicalFile&&folder.name==name)
            folder.deleteRecursively();base.deleteSharedPreferences("$name-navigation-regions")
        }
    }
}
