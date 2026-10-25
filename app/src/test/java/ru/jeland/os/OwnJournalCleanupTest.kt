package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.OwnJournalCleanup
import java.io.File
import java.nio.file.Files

class OwnJournalCleanupTest {
    @Test fun removesOnlyExactDirectJournalsAndResetsActiveWithoutTouchingOtherData() {
        val root=Files.createTempDirectory("jeland-cleanup-test-").toFile()
        val active=File(root,"JelandOS_vehicle_1.log").apply {writeText("old active data".repeat(100))}
        val old=File(root,"JelandOS_vehicle_2.log").apply {writeText("old journal")}
        val prefs=File(root,"settings.json").apply {writeText("keep")}
        val malformed=File(root,"JelandOS_vehicle_notes.log").apply {writeText("keep")}
        val maps=File(root,"map.pmtiles").apply {writeText("keep")}
        val nested=File(root,"nested").apply {mkdir()}
        val nestedLog=File(nested,"JelandOS_vehicle_3.log").apply {writeText("keep")}
        try {
            val result=OwnJournalCleanup.clear(listOf(root,File(root,".")),active,"NEW SESSION\n")
            assertEquals(1,result.deleted);assertEquals(1,result.reset);assertEquals(0,result.failed)
            assertTrue(result.freedBytes>0);assertFalse(old.exists());assertEquals("NEW SESSION\n",active.readText())
            for(file in listOf(prefs,malformed,maps,nestedLog))assertEquals("keep",file.readText())
            active.appendText("next event\n");assertTrue(active.readText().contains("next event"))
        }finally {listOf(nestedLog,nested,maps,malformed,prefs,old,active,root).forEach {it.delete()}}
    }
    @Test fun neverTruncatesActiveFileOutsideAllowedRoot() {
        val root=Files.createTempDirectory("jeland-cleanup-test-").toFile()
        val outside=Files.createTempFile("jeland-cleanup-outside-",".log").toFile().apply {writeText("keep")}
        val old=File(root,"JelandOS_vehicle_3.log").apply {writeText("old")}
        try {
            val result=OwnJournalCleanup.clear(listOf(root),outside,"NEW")
            assertEquals(1,result.deleted);assertEquals(0,result.reset);assertEquals("keep",outside.readText())
        }finally {listOf(old,root,outside).forEach {it.delete()}}
    }
}
