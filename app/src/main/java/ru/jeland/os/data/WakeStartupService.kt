package ru.jeland.os.data

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import ru.jeland.os.R
import ru.jeland.os.core.WakeStartupGate
import java.io.File

/** Passive screen observer while explicitly enabled. No wake locks, timers polling power, or vehicle graph. */
class WakeStartupService:Service() {
    private lateinit var store:AndroidSettingsStore
    private lateinit var gate:WakeStartupGate
    private val handler=Handler(Looper.getMainLooper())
    private var registered=false
    private var destroyed=false
    private val openAfterWake=Runnable {
        if(destroyed)return@Runnable
        if(!allowed()){record("wake_skipped disabled_or_permission_revoked");stopSelf();return@Runnable}
        val interactive=getSystemService(PowerManager::class.java)?.isInteractive==true
        val locked=getSystemService(KeyguardManager::class.java)?.isKeyguardLocked==true
        if(!gate.takeWake(interactive,locked)){record("wake_wait pending=${gate.pending} interactive=$interactive locked=$locked");return@Runnable}
        if(StartupVisibility.resumed){record("wake_consumed already_visible");return@Runnable}
        SystemStartup(this,store,::record).onWake()
    }
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            when(intent.action) {
                Intent.ACTION_SCREEN_OFF->{handler.removeCallbacks(openAfterWake);gate.screenOff();record("screen_off armed=true")}
                Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT->scheduleWake(intent.action.orEmpty())
            }
        }
    }
    private fun allowed()=store.string(SystemStartup.ENABLED_KEY,"false")=="true"&&runCatching {Settings.canDrawOverlays(this)}.getOrDefault(false)
    override fun onCreate() {
        super.onCreate();store=AndroidSettingsStore(this)
        gate=WakeStartupGate(store.string(SystemStartup.WAKE_PENDING_KEY,"false")=="true") {
            store.put(SystemStartup.WAKE_PENDING_KEY,it.toString())
        }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==DISABLE){store.put(SystemStartup.ENABLED_KEY,"false");record("user_disabled");stopSelf();return START_NOT_STICKY}
        if(!allowed()){gate.clear();stopSelf();return START_NOT_STICKY}
        try {
            val strings=createConfigurationContext(android.content.res.Configuration(resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(store.string("app.language","ru")))
            })
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,strings.getString(R.string.startup_channel),NotificationManager.IMPORTANCE_LOW).apply {
                description=strings.getString(R.string.startup_channel_description)
                setShowBadge(false)
            })
            val open=PendingIntent.getActivity(this,0,SystemStartup.launchIntent(this,"startup_notification"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val disable=PendingIntent.getService(this,1,Intent(this,WakeStartupService::class.java).setAction(DISABLE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_startup_notification)
                .setContentTitle(strings.getString(R.string.startup_notification_title)).setContentText(strings.getString(R.string.startup_notification_body))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .addAction(Notification.Action.Builder(null,strings.getString(R.string.startup_disable),disable).build()).build()
            if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(NOTIFICATION_ID,notification)
            if(!registered) {
                ContextCompat.registerReceiver(this,receiver,IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
                },ContextCompat.RECEIVER_NOT_EXPORTED)
                registered=true
                if(getSystemService(PowerManager::class.java)?.isInteractive==false)gate.screenOff()
                record("watcher_running pending=${gate.pending}")
                // Recover an off-cycle if Android recreated the service after the wake broadcast.
                if(gate.pending)scheduleWake("service_recreated")
            }
        }catch(error:Exception){record("watcher_failed ${error.javaClass.simpleName}");stopSelf();return START_NOT_STICKY}
        return START_STICKY
    }
    private fun scheduleWake(reason:String) {
        if(!gate.pending)return
        record("wake_event $reason")
        handler.removeCallbacks(openAfterWake)
        handler.postDelayed(openAfterWake,600) // One coalesced event, not a focus-reclaim loop.
    }
    private fun record(text:String) {
        Log.i("JelandOSStartup",text)
        store.put(SystemStartup.WATCH_STATUS_KEY,"${System.currentTimeMillis()} $text")
        // Own bounded journal, compatible with both app ZIP export and START -> L.
        runCatching {
            val file=File(getExternalFilesDir(null)?:filesDir,"JelandOS_vehicle_0.log")
            if(file.length()>64*1024)file.writeText("")
            file.appendText("${System.currentTimeMillis()} STARTUP $text\n")
        }
    }
    override fun onDestroy() {
        destroyed=true;handler.removeCallbacks(openAfterWake)
        if(registered){unregisterReceiver(receiver);registered=false}
        if(store.string(SystemStartup.ENABLED_KEY,"false")!="true")gate.clear()
        record("watcher_destroyed");stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    override fun onBind(intent:Intent?)=null
    companion object {
        private const val CHANNEL="jeland_startup"
        private const val NOTIFICATION_ID=2701
        private const val DISABLE="ru.jeland.os.startup.DISABLE"
        fun sync(context:Context,enabled:Boolean) {
            val app=context.applicationContext
            try {
                if(enabled)ContextCompat.startForegroundService(app,Intent(app,WakeStartupService::class.java))
                else app.stopService(Intent(app,WakeStartupService::class.java))
            }catch(error:Exception) {
                val message="watcher_start_failed ${error.javaClass.simpleName}"
                AndroidSettingsStore(app).put(SystemStartup.WATCH_STATUS_KEY,"${System.currentTimeMillis()} $message")
                Log.e("JelandOSStartup",message,error)
            }
        }
    }
}

/** Only MainActivity lifecycle sets this; a visible app never reopens itself on screen-on. */
internal object StartupVisibility {@Volatile var resumed=false}
