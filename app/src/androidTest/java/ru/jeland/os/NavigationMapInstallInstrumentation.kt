package ru.jeland.os

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.OfflineMapManager
import java.io.File
import java.security.MessageDigest

class NavigationMapInstallInstrumentation {
    @Test fun deletingMapsPersistsAcrossRestartAndVerifiedReinstallWorks()=runBlocking<Unit> {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-map-delete-${java.util.UUID.randomUUID()}"
        val folder=File(base.cacheDir,name).apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(key:String,mode:Int)=base.getSharedPreferences("$name-$key",mode)
        }
        try {
            val maps=OfflineMapManager(context);val original=maps.prepare()
            val overviewBackup=File(folder,"overview-backup.pmtiles").also {maps.overviewFile().copyTo(it)}
            val archive=File(folder,"region-backup.zip")
            java.util.zip.ZipOutputStream(archive.outputStream()).use {zip->
                for(file in maps.directory.listFiles().orEmpty().filter {it.isFile}) {
                    zip.putNextEntry(java.util.zip.ZipEntry(file.name));file.inputStream().use {it.copyTo(zip)};zip.closeEntry()
                }
            }
            val unrelated=File(folder,"navigation/another-region").apply {mkdirs()}
            File(unrelated,"manifest.json").writeText("""{"id":"another-region"}""")
            assertTrue(runCatching {maps.removeRegion("another-region")}.isFailure)
            assertTrue(maps.mapFile().exists())
            val oldDirectory=maps.directory
            maps.removeRegion(original.id)
            assertNull(maps.region.value);assertFalse(oldDirectory.exists())
            assertTrue(unrelated.exists());assertTrue(maps.overviewFile().exists())
            val reopened=OfflineMapManager(context)
            assertTrue(runCatching {reopened.prepare()}.isFailure)
            assertFalse(reopened.mapFile().exists())
            val style=org.json.JSONObject(ru.jeland.os.navigation.localStyle(reopened,false))
            assertEquals(0,style.getJSONObject("sources").getJSONObject("region").getJSONObject("data").getJSONArray("features").length())
            assertTrue(style.getJSONObject("sources").has("overview"))
            reopened.removeOverview()
            val empty=OfflineMapManager(context)
            assertTrue(runCatching {empty.prepare()}.isFailure)
            assertFalse(empty.overviewAvailable.value);assertFalse(empty.overviewFile().exists())
            empty.installOverview(Uri.fromFile(overviewBackup))
            assertTrue(empty.overviewAvailable.value)
            assertEquals(original.id,empty.importRegion(Uri.fromFile(archive)).id)
            assertEquals(original.id,OfflineMapManager(context).prepare().id)
        }finally {
            check(folder.canonicalFile.parentFile==base.cacheDir.canonicalFile&&folder.name==name)
            folder.deleteRecursively();base.deleteSharedPreferences("$name-navigation-regions")
        }
    }
    @Test fun validOverviewInstallsAndBadReplacementDoesNotTouchWorkingMaps()=runBlocking<Unit> {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-map-${java.util.UUID.randomUUID()}"
        val folder=File(base.cacheDir,name).apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getFilesDir()=folder
            override fun getSharedPreferences(key:String,mode:Int)=base.getSharedPreferences("$name-$key",mode)
        }
        fun hash(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
        try {
            val maps=OfflineMapManager(context)
            val region=maps.prepare()
            val originalMap=maps.mapFile();val graphLength=maps.graphFile.length()
            maps.installOverview(Uri.fromFile(maps.overviewFile()),"https://example.org/russia.pmtiles")
            val installed=maps.overviewFile();val digest=hash(installed)
            val revision=maps.revision.value
            assertTrue(installed.name.contains("custom"))
            assertEquals("https://example.org/russia.pmtiles",OfflineMapManager(context).overviewSourceUrl.value)
            val bad=File(folder,"bad.pmtiles").apply {writeBytes(ByteArray(150))}
            assertTrue(runCatching {maps.installOverview(Uri.fromFile(bad))}.isFailure)
            assertEquals(digest,hash(maps.overviewFile()))
            assertEquals(revision,maps.revision.value)
            assertEquals("https://example.org/russia.pmtiles",maps.overviewSourceUrl.value)
            assertEquals(originalMap,maps.mapFile());assertEquals(graphLength,maps.graphFile.length())
            assertEquals(region.id,maps.region.value!!.id)
        }finally {
            check(folder.canonicalFile.parentFile==base.cacheDir.canonicalFile&&folder.name==name)
            folder.deleteRecursively();base.deleteSharedPreferences("$name-navigation-regions")
        }
    }
}
