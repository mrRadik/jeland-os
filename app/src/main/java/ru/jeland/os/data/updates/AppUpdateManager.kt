package ru.jeland.os.data.updates

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
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
    val installing:Boolean=false,val installFallback:Boolean=false,val cleaningApks:Boolean=false,
)

/** App-scoped downloads; no APK installation, screen launch or vehicle command happens automatically. */
class AppUpdateManager(context:Context,private val store:SettingsStore,private val report:(String)->Unit={}) {
    private val app=context.applicationContext
    val installAssist=UpdateInstallAssist(app,store,report)
    private val cm=app.getSystemService(ConnectivityManager::class.java)
    private val source=GitHubUpdateSource()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var foreground=false;private var observing=false
    private var timer:Job?=null;private var checkJob:Job?=null;private var downloadJob:Job?=null
    private var installJob:Job?=null;private var installWatchdog:Job?=null
    private val installer=UpdateInstallSession(app)
    private var confirmation:Intent?=null
    private val initial=runCatching {GitHubUpdateCodec.restore(store.string(PENDING,""))}.getOrNull()
        ?.takeIf {it.manifest.versionCode>BuildConfig.VERSION_CODE&&file(it).length()==it.manifest.size}
    private val mutable=MutableStateFlow(AppUpdateState(
        autoCheck=store.string(AUTO_CHECK,"true").toBooleanStrictOrNull()?:true,
        autoDownloadWifi=store.string(AUTO_DOWNLOAD,"false").toBooleanStrictOrNull()?:false,
        available=initial,downloaded=initial!=null,lastChecked=store.string(LAST_SUCCESS,"").toLongOrNull(),
        installPermission=canInstall(),message=if(initial==null)"Обновления из GitHub Releases"else
            store.string(INSTALL_MESSAGE,"").ifBlank {"Обновление скачано. Установка только по вашей кнопке."}))
    val state=mutable.asStateFlow()
    private fun file(c:AppUpdateCandidate)=File(app.filesDir,"updates/ready/JelandOS-${c.manifest.versionCode}.apk")
    private fun canInstall()=runCatching {app.packageManager.canRequestPackageInstalls()}.getOrDefault(false)
    // Some OEM ROMs cannot validate Google's connectivity endpoint even when GitHub is reachable.
    private fun online()=runCatching {cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true}.getOrDefault(false)
    private fun wifiUnmetered()=runCatching {cm.getNetworkCapabilities(cm.activeNetwork)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }==true}.getOrDefault(false)
    private fun networkState(n:Network)=cm.getNetworkCapabilities(n)?.let {
        UpdateNetworkState(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }
    private fun downloadNetwork(allowMobile:Boolean,automatic:Boolean):Network? {
        val active=cm.activeNetwork
        return (listOfNotNull(active)+cm.allNetworks.toList()).distinct().firstOrNull {
            networkState(it)?.allows(allowMobile,automatic)==true
        }
    }
    private val network=object:ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n:Network,c:NetworkCapabilities) {scope.launch {if(foreground){check(false);maybeAutoDownload()}}}
    }
    fun resume() {
        installAssist.refresh()
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
        if(state.value.cleaningApks)return
        if(checkJob?.isActive==true||downloadJob?.isActive==true||state.value.installing||installJob?.isActive==true)return
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
            catch(e:Exception){mutable.update {it.copy(message="Не удалось проверить: ${reason(e)}")};report("UPDATE_CHECK_FAILED ${e.javaClass.simpleName}: ${reason(e)}")}
            finally {mutable.update {it.copy(checking=false)}}
            maybeAutoDownload()
        }
    }
    private fun maybeAutoDownload() {
        if(!foreground||!state.value.autoDownloadWifi||state.value.downloaded||state.value.available==null||!wifiUnmetered())return
        if(!AppUpdatePolicy.due(store.string(LAST_AUTO_DOWNLOAD,"0").toLongOrNull()?:0,System.currentTimeMillis()))return
        if(checkJob?.isActive==true&&state.value.checking)return
        if(downloadJob?.isActive==true)return
        store.put(LAST_AUTO_DOWNLOAD,System.currentTimeMillis().toString());download(automatic=true)
    }
    fun download(allowMetered:Boolean=false,automatic:Boolean=false) {
        if(state.value.cleaningApks)return
        if(downloadJob?.isActive==true||state.value.checking||state.value.installing||installJob?.isActive==true)return
        val candidate=state.value.available?:return
        downloadJob=scope.launch {
            mutable.update {it.copy(downloading=true,progress=0,message="Скачиваем обновление…")}
            try {
                withContext(Dispatchers.IO) {
                    val route=checkNotNull(downloadNetwork(allowMetered,automatic)) {
                        if(automatic)"Автоматическая загрузка ожидает Wi-Fi без тарификации"
                        else if(allowMetered)"Нет подключения к интернету"else"Wi-Fi не подключён. Подключитесь или разрешите загрузку через любую сеть"
                    }
                    report("UPDATE_NETWORK wifi=${networkState(route)?.wifi} unmetered=${networkState(route)?.unmetered} automatic=$automatic mobileAllowed=$allowMetered")
                    val staging=File(app.filesDir,"updates/staging/update.part");check(staging.parentFile!!.mkdirs()||staging.parentFile!!.isDirectory)
                    if(staging.exists())check(staging.delete()){"Не удалось очистить незавершённую загрузку"}
                    check(app.filesDir.usableSpace>candidate.manifest.size+16*1024*1024){"Недостаточно свободного места"}
                    // Keep DNS, all redirects and APK bytes on the selected network.
                    val connection=source.open(candidate.apk.url,route)
                    try {
                        check(connection.contentLengthLong<0||connection.contentLengthLong==candidate.manifest.size){"Размер ответа не совпадает с релизом"}
                        connection.inputStream.use {input->staging.outputStream().use {output->
                            val buffer=ByteArray(64*1024);var total=0L;var percent=-1
                            while(true) {
                                ensureActive()
                                check(networkState(route)?.allows(allowMetered,automatic)==true){ "Выбранная сеть отключилась. Загрузка остановлена" }
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
                    store.put(INSTALL_MESSAGE,"")
                    // Only obsolete updater-owned APKs; never logs, installed apps or user-selected files.
                    ready.parentFile?.listFiles()?.filter {it!=ready&&it.isFile&&Regex("JelandOS-[0-9]+\\.apk").matches(it.name)}?.forEach {it.delete()}
                }
                mutable.update {it.copy(downloaded=true,message="Обновление проверено. Установите его на стоянке.")}
                report("UPDATE_READY version=${candidate.manifest.versionCode}")
            }catch(c:CancellationException){throw c}
            catch(e:Exception){mutable.update {it.copy(downloaded=false,message="Загрузка не завершена: ${reason(e)}")};report("UPDATE_DOWNLOAD_FAILED ${e.javaClass.simpleName}: ${reason(e)}")}
            finally {mutable.update {it.copy(downloading=false)}}
        }
    }
    fun cancelDownload() {
        downloadJob?.cancel()
        mutable.update {it.copy(message="Загрузка остановлена. Установленная версия не изменена.")}
    }
    fun install(useSession:Boolean=true) {
        if(state.value.cleaningApks)return
        if(state.value.downloading||state.value.checking||state.value.installing||downloadJob?.isActive==true||installJob?.isActive==true)return
        val candidate=state.value.available?.takeIf {state.value.downloaded}?:return
        installAssist.cancel()
        report("UPDATE_INSTALL_REQUEST version=${candidate.manifest.versionCode} permission=${canInstall()}")
        if(!canInstall()) {
            mutable.update {it.copy(installPermission=false,message="Разрешите установку обновлений для Jeland OS и повторите установку.")}
            runCatching {app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onFailure {installMessage("Экран разрешения недоступен. Разрешите установку через START.cmd → U или комплектный установщик.")}
            return
        }
        installAssist.refresh()
        if(installAssist.state.value.enabled&&!installAssist.state.value.available) {
            installMessage("Включите «Jeland OS — помощь при установке» в специальных возможностях Android и повторите установку.")
            installAssist.openPermission();return
        }
        if(!useSession) {
            installJob=scope.launch {
                mutable.update {it.copy(installing=true,installFallback=false)}
                installMessage("Проверяем APK перед установкой…")
                try {
                    val ready=file(candidate)
                    withContext(Dispatchers.IO){validateApk(ready,candidate)}
                    check(foreground){"Вернитесь в Jeland OS и повторите установку"}
                    confirmation=null;installWatchdog?.cancel()
                    installer.abandon(store.string(INSTALL_SESSION,"-1").toIntOrNull()?:-1)
                    store.put(INSTALL_SESSION,"-1");store.put(INSTALL_METHOD,"package_intent")
                    val uri=FileProvider.getUriForFile(app,app.packageName+".updates",ready)
                    installAssist.arm()
                    app.startActivity(Intent(app,UpdateInstallActivity::class.java).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    report("UPDATE_INSTALL_NATIVE_REQUEST version=${candidate.manifest.versionCode} permission=${canInstall()}")
                    installMessage("Открываем системное подтверждение установки…")
                }catch(c:CancellationException){throw c}
                catch(e:Exception){nativeInstallFailed(e)}
            }
            return
        }
        confirmation?.let {openConfirmation(it);return}
        installJob=scope.launch {
            mutable.update {it.copy(installing=true,installFallback=false,message="Проверяем APK перед установкой…")}
            var session=-1
            try {
                val ready=file(candidate)
                session=withContext(Dispatchers.IO){validateApk(ready,candidate);installer.prepare(ready)}
                check(foreground){"Вернитесь в Jeland OS и повторите установку"}
                installer.abandon(store.string(INSTALL_SESSION,"-1").toIntOrNull()?:-1)
                if(store is DurableSettingsStore)store.putDurable(INSTALL_SESSION,session.toString())
                else store.put(INSTALL_SESSION,session.toString())
                store.put(INSTALL_METHOD,"session")
                installMessage("Ожидаем системный установщик…")
                installAssist.arm()
                installer.commit(session)
                report("UPDATE_INSTALL_COMMIT session=$session version=${candidate.manifest.versionCode}")
                installWatchdog?.cancel();installWatchdog=scope.launch {
                    delay(20_000)
                    if(state.value.installing) {
                        mutable.update {it.copy(installing=false,installFallback=true)}
                        installAssist.cancel()
                        installMessage("Установщик не ответил. Нажмите «Установить обновление» ещё раз; файл сохранён.")
                        report("UPDATE_INSTALL_TIMEOUT session=$session")
                    }
                }
            }catch(c:CancellationException){throw c}
            catch(e:Exception){installAssist.cancel();installer.abandon(session);store.put(INSTALL_SESSION,"-1");mutable.update {it.copy(installing=false,installFallback=true)}
                installMessage("Установка не открыта: ${reason(e)}");report("UPDATE_INSTALL_FAILED ${e.javaClass.simpleName}: ${reason(e)}")}
        }
    }
    internal fun nativeInstallerOpened(intent:Intent) {
        val component=intent.resolveActivity(app.packageManager)?.flattenToShortString().orEmpty()
        store.put(INSTALL_COMPONENT,component)
        report("UPDATE_INSTALL_NATIVE_LAUNCHED action=${intent.action} component=$component permission=${canInstall()}")
        installMessage("Подтвердите установку в системном окне. Данные приложения сохраняются.")
    }
    internal fun nativeInstallFailed(e:Exception) {
        installAssist.cancel()
        mutable.update {it.copy(installing=false,installFallback=true,installPermission=canInstall())}
        installMessage("Не удалось открыть установку: ${reason(e)}. APK сохранён; можно попробовать другой способ.")
        report("UPDATE_INSTALL_NATIVE_FAILED ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}")
    }
    internal fun nativeInstallReturned(result:Int,data:Intent?) {
        installAssist.cancel()
        val legacy=data?.getIntExtra("android.intent.extra.INSTALL_RESULT",Int.MIN_VALUE)
        store.put(INSTALL_RESULT,"activity=$result legacy=$legacy")
        report("UPDATE_INSTALL_NATIVE_RESULT activity=$result legacy=$legacy permission=${canInstall()}")
        val expected=state.value.available?.manifest?.versionCode
        val actual=runCatching {app.packageManager.getPackageInfo(app.packageName,0).longVersionCode}.getOrDefault(0)
        val installed=expected!=null&&actual>=expected
        mutable.update {it.copy(installing=false,installFallback=!installed,installPermission=canInstall(),downloaded=it.downloaded&&!installed)}
        if(installed){store.put(PENDING,"");installMessage("Обновление установлено");return}
        val message=when {
            !canInstall()->"Установка не разрешена. Разрешите её в Android или выполните start.cmd → U. APK сохранён."
            result==Activity.RESULT_CANCELED->"Системный установщик закрылся без установки. APK сохранён; можно попробовать другой способ."
            else->"Установка не подтверждена (результат $result, код $legacy). APK сохранён; можно попробовать другой способ."
        }
        installMessage(message)
    }
    fun clearDownloadedApks(){scope.launch {clearDownloadedApksNow()}}
    internal suspend fun apkCacheInfo()=withContext(Dispatchers.IO){OwnUpdateApkCache.inspect(app.filesDir)}
    internal suspend fun clearDownloadedApksNow():UpdateApkCleanupResult=withContext(Dispatchers.Main.immediate) {
        if(state.value.cleaningApks||state.value.downloading||state.value.installing||state.value.checking||
            downloadJob?.isActive==true||installJob?.isActive==true||checkJob?.isActive==true)
            return@withContext UpdateApkCleanupResult(busy=true)
        mutable.update {it.copy(cleaningApks=true)}
        try {
            val result=withContext(Dispatchers.IO) {
                val id=store.string(INSTALL_SESSION,"-1").toIntOrNull()?:-1
                val session=app.packageManager.packageInstaller.mySessions.firstOrNull {it.sessionId==id}
                check(session?.isActive!=true){"Дождитесь завершения установки и повторите очистку"}
                if(session!=null)app.packageManager.packageInstaller.abandonSession(id)
                OwnUpdateApkCache.clear(app.filesDir)
            }
            confirmation=null;installWatchdog?.cancel();store.put(INSTALL_SESSION,"-1")
            installAssist.cancel()
            val ready=state.value.available?.let {file(it).isFile}==true
            if(!ready)store.put(PENDING,"")
            store.put(LAST_AUTO_DOWNLOAD,System.currentTimeMillis().toString())
            mutable.update {it.copy(downloaded=ready,installFallback=false)}
            installMessage(if(result.failed>0)"Удалено файлов: ${result.deleted}. Не удалось удалить: ${result.failed}." else
                if(result.deleted==0)"Скачанных APK для очистки нет" else "Удалено файлов: ${result.deleted} · освобождено ${result.bytes/1048576} МБ")
            report("UPDATE_CACHE_CLEARED files=${result.deleted} bytes=${result.bytes} failed=${result.failed}")
            result
        }catch(e:Exception) {
            installMessage("Очистка не завершена: ${reason(e)}");report("UPDATE_CACHE_CLEAR_FAILED ${e.javaClass.simpleName}")
            UpdateApkCleanupResult(failed=1)
        }finally {mutable.update {it.copy(cleaningApks=false)}}
    }
    private fun installMessage(message:String) {store.put(INSTALL_MESSAGE,message);mutable.update {it.copy(message=message)}}
    internal fun installStatus(intent:Intent) {
        val id=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)
        if(id<0||id!=store.string(INSTALL_SESSION,"-1").toIntOrNull())return
        installWatchdog?.cancel()
        val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        report("UPDATE_INSTALL_STATUS session=$id status=$status detail="+intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty().take(200))
        mutable.update {it.copy(installing=false)}
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION") val next=intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if(next==null){installAssist.cancel();installMessage("Установщик не передал окно подтверждения. Повторите установку.");return}
            confirmation=next
            if(foreground)openConfirmation(next)else {installAssist.cancel();installMessage("Установщик готов. Нажмите «Установить обновление», чтобы открыть подтверждение.")}
            return
        }
        confirmation=null;store.put(INSTALL_SESSION,"-1")
        installAssist.cancel()
        mutable.update {it.copy(installFallback=status!=PackageInstaller.STATUS_SUCCESS)}
        val message=when(status) {
            PackageInstaller.STATUS_SUCCESS->{store.put(PENDING,"");mutable.update {it.copy(downloaded=false)};"Обновление установлено"}
            PackageInstaller.STATUS_FAILURE_ABORTED->"Установка отменена. APK сохранён, можно повторить."
            PackageInstaller.STATUS_FAILURE_BLOCKED->"Android заблокировал установку. Проверьте разрешение установки для Jeland OS. APK сохранён."
            PackageInstaller.STATUS_FAILURE_STORAGE->"Недостаточно места для установки. Освободите память и повторите."
            PackageInstaller.STATUS_FAILURE_CONFLICT->"Установка отклонена: конфликт версии или подписи приложения."
            PackageInstaller.STATUS_FAILURE_INVALID->"Android не смог установить APK. Повторите загрузку; причина сохранена в журнале."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE->"Эта версия APK несовместима с устройством."
            else->"Установка не выполнена (код $status). APK сохранён, причина записана в журнале."
        }
        installMessage(message)
    }
    private fun openConfirmation(intent:Intent) {
        try {
            val id=store.string(INSTALL_SESSION,"-1").toIntOrNull()?:-1
            check(id>=0){"Установочная сессия отсутствует"}
            installAssist.arm()
            app.startActivity(Intent(app,UpdateInstallActivity::class.java)
                .putExtra(UpdateInstallActivity.CONFIRMATION,Intent(intent))
                .putExtra(UpdateInstallActivity.SESSION,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            installMessage("Подтвердите обновление в системном установщике. Данные приложения сохраняются.")
            report("UPDATE_INSTALL_CONFIRMATION_OPENED session=$id")
        }catch(e:Exception){installAssist.cancel();confirmation=null;mutable.update {it.copy(installing=false,installFallback=true)};installMessage("Не удалось открыть установщик: ${reason(e)}");report("UPDATE_INSTALL_CONFIRMATION_FAILED ${e.javaClass.simpleName}")}
    }
    internal fun sessionConfirmationReturned(id:Int) {
        // Android 11 may return RESULT_CANCELED even after acceptance. The session callback,
        // rather than this Activity result, is authoritative for both success and cancellation.
        if(id<0||id!=store.string(INSTALL_SESSION,"-1").toIntOrNull())return
        report("UPDATE_INSTALL_CONFIRMATION_RETURNED session=$id")
        mutable.update {it.copy(installing=true)}
        installMessage("Ожидаем результат установки от Android…")
        installWatchdog?.cancel();installWatchdog=scope.launch {
            delay(20_000)
            if(id==store.string(INSTALL_SESSION,"-1").toIntOrNull()&&state.value.installing) {
                mutable.update {it.copy(installing=false,installFallback=true)}
                installAssist.cancel()
                installMessage("Android не подтвердил установку. APK сохранён; доступен запасной способ.")
                report("UPDATE_INSTALL_CONFIRMATION_TIMEOUT session=$id")
            }
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
    private fun reason(e:Exception)=when(e) {
        is SecurityException->"Android запретил действие. Проверьте разрешения Jeland OS в штатных настройках."
        is android.content.ActivityNotFoundException->"На устройстве не найдено системное окно установки или разрешений."
        is IllegalStateException,is IllegalArgumentException->e.message?.take(220)?:"Проверка не пройдена"
        else->"Сеть или файл недоступны. Повторите позже"
    }
    companion object {
        private const val AUTO_CHECK="updates.auto_check";private const val AUTO_DOWNLOAD="updates.auto_download_wifi"
        private const val LAST_ATTEMPT="updates.last_attempt";private const val LAST_SUCCESS="updates.last_success"
        private const val LAST_AUTO_DOWNLOAD="updates.last_auto_download";private const val PENDING="updates.pending"
        private const val INSTALL_SESSION="updates.install_session";private const val INSTALL_MESSAGE="updates.install_message"
        private const val INSTALL_METHOD="updates.install_method";private const val INSTALL_COMPONENT="updates.install_component"
        private const val INSTALL_RESULT="updates.install_result"
    }
}
