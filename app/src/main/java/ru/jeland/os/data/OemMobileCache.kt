package ru.jeland.os.data

import android.content.*
import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.carlan.bean.VDNetworkStatus
import ru.jeland.os.core.RadioReadings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class MobileCache(val connected:Boolean,val registered:Boolean,val type:String?,val level:Int?,val at:Long)

/** GET-only cached TBox state. Never calls SET, refresh, scan or a weather provider. */
internal class OemMobileCache(private val context:Context,private val changed:()->Unit,private val report:(String)->Unit) {
    private val main=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
    private var connection:ServiceConnection?=null;private var bus:IVDBus?=null
    @Volatile private var epoch=0
    private val inFlight=AtomicBoolean(false)
    private val bindTimeout=Runnable {if(connection!=null&&bus==null){report("TBOX_CACHE bind timeout");stop();changed()}}
    @Volatile var value:MobileCache?=null;private set
    private val poll=object:Runnable {override fun run(){val current=bus;val token=epoch
        if(current!=null&&inFlight.compareAndSet(false,true))worker.execute {try {runCatching {
            if(token!=epoch)return@runCatching
            val event=current.get(VDEvent(720899,Bundle()))
            if(event?.id!=720899)return@runCatching
            val bundle=event.payload?:return@runCatching
            bundle.classLoader=VDNetworkStatus::class.java.classLoader
            @Suppress("DEPRECATION") val data=if(bundle.getBoolean("IS_NULL",false))null else bundle.get("data") as? VDNetworkStatus
            val result=data?.let {MobileCache(it.connected,it.registered,RadioReadings.mobileType(it.type),RadioReadings.mobileLevel(it.level),SystemClock.elapsedRealtime())}
            main.post {if(token==epoch){value=result;changed()}}
        }.onFailure {report("TBOX_CACHE ${it.javaClass.simpleName}")}}finally {inFlight.set(false)}}
        if(connection!=null)main.postDelayed(this,10000)
    }}
    fun start() {
        if(connection!=null)return
        val token=++epoch
        val client=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder){if(token==epoch){main.removeCallbacks(bindTimeout);bus=IVDBus.Stub.asInterface(binder);main.removeCallbacks(poll);main.post(poll)}}
            override fun onServiceDisconnected(name:ComponentName){if(token==epoch){bus=null;value=null;changed()}}
            override fun onNullBinding(name:ComponentName){if(token==epoch){stop();changed()}}
            override fun onBindingDied(name:ComponentName){if(token==epoch){stop();changed()}}
        }
        try {
            val component=ComponentName("com.desaysv.ivi.vds.carlan","com.desaysv.ivi.vds.carlan.service.CarLanService")
            @Suppress("DEPRECATION") val info=context.packageManager.getServiceInfo(component,0)
            if(!info.exported||!info.enabled)return
            connection=client
            if(!context.bindService(Intent("action.desaysv.ivi.vds.carlan.SERVICE").setComponent(component),client,Context.BIND_AUTO_CREATE))connection=null
            else main.postDelayed(bindTimeout,5000)
        }catch(error:Exception){connection=null;report("TBOX_CACHE unavailable ${error.javaClass.simpleName}")}
    }
    fun stop(){epoch++;main.removeCallbacks(poll);main.removeCallbacks(bindTimeout);connection?.let {runCatching {context.unbindService(it)}};connection=null;bus=null;value=null}
}
