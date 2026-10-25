package ru.jeland.os.data

import android.content.*
import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.event.VDEvent
import ru.jeland.os.core.*
import java.util.concurrent.Executors

/** The two native Launcher paths, not a camera Activity or an engineering/calibration test. */
class OemCameraLauncher internal constructor(context:Context,private val report:(String)->Unit,
    private val canOpen:()->Boolean,private val legacyOpen:()->Unit,
    private val configuration:()->NativeCameraConfiguration) {
    constructor(context:Context,report:(String)->Unit,canOpen:()->Boolean,legacyOpen:()->Unit):this(
        context,report,canOpen,legacyOpen,{readCameraConfiguration(report)})
    private val context=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"Jeland-Cameras").apply {isDaemon=true}}
    @Volatile private var generation=0L
    private var pending=false
    private var lastPress=-1000L
    private var connection:ServiceConnection?=null
    companion object {
        internal const val RVC_PACKAGE="com.desaysv.ivi.vds.rvc"
        internal const val RVC_SERVICE="com.desaysv.ivi.vds.rvc.service.RvcService"
        internal const val ICON_PRESS=786834
        internal const val VIDEO_SIGNAL=786635
        private fun readCameraConfiguration(report:(String)->Unit):NativeCameraConfiguration {
            val arch=runCatching {Class.forName("android.os.SystemProperties").getMethod("get",String::class.java,String::class.java)
                .invoke(null,"ro.desay.vehicle.protocol.version","4.0") as String}.getOrDefault("4.0")
            return try {
                val binder=Class.forName("android.os.ServiceManager").getMethod("getService",String::class.java)
                    .invoke(null,"com.desaysv.ivi.vds.vdev.service.VehicleDevice") as? IBinder
                val bus=binder?.let {IVDBus.Stub.asInterface(it)}
                fun read(index:Int):String?=bus?.get(VDEvent(917510,Bundle().apply {
                    putString("type","vehicle.persist.project.ext.configs$index")
                }))?.takeIf {it.id==917510}?.payload?.getString("value")
                nativeCameraConfiguration(arch,read(3),read(4))
            }catch(e:Exception){report("CAMERA_CONFIG_UNAVAILABLE ${e.javaClass.simpleName}; native RVC default");nativeCameraConfiguration(arch,null,null)}
        }
    }
    fun open(onError:(String)->Unit) {
        if(!canOpen()){onError("Камеры: нет активного подключения ГУ.");return}
        val now=SystemClock.elapsedRealtime()
        if(pending||now-lastPress<500){report("CAMERA_DUPLICATE_IGNORED");return}
        lastPress=now;pending=true
        val token=++generation
        worker.execute {
            val config=runCatching {configuration()}.getOrElse {
                report("CAMERA_CONFIG_ERROR ${it.javaClass.simpleName}");nativeCameraConfiguration("unknown",null,null)
            }
            report("CAMERA_ROUTE arch=${config.architecture} config55=${config.flag55} config112=${config.flag112} route=${config.route}")
            main.post {
                if(token!=generation)return@post
                if(!canOpen()){finish(token);return@post}
                if(config.route==NativeCameraRoute.CARINFO) {
                    finish(token);legacyOpen()
                }else bindRvc(token,onError)
            }
        }
        main.postDelayed({if(token==generation&&pending){finish(token);onError("Камеры: штатный сервис не ответил. Сохраните журнал.")}},3000)
    }
    private fun bindRvc(token:Long,onError:(String)->Unit) {
        val binding=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(token!=generation||!canOpen()){finish(token);return}
                val bus=IVDBus.Stub.asInterface(binder)
                worker.execute {
                    if(token!=generation||!canOpen())return@execute
                    val result=runCatching {
                        val status=runCatching {bus.get(VDEvent(VIDEO_SIGNAL,Bundle()))?.takeIf {it.id==VIDEO_SIGNAL}?.payload?.getInt("status",-1)}.getOrNull()
                        report("CAMERA_RVC_VIDEO_STATUS=$status")
                        if(status==2){report("CAMERA_RVC_ALREADY_ACTIVE; no toggle");return@runCatching}
                        if(token!=generation||!canOpen())return@runCatching
                        // EXACT Launcher/SystemUI payload: empty Bundle, no CMD_ID/VALUE.
                        bus.set(VDEvent(ICON_PRESS,Bundle()).also {it.createTimeMillis()})
                        report("CAMERA_RVC_ICON_SENT id=$ICON_PRESS; once; visibility not confirmed")
                    }
                    main.post {
                        if(token!=generation)return@post
                        finish(token)
                        result.exceptionOrNull()?.let {report("CAMERA_RVC_ERROR ${it.javaClass.simpleName}; no retry/fallback");onError("Камеры: запрос не выполнен. Сохраните журнал.")}
                    }
                }
            }
            override fun onServiceDisconnected(name:ComponentName){if(token==generation){finish(token);onError("Камеры: связь со штатным сервисом потеряна.")}}
            override fun onBindingDied(name:ComponentName)=onServiceDisconnected(name)
            override fun onNullBinding(name:ComponentName)=onServiceDisconnected(name)
        }
        connection=binding
        try {
            val intent=Intent("action.desaysv.ivi.vds.rvc.SERVICE").setComponent(ComponentName(RVC_PACKAGE,RVC_SERVICE))
            if(!context.bindService(intent,binding,Context.BIND_AUTO_CREATE)) {
                finish(token);onError("Камеры: штатный RVC-сервис недоступен.")
            }
        }catch(e:Exception){finish(token);report("CAMERA_RVC_BIND_ERROR ${e.javaClass.simpleName}");onError("Камеры: нет доступа к штатному сервису.")}
    }
    private fun finish(token:Long) {
        if(token!=generation)return
        generation++;pending=false
        connection?.let {runCatching {context.unbindService(it)}};connection=null
    }
    fun cancel(){finish(generation)} // Unbinding never closes/restarts the native camera UI.
}
