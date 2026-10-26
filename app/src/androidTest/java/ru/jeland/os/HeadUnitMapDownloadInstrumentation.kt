package ru.jeland.os

import android.app.DownloadManager
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.*
import java.io.File
import java.security.MessageDigest

class HeadUnitMapDownloadInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture="https://github.com/mrRadik/jeland-os/releases/download/maps-2026-10-26/maps.json"
    private class MissingProvider:MapSystemDownloadDriver {
        var enqueueCalls=0
        override fun enqueue(request:DownloadManager.Request):Long {enqueueCalls++;throw IllegalArgumentException("Unknown URL content://downloads/my_downloads")}
        override fun query(id:Long):Cursor=throw IllegalArgumentException("Unknown URL content://downloads/my_downloads")
        override fun uri(id:Long):Uri?=null
        override fun remove(id:Long){}
    }
    @Test fun missingProviderFallsBackAndPartialFileSurvivesRecreation()=runBlocking<Unit> {
        val name="qa-hu-map-${java.util.UUID.randomUUID()}";val driver=MissingProvider()
        var downloads=NavigationDownloads(context,name,driver)
        try {
            downloads.start(fixture,"region","",true)
            val result=withTimeout(120000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNotNull(result.status,result.ready);assertFalse(result.systemOwned);assertEquals("file",result.ready!!.scheme)
            val full=File(result.ready!!.path!!).readBytes();assertTrue(full.size>100)
            downloads.close()
            // Recreate a real interrupted transfer with its original ETag and exact prefix.
            val prefs=context.getSharedPreferences(name,0)
            val token=prefs.getString("directToken",null)!!
            val partial=File(context.filesDir,"navigation-downloads/$token.part")
            partial.writeBytes(full.copyOfRange(0,full.size/2))
            File(result.ready!!.path!!).delete()
            val hash=MessageDigest.getInstance("SHA-256").digest(full).joinToString(""){"%02x".format(it)}
            prefs.edit().putBoolean("directComplete",false).putString("sha256",hash).commit()
            downloads=NavigationDownloads(context,name,driver)
            val restored=withTimeout(120000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNotNull(restored.status,restored.ready)
            assertArrayEquals(full,File(restored.ready!!.path!!).readBytes())
            assertEquals("The missing service is not retried",1,driver.enqueueCalls)
        }finally {downloads.cancel();downloads.close();context.deleteSharedPreferences(name)}
    }
    @Test fun fallbackRejectsWrongChecksum()=runBlocking<Unit> {
        val name="qa-hu-map-${java.util.UUID.randomUUID()}"
        val downloads=NavigationDownloads(context,name,MissingProvider())
        try {
            downloads.start(fixture,"region","0".repeat(64),true)
            val result=withTimeout(120000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNull(result.ready);assertTrue(result.status,result.status.contains("сумма"))
        }finally {downloads.cancel();downloads.close();context.deleteSharedPreferences(name)}
    }
    @Test fun oldSystemRequestRestoresWithoutItsMissingProvider()=runBlocking<Unit> {
        val name="qa-hu-map-${java.util.UUID.randomUUID()}"
        context.getSharedPreferences(name,0).edit().putLong("id",42).putString("sourceUrl",fixture)
            .putString("kind","region").putString("title","Тест восстановления").putBoolean("allowMetered",true).commit()
        val downloads=NavigationDownloads(context,name,MissingProvider())
        try {
            val result=withTimeout(120000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNotNull(result.status,result.ready);assertEquals("file",result.ready!!.scheme)
        }finally {downloads.cancel();downloads.close();context.deleteSharedPreferences(name)}
    }
    @Test fun realGithubMapDownloadsAndInstallsWithoutSystemProvider()=runBlocking<Unit> {
        val name="qa-hu-map-${java.util.UUID.randomUUID()}"
        val folder=File(context.cacheDir,name).apply {mkdirs()}
        val isolated=object:ContextWrapper(context) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(key:String,mode:Int)=context.getSharedPreferences("$name-$key",mode)
        }
        val downloads=NavigationDownloads(context,name,MissingProvider())
        try {
            val sha="920ed56e1464de9fd4743e4906d43385552308ca4cecf3d3d347c3a734f8103b"
            downloads.start("https://github.com/mrRadik/jeland-os/releases/download/maps-2026-10-26/russia-overview.pmtiles","overview",sha,true)
            val result=withTimeout(90000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNotNull(result.status,result.ready)
            val maps=OfflineMapManager(isolated)
            maps.installOverview(result.ready!!,result.sourceUrl)
            assertTrue(maps.overviewAvailable.value);assertEquals(6494875L,maps.overviewFile().length())
            downloads.finishInstallation();assertTrue(downloads.state.value.installed)
        }finally {
            downloads.cancel();downloads.close();context.deleteSharedPreferences(name)
            check(folder.canonicalFile.parentFile==context.cacheDir.canonicalFile&&folder.name==name)
            folder.deleteRecursively();context.deleteSharedPreferences("$name-navigation-regions")
        }
    }
}
