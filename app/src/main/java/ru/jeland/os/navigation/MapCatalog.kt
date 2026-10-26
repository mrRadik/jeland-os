package ru.jeland.os.navigation

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import javax.net.ssl.HttpsURLConnection

const val DEFAULT_MAP_SOURCE="https://github.com/mrRadik/jeland-os/releases"

data class DownloadableMap(val id:String,val title:String,val url:String,val bytes:Long,val sha256:String="",val kind:String="region")
data class MapCatalogState(val source:String=DEFAULT_MAP_SOURCE,val loading:Boolean=false,
    val maps:List<DownloadableMap> = emptyList(),val error:String="",val loaded:Boolean=false)
data class MapCatalogSource(val url:String,val github:Boolean)

/** GitHub repository/release links or an HTTPS directory containing maps.json. */
fun mapCatalogSource(value:String):MapCatalogSource {
    val address=validatedMapUrl(value.ifBlank {DEFAULT_MAP_SOURCE})
    val uri=URI(address)
    if(uri.path.endsWith(".json",true))return MapCatalogSource(address,false)
    if(uri.host.equals("github.com",true)) {
        require(uri.query==null){"Укажите ссылку на репозиторий или его релизы"}
        val parts=uri.path.trim('/').split('/')
        require(parts.size>=2&&parts.take(2).all {it.matches(Regex("[A-Za-z0-9_.-]+"))}){"Не удалось определить репозиторий GitHub"}
        val base="https://api.github.com/repos/${parts[0]}/${parts[1].removeSuffix(".git")}/releases"
        val suffix=parts.drop(2)
        return when {
            suffix.isEmpty()||suffix==listOf("releases")->MapCatalogSource("$base?per_page=100",true)
            suffix==listOf("releases","latest")->MapCatalogSource("$base/latest",true)
            suffix.size==3&&suffix.take(2)==listOf("releases","tag")->MapCatalogSource("$base/tags/${suffix[2]}",true)
            else->error("Укажите репозиторий GitHub, страницу релизов или maps.json")
        }
    }
    if(uri.host.equals("api.github.com",true)) {
        require(uri.path.matches(Regex("/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases(?:/latest|/tags/[^/]+)?"))){"Неверная ссылка на релизы GitHub"}
        return MapCatalogSource(address,true)
    }
    require(uri.query==null||uri.path.endsWith(".json",true)){"Укажите ссылку на каталог maps.json"}
    return MapCatalogSource(if(uri.path.endsWith(".json",true))address else address.trimEnd('/')+"/maps.json",false)
}

internal fun parseMapCatalog(text:String,source:MapCatalogSource):List<DownloadableMap> {
    fun entry(id:String,title:String,url:String,bytes:Long,sha:String,kind:String):DownloadableMap {
        require(id.matches(Regex("[A-Za-z0-9_.-]{1,160}"))&&title.isNotBlank()&&title.length<=160){"В каталоге неверное название карты"}
        require(bytes in 1..NavigationDownloads.MAX_BYTES){"Карта «$title» превышает допустимый размер 512 МБ"}
        val checksum=sha.removePrefix("sha256:").lowercase()
        require(checksum.isEmpty()||checksum.matches(Regex("[a-f0-9]{64}"))){"Некорректная контрольная сумма карты"}
        require(kind in setOf("region","overview")){"Этот формат карты не поддерживается"}
        return DownloadableMap(id,title,validatedMapUrl(url),bytes,checksum,kind)
    }
    if(!source.github) {
        val root=JSONObject(text)
        require(root.getInt("format")==1){"Версия каталога карт не поддерживается"}
        val rows=root.getJSONArray("maps");require(rows.length()<=500){"Слишком большой каталог карт"}
        val result=(0 until rows.length()).map {i->
            val row=rows.getJSONObject(i)
            entry(row.getString("id"),row.getString("title"),URI(source.url).resolve(row.getString("url")).toString(),
                row.getLong("bytes"),row.optString("sha256"),row.optString("kind","region"))
        }
        require(result.map {it.id}.distinct().size==result.size){"Повторяющиеся карты в каталоге"}
        return result.sortedBy {it.title}
    }
    val releases=if(text.trimStart().startsWith("["))JSONArray(text)else JSONArray().put(JSONObject(text))
    require(releases.length()<=100){"Слишком много релизов"}
    val result=linkedMapOf<String,DownloadableMap>()
    // Use publication time, not API position, to prefer newer packages with the same filename.
    val published=(0 until releases.length()).map {releases.getJSONObject(it)}
        .filter {!it.optBoolean("draft")&&!it.optBoolean("prerelease")}
        .sortedByDescending {it.optString("published_at")}
    for(release in published) {
        val assets=release.optJSONArray("assets")?:continue
        require(assets.length()<=1000){"Слишком много файлов в релизе"}
        for(i in 0 until assets.length()) {
            val asset=assets.getJSONObject(i);val name=asset.optString("name")
            val kind=when {name.endsWith(".jelandnav.zip",true)->"region";name.endsWith(".pmtiles",true)->"overview";else->continue}
            if(asset.optString("state")!="uploaded")continue
            val id=name.dropLast(if(kind=="region")".jelandnav.zip".length else".pmtiles".length)
            if(id in result)continue
            // Ignore oversized/unrelated files without hiding the other valid regions.
            val bytes=asset.optLong("size");if(bytes !in 1..NavigationDownloads.MAX_BYTES)continue
            val label=asset.optString("label").takeUnless {it.isBlank()||it=="null"}?:id.replace('-',' ')
            val digest=asset.optString("digest").takeIf {it.startsWith("sha256:")}.orEmpty()
            result[id]=entry(id,label,asset.getString("browser_download_url"),bytes,digest,kind)
        }
    }
    return result.values.sortedBy {it.title}
}

