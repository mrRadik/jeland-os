package ru.jeland.os.data.roadalerts

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*

data class StrelkaRoadAlertsState(val enabled:Boolean=false,val autoStart:Boolean=false,val installed:Boolean=false,
    val version:String="",val permission:Boolean=false,val connected:Boolean=false,val running:Boolean=false,
    val alert:RoadAlert?=null,val message:String="")

/** Small process-wide endpoint, independent of the car/renderer graph and active navigation route. */
object RoadAlertsRuntime {
    @Volatile private var instance:StrelkaRoadAlerts?=null
    fun get(context:Context):StrelkaRoadAlerts=instance?:synchronized(this) {
        instance?:StrelkaRoadAlerts(context.applicationContext).also {instance=it}
    }
}

class StrelkaRoadAlerts internal constructor(context:Context) {
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("road-alerts",Context.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private val inbox=RoadAlertInbox()
    private var listener:StrelkaNotificationListener?=null
    private var serviceKey:String?=null
    private var alertGeneration=0L
    private var lastStart=-30_000L
    private var lastBind=-30_000L
    private var startGeneration=0L
    private val mutable=MutableStateFlow(StrelkaRoadAlertsState(prefs.getBoolean("enabled",false),prefs.getBoolean("auto-start",false)))
    val state=mutable.asStateFlow()
    private val expiry=Runnable {expire()}
    init {refresh()}
    private fun now()=SystemClock.elapsedRealtime()
    private fun publish(){
        inbox.expire(now())
        mutable.value=state.value.copy(alert=inbox.current)
        handler.removeCallbacks(expiry)
        inbox.current?.let {handler.postDelayed(expiry,(it.expiresAt-now()).coerceAtLeast(1))}
    }
    fun expire(){publish()}
    fun refresh() {
        val info=runCatching {app.packageManager.getPackageInfo(StrelkaNoticePolicy.PACKAGE,0)}.getOrNull()
        val permitted=runCatching {app.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(app,StrelkaNotificationListener::class.java))}.getOrDefault(false)
        if(!permitted||info==null){inbox.clear();serviceKey=null}
        mutable.value=state.value.copy(installed=info!=null,version=info?.versionName.orEmpty(),permission=permitted,
            connected=permitted&&listener!=null,running=permitted&&serviceKey!=null)
        publish()
    }
    fun resume() {
        refresh()
        if(state.value.enabled&&state.value.installed&&state.value.permission&&listener==null&&now()-lastBind>=30_000) {
            lastBind=now()
            runCatching {android.service.notification.NotificationListenerService.requestRebind(ComponentName(app,StrelkaNotificationListener::class.java))}
        }
        if(state.value.enabled&&state.value.autoStart&&state.value.permission&&!state.value.running&&now()-lastStart>=30_000)start(background=true)
    }
    fun setEnabled(value:Boolean) {
        prefs.edit().putBoolean("enabled",value).apply()
        if(!value){inbox.clear();serviceKey=null;startGeneration++}
        mutable.value=state.value.copy(enabled=value,running=value&&state.value.running,message="")
        publish()
        if(value){refresh();listener?.sync();resume()}
    }
    fun setAutoStart(value:Boolean) {
        prefs.edit().putBoolean("auto-start",value).apply();mutable.value=state.value.copy(autoStart=value)
        if(value)resume()
    }
    private fun message(text:String){mutable.value=state.value.copy(message=text)}
    fun start()=start(background=false)
    private fun start(background:Boolean) {
        refresh()
        if(!state.value.enabled){message("Включите предупреждения Стрелки");return}
        if(!state.value.installed){message("Установите и настройте Стрелку");return}
        val component=ComponentName(StrelkaNoticePolicy.PACKAGE,StrelkaNoticePolicy.PACKAGE+".ExtCommands")
        val receiver=runCatching {app.packageManager.getReceiverInfo(component,0)}.getOrNull()
        if(receiver==null||!receiver.exported||!receiver.enabled){message("Эта версия Стрелки не поддерживает внешний запуск");return}
        runCatching {
            lastStart=now()
            if(background)app.sendBroadcast(Intent(StrelkaNoticePolicy.PACKAGE+".action.START_SERVICE").setComponent(component).addCategory(Intent.CATEGORY_DEFAULT))
            else app.startActivity(mainShortcut("scut_start")?:error("Strelka activity unavailable"))
        }.onSuccess {
            if(!state.value.running) {
                message("Команда запуска передана в Стрелку")
                val generation=++startGeneration
                handler.postDelayed({
                    if(startGeneration==generation&&state.value.enabled&&!state.value.running)
                        message("Стрелка не подтвердила запуск. Откройте её и проверьте GPS, автоматизацию и лицензию.")
                },10_000)
            }
        }
            .onFailure {message("Не удалось запустить Стрелку. Откройте её вручную.")}
    }
    // Official public launcher shortcuts in Strelka's res/xml/shortcuts, verified in 25.01.1s.
    private fun mainShortcut(extra:String):Intent? {
        val component=ComponentName(StrelkaNoticePolicy.PACKAGE,StrelkaNoticePolicy.PACKAGE+".MainActivity")
        val activity=runCatching {app.packageManager.getActivityInfo(component,0)}.getOrNull()
        if(activity==null||!activity.exported||!activity.enabled)return null
        return Intent(Intent.ACTION_VIEW).setComponent(component).putExtra(extra,1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun openApp() {
        val intent=mainShortcut("scut_main")?:app.packageManager.getLaunchIntentForPackage(StrelkaNoticePolicy.PACKAGE)
        if(intent==null){message("Установите и настройте Стрелку");return}
        runCatching {app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            .onFailure {message("Не удалось открыть Стрелку")}
    }
    fun download() {
        runCatching {app.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.ivolk.ru/download.htm")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            .onFailure {message("Официальная загрузка: www.ivolk.ru/download.htm")}
    }
    fun openPermission() {
        val component=ComponentName(app,StrelkaNotificationListener::class.java)
        for(intent in listOf(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,component.flattenToString()),Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            try {app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return}catch(_:Exception){}
        }
        message("Системный экран недоступен. Используйте setup-strelka.cmd на компьютере.")
    }
    internal fun attached(value:StrelkaNotificationListener) {
        listener=value;refresh()
    }
    internal fun detached(value:StrelkaNotificationListener) {
        if(listener!==value)return
        listener=null;serviceKey=null;inbox.clear();mutable.value=state.value.copy(connected=false,running=false);refresh()
    }
    internal fun posted(value:StatusBarNotification,replay:Boolean=false) {
        // Reject other applications BEFORE inspecting extras. Do not log or persist notification content.
        if(value.packageName!=StrelkaNoticePolicy.PACKAGE||!state.value.enabled)return
        val n=value.notification
        if(value.id==1095&&n.channelId=="1037"&&n.flags and Notification.FLAG_FOREGROUND_SERVICE!=0&&n.flags and Notification.FLAG_GROUP_SUMMARY==0){
            serviceKey=value.key;mutable.value=state.value.copy(running=true,message="");return
        }
        if(n.channelId!="1038"||value.id!=10017||value.tag!="Alert")return
        val snapshot=StrelkaNotice(value.packageName,n.channelId,value.id,value.tag,value.key,
            n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),value.postTime,
            n.flags and Notification.FLAG_FOREGROUND_SERVICE!=0,n.flags and Notification.FLAG_GROUP_SUMMARY!=0)
        if(inbox.accept(snapshot,now(),System.currentTimeMillis(),replay))alertGeneration++
        publish()
    }
    internal fun removed(value:StatusBarNotification) {
        if(value.packageName!=StrelkaNoticePolicy.PACKAGE)return
        if(serviceKey==value.key){serviceKey=null;inbox.clear();mutable.value=state.value.copy(running=false);publish();return}
        val key=value.key
        // Strelka cancels/reposts the same notification when updating it. Coalesce that short gap.
        val generation=alertGeneration
        handler.postDelayed({if(alertGeneration==generation){inbox.remove(key);publish()}},180)
    }
}
