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
    val status:String="",val ready:Uri?=null,val kind:String="region",val systemOwned:Boolean=false)

fun validatedMapUrl(value:String):String {
    val uri=URI(value.trim())
    require(uri.scheme.equals("https",true)&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.fragment==null){"Нужна прямая HTTPS-ссылка без логина в адресе"}
    return uri.toASCIIString()
}

/** Android owns the transfer: app restarts do not discard it. Installation is a separate,
 * explicit action so finishing a download never interrupts an active route.
 */
class NavigationDownloads(context:Context,preferencesName:String="navigation-downloads"):AutoCloseable {
    private val app=context.applicationContext
    private val manager=app.getSystemService(DownloadManager::class.java)
    private val prefs=app.getSharedPreferences(preferencesName,0)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var job:Job?=null
    val state=MutableStateFlow(MapDownload())
    init {if(prefs.getLong("id",0)>0)watch(prefs.getLong("id",0))}
    fun start(url:String,kind:String,sha256:String,allowMetered:Boolean) {
        require(!state.value.running){"Дождитесь загрузки или отмените её"}
        require(kind in setOf("region","overview"))
        val address=validatedMapUrl(url)
        val checksum=sha256.trim().lowercase()
        require(checksum.isEmpty()||checksum.matches(Regex("[a-f0-9]{64}"))){"SHA-256 должен содержать 64 шестнадцатеричных символа"}
        val title=if(kind=="overview")"Обзорная карта Protomaps"else"Пакет региона Jeland"
        prefs.edit().remove("id").apply() // Never let cancellation of a new preflight delete the previous completed file.
        state.value=MapDownload(title,true,status="Проверка размера и HTTPS-ссылки…",kind=kind)
        job=scope.launch {
        try {
        val remote=inspect(address)
        ensureActive()
        val request=DownloadManager.Request(Uri.parse(remote.first)).setTitle("Jeland OS — $title")
            .setDescription("Карта устанавливается только после проверки и вашего нажатия")
            .setAllowedOverMetered(allowMetered).setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app,Environment.DIRECTORY_DOWNLOADS,"jeland-map-${java.util.UUID.randomUUID()}.${if(kind=="overview")"pmtiles"else"zip"}")
        remote.second?.let {request.addRequestHeader("If-Match",it)}
        val id=manager.enqueue(request)
        if(!isActive){manager.remove(id);return@launch}
        check(prefs.edit().putLong("id",id).putString("kind",kind).putString("title",title).putString("sha256",checksum).commit())
        if(!isActive){manager.remove(id);prefs.edit().remove("id").apply();return@launch}
        state.value=MapDownload(title,true,status="Ожидание сети",kind=kind)
        watch(id)
        }catch(e:CancellationException){throw e}
        catch(e:Exception){state.value=MapDownload(title,status=e.message?:"Не удалось проверить ссылку",kind=kind)}
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
        state.value=MapDownload(prefs.getString("title","Пакет карты")!!,true,status="Восстановление загрузки…",kind=prefs.getString("kind","region")!!,systemOwned=true)
        job?.cancel();job=scope.launch {
            val title=prefs.getString("title","Пакет карты")!!;val kind=prefs.getString("kind","region")!!
            try {
                while(isActive) {
                    manager.query(DownloadManager.Query().setFilterById(id)).use {cursor->
                        check(cursor.moveToFirst()){"Загрузка отсутствует в системе. Можно скачать заново."}
                        fun long(name:String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
                        val received=long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val total=long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES).coerceAtLeast(0)
                        require(maxOf(received,total)<=MAX_BYTES){"Пакет больше 512 МБ. Используйте меньший регион."}
                        when(long(DownloadManager.COLUMN_STATUS).toInt()) {
                            DownloadManager.STATUS_SUCCESSFUL->{
                                state.value=MapDownload(title,true,received,total,"Проверка SHA-256…",kind=kind,systemOwned=true)
                                val uri=checkNotNull(manager.getUriForDownloadedFile(id))
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
                                state.value=MapDownload(title,false,size,size,if(expected.isBlank())"Загружено · SHA-256 автора не задан"else"Загружено · SHA-256 совпал",uri,kind,true)
                                return@launch
                            }
                            DownloadManager.STATUS_FAILED->error("Загрузка не выполнена (код ${long(DownloadManager.COLUMN_REASON)}). Проверьте ссылку и свободное место.")
                            DownloadManager.STATUS_PAUSED->state.value=MapDownload(title,true,received,total,"Пауза · ожидаем разрешённую сеть / повтор",kind=kind,systemOwned=true)
                            else->state.value=MapDownload(title,true,received,total,"Загрузка…",kind=kind,systemOwned=true)
                        }
                    }
                    delay(700)
                }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(!isActive)return@launch
                manager.remove(id);prefs.edit().remove("id").apply()
                state.value=MapDownload(title,status=e.message?:"Ошибка загрузки",kind=kind)
            }
        }
    }
    fun cancel(){
        job?.cancel();val id=prefs.getLong("id",0)
        if(id>0){manager.remove(id);prefs.edit().remove("id").apply()}
        state.value=MapDownload(status="Загрузка отменена")
    }
    override fun close(){scope.cancel()} // DownloadManager intentionally keeps an authorised transfer alive.
    companion object {const val MAX_BYTES=512L*1024*1024}
}
