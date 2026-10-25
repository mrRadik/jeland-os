package ru.jeland.os.data.media

import android.content.*
import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import ru.jeland.os.core.PhoneMusicSource
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Follows current music audio-focus owner; never writes source or requests focus. */
class OemSourceMonitor(private val context:Context,private val log:(String)->Unit) {
    @Volatile var bus:IVDBus?=null;private set
    @Volatile private var generation=0
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val reading=AtomicBoolean()
    private var connection:ServiceConnection?=null
    private var receive:((PhoneMusicSource?)->Unit)?=null
    fun start(receive:(PhoneMusicSource?)->Unit) {
        stop();this.receive=receive;val token=generation
        val component=ComponentName(OemMediaProtocol.PACKAGE,OemMediaProtocol.SERVICE)
        try {
            @Suppress("DEPRECATION") val info=context.packageManager.getServiceInfo(component,0)
            check(info.enabled&&info.exported)
            val c=object:ServiceConnection {
                override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                    if(token!=generation)return
                    if(binder.interfaceDescriptor!="com.desaysv.ivi.vdb.IVDBus"){receive(null);return}
                    bus=IVDBus.Stub.asInterface(binder);refresh()
                }
                override fun onServiceDisconnected(name:ComponentName){if(token==generation){bus=null;receive(null)}}
                override fun onBindingDied(name:ComponentName){onServiceDisconnected(name)}
                override fun onNullBinding(name:ComponentName){onServiceDisconnected(name)}
            }
            connection=c
            if(!context.bindService(Intent(OemMediaProtocol.ACTION).setComponent(component),c,Context.BIND_AUTO_CREATE)){connection=null;receive(null)}
        }catch(error:Exception){log("MEDIA_SOURCE_UNAVAILABLE ${error.javaClass.simpleName}");receive(null)}
    }
    fun refresh() {
        val token=generation;val current=bus?:return
        if(!reading.compareAndSet(false,true))return
        worker.execute {try {
            val source=runCatching {OemMediaProtocol.active(current)}.getOrElse {log("MEDIA_SOURCE_READ_ERROR ${it.javaClass.simpleName}");null}
            main.post {if(token==generation)receive?.invoke(source)}
        }finally {reading.set(false)}}
    }
    fun permits(source:PhoneMusicSource)=bus?.let {runCatching {OemMediaProtocol.active(it)==source}.getOrDefault(false)}==true
    fun stop(){generation++;bus=null;receive=null;val old=connection;connection=null;if(old!=null)runCatching {context.unbindService(old)}}
}
