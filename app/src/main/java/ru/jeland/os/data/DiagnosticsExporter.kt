package ru.jeland.os.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.BuildConfig
import ru.jeland.os.core.VehicleLinkState
import ru.jeland.os.core.TyreTemperatureDiagnostics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SavedDiagnostics(val uri:Uri,val name:String,val logCount:Int) {
    val location:String get()="Download/JelandOS/$name"
}

/** User-initiated export of this app's files only. No GET/SET, logcat, root or storage permission. */
object DiagnosticsExporter {
    private const val MAX_TOTAL=32L*1024*1024
    private data class LogPart(val file:File,val start:Long,val length:Long)
    fun save(context:Context,link:VehicleLinkState,tyreDiagnostics:TyreTemperatureDiagnostics=TyreTemperatureDiagnostics()):SavedDiagnostics {
        val name="JelandOSLogs_${SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date())}_${UUID.randomUUID().toString().take(8)}.zip"
        val files=listOfNotNull(context.getExternalFilesDir(null),context.filesDir)
            .distinctBy {it.canonicalPath}.flatMap {it.listFiles().orEmpty().toList()}
            .filter {it.isFile && it.name.startsWith("JelandOS_vehicle_") && it.extension=="log" && it.canonicalFile.parentFile==it.parentFile?.canonicalFile}
            .sortedByDescending {it.lastModified()}
        var budget=MAX_TOTAL
        val selected=files.mapNotNull {file->
            val end=file.length();val length=minOf(end,budget)
            if(length<=0)null else {budget-=length;LogPart(file,end-length,length)}
        }
        val metadata=JSONObject().apply {
            put("schema",1);put("appVersion",BuildConfig.VERSION_NAME);put("versionCode",BuildConfig.VERSION_CODE)
            put("createdUtcMillis",System.currentTimeMillis());put("sdk",Build.VERSION.SDK_INT)
            put("mode",if(link.demo)"DEMO"else"VEHICLE");put("connected",link.connected);put("writesEnabled",link.writesEnabled)
            put("detail",link.detail);put("lastCommand",link.lastCommand?:JSONObject.NULL)
            val startupStore=AndroidSettingsStore(context)
            put("updates",JSONObject()
                .put("installPermission",runCatching {context.packageManager.canRequestPackageInstalls()}.getOrDefault(false))
                .put("installSession",startupStore.string("updates.install_session","-1"))
                .put("installMessage",startupStore.string("updates.install_message",""))
                .put("pendingVersion",runCatching {JSONObject(startupStore.string("updates.pending","{}")).optLong("versionCode",0)}.getOrDefault(0)))
            put("startup",JSONObject().put("enabled",startupStore.string(SystemStartup.ENABLED_KEY,"false")=="true")
                .put("permissionGranted",runCatching {android.provider.Settings.canDrawOverlays(context)}.getOrDefault(false))
                .put("lastBoot",startupStore.string(SystemStartup.LAST_BOOT_KEY,"none"))
                .put("lastWake",startupStore.string(SystemStartup.LAST_WAKE_KEY,"none"))
                .put("wakePending",startupStore.string(SystemStartup.WAKE_PENDING_KEY,"false"))
                .put("wakeWatcher",startupStore.string(SystemStartup.WATCH_STATUS_KEY,"none")))
            put("tyreTemperatureDebug",tyreDiagnosticsJson(tyreDiagnostics))
            put("availableLogFiles",files.size);put("includedLogFiles",selected.size)
            put("partial",selected.size<files.size || selected.any {it.start>0})
            put("logFiles",JSONArray().apply {selected.forEach {(file,start,size)->put(JSONObject().put("name",file.name).put("bytes",size).put("tailOnly",start>0))}})
            put("raw",JSONArray().apply {link.raw.forEach {(key,sample)->put(JSONObject().put("key",key.toString())
                .put("values",sample.values?.let {JSONArray(it)}?:JSONObject.NULL)
                .put("ageMillis",(SystemClock.elapsedRealtime()-sample.receivedMillis).coerceAtLeast(0)))}})
        }
        val resolver=context.contentResolver
        val collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri=resolver.insert(collection,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/JelandOS")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        })?:error("Не удалось создать ZIP в Загрузках")
        try {
            val output=resolver.openOutputStream(uri)?:error("Не удалось открыть ZIP для записи")
            ZipOutputStream(output).use {zip->
                fun text(entry:String,value:String){zip.putNextEntry(ZipEntry(entry));zip.write(value.toByteArray(Charsets.UTF_8));zip.closeEntry()}
                text("diagnostics.json",metadata.toString(2))
                text("README.txt","Jeland OS ${BuildConfig.VERSION_NAME}\nMode: ${if(link.demo)"DEMO"else"VEHICLE"}\nOnly this app's journals and cached RAW. Export sends no vehicle commands.\nNewest logs included up to 32 MiB; diagnostics.json marks any omitted/truncated files.\nLogs may contain vehicle data. Share only with people you trust.\n")
                for((file,start,length) in selected) {
                    zip.putNextEntry(ZipEntry("journals/${file.name}"))
                    java.io.RandomAccessFile(file,"r").use {input->
                        input.seek(start)
                        val buffer=ByteArray(8192);var remaining=length
                        while(remaining>0){val count=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(count<0)break;zip.write(buffer,0,count);remaining-=count}
                    }
                    zip.closeEntry()
                }
            }
            check(resolver.update(uri,ContentValues().apply {put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)==1){"Не удалось завершить сохранение ZIP"}
            return SavedDiagnostics(uri,name,selected.size)
        }catch(error:Exception){runCatching {resolver.delete(uri,null,null)};throw error}
    }
    internal fun tyreDiagnosticsJson(s:TyreTemperatureDiagnostics)=JSONObject().apply {
        put("active",s.active);put("phase",s.phase.name);put("attempts",s.attempts)
        put("interface",s.interfaceName);put("tcpConnected",s.tcpConnected)
        put("clock","elapsedRealtimeMillis");put("exportedAtElapsedMillis",SystemClock.elapsedRealtime())
        put("lastError",s.lastError);put("failedPhase",s.failedPhase?.name?:JSONObject.NULL)
        put("errorAtMillis",s.errorAtMillis?:JSONObject.NULL);put("retryAtMillis",s.retryAtMillis?:JSONObject.NULL)
        put("trace",JSONObject().put("phase",s.trace.phase.name).put("command",s.trace.command)
            .put("rxBytes",s.trace.receivedBytes).put("txBytes",s.trace.sentBytes)
            .put("textTail",s.trace.textTail).put("hexTail",s.trace.hexTail))
        put("lastPayload",s.payload.take(2048));put("packetCount",s.packetCount)
        put("lastPacketAtMillis",s.lastPacketAtMillis?:JSONObject.NULL)
        put("samples",JSONArray().apply {s.samples.forEach {(wheel,value)->put(JSONObject().put("wheel",wheel.name)
            .put("raw",value.raw).put("status",value.status).put("celsius",value.celsius?:JSONObject.NULL).put("atMillis",value.atMillis))}})
    }
}
