package ru.jeland.os.data.updates

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.runBlocking
import ru.jeland.os.JelandApplication

/** Fixed maintenance operations for start.cmd. No paths, shell commands or install API. */
class UpdateMaintenanceProvider:ContentProvider() {
    override fun onCreate()=true
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle {
        check(Binder.getCallingUid()==2000){"ADB shell only"}
        require(arg==null&&(extras==null||extras.isEmpty)){"Arguments are not supported"}
        require(method=="apk-cache-info"||method=="clear-apk-cache"){"Unknown operation"}
        check(Looper.myLooper()!=Looper.getMainLooper()){"Use the maintenance Binder interface"}
        val manager=(context!!.applicationContext as JelandApplication).container.updates
        return runBlocking {
            if(method=="apk-cache-info") {
                val cache=manager.apkCacheInfo()
                Bundle().apply {putString("status","READY");putInt("count",cache.count);putLong("bytes",cache.bytes)}
            }else {
                val result=manager.clearDownloadedApksNow()
                Bundle().apply {
                    putString("status",if(result.busy)"BUSY"else if(result.failed>0)"FAILED"else"CLEARED")
                    putInt("deleted",result.deleted);putLong("bytes",result.bytes);putInt("failed",result.failed)
                }
            }
        }
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
