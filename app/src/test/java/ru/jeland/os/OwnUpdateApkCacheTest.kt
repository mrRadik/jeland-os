package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.OwnUpdateApkCache
import java.nio.file.Files

class OwnUpdateApkCacheTest {
    @Test fun cleanupRemovesOnlyOwnedDownloadedFiles() {
        val root=Files.createTempDirectory("apk-cleanup").toFile()
        try {
            fun file(path:String)=java.io.File(root,path).apply {parentFile!!.mkdirs();writeText("1234")}
            val apk=file("updates/ready/JelandOS-92.apk")
            val partial=file("updates/staging/update.part")
            val preserved=listOf(file("updates/ready/another.apk"),file("updates/ready/JelandOS-invalid.apk"),
                file("updates/ready/nested/JelandOS-93.apk"),file("navigation/map.pmtiles"),file("prefs/settings.xml"),file("base.apk"))
            assertEquals(2,OwnUpdateApkCache.inspect(root).count)
            val result=OwnUpdateApkCache.clear(root)
            assertEquals(2,result.deleted);assertEquals(8L,result.bytes);assertEquals(0,result.failed)
            assertFalse(apk.exists());assertFalse(partial.exists());preserved.forEach {assertTrue(it.path,it.isFile)}
            assertEquals(0,OwnUpdateApkCache.clear(root).deleted)
        }finally {root.deleteRecursively()}
    }
    @Test fun absentCacheDoesNotCreateDirectories() {
        val root=Files.createTempDirectory("apk-cleanup-empty").toFile()
        try {assertEquals(0,OwnUpdateApkCache.clear(root).deleted);assertEquals(0,root.listFiles()!!.size)}finally {root.deleteRecursively()}
    }
    @Test fun symbolicLinkCannotDeleteOutsideFile() {
        val root=Files.createTempDirectory("apk-cleanup-link").toFile()
        val outside=Files.createTempDirectory("apk-cleanup-outside").toFile()
        try {
            val target=java.io.File(outside,"JelandOS-92.apk").apply {writeText("keep")}
            val ready=java.io.File(root,"updates/ready").apply {mkdirs()}
            try {Files.createSymbolicLink(java.io.File(ready,target.name).toPath(),target.toPath())}
            catch(e:java.nio.file.FileSystemException){org.junit.Assume.assumeNoException(e)}
            assertEquals(0,OwnUpdateApkCache.clear(root).deleted);assertEquals("keep",target.readText())
        }finally {root.deleteRecursively();outside.deleteRecursively()}
    }
}