/** Loaded only when the user opens the list; a failed refresh keeps the current catalog. */
class NavigationMapCatalog(context:Context,preferencesName:String="navigation-map-catalog",
    private val fetch:suspend (String)->String = {fetchMapCatalogJson(it)}):AutoCloseable {
    private val prefs=context.applicationContext.getSharedPreferences(preferencesName,0)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private val mutable=MutableStateFlow(MapCatalogState(source=prefs.getString("source",DEFAULT_MAP_SOURCE).orEmpty()))
    val state=mutable.asStateFlow()
    fun setSource(value:String) {
        val source=value.trim().take(2048)
        if(source==mutable.value.source)return
        job?.cancel()
        prefs.edit().putString("source",source).apply()
        mutable.value=MapCatalogState(source)
    }
    fun refresh(force:Boolean=true) {
        if(mutable.value.loading||(!force&&mutable.value.loaded))return
        val before=mutable.value
        mutable.value=before.copy(loading=true,error="")
        job=scope.launch {
            try {
                val source=mapCatalogSource(before.source)
                val rows=parseMapCatalog(fetch(source.url),source)
                ensureActive();mutable.value=before.copy(maps=rows,loaded=true)
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(isActive)mutable.value=before.copy(error=e.message?:"Не удалось загрузить список карт")}
        }
    }
    override fun close(){scope.cancel()}
}

internal suspend fun fetchMapCatalogJson(address:String):String=withContext(Dispatchers.IO) {
    var url=address
    repeat(6) {
        ensureActive()
        val connection=java.net.URL(validatedMapUrl(url)).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("User-Agent","JelandOS-Maps")
            connection.setRequestProperty("Accept","application/json")
            when(val code=connection.responseCode) {
                301,302,303,307,308->{url=URI(url).resolve(checkNotNull(connection.getHeaderField("Location"))).toString()}
                200->{
                    require(connection.contentLengthLong<=2*1048576){"Слишком большой каталог карт"}
                    val bytes=java.io.ByteArrayOutputStream()
                    connection.inputStream.use {input->val block=ByteArray(8192)
                        while(true){ensureActive();val n=input.read(block);if(n<0)break;require(bytes.size()+n<=2*1048576){"Слишком большой каталог карт"};bytes.write(block,0,n)}
                    }
                    return@withContext bytes.toString(Charsets.UTF_8.name())
                }
                403,429->error("Источник временно ограничил запросы. Попробуйте позже.")
                404->error("Каталог карт не найден. Проверьте путь до карт.")
                else->error("Не удалось получить список карт (HTTP $code)")
            }
        }finally {connection.disconnect()}
    }
    error("Слишком много перенаправлений каталога")
}
