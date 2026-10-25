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
            maps.installOverview(Uri.fromFile(maps.overviewFile()))
            val installed=maps.overviewFile();val digest=hash(installed)
            val revision=maps.revision.value
            assertTrue(installed.name.contains("custom"))
            val bad=File(folder,"bad.pmtiles").apply {writeBytes(ByteArray(150))}
            assertTrue(runCatching {maps.installOverview(Uri.fromFile(bad))}.isFailure)
            assertEquals(digest,hash(maps.overviewFile()))
            assertEquals(revision,maps.revision.value)
            assertEquals(originalMap,maps.mapFile());assertEquals(graphLength,maps.graphFile.length())
            assertEquals(region.id,maps.region.value!!.id)
        }finally {
            check(folder.canonicalFile.parentFile==base.cacheDir.canonicalFile&&folder.name==name)
            folder.deleteRecursively();base.deleteSharedPreferences("$name-navigation-regions")
        }
    }
}
