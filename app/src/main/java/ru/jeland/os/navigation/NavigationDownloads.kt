package ru.jeland.os.navigation

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.net.URI
import java.security.MessageDigest

data class MapDownload(val title:String="",val running:Boolean=false,val received:Long=0,val total:Long=0,
    val status:String="",val ready:Uri?=null,val kind:String="region",val systemOwned:Boolean=false,
    val sourceUrl:String="",val autoInstall:Boolean=false,val installed:Boolean=false)

fun validatedMapUrl(value:String):String {
    val uri=URI(value.trim())
    require(uri.scheme.equals("https",true)&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.fragment==null){"Нужна прямая HTTPS-ссылка без логина в адресе"}
    return uri.toASCIIString()
}

/** Prefer Android's persistent transfer; OEMs without its provider use resumable HTTPS.
 * The repository installs a requested catalog download only while no route is active.
 */
class NavigationDownloads(context:Context,preferencesName:String="navigation-downloads",
    private val manager:MapSystemDownloadDriver=AndroidMapSystemDownloadDriver(context.applicationContext)):AutoCloseable {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences(preferencesName,0)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    private var direct:DirectMapTransfer?=null
    val state=MutableStateFlow(MapDownload())
    init {
        if(prefs.contains("directToken"))resumeDirect()
        else if(prefs.getLong("id",0)>0)watch(prefs.getLong("id",0))
    }
    fun start(url:String,kind:String,sha256:String,allowMetered:Boolean,
        title:String=if(kind=="overview")"Обзорная карта России"else"Карта региона",autoInstall:Boolean=false,
        allowCellular:Boolean=allowMetered) {
        require(!state.value.running){"Дождитесь загрузки или отмените её"}
        require(kind in setOf("region","overview"))
        val address=validatedMapUrl(url)
        val checksum=sha256.trim().lowercase()
        require(checksum.isEmpty()||checksum.matches(Regex("[a-f0-9]{64}"))){"SHA-256 должен содержать 64 шестнадцатеричных символа"}
        val reuseDirect=prefs.contains("directToken")&&prefs.getString("sourceUrl","")==address&&prefs.getString("sha256","")==checksum
        if(!reuseDirect)clearDirect()
        prefs.edit().remove("id").putString("kind",kind).putString("title",title).putString("sha256",checksum)
            .putString("sourceUrl",address).putBoolean("autoInstall",autoInstall).putBoolean("allowMetered",allowMetered)
            .putBoolean("allowCellular",allowCellular).commit()
        state.value=MapDownload(title,true,status="Подготовка загрузки…",kind=kind,sourceUrl=address,autoInstall=autoInstall)
        if(reuseDirect||prefs.getBoolean("directOnly",false)){resumeDirect();return}
        launchWork {
        try {
        val remote=inspect(address)
        ensureActive()
        // Keep the stable asset URL: GitHub's preflight redirect expires while Android waits for Wi-Fi.
        val request=DownloadManager.Request(Uri.parse(address)).setTitle("Jeland OS — $title")
            .setDescription("Загрузка офлайн-карты Jeland OS")
            .setAllowedOverMetered(allowMetered).setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app,Environment.DIRECTORY_DOWNLOADS,"jeland-map-${java.util.UUID.randomUUID()}.${if(kind=="overview")"pmtiles"else"zip"}")
        remote.second?.let {request.addRequestHeader("If-Match",it)}
        val id=try {manager.enqueue(request)}catch(e:Exception) {
            android.util.Log.w("JelandNavigation","System map downloader unavailable; using app HTTPS",e)
            prefs.edit().putBoolean("directOnly",true).apply()
            resumeDirect();return@launchWork
        }
        if(!isActive){manager.remove(id);return@launchWork}
        check(prefs.edit().putLong("id",id).putString("kind",kind).putString("title",title).putString("sha256",checksum)
            .putString("sourceUrl",address).putBoolean("autoInstall",autoInstall).commit())
        if(!isActive){manager.remove(id);prefs.edit().remove("id").apply();return@launchWork}
        state.value=MapDownload(title,true,status="Ожидание сети",kind=kind,sourceUrl=address,autoInstall=autoInstall)
        watch(id)
        }catch(e:CancellationException){throw e}
        catch(e:Exception){state.value=MapDownload(title,status=e.message?:"Не удалось начать загрузку",kind=kind,sourceUrl=address)}
        }
    }
    private fun inspect(address:String):Pair<String,String?> {
        var url=address
        repeat(6) {
            val connection=java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
            try {
                connection.instanceFollowRedirects=false;connection.requestMethod="HEAD"
                connection.connectTimeout=15000;connection.readTimeout=15000
                connection.setRequestProperty("Accept-Encoding","identity")
                val code=connection.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    val location=checkNotNull(connection.getHeaderField("Location")){"Пустое перенаправление"}
                    url=validatedMapUrl(java.net.URI(url).resolve(location).toString())
                }else {
                    require(code in 200..299){"Сервер не подтвердил файл (HTTP $code). Нужна прямая ссылка."}
                    val size=connection.contentLengthLong
                    require(size in 1..MAX_BYTES){"Сервер должен сообщить размер файла от 1 байта до 512 МБ"}
                    require((app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.usableSpace?:0)>size+16*1048576){"Недостаточно места для загрузки"}
                    return url to connection.getHeaderField("ETag")
                }
            }finally {connection.disconnect()}
        }
        error("Слишком много перенаправлений")
    }
    private fun watch(id:Long) {
        val sourceUrl=prefs.getString("sourceUrl","").orEmpty()
        val autoInstall=prefs.getBoolean("autoInstall",false)
        state.value=MapDownload(prefs.getString("title","Карта")!!,true,status="Восстановление загрузки…",kind=prefs.getString("kind","region")!!,systemOwned=true,sourceUrl=sourceUrl,autoInstall=autoInstall)
        launchWork {
            val title=prefs.getString("title","Пакет карты")!!;val kind=prefs.getString("kind","region")!!
            try {
                while(isActive) {
                    val result=try {manager.query(id)}catch(e:Exception){throw MapSystemDownloadException(e)}
                    result.use {cursor->
                        check(cursor.moveToFirst()){"Загрузка отсутствует в системе. Можно скачать заново."}
                        fun long(name:String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
                        val received=long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val total=long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0)
                        require(maxOf(received,total)<=MAX_BYTES){"Пакет больше 512 МБ. Используйте меньший регион."}
                        when(long(DownloadManager.COLUMN_STATUS).toInt()) {
                            DownloadManager.STATUS_SUCCESSFUL->{
                                state.value=MapDownload(title,true,received,total,"Проверка файла…",kind=kind,systemOwned=true,sourceUrl=sourceUrl,autoInstall=autoInstall)
                                val uri=checkNotNull(try {manager.uri(id)}catch(e:Exception){throw MapSystemDownloadException(e)})
                                val hash=MessageDigest.getInstance("SHA-256")
                                var size=0L
                                app.contentResolver.openInputStream(uri).use {input->
                                    checkNotNull(input)
                                    val block=ByteArray(65536)
                                    while(true){ensureActive();val n=input.read(block);if(n<0)break;size+=n;require(size<=MAX_BYTES);hash.update(block,0,n)}
                                }
                                val expected=prefs.getString("sha256","").orEmpty()
                                val actual=hash.digest().joinToString(""){"%02x".format(it)}
                                check(expected.isBlank()||actual==expected){"Контрольная сумма не совпала. Не устанавливайте этот пакет."}
                                state.value=MapDownload(title,false,size,size,"Загружено",uri,kind,true,sourceUrl,autoInstall)
                                return@launchWork
                            }
                            DownloadManager.STATUS_FAILED->error("Загрузка не выполнена (код ${long(DownloadManager.COLUMN_REASON)}). Проверьте ссылку и свободное место.")
                            DownloadManager.STATUS_PAUSED->state.value=MapDownload(title,true,received,total,"Ожидание сети…",kind=kind,systemOwned=true,sourceUrl=sourceUrl,autoInstall=autoInstall)
                            else->state.value=MapDownload(title,true,received,total,"Загрузка…",kind=kind,systemOwned=true,sourceUrl=sourceUrl,autoInstall=autoInstall)
                        }
                    }
                    delay(700)
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(!isActive)return@launchWork
                runCatching {manager.remove(id)};prefs.edit().remove("id").apply()
                if(e is MapSystemDownloadException) {
                    android.util.Log.w("JelandNavigation","System map provider unavailable during restore; using app HTTPS",e)
                    prefs.edit().putBoolean("directOnly",true).apply();resumeDirect()
                }else state.value=MapDownload(title,status=e.message?:"Ошибка загрузки",kind=kind,sourceUrl=sourceUrl)
            }
        }
    }
    private fun launchWork(block:suspend CoroutineScope.()->Unit) {
        job?.cancel()
        val next=scope.launch(start=CoroutineStart.LAZY,block=block)
        job=next
        next.start()
    }
    private fun resumeDirect() {
        job?.cancel();direct?.close()
        val transfer=DirectMapTransfer(app,prefs,state)
        direct=transfer
        launchWork {transfer.run()}
    }
    private fun clearDirect() {
        direct?.close();direct=null
        DirectMapTransfer.removeFiles(app,prefs)
    }
    fun deferInstallation() {
        prefs.edit().putBoolean("autoInstall",false).apply()
        state.value=state.value.copy(autoInstall=false)
    }
    fun forgetInstalled(sourceUrl:String,kind:String) {
        if(!state.value.running&&state.value.installed&&(state.value.sourceUrl==sourceUrl||state.value.kind==kind))state.value=MapDownload()
    }
    fun finishInstallation(error:String?=null) {
        val id=prefs.getLong("id",0)
        if(id>0)runCatching {manager.remove(id)}
        clearDirect()
        prefs.edit().remove("id").putBoolean("autoInstall",false).apply()
        state.value=state.value.copy(ready=null,autoInstall=false,installed=error==null,
            status=error?.let {"Не удалось установить карту: $it"}?:"Установлено")
    }
    fun cancel(){
        job?.cancel();clearDirect();val id=prefs.getLong("id",0)
        if(id>0){runCatching {manager.remove(id)};prefs.edit().remove("id").apply()}
        state.value=MapDownload(status="Загрузка отменена")
    }
    override fun close(){direct?.close();scope.cancel()} // Native transfers keep their marker/partial file for the next process.
    companion object {const val MAX_BYTES=512L*1024*1024}
}
