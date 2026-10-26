package ru.jeland.os.core

import java.net.URI

data class UpdateAsset(val name:String,val url:String,val size:Long)
data class AppUpdateManifest(
    val schema:Int,val applicationId:String,val versionCode:Long,val versionName:String,
    val minSdk:Int,val apkName:String,val size:Long,val sha256:String,
)
data class AppUpdateCandidate(val manifest:AppUpdateManifest,val apk:UpdateAsset,val releaseTag:String)
data class UpdateApkIdentity(val applicationId:String,val versionCode:Long,val versionName:String?,val minSdk:Int,val signers:Set<String>)

/** A phone hotspot is Wi-Fi even when Android marks it as metered. */
data class UpdateNetworkState(val internet:Boolean,val wifi:Boolean,val unmetered:Boolean) {
    fun allows(allowMobile:Boolean=false,automatic:Boolean=false)=internet&&
        if(automatic)wifi&&unmetered else (wifi||allowMobile)
}

/** No access token, arbitrary update URL, version-name ordering or implicit APK selection. */
object AppUpdatePolicy {
    const val REPOSITORY="mrRadik/jeland-os"
    const val API="https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val MANIFEST="jelandos-update.json"
    const val APPLICATION_ID="ru.jeland.os"
    const val MAX_APK_BYTES=512L*1024*1024
    const val CHECK_INTERVAL=6L*60*60*1000
    fun validate(m:AppUpdateManifest) {
        require(m.schema==1){"Неподдерживаемый формат обновления"}
        require(m.applicationId==APPLICATION_ID){"Обновление другого приложения"}
        require(m.versionCode>0&&m.versionName.isNotBlank()&&m.versionName.length<=100){"Некорректная версия"}
        require(m.minSdk>=30&&m.minSdk<=100){"Некорректная версия Android"}
        require(Regex("JelandOS-[A-Za-z0-9._-]+\\.apk").matches(m.apkName)&&".." !in m.apkName){"Некорректное имя APK"}
        require(m.size in 1..MAX_APK_BYTES){"Недопустимый размер APK"}
        require(Regex("[a-fA-F0-9]{64}").matches(m.sha256)){"Нет корректной SHA-256"}
    }
    fun releaseUrl(value:String,filename:String):Boolean=runCatching {
        val u=URI(value)
        u.scheme=="https"&&u.host.equals("github.com",true)&&u.userInfo==null&&u.port==-1&&u.query==null&&u.fragment==null&&
            u.path.startsWith("/$REPOSITORY/releases/download/")&&u.path.substringAfterLast('/')==filename&&
            u.path.split('/').none {it=="."||it==".."}&&u.path.substringAfter("/releases/download/").count {it=='/'}==1
    }.getOrDefault(false)
    fun candidate(m:AppUpdateManifest,assets:List<UpdateAsset>,tag:String,manifestUrl:String):AppUpdateCandidate {
        validate(m)
        require(tag.isNotBlank()&&tag.length<=200&&releaseUrl(manifestUrl,MANIFEST)){"Некорректный релиз"}
        val apk=assets.singleOrNull {it.name==m.apkName}?:error("В релизе нет единственного APK из описания")
        require(apk.size==m.size&&releaseUrl(apk.url,m.apkName)){"APK не соответствует описанию"}
        require(URI(apk.url).rawPath.substringBeforeLast('/')==URI(manifestUrl).rawPath.substringBeforeLast('/')){"Файлы из разных релизов"}
        return AppUpdateCandidate(m,apk,tag)
    }
    fun requireInstallable(m:AppUpdateManifest,installed:UpdateApkIdentity,apk:UpdateApkIdentity,sdk:Int) {
        validate(m)
        require(installed.applicationId==APPLICATION_ID&&apk.applicationId==APPLICATION_ID){"Пакет APK не подходит"}
        require(apk.versionCode==m.versionCode&&apk.versionCode>installed.versionCode){"Версия APK не подходит или не новее установленной"}
        require(apk.versionName==m.versionName&&apk.minSdk==m.minSdk&&m.minSdk<=sdk){"Параметры APK не совпадают с описанием"}
        require(apk.signers.isNotEmpty()&&apk.signers==installed.signers){"Подпись APK отличается от установленного Jeland OS. Обновление заблокировано"}
    }
    fun due(lastAttempt:Long,now:Long)=lastAttempt<=0||now<lastAttempt||now-lastAttempt>=CHECK_INTERVAL
}
