package ru.jeland.os.data.cluster

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.core.*
import java.lang.ref.WeakReference

internal interface ClusterRoute {
    fun open(afterArea:()->Unit)
    fun restore()
    fun restore(completed:(Boolean)->Unit){restore();completed(true)}
    fun closeStockMap(phase:String)
    fun resumeAfterSleep()=Unit
}

/** Window ownership is separate from the preview selection and survives an Android sleep cycle. */
internal interface ClusterOutputHost {
    val closing:Boolean
    val clusterDisplayId:Int? get()=null
    fun rebuildOutput()
    fun finishOutput()
}
data class ClusterOutput(val design:ClusterDesign,val accent:ClusterAccent,val colorMode:ClusterColorMode,val tripPeriod:ClusterTripPeriod=ClusterTripPeriod.SINCE_START)

class ClusterProjection internal constructor(
    private val app:Context,
    private val preferences:InterfaceRepository,
    private val report:(String)->Unit,
    private val route:ClusterRoute=OemClusterRoute(app,report),
    private val displays:()->List<Int> = {app.getSystemService(DisplayManager::class.java).displays.map {it.displayId}},
    private val launch:(Int)->Unit={id->
        app.startActivity(Intent(app,ClusterDisplayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle())
    },
    private val displayReady:(Int)->Boolean={id->app.getSystemService(DisplayManager::class.java).getDisplay(id)?.let {
        it.state !in setOf(Display.STATE_OFF,Display.STATE_DOZE,Display.STATE_DOZE_SUSPEND)
    }==true},
) {
    private val handler=Handler(Looper.getMainLooper())
    private val persisted=app.getSharedPreferences("cluster-projection",Context.MODE_PRIVATE)
    private var activity=WeakReference<ClusterOutputHost>(null)
    private var running=false
    private var recoveryEnabled=false
    private var restoring=false
    private var registered=false
    private var sleeping=false
    private var displaySleeping=false
    private var needsRecovery=false
    private var recovering=false
    private var recoveryScheduled=false
    private var recoveryAttempts=0
    private var recoveryTimeout:Runnable?=null
    var openedAtMillis:Long?=null;private set
    private var targetDisplay:Int?=null
    private var generation=0L
    private val mutable=MutableStateFlow("Штатная приборная панель")
    val status=mutable.asStateFlow()
    private val output=MutableStateFlow<ClusterOutput?>(null)
    val applied=output.asStateFlow()
    private val listener=object:DisplayManager.DisplayListener {
        override fun onDisplayAdded(id:Int)=displayAdded(id)
        override fun onDisplayChanged(id:Int)=displayChanged(id)
        override fun onDisplayRemoved(id:Int)=displayRemoved(id)
    }
    private val power=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent) {
            when(intent.action){
                Intent.ACTION_SCREEN_OFF->screenChanged(false)
                Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT->screenChanged(true)
            }
        }
    }
    fun start() {
        if(registered)return
        app.getSystemService(DisplayManager::class.java).registerDisplayListener(listener,handler)
        ContextCompat.registerReceiver(app,power,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_USER_PRESENT)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
        registered=true
        if(persisted.getBoolean("owns-cluster",false)) {
            // Only an explicitly applied output can resume, never an unapplied preview design.
            val last=loadOutput()
            if(last==null)restoreStock()else {
                output.value=last;running=true;recoveryEnabled=true;needsRecovery=true
                mutable.value="Восстанавливаем приборную панель…"
                requestRecovery("process-recreated")
            }
        }
    }
    private fun loadOutput():ClusterOutput?=runCatching {
        val design=ClusterDesign.valueOf(checkNotNull(persisted.getString("active-design",null)))
        require(design!=ClusterDesign.ORIGINAL)
        ClusterOutput(design,ClusterAccent.valueOf(checkNotNull(persisted.getString("active-accent",null))),
            ClusterColorMode.valueOf(checkNotNull(persisted.getString("active-color-mode",null))),
            ClusterTripPeriod.fromStored(persisted.getString("active-trip-period",ClusterTripPeriod.SINCE_START.name).orEmpty()))
    }.getOrNull()
    private fun saveOutput(value:ClusterOutput) {
        check(persisted.edit().putBoolean("owns-cluster",true).putString("active-design",value.design.name)
            .putString("active-accent",value.accent.name).putString("active-color-mode",value.colorMode.name)
            .putString("active-trip-period",value.tripPeriod.name).commit())
    }
    private fun clearSavedOutput(){persisted.edit().remove("active-design").remove("active-accent").remove("active-color-mode").remove("active-trip-period").commit()}
    private fun target():Int?=displays().let {ids->targetDisplay?.takeIf {it in ids}?:ids.firstOrNull {it!=Display.DEFAULT_DISPLAY}}
    fun show() {
        val prefs=preferences.state.value
        if(prefs.clusterDesign==ClusterDesign.ORIGINAL){restoreStock();return}
        if(restoring){mutable.value="Возвращаем штатную приборную панель…";return}
        val target=target()
        if(target==null) {
            if(running){unavailable("show-display-missing");requestRecovery("explicit-show")}
            mutable.value="Показ на ПП доступен при подключении к головному устройству"
            return
        }
        val selected=ClusterOutput(prefs.clusterDesign,prefs.clusterAccent,prefs.clusterColorMode,prefs.clusterTripPeriod)
        recoveryEnabled=true
        if(running&&targetDisplay==target) {
            saveOutput(selected);output.value=selected
            if(activity.get()?.closing==true)unavailable("activity-closing")
            if(needsRecovery) {
                if(!recovering){recoveryAttempts=0;handler.removeCallbacks(recover);recoveryScheduled=false}
                requestRecovery("explicit-show",0)
            }else mutable.value="Приборная панель включена"
            return
        }
        running=true;targetDisplay=target;openedAtMillis=SystemClock.elapsedRealtime()
        output.value=selected;saveOutput(selected)
        val request=++generation
        report("CLUSTER_OPEN_REQUEST explicit=true display=$target route=activity-area10-qnx design=${prefs.clusterDesign}")
        try {
            route.open {handler.post {if(running&&request==generation)route.closeStockMap("after-area")}}
            launch(target)
            mutable.value="Приборная панель включена"
        }catch(error:Exception) {
            report("CLUSTER_OPEN_ERROR ${error.javaClass.simpleName}: ${error.message}")
            restoreStock();mutable.value="Не удалось открыть приборную панель"
        }
    }
    internal fun screenChanged(on:Boolean) {
        val wasSleeping=sleeping
        sleeping=!on
        if(!on)unavailable("screen-off")else {
            if(wasSleeping)recoveryAttempts=0
            requestRecovery("screen-on")
        }
    }
    internal fun displayChanged(id:Int) {
        if(!running||id!=targetDisplay)return
        if(!displayReady(id)){displaySleeping=true;unavailable("display-off")}else if(needsRecovery) {
            if(displaySleeping){
                displaySleeping=false;recoveryAttempts=0
                handler.removeCallbacks(recover);recoveryScheduled=false
            }
            requestRecovery("display-on",150)
        }
    }
    internal fun displayAdded(id:Int) {
        if(running&&needsRecovery&&id!=Display.DEFAULT_DISPLAY&&!recovering){
            recoveryAttempts=0
            handler.removeCallbacks(recover);recoveryScheduled=false
            requestRecovery("display-added",150)
        }
    }
    internal fun displayRemoved(id:Int) {
        if(running&&id==targetDisplay){displaySleeping=true;unavailable("display-removed")}
    }
    private fun unavailable(reason:String) {
        if(!running||!recoveryEnabled||restoring)return
        if(!needsRecovery){recoveryAttempts=0;report("CLUSTER_OUTPUT_SUSPENDED reason=$reason display=$targetDisplay")}
        needsRecovery=true
        handler.removeCallbacks(recover);recoveryScheduled=false
        recoveryTimeout?.let(handler::removeCallbacks);recoveryTimeout=null
        recovering=false
    }
    fun dashboardResumed() {
        if(running&&!recovering&&activity.get()==null)unavailable("activity-missing")
        requestRecovery("dashboard-resumed")
    }
    private fun requestRecovery(reason:String,delay:Long=600) {
        if(!running||!recoveryEnabled||restoring||sleeping||!needsRecovery||recovering||recoveryScheduled||recoveryAttempts>=4)return
        recoveryScheduled=true
        report("CLUSTER_RECOVERY_SCHEDULE reason=$reason attempt=${recoveryAttempts+1}")
        handler.postDelayed(recover,delay)
    }
    private val recover=Runnable {
        recoveryScheduled=false
        if(!running||!recoveryEnabled||restoring||sleeping||!needsRecovery||recovering)return@Runnable
        recoveryAttempts++
        val id=target()
        if(id==null||!displayReady(id)) {
            displaySleeping=true
            report("CLUSTER_RECOVERY_WAIT_DISPLAY display=$id attempt=$recoveryAttempts")
            requestRecovery("display-not-ready",1500)
            return@Runnable
        }
        val oldDisplay=targetDisplay
        targetDisplay=id;recovering=true
        val request=++generation
        openedAtMillis=SystemClock.elapsedRealtime()
        mutable.value="Восстанавливаем приборную панель…"
        report("CLUSTER_RECOVERY_BEGIN display=$id attempt=$recoveryAttempts design=${output.value?.design}")
        try {
            route.resumeAfterSleep()
            // Re-negotiate ownership using the same release/acquire sequence as the working
            // stock -> custom switch. Repeating area=10 alone can leave an OEM stale surface.
            route.restore {released->handler.post {
                if(request!=generation||!recovering||!running||sleeping)return@post
                report("CLUSTER_RECOVERY_ROUTE_RESET released=$released")
                try {
                    route.open {handler.post {if(running&&request==generation)route.closeStockMap("wake-after-area")}}
                    val existing=activity.get()?.takeIf {!it.closing&&(it.clusterDisplayId?:oldDisplay)==id}
                    if(existing!=null)existing.rebuildOutput()else {activity.clear();launch(id)}
                }catch(error:Exception) {
                    recovering=false;report("CLUSTER_RECOVERY_ERROR ${error.javaClass.simpleName}: ${error.message}")
                    requestRecovery("retry",1500)
                }
            }}
            recoveryTimeout=Runnable {
                if(request==generation&&recovering){
                    recovering=false;report("CLUSTER_RECOVERY_TIMEOUT display=$id")
                    requestRecovery("window-not-resumed",1500)
                }
            }.also {handler.postDelayed(it,8000)}
        }catch(error:Exception) {
            recovering=false;report("CLUSTER_RECOVERY_ERROR ${error.javaClass.simpleName}: ${error.message}")
            requestRecovery("retry",1500)
        }
    }
    fun restoreStock() {
        if(restoring)return
        restoring=true;recoveryEnabled=false
        clearSavedOutput() // A user-requested return must not be undone by process/wake recovery.
        handler.removeCallbacks(recover);recoveryScheduled=false;recovering=false;needsRecovery=false
        recoveryTimeout?.let(handler::removeCallbacks);recoveryTimeout=null
        val request=++generation
        mutable.value="Возвращаем штатную приборную панель…"
        report("CLUSTER_RESTORE_REQUEST")
        val timeout=Runnable {
            if(request==generation&&restoring){restoring=false;mutable.value="Не удалось вернуть штатную панель. Повторите попытку.";report("CLUSTER_RESTORE_TIMEOUT; custom scene retained")}
        }
        handler.postDelayed(timeout,8000)
        route.restore {success->
            val finish=Runnable {
                if(request!=generation||!restoring)return@Runnable
                handler.removeCallbacks(timeout);restoring=false
                if(!success){mutable.value="Не удалось вернуть штатную панель. Повторите попытку.";report("CLUSTER_RESTORE_FAILED; custom scene retained");return@Runnable}
                running=false;targetDisplay=null;openedAtMillis=null;recoveryAttempts=0
                output.value=null
                persisted.edit().putBoolean("owns-cluster",false).apply()
                activity.get()?.finishOutput();activity.clear()
                mutable.value="Штатная приборная панель";report("CLUSTER_RESTORE_COMPLETE")
            }
            if(Looper.myLooper()==Looper.getMainLooper())finish.run()else handler.post(finish)
        }
    }
    internal fun attached(value:ClusterOutputHost) {
        if(!running||output.value==null){value.finishOutput();return}
        activity=WeakReference(value)
        route.closeStockMap("activity-start")
    }
    internal fun resumed(value:ClusterOutputHost) {
        if(activity.get()!==value||!running||restoring)return
        if(recovering) {
            recoveryTimeout?.let(handler::removeCallbacks);recoveryTimeout=null
            recovering=false;needsRecovery=false;recoveryAttempts=0
            mutable.value="Приборная панель включена";report("CLUSTER_RECOVERY_COMPLETE display=$targetDisplay")
        }else requestRecovery("activity-resumed")
    }
    internal fun stopped(value:ClusterOutputHost) {
        if(activity.get()===value&&!recovering)unavailable("activity-stopped")
    }
    internal fun detached(value:ClusterOutputHost) {
        if(activity.get()!==value)return
        activity.clear()
        if(running&&!restoring&&!recovering){unavailable("activity-destroyed");requestRecovery("activity-destroyed")}
    }
    internal fun close() {
        if(registered){app.getSystemService(DisplayManager::class.java).unregisterDisplayListener(listener);app.unregisterReceiver(power);registered=false}
        handler.removeCallbacks(recover);recoveryScheduled=false
        recoveryTimeout?.let(handler::removeCallbacks);recoveryTimeout=null
        if(running)restoreStock()
    }
}
