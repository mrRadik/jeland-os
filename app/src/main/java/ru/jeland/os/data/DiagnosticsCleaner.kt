package ru.jeland.os.data

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import ru.jeland.os.core.OwnJournalCleanup
import java.io.File

data class ClearedDiagnostics(val journals:Int,val archives:Int,val activeReset:Int,val failed:Int,val freedBytes:Long)

/** User-confirmed deletion of our direct journals and our own exported MediaStore ZIPs only. */
object DiagnosticsCleaner {
    internal fun ownExport(name:String?,path:String?,owner:String?,packageName:String)=
        owner==packageName&&path=="${Environment.DIRECTORY_DOWNLOADS}/JelandOS/"&&
            name!=null&&Regex("JelandOSLogs_[0-9]{8}_[0-9]{6}_[0-9a-f]{8}\\.zip").matches(name)
    fun clear(context:Context,active:File,header:String):ClearedDiagnostics {
        val logs=OwnJournalCleanup.clear(listOfNotNull(context.getExternalFilesDir(null),context.filesDir),active,header)
        var archives=0;var failed=logs.failed;var bytes=logs.freedBytes
        val collection=MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder="${Environment.DIRECTORY_DOWNLOADS}/JelandOS/"
        val id=MediaStore.MediaColumns._ID;val name=MediaStore.MediaColumns.DISPLAY_NAME
        val path=MediaStore.MediaColumns.RELATIVE_PATH;val owner=MediaStore.MediaColumns.OWNER_PACKAGE_NAME
        val size=MediaStore.MediaColumns.SIZE
        try {
            val cursor=context.contentResolver.query(collection,arrayOf(id,name,path,owner,size),
                "$owner=? AND $path=?",arrayOf(context.packageName,folder),null)
                ?:throw java.io.IOException("Downloads unavailable")
            cursor.use {rows->while(rows.moveToNext()) {
                val fileName=rows.getString(1);val relative=rows.getString(2);val packageOwner=rows.getString(3)
                if(!ownExport(fileName,relative,packageOwner,context.packageName))continue
                val uri=ContentUris.withAppendedId(collection,rows.getLong(0))
                val length=rows.getLong(4).coerceAtLeast(0)
                try {
                    if(context.contentResolver.delete(uri,"$owner=? AND $path=? AND $name=?",
                        arrayOf(context.packageName,folder,fileName))!=1)failed++
                    else {archives++;bytes+=length}
                }catch(_:Exception){failed++}
            }}
        }catch(_:Exception){failed++}
        return ClearedDiagnostics(logs.deleted,archives,logs.reset,failed,bytes)
    }
}
