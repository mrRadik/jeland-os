package ru.jeland.os.navigation

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** OEM fallback: app-owned files and HTTPS, no content://downloads dependency.
 * A stable source URL, partial file and ETag survive process recreation.
 */
internal class DirectMapTransfer(private val app:Context,private val prefs:SharedPreferences,
    private val state:MutableStateFlow<MapDownload>):AutoCloseable {
    @Volatile private var connection:HttpURLConnection?=null
    private val title=prefs.getString("title","Карта").orEmpty()
    private val kind=prefs.getString("kind","region").orEmpty()
    private val address=prefs.getString("sourceUrl","").orEmpty()
    private val expected=prefs.getString("sha256","").orEmpty()
    private val autoInstall=prefs.getBoolean("autoInstall",false)
    private val allowMetered=prefs.getBoolean("allowMetered",false)
    private val allowCellular=prefs.getBoolean("allowCellular",false)
    private val root=File(app.filesDir,"navigation-downloads").apply {mkdirs()}
    private val token=prefs.getString("directToken",null)?.takeIf {TOKEN.matches(it)}?:UUID.randomUUID().toString().also {
        check(prefs.edit().putString("directToken",it).putBoolean("directComplete",false).remove("directEtag").commit())
    }
    private val partial=File(root,"$token.part")
    private val ready=File(root,"$token.${if(kind=="overview")"pmtiles"else"zip"}")
    private fun status(text:String,received:Long=partial.length(),total:Long=0)=
        MapDownload(title,true,received,total,text,kind=kind,sourceUrl=address,autoInstall=autoInstall)

    suspend fun run() {
        try {
            validatedMapUrl(address)
            state.value=status("Восстановление загрузки…")
            if(prefs.getBoolean("directComplete",false)&&ready.exists()) {
                verifiedReady(ready);return
            }
            for(attempt in 0..2) {
                try {transfer();return}
                catch(e:IOException) {
                    currentCoroutineContext().ensureActive()
                    if(attempt==2)throw e
                    state.value=status("Соединение прервано · повторяем…")
                    delay(1500)
                }
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception) {
            if(currentCoroutineContext().isActive) {
                android.util.Log.w("JelandNavigation","Direct map transfer failed",e)
                val reason=when(e){is IOException->"Не удалось скачать карту. Проверьте подключение и повторите.";else->e.message?:"Ошибка загрузки карты"}
                state.value=MapDownload(title,status=reason,kind=kind,sourceUrl=address)
            }
        }finally {close()}
    }
    private suspend fun network():Network {
        val manager=app.getSystemService(ConnectivityManager::class.java)
        while(true) {
            currentCoroutineContext().ensureActive()
            val eligible=manager.allNetworks.mapNotNull {n->manager.getNetworkCapabilities(n)?.let {n to it}}
                .filter {(_,c)->c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&
                    (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)||
                     c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&allowMetered||allowCellular)}
                .sortedByDescending {(_,c)->c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}
            eligible.firstOrNull()?.let {return it.first}
            state.value=status("Ожидание Wi-Fi или разрешённой сети…")
            delay(1500)
        }
    }
    private suspend fun open(network:Network,offset:Long,etag:String):HttpURLConnection {
        var next=address
        repeat(6) {
            currentCoroutineContext().ensureActive()
            val url=validatedMapUrl(next)
            val c=network.openConnection(URL(url)) as HttpURLConnection
            connection=c
            try {
                c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000
                c.setRequestProperty("User-Agent","JelandOS-Maps")
                c.setRequestProperty("Accept-Encoding","identity")
                if(offset>0){c.setRequestProperty("Range","bytes=$offset-");c.setRequestProperty("If-Range",etag)}
                val code=c.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    next=URI(url).resolve(checkNotNull(c.getHeaderField("Location")){"Пустое перенаправление"}).toString()
                    c.disconnect()
                }else return c
            }catch(e:Exception){c.disconnect();throw e}
        }
        error("Слишком много перенаправлений загрузки")
    }
    private suspend fun transfer() {
        val network=network()
        val savedEtag=prefs.getString("directEtag","").orEmpty()
        var offset=if(savedEtag.isNotBlank())partial.length()else 0L
        if(offset>NavigationDownloads.MAX_BYTES){partial.delete();offset=0}
        val c=open(network,offset,savedEtag)
        try {
            val code=c.responseCode
            require(code in setOf(200,206)){"Не удалось получить карту (HTTP $code)"}
            val total:Long
            if(code==206) {
                val range=Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(c.getHeaderField("Content-Range").orEmpty())
                    ?:error("Сервер вернул неверный диапазон файла")
                require(range.groupValues[1].toLong()==offset){"Сервер вернул другой участок карты"}
                total=range.groupValues[3].toLong()
                require(range.groupValues[2].toLong()==total-1){"Сервер вернул неполный диапазон карты"}
                require(c.getHeaderField("ETag")==null||c.getHeaderField("ETag")==savedEtag){"Карта на сервере изменилась. Отмените загрузку и скачайте заново."}
            }else {offset=0;total=c.contentLengthLong}
            require(total in 1..NavigationDownloads.MAX_BYTES){"Размер карты должен быть от 1 байта до 512 МиБ"}
            require(root.usableSpace>total-offset+16*1048576){"Недостаточно места для карты"}
            currentCoroutineContext().ensureActive()
            check(prefs.edit().putString("directEtag",c.getHeaderField("ETag").orEmpty()).putBoolean("directComplete",false).commit())
            var received=offset;var lastUpdate=0L
            c.inputStream.use {input->FileOutputStream(partial,offset>0).use {output->
                val block=ByteArray(65536)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(block);if(n<0)break
                    received+=n
                    require(received<=total&&received<=NavigationDownloads.MAX_BYTES){"Размер карты превышает заявленный"}
                    output.write(block,0,n)
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(now-lastUpdate>=200){state.value=status("Загрузка…",received,total);lastUpdate=now}
                }
            }}
            check(received==total){"Карта скачана не полностью. Повторите загрузку."}
            verifiedReady(partial)
        }finally {c.disconnect();connection=null}
    }
    private suspend fun verifiedReady(file:File) {
        state.value=status("Проверка файла…",file.length(),file.length())
        require(file.length() in 1..NavigationDownloads.MAX_BYTES)
        val hash=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val block=ByteArray(65536)
            while(true){currentCoroutineContext().ensureActive();val n=input.read(block);if(n<0)break;hash.update(block,0,n)}
        }
        val actual=hash.digest().joinToString(""){"%02x".format(it)}
        if(expected.isNotBlank()&&actual!=expected) {
            file.delete();prefs.edit().remove("directToken").remove("directEtag").putBoolean("directComplete",false).commit()
            error("Контрольная сумма не совпала. Скачайте карту заново.")
        }
        currentCoroutineContext().ensureActive()
        if(file!=ready)check(file.renameTo(ready)){"Не удалось сохранить карту"}
        check(prefs.edit().putBoolean("directComplete",true).commit())
        state.value=MapDownload(title,false,ready.length(),ready.length(),"Загружено",Uri.fromFile(ready),kind,false,address,autoInstall)
    }
    override fun close(){connection?.disconnect();connection=null}
    companion object {
        private val TOKEN=Regex("[a-f0-9-]{36}")
        fun removeFiles(context:Context,prefs:SharedPreferences) {
            prefs.getString("directToken",null)?.takeIf {TOKEN.matches(it)}?.let {token->
                val root=File(context.filesDir,"navigation-downloads")
                for(ext in listOf("part","zip","pmtiles"))File(root,"$token.$ext").delete()
            }
            prefs.edit().remove("directToken").remove("directEtag").remove("directComplete").apply()
        }
    }
}
