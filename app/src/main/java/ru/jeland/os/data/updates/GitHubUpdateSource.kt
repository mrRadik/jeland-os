package ru.jeland.os.data.updates

import org.json.JSONObject
import ru.jeland.os.core.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream

object GitHubUpdateCodec {
    fun manifest(json:JSONObject)=AppUpdateManifest(json.getInt("schema"),json.getString("applicationId"),
        json.getLong("versionCode"),json.getString("versionName"),json.getInt("minSdk"),
        json.getString("apkName"),json.getLong("size"),json.getString("sha256")).also(AppUpdatePolicy::validate)
    fun manifestJson(m:AppUpdateManifest)=JSONObject().put("schema",m.schema).put("applicationId",m.applicationId)
        .put("versionCode",m.versionCode).put("versionName",m.versionName).put("minSdk",m.minSdk)
        .put("apkName",m.apkName).put("size",m.size).put("sha256",m.sha256)
    fun pending(c:AppUpdateCandidate)=manifestJson(c.manifest).put("apkUrl",c.apk.url).put("releaseTag",c.releaseTag).toString()
    fun restore(value:String):AppUpdateCandidate {
        val j=JSONObject(value);val m=manifest(j);val url=j.getString("apkUrl")
        return AppUpdatePolicy.candidate(m,listOf(UpdateAsset(m.apkName,url,m.size)),j.getString("releaseTag"),url.substringBeforeLast('/')+"/"+AppUpdatePolicy.MANIFEST)
    }
}

/** Public GitHub Releases only. No token, login, cookies or arbitrary endpoint settings. */
class GitHubUpdateSource {
    fun latest():AppUpdateCandidate {
        val release=JSONObject(text(AppUpdatePolicy.API))
        check(!release.optBoolean("draft")&&!release.optBoolean("prerelease")){"Доступен только тестовый релиз"}
        val items=release.getJSONArray("assets")
        val assets=(0 until items.length()).map {i->items.getJSONObject(i).let {
            UpdateAsset(it.getString("name"),it.getString("browser_download_url"),it.getLong("size"))}}
        val manifest=assets.singleOrNull {it.name==AppUpdatePolicy.MANIFEST}?:error("В релизе пока нет jelandos-update.json")
        check(manifest.size in 1..MAX_METADATA.toLong()&&AppUpdatePolicy.releaseUrl(manifest.url,AppUpdatePolicy.MANIFEST)){"Некорректное описание релиза"}
        return AppUpdatePolicy.candidate(GitHubUpdateCodec.manifest(JSONObject(text(manifest.url))),assets,release.getString("tag_name"),manifest.url)
    }
    private fun text(url:String):String {
        val connection=open(url)
        try {
            check(connection.contentLengthLong<=MAX_METADATA){"Описание релиза слишком большое"}
            return connection.inputStream.use {input->
                val output=ByteArrayOutputStream();val bytes=ByteArray(8192)
                while(true){val n=input.read(bytes);if(n<0)break;check(output.size()+n<=MAX_METADATA){"Описание релиза слишком большое"};output.write(bytes,0,n)}
                output.toString(Charsets.UTF_8.name())
            }
        }finally {connection.disconnect()}
    }
    fun open(url:String,network:android.net.Network?=null):HttpURLConnection {
        var next=url
        repeat(6) {
            val uri=URI(next)
            check(uri.scheme=="https"&&uri.userInfo==null&&uri.port in listOf(-1,443)&&uri.host?.lowercase() in HOSTS){"Небезопасный адрес обновления"}
            val connection=(network?.openConnection(URL(next))?:URL(next).openConnection()) as HttpURLConnection
            try {
                connection.instanceFollowRedirects=false;connection.connectTimeout=12000;connection.readTimeout=15000
                connection.setRequestProperty("User-Agent","JelandOS-Updater")
                connection.setRequestProperty("Accept","application/vnd.github+json")
                connection.setRequestProperty("Accept-Encoding","identity")
                val code=connection.responseCode
                if(code in listOf(301,302,303,307,308)) {
                    val location=checkNotNull(connection.getHeaderField("Location")){"Пустое перенаправление"}
                    next=URL(URL(next),location).toString();connection.disconnect()
                }else {
                    check(code!=404){"Публичный репозиторий или релиз пока недоступен"}
                    check(code!=403&&code!=429){"GitHub ограничил частоту запросов. Повторите позже"}
                    check(code==200){"Сервер обновлений ответил HTTP $code"}
                    return connection
                }
            }catch(e:Exception){connection.disconnect();throw e}
        }
        error("Слишком много перенаправлений GitHub")
    }
    companion object {
        private const val MAX_METADATA=256*1024
        private val HOSTS=setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com")
    }
}
