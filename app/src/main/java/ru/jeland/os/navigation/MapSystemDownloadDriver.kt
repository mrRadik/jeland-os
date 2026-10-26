package ru.jeland.os.navigation

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri

/** The provider behind DownloadManager is absent on some head units. */
interface MapSystemDownloadDriver {
    fun enqueue(request:DownloadManager.Request):Long
    fun query(id:Long):Cursor
    fun uri(id:Long):Uri?
    fun remove(id:Long)
}
internal class MapSystemDownloadException(cause:Exception):RuntimeException(cause)
class AndroidMapSystemDownloadDriver(context:Context):MapSystemDownloadDriver {
    private val manager=context.getSystemService(DownloadManager::class.java)
    override fun enqueue(request:DownloadManager.Request)=checkNotNull(manager){"Системная загрузка недоступна"}.enqueue(request)
    override fun query(id:Long)=checkNotNull(manager){"Системная загрузка недоступна"}.query(DownloadManager.Query().setFilterById(id))
    override fun uri(id:Long)=manager?.getUriForDownloadedFile(id)
    override fun remove(id:Long){manager?.remove(id)}
}
