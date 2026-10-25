package ru.jeland.os.navigation

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** Immutable, verified local region packages. No downloads or network access. */
class OfflineMapManager(private val context:Context):MapDataRepository {
    private val root=File(context.filesDir,"navigation")
    private val prefs=context.getSharedPreferences("navigation-regions",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    private val mutableRegion=MutableStateFlow<RegionData?>(null)
    val region=mutableRegion.asStateFlow()
    val revision=MutableStateFlow(0)
    fun overviewFile()=File(root,"overview-custom.pmtiles").takeIf {it.exists()}?:File(root,"overview-bundled.pmtiles")
    var directory=File(root,prefs.getString("active","chelyabinsk-west-bundled")!!);private set
    override fun mapFile()=File(directory,if(File(directory,"map.pmtiles").exists())"map.pmtiles"else"map.geojson")
    val graphFile get()=File(directory,"valhalla_tiles.tar")
    private var searchCache:Pair<File,List<NavigationPlace>>?=null
    override suspend fun prepare():RegionData=withContext(Dispatchers.IO) {mutex.withLock {
        mutableRegion.value?.let {return@withLock it}
        root.mkdirs()
        val overviewManifest=JSONObject(context.assets.open("navigation/overview/russia-overview.json").bufferedReader().use {it.readText()})
        val overview=File(root,"overview-bundled.pmtiles")
        if(!overview.exists()||sha256(overview)!=overviewManifest.getString("sha256")) {
            val pending=File(root,"overview-bundled.partial")
            context.assets.open("navigation/overview/russia-overview.pmtiles").use {input->pending.outputStream().use {input.copyTo(it)}}
            check(sha256(pending)==overviewManifest.getString("sha256"))
            check(pending.renameTo(overview))
        }
        val previousDirectory=directory
        val upgradeLegacy=runCatching {
            directory.name!="chelyabinsk-west-bundled"&&File(directory,"manifest.json").exists()&&
                JSONObject(File(directory,"manifest.json").readText()).optString("id")=="chelyabinsk-west-20261019"&&
                sha256(File(directory,"map.pmtiles"))=="7fe598e2f2e8d7b04d93a58405f0ab74591d9d346aa71ada27c750d9de68fc5b"
        }.getOrDefault(false)
        if(directory.name=="chelyabinsk-west-bundled" || directory.name=="andorra-poc"||upgradeLegacy) {
            try {
            // Upgrade the old research fixture to the bundled, same-source regional data.
            directory=File(root,"chelyabinsk-west-bundled")
            directory.mkdirs()
            val manifestText=context.assets.open("navigation/bundled/manifest.json").bufferedReader().use {it.readText()}
            val manifest=JSONObject(manifestText)
            val files=manifest.getJSONArray("files")
            for(i in 0 until files.length()) {
                val item=files.getJSONObject(i);val name=item.getString("name")
                require(name in setOf("map.pmtiles","search.json","valhalla_tiles.tar"))
                val target=File(directory,name);val hash=item.getString("sha256")
                if(!target.exists()||sha256(target)!=hash) {
                    val pending=File(directory,"$name.partial")
                    context.assets.open("navigation/bundled/$name").use {input->pending.outputStream().use {input.copyTo(it)}}
                    check(sha256(pending)==hash){"Повреждён файл региона: $name"}
                    check(pending.renameTo(target)){"Не удалось активировать $name"}
                }
            }
            File(directory,"manifest.json").writeText(manifestText)
            readManifest(directory).also {
                if(upgradeLegacy)check(prefs.edit().putString("active",directory.name).commit())
                mutableRegion.value=it
            }
            }catch(error:Exception){directory=previousDirectory;throw error}
        }else readManifest(directory).also {mutableRegion.value=it}
    }}
    suspend fun installOverview(uri:Uri)=withContext(Dispatchers.IO) {mutex.withLock {
        val pending=File(root,"overview-${UUID.randomUUID()}.partial")
        try {
            root.mkdirs()
            context.contentResolver.openInputStream(uri).use {input->
                checkNotNull(input);var bytes=0L
                pending.outputStream().use {output->val block=ByteArray(65536)
                    while(true){val n=input.read(block);if(n<0)break;bytes+=n;require(bytes<=NavigationDownloads.MAX_BYTES);output.write(block,0,n)}
                }
            }
            validateOverview(pending)
            check(pending.renameTo(File(root,"overview-custom.pmtiles"))){"Не удалось установить обзорную карту"}
            revision.value++
        }finally {pending.delete()}
    }}
    private fun validateOverview(file:File) {
        java.io.RandomAccessFile(file,"r").use {input->
            val bytes=ByteArray(127);input.readFully(bytes)
            require(String(bytes,0,7,Charsets.US_ASCII)=="PMTiles"&&bytes[7].toInt()==3&&bytes[99].toInt()==1){"Нужен векторный PMTiles v3 Protomaps"}
            val header=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val offset=header.getLong(24);val length=header.getLong(32)
            require(offset>=127&&length in 1..1048576&&offset<=file.length()-length){"Повреждён заголовок PMTiles"}
            input.seek(offset);val metadata=ByteArray(length.toInt());input.readFully(metadata)
            val decoded=when(bytes[97].toInt()) {
                1->metadata
                2->java.util.zip.GZIPInputStream(metadata.inputStream()).use {stream->
                    val output=java.io.ByteArrayOutputStream();val block=ByteArray(4096)
                    while(true){val n=stream.read(block);if(n<0)break;require(output.size()+n<=4*1048576);output.write(block,0,n)};output.toByteArray()
                }
                else->error("Неподдерживаемое сжатие PMTiles")
            }
            val layers=JSONObject(String(decoded,Charsets.UTF_8)).getJSONArray("vector_layers")
            val names=(0 until layers.length()).map {layers.getJSONObject(it).getString("id")}.toSet()
            require(names.containsAll(setOf("earth","roads","places"))){"Схема карты не Protomaps: нужны earth/roads/places"}
        }
    }
    /** A failed import never replaces the working region. All names and sizes are bounded. */
    suspend fun importRegion(uri:Uri):RegionData=withContext(Dispatchers.IO) {mutex.withLock {
        root.mkdirs()
        val staging=File(root,"import-${UUID.randomUUID()}").apply {mkdirs()}
        try {
            val seen=mutableSetOf<String>();var total=0L
            context.contentResolver.openInputStream(uri).use {stream->
                requireNotNull(stream){"Не удалось открыть пакет"}
                ZipInputStream(stream).use {zip->
                    while(true) {
                        val entry=zip.nextEntry?:break
                        val name=entry.name
                        require(!entry.isDirectory&&name in FILES&&seen.add(name)){"Неожиданный/повторный файл: $name"}
                        val limit=when(name){"manifest.json"->65536L;"search.json"->32L*1048576;"map.geojson","map.pmtiles"->128L*1048576;else->400L*1048576}
                        var count=0L
                        File(staging,name).outputStream().use {output->
                            val block=ByteArray(65536)
                            while(true){val n=zip.read(block);if(n<0)break;count+=n;total+=n
                                require(count<=limit&&total<=512L*1048576){"Слишком большой пакет региона"};output.write(block,0,n)}
                        }
                        zip.closeEntry()
                    }
                }
            }
            val info=readManifest(staging)
            val manifest=JSONObject(File(staging,"manifest.json").readText())
            val mapName=if(manifest.getInt("format")==2)"map.pmtiles"else"map.geojson"
            val expected=setOf("manifest.json",mapName,"search.json","valhalla_tiles.tar")
            require(seen==expected){"Неполный пакет картографических данных"}
            val items=manifest.getJSONArray("files");val declared=mutableSetOf<String>()
            require(items.length()==3)
            for(i in 0 until items.length()) {
                val item=items.getJSONObject(i);val name=item.getString("name")
                require(name in FILES&&name!="manifest.json"&&declared.add(name))
                val file=File(staging,name)
                require(file.length()==item.getLong("bytes")&&sha256(file)==item.getString("sha256")){"Проверка целостности не пройдена: $name"}
            }
            require(declared==expected-setOf("manifest.json"))
            if(mapName=="map.pmtiles") {
                File(staging,mapName).inputStream().use {input->
                    val header=ByteArray(127);require(input.read(header)==127&&String(header,0,7,Charsets.US_ASCII)=="PMTiles"&&header[7].toInt()==3){"Неверный PMTiles v3"}
                }
            }else android.util.JsonReader(File(staging,mapName).reader()).use {reader->
                var featureCollection=false
                reader.beginObject()
                while(reader.hasNext()) {if(reader.nextName()=="type")featureCollection=reader.nextString()=="FeatureCollection"else reader.skipValue()}
                reader.endObject();require(featureCollection){"Карта не является GeoJSON FeatureCollection"}
            }
            android.util.JsonReader(File(staging,"search.json").reader()).use {reader->
                reader.beginArray();var entries=0
                while(reader.hasNext()){require(++entries<=250000){"Слишком большой поисковый индекс"};reader.skipValue()};reader.endArray()
            }
            val target=File(root,"${info.id}-${UUID.randomUUID()}")
            check(staging.renameTo(target)){"Не удалось сохранить регион"}
            check(prefs.edit().putString("active",target.name).commit())
            directory=target;searchCache=null;mutableRegion.value=info
            android.util.Log.i("JelandNavigation","REGION_IMPORTED ${info.id} bytes=${info.bytes}")
            info
        }finally {if(staging.exists())staging.deleteRecursively()}
    }}
    suspend fun search(query:String):List<NavigationPlace> = withContext(Dispatchers.IO) {
        prepare()
        val searchContext=kotlin.coroutines.coroutineContext
        if(query.isBlank())return@withContext emptyList()
        val file=File(directory,"search.json")
        val places=searchCache?.takeIf {it.first==file}?.second?:if(file.exists()) {
            val entries=ArrayList<NavigationPlace>()
            android.util.JsonReader(file.reader()).use {reader->
                reader.beginArray()
                while(reader.hasNext()) {
                    if(entries.size%512==0)searchContext.ensureActive()
                    var name="";var lat=Double.NaN;var lon=Double.NaN;var kind="point";var locality="";var address=""
                    reader.beginObject()
                    while(reader.hasNext())when(reader.nextName()) {
                        "name"->name=reader.nextString();"lat"->lat=reader.nextDouble();"lon"->lon=reader.nextDouble();"kind"->kind=reader.nextString()
                        "locality","city"->if(reader.peek()==android.util.JsonToken.NULL)reader.nextNull()else locality=reader.nextString().take(160)
                        "address"->if(reader.peek()==android.util.JsonToken.NULL)reader.nextNull()else address=reader.nextString().take(320)
                        else->reader.skipValue()
                    }
                    reader.endObject()
                    if(name.isNotBlank()&&lat.isFinite()&&lon.isFinite())entries+=NavigationPlace(name,GeoPoint(lat,lon),kind,locality,address)
                };reader.endArray()
            }
            entries.also {searchCache=file to it}
        }else emptyList()
        searchNavigationPlaces(places,query,checkCancelled={searchContext.ensureActive()})
    }
    private fun readManifest(folder:File):RegionData {
        val m=JSONObject(File(folder,"manifest.json").readText());require(m.getInt("format") in 1..2)
        val id=m.getString("id");require(id.matches(Regex("[a-z0-9-]{1,64}")))
        val b=m.getJSONArray("bounds");require(b.length()==4);val bounds=List(4){b.getDouble(it)}
        require(bounds.all {it.isFinite()}&&bounds[0]>=-180&&bounds[2]<=180&&bounds[1]>=-90&&bounds[3]<=90&&bounds[0]<bounds[2]&&bounds[1]<bounds[3])
        val c=m.getJSONArray("center");val center=GeoPoint(c.getDouble(0),c.getDouble(1))
        val info=RegionData(id,m.getString("title").take(160),FILES.filter {it!="manifest.json"}.sumOf {File(folder,it).length()},m.getString("attribution"),center,bounds,m.optInt("houseNumberCount",0).coerceIn(0,500000))
        require(info.contains(center));return info
    }
    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val block=ByteArray(65536);while(true){val n=input.read(block);if(n<0)break;digest.update(block,0,n)}}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    private companion object {val FILES=setOf("manifest.json","map.geojson","map.pmtiles","search.json","valhalla_tiles.tar")}
}
