package ru.jeland.os.navigation.comaps

import android.content.Context
import android.content.ContextWrapper
import java.io.File

/** Keep the upstream core's settings, caches and maps in its own application folders. */
internal class CoMapsContext(context:Context):ContextWrapper(context.applicationContext) {
    private fun folder(parent:File?)=parent?.let {File(it,"comaps").apply {check(isDirectory||mkdirs())}}
    override fun getApplicationContext():Context=this
    override fun getFilesDir():File=checkNotNull(folder(baseContext.filesDir))
    override fun getCacheDir():File=checkNotNull(folder(baseContext.cacheDir))
    override fun getExternalFilesDir(type:String?):File?=folder(baseContext.getExternalFilesDir(type))
    override fun getExternalFilesDirs(type:String?):Array<File> =baseContext.getExternalFilesDirs(type).mapNotNull {folder(it)}.toTypedArray()
    fun prepareRenderer() {
        // Use OpenGL ES on Android 11 head units. The
        // upstream default is Vulkan, which also corrupts the emulator's HWUI.
        val file=android.util.AtomicFile(File(filesDir,"settings.ini"))
        val previous=runCatching {file.openRead().bufferedReader().use {it.readText()}}.getOrDefault("")
        val lines=previous.lineSequence().filter {it.isNotBlank()&&!it.startsWith("PreferredGraphicsAPI=")}.toList()
        val content=(lines+"PreferredGraphicsAPI=OpenGLES3").joinToString("\n",postfix="\n")
        if(content==previous)return
        val stream=file.startWrite()
        try {stream.write(content.toByteArray());file.finishWrite(stream)}catch(error:Exception){file.failWrite(stream);throw error}
    }
}
