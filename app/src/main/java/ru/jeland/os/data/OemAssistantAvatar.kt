package ru.jeland.os.data

import android.content.*
import android.os.*
import com.desaysv.ivi.vdb.IVDBus
import com.desaysv.ivi.vdb.IVDBusNotify
import com.desaysv.ivi.vdb.event.VDEvent
import com.desaysv.ivi.vdb.event.id.vr.bean.VDAsr
import com.desaysv.ivi.vdb.event.id.vr.bean.VDTTS
import org.json.JSONObject
import ru.jeland.os.core.AssistantAvatarPolicy
import java.util.concurrent.Executors

/** The stock SceneEngine visibility request, not an overlay, force-stop or speech command. */
class OemAssistantAvatar(context:Context,private val report:(String)->Unit) {
    private val context=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var generation=0
    private var connection:ServiceConnection?=null
    private var bus:IVDBus?=null
    private var callback:IVDBusNotify?=null
    private var foreground=false
    private var subscribed=false
    private var policy=AssistantAvatarPolicy()
    private var lastVisible:Boolean?=null
    private var hidden=false
    fun start() {
        if(foreground)return
        foreground=true;policy=AssistantAvatarPolicy();lastVisible=null
        val token=++generation
        val notify=object:IVDBusNotify.Stub() {
            override fun onVDBusNotify(event:VDEvent?) {
                main.post {
                    if(token!=generation||!foreground)return@post
                    try {
                        val payload=event?.payload?:error("Missing VR payload")
                        payload.classLoader=VDAsr::class.java.classLoader
                        when(event.id) {
                            ASR->{val bean=payload.getParcelable<VDAsr>("status");val status=bean?.takeIf {it.key=="controlRecorder"}?.value;policy.asr(status);report("AVATAR ASR=$status key=${bean?.key}")}
                            TTS->{val status=payload.getParcelable<VDTTS>("status");policy.tts(status?.ttsType,status?.ttsStatus);report("AVATAR TTS=${status?.ttsType}/${status?.ttsStatus}")}
                            else->return@post
                        }
                    }catch(error:Exception){policy.asr(null);report("AVATAR UNKNOWN ${error.javaClass.simpleName}")}
                    updateVisibility()
                }
            }
        }
        callback=notify
        val binding=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(token!=generation||!foreground)return
                val service=IVDBus.Stub.asInterface(binder);bus=service
                worker.execute {
                    val result=runCatching {for(id in intArrayOf(ASR,TTS))service.subscribeCustomizedEvent(VDEvent(id,Bundle()),notify)}
                    main.post {
                        if(token!=generation)return@post
                        subscribed=result.isSuccess
                        report("AVATAR VR_SUBSCRIBED=$subscribed ${result.exceptionOrNull()?.javaClass?.simpleName.orEmpty()}")
                        updateVisibility()
                    }
                }
            }
            override fun onServiceDisconnected(name:ComponentName){if(token==generation){subscribed=false;updateVisibility()}}
            override fun onBindingDied(name:ComponentName){onServiceDisconnected(name)}
            override fun onNullBinding(name:ComponentName){onServiceDisconnected(name)}
        }
        connection=binding
        try {
            if(!context.bindService(Intent("action.desaysv.ivi.vds.vr.SERVICE").setComponent(
                    ComponentName("com.desaysv.ivi.vds.vr","com.desaysv.ivi.vds.vr.service.VRService")),binding,Context.BIND_AUTO_CREATE)) {
                connection=null;report("AVATAR VR_UNAVAILABLE; no hide request")
            }
        }catch(error:Exception){connection=null;report("AVATAR VR_DENIED ${error.javaClass.simpleName}; no hide request")}
    }
    private fun updateVisibility() {
        val visible=policy.shouldShow(foreground,subscribed)
        // If binding failed before we ever hid anything, do not touch another application's avatar.
        if((!subscribed&&!hidden)||lastVisible==visible)return
        val intent=avatarVisibilityIntent(context.packageName,visible)
        try {
            context.startForegroundService(intent)
            lastVisible=visible;hidden=!visible
            report("AVATAR VISIBILITY_REQUEST show=$visible — service acceptance is not visual confirmation")
        }catch(error:Exception){report("AVATAR VISIBILITY_REJECTED show=$visible ${error.javaClass.simpleName}")}
    }
    fun stop() {
        foreground=false;subscribed=false;++generation
        if(hidden)updateVisibility()
        val service=bus;val notify=callback;val binding=connection
        bus=null;callback=null;connection=null
        if(service!=null&&notify!=null)worker.execute {for(id in intArrayOf(ASR,TTS))runCatching {service.unsubscribeCustomizedEvent(VDEvent(id,Bundle()),notify)}}
        if(binding!=null)runCatching {context.unbindService(binding)}
        lastVisible=null
    }
    companion object {
        const val ASR=0x80002
        const val TTS=0x80004
        internal fun avatarVisibilityIntent(packageName:String,visible:Boolean)=Intent("com.iflytek.autofly.sceneengine.DATAUPLOAD")
            .setPackage("com.iflytek.autofly.sceneengine").putExtra("value",JSONObject()
                .put("dt","active_care").put("type","view_avatar")
                .put("activityFullPath","$packageName.MainActivity").put("showAvatar",visible).toString())
    }
}
