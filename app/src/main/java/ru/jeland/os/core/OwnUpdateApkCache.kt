package ru.jeland.os.core

import java.io.File
import java.nio.file.Files

data class UpdateApkCacheInfo(val count:Int=0,val bytes:Long=0)
data class UpdateApkCleanupResult(val deleted:Int=0,val bytes:Long=0,val failed:Int=0,val busy:Boolean=false)

/** Only direct updater-owned files. Never walks a tree or follows symbolic links. */
object OwnUpdateApkCache {
    private fun files(appFiles:File):List<File> {
        if(Files.isSymbolicLink(appFiles.toPath()))return emptyList()
        val root=appFiles.canonicalFile
        val updates=File(root,"updates")
        if(Files.isSymbolicLink(updates.toPath())||updates.canonicalFile.parentFile!=root)return emptyList()
        return listOf("ready","staging").flatMap {name->
            val folder=File(updates,name)
            if(Files.isSymbolicLink(folder.toPath())||folder.canonicalFile.parentFile!=updates.canonicalFile)emptyList()
            else folder.listFiles().orEmpty().filter {file->
                val owned=if(name=="ready")Regex("JelandOS-[0-9]+\\.apk").matches(file.name) else file.name=="update.part"
                owned&&file.isFile&&!Files.isSymbolicLink(file.toPath())&&file.canonicalFile.parentFile==folder.canonicalFile
            }
        }
    }
    fun inspect(appFiles:File):UpdateApkCacheInfo=files(appFiles).let {UpdateApkCacheInfo(it.size,it.sumOf(File::length))}
    fun clear(appFiles:File):UpdateApkCleanupResult {
        return remove(files(appFiles))
    }
    /** A newer download must survive a restart of an older installed version. */
    fun clearInstalled(appFiles:File,installedVersion:Long,clearStaging:Boolean=false):UpdateApkCleanupResult =
        remove(files(appFiles).filter {file->
            if(file.name=="update.part")clearStaging
            else file.name.removePrefix("JelandOS-").removeSuffix(".apk").toLongOrNull()?.let {it<=installedVersion}==true
        })
    private fun remove(files:List<File>):UpdateApkCleanupResult {
        var count=0;var bytes=0L;var failed=0
        for(file in files) {
            val size=file.length()
            if(file.delete()){count++;bytes+=size}else failed++
        }
        return UpdateApkCleanupResult(count,bytes,failed)
    }
}
