package ru.jeland.os.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

data class JournalCleanupResult(val deleted:Int=0,val reset:Int=0,val failed:Int=0,val freedBytes:Long=0)
/** Direct children with our exact journal name only; never follows links or walks subdirectories. */
object OwnJournalCleanup {
    private val journalName=Regex("JelandOS_vehicle_[0-9]+\\.log")
    fun clear(roots:List<File>,active:File,header:String):JournalCleanupResult {
        var deleted=0;var reset=0;var failed=0;var bytes=0L
        for(root in roots.distinctBy {it.canonicalPath}) {
            val canonicalRoot=root.canonicalFile
            val files=root.listFiles()
            if(files==null){if(root.exists())failed++;continue}
            for(file in files) {
                if(!journalName.matches(file.name)||!file.isFile||Files.isSymbolicLink(file.toPath())||file.canonicalFile.parentFile!=canonicalRoot)continue
                try {
                    val before=file.length()
                    if(file.canonicalFile==active.canonicalFile) {
                        RandomAccessFile(file,"rw").use {it.setLength(0);it.write(header.toByteArray(Charsets.UTF_8))}
                        reset++;bytes+=(before-file.length()).coerceAtLeast(0)
                    }else {
                        if(!file.delete())throw java.io.IOException("Cannot delete own journal")
                        deleted++;bytes+=before
                    }
                }catch(_:Exception){failed++}
            }
        }
        return JournalCleanupResult(deleted,reset,failed,bytes)
    }
}
