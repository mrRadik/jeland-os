package ru.jeland.os.data.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.*
import java.io.File
import java.security.MessageDigest

data class AppUpdateState(
    val autoCheck:Boolean=true,val autoDownloadWifi:Boolean=false,val checking:Boolean=false,val downloading:Boolean=false,
    val progress:Int=0,val available:AppUpdateCandidate?=null,val downloaded:Boolean=false,
    val message:String="Обновления из GitHub Releases",val lastChecked:Long?=null,val installPermission:Boolean=false,
)

/** App-scoped downloads; no APK installation, screen launch or vehicle command happens automatically. */
class AppUpdateManager(context:Context,private val store:SettingsStore,private val report:(String)->Unit={}) {
    private val app=context.applicationContext
    private val cm=app.getSystemService(ConnectivityManager::class.java)
    private val source=GitHubUpdateSource()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var foreground=false;private var observing=false
    private var timer:Job?=null;private var checkJob:Job?=null;private var downloadJob:Job?=null
    private val initial=runCatching {GitHubUpdateCodec.restore(store.string(PENDING,""))}.getOrNull()
        ?.takeIf {it.manifest.versionCode>BuildConfig.VERSION_CODE&&file(it).length()==it.manifest.size}
    private val mutable=MutableStateFlow(AppUpdateState(
        autoCheck=store.string(AUTO_CHECK,"true").toBooleanStrictOrNull()?:true,
        autoDownloadWifi=store.string(AUTO_DOWNLOAD,"false").toBooleanStrictOrNull()?:false,
        available=initial,downloaded=initial!=null,lastChecked=store.string(LAST_SUCCESS,"").toLongOrNull(),
        installPermission=canInstall(),message=if(initial==null)"Обновления из GitHub Releases"else"Обновление скачано. Установка только по вашей кнопке."))
    val state=mutable.asStateFlow()
    private fun file(c:AppUpdateCandidate)=File(app.filesDir,"updates/ready/JelandOS-${c.manifest.versionCode}.apk")
    private fun canInstall()=runCatching {app.packageManager.canRequestPackageInstalls()}.getOrDefault(false)
    // Some OEM ROMs cannot validate Google's connectivity endpoint even when GitHub is reachable.
    private fun online()=runCatching {cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}.getOrDefault(false)
    private fun wifiUnmetered()=runCatching {cm.getNetworkCapabilities(cm.activeNetwork)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }==true}.getOrDefault(false)
    private val network=object:ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n:Network,c:NetworkCapabilities) {scope.launch {if(foreground){check(false);maybeAutoDownload()}}}
    }
    fun resume() {
        foreground=true;mutable.update {it.copy(installPermission=canInstall())}
        if(!observing)runCatching {cm.registerDefaultNetworkCallback(network);observing=true}
        if(timer?.isActive!=true)timer=scope.launch {while(isActive){check(false);maybeAutoDownload();delay(60_000)}}
    }
    fun pause() {
        foreground=false;timer?.cancel();timer=null
        if(observing)runCatching {cm.unregisterNetworkCallback(network)};observing=false
    }
    fun autoCheck(value:Boolean) {store.put(AUTO_CHECK,value.toString());mutable.update {it.copy(autoCheck=value)};if(value)check(false)}
    fun autoDownload(value:Boolean) {
        store.put(AUTO_DOWNLOAD,value.toString());mutable.update {it.copy(autoDownloadWifi=value)}
        if(value){store.put(LAST_AUTO_DOWNLOAD,"0");maybeAutoDownload()}
    }
    fun check(manual:Boolean=true) {
        if(checkJob?.isActive==true||downloadJob?.isActive==true)return
        val now=System.currentTimeMillis()
        if(!manual&&(!state.value.autoCheck||!AppUpdatePolicy.due(store.string(LAST_ATTEMPT,"0").toLongOrNull()?:0,now)))return
        if(!online()){if(manual)mutable.update {it.copy(message="Нет подключения к интернету. Скачанные файлы сохранены.")};return}
        store.put(LAST_ATTEMPT,now.toString())
        checkJob=scope.launch {
            mutable.update {it.copy(checking=true,message="Проверяем GitHub Releases…")}
            try {
                val candidate=withContext(Dispatchers.IO){source.latest()}
                check(candidate.manifest.minSdk<=Build.VERSION.SDK_INT){"Новая версия требует Android API ${candidate.manifest.minSdk}; на ГУ API ${Build.VERSION.SDK_INT}"}
                val available=candidate.takeIf {it.manifest.versionCode>BuildConfig.VERSION_CODE}
                val ready=available!=null&&state.value.available==available&&state.value.downloaded&&file(available).length()==available.manifest.size
                store.put(LAST_SUCCESS,now.toString())
                mutable.update {it.copy(available=available,downloaded=ready,lastChecked=now,
                    message=if(available==null)"Установлена актуальная версия"else if(ready)"Обновление готово к установке"else"Доступна версия ${candidate.manifest.versionName}")}
                report("UPDATE_CHECK latest=${candidate.manifest.versionCode} installed=${BuildConfig.VERSION_CODE}")
            }catch(c:CancellationException){throw c}
            catch(e:Exception){mutable.update {it.copy(message="Не удалось проверить: ${reason(e)}")};report("UPDATE_CHECK_FAILED ${e.javaClass.simpleName}")}
            finally {mutable.update {it.copy(checking=false)}}
            maybeAutoDownload()
        }
    }
    private fun maybeAutoDownload() {
        if(!foreground||!state.value.autoDownloadWifi||state.value.downloaded||state.value.available==null||!wifiUnmetered())return
        if(!AppUpdatePolicy.due(store.string(LAST_AUTO_DOWNLOAD,"0").toLongOrNull()?:0,System.currentTimeMillis()))return
        if(checkJob?.isActive==true&&state.value.checking)return
        if(downloadJob?.isActive==true)return
        store.put(LAST_AUTO_DOWNLOAD,System.currentTimeMillis().toString());download(false)
    }
    fun download(allowMetered:Boolean=false) {
        if(downloadJob?.isActive==true||state.value.checking)return
        val candidate=state.value.available?:return
        downloadJob=scope.launch {
            mutable.update {it.copy(downloading=true,progress=0,message="Скачиваем обновление…")}
            try {
                withContext(Dispatchers.IO) {
                    check(online()){ "Нет подключения к интернету" }
                    check(allowMetered||wifiUnmetered()){ "Нужен Wi-Fi без ограничения трафика. Мобильную загрузку нужно подтвердить отдельно" }
                    val staging=File(app.filesDir,"updates/staging/update.part");check(staging.parentFile!!.mkdirs()||staging.parentFile!!.isDirectory)
                    if(staging.exists())check(staging.delete()){"Не удалось очистить незавершённую загрузку"}
                    check(app.filesDir.usableSpace>candidate.manifest.size+16*1024*1024){"Недостаточно свободного места"}
                    val connection=source.open(candidate.apk.url)
                    try {
                        check(connection.contentLengthLong<0||connection.contentLengthLong==candidate.manifest.size){"Размер ответа не совпадает с релизом"}
                        connection.inputStream.use {input->staging.outputStream().use {output->
                            val buffer=ByteArray(64*1024);var total=0L;var percent=-1
                            while(true) {
                                ensureActive()
                                check(allowMetered||wifiUnmetered()){ "Wi-Fi отключён или стал платным. Загрузка остановлена" }
                                val n=input.read(buffer);if(n<0)break
                                total+=n;check(total<=candidate.manifest.size){"APK больше заявленного размера"}
                                output.write(buffer,0,n)
                                val next=(total*100/candidate.manifest.size).toInt()
                                if(next!=percent){percent=next;mutable.update {it.copy(progress=next)}}
                            }
                            check(total==candidate.manifest.size){"APK скачан не полностью"}
                        }}
                    }finally {connection.disconnect()}
                    validateApk(staging,candidate)
                    val ready=file(candidate);check(ready.parentFile!!.mkdirs()||ready.parentFile!!.isDirectory)
                    if(ready.exists())check(ready.delete()){ "Не удалось заменить старый файл обновления" }
                    check(staging.renameTo(ready)){"Не удалось сохранить проверенный APK"}
                    store.put(PENDING,GitHubUpdateCodec.pending(candidate))
                    // Only obsolete updater-owned APKs; never logs, installed apps or user-selected files.
                    ready.parentFile?.listFiles()?.filter {it!=ready&&it.isFile&&Regex("JelandOS-[0-9]+\\.apk").matches(it.name)}?.forEach {it.delete()}
                }
                mutable.update {it.copy(downloaded=true,message="Обновление проверено. Установите его на стоянке.")}
                report("UPDATE_READY version=${candidate.manifest.versionCode}")
            }catch(c:CancellationException){throw c}
            catch(e:Exception){mutable.update {it.copy(downloaded=false,message="Загрузка не завершена: ${reason(e)}")};report("UPDATE_DOWNLOAD_FAILED ${e.javaClass.simpleName}")}
            finally {mutable.update {it.copy(downloading=false)}}
        }
    }
    fun cancelDownload() {
        downloadJob?.cancel()
        mutable.update {it.copy(message="Загрузка остановлена. Установленная версия не изменена.")}
    }
    fun install() {
        if(state.value.downloading||state.value.checking||downloadJob?.isActive==true)return
        val candidate=state.value.available?.takeIf {state.value.downloaded}?:return
        if(!canInstall()) {
            mutable.update {it.copy(installPermission=false,message="Разрешите Jeland OS установку приложений и нажмите «Установить» ещё раз.")}
            runCatching {app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onFailure {mutable.update {it.copy(message="Экран разрешения недоступен. Разрешите установку для Jeland OS в штатных настройках Android.")}}
            return
        }
        downloadJob=scope.launch {
            mutable.update {it.copy(downloading=true,message="Проверяем APK перед установкой…")}
            try {
                val ready=file(candidate)
                withContext(Dispatchers.IO){validateApk(ready,candidate)}
                val uri=FileProvider.getUriForFile(app,"${app.packageName}.updates",ready)
                app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
                mutable.update {it.copy(message="Подтвердите обновление в системном установщике. Данные приложения сохраняются.")}
            }catch(c:CancellationException){throw c}
            catch(e:Exception){mutable.update {it.copy(message="Установка не открыта: ${reason(e)}")}}
            finally {mutable.update {it.copy(downloading=false)}}
        }
    }
    @Suppress("DEPRECATION") private fun validateApk(file:File,c:AppUpdateCandidate) {
        check(file.length()==c.manifest.size){"Размер APK не совпадает"}
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input->val buffer=ByteArray(64*1024);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        check(digest.digest().joinToString(""){"%02x".format(it)}.equals(c.manifest.sha256,true)){"SHA-256 не совпадает"}
        val pm=app.packageManager
        val apk=checkNotNull(pm.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)){"APK не распознан Android"}
        val current=pm.getPackageInfo(app.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        fun signers(info:android.content.pm.PackageInfo)=info.signingInfo?.apkContentsSigners?.map {it.toCharsString()}?.toSet().orEmpty()
        fun identity(info:android.content.pm.PackageInfo)=UpdateApkIdentity(info.packageName,info.longVersionCode,info.versionName,info.applicationInfo?.minSdkVersion?:-1,signers(info))
        AppUpdatePolicy.requireInstallable(c.manifest,identity(current),identity(apk),Build.VERSION.SDK_INT)
    }
    private fun reason(e:Exception)=if(e is IllegalStateException||e is IllegalArgumentException)e.message?.take(220)?:"Проверка не пройдена"else"Сеть или файл недоступны. Повторите позже"
    companion object {
        private const val AUTO_CHECK="updates.auto_check";private const val AUTO_DOWNLOAD="updates.auto_download_wifi"
        private const val LAST_ATTEMPT="updates.last_attempt";private const val LAST_SUCCESS="updates.last_success"
        private const val LAST_AUTO_DOWNLOAD="updates.last_auto_download";private const val PENDING="updates.pending"
    }
}
